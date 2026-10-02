package fr.tomcatchooser;

import javafx.scene.control.Dialog;

/** Thème Material (feuille de style partagée par la fenêtre principale et les boîtes de dialogue), clair ou sombre. */
final class Material {

    static final String STYLESHEET = Material.class.getResource("material.css").toExternalForm();
    static final String DARK = "dark";

    /** Mode sombre actif : mis à jour par la fenêtre principale, lu à l'ouverture de chaque boîte de dialogue. */
    private static volatile boolean dark;

    private Material() {
    }

    static void setDark(boolean value) {
        dark = value;
    }

    static boolean isDark() {
        return dark;
    }

    /** Applique le thème (feuille de style, et mode sombre si actif) à une boîte de dialogue, Alert comprise. */
    static void apply(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(STYLESHEET);
        if (dark) {
            dialog.getDialogPane().getStyleClass().add(DARK);
        }
    }
}
