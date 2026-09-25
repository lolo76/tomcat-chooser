package fr.tomcatchooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javafx.application.Application;
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
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Fenêtre principale : liste des Context de server.xml avec un bouton pour (dé)commenter chacun. */
public class TomcatChooserApp extends Application {

    static final Path DEFAULT_SERVER_XML = Paths.get("C:\\Tomcat70\\conf\\server.xml");
    private static final String PREF_LAST_FILE = "lastServerXml";
    private static final double ROW_HEIGHT = 30;
    private static final int MIN_VISIBLE_ROWS = 3;
    private static final double STATUS_WIDTH = 110;
    /** Icône « recharger » (flèche circulaire, Material Design). */
    private static final String REFRESH_ICON = "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8"
            + "c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6"
            + "c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";
    /** Icône « dossier » (Material Design). */
    private static final String FOLDER_ICON = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8"
            + "c0-1.1-.9-2-2-2h-8l-2-2z";
    private static final double ICON_SIZE = 16;
    /** Icône « modifier » (crayon, Material Design). */
    private static final String EDIT_ICON = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z"
            + "M20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";

    private final Preferences prefs = Preferences.userNodeForPackage(TomcatChooserApp.class);
    private final TextField pathField = new TextField();
    private final TableView<ContextEntry> table = new TableView<>();
    private final Label status = new Label();
    private final TextField search = new TextField();
    private final Label tomcatState = new Label();
    private volatile TomcatStatus tomcatStatus;
    private final ObservableList<ContextEntry> contexts = FXCollections.observableArrayList();
    private final FilteredList<ContextEntry> filtered = new FilteredList<>(contexts);
    private Stage stage;
    private ServerXml serverXml;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        Button browse = iconButton(FOLDER_ICON);
        browse.setOnAction(e -> chooseFile());
        Button reload = iconButton(REFRESH_ICON);
        reload.setOnAction(e -> load(Paths.get(pathField.getText().trim())));
        Button editFile = iconButton(EDIT_ICON);
        editFile.setOnAction(e -> openInSystemEditor(Paths.get(pathField.getText().trim())));
        pathField.setOnAction(e -> load(Paths.get(pathField.getText().trim())));
        pathField.setPromptText("Chemin du server.xml");
        HBox icons = new HBox(4, browse, reload, editFile);
        icons.setAlignment(Pos.CENTER_LEFT);
        HBox top = new HBox(0, icons, pathField);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10, 11, 4, 11));

        search.setPromptText("Rechercher une application…");
        search.textProperty().addListener((obs, old, text) -> {
            String q = text.trim().toLowerCase();
            filtered.setPredicate(c -> q.isEmpty() || c.displayPath().toLowerCase().contains(q));
        });

        buildTable();
        // Le champ de recherche est placé au-dessus de la colonne Application, à sa largeur.
        TableColumn<ContextEntry, ?> statusColumn = table.getColumns().get(0);
        TableColumn<ContextEntry, ?> applicationColumn = table.getColumns().get(1);
        Region statusSpace = new Region();
        statusSpace.minWidthProperty().bind(statusColumn.widthProperty());
        statusSpace.prefWidthProperty().bind(statusColumn.widthProperty());
        search.minWidthProperty().bind(applicationColumn.widthProperty());
        search.prefWidthProperty().bind(applicationColumn.widthProperty());
        search.maxWidthProperty().bind(applicationColumn.widthProperty());
        // Les icônes occupent la largeur de la colonne Statut : le chemin s'aligne sur la recherche.
        icons.minWidthProperty().bind(statusColumn.widthProperty());
        icons.prefWidthProperty().bind(statusColumn.widthProperty());
        pathField.minWidthProperty().bind(applicationColumn.widthProperty());
        pathField.prefWidthProperty().bind(applicationColumn.widthProperty());
        pathField.maxWidthProperty().bind(applicationColumn.widthProperty());
        HBox searchBar = new HBox(0, statusSpace, search);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        // 1 px : bordure du tableau.
        searchBar.setPadding(new Insets(0, 11, 8, 11));

        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(status, Priority.ALWAYS);
        HBox bottom = new HBox(12, status, tomcatState);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(6, 10, 8, 10));

        BorderPane root = new BorderPane(table, new VBox(top, searchBar), null, bottom, null);
        BorderPane.setMargin(table, new Insets(0, 10, 0, 10));

        stage.setTitle("Tomcat Chooser");
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(
                    TomcatChooserApp.class.getResourceAsStream("tomcat-" + size + ".png")));
        }
        stage.setScene(new Scene(root));
        // Taille fixe : la fenêtre s'ajuste seule au nombre de Context (voir fitWindowToRows).
        stage.setResizable(false);
        stage.show();

        String param = getParameters().getRaw().isEmpty() ? null : getParameters().getRaw().get(0);
        String last = param != null ? param : prefs.get(PREF_LAST_FILE, DEFAULT_SERVER_XML.toString());
        load(Paths.get(last));
        startTomcatMonitor();
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
            private final Button button = new Button();

            {
                button.setMaxWidth(Double.MAX_VALUE);
                button.setOnAction(e -> toggle(getItem()));
            }

            @Override
            protected void updateItem(ContextEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    // Le bouton montre l'état ; un clic bascule vers l'autre état.
                    button.setText(item.commented() ? "Inactif" : "Actif");
                    button.setStyle(item.commented()
                            ? "-fx-base: #e0e0e0; -fx-text-fill: #666666;"
                            : "-fx-base: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
                    setGraphic(button);
                }
            }
        });
        action.setSortable(false);

        // Colonnes redimensionnables à la souris, sans barre de défilement horizontale.
        action.setPrefWidth(STATUS_WIDTH);
        action.setMinWidth(80);
        application.setMinWidth(80);
        application.setPrefWidth(200);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);

        SortedList<ContextEntry> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
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
        table.setPrefWidth(320);
        // Pas de ligne de titres au-dessus des colonnes.
        table.getStylesheets().add("data:text/css,"
                + ".column-header-background { -fx-pref-height: 0; -fx-min-height: 0; -fx-max-height: 0; visibility: hidden; }");
        table.setPlaceholder(new Label("Aucun <Context> trouvé dans ce fichier."));
    }

    /** Bouton carré avec une icône bleue, toutes les icônes ramenées à la même taille. */
    private static Button iconButton(String svg) {
        SVGPath path = new SVGPath();
        path.setContent(svg);
        path.setStyle("-fx-fill: #1565c0;");
        double scale = ICON_SIZE / Math.max(path.getLayoutBounds().getWidth(), path.getLayoutBounds().getHeight());
        path.setScaleX(scale);
        path.setScaleY(scale);
        Button button = new Button(null, new Group(path));
        button.setMinSize(32, 28);
        button.setPrefSize(32, 28);
        return button;
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

    /** Ouvre le fichier dans l'éditeur associé par le système (Bloc-notes à défaut). */
    private void openInSystemEditor(Path file) {
        if (!Files.isRegularFile(file)) {
            setStatus("Fichier introuvable : " + file, true);
            return;
        }
        Thread opener = new Thread(() -> {
            try {
                openWithDesktop(file.toFile());
                Platform.runLater(() -> setStatus("server.xml ouvert dans l'éditeur. Cliquez sur Recharger après l'avoir enregistré.", false));
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("Impossible d'ouvrir l'éditeur : " + ex.getMessage(), true));
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
            serverXml = null;
            tomcatStatus = null;
            contexts.clear();
            setStatus("Fichier introuvable : " + file + ". Cliquez sur « Parcourir… » pour choisir un server.xml.", true);
            return;
        }
        try {
            serverXml = ServerXml.load(file);
            prefs.put(PREF_LAST_FILE, file.toString());
            refresh();
            long active = serverXml.contexts().stream().filter(c -> !c.commented()).count();
            setStatus(serverXml.contexts().size() + " Context trouvé(s), dont " + active + " actif(s).", false);
        } catch (Exception ex) {
            serverXml = null;
            tomcatStatus = null;
            contexts.clear();
            setStatus("Impossible de lire " + file + " : " + ex.getMessage(), true);
        }
    }

    private void toggle(ContextEntry entry) {
        if (serverXml == null || entry == null) {
            return;
        }
        try {
            ContextEntry updated = serverXml.toggle(entry);
            refresh();
            select(updated);
            setStatus("Context " + updated.displayPath() + (updated.commented() ? " désactivé (commenté)" : " activé (décommenté)")
                    + ". Redémarrez Tomcat pour appliquer.", false);
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
        setStatus(ex.getMessage(), true);
        Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage());
        alert.setHeaderText("Modification impossible");
        alert.initOwner(stage);
        alert.showAndWait();
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
                new ContextEditorDialog(stage, entry, current).showAndWait();
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
            setStatus("Context " + updated.displayPath() + " modifié. Redémarrez Tomcat pour appliquer.", false);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void select(ContextEntry entry) {
        table.getItems().stream().filter(c -> c.index() == entry.index()).findFirst()
                .ifPresent(c -> table.getSelectionModel().select(c));
    }

    /** Vérifie toutes les 3 s si Tomcat tourne (ports de server.xml) et met à jour le voyant. */
    private void startTomcatMonitor() {
        tomcatState.setMinWidth(Region.USE_PREF_SIZE);
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tomcat-status");
            t.setDaemon(true);
            return t;
        });
        monitor.scheduleWithFixedDelay(() -> {
            TomcatStatus status = tomcatStatus;
            TomcatStatus.State state = status == null ? null : status.check();
            Platform.runLater(() -> showTomcatState(status, state));
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void showTomcatState(TomcatStatus status, TomcatStatus.State state) {
        if (status == null) {
            tomcatState.setText("");
            return;
        }
        boolean started = state == TomcatStatus.State.STARTED;
        String port = status.httpPort() > 0 ? " (port " + status.httpPort() + ")" : "";
        tomcatState.setText((started ? "● Tomcat démarré" : "● Tomcat arrêté") + port);
        tomcatState.setStyle(started
                ? "-fx-text-fill: #2e7d32; -fx-font-weight: bold;"
                : "-fx-text-fill: #c62828;");
    }

    private void refresh() {
        tomcatStatus = TomcatStatus.fromServerXml(serverXml.text());
        contexts.setAll(serverXml.contexts());
        table.refresh();
        fitWindowToRows();
    }

    /** Agrandit la fenêtre pour afficher toutes les lignes, sans dépasser l'écran. */
    private void fitWindowToRows() {
        table.applyCss();
        table.layout();
        Node header = table.lookup(".column-header-background");
        double headerHeight = header == null ? 0 : header.prefHeight(-1);
        int rows = Math.max(MIN_VISIBLE_ROWS, contexts.size());
        double tableHeight = headerHeight + rows * ROW_HEIGHT + 4;

        Rectangle2D screen = Screen.getScreensForRectangle(stage.getX(), stage.getY(), 1, 1).stream()
                .findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        Parent root = stage.getScene().getRoot();
        table.setPrefHeight(tableHeight);
        double others = root.prefHeight(-1) - tableHeight;
        double decorations = Math.max(0, stage.getHeight() - stage.getScene().getHeight());
        double available = screen.getHeight() - decorations - others;
        if (tableHeight > available) {
            table.setPrefHeight(Math.max(headerHeight + ROW_HEIGHT, available));
        }
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

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.setStyle(error ? "-fx-text-fill: #c62828;" : "");
    }
}
