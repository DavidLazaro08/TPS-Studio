package com.tpsstudio.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/**
 * Utility class to create Alert dialogs that automatically apply the application's dialogs.css stylesheet.
 */
public class AlertHelper {

    private static final String CSS = AlertHelper.class.getResource("/css/dialogs.css").toExternalForm();

    public static Alert createAlert(Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.getDialogPane().getStylesheets().add(CSS);
        return alert;
    }

    public static Alert createAlert(Alert.AlertType alertType, String contentText, ButtonType... buttons) {
        Alert alert = new Alert(alertType, contentText, buttons);
        alert.getDialogPane().getStylesheets().add(CSS);
        return alert;
    }
}
