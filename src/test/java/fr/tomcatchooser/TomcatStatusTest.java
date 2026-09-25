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
    void demarreQuandUnPortEcoute() throws Exception {
        try (ServerSocket fake = new ServerSocket(0)) {
            int port = fake.getLocalPort();
            assertEquals(TomcatStatus.State.STARTED, new TomcatStatus(port, -1).check());
            assertEquals(TomcatStatus.State.STARTED, new TomcatStatus(-1, port).check());
        }
        int free;
        try (ServerSocket s = new ServerSocket(0)) {
            free = s.getLocalPort();
        }
        assertEquals(TomcatStatus.State.STOPPED, new TomcatStatus(free, -1).check());
    }
}
