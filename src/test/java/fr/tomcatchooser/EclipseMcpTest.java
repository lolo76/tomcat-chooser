package fr.tomcatchooser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Client du plugin Eclipse MCP Server, essayé contre un faux serveur MCP en local. */
class EclipseMcpTest {

    private static final String CRLF = "\r\n";

    @TempDir
    Path dir;

    private ServerSocket server;
    private int port;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile String toolText = "{}";
    private volatile int status = 200;

    /** Petit serveur HTTP en local (sans com.sun.net.httpserver, hors du module) : répond comme le plugin. */
    @BeforeEach
    void start() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        port = server.getLocalPort();
        Thread thread = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    answer(socket);
                } catch (IOException e) {
                    // serveur fermé ou connexion coupée : rien à faire
                }
            }
        }, "faux-mcp");
        thread.setDaemon(true);
        thread.start();
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
    }

    private void answer(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        StringBuilder head = new StringBuilder();
        int c = 0;
        while (!head.toString().endsWith(CRLF + CRLF) && (c = in.read()) >= 0) {
            head.append((char) c);
        }
        if (c < 0) {
            return; // simple test de connexion (connect) : pas de requête HTTP
        }
        int length = 0;
        String authorization = null;
        for (String line : head.toString().split(CRLF)) {
            String lower = line.toLowerCase();
            if (lower.startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(15).trim());
            } else if (lower.startsWith("authorization:")) {
                authorization = line.substring(14).trim();
            }
        }
        String body = new String(in.readNBytes(length), StandardCharsets.UTF_8);
        requests.add(authorization + " | " + body);

        int code = status;
        String headers = "";
        String content = "";
        if (code == 200) {
            if (body.contains("\"method\":\"notifications/initialized\"")) {
                code = 202;
            } else if (body.contains("\"method\":\"initialize\"")) {
                headers = "Content-Type: application/json" + CRLF + "Mcp-Session-Id: S1" + CRLF;
                content = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";
            } else {
                String escaped = toolText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
                // Réponse en flux d'événements, comme le vrai plugin.
                headers = "Content-Type: text/event-stream" + CRLF;
                content = "id: 1\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"" + escaped + "\"}]}}\n\n";
            }
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String reply = "HTTP/1.1 " + code + " X" + CRLF + headers + "Content-Length: " + bytes.length + CRLF
                + "Connection: close" + CRLF + CRLF;
        OutputStream out = socket.getOutputStream();
        out.write(reply.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private EclipseMcp client() throws IOException {
        Path token = dir.resolve("token");
        Files.writeString(token, "secret-123\n");
        EclipseMcp mcp = EclipseMcp.connect(token, port);
        assertNotNull(mcp);
        return mcp;
    }

    @Test
    void pasDeClientSansTokenOuSansServeur() throws Exception {
        assertNull(EclipseMcp.connect(dir.resolve("absent"), port));
        Path token = dir.resolve("token");
        Files.writeString(token, "x");
        int closed;
        try (ServerSocket s = new ServerSocket(0)) {
            closed = s.getLocalPort();
        }
        assertNull(EclipseMcp.connect(token, closed));
    }

    @Test
    void lancheUneCommandeAvecLeToken() throws Exception {
        toolText = "{\n  \"executed\": true,\n  \"outcome\": \"success\"\n}";
        client().runCommand(EclipseMcp.SYSDEO_START);
        assertEquals(3, requests.size(), requests.toString());
        assertTrue(requests.stream().allMatch(r -> r.startsWith("Bearer secret-123 | ")), requests.toString());
        String call = requests.get(2);
        assertTrue(call.contains("\"name\":\"eclipse_run_workbench_command\""), call);
        assertTrue(call.contains("com.sysdeo.eclipse.tomcat.start"), call);
    }

    @Test
    void refuseUneCommandeNonExecutee() throws Exception {
        toolText = "{\"executed\": false}";
        assertThrows(IOException.class, () -> client().runCommand(EclipseMcp.SYSDEO_STOP));
        toolText = "{\"executed\": true, \"outcome\": \"error\"}";
        assertThrows(IOException.class, () -> client().runCommand(EclipseMcp.SYSDEO_STOP));
    }

    @Test
    void signaleUnTokenRefuse() throws Exception {
        status = 401;
        IOException e = assertThrows(IOException.class, () -> client().runCommand(EclipseMcp.SYSDEO_START));
        assertTrue(e.getMessage().contains("401"), e.getMessage());
    }

    @Test
    void reconnaitLeServeurQuePiloteSysdeo() throws Exception {
        toolText = "{\"preferences\": [{\"key\": \"tomcatConfigFile\", \"effective\": \"C:\\\\Tomcat70\\\\conf\\\\server.xml\"}]}";
        EclipseMcp mcp = client();
        assertTrue(mcp.drives(Paths.get("C:\\Tomcat70\\conf\\server.xml")));
        assertTrue(mcp.drives(Paths.get("c:/tomcat70/conf/../conf/server.xml")));
        assertFalse(mcp.drives(Paths.get("C:\\Tomcat90\\conf\\server.xml")));
        toolText = "{\"preferences\": []}";
        assertFalse(mcp.drives(Paths.get("C:\\Tomcat70\\conf\\server.xml")));
    }

    @Test
    void analyseLesReponsesEtLesChemins() {
        assertEquals("{\"a\":1}", EclipseMcp.payload("id: 1\nevent: message\ndata: {\"a\":1}\n\n"));
        assertEquals("{\"a\":1}", EclipseMcp.payload("{\"a\":1}"));
        assertEquals("C:\\x\\y", EclipseMcp.effectiveValue("\"effective\": \"C:\\\\x\\\\y\""));
        assertNull(EclipseMcp.effectiveValue("{}"));
        assertEquals("a\\\"b\\\\c", EclipseMcp.escape("a\"b\\c"));
    }
}
