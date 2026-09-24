package fr.tomcatchooser;

/**
 * Un élément {@code <Context>} trouvé dans server.xml, actif ou mis en commentaire.
 * <p>
 * Classe classique plutôt qu'un record, pour rester compatible avec les anciennes versions d'Eclipse.
 */
public final class ContextEntry {

    private final int index;
    private final int start;
    private final int end;
    private final boolean commented;
    private final String path;
    private final String docBase;
    private final int line;
    private final String rawText;

    /**
     * @param index     position de l'entrée dans la liste (ordre du fichier)
     * @param start     début de la zone dans le texte (le {@code <Context} ou le {@code <!--})
     * @param end       fin (exclusive) de la zone dans le texte
     * @param commented vrai si le Context est dans un commentaire {@code <!-- ... -->}
     * @param path      attribut path (peut être vide)
     * @param docBase   attribut docBase (peut être vide)
     * @param line      numéro de ligne (1-based) du début de la zone
     * @param rawText   texte exact de la zone, utilisé pour vérifier que le fichier n'a pas changé
     */
    public ContextEntry(int index, int start, int end, boolean commented,
                        String path, String docBase, int line, String rawText) {
        this.index = index;
        this.start = start;
        this.end = end;
        this.commented = commented;
        this.path = path;
        this.docBase = docBase;
        this.line = line;
        this.rawText = rawText;
    }

    public int index() {
        return index;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public boolean commented() {
        return commented;
    }

    public String path() {
        return path;
    }

    public String docBase() {
        return docBase;
    }

    public int line() {
        return line;
    }

    public String rawText() {
        return rawText;
    }

    /** Nom affiché : le path, ou "/ (ROOT)" pour le contexte racine. */
    public String displayPath() {
        return path.isEmpty() || path.equals("/") ? "/ (ROOT)" : path;
    }
}
