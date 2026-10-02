package fr.tomcatchooser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Démarre Tomcat hors d'Eclipse, en mode débogage (JPDA) : Eclipse s'y attache ensuite avec une
 * configuration « Remote Java Application » sur le port {@value #DEBUG_PORT}.
 * <p>
 * Le Tomcat lancé est celui du server.xml choisi : CATALINA_BASE est le dossier parent de conf,
 * et CATALINA_HOME aussi (il doit contenir bin\catalina.bat). Le JDK et la mémoire se règlent,
 * comme pour tout Tomcat, dans bin\setenv.bat (JAVA_HOME, CATALINA_OPTS).
 */
public final class TomcatLauncher {

    static final int DEBUG_PORT = 8000;

    private TomcatLauncher() {
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    /** Dossier de Tomcat déduit de .../conf/server.xml, ou null s'il ne contient pas bin/catalina. */
    public static Path catalinaHome(Path serverXml) {
        Path conf = serverXml.toAbsolutePath().getParent();
        Path home = conf == null ? null : conf.getParent();
        if (home == null || !Files.isRegularFile(script(home))) {
            return null;
        }
        return home;
    }

    private static Path script(Path home) {
        return home.resolve("bin").resolve(isWindows() ? "catalina.bat" : "catalina.sh");
    }

    /**
     * Lance « catalina jpda start » : Tomcat s'ouvre dans sa propre console (Windows) et continue
     * de tourner si Tomcat Chooser est fermé.
     */
    public static void start(Path serverXml) throws IOException {
        Path home = catalinaHome(serverXml);
        if (home == null) {
            throw new IOException("Impossible de démarrer Tomcat : pas de bin\\" + (isWindows() ? "catalina.bat" : "catalina.sh")
                    + " à côté du dossier conf de " + serverXml + ".");
        }
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.addAll(List.of("cmd.exe", "/c", script(home).toString()));
        } else {
            command.addAll(List.of("sh", script(home).toString()));
        }
        command.addAll(List.of("jpda", "start"));
        ProcessBuilder builder = new ProcessBuilder(command).directory(home.resolve("bin").toFile());
        Map<String, String> env = builder.environment();
        env.put("CATALINA_HOME", home.toString());
        env.put("CATALINA_BASE", home.toString());
        env.put("JPDA_TRANSPORT", "dt_socket");
        env.put("JPDA_ADDRESS", "localhost:" + DEBUG_PORT);
        env.put("JPDA_SUSPEND", "n");
        builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.start();
    }
}
