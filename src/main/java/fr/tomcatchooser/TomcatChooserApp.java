package fr.tomcatchooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Fenêtre principale : liste des Context de server.xml avec un bouton pour (dé)commenter chacun. */
public class TomcatChooserApp extends Application {

    static final Path DEFAULT_SERVER_XML = Paths.get("C:\\Tomcat70\\conf\\server.xml");
    private static final String PREF_LAST_FILE = "lastServerXml";

    private final Preferences prefs = Preferences.userNodeForPackage(TomcatChooserApp.class);
    private final TextField pathField = new TextField();
    private final TableView<ContextEntry> table = new TableView<>();
    private final Label status = new Label();
    private Stage stage;
    private ServerXml serverXml;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        Button browse = new Button("Parcourir…");
        browse.setOnAction(e -> chooseFile());
        Button reload = new Button("Recharger");
        reload.setOnAction(e -> load(Paths.get(pathField.getText().trim())));
        pathField.setOnAction(e -> load(Paths.get(pathField.getText().trim())));
        HBox.setHgrow(pathField, Priority.ALWAYS);
        HBox top = new HBox(8, new Label("server.xml :"), pathField, browse, reload);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));

        buildTable();

        status.setPadding(new Insets(6, 10, 8, 10));
        status.setWrapText(true);

        BorderPane root = new BorderPane(table, top, null, status, null);
        BorderPane.setMargin(table, new Insets(0, 10, 0, 10));

        stage.setTitle("Tomcat Chooser");
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(
                    TomcatChooserApp.class.getResourceAsStream("tomcat-" + size + ".png")));
        }
        stage.setScene(new Scene(root, 900, 500));
        stage.show();

        String param = getParameters().getRaw().isEmpty() ? null : getParameters().getRaw().get(0);
        String last = param != null ? param : prefs.get(PREF_LAST_FILE, DEFAULT_SERVER_XML.toString());
        load(Paths.get(last));
    }

    private void buildTable() {
        TableColumn<ContextEntry, ContextEntry> state = new TableColumn<>("État");
        state.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        state.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ContextEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else if (item.commented()) {
                    setText("○ Commenté");
                    setStyle("-fx-text-fill: #888888;");
                } else {
                    setText("● Actif");
                    setStyle("-fx-text-fill: #1a7f37; -fx-font-weight: bold;");
                }
            }
        });
        state.setPrefWidth(110);

        TableColumn<ContextEntry, String> path = new TableColumn<>("Path");
        path.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().displayPath()));
        path.setPrefWidth(180);

        TableColumn<ContextEntry, String> docBase = new TableColumn<>("DocBase");
        docBase.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().docBase()));
        docBase.setPrefWidth(400);

        TableColumn<ContextEntry, Number> line = new TableColumn<>("Ligne");
        line.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().line()));
        line.setPrefWidth(60);

        TableColumn<ContextEntry, ContextEntry> action = new TableColumn<>("Action");
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
                    button.setText(item.commented() ? "Décommenter" : "Commenter");
                    button.setTooltip(new Tooltip(item.rawText()));
                    setGraphic(button);
                }
            }
        });
        action.setPrefWidth(120);
        action.setSortable(false);

        table.getColumns().add(state);
        table.getColumns().add(path);
        table.getColumns().add(docBase);
        table.getColumns().add(line);
        table.getColumns().add(action);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucun <Context> trouvé dans ce fichier."));
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

    private void load(Path file) {
        pathField.setText(file.toString());
        if (!Files.isRegularFile(file)) {
            serverXml = null;
            table.setItems(FXCollections.observableArrayList());
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
            table.setItems(FXCollections.observableArrayList());
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
            table.getSelectionModel().select(updated.index());
            setStatus("Context " + updated.displayPath() + (updated.commented() ? " commenté" : " décommenté")
                    + ". Sauvegarde précédente : " + serverXml.file().getFileName()
                    + ".bak. Redémarrez Tomcat pour appliquer.", false);
        } catch (Exception ex) {
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
    }

    private void refresh() {
        table.setItems(FXCollections.observableArrayList(serverXml.contexts()));
        table.refresh();
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.setStyle(error ? "-fx-text-fill: #c62828;" : "");
    }
}
