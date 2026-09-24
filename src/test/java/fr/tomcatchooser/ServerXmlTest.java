package fr.tomcatchooser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerXmlTest {

    private static final String SERVER_XML = """
            <?xml version='1.0' encoding='utf-8'?>
            <!-- Commentaire de licence -->
            <Server port="8005" shutdown="SHUTDOWN">
              <Service name="Catalina">
                <Engine name="Catalina" defaultHost="localhost">
                  <Host name="localhost" appBase="webapps">
                    <Context path="/appli1" docBase="C:/apps/appli1" reloadable="true"/>
                    <!-- <Context path="/appli2" docBase="C:/apps/appli2"/> -->
                    <!--
                    <Context path="/appli3" docBase="C:/apps/appli3">
                      <Resource name="jdbc/ds" auth="Container"/>
                    </Context>
                    -->
                    <!-- Un commentaire ordinaire -->
                    <Context path="" docBase='C:/apps/root'>
                      <Parameter name="x" value="1"/>
                    </Context>
                  </Host>
                </Engine>
              </Service>
            </Server>
            """.replace("\n", "\r\n");

    @TempDir
    Path dir;

    private Path write(String content) throws Exception {
        Path f = dir.resolve("server.xml");
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void trouveLesContextActifsEtCommentes() throws Exception {
        List<ContextEntry> c = ServerXml.load(write(SERVER_XML)).contexts();
        assertEquals(4, c.size());
        assertEquals("/appli1", c.get(0).path());
        assertFalse(c.get(0).commented());
        assertEquals("/appli2", c.get(1).path());
        assertTrue(c.get(1).commented());
        assertEquals("/appli3", c.get(2).path());
        assertTrue(c.get(2).commented());
        assertEquals("", c.get(3).path());
        assertEquals("C:/apps/root", c.get(3).docBase());
        assertEquals("/ (ROOT)", c.get(3).displayPath());
        assertFalse(c.get(3).commented());
        assertEquals(7, c.get(0).line());
    }

    @Test
    void commenterPuisDecommenterRestaureLeFichierAlIdentique() throws Exception {
        Path f = write(SERVER_XML);
        // Le bloc multiligne (index 2) est recommenté sur une seule ligne : testé à part.
        for (int i : new int[] {0, 1, 3}) {
            ServerXml xml = ServerXml.load(f);
            ContextEntry before = xml.contexts().get(i);
            ContextEntry after = xml.toggle(before);
            assertEquals(!before.commented(), after.commented());
            after = ServerXml.load(f).toggle(after);
            assertEquals(before.commented(), after.commented());
        }
        String result = Files.readString(f);
        assertEquals(SERVER_XML, result);
    }

    @Test
    void commenterUnContextActif() throws Exception {
        Path f = write(SERVER_XML);
        ServerXml xml = ServerXml.load(f);
        xml.toggle(xml.contexts().get(0));
        assertTrue(Files.readString(f).contains(
                "    <!-- <Context path=\"/appli1\" docBase=\"C:/apps/appli1\" reloadable=\"true\"/> -->\r\n"));
        assertTrue(Files.exists(dir.resolve("server.xml.bak")));
        assertTrue(Files.exists(dir.resolve("server.xml.orig")));
        assertEquals(SERVER_XML, Files.readString(dir.resolve("server.xml.bak")));
    }

    @Test
    void decommenterUnBlocMultiligneSupprimeLesLignesDeCommentaire() throws Exception {
        Path f = write(SERVER_XML);
        ServerXml xml = ServerXml.load(f);
        xml.toggle(xml.contexts().get(2));
        String expected = """
                    <!-- <Context path="/appli2" docBase="C:/apps/appli2"/> -->
                    <Context path="/appli3" docBase="C:/apps/appli3">
                      <Resource name="jdbc/ds" auth="Container"/>
                    </Context>
                    <!-- Un commentaire ordinaire -->
            """.replace("\n", "\r\n");
        assertTrue(Files.readString(f).contains(expected), Files.readString(f));
        assertFalse(ServerXml.load(f).contexts().get(2).commented());

        // Recommenté : une seule ligne de commentaire, toujours reconnu comme Context commenté.
        xml = ServerXml.load(f);
        xml.toggle(xml.contexts().get(2));
        assertTrue(ServerXml.load(f).contexts().get(2).commented());
    }

    @Test
    void refuseDeCommenterUnContextContenantUnCommentaire() throws Exception {
        String xmlText = SERVER_XML.replace("<Parameter name=\"x\" value=\"1\"/>",
                "<!-- note --><Parameter name=\"x\" value=\"1\"/>");
        Path f = write(xmlText);
        ServerXml xml = ServerXml.load(f);
        // le commentaire interne est vu à part, le Context racine reste détecté
        ContextEntry root = xml.contexts().get(3);
        assertThrows(IllegalStateException.class, () -> xml.toggle(root));
        assertEquals(xmlText, Files.readString(f));
    }

    @Test
    void detecteUneModificationExterne() throws Exception {
        Path f = write(SERVER_XML);
        ServerXml xml = ServerXml.load(f);
        ContextEntry first = xml.contexts().get(0);
        Files.writeString(f, SERVER_XML.replace("/appli1", "/autre"));
        assertThrows(java.io.IOException.class, () -> xml.toggle(first));
    }

    @Test
    void conserveLeBomEtLencodage() throws Exception {
        Path f = dir.resolve("server.xml");
        String latin = SERVER_XML.replace("utf-8", "ISO-8859-1").replace("/appli1", "/télé");
        byte[] body = latin.getBytes(StandardCharsets.ISO_8859_1);
        Files.write(f, body);
        ServerXml xml = ServerXml.load(f);
        assertEquals("/télé", xml.contexts().get(0).path());
        xml.toggle(xml.contexts().get(0));
        xml.toggle(xml.contexts().get(0));
        assertEquals(latin, new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1));
    }

    private static final String LIGNE_PAR_LIGNE = """
            <?xml version='1.0' encoding='utf-8'?>
            <Server port="8005" shutdown="SHUTDOWN">
              <Service name="Catalina">
                <Engine name="Catalina" defaultHost="localhost">
                  <Host name="localhost" appBase="webapps">
            <!-- <Context path="/Sireo_CG44" reloadable="false" docBase="C:\\eclipse\\workspace\\Sireo_CG44\\src\\main\\webapp" workDir="C:\\eclipse\\workspace\\Sireo_CG44\\work" > -->
            \t<!-- <Logger className="org.apache.catalina.logger.SystemOutLogger" verbosity="4" timestamp="true"/> -->
            \t<!-- <Loader className="org.apache.catalina.loader.DevLoader" reloadable="true" debug="1" useSystemClassLoaderAsParent="false" /> -->
            <!-- </Context> -->
                    <Context path="/autre" docBase="C:/apps/autre"/>
                  </Host>
                </Engine>
              </Service>
            </Server>
            """.replace("\n", "\r\n");

    @Test
    void litUnContextCommenteLigneParLigne() throws Exception {
        Path f = write(LIGNE_PAR_LIGNE);
        ServerXml xml = ServerXml.load(f);
        List<ContextEntry> c = xml.contexts();
        assertEquals(2, c.size());
        assertEquals("/Sireo_CG44", c.get(0).path());
        assertEquals("C:\\eclipse\\workspace\\Sireo_CG44\\src\\main\\webapp", c.get(0).docBase());
        assertTrue(c.get(0).commented());
        assertEquals(6, c.get(0).line());
        assertEquals("/autre", c.get(1).path());

        xml.toggle(c.get(0));
        String active = Files.readString(f);
        assertTrue(active.contains("""
                <Context path="/Sireo_CG44" reloadable="false" docBase="C:\\eclipse\\workspace\\Sireo_CG44\\src\\main\\webapp" workDir="C:\\eclipse\\workspace\\Sireo_CG44\\work" >
                \t<Logger className="org.apache.catalina.logger.SystemOutLogger" verbosity="4" timestamp="true"/>
                \t<Loader className="org.apache.catalina.loader.DevLoader" reloadable="true" debug="1" useSystemClassLoaderAsParent="false" />
                </Context>
                """.replace("\n", "\r\n")), active);
        assertFalse(ServerXml.load(f).contexts().get(0).commented());

        // Recommenter englobe tout le bloc dans un seul commentaire.
        xml = ServerXml.load(f);
        xml.toggle(xml.contexts().get(0));
        String recommented = Files.readString(f);
        assertTrue(recommented.contains("<!-- <Context path=\"/Sireo_CG44\""), recommented);
        assertTrue(recommented.contains("useSystemClassLoaderAsParent=\"false\" />\r\n</Context> -->"), recommented);
        assertTrue(ServerXml.load(f).contexts().get(0).commented());
    }
}
