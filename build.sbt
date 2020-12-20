Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(Seq(
	organization	:= "de.djini",
	version			:= "0.2.0",

	scalaVersion	:= "2.13.4",
	scalacOptions	++= Seq(
		"-feature",
		"-deprecation",
		"-unchecked",
		"-Werror",
		"-Xlint",
	),

	conflictManager	:= ConflictManager.strict
))

lazy val `kapsel` =
	project.in(file("."))
	.aggregate(
		`kapsel-start`,
		`kapsel-example`,
	)
	.settings(
		publishArtifact := false
		//publish		:= {},
		//publishLocal	:= {}
	)

//------------------------------------------------------------------------------

lazy val `kapsel-start`	=
	project.in(file("modules/start"))
	.settings(
		// this is a pure java project
		crossPaths			:= false,
		autoScalaLibrary	:= false,

		// stay compatible with java 8
		Compile				/ javacOptions	++= Seq("-source", "1.8"),
		Compile	/ compile	/ javacOptions	++= Seq("-target", "1.8"),
	)

//------------------------------------------------------------------------------

lazy val `kapsel-example`	=
	project.in(file("modules/example"))
	.settings(
		TaskKey[File]("kapsel")	:= {
			val kapselClass		= (`kapsel-start` / Runtime / products).value(0) / "Kapsel.class"

			val bundleId		= name.value + "-" + version.value
			val bundleMainClass	= (Compile / mainClass).value getOrElse (sys error "missing main class")
			val bundleClassPath	= (Compile / fullClasspathAsJars).value map (_.data)

			// prevent name clashes in the classpath
			val classPath	= bundleClassPath.zipWithIndex map { case (file,index) => file -> s"$index-${file.getName}" }

			val kapselDir	= target.value / "kapsel"
			IO delete  kapselDir

			// build kapsel jar without exec header
			val tempJar		= kapselDir / s"headerless-${bundleId}.jar"
			val manifest	= new java.util.jar.Manifest
			manifest.getMainAttributes.putValue("Main-Class",				"Kapsel")
			manifest.getMainAttributes.putValue("Kapsel-Application-Id",	bundleId)
			manifest.getMainAttributes.putValue("Kapsel-Jvm-Options",		"-Xmx128m")
			manifest.getMainAttributes.putValue("Kapsel-Class-Path",		classPath.map(_._2).mkString(" "))
			manifest.getMainAttributes.putValue("Kapsel-Main-Class",		bundleMainClass)
			val bundleContent	= (kapselClass	-> "Kapsel.class") +: classPath
			IO jar (bundleContent, tempJar, manifest, None)

			// build kapsel jar with exec header
			val bundleJar	= kapselDir / s"${bundleId}.jar"
			IO write (
				bundleJar,
				"""	|#/bin/sh
					|exec java -jar "$0" "$@"
					|""".stripMargin
			)
			IO append (bundleJar, IO readBytes tempJar)
			bundleJar setExecutable (true, false)

			streams.value.log info s"built kapsel bundle $bundleJar"

			bundleJar
		}
	)
