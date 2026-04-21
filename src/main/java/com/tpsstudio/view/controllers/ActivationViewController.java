package com.tpsstudio.view.controllers;

import com.tpsstudio.service.AuthService;
import com.tpsstudio.util.TPSToast;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * Controlador de la vista de activación.
 */
public class ActivationViewController {

    @FXML private TextField txtUsername;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtLicense;
    @FXML private Label lblError;

    private final AuthService authService = AuthService.getInstance();

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

        // Simulación de activación local
        boolean success = authService.activate(license, username, email, password);

        if (success) {
            TPSToast.mostrar(txtUsername.getScene().getWindow(), 
                "¡Software activado con éxito!", "Ya puedes iniciar sesión con tus credenciales.", TPSToast.Tipo.EXITO);
            
            // Cerrar la ventana tras éxito
            Platform.runLater(() -> {
                Stage stage = (Stage) txtUsername.getScene().getWindow();
                stage.close();
            });
        } else {
            showError("Clave de licencia no válida. El formato es TPS-XXXX...");
        }
    }

    @FXML
    private void handleHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ayuda de Activación");
        alert.setHeaderText("Clave de Licencia");
        alert.setContentText("Puedes encontrar tu clave en el correo de confirmación de compra configurado por el administrador.\n\n" +
                "Formato esperado: TPS-XXXX-XXXX-XXXX");
        
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("alert-dialog");
        
        alert.showAndWait();
    }

    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }
}
