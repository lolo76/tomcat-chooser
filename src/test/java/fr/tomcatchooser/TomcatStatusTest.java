package fr.tomcatchooser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.ServerSocket;

import org.junit.jupiter.api.Test;

class TomcatStatusTest {

    @Test
    void litLesPortsEnIgnorantCommentairesEtAjp() {
        TomcatStatus status = TomcatStatus.fromServerXml("""
                <Server port="8005" shutdown="SHUTDOWN">
                  <Service name="Catalina">
                    <!-- <Connector port="9999" protocol="HTTP/1.1"/> -->
                    <Connector port="8009" protocol="AJP/1.3" redirectPort="8443" />
                    <Connector port="8080" protocol="HTTP/1.1" connectionTimeout="20000"/>
                  </Service>
                </Server>
                """);
        assertEquals(8005, status.shutdownPort());
        assertEquals(8080, status.httpPort());
    }

    @Test
    void envoieLaCommandeDArretDeServerXml() throws Exception {
        try (ServerSocket fake = new ServerSocket(0)) {
            TomcatStatus status = TomcatStatus.fromServerXml(
                    "<Server port=\"" + fake.getLocalPort() + "\" shutdown=\"STOP-ICI\"><Connector port=\"1\"/></Server>");
            assertEquals("STOP-ICI", status.shutdownCommand());
            status.shutdown();
            try (java.net.Socket received = fake.accept()) {
                assertEquals("STOP-ICI", new String(received.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.ISO_8859_1));
            }
        }
    }

    @Test
    void attendQueLePortDArretSOuvre() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        TomcatStatus status = new TomcatStatus(port, -1, "SHUTDOWN");
        java.util.concurrent.CompletableFuture<String> received = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(700); // Tomcat ouvre son port d'arrêt un peu après son port HTTP
                try (ServerSocket late = new ServerSocket(port); java.net.Socket c = late.accept()) {
                    return new String(c.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        status.shutdown(5000);
        assertEquals("SHUTDOWN", received.get());
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> status.shutdown(300));
    }

    @Test
    void demarreQuandLePortHttpEcoute() throws Exception {
        try (ServerSocket fake = new ServerSocket(0)) {
            int port = fake.getLocalPort();
            assertEquals(TomcatStatus.State.STARTED, new TomcatStatus(-1, port).check());
            // Le port d'arrêt n'est jamais testé.
            assertEquals(TomcatStatus.State.STOPPED, new TomcatStatus(port, -1).check());
        }
        int free;
        try (ServerSocket s = new ServerSocket(0)) {
            free = s.getLocalPort();
        }
        assertEquals(TomcatStatus.State.STOPPED, new TomcatStatus(-1, free).check());
    }
}
