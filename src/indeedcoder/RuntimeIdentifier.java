package indeedcoder;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RuntimeIdentifier {

	public static void main(String[] args) {
		RuntimeIdentifierUtils.getRuntime();

	}
}

enum RuntimeType {
	JAR, IDE, OTHER;
}

class RuntimeIdentifierUtils {

	private static Map<RuntimeType, Integer> map = new HashMap<>();
	private static String sourceIde = "";
	private static String sourceJar = "";

	private RuntimeIdentifierUtils() {
	}

	public static void getRuntime() {
		System.out.println(String.format("Running from jar based on location: %b", isRunningFromJarBasedOnLocation()));
		System.out.println(String.format("Running from jar based on manifest: %b", isRunningFromJarBasedOnManifest()));
		System.out.println(String.format("Running from IDE based on classpath: %s", identifyIDE()));
		URL classUrl = getClassLoaderResourceUrl();
		System.out.println("Class resource: " + classUrl);
		System.out.println(String.format("Class Loader result: %s", useClassLoader(classUrl)));
		System.out.println(String.format("Jar connection result: %s", useJarConnectionCheck(classUrl)));
		System.out.println("_________________________________________________________________________");
		System.out.print("Final Result: ");
		Optional<Map.Entry<RuntimeType, Integer>> maxEntry = map.entrySet().stream().max(Map.Entry.comparingByValue());
		if (maxEntry.isPresent()) {
			RuntimeType key = maxEntry.get().getKey();
			switch (key) {
			case IDE:
				System.out.println(sourceIde + " IDE");
				break;
			case JAR:
				System.out.println(sourceJar + " JAR");
				break;
			case OTHER:
				System.out.println("Neither IDE nor JAR");
			}
		} else {
			System.out.println("Unidentified Environment.");
		}
	}

	private static boolean isRunningFromJarBasedOnLocation() {
		boolean runningFromJar = false;
		try {
			String location = RuntimeIdentifier.class.getProtectionDomain().getCodeSource().getLocation().toURI()
					.getPath();
			if (location != null) {
				runningFromJar = location.endsWith(".jar");
			}
		} catch (URISyntaxException e) {
		}
		voteToJarOrElse(runningFromJar);
		return runningFromJar;
	}

	private static boolean isRunningFromJarBasedOnManifest() {
		boolean runningFromJar = false;
		try (InputStream manifestStream = RuntimeIdentifier.class.getClassLoader()
				.getResourceAsStream("META-INF/MANIFEST.MF")) {
			runningFromJar = (manifestStream != null);
		} catch (Exception e) {
		}
		voteToJarOrElse(runningFromJar);
		return runningFromJar;
	}

	private static void voteToJarOrElse(boolean runningFromJar) {
		if (runningFromJar) {
			voteToRuntimeType(RuntimeType.JAR);
		} else {
			voteToRuntimeType(RuntimeType.IDE);
			voteToRuntimeType(RuntimeType.OTHER);
		}
	}

	private static void voteToRuntimeType(RuntimeType runtimeType) {
		map.put(runtimeType, map.getOrDefault(runtimeType, 0) + 1);
	}

	private static String identifyIDE() {
		String property = System.getProperty("java.class.path").toLowerCase();
		if (property == null) {
			voteToRuntimeType(RuntimeType.OTHER);
			return "Unidentified";
		} else if (property.contains("idea_rt.jar")) {
			voteToRuntimeType(RuntimeType.IDE);
			sourceIde = "IntelliJ IDEA.";
			return sourceIde;
		} else if (property.contains("eclipse")) {
			voteToRuntimeType(RuntimeType.IDE);
			sourceIde = "Eclipse";
			return sourceIde;
		} else {
			voteToRuntimeType(RuntimeType.JAR);
			voteToRuntimeType(RuntimeType.OTHER);
			return "JAR or another environment";
		}
		// Limitation: Not perfect. IDEs may still reference a `.jar` in the classpath
		// for libraries.
		// so check the main class source instead for more accuracy.
	}

	private static URL getClassLoaderResourceUrl() {
		String classFile = RuntimeIdentifier.class.getName().replace('.', '/') + ".class";
		return RuntimeIdentifier.class.getClassLoader().getResource(classFile);
	}

	private static String useClassLoader(URL classUrl) {
		if (classUrl != null && (classUrl.toString().startsWith("jar") || classUrl.toString().startsWith("rsrc"))) {
			voteToRuntimeType(RuntimeType.JAR);
			return "Running from a JAR";
		} else {
			voteToRuntimeType(RuntimeType.IDE);
			return "Running from an IDE";
		}
		// In a JAR: `jar:file:/path/to/app.jar!/path/to/class.class`
		// In IDE: `file:/path/to/build/classes/...`
	}

	private static String useJarConnectionCheck(URL classUrl) {
		String result = "";
		if (classUrl != null && ("jar".equals(classUrl.getProtocol()) || "rsrc".equals(classUrl.getProtocol()))) {
			try {
				voteToRuntimeType(RuntimeType.JAR);
				result = "Running from a JAR";
				JarURLConnection conn = (JarURLConnection) classUrl.openConnection();
				sourceJar = conn.getJarFileURL().toString();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
			}
		} else {
			result = "Running from an IDE";
			voteToRuntimeType(RuntimeType.IDE);
		}
		return result;
	}
}