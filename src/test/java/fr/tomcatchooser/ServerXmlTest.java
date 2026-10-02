package fr.tomcatchooser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
        assertEquals("ROOT", c.get(3).displayPath());
        assertEquals("appli1", c.get(0).displayPath());
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
        assertFalse(Files.exists(dir.resolve("server.xml.bak")));
        assertEquals(SERVER_XML, Files.readString(dir.resolve("server.xml.orig")));
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

    @Test
    void litLesAttributsDuContext() throws Exception {
        ServerXml xml = ServerXml.load(write(SERVER_XML));
        Map<String, String> attrs = xml.attributes(xml.contexts().get(0));
        assertEquals(List.of("path", "docBase", "reloadable"), List.copyOf(attrs.keySet()));
        assertEquals("C:/apps/appli1", attrs.get("docBase"));
        // Context commenté sur plusieurs lignes
        assertEquals("C:/apps/appli3", xml.attributes(xml.contexts().get(2)).get("docBase"));
    }

    @Test
    void modifieSupprimeEtAjouteDesAttributs() throws Exception {
        Path f = write(SERVER_XML);
        ServerXml xml = ServerXml.load(f);
        Map<String, String> attrs = xml.attributes(xml.contexts().get(0));
        attrs.put("docBase", "D:/nouveau & <test>");
        attrs.remove("reloadable");
        attrs.put("workDir", "D:/work");
        xml.updateAttributes(xml.contexts().get(0), attrs);
        assertTrue(Files.readString(f).contains(
                "    <Context path=\"/appli1\" docBase=\"D:/nouveau &amp; &lt;test>\" workDir=\"D:/work\"/>\r\n"),
                Files.readString(f));
        ServerXml reloaded = ServerXml.load(f);
        assertEquals("D:/nouveau & <test>", reloaded.attributes(reloaded.contexts().get(0)).get("docBase"));
        // le reste du fichier est intact
        assertEquals(SERVER_XML.replace(" docBase=\"C:/apps/appli1\" reloadable=\"true\"/>",
                " docBase=\"D:/nouveau &amp; &lt;test>\" workDir=\"D:/work\"/>"), Files.readString(f));
    }

    @Test
    void modifieUnContextCommenteLigneParLigne() throws Exception {
        Path f = write(LIGNE_PAR_LIGNE);
        ServerXml xml = ServerXml.load(f);
        Map<String, String> attrs = xml.attributes(xml.contexts().get(0));
        assertEquals("false", attrs.get("reloadable"));
        attrs.put("reloadable", "true");
        xml.updateAttributes(xml.contexts().get(0), attrs);
        String result = Files.readString(f);
        assertEquals(LIGNE_PAR_LIGNE.replace("reloadable=\"false\" docBase", "reloadable=\"true\" docBase"), result);
        assertTrue(ServerXml.load(f).contexts().get(0).commented());
    }

    @Test
    void refuseUnNomDAttributInvalide() throws Exception {
        Path f = write(SERVER_XML);
        ServerXml xml = ServerXml.load(f);
        Map<String, String> attrs = xml.attributes(xml.contexts().get(0));
        attrs.put("mauvais nom", "x");
        assertThrows(IllegalArgumentException.class, () -> xml.updateAttributes(xml.contexts().get(0), attrs));
        assertEquals(SERVER_XML, Files.readString(f));
    }

    @Test
    void modifieLoggerEtLoader() throws Exception {
        Path f = write(LIGNE_PAR_LIGNE);
        ServerXml xml = ServerXml.load(f);
        ContextEntry sireo = xml.contexts().get(0);
        Map<String, String> logger = xml.attributes(sireo, "Logger");
        Map<String, String> loader = xml.attributes(sireo, "Loader");
        assertEquals("4", logger.get("verbosity"));
        assertEquals("org.apache.catalina.loader.DevLoader", loader.get("className"));
        assertEquals(null, xml.attributes(xml.contexts().get(1), "Loader"));

        logger.put("verbosity", "2");
        loader.put("debug", "0");
        xml.updateTags(sireo, Map.of("Logger", logger, "Loader", loader));
        assertEquals(LIGNE_PAR_LIGNE.replace("verbosity=\"4\"", "verbosity=\"2\"").replace("debug=\"1\"", "debug=\"0\""),
                Files.readString(f));
    }

    @Test
    void ajouteUnContextApresLeDernier() throws Exception {
        Path f = write(SERVER_XML);
        ServerXml xml = ServerXml.load(f);
        Map<String, String> attrs = new java.util.LinkedHashMap<>();
        attrs.put("path", "nouvelle");
        attrs.put("docBase", "C:/apps/nouvelle & co");
        attrs.put("reloadable", "true");
        attrs.put("workDir", "");
        ContextEntry added = xml.addContext(attrs);

        assertEquals("/nouvelle", added.path());
        assertFalse(added.commented());
        assertEquals(5, xml.contexts().size());
        String text = Files.readString(f);
        assertTrue(text.contains("        </Context>\r\n        <Context path=\"/nouvelle\" "
                + "docBase=\"C:/apps/nouvelle &amp; co\" reloadable=\"true\"/>\r\n      </Host>"), text);
        // Le nouveau Context se désactive et se réactive comme les autres.
        ContextEntry off = xml.toggle(added);
        assertTrue(off.commented());
        ServerXml reloaded = ServerXml.load(f);
        assertEquals("C:/apps/nouvelle & co", reloaded.attributes(reloaded.contexts().get(4)).get("docBase"));
    }

    @Test
    void ajouteAvantHostSansAucunContext() throws Exception {
        Path f = write("""
                <Server port="8005">
                  <Service name="Catalina">
                    <Engine name="Catalina" defaultHost="localhost">
                      <Host name="localhost" appBase="webapps">
                        <!-- </Host> dans un commentaire -->
                      </Host>
                    </Engine>
                  </Service>
                </Server>
                """);
        ServerXml xml = ServerXml.load(f);
        xml.addContext(Map.of("path", "", "docBase", "/srv/root"));
        assertEquals(1, xml.contexts().size());
        assertEquals("ROOT", xml.contexts().get(0).displayPath());
        assertTrue(Files.readString(f).contains(
                "-->\n        <Context path=\"\" docBase=\"/srv/root\"/>\n      </Host>"), Files.readString(f));
    }

    @Test
    void dupliqueUnContextCommenteAvecSesEnfants() throws Exception {
        Path f = write(LIGNE_PAR_LIGNE);
        ServerXml xml = ServerXml.load(f);
        ContextEntry sireo = xml.contexts().get(0);
        Map<String, String> context = xml.attributes(sireo, "Context");
        Map<String, String> loader = xml.attributes(sireo, "Loader");
        context.put("path", "Sireo_CG44_copie");
        loader.put("debug", "0");
        ContextEntry copy = xml.duplicateContext(sireo, Map.of("Context", context, "Loader", loader));

        assertEquals("/Sireo_CG44_copie", copy.path());
        assertFalse(copy.commented());
        assertEquals(3, xml.contexts().size());
        String text = Files.readString(f);
        // L'original reste commenté et intact ; la copie, active, suit le dernier Context.
        assertTrue(text.startsWith(LIGNE_PAR_LIGNE.substring(0, LIGNE_PAR_LIGNE.indexOf("        <Context path=\"/autre\""))), text);
        assertTrue(text.contains("""
                        <Context path="/autre" docBase="C:/apps/autre"/>
                        <Context path="/Sireo_CG44_copie" reloadable="false" docBase="C:\\eclipse\\workspace\\Sireo_CG44\\src\\main\\webapp" workDir="C:\\eclipse\\workspace\\Sireo_CG44\\work" >
                \t<Logger className="org.apache.catalina.logger.SystemOutLogger" verbosity="4" timestamp="true"/>
                \t<Loader className="org.apache.catalina.loader.DevLoader" reloadable="true" debug="0" useSystemClassLoaderAsParent="false" />
                </Context>
                      </Host>""".replace("\n", "\r\n")), text);
    }

    @Test
    void dupliqueUnContextActifEtRefuseUnPathExistant() throws Exception {
        Path f = write(SERVER_XML);
        ServerXml xml = ServerXml.load(f);
        ContextEntry root = xml.contexts().get(3);
        Map<String, String> context = xml.attributes(root, "Context");
        assertThrows(java.io.IOException.class, () -> xml.duplicateContext(root, Map.of("Context", context)));
        context.put("path", "/copie");
        Map<String, Map<String, String>> byTag = new java.util.HashMap<>();
        byTag.put("Context", context);
        byTag.put("Loader", null);
        xml.duplicateContext(root, byTag);
        assertTrue(Files.readString(f).contains("        </Context>\r\n"
                + "        <Context path=\"/copie\" docBase='C:/apps/root'>\r\n"
                + "          <Parameter name=\"x\" value=\"1\"/>\r\n"
                + "        </Context>\r\n"
                + "      </Host>"), Files.readString(f));
    }

    @Test
    void ajouteUnContextAvecSonLoader() throws Exception {
        Path f = write(LIGNE_PAR_LIGNE);
        ServerXml xml = ServerXml.load(f);
        Map<String, String> context = new java.util.LinkedHashMap<>();
        context.put("path", "nouveau");
        context.put("reloadable", "false");
        context.put("docBase", "C:/apps/nouveau");
        context.put("workDir", "");
        Map<String, String> loader = new java.util.LinkedHashMap<>();
        loader.put("className", "org.apache.catalina.loader.DevLoader");
        loader.put("reloadable", "true");
        ContextEntry added = xml.addContextTags(Map.of("Context", context, "Loader", loader));
        assertEquals("/nouveau", added.path());
        assertTrue(Files.readString(f).contains("        <Context path=\"/autre\" docBase=\"C:/apps/autre\"/>\r\n"
                + "        <Context path=\"/nouveau\" reloadable=\"false\" docBase=\"C:/apps/nouveau\">\r\n"
                + "        \t<Loader className=\"org.apache.catalina.loader.DevLoader\" reloadable=\"true\"/>\r\n"
                + "        </Context>\r\n"
                + "      </Host>"), Files.readString(f));
        assertEquals("true", ServerXml.load(f).attributes(added, "Loader").get("reloadable"));
    }

    @Test
    void supprimeUnContextActifOuCommente() throws Exception {
        Path f = write(SERVER_XML);
        ServerXml xml = ServerXml.load(f);
        xml.deleteContext(xml.contexts().get(0));
        assertEquals(SERVER_XML.replace("        <Context path=\"/appli1\" docBase=\"C:/apps/appli1\" reloadable=\"true\"/>\r\n", ""),
                Files.readString(f));
        xml.deleteContext(xml.contexts().get(1)); // appli3, commenté sur plusieurs lignes
        assertFalse(Files.readString(f).contains("appli3"), Files.readString(f));
        assertTrue(Files.readString(f).contains("        <!-- <Context path=\"/appli2\" docBase=\"C:/apps/appli2\"/> -->\r\n"
                + "        <!-- Un commentaire ordinaire -->"), Files.readString(f));
        assertEquals(2, ServerXml.load(f).contexts().size());

        Path g = write(LIGNE_PAR_LIGNE);
        ServerXml lignes = ServerXml.load(g);
        lignes.deleteContext(lignes.contexts().get(0));
        assertFalse(Files.readString(g).contains("Sireo_CG44"), Files.readString(g));
        assertFalse(Files.readString(g).contains("Loader"), Files.readString(g));
    }

    @Test
    void refuseUnDoublonOuSansDocBase() throws Exception {
        ServerXml xml = ServerXml.load(write(SERVER_XML));
        assertThrows(java.io.IOException.class, () -> xml.addContext(Map.of("path", "/appli2", "docBase", "x")));
        assertThrows(java.io.IOException.class, () -> xml.addContext(Map.of("path", "/autre", "docBase", " ")));
        assertEquals(4, xml.contexts().size());
    }
}
