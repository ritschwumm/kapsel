import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.jar.*;

public final class Kapsel {
	private static final int OS_LINUX	= 1;
	private static final int OS_BSD		= 2;
	private static final int OS_MACOSX	= 3;
	private static final int OS_WINDOWS	= 4;
	private static final int OS_UNKNOWN	= 0;

	public static void main(String[] args) {
		try {
			final FileSystem fileSystem	= FileSystems.getDefault();

			// -J arguments get passed to the app jvm without the -J prefix
			final Map <Boolean,List<String>>	partArgs	=
				Arrays.asList(args).stream().collect(
					Collectors.partitioningBy(arg -> arg.startsWith("-J"))
				);
			final List<String>	jvmArgs		= partArgs.get(true).stream().map(arg -> arg.substring(2)).collect(Collectors.toList());
			final List<String>	plainArgs	= partArgs.get(false);
			debug("jvmArgs",	jvmArgs);
			debug("plainArgs",	plainArgs);

			final String	osName	= System.getProperty("os.name").toLowerCase(Locale.ROOT);
			final int 		os		= osName.contains("linux")		? OS_LINUX
									: osName.contains("bsd")		? OS_BSD
									: osName.contains("mac")		? OS_MACOSX
									: osName.contains("windows")	? OS_WINDOWS
									: OS_UNKNOWN;
			debug("os",	os);

			final Path	javaBin	=
				envVar("KAPSEL_JAVA").map(fileSystem::getPath).orElseGet(() ->
					fileSystem.getPath(
						System.getProperty("java.home"),
						"bin",
						os == OS_WINDOWS ? "javaw" : "java"
					)
				);
			debug("javaBin",	javaBin);

			try (InputStream manifestStream = resource("META-INF/MANIFEST.MF")) {
				final Manifest		manifest		= new Manifest(manifestStream);
				final Attributes	attributes		= manifest.getMainAttributes();
				final String		applicationId	= oneArg	(attributes, "Kapsel-Application-Id");
				final List<String>	jvmOptions		= manyArg	(attributes, "Kapsel-Jvm-Options");
				final String		mainClass		= oneArg	(attributes, "Kapsel-Main-class");
				final List<String>	classPath		= manyArg	(attributes, "Kapsel-Class-Path");
				debug("applicationId",	applicationId);
				debug("jvmOptions",		jvmOptions);
				debug("mainClass",		mainClass);
				debug("classPath",		classPath);

				final Path cacheBase	=
					envVar("KAPSEL_CACHE").map(fileSystem::getPath).orElseGet(() -> standardCacheBase(fileSystem, os));
				debug("cacheBase",	cacheBase);

				final Path cache	= cacheBase.resolve(applicationId);
				debug("cache",	cache);

				Files.createDirectories(cache);
				for (final String jar : classPath) {
					final Path	output	= cache.resolve(jar);
					// if (!Files.exists(output)) {
					debug("copying jar",	output);
					try (final InputStream input = resource(jar)) {
						Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
					}
				}

				final String classPathArg	=
					classPath.stream()
					.map(cache::resolve)
					.map(Path::toString)
					.collect(Collectors.joining(""+File.pathSeparatorChar));
				debug("classPathArg",	classPathArg);

				final List<String>	command	= new ArrayList<>();
				command.add(javaBin.toString());
				command.addAll(jvmOptions);
				command.addAll(jvmArgs);
				command.add("-cp");
				command.add(classPathArg);
				command.add(mainClass);
				command.addAll(plainArgs);
				debug("command", command);

				final Process process	=
					new ProcessBuilder(command)
					.redirectInput	(ProcessBuilder.Redirect.INHERIT)
					.redirectOutput	(ProcessBuilder.Redirect.INHERIT)
					.redirectError	(ProcessBuilder.Redirect.INHERIT)
					.start();

				Runtime.getRuntime().addShutdownHook(new Thread(() -> process.destroy()));

				final int rc	= process.waitFor();
				debug("rc", rc);
				System.exit(rc);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(128);
		}
	}

	private static Path standardCacheBase(FileSystem fileSystem, int os) {
		final Path	userHome	= fileSystem.getPath(System.getProperty("user.home"));
		switch (os) {
			case OS_LINUX:
			case OS_BSD:
				return envVar("XDG_CACHE_HOME").map(fileSystem::getPath).orElse(userHome.resolve(".cache")).resolve("kapsel");
			case OS_MACOSX:
				return userHome.resolve("Library").resolve("Caches").resolve("kapsel");
			case OS_WINDOWS:
				or(envVar("LOCALAPPDATA"), envVar("APPDATA")).map(fileSystem::getPath).orElse(userHome).resolve("kapsel").resolve("cache");
			default:
				return userHome.resolve(".kapsel");
		}
	}

	private static String oneArg(Attributes attributes, String key) {
		final String value	= attributes.getValue(key);
		if (value == null)	throw new RuntimeException("attribute " + key + " not found in the manifest");
		return value;
	}

	private static List<String> manyArg(Attributes attributes, String key) {
		final String value	= attributes.getValue(key);
		if (value == null)	return Collections.emptyList();
		return Arrays.stream(value.split("\\s"))
			.map(it -> it.trim())
			.filter(it -> !it.isEmpty())
			.collect(Collectors.toList());
	}

	private static void debug(Object... args) {
		envVar("KAPSEL_DEBUG")
		.filter(it -> it.equals("true"))
		.ifPresent(it ->
			System.err.println(Arrays.toString(args))
			//System.out.println(String.join(" ", Arrays.asList(args)));
		);
	}

	private static InputStream resource(String path) {
		return Kapsel.class.getClassLoader().getResourceAsStream(path);
	}

	private static Optional<String> envVar(String name) {
		return Optional.ofNullable(System.getenv(name));
	}

	// NOTE Optional.or exists from java 9 on
	private static <T> Optional<T> or(Optional<T> a, Optional<T> b) {
		return a.isPresent() ? a : b;
	}
}
