package fr.tomcatchooser;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * État de Tomcat, déduit des ports déclarés dans server.xml : Tomcat est considéré démarré quand
 * son connecteur HTTP écoute sur localhost. Le port d'arrêt ({@code <Server port=...>}) n'est jamais
 * testé : chaque connexion y fait écrire à Tomcat « Une commande d'arrêt invalide a été reçue ».
 * Cela marche quel que soit le lanceur (Eclipse, startup.bat, service), tant qu'il utilise ce server.xml.
 * Le port d'arrêt sert seulement à {@link #shutdown()}, avec la bonne commande.
 */
public final class TomcatStatus {

    public enum State { STARTED, STOPPED }

    private static final Pattern SERVER_TAG = Pattern.compile("<Server\\b[^>]*>");
    private static final Pattern CONNECTOR_TAG = Pattern.compile("<Connector\\b[^>]*>");
    private static final Pattern PORT = Pattern.compile("\\sport\\s*=\\s*[\"'](\\d+)[\"']");
    private static final Pattern PROTOCOL = Pattern.compile("\\sprotocol\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern SHUTDOWN = Pattern.compile("\\sshutdown\\s*=\\s*[\"']([^\"']*)[\"']");

    private final int shutdownPort;
    private final int httpPort;
    private final String shutdownCommand;

    TomcatStatus(int shutdownPort, int httpPort) {
        this(shutdownPort, httpPort, "SHUTDOWN");
    }

    TomcatStatus(int shutdownPort, int httpPort, String shutdownCommand) {
        this.shutdownPort = shutdownPort;
        this.httpPort = httpPort;
        this.shutdownCommand = shutdownCommand;
    }

    /** Lit les ports dans le texte de server.xml (hors commentaires) ; -1 si absent ou désactivé. */
    public static TomcatStatus fromServerXml(String text) {
        String xml = text.replaceAll("(?s)<!--.*?-->", "");
        int shutdown = -1;
        String command = "SHUTDOWN";
        Matcher server = SERVER_TAG.matcher(xml);
        if (server.find()) {
            shutdown = port(server.group());
            Matcher m = SHUTDOWN.matcher(server.group());
            if (m.find()) {
                command = m.group(1);
            }
        }
        int http = -1;
        Matcher connector = CONNECTOR_TAG.matcher(xml);
        while (connector.find() && http < 0) {
            Matcher protocol = PROTOCOL.matcher(connector.group());
            // Pas de protocol = HTTP/1.1 par défaut ; on écarte AJP.
            if (!protocol.find() || !protocol.group(1).toUpperCase().contains("AJP")) {
                http = port(connector.group());
            }
        }
        return new TomcatStatus(shutdown, http, command);
    }

    public int httpPort() {
        return httpPort;
    }

    public int shutdownPort() {
        return shutdownPort;
    }

    public String shutdownCommand() {
        return shutdownCommand;
    }

    /**
     * Arrête Tomcat en envoyant la commande d'arrêt sur son port, comme shutdown.bat.
     * Fonctionne aussi pour un Tomcat lancé depuis Eclipse : Eclipse le voit simplement s'arrêter.
     */
    public void shutdown() throws IOException {
        shutdown(0);
    }

    /**
     * Comme {@link #shutdown()}, en réessayant pendant retryMs si le port d'arrêt refuse la connexion :
     * juste après le démarrage, Tomcat répond déjà en HTTP mais n'a pas encore ouvert ce port.
     * Bloquant : à appeler hors du thread JavaFX.
     */
    public void shutdown(long retryMs) throws IOException {
        if (shutdownPort <= 0) {
            throw new IOException("Le port d'arrêt est désactivé dans server.xml (<Server port=\"-1\">).");
        }
        long deadline = System.currentTimeMillis() + retryMs;
        while (true) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", shutdownPort), 1000);
                OutputStream out = socket.getOutputStream();
                out.write(shutdownCommand.getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                return;
            } catch (ConnectException refused) {
                if (System.currentTimeMillis() >= deadline) {
                    throw new IOException("le port d'arrêt " + shutdownPort + " ne répond pas.", refused);
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw refused;
                }
            }
        }
    }

    /** Teste les ports (bloquant, quelques centaines de ms au plus) : à appeler hors du thread JavaFX. */
    public State check() {
        return isListening(httpPort) ? State.STARTED : State.STOPPED;
    }

    static boolean isListening(int port) {
        if (port <= 0) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int port(String tag) {
        Matcher m = PORT.matcher(tag);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
