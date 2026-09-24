package fr.tomcatchooser;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Fenêtre de modification des attributs d'un {@code <Context>} : modifier, supprimer, ajouter. */
final class ContextEditorDialog extends Dialog<Map<String, String>> {

    private static final double NAME_WIDTH = 160;

    private final VBox rows = new VBox(6);

    ContextEditorDialog(Window owner, ContextEntry entry, Map<String, String> attributes) {
        initOwner(owner);
        setTitle("Modifier le Context");
        setHeaderText("Application : " + entry.displayPath()
                + (entry.commented() ? "   (inactif)" : "   (actif)"));
        setResizable(true);

        attributes.forEach(this::addRow);

        TextField newName = new TextField();
        newName.setPromptText("nouvel attribut");
        newName.setPrefWidth(NAME_WIDTH);
        TextField newValue = new TextField();
        newValue.setPromptText("valeur");
        HBox.setHgrow(newValue, Priority.ALWAYS);
        Button add = new Button("Ajouter");
        add.setOnAction(e -> {
            String name = newName.getText().trim();
            if (!name.isEmpty()) {
                addRow(name, newValue.getText());
                newName.clear();
                newValue.clear();
                newName.requestFocus();
            }
        });
        HBox addRow = new HBox(6, newName, newValue, add);
        addRow.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(400, 36 * Math.max(3, attributes.size())));
        scroll.setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(10, scroll, new Separator(), addRow);
        content.setPadding(new Insets(10));
        content.setPrefWidth(720);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) getDialogPane().lookupButton(ButtonType.OK)).setText("Enregistrer");
        ((Button) getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Annuler");

        setResultConverter(button -> button == ButtonType.OK ? collect() : null);
    }

    private void addRow(String name, String value) {
        // Un attribut déjà présent est remplacé plutôt que dupliqué.
        for (var node : rows.getChildren()) {
            HBox row = (HBox) node;
            if (((Label) row.getChildren().get(0)).getText().equals(name)) {
                ((TextField) row.getChildren().get(1)).setText(value);
                return;
            }
        }
        Label label = new Label(name);
        label.setPrefWidth(NAME_WIDTH);
        label.setMinWidth(NAME_WIDTH);
        TextField field = new TextField(value);
        HBox.setHgrow(field, Priority.ALWAYS);
        Button remove = new Button("✕");
        remove.setTooltip(new Tooltip("Supprimer l'attribut " + name));
        HBox row = new HBox(6, label, field, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        remove.setOnAction(e -> rows.getChildren().remove(row));
        rows.getChildren().add(row);
    }

    private Map<String, String> collect() {
        Map<String, String> result = new LinkedHashMap<>();
        for (var node : rows.getChildren()) {
            HBox row = (HBox) node;
            result.put(((Label) row.getChildren().get(0)).getText(), ((TextField) row.getChildren().get(1)).getText());
        }
        return result;
    }
}
