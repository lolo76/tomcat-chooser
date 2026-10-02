package fr.tomcatchooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Fenêtre principale : état et contrôle de Tomcat, puis la liste des Context de server.xml avec
 * un point vert (actif) ou rouge (inactif) pour (dé)commenter chacun. Tout est actualisé toutes les 3 s.
 */
public class TomcatChooserApp extends Application {

    static final Path DEFAULT_SERVER_XML = defaultServerXml();
    private static final String PREF_LAST_FILE = "lastServerXml";
    private static final double ROW_HEIGHT = 30;
    /** Colonne du point vert / rouge : juste sa largeur. */
    private static final double STATUS_WIDTH = 36;
    private static final double APPLICATION_WIDTH = 236;
    /** Nombre maximal de lignes affichées avant l'ascenseur. */
    private static final int MAX_ROWS = 10;
    private static final double DOT_RADIUS = 7;
    /** Taille du texte d'état de Tomcat, plus petit que le reste. */
    private static final double STATE_FONT_SIZE = 11;
    /** Icône « dossier » (Material Design). */
    private static final String FOLDER_ICON = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8"
            + "c0-1.1-.9-2-2-2h-8l-2-2z";
    private static final double ICON_SIZE = 18;
    /** Côté des boutons-icônes ronds. */
    private static final double BUTTON_SIZE = 32;
    /** Icône « fichier texte » (Material Design « description ») : ouvre server.xml dans l'éditeur. */
    private static final String FILE_ICON = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6z"
            + "m2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z";
    /** Icônes « ajouter », « copier » et « supprimer » (Material Design). */
    private static final String ADD_ICON = "M19 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"
            + "m-2 10h-4v4h-2v-4H7v-2h4V7h2v4h4v2z";
    /** Icônes du thème : soleil (clair), lune (sombre) et demi-disque (système), Material Design. */
    private static final String THEME_LIGHT_ICON = "M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1"
            + "s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1z"
            + "M11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2"
            + "c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06"
            + "c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41"
            + "l1.06 1.06c.39.39 1.03.39 1.41 0 .39-.39.39-1.03 0-1.41l-1.06-1.06zm1.06-10.96c.39-.39.39-1.03 0-1.41"
            + "-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36"
            + "c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z";
    private static final String THEME_DARK_ICON = "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36"
            + "-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z";
    private static final String THEME_SYSTEM_ICON = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"
            + "m0 18V4c4.42 0 8 3.58 8 8s-3.58 8-8 8z";
    private static final String PREF_THEME = "theme";
    private static final String COPY_ICON = "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11"
            + "c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z";
    private static final String DELETE_ICON = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";
    /** Icône « modifier » (crayon, Material Design). */
    private static final String EDIT_ICON = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z"
            + "M20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";
    /** Icônes « démarrer » (triangle) et « arrêter » (carré), Material Design. */
    private static final String START_ICON = "M8 5v14l11-7z";
    private static final String STOP_ICON = "M6 6h12v12H6z";
    /** Icône « redémarrer » (flèche qui revient, Material Design « replay »). */
    private static final String RESTART_ICON = "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4"
            + "c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z";
    /** Délais au-delà desquels on cesse d'attendre le changement d'état demandé. */
    private static final long START_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long STOP_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1);
    /**
     * Arrêt par Sysdeo : plus long, car Sysdeo perd l'ordre tant que Tomcat n'a pas fini de démarrer
     * (plus d'une minute pour Areo). L'ordre est renvoyé toutes les ECLIPSE_STOP_RETRY_MS.
     */
    private static final long ECLIPSE_STOP_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(4);
    private static final long ECLIPSE_STOP_RETRY_MS = TimeUnit.SECONDS.toMillis(10);
    /** Durée pendant laquelle on réessaie d'envoyer la commande d'arrêt (port d'arrêt pas encore ouvert). */
    private static final long SHUTDOWN_RETRY_MS = TimeUnit.SECONDS.toMillis(15);

    private final Preferences prefs = Preferences.userNodeForPackage(TomcatChooserApp.class);
    private final TextField pathField = new TextField();
    private final TableView<ContextEntry> table = new TableView<>();
    private final Label placeholder = new Label();
    private final TextField search = new TextField();
    private final Label tomcatState = new Label();
    private volatile TomcatStatus tomcatStatus;
    private final ObservableList<ContextEntry> contexts = FXCollections.observableArrayList();
    private final FilteredList<ContextEntry> filtered = new FilteredList<>(contexts);
    private Stage stage;
    private Region header;
    private Region footer;
    private VBox listBox;
    /** Thème de l'interface : celui du système, clair ou sombre (mémorisé). */
    private enum Theme { SYSTEM, LIGHT, DARK }

    private Theme theme = Theme.SYSTEM;
    private final Button themeButton = iconButton(THEME_SYSTEM_ICON);
    /** Sous le tableau : nombre de Context actifs. */
    private final Label statusLabel = new Label();
    private final Button addButton = iconButton(ADD_ICON);
    private final Button duplicateButton = iconButton(COPY_ICON);
    private final Button editButton = iconButton(EDIT_ICON);
    private final Button deleteButton = iconButton(DELETE_ICON);
    private final Button startStopButton = iconButton(START_ICON);
    private final Button restartButton = iconButton(RESTART_ICON);
    /** Redémarrage en cours : après l'arrêt, Tomcat est relancé. */
    private boolean restarting;
    /** Le port de débogage était ouvert au moment du redémarrage : on attend qu'il se libère avant de relancer. */
    private volatile boolean waitDebugPort;
    /**
     * Eclipse pilote le Tomcat de ce server.xml (plugin MCP vogella + plugin Sysdeo) : démarrer, arrêter et
     * redémarrer passent alors par Sysdeo, dans Eclipse. Null sinon (Eclipse fermé, plugin absent ou autre serveur).
     */
    private volatile EclipseMcp eclipse;
    /** Dernière vérification « Sysdeo pilote ce server.xml » : refaite au plus toutes les 15 s. */
    private long driveCheckedAt;
    private boolean driveValue;
    private Path driveCheckedFor;
    /** L'opération en cours (démarrage, arrêt, redémarrage) a été confiée à Eclipse. */
    private boolean viaEclipse;
    /** Dernier client Eclipse connu : sert à finir une opération si Eclipse disparaît un instant de la détection. */
    private volatile EclipseMcp lastEclipse;
    /** Dernier envoi d'un ordre d'arrêt à Sysdeo, et nombre de mesures « arrêté » d'un redémarrage en cours. */
    private long lastEclipseStop;
    private int stoppedPolls;
    /** État attendu après un clic sur Démarrer / Arrêter (null : aucune demande en cours) et sa limite. */
    private TomcatStatus.State pendingState;
    private long pendingDeadline;
    private volatile ServerXml serverXml;
    /** Rang d'affichage de chaque Context (par position dans le fichier), fixé à chaque tri. */
    private int[] rankByIndex = new int[0];
    /** La fenêtre a reçu sa taille (fixe) : elle ne change plus ensuite. */
    private boolean sized;
    /** Date de server.xml lors de la dernière lecture : un changement sur le disque déclenche une relecture. */
    private FileTime lastModified;

    /**
     * server.xml proposé au premier lancement : C:\Tomcat70 sous Windows ; ailleurs, celui de
     * $CATALINA_HOME s'il est défini, sinon /opt/tomcat.
     */
    private static Path defaultServerXml() {
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            return Paths.get("C:\\Tomcat70\\conf\\server.xml");
        }
        String catalinaHome = System.getenv("CATALINA_HOME");
        Path home = catalinaHome != null && !catalinaHome.isBlank() ? Paths.get(catalinaHome) : Paths.get("/opt/tomcat");
        return home.resolve("conf").resolve("server.xml");
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        // Barre d'application.
        Label title = new Label("Choisi ton projet");
        title.getStyleClass().add("app-bar-title");
        Region barSpace = new Region();
        HBox.setHgrow(barSpace, Priority.ALWAYS);
        // En haut à droite : le thème, clair, sombre ou celui du système (un clic passe au suivant).
        themeButton.setOnAction(e -> {
            theme = Theme.values()[(theme.ordinal() + 1) % Theme.values().length];
            prefs.put(PREF_THEME, theme.name());
            applyTheme();
        });
        HBox appBar = new HBox(title, barSpace, themeButton);
        appBar.getStyleClass().add("app-bar");
        appBar.setAlignment(Pos.CENTER_LEFT);

        // Première carte : chemin (icônes à droite), état de Tomcat (boutons à droite) et actions sur les Context.
        Button browse = iconButton(FOLDER_ICON);
        browse.setOnAction(e -> chooseFile());
        Button editFile = iconButton(FILE_ICON);
        editFile.setOnAction(e -> openInSystemEditor(Paths.get(pathField.getText().trim())));
        pathField.setOnAction(e -> load(Paths.get(pathField.getText().trim())));
        pathField.setPromptText("Chemin du server.xml");
        HBox.setHgrow(pathField, Priority.ALWAYS);
        HBox top = new HBox(4, pathField, browse, editFile);
        top.setAlignment(Pos.CENTER_LEFT);

        startStopButton.setOnAction(e -> startOrStopTomcat());
        startStopButton.setVisible(false);
        startStopButton.managedProperty().bind(startStopButton.visibleProperty());
        restartButton.setOnAction(e -> restartTomcat());
        restartButton.setVisible(false);
        restartButton.managedProperty().bind(restartButton.visibleProperty());
        // Largeur réservée au plus long libellé : la fenêtre, de taille fixe, ne le coupe jamais.
        Text widest = new Text("Tomcat démarré (port 65535)");
        widest.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, STATE_FONT_SIZE));
        double stateWidth = Math.ceil(widest.getLayoutBounds().getWidth()) + 6;
        tomcatState.setMinWidth(stateWidth);
        tomcatState.setPrefWidth(stateWidth);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox tomcatRow = new HBox(4, tomcatState, spacer, startStopButton, restartButton);
        tomcatRow.setAlignment(Pos.CENTER_LEFT);
        // Hauteur réservée même sans bouton : la fenêtre ne saute pas quand ils apparaissent.
        tomcatRow.setMinHeight(BUTTON_SIZE);

        search.setPromptText("Rechercher");
        search.textProperty().addListener((obs, old, text) -> {
            String q = text.trim().toLowerCase();
            filtered.setPredicate(c -> q.isEmpty() || c.displayPath().toLowerCase().contains(q));
            updateCount();
            fitWindowToRows();
        });

        buildTable();

        // Boutons Ajouter, Dupliquer, Modifier et Supprimer (ces trois-là sur la ligne sélectionnée).
        deleteButton.setGraphic(iconGraphic(DELETE_ICON, "icon-danger"));
        addButton.setOnAction(e -> addContext());
        addButton.setDisable(true);
        duplicateButton.setOnAction(e -> duplicateContext());
        editButton.setOnAction(e -> edit(table.getSelectionModel().getSelectedItem()));
        deleteButton.setOnAction(e -> deleteContext(table.getSelectionModel().getSelectedItem()));
        for (Button b : new Button[] {duplicateButton, editButton, deleteButton}) {
            b.disableProperty().bind(addButton.disableProperty()
                    .or(table.getSelectionModel().selectedItemProperty().isNull()));
        }
        tip(themeButton, "");
        tip(browse, "Choisir un autre server.xml");
        tip(editFile, "Ouvrir server.xml dans l'éditeur");
        tip(startStopButton, "Démarrer Tomcat");
        tip(restartButton, "Redémarrer Tomcat");
        tip(addButton, "Ajouter un Context");
        tip(duplicateButton, "Dupliquer le Context sélectionné");
        tip(editButton, "Modifier le Context sélectionné");
        tip(deleteButton, "Supprimer le Context sélectionné");
        HBox buttonRow = new HBox(8, addButton, duplicateButton, editButton, deleteButton);
        buttonRow.setAlignment(Pos.CENTER);

        VBox card = new VBox(8, top, tomcatRow);
        card.getStyleClass().add("card");
        VBox.setMargin(card, new Insets(10, 10, 0, 10));

        // Seconde carte, sous la première : la recherche et le tableau.
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setMinHeight(16);
        statusLabel.setPrefHeight(16);
        tomcatState.getStyleClass().add("tomcat-state");
        listBox = new VBox(8, buttonRow, search, table, statusLabel);
        listBox.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.NEVER);
        header = new VBox(appBar, card);
        Region bottomSpace = new Region();
        bottomSpace.setMinHeight(10);
        bottomSpace.setPrefHeight(10);
        footer = bottomSpace;
        BorderPane root = new BorderPane(listBox, header, null, footer, null);
        BorderPane.setMargin(listBox, new Insets(10, 10, 0, 10));

        stage.setTitle("Tomcat Chooser");
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(
                    TomcatChooserApp.class.getResourceAsStream("tomcat-" + size + ".png")));
        }
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Material.STYLESHEET);
        stage.setScene(scene);
        try {
            theme = Theme.valueOf(prefs.get(PREF_THEME, Theme.SYSTEM.name()));
        } catch (IllegalArgumentException unknown) {
            theme = Theme.SYSTEM;
        }
        // Mode « système » : l'interface suit le thème de Windows, y compris quand il change en cours de route.
        Platform.getPreferences().colorSchemeProperty().addListener((obs, old, scheme) -> {
            if (theme == Theme.SYSTEM) {
                applyTheme();
            }
        });
        applyTheme();
        // Taille fixe : calculée une fois pour MAX_ROWS lignes (voir fitWindowToRows).
        stage.setResizable(false);
        stage.show();

        String param = getParameters().getRaw().isEmpty() ? null : getParameters().getRaw().get(0);
        String last = param != null ? param : prefs.get(PREF_LAST_FILE, DEFAULT_SERVER_XML.toString());
        load(Paths.get(last));
        startMonitor();
    }

    /** Statut sous le tableau : nombre de Context actifs (et nombre affiché si une recherche filtre la liste). */
    private void updateCount() {
        long active = contexts.stream().filter(c -> !c.commented()).count();
        int total = contexts.size();
        String text = total == 0 ? "" : active + (active > 1 ? " actifs" : " actif") + " sur " + total;
        if (total > 0 && filtered.size() != total) {
            text += " · " + filtered.size() + (filtered.size() > 1 ? " affichés" : " affiché");
        }
        statusLabel.setText(text);
    }

    /** Applique le thème choisi : classe « dark » sur la racine, icône et infobulle du bouton. */
    private void applyTheme() {
        boolean dark = theme == Theme.DARK
                || (theme == Theme.SYSTEM && Platform.getPreferences().getColorScheme() == ColorScheme.DARK);
        Material.setDark(dark);
        Parent root = stage.getScene().getRoot();
        root.getStyleClass().remove(Material.DARK);
        if (dark) {
            root.getStyleClass().add(Material.DARK);
        }
        String icon = theme == Theme.LIGHT ? THEME_LIGHT_ICON : theme == Theme.DARK ? THEME_DARK_ICON : THEME_SYSTEM_ICON;
        themeButton.setGraphic(iconGraphic(icon, "icon-on-primary"));
        String next = theme == Theme.SYSTEM ? "clair" : theme == Theme.LIGHT ? "sombre" : "du système";
        String now = theme == Theme.SYSTEM ? "du système (" + (dark ? "sombre" : "clair") + ")"
                : theme == Theme.LIGHT ? "clair" : "sombre";
        themeButton.getTooltip().setText("Thème " + now + " — cliquer pour passer au thème " + next);
    }

    /**
     * Termine le processus à la fermeture de la fenêtre. Sans cela, les threads AWT démarrés par
     * le bouton « éditer » (java.awt.Desktop) gardent l'exe en vie, et son fichier reste verrouillé.
     */
    @Override
    public void stop() {
        System.exit(0);
    }

    private void buildTable() {
        TableColumn<ContextEntry, String> application = new TableColumn<>("Application");
        application.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().displayPath()));

        TableColumn<ContextEntry, ContextEntry> action = new TableColumn<>("Statut");
        action.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        action.setCellFactory(col -> new TableCell<>() {
            // Point vert (actif) ou rouge (inactif) ; un clic bascule vers l'autre état.
            private final Circle dot = new Circle(DOT_RADIUS);
            private final Button button = new Button(null, dot);

            {
                button.getStyleClass().add("dot-button");
                button.setOnAction(e -> toggle(getItem()));
                tip(button, "");
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(ContextEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    dot.getStyleClass().removeAll("dot-active", "dot-inactive");
                    dot.getStyleClass().add(item.commented() ? "dot-inactive" : "dot-active");
                    button.getTooltip().setText(item.commented()
                            ? "Inactif : cliquer pour activer (décommenter)"
                            : "Actif : cliquer pour désactiver (commenter)");
                    setGraphic(button);
                }
            }
        });
        action.setSortable(false);

        // Colonnes redimensionnables à la souris, sans barre de défilement horizontale.
        action.setPrefWidth(STATUS_WIDTH);
        action.setMinWidth(STATUS_WIDTH);
        application.setMinWidth(80);
        application.setPrefWidth(APPLICATION_WIDTH);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);

        // Les actifs d'abord, puis par ordre alphabétique (sans tenir compte de la casse).
        application.setSortable(false);
        SortedList<ContextEntry> sorted = new SortedList<>(filtered);
        sorted.setComparator(Comparator
                .comparingInt((ContextEntry c) -> c.index() < rankByIndex.length ? rankByIndex[c.index()] : Integer.MAX_VALUE)
                .thenComparingInt(ContextEntry::index));
        table.setItems(sorted);
        table.setRowFactory(tv -> {
            TableRow<ContextEntry> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()
                        && !isInsideButton(e.getTarget())) {
                    edit(row.getItem());
                }
            });
            return row;
        });
        table.getColumns().add(action);
        table.getColumns().add(application);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPrefWidth(STATUS_WIDTH + APPLICATION_WIDTH);
        placeholder.setWrapText(true);
        placeholder.setPadding(new Insets(0, 8, 0, 8));
        table.setPlaceholder(placeholder);
    }

    /** Infobulle sur un bouton, au style Material (fond gris foncé, texte blanc). */
    private static void tip(Button button, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setStyle("-fx-background-color: #424242; -fx-text-fill: white; -fx-font-size: 11px;"
                + " -fx-background-radius: 4; -fx-padding: 5 8 5 8;");
        tooltip.setShowDelay(Duration.millis(350));
        button.setTooltip(tooltip);
    }

    /** Bouton carré avec une icône bleue, toutes les icônes ramenées à la même taille. */
    private static Button iconButton(String svg) {
        Button button = new Button(null, iconGraphic(svg));
        button.getStyleClass().add("icon-button");
        button.setMinSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setMaxSize(BUTTON_SIZE, BUTTON_SIZE);
        return button;
    }

    private static Node iconGraphic(String svg) {
        return iconGraphic(svg, "icon");
    }

    private static Node iconGraphic(String svg, String styleClass) {
        SVGPath path = new SVGPath();
        path.setContent(svg);
        path.getStyleClass().add(styleClass);
        double scale = ICON_SIZE / Math.max(path.getLayoutBounds().getWidth(), path.getLayoutBounds().getHeight());
        path.setScaleX(scale);
        path.setScaleY(scale);
        return new Group(path);
    }

    private static boolean isInsideButton(Object target) {
        for (Node n = target instanceof Node node ? node : null; n != null; n = n.getParent()) {
            if (n instanceof ButtonBase) {
                return true;
            }
        }
        return false;
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir un server.xml");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fichiers XML", "*.xml"),
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"));
        Path current = Paths.get(pathField.getText().trim()).toAbsolutePath().getParent();
        if (current == null || !Files.isDirectory(current)) {
            current = DEFAULT_SERVER_XML.getParent();
        }
        if (current != null && Files.isDirectory(current)) {
            chooser.setInitialDirectory(current.toFile());
        }
        File chosen = chooser.showOpenDialog(stage);
        if (chosen != null) {
            load(chosen.toPath());
        }
    }

    /** Ouvre le fichier dans l'éditeur du système (Bloc-notes à défaut) ; la liste suit seule ses modifications. */
    private void openInSystemEditor(Path file) {
        if (!Files.isRegularFile(file)) {
            showMessage("Fichier introuvable", file.toString());
            return;
        }
        Thread opener = new Thread(() -> {
            try {
                openWithDesktop(file.toFile());
            } catch (Exception ex) {
                Platform.runLater(() -> showMessage("Impossible d'ouvrir l'éditeur", ex.getMessage()));
            }
        }, "editeur-systeme");
        opener.setDaemon(true);
        opener.start();
    }

    private static void openWithDesktop(File file) throws Exception {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.EDIT)) {
                try {
                    desktop.edit(file);
                    return;
                } catch (Exception noEditor) {
                    // pas d'éditeur associé : on essaie les solutions suivantes
                }
            }
            if (System.getProperty("os.name", "").startsWith("Windows")) {
                new ProcessBuilder("notepad.exe", file.getAbsolutePath()).start();
                return;
            }
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(file);
                return;
            }
        }
        throw new IllegalStateException("aucun éditeur disponible");
    }

    private void load(Path file) {
        pathField.setText(file.toString());
        if (!Files.isRegularFile(file)) {
            unload("Fichier introuvable : " + file + ". Choisissez un server.xml avec l'icône dossier.");
            return;
        }
        try {
            serverXml = ServerXml.load(file);
            lastModified = modifiedTime(file);
            addButton.setDisable(false);
            prefs.put(PREF_LAST_FILE, file.toString());
            placeholder.setText("Aucun <Context> trouvé dans ce fichier.");
            refresh();
        } catch (Exception ex) {
            unload("Impossible de lire " + file + " : " + ex.getMessage());
        }
    }

    /** Plus de fichier lu : liste vide, et la raison affichée à la place du tableau. */
    private void unload(String reason) {
        serverXml = null;
        lastModified = null;
        addButton.setDisable(true);
        tomcatStatus = null;
        contexts.clear();
        updateCount();
        placeholder.setText(reason);
        fitWindowToRows();
    }

    private static FileTime modifiedTime(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (Exception e) {
            return null;
        }
    }

    private void toggle(ContextEntry entry) {
        if (serverXml == null || entry == null) {
            return;
        }
        try {
            ContextEntry updated = serverXml.toggle(entry);
            refresh(false); // la ligne garde sa place : pas de nouveau tri
            select(updated);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showError(Exception ex) {
        try {
            serverXml.reload();
            refresh();
        } catch (Exception ignored) {
            // l'erreur principale est affichée ci-dessous
        }
        Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage());
        Material.apply(alert);
        alert.setHeaderText("Modification impossible");
        alert.initOwner(stage);
        alert.showAndWait();
    }

    /** Message d'erreur sans bloquer : les erreurs de Tomcat arrivent pendant l'actualisation automatique. */
    private void showMessage(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        Material.apply(alert);
        alert.setHeaderText(header);
        alert.initOwner(stage);
        alert.show();
    }

    private void edit(ContextEntry entry) {
        if (serverXml == null || entry == null) {
            return;
        }
        Map<String, Map<String, String>> current = new LinkedHashMap<>();
        for (String tag : ServerXml.EDITABLE_TAGS) {
            current.put(tag, serverXml.attributes(entry, tag));
        }
        Optional<Map<String, Map<String, String>>> result =
                ContextEditorDialog.edit(stage, entry, current).showAndWait();
        if (result.isEmpty()) {
            return;
        }
        Map<String, Map<String, String>> changed = new LinkedHashMap<>(result.get());
        changed.entrySet().removeIf(e -> e.getValue().equals(current.get(e.getKey())));
        if (changed.isEmpty()) {
            return;
        }
        try {
            ContextEntry updated = serverXml.updateTags(entry, changed);
            refresh();
            select(updated);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    /**
     * Ajoute un Context avec exactement la même fenêtre que Modifier : path, reloadable, docBase, workDir, et un
     * Loader prérempli comme celui déjà utilisé dans le fichier (vider son className pour ne pas en mettre).
     * docBase et workDir sont préremplis d'après les derniers Context du fichier, et suivent le path saisi.
     */
    private void addContext() {
        if (serverXml == null) {
            return;
        }
        Map<String, String> templates = docBaseTemplates();
        Map<String, String> context = new LinkedHashMap<>();
        context.put("path", "");
        context.put("reloadable", "true");
        context.put("docBase", templates.containsKey("docBase") ? ContextEditorDialog.fill(templates.get("docBase"), "") : "");
        context.put("workDir", templates.containsKey("workDir") ? ContextEditorDialog.fill(templates.get("workDir"), "") : "");
        Map<String, Map<String, String>> byTag = new LinkedHashMap<>();
        byTag.put("Context", context);
        serverXml.contexts().stream().map(c -> serverXml.attributes(c, "Loader")).filter(l -> l != null)
                .findFirst().ifPresent(loader -> {
                    // Un nouveau Context n'a besoin ni de debug ni de useSystemClassLoaderAsParent.
                    Map<String, String> simple = new LinkedHashMap<>(loader);
                    simple.remove("debug");
                    simple.remove("useSystemClassLoaderAsParent");
                    byTag.put("Loader", simple);
                });
        Optional<Map<String, Map<String, String>>> result =
                ContextEditorDialog.add(stage, byTag, templates).showAndWait();
        if (result.isEmpty()) {
            return;
        }
        try {
            ContextEntry added = serverXml.addContextTags(result.get());
            refresh();
            select(added);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    /**
     * Modèles de docBase et de workDir d'après les derniers Context du fichier : leur valeur, où le nom de
     * l'application (le path sans « / », comme dossier) est remplacé par {@link ContextEditorDialog#NAME_MARK}.
     */
    private Map<String, String> docBaseTemplates() {
        Map<String, String> templates = new LinkedHashMap<>();
        java.util.List<ContextEntry> all = serverXml.contexts();
        for (int i = all.size() - 1; i >= 0 && templates.size() < 2; i--) {
            ContextEntry c = all.get(i);
            String name = c.path().startsWith("/") ? c.path().substring(1) : c.path();
            if (name.isEmpty()) {
                continue;
            }
            Map<String, String> attrs = serverXml.attributes(c, "Context");
            for (String key : new String[] {"docBase", "workDir"}) {
                String value = attrs == null ? null : attrs.get(key);
                if (value == null || templates.containsKey(key)) {
                    continue;
                }
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(^|[\\\\/])" + java.util.regex.Pattern.quote(name) + "([\\\\/]|$)",
                                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
                if (m.find()) {
                    templates.put(key, value.substring(0, m.start()) + m.group(1) + ContextEditorDialog.NAME_MARK
                            + m.group(2) + value.substring(m.end()));
                }
            }
        }
        return templates;
    }

    /** Supprime le Context sélectionné de server.xml, après confirmation. */
    private void deleteContext(ContextEntry entry) {
        if (serverXml == null || entry == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Il sera retiré de " + serverXml.file().toAbsolutePath().normalize() + ".",
                ButtonType.OK, ButtonType.CANCEL);
        Material.apply(confirm);
        confirm.setTitle("Supprimer");
        confirm.setHeaderText("Supprimer le Context " + entry.displayPath() + " ?");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("Supprimer");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Annuler");
        confirm.initOwner(stage);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            serverXml.deleteContext(entry);
            refresh();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    /** Duplique le Context sélectionné : fenêtre préremplie (path suffixé « _copie »), puis insertion active. */
    private void duplicateContext() {
        ContextEntry source = table.getSelectionModel().getSelectedItem();
        if (serverXml == null || source == null) {
            return;
        }
        Map<String, Map<String, String>> byTag = new LinkedHashMap<>();
        for (String tag : ServerXml.EDITABLE_TAGS) {
            byTag.put(tag, serverXml.attributes(source, tag));
        }
        Map<String, String> context = new LinkedHashMap<>(byTag.get("Context"));
        context.put("path", (source.path().isEmpty() ? "/ROOT" : source.path()) + "_copie");
        byTag.put("Context", context);
        Optional<Map<String, Map<String, String>>> result =
                ContextEditorDialog.duplicate(stage, source, byTag).showAndWait();
        if (result.isEmpty()) {
            return;
        }
        try {
            ContextEntry added = serverXml.duplicateContext(source, result.get());
            refresh();
            select(added);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void select(ContextEntry entry) {
        table.getItems().stream().filter(c -> c.index() == entry.index()).findFirst()
                .ifPresent(c -> table.getSelectionModel().select(c));
    }

    /**
     * Toutes les 3 s : vérifie si Tomcat tourne (port HTTP de server.xml) et relit server.xml
     * s'il a changé sur le disque (modifié à la main ou par un autre outil).
     */
    private void startMonitor() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "actualisation");
            t.setDaemon(true);
            return t;
        });
        monitor.scheduleWithFixedDelay(() -> {
            ServerXml xml = serverXml;
            FileTime modified = xml == null ? null : modifiedTime(xml.file());
            TomcatStatus status = tomcatStatus;
            TomcatStatus.State state = status == null ? null : status.check();
            eclipse = detectEclipse(xml);
            if (eclipse != null) {
                lastEclipse = eclipse;
            }
            Platform.runLater(() -> {
                reloadIfChanged(xml, modified);
                showTomcatState(tomcatStatus, tomcatStatus == status ? state : null);
            });
        }, 0, 3, TimeUnit.SECONDS);
    }

    /**
     * Le client d'Eclipse si le plugin MCP répond et que le plugin Sysdeo pilote le Tomcat de ce server.xml ;
     * null sinon. Appelé par le moniteur, hors du thread JavaFX.
     */
    private EclipseMcp detectEclipse(ServerXml xml) {
        EclipseMcp mcp = EclipseMcp.connect();
        if (mcp == null || xml == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (!xml.file().equals(driveCheckedFor) || now - driveCheckedAt > 15_000) {
            try {
                driveValue = mcp.drives(xml.file());
            } catch (Exception e) {
                driveValue = false;
            }
            driveCheckedFor = xml.file();
            driveCheckedAt = now;
        }
        return driveValue ? mcp : null;
    }

    /** Relit server.xml s'il a changé sur le disque, en gardant la ligne sélectionnée et la recherche. */
    private void reloadIfChanged(ServerXml xml, FileTime modified) {
        if (xml == null || xml != serverXml || modified == null || modified.equals(lastModified)) {
            return;
        }
        lastModified = modified;
        try {
            String before = xml.text();
            xml.reload();
            if (!before.equals(xml.text())) {
                ContextEntry selected = table.getSelectionModel().getSelectedItem();
                refresh();
                if (selected != null) {
                    contexts.stream()
                            .filter(c -> c.path().equals(selected.path()))
                            .min((a, b) -> Integer.compare(Math.abs(a.index() - selected.index()),
                                    Math.abs(b.index() - selected.index())))
                            .ifPresent(this::select);
                }
            }
        } catch (Exception ignored) {
            // Fichier en cours d'écriture ou momentanément illisible : nouvel essai dans 3 s.
            lastModified = null;
        }
    }

    private void showTomcatState(TomcatStatus status, TomcatStatus.State state) {
        if (status == null) {
            tomcatState.setText("");
            startStopButton.setVisible(false);
            restartButton.setVisible(false);
            return;
        }
        if (state == null) {
            // Le server.xml vient de changer : l'état sera mesuré au prochain passage.
            return;
        }
        boolean started = state == TomcatStatus.State.STARTED;
        long now = System.currentTimeMillis();
        if (viaEclipse && pendingState == TomcatStatus.State.STOPPED && started
                && now - lastEclipseStop > ECLIPSE_STOP_RETRY_MS && now <= pendingDeadline) {
            // Tomcat répond encore : Sysdeo perd l'ordre d'arrêt tant que Tomcat n'a pas fini de démarrer
            // (son port d'arrêt est alors fermé). On le redemande jusqu'à ce que Tomcat s'arrête.
            lastEclipseStop = now;
            sendToEclipse(EclipseMcp.SYSDEO_STOP, status, TomcatStatus.State.STARTED);
        }
        if (restarting && pendingState == TomcatStatus.State.STOPPED && state == TomcatStatus.State.STOPPED
                && now <= pendingDeadline) {
            if (viaEclipse) {
                // Arrêté : on laisse un instant à l'ancienne JVM pour se terminer, puis Sysdeo redémarre Tomcat.
                if (++stoppedPolls >= 2) {
                    stoppedPolls = 0;
                    pendingState = TomcatStatus.State.STARTED;
                    pendingDeadline = now + START_TIMEOUT_MS;
                    sendToEclipse(EclipseMcp.SYSDEO_START, status, TomcatStatus.State.STOPPED);
                }
            } else if (!waitDebugPort || !TomcatStatus.isListening(TomcatLauncher.DEBUG_PORT)) {
                // Arrêté : on relance, une fois le port de débogage libéré par l'ancienne JVM.
                try {
                    TomcatLauncher.start(serverXml.file());
                    pendingState = TomcatStatus.State.STARTED;
                    pendingDeadline = System.currentTimeMillis() + START_TIMEOUT_MS;
                } catch (Exception ex) {
                    pendingState = null;
                    restarting = false;
                    showMessage("Tomcat est arrêté mais n'a pas pu être relancé", ex.getMessage());
                }
            }
        } else if (pendingState != null && (state == pendingState || System.currentTimeMillis() > pendingDeadline)) {
            if (state != pendingState) {
                showMessage(pendingState == TomcatStatus.State.STARTED ? "Tomcat n'a pas démarré" : "Tomcat ne s'est pas arrêté",
                        pendingState == TomcatStatus.State.STARTED ? "Voyez sa console ou ses logs." : "Arrêtez-le depuis sa console.");
            } else if (restarting && state == TomcatStatus.State.STOPPED && !viaEclipse) {
                showMessage("Tomcat est arrêté mais n'a pas été relancé",
                        "Le port " + TomcatLauncher.DEBUG_PORT + " reste occupé : relancez-le avec ▶.");
            }
            pendingState = null;
            restarting = false;
            viaEclipse = false;
        }
        boolean viaPlugin = eclipse != null;
        boolean canStart = serverXml != null && (viaPlugin || TomcatLauncher.catalinaHome(serverXml.file()) != null);
        startStopButton.setVisible(serverXml != null && (started || canStart));
        startStopButton.setDisable(pendingState != null);
        startStopButton.setGraphic(iconGraphic(started ? STOP_ICON : START_ICON));
        startStopButton.getTooltip().setText(viaPlugin
                ? (started ? "Arrêter Tomcat avec Sysdeo (dans Eclipse)" : "Démarrer Tomcat avec Sysdeo (dans Eclipse)")
                : (started ? "Arrêter Tomcat" : "Démarrer Tomcat (débogage sur le port " + TomcatLauncher.DEBUG_PORT + ")"));
        restartButton.getTooltip().setText(viaPlugin ? "Redémarrer Tomcat avec Sysdeo (dans Eclipse)"
                : "Redémarrer Tomcat (relancé hors d'Eclipse)");
        restartButton.setVisible(canStart && (started || restarting));
        restartButton.setDisable(pendingState != null);
        if (pendingState != null) {
            tomcatState.setText(restarting ? "Redémarrage…"
                    : pendingState == TomcatStatus.State.STARTED ? "Démarrage…" : "Arrêt…");
            setStateKind("pending");
            return;
        }
        String port = status.httpPort() > 0 ? " (port " + status.httpPort() + ")" : "";
        tomcatState.setText((started ? "Tomcat démarré" : "Tomcat arrêté") + port);
        setStateKind(started ? "started" : "stopped");
    }

    /** Couleur du texte d'état de Tomcat (classe CSS : started, stopped ou pending), selon le thème. */
    private void setStateKind(String kind) {
        tomcatState.getStyleClass().removeAll("started", "stopped", "pending");
        tomcatState.getStyleClass().add(kind);
    }

    /**
     * Démarre Tomcat (catalina jpda start, débogable depuis Eclipse sur le port 8000) ou l'arrête
     * (commande d'arrêt sur le port de server.xml), selon le voyant.
     */
    private void startOrStopTomcat() {
        TomcatStatus status = tomcatStatus;
        if (status == null || serverXml == null || pendingState != null) {
            return;
        }
        boolean started = status.check() == TomcatStatus.State.STARTED;
        if (eclipse != null) {
            // Via Eclipse : le plugin Sysdeo démarre ou arrête le Tomcat, dans la console et le débogueur d'Eclipse.
            runInEclipse(started ? EclipseMcp.SYSDEO_STOP : EclipseMcp.SYSDEO_START,
                    started ? TomcatStatus.State.STOPPED : TomcatStatus.State.STARTED, false);
            return;
        }
        if (started) {
            requestShutdown(status, false);
            return;
        }
        try {
            TomcatLauncher.start(serverXml.file());
            pendingState = TomcatStatus.State.STARTED;
            pendingDeadline = System.currentTimeMillis() + START_TIMEOUT_MS;
            showTomcatState(status, TomcatStatus.State.STOPPED);
        } catch (Exception ex) {
            showMessage("Démarrage impossible", ex.getMessage());
        }
    }

    /**
     * Redémarre Tomcat : commande d'arrêt, attente de l'arrêt complet, puis catalina jpda start.
     * Un Tomcat lancé depuis Eclipse est donc relancé hors d'Eclipse (débogable sur le port 8000).
     */
    private void restartTomcat() {
        TomcatStatus status = tomcatStatus;
        if (status == null || serverXml == null || pendingState != null) {
            return;
        }
        if (eclipse != null) {
            // Arrêt, puis démarrage, par Sysdeo : la commande « Redémarrer » de Sysdeo perd elle aussi son ordre
            // d'arrêt si Tomcat finit de démarrer, alors qu'ici l'arrêt est réessayé jusqu'à ce qu'il aboutisse.
            runInEclipse(EclipseMcp.SYSDEO_STOP, TomcatStatus.State.STOPPED, true);
            return;
        }
        requestShutdown(status, true);
    }

    /**
     * Confie une commande Sysdeo (démarrer, arrêter, redémarrer) à Eclipse, par le plugin MCP, hors du thread
     * JavaFX. Le voyant passe en orange jusqu'à ce que Tomcat atteigne l'état attendu.
     */
    private void runInEclipse(String command, TomcatStatus.State expected, boolean restart) {
        TomcatStatus status = tomcatStatus;
        viaEclipse = true;
        restarting = restart;
        stoppedPolls = 0;
        pendingState = expected;
        pendingDeadline = System.currentTimeMillis()
                + (expected == TomcatStatus.State.STARTED ? START_TIMEOUT_MS : ECLIPSE_STOP_TIMEOUT_MS);
        TomcatStatus.State before = expected == TomcatStatus.State.STARTED
                ? TomcatStatus.State.STOPPED : TomcatStatus.State.STARTED;
        lastEclipseStop = System.currentTimeMillis();
        showTomcatState(status, before);
        sendToEclipse(command, status, before);
    }

    /** Envoie la commande à Eclipse hors du thread JavaFX ; en cas d'échec, abandonne l'opération et prévient. */
    private void sendToEclipse(String command, TomcatStatus status, TomcatStatus.State stateOnError) {
        EclipseMcp mcp = eclipse;
        if (mcp == null) {
            mcp = lastEclipse;
        }
        EclipseMcp target = mcp;
        Thread sender = new Thread(() -> {
            try {
                target.runCommand(command);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    pendingState = null;
                    restarting = false;
                    viaEclipse = false;
                    showMessage("Eclipse n'a pas pu exécuter la commande", ex.getMessage());
                    showTomcatState(status, stateOnError);
                });
            }
        }, "eclipse-sysdeo");
        sender.setDaemon(true);
        sender.start();
    }

    /**
     * Envoie la commande d'arrêt hors du thread JavaFX : juste après le démarrage, Tomcat peut
     * répondre en HTTP avant d'ouvrir son port d'arrêt, d'où quelques secondes de nouvelles tentatives.
     */
    private void requestShutdown(TomcatStatus status, boolean restart) {
        restarting = restart;
        pendingState = TomcatStatus.State.STOPPED;
        pendingDeadline = System.currentTimeMillis() + STOP_TIMEOUT_MS;
        showTomcatState(status, TomcatStatus.State.STARTED);
        Thread sender = new Thread(() -> {
            try {
                waitDebugPort = restart && TomcatStatus.isListening(TomcatLauncher.DEBUG_PORT);
                status.shutdown(SHUTDOWN_RETRY_MS);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    pendingState = null;
                    restarting = false;
                    showMessage((restart ? "Redémarrage" : "Arrêt") + " impossible", ex.getMessage());
                    showTomcatState(status, TomcatStatus.State.STARTED);
                });
            }
        }, "tomcat-arret");
        sender.setDaemon(true);
        sender.start();
    }

    private void refresh() {
        refresh(true);
    }

    /**
     * Met à jour la liste depuis server.xml. Avec resort, l'ordre est recalculé : actifs d'abord, puis
     * alphabétique. Sans, les lignes gardent leur place (activer ou désactiver un Context ne la change pas).
     */
    private void refresh(boolean resort) {
        tomcatStatus = TomcatStatus.fromServerXml(serverXml.text());
        lastModified = modifiedTime(serverXml.file());
        java.util.List<ContextEntry> all = serverXml.contexts();
        if (resort || rankByIndex.length != all.size()) {
            java.util.List<ContextEntry> ordered = new java.util.ArrayList<>(all);
            ordered.sort(Comparator.comparing(ContextEntry::commented)
                    .thenComparing(ContextEntry::displayPath, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(ContextEntry::index));
            int[] ranks = new int[all.size()];
            for (int i = 0; i < ordered.size(); i++) {
                ranks[ordered.get(i).index()] = i;
            }
            rankByIndex = ranks;
        }
        contexts.setAll(all);
        updateCount();
        table.refresh();
        fitWindowToRows();
    }

    /**
     * Ajuste la fenêtre pour afficher exactement les lignes de la liste (après filtre), sans ligne vide
     * ni barre de défilement, tant que l'écran le permet.
     */
    private void fitWindowToRows() {
        if (header == null || stage.getScene() == null) {
            return;
        }
        Parent root = stage.getScene().getRoot();
        root.applyCss();
        Node columnHeader = table.lookup(".column-header-background");
        double headerHeight = columnHeader == null ? 0 : columnHeader.prefHeight(-1);
        double borders = table.getInsets().getTop() + table.getInsets().getBottom();
        // Taille fixe, calculée une seule fois : MAX_ROWS lignes visibles, au-delà la liste a son ascenseur.
        if (sized) {
            return;
        }
        sized = true;
        int rows = MAX_ROWS;
        double tableHeight = headerHeight + rows * ROW_HEIGHT + borders;

        double width = root.prefWidth(-1);
        // Hors tableau : marge et marges de la carte du bas, et ce qui l'entoure (boutons, recherche, statut).
        double listBoxExtra = listBox.getInsets().getTop() + listBox.getInsets().getBottom()
                + BorderPane.getMargin(listBox).getTop() + (listBox.getChildren().size() - 1) * listBox.getSpacing();
        for (Node child : listBox.getChildren()) {
            if (child != table) {
                listBoxExtra += child.prefHeight(-1);
            }
        }
        double others = header.prefHeight(width) + footer.prefHeight(width) + listBoxExtra;

        Rectangle2D screen = Screen.getScreensForRectangle(stage.getX(), stage.getY(), 1, 1).stream()
                .findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double decorations = Math.max(0, stage.getHeight() - stage.getScene().getHeight());
        double available = screen.getHeight() - decorations - others;
        if (tableHeight > available) {
            // Trop de lignes pour l'écran : un nombre entier de lignes, et une barre de défilement.
            int visible = (int) Math.max(1, Math.floor((available - headerHeight - borders) / ROW_HEIGHT));
            tableHeight = headerHeight + visible * ROW_HEIGHT + borders;
        }
        table.setPrefHeight(tableHeight);
        table.setMinHeight(tableHeight);
        table.setMaxHeight(tableHeight);
        ((Region) root).setPrefHeight(others + tableHeight);
        stage.sizeToScene();
        if (stage.getWidth() > screen.getWidth()) {
            stage.setWidth(screen.getWidth());
        }
        // Garde la fenêtre entièrement visible après l'agrandissement.
        if (stage.getY() + stage.getHeight() > screen.getMaxY()) {
            stage.setY(Math.max(screen.getMinY(), screen.getMaxY() - stage.getHeight()));
        }
        if (stage.getX() + stage.getWidth() > screen.getMaxX()) {
            stage.setX(Math.max(screen.getMinX(), screen.getMaxX() - stage.getWidth()));
        }
    }
}
