package fr.tomcatchooser;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Fenêtre de modification d'un {@code <Context>} : ses attributs, et ceux de ses éléments Logger et Loader.
 * Le résultat associe chaque balise présente à ses nouveaux attributs.
 */
final class ContextEditorDialog extends Dialog<Map<String, Map<String, String>>> {

    private static final double NAME_WIDTH = 230;

    private final Map<String, AttributeEditor> editors = new LinkedHashMap<>();

    /** @param byTag attributs par balise ; une valeur null signifie que la balise est absente. */
    ContextEditorDialog(Window owner, ContextEntry entry, Map<String, Map<String, String>> byTag) {
        initOwner(owner);
        setTitle("Modifier le Context");
        setHeaderText("Application : " + entry.displayPath() + (entry.commented() ? "   (inactif)" : "   (actif)"));
        setResizable(true);

        VBox sections = new VBox(8);
        byTag.forEach((tag, attributes) -> {
            Node body;
            if (attributes == null) {
                Label none = new Label("Aucun <" + tag + "> dans ce Context.");
                none.setStyle("-fx-text-fill: #888888;");
                body = none;
            } else {
                AttributeEditor editor = new AttributeEditor(attributes);
                editors.put(tag, editor);
                body = editor;
            }
            TitledPane pane = new TitledPane(tag, body);
            pane.setExpanded(attributes != null);
            sections.getChildren().add(pane);
        });

        ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(520);
        scroll.setPrefViewportWidth(820);
        getDialogPane().setContent(scroll);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) getDialogPane().lookupButton(ButtonType.OK)).setText("Enregistrer");
        ((Button) getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Annuler");

        setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            Map<String, Map<String, String>> result = new LinkedHashMap<>();
            editors.forEach((tag, editor) -> result.put(tag, editor.values()));
            return result;
        });
    }

    /** Liste d'attributs modifiable : valeur, suppression (✕) et ajout. true/false s'affichent en bouton. */
    private static final class AttributeEditor extends VBox {

        private final VBox rows = new VBox(6);

        AttributeEditor(Map<String, String> attributes) {
            super(10);
            attributes.forEach(this::addRow);

            TextField newName = new TextField();
            newName.setPromptText("nouvel attribut");
            newName.setPrefWidth(NAME_WIDTH);
            newName.setMinWidth(NAME_WIDTH);
            TextField newValue = new TextField();
            newValue.setPromptText("valeur (true / false pour un bouton)");
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
            getChildren().addAll(rows, addRow);
        }

        private void addRow(String name, String value) {
            // Un attribut déjà présent est remplacé plutôt que dupliqué.
            rows.getChildren().removeIf(node -> ((Label) ((HBox) node).getChildren().get(0)).getText().equals(name));
            Label label = new Label(name);
            label.setPrefWidth(NAME_WIDTH);
            label.setMinWidth(NAME_WIDTH);
            Control field = isBoolean(value) ? booleanButton(value) : new TextField(value);
            if (field instanceof TextField) {
                HBox.setHgrow(field, Priority.ALWAYS);
            }
            Button remove = new Button("✕");
            remove.setTooltip(new Tooltip("Supprimer l'attribut " + name));
            HBox spacer = new HBox();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = field instanceof TextField
                    ? new HBox(6, label, field, remove)
                    : new HBox(6, label, field, spacer, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            remove.setOnAction(e -> rows.getChildren().remove(row));
            rows.getChildren().add(row);
        }

        private static boolean isBoolean(String value) {
            return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
        }

        private static ToggleButton booleanButton(String value) {
            ToggleButton button = new ToggleButton();
            button.setUserData(value); // valeur d'origine, pour ne pas changer sa casse si rien ne bouge
            button.setPrefWidth(90);
            button.selectedProperty().addListener((obs, old, on) -> style(button, on));
            button.setSelected(value.equalsIgnoreCase("true"));
            style(button, button.isSelected());
            return button;
        }

        private static void style(ToggleButton button, boolean on) {
            button.setText(on ? "true" : "false");
            button.setStyle(on
                    ? "-fx-base: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;"
                    : "-fx-base: #e0e0e0; -fx-text-fill: #666666;");
        }

        private static String booleanValue(ToggleButton button) {
            String original = (String) button.getUserData();
            return Boolean.parseBoolean(original) == button.isSelected() ? original : button.getText();
        }

        Map<String, String> values() {
            Map<String, String> result = new LinkedHashMap<>();
            for (Node node : rows.getChildren()) {
                HBox row = (HBox) node;
                String name = ((Label) row.getChildren().get(0)).getText();
                Node field = row.getChildren().get(1);
                result.put(name, field instanceof ToggleButton t ? booleanValue(t) : ((TextField) field).getText());
            }
            return result;
        }
    }
}
