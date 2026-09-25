package fr.tomcatchooser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * État de Tomcat, déduit des ports déclarés dans server.xml : Tomcat est considéré démarré quand
 * son port d'arrêt ({@code <Server port=...>}) ou son connecteur HTTP écoute sur localhost.
 * Cela marche quel que soit le lanceur (Eclipse, startup.bat, service), tant qu'il utilise ce server.xml.
 */
public final class TomcatStatus {

    public enum State { STARTED, STOPPED }

    private static final Pattern SERVER_TAG = Pattern.compile("<Server\\b[^>]*>");
    private static final Pattern CONNECTOR_TAG = Pattern.compile("<Connector\\b[^>]*>");
    private static final Pattern PORT = Pattern.compile("\\sport\\s*=\\s*[\"'](\\d+)[\"']");
    private static final Pattern PROTOCOL = Pattern.compile("\\sprotocol\\s*=\\s*[\"']([^\"']*)[\"']");

    private final int shutdownPort;
    private final int httpPort;

    TomcatStatus(int shutdownPort, int httpPort) {
        this.shutdownPort = shutdownPort;
        this.httpPort = httpPort;
    }

    /** Lit les ports dans le texte de server.xml (hors commentaires) ; -1 si absent ou désactivé. */
    public static TomcatStatus fromServerXml(String text) {
        String xml = text.replaceAll("(?s)<!--.*?-->", "");
        int shutdown = -1;
        Matcher server = SERVER_TAG.matcher(xml);
        if (server.find()) {
            shutdown = port(server.group());
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
        return new TomcatStatus(shutdown, http);
    }

    public int httpPort() {
        return httpPort;
    }

    public int shutdownPort() {
        return shutdownPort;
    }

    /** Teste les ports (bloquant, quelques centaines de ms au plus) : à appeler hors du thread JavaFX. */
    public State check() {
        return isListening(shutdownPort) || isListening(httpPort) ? State.STARTED : State.STOPPED;
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
