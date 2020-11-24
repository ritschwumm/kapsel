
# about

kapsel is a bare-bones packaging tool for java applications which allows distributing java applications in a single jar file in an extremely simple way. in essence, kapsel is a stripped-to-the-bones reimplementation of [capsule](https://github.com/puniverse/capsule)'s core functionality, without any of the bells and whistles.

# how it works

a kapsel bundle is an executable jar. it consists of
* a starter class named `Kapsel.class` itself
* a standard `META-INF/MAINFEST.MF` with some additional attributes used by the starter class
* a couple of jar files containing the code of the application.

the startup of a kapsel bundle happens in two stages:
* a first jvm instance (called the kapsel jvm in the following) runs the starter class
* the starter class then starts a second jvm (called the application jvm) which runs the actual application

when a kapsel bundle is run with `java -jar bundle.jar`, the kapsel jvm will execute the main class `Kapsel.class`. the kapsel main class will then read `META-INF/MAINFEST.MF`. for each `Kapsel-Class-Path` entry in the kapsel manifest, a copy of the jar file of the same name will be copied from inside the bundle to the kapsel cache directory under  `${user.home}/.kapsel` named after `Kapsel-Applicaton-Id`. finally, kapsel will start a new application jvm from `${java.home}/bin/java` with the jar files on the classpath and additional parameters passed in from `Kapsel-Jvm-Options` and the main class taken from `Kapsel-Main-Class`.

# example bundle

the contents of a kapsel bundle could look like this:
```
/META-INF/MANIFEST.MF
/Kapsel.class
/application.jar
/library.jar
```

the files inside that jar file are
* `Kapsel.class` is provided by this project
* `application.jar` is the main application jar and contains a main class `application.Main`
* `library.jar` is some additional ja required by the application
* `META-INF/MANIFEST.MF` contains additional properties as described below

here's an example of how `META-INF/MANIFEST.MF` could look:
```
# this tells the kapsel jvm to start kapsel
Main-Class: Kapsel

# the identifier of this application:
# this is used to build the path of the cache directory,
# so it shouldn't contain any funky characters
Kapsel-Application-Id: application-1.0.0

# whitespace-separated options passed to the application jvm
Kapsel-Jvm-Options: -Xmx128m

# whitespace-separated names of packaged jar files:
# these will be part of the application jvm's classpath.
Kapsel-Class-Path: application.jar  library.jar

# the name of the main application class:
# this class must exist in one of the packaged jar files.
Kapsel-Main-Class: application.Main
```

now imagine running the bundle in a kapsel jvm like this:
```java -jar example.jar -J-Dtest.config=foobar hello world```

the command command line used by `Kapsel.class` to start the application jvm now will be something like this:
```
/usr/bin/java
  -Xmx128m \
  -Dtest.config=foobar \
  -cp /home/myself/.kapsel/application.jar:/home/myself/.kapsel/library.jar \
  application.Main hello world
```

# additional notes

## environment variables

the `Kapsel.class` running in the kapsel jvm understands a few environment variables:
* `KAPSEL_JAVA` changes the java command used to run the application jvm
* `KAPSEL_CACHE` changes the base directory for application jars extracted from the kapsel bundle
* `KAPSEL_DEBUG` enables debug output on stderr

## passing options to the application jvm

parameters prefixed with `-J` will have this prefix stripped off and then be passed directly to the application jvm.

## executable jars on linux/mac os x

optionally, a simple header can be put in front of the kapsel bundle file which doubles as a shell script to run it. this is possible, because zip files (which jar files essentially are) can contain arbitrary data before theit actual content begins. which this header in place, setting the kapsel bundle's executable bit with `chmod +x bundle.jar` will allow running the bundle directly from the command line.

```
#!/bin/sh
exec java -jar "$0" "$@"
```
