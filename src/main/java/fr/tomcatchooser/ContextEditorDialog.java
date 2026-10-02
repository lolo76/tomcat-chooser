package fr.tomcatchooser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Window;

/**
 * Fenêtre de modification d'un {@code <Context>} : ses attributs, puis ceux de son élément Loader.
 * Les valeurs true/false sont des pastilles, placées sur la ligne du champ qui les précède.
 * Le résultat associe chaque balise présente à ses nouveaux attributs.
 * <p>
 * L'ajout d'un Context utilise exactement cette fenêtre, avec des champs vides ou préremplis.
 */
final class ContextEditorDialog extends Dialog<Map<String, Map<String, String>>> {

    /** Largeur de la zone de saisie. */
    private static final double CONTENT_WIDTH = 550;
    /** Marque, dans un modèle de docBase / workDir, la place du nom de l'application. */
    static final String NAME_MARK = "{nom}";

    /** Attributs modifiables, par balise, dans l'ordre d'affichage. */
    private final Map<String, List<Attribute>> attributesByTag = new LinkedHashMap<>();

    /** Un attribut et son champ de saisie (texte ou pastille true/false). Pas de record, comme ContextEntry. */
    private static final class Attribute {
        private final String name;
        private final Control field;

        Attribute(String name, Control field) {
            this.name = name;
            this.field = field;
        }

        String name() {
            return name;
        }

        Control field() {
            return field;
        }
    }

    /** Modification du Context donné. */
    static ContextEditorDialog edit(Window owner, ContextEntry entry, Map<String, Map<String, String>> byTag) {
        return new ContextEditorDialog(owner,
                "Application : " + entry.displayPath() + (entry.commented() ? "   (inactif)" : "   (actif)"),
                "Enregistrer", byTag, Map.of());
    }

    /**
     * Nouveau Context : la même fenêtre que la modification. Pendant la saisie du path, les champs dont un
     * modèle est fourni (clé = attribut, valeur contenant {@link #NAME_MARK}) suivent le nom saisi,
     * tant qu'ils n'ont pas été modifiés à la main.
     */
    static ContextEditorDialog add(Window owner, Map<String, Map<String, String>> byTag, Map<String, String> templates) {
        return new ContextEditorDialog(owner, "Application : nouvelle   (actif)", "Enregistrer", byTag, templates);
    }

    /** Copie du Context donné, avec ses éléments enfants ; la copie est créée active. */
    static ContextEditorDialog duplicate(Window owner, ContextEntry entry, Map<String, Map<String, String>> byTag) {
        return new ContextEditorDialog(owner,
                "Copie de " + entry.displayPath() + "   (la copie sera active)", "Dupliquer", byTag, Map.of());
    }

    /** Valeur d'un modèle pour un nom d'application (sans « / »). Vide : le séparateur en trop est retiré. */
    static String fill(String template, String name) {
        int at = template.indexOf(NAME_MARK);
        if (at < 0) {
            return template;
        }
        String before = template.substring(0, at);
        String after = template.substring(at + NAME_MARK.length());
        if (name.isEmpty() && !before.isEmpty() && !after.isEmpty()
                && "\\/".indexOf(before.charAt(before.length() - 1)) >= 0 && "\\/".indexOf(after.charAt(0)) >= 0) {
            after = after.substring(1);
        }
        return before + name + after;
    }

    /** @param byTag attributs par balise ; une valeur null signifie que la balise est absente. */
    private ContextEditorDialog(Window owner, String header, String okText,
                                Map<String, Map<String, String>> byTag, Map<String, String> templates) {
        initOwner(owner);
        setTitle("Context");
        setHeaderText(header);
        setResizable(true);
        Material.apply(this);

        // Une seule colonne de noms pour toutes les balises : les champs sont alignés.
        double nameWidth = byTag.values().stream().filter(a -> a != null)
                .flatMap(a -> a.entrySet().stream()).filter(e -> !isBoolean(e.getValue()))
                .mapToDouble(e -> new Text(e.getKey()).getLayoutBounds().getWidth()).max().orElse(60) + 12;

        VBox rows = new VBox(8);
        rows.setPadding(new Insets(14, 16, 8, 16));
        byTag.forEach((tag, attributes) -> {
            if (attributes == null || attributes.isEmpty()) {
                return;
            }
            if (!rows.getChildren().isEmpty()) {
                // Pas de cadre : un simple trait et le nom de la balise séparent ses attributs.
                Label caption = new Label(tag);
                caption.getStyleClass().add("section-caption");
                rows.getChildren().addAll(new Separator(), caption);
            }
            List<Attribute> list = new ArrayList<>();
            attributesByTag.put(tag, list);
            HBox previous = null;
            for (Map.Entry<String, String> e : attributes.entrySet()) {
                Label label = new Label(e.getKey());
                label.getStyleClass().add("attribute-name");
                if (isBoolean(e.getValue())) {
                    ToggleButton toggle = booleanButton(e.getValue());
                    list.add(new Attribute(e.getKey(), toggle));
                    if (previous != null) {
                        // true/false sur la ligne du champ précédent.
                        previous.getChildren().addAll(label, toggle);
                        continue;
                    }
                    label.setMinWidth(nameWidth);
                    label.setPrefWidth(nameWidth);
                    previous = new HBox(6, label, toggle);
                } else {
                    TextField field = new TextField(e.getValue());
                    HBox.setHgrow(field, Priority.ALWAYS);
                    list.add(new Attribute(e.getKey(), field));
                    label.setMinWidth(nameWidth);
                    label.setPrefWidth(nameWidth);
                    previous = new HBox(6, label, field);
                }
                previous.setAlignment(Pos.CENTER_LEFT);
                rows.getChildren().add(previous);
            }
        });
        autoFill(attributesByTag.get("Context"), templates);

        // Pas de zone défilante : la fenêtre prend la hauteur de ses quelques attributs.
        rows.setPrefWidth(CONTENT_WIDTH);
        getDialogPane().setContent(rows);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) getDialogPane().lookupButton(ButtonType.OK)).setText(okText);
        ((Button) getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Annuler");

        setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            Map<String, Map<String, String>> result = new LinkedHashMap<>();
            attributesByTag.forEach((tag, list) -> {
                Map<String, String> values = new LinkedHashMap<>();
                for (Attribute a : list) {
                    values.put(a.name(), a.field() instanceof ToggleButton t ? booleanValue(t) : ((TextField) a.field()).getText());
                }
                result.put(tag, values);
            });
            return result;
        });
    }

    /** Fait suivre le path aux champs qui ont un modèle (docBase, workDir…), tant qu'on n'y a pas touché. */
    private static void autoFill(List<Attribute> context, Map<String, String> templates) {
        if (context == null || templates.isEmpty()) {
            return;
        }
        TextField path = null;
        Map<TextField, String> template = new HashMap<>();
        Map<TextField, String> proposed = new HashMap<>();
        for (Attribute a : context) {
            if (!(a.field() instanceof TextField field)) {
                continue;
            }
            if (a.name().equals("path")) {
                path = field;
            } else if (templates.containsKey(a.name())) {
                template.put(field, templates.get(a.name()));
                proposed.put(field, field.getText());
            }
        }
        if (path == null) {
            return;
        }
        path.textProperty().addListener((obs, old, text) -> {
            String name = text.trim();
            name = name.startsWith("/") ? name.substring(1) : name;
            for (Map.Entry<TextField, String> e : template.entrySet()) {
                TextField field = e.getKey();
                if (field.getText().equals(proposed.get(field))) {
                    String value = fill(e.getValue(), name);
                    field.setText(value);
                    proposed.put(field, value);
                }
            }
        });
    }

    private static boolean isBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
    }

    private static ToggleButton booleanButton(String value) {
        ToggleButton button = new ToggleButton();
        button.getStyleClass().add("chip");
        button.setUserData(value); // valeur d'origine, pour ne pas changer sa casse si rien ne bouge
        button.setPrefWidth(70);
        button.setMinWidth(70);
        button.selectedProperty().addListener((obs, old, on) -> button.setText(on ? "true" : "false"));
        button.setSelected(value.equalsIgnoreCase("true"));
        button.setText(button.isSelected() ? "true" : "false");
        return button;
    }

    private static String booleanValue(ToggleButton button) {
        String original = (String) button.getUserData();
        return Boolean.parseBoolean(original) == button.isSelected() ? original : button.getText();
    }
}
