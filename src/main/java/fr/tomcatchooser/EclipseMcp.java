package fr.tomcatchooser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client minimal du plugin « Eclipse MCP Server » (vogella) : pilote Eclipse depuis l'extérieur.
 * <p>
 * Tomcat Chooser s'en sert pour démarrer, arrêter et redémarrer Tomcat avec le plugin Sysdeo, c'est-à-dire
 * dans Eclipse, avec sa console et son débogueur. Le serveur écoute en local (127.0.0.1) et exige un token,
 * lu dans le fichier que le plugin crée ; il est désactivé par défaut (Preferences > General > MCP Server).
 */
final class EclipseMcp {

    static final int DEFAULT_PORT = 8642;
    /** Commandes du plugin Sysdeo Tomcat Launcher (menu Tomcat). */
    static final String SYSDEO_START = "AUTOGEN:::com_sysdeo_eclipse_tomcat_actionSet/com.sysdeo.eclipse.tomcat.start";
    static final String SYSDEO_STOP = "AUTOGEN:::com_sysdeo_eclipse_tomcat_actionSet/com.sysdeo.eclipse.tomcat.stop";
    static final String SYSDEO_RESTART = "AUTOGEN:::com_sysdeo_eclipse_tomcat_actionSet/com.sysdeo.eclipse.tomcat.restart";

    private static final Pattern EFFECTIVE = Pattern.compile("\"effective\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final URI endpoint;
    private final String token;

    private EclipseMcp(int port, String token) {
        this.endpoint = URI.create("http://127.0.0.1:" + port + "/mcp");
        this.token = token;
    }

    /** Fichier du token créé par le plugin dans le dossier de l'utilisateur. */
    static Path tokenFile() {
        return Paths.get(System.getProperty("user.home"), ".eclipse", "com.vogella.eclipse.mcp.server", "token");
    }

    /**
     * Client prêt à l'emploi, ou null si le plugin est absent, désactivé ou Eclipse fermé : pas de fichier token,
     * ou rien n'écoute sur le port. Rapide (quelques centaines de ms au plus) : à appeler hors du thread JavaFX.
     */
    static EclipseMcp connect() {
        return connect(tokenFile(), Integer.getInteger("tomcatchooser.mcpPort", DEFAULT_PORT));
    }

    static EclipseMcp connect(Path tokenFile, int port) {
        try {
            if (!Files.isRegularFile(tokenFile)) {
                return null;
            }
            String token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
            if (token.isEmpty()) {
                return null;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
            }
            return new EclipseMcp(port, token);
        } catch (IOException e) {
            return null;
        }
    }

    /** Vrai si le Tomcat de Sysdeo est celui de ce server.xml (préférence tomcatConfigFile du plugin). */
    boolean drives(Path serverXml) throws IOException {
        String text = callTool("eclipse_get_preferences",
                "{\"qualifier\":\"net.sf.eclipse.tomcat\",\"key\":\"tomcatConfigFile\"}");
        String configured = effectiveValue(text);
        if (configured == null) {
            return false;
        }
        return samePath(Paths.get(configured), serverXml);
    }

    /** Exécute une commande du workbench, comme son bouton de barre d'outils. */
    void runCommand(String commandId) throws IOException {
        String text = callTool("eclipse_run_workbench_command", "{\"command\":\"" + escape(commandId) + "\"}");
        if (!text.contains("\"executed\": true") && !text.contains("\"executed\":true")) {
            throw new IOException("Eclipse n'a pas exécuté la commande : " + abbreviate(text));
        }
        if (text.contains("\"outcome\": \"error\"") || text.contains("\"outcome\":\"error\"")) {
            throw new IOException("La commande a échoué dans Eclipse : " + abbreviate(text));
        }
    }

    // ---------------------------------------------------------------- protocole

    /** Un appel d'outil MCP (HTTP « streamable ») : initialisation, notification, puis l'appel lui-même. */
    private String callTool(String tool, String argumentsJson) throws IOException {
        Reply init = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                + "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"tomcat-chooser\",\"version\":\"1\"}}}", null);
        String session = init.session;
        post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", session);
        Reply answer = post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"" + tool + "\",\"arguments\":" + argumentsJson + "}}", session);
        String body = payload(answer.body);
        if (body.contains("\"isError\":true") || body.contains("\"isError\": true") || body.contains("\"error\":{")) {
            throw new IOException("Eclipse a refusé l'appel " + tool + " : " + abbreviate(body));
        }
        return unescapeJson(body);
    }

    /** Réponse HTTP : corps et identifiant de session MCP. */
    private static final class Reply {
        final String body;
        final String session;

        Reply(String body, String session) {
            this.body = body;
            this.session = session;
        }
    }

    /**
     * Requête POST en HTTP bloquant (HttpURLConnection) : le client HTTP du JDK a besoin d'un sélecteur NIO,
     * que Windows refuse parfois de créer (« Unable to establish loopback connection »).
     */
    private Reply post(String json, String session) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint.toString()).openConnection();
        try {
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(40_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            if (session != null) {
                connection.setRequestProperty("Mcp-Session-Id", session);
            }
            try (OutputStream out = connection.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            if (code == 401) {
                throw new IOException("Eclipse a refusé le token (401) : le fichier " + tokenFile() + " a-t-il changé ?");
            }
            if (code >= 300) {
                throw new IOException("Eclipse a répondu HTTP " + code + ".");
            }
            String body = "";
            try (InputStream in = connection.getInputStream()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return new Reply(body, connection.getHeaderField("Mcp-Session-Id"));
        } finally {
            connection.disconnect();
        }
    }

    /** Corps JSON d'une réponse, qu'elle soit en JSON simple ou en flux d'événements (lignes « data: »). */
    static String payload(String body) {
        if (!body.contains("data:")) {
            return body;
        }
        StringBuilder json = new StringBuilder();
        for (String line : body.split("\\R")) {
            if (line.startsWith("data:")) {
                json.append(line.substring(5).trim());
            }
        }
        return json.toString();
    }

    /** Valeur « effective » d'une préférence dans la réponse de eclipse_get_preferences ; null si absente. */
    static String effectiveValue(String text) {
        Matcher m = EFFECTIVE.matcher(text);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }

    /** Les mêmes chemins, sans tenir compte de la casse ni de la forme (Windows). */
    static boolean samePath(Path a, Path b) {
        try {
            return a.toAbsolutePath().normalize().toString()
                    .equalsIgnoreCase(b.toAbsolutePath().normalize().toString());
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Défait les échappements JSON courants (le contenu d'un outil MCP est une chaîne JSON dans du JSON). */
    static String unescapeJson(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                default -> out.append(n);
            }
        }
        return out.toString();
    }

    private static String abbreviate(String s) {
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
    }
}
