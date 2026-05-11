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
        // No usamos AlertHelper porque fuerza el tema claro de dialogs.css
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ayuda de Activación");
        alert.setHeaderText("Clave de Licencia");
        
        // Label personalizado: control total sobre el wrap y el ancho para evitar "..."
        Label contentLabel = new Label("Puedes encontrar tu clave en el correo de confirmación de compra configurado por el administrador.\n\n" +
                "Formato esperado: TPS-XXXX-XXXX-XXXX");
        contentLabel.setWrapText(true);
        contentLabel.setPrefWidth(400); 
        contentLabel.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 13px;");
        
        DialogPane pane = alert.getDialogPane();
        pane.setContent(contentLabel);
        
        // Cargamos app.css (tema oscuro) y aplicamos la clase alert-dialog
        pane.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        pane.getStyleClass().add("alert-dialog");
        
        // Fix quirúrgico: Forzar fondo oscuro en el pane y en la barra de botones (la falda)
        // para asegurar que nada de dialogs.css o del sistema manche de blanco
        pane.setStyle("-fx-background-color: #1e1a1c;");
        
        // Intentar aplicar estilo a la barra de botones si ya está disponible
        Platform.runLater(() -> {
            javafx.scene.Node buttonBar = pane.lookup(".button-bar");
            if (buttonBar != null) buttonBar.setStyle("-fx-background-color: #1e1a1c;");
        });
        
        alert.showAndWait();
    }

    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }
}
