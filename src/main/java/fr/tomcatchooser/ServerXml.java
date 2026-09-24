package fr.tomcatchooser;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Lecture et modification textuelle de server.xml.
 * <p>
 * Le fichier est traité comme du texte : seule la zone du Context basculé change,
 * le reste (indentation, commentaires, fins de ligne, encodage) est conservé à l'identique.
 */
public final class ServerXml {

    private static final Pattern ENCODING_DECL =
            Pattern.compile("<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");
    /** Attribut dans une balise : groupe 1 = nom, 2 = valeur avec guillemets, 3 ou 4 = valeur. */
    private static final Pattern ATTRIBUTE =
            Pattern.compile("\\s([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");
    private static final Pattern CLOSING_CONTEXT = Pattern.compile("</Context\\s*>");
    private static final Pattern COMMENT = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final Path file;
    private Charset charset;
    private boolean bom;
    private String text;
    private List<ContextEntry> contexts;

    private ServerXml(Path file) {
        this.file = file;
    }

    public static ServerXml load(Path file) throws IOException {
        ServerXml xml = new ServerXml(file);
        xml.reload();
        return xml;
    }

    public Path file() {
        return file;
    }

    public String text() {
        return text;
    }

    public List<ContextEntry> contexts() {
        return contexts;
    }

    /** Relit le fichier depuis le disque. */
    public void reload() throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        bom = bytes.length >= 3 && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2];
        int offset = bom ? 3 : 0;
        charset = bom ? StandardCharsets.UTF_8 : detectCharset(bytes);
        text = new String(bytes, offset, bytes.length - offset, charset);
        contexts = scan(text);
    }

    /**
     * Commente ou décommente le Context donné, puis enregistre le fichier
     * (avec une copie server.xml.orig la première fois).
     *
     * @return le Context dans son nouvel état
     */
    public ContextEntry toggle(ContextEntry entry) throws IOException {
        ContextEntry current = reloadAndCheck(entry);
        String newText = current.commented() ? uncomment(text, current) : comment(text, current);
        validateXml(newText);
        save(newText);
        return contexts.get(entry.index());
    }

    /** Attributs de la balise {@code <Context>}, dans l'ordre du fichier (valeurs décodées). */
    public Map<String, String> attributes(ContextEntry entry) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = ATTRIBUTE.matcher(openingTag(text, entry));
        while (m.find()) {
            result.put(m.group(1), unescape(m.group(3) != null ? m.group(3) : m.group(4)));
        }
        return result;
    }

    /**
     * Remplace les attributs de la balise {@code <Context>} (actif ou commenté), puis enregistre.
     * Les attributs inchangés gardent leur texte exact ; les nouveaux sont ajoutés à la fin de la balise.
     *
     * @return le Context mis à jour
     */
    public ContextEntry updateAttributes(ContextEntry entry, Map<String, String> newAttributes) throws IOException {
        for (String name : newAttributes.keySet()) {
            if (!name.matches("[A-Za-z_:][A-Za-z0-9_:.-]*")) {
                throw new IllegalArgumentException("Nom d'attribut invalide : \"" + name + "\"");
            }
        }
        ContextEntry current = reloadAndCheck(entry);
        int tagStart = openingTagStart(text, current);
        String tag = openingTag(text, current);
        String newTag = rewriteTag(tag, newAttributes);
        String newText = text.substring(0, tagStart) + newTag + text.substring(tagStart + tag.length());
        validateXml(newText);
        save(newText);
        return contexts.get(entry.index());
    }

    /** Relit le fichier et vérifie que le Context n'a pas changé depuis l'affichage. */
    private ContextEntry reloadAndCheck(ContextEntry entry) throws IOException {
        // Le fichier a pu être modifié à la main entre-temps : on relit et on vérifie.
        reload();
        if (entry.index() >= contexts.size()
                || !contexts.get(entry.index()).rawText().equals(entry.rawText())) {
            throw new IOException("Le fichier a été modifié en dehors de l'application. "
                    + "La liste vient d'être rechargée, réessayez.");
        }
        return contexts.get(entry.index());
    }

    // ---------------------------------------------------------------- édition

    private static int openingTagStart(String text, ContextEntry e) {
        return e.start() + findContextTag(text.substring(e.start(), e.end()), 0);
    }

    private static String openingTag(String text, ContextEntry e) {
        int start = openingTagStart(text, e);
        return text.substring(start, openTagEnd(text, start));
    }

    static String rewriteTag(String tag, Map<String, String> newAttributes) {
        Map<String, String> remaining = new LinkedHashMap<>(newAttributes);
        StringBuilder out = new StringBuilder();
        Matcher m = ATTRIBUTE.matcher(tag);
        int last = 0;
        while (m.find()) {
            String name = m.group(1);
            String oldValue = unescape(m.group(3) != null ? m.group(3) : m.group(4));
            out.append(tag, last, m.start());
            if (!remaining.containsKey(name)) {
                // attribut supprimé : on retire aussi l'espace qui le précède
            } else if (remaining.get(name).equals(oldValue)) {
                out.append(m.group());
            } else {
                String quote = m.group(3) != null ? "\"" : "'";
                out.append(m.group(), 0, m.start(2) - m.start())
                        .append(quote).append(escape(remaining.get(name), quote)).append(quote);
            }
            remaining.remove(name);
            last = m.end();
        }
        String rest = tag.substring(last);
        // rest = fin de balise : blancs éventuels puis ">" ou "/>" ; les nouveaux attributs vont avant.
        int insert = rest.endsWith("/>") ? rest.length() - 2 : rest.length() - 1;
        while (insert > 0 && Character.isWhitespace(rest.charAt(insert - 1))) {
            insert--;
        }
        out.append(rest, 0, insert);
        remaining.forEach((name, value) ->
                out.append(' ').append(name).append("=\"").append(escape(value, "\"")).append('"'));
        out.append(rest.substring(insert));
        return out.toString();
    }

    private static String escape(String value, String quote) {
        String v = value.replace("&", "&amp;").replace("<", "&lt;");
        return quote.equals("\"") ? v.replace("\"", "&quot;") : v.replace("'", "&apos;");
    }

    private static String unescape(String value) {
        return value.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&");
    }

    static String comment(String text, ContextEntry e) {
        String element = text.substring(e.start(), e.end());
        if (element.contains("--")) {
            throw new IllegalStateException("Le Context " + e.displayPath()
                    + " contient \"--\" (par exemple un commentaire interne) : "
                    + "un commentaire XML ne peut pas l'englober. Modifiez-le à la main.");
        }
        return text.substring(0, e.start()) + "<!-- " + element + " -->" + text.substring(e.end());
    }

    static String uncomment(String text, ContextEntry e) {
        String region = text.substring(e.start(), e.end());
        if (region.indexOf("<!--", 4) >= 0) {
            // Bloc commenté ligne par ligne : on retire les marques de chaque commentaire.
            Matcher m = COMMENT.matcher(region);
            StringBuilder out = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(out, Matcher.quoteReplacement(stripHorizontal(m.group(1))));
            }
            m.appendTail(out);
            return text.substring(0, e.start()) + out + text.substring(e.end());
        }
        int start = e.start();
        int end = e.end();
        String inner = text.substring(start + 4, end - 3);

        // "<!--" seul sur sa ligne : on supprime toute la ligne d'ouverture.
        int lead = leadingWhitespace(inner);
        int lineStart = text.lastIndexOf('\n', start - 1) + 1;
        if (inner.substring(0, lead).contains("\n") && text.substring(lineStart, start).isBlank()) {
            start = lineStart;
            inner = inner.substring(inner.indexOf('\n') + 1);
        } else {
            inner = inner.substring(horizontalWhitespace(inner, 0));
        }

        // "-->" seul sur sa ligne : on supprime toute la ligne de fermeture.
        int trail = inner.length() - trailingWhitespace(inner);
        int lineEnd = text.indexOf('\n', end);
        String afterComment = lineEnd < 0 ? text.substring(end) : text.substring(end, lineEnd);
        if (inner.substring(trail).contains("\n") && afterComment.isBlank()) {
            end = lineEnd < 0 ? text.length() : lineEnd + 1;
            inner = inner.substring(0, inner.lastIndexOf('\n') + 1);
        } else {
            int k = inner.length();
            while (k > 0 && (inner.charAt(k - 1) == ' ' || inner.charAt(k - 1) == '\t')) {
                k--;
            }
            inner = inner.substring(0, k);
        }
        return text.substring(0, start) + inner + text.substring(end);
    }

    private void save(String newText) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        String name = file.getFileName().toString();
        Path orig = dir.resolve(name + ".orig");
        if (!Files.exists(orig)) {
            Files.copy(file, orig);
        }

        byte[] body = newText.getBytes(charset);
        byte[] out = body;
        if (bom) {
            out = new byte[body.length + 3];
            System.arraycopy(UTF8_BOM, 0, out, 0, 3);
            System.arraycopy(body, 0, out, 3, body.length);
        }
        Path tmp = dir.resolve(name + ".tmp");
        Files.write(tmp, out);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        text = newText;
        contexts = scan(text);
    }

    /** Refuse d'écrire un fichier qui ne serait plus du XML bien formé. */
    static void validateXml(String xml) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException e) throws SAXParseException {
                    throw e;
                }
            });
            builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new IOException("La modification rendrait server.xml invalide, rien n'a été écrit : "
                    + ex.getMessage(), ex);
        }
    }

    // ---------------------------------------------------------------- analyse

    static List<ContextEntry> scan(String text) {
        List<ContextEntry> result = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int comment = text.indexOf("<!--", i);
            int context = findContextTag(text, i);
            if (comment < 0 && context < 0) {
                break;
            }
            if (comment >= 0 && (context < 0 || comment < context)) {
                int close = text.indexOf("-->", comment + 4);
                if (close < 0) {
                    break;
                }
                int end = close + 3;
                String inner = text.substring(comment + 4, close).strip();
                if (findContextTag(inner, 0) == 0 && elementEnd(inner, 0) == inner.length()) {
                    result.add(entry(result.size(), text, comment, end, true, inner));
                } else if (findContextTag(inner, 0) == 0 && openTagEnd(inner, 0) == inner.length()) {
                    // Context commenté ligne par ligne : <!-- <Context ...> --> ... <!-- </Context> -->
                    int blockEnd = lineByLineBlockEnd(text, end);
                    if (blockEnd > 0) {
                        result.add(entry(result.size(), text, comment, blockEnd, true, inner));
                        end = blockEnd;
                    }
                }
                i = end;
            } else {
                int end = elementEnd(text, context);
                if (end < 0) {
                    break;
                }
                result.add(entry(result.size(), text, context, end, false, text.substring(context, end)));
                i = end;
            }
        }
        return List.copyOf(result);
    }

    /**
     * À partir de from (juste après le commentaire {@code <!-- <Context ...> -->}), cherche une suite de
     * commentaires séparés uniquement par des blancs et terminée par {@code <!-- </Context> -->}.
     *
     * @return la fin (exclusive) du dernier commentaire, ou -1 si ce n'est pas un bloc complet
     */
    private static int lineByLineBlockEnd(String text, int from) {
        int j = from;
        while (true) {
            while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                j++;
            }
            if (!text.startsWith("<!--", j)) {
                return -1;
            }
            int close = text.indexOf("-->", j + 4);
            if (close < 0) {
                return -1;
            }
            String inner = text.substring(j + 4, close).strip();
            if (CLOSING_CONTEXT.matcher(inner).matches()) {
                return close + 3;
            }
            if (findContextTag(inner, 0) == 0) {
                return -1; // un autre Context commence : bloc incomplet
            }
            j = close + 3;
        }
    }

    private static ContextEntry entry(int index, String text, int start, int end, boolean commented,
                                      String element) {
        String openTag = element.substring(0, Math.max(0, openTagEnd(element, 0)));
        int line = 1;
        for (int k = 0; k < start; k++) {
            if (text.charAt(k) == '\n') {
                line++;
            }
        }
        return new ContextEntry(index, start, end, commented,
                attribute(openTag, "path"), attribute(openTag, "docBase"), line, text.substring(start, end));
    }

    /** Position du prochain "<Context" (mot entier) à partir de from, ou -1. */
    private static int findContextTag(String s, int from) {
        int k = from;
        while ((k = s.indexOf("<Context", k)) >= 0) {
            int after = k + "<Context".length();
            if (after >= s.length()) {
                return -1;
            }
            char c = s.charAt(after);
            if (Character.isWhitespace(c) || c == '>' || c == '/') {
                return k;
            }
            k = after;
        }
        return -1;
    }

    /** Fin (exclusive) de la balise ouvrante commençant à start, en tenant compte des guillemets. */
    private static int openTagEnd(String s, int start) {
        char quote = 0;
        for (int k = start; k < s.length(); k++) {
            char c = s.charAt(k);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return k + 1;
            }
        }
        return -1;
    }

    /** Fin (exclusive) de l'élément Context commençant à start (auto-fermant ou avec </Context>). */
    private static int elementEnd(String s, int start) {
        int tagEnd = openTagEnd(s, start);
        if (tagEnd < 0) {
            return -1;
        }
        if (s.charAt(tagEnd - 2) == '/') {
            return tagEnd;
        }
        int close = s.indexOf("</Context", tagEnd);
        if (close < 0) {
            return -1;
        }
        int gt = s.indexOf('>', close);
        return gt < 0 ? -1 : gt + 1;
    }

    private static String attribute(String tag, String name) {
        Matcher m = Pattern.compile("\\s" + name + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)')").matcher(tag);
        if (!m.find()) {
            return "";
        }
        return m.group(2) != null ? m.group(2) : m.group(3);
    }

    private static Charset detectCharset(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.ISO_8859_1);
        Matcher m = ENCODING_DECL.matcher(head);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (IllegalArgumentException ignored) {
                // encodage inconnu : on retombe sur UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static int leadingWhitespace(String s) {
        int k = 0;
        while (k < s.length() && Character.isWhitespace(s.charAt(k))) {
            k++;
        }
        return k;
    }

    private static int trailingWhitespace(String s) {
        int k = s.length();
        while (k > 0 && Character.isWhitespace(s.charAt(k - 1))) {
            k--;
        }
        return s.length() - k;
    }

    private static String stripHorizontal(String s) {
        int a = horizontalWhitespace(s, 0);
        int b = s.length();
        while (b > a && (s.charAt(b - 1) == ' ' || s.charAt(b - 1) == '\t')) {
            b--;
        }
        return s.substring(a, b);
    }

    private static int horizontalWhitespace(String s, int from) {
        int k = from;
        while (k < s.length() && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) {
            k++;
        }
        return k - from;
    }
}
