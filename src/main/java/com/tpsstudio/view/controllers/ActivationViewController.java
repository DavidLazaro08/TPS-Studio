package com.tpsstudio.view.controllers;

import com.tpsstudio.service.AuthService;
import com.tpsstudio.util.TPSToast;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * Controlador de la ventana de activación.
 *
 * Permite registrar una licencia local y crear el primer usuario de acceso.
 */
public class ActivationViewController {

    @FXML
    private TextField txtUsername;
    @FXML
    private TextField txtEmail;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private TextField txtLicense;
    @FXML
    private Label lblError;

    private final AuthService authService = AuthService.getInstance();

    // =====================================================
    // Activación
    // =====================================================

    @FXML
    private void handleActivate() {
        lblError.setVisible(false);
        lblError.setManaged(false);

        String username = txtUsername.getText();
        String email = txtEmail.getText();
        String password = txtPassword.getText();
        String license = txtLicense.getText();

        if (username.isBlank() || email.isBlank() || password.isBlank() || license.isBlank()) {
            showError("Todos los campos son obligatorios.");
            return;
        }

        if (!email.contains("@")) {
            showError("Introduce un correo electrónico válido.");
            return;
        }

        boolean success = authService.activate(license, username, email, password);

        if (success) {
            TPSToast.mostrar(
                    txtUsername.getScene().getWindow(),
                    "¡Software activado con éxito!",
                    "Ya puedes iniciar sesión con tus credenciales.",
                    TPSToast.Tipo.EXITO
            );

            Platform.runLater(() -> {
                Stage stage = (Stage) txtUsername.getScene().getWindow();
                stage.close();
            });

        } else {
            showError("Clave de licencia no válida. El formato es TPS-XXXX...");
        }
    }

    // =====================================================
    // Ayuda
    // =====================================================

    @FXML
    private void handleHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ayuda de Activación");
        alert.setHeaderText("Clave de Licencia");

        Label contentLabel = new Label(
                "Puedes encontrar tu clave en el correo de confirmación de compra configurado por el administrador.\n\n" +
                        "Formato esperado: TPS-XXXX-XXXX-XXXX"
        );
        contentLabel.setWrapText(true);
        contentLabel.setPrefWidth(400);
        contentLabel.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 13px;");

        DialogPane pane = alert.getDialogPane();
        pane.setContent(contentLabel);
        pane.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        pane.getStyleClass().add("alert-dialog");
        pane.setStyle("-fx-background-color: #1e1a1c;");

        Platform.runLater(() -> {
            javafx.scene.Node buttonBar = pane.lookup(".button-bar");
            if (buttonBar != null) {
                buttonBar.setStyle("-fx-background-color: #1e1a1c;");
            }
        });

        alert.showAndWait();
    }

    // =====================================================
    // Mensajes
    // =====================================================

    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }
}