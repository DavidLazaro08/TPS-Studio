package com.tpsstudio.view;

import com.tpsstudio.viewmodel.LoginViewModel;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.prefs.Preferences;

public class LoginViewController {

    @FXML
    private VBox loginCard;

    @FXML
    private TextField txtUser;

    @FXML
    private PasswordField txtPass;

    @FXML
    private TextField txtPassVisible;

    @FXML
    private Button btnTogglePassword;

    @FXML
    private SVGPath eyeIcon;

    @FXML
    private CheckBox chkRememberMe;

    @FXML
    private Label lblError;

    @FXML
    private Hyperlink linkCreateAccount;

    private LoginViewModel viewModel;
    private boolean isPasswordVisible = false;

    // Clave para Preferences
    private static final String PREF_REMEMBERED_USER = "remembered_user";

    // SVG paths para los iconos de ojo
    private static final String EYE_OPEN = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String EYE_CLOSED = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z";

    public LoginViewController() {
        this.viewModel = new LoginViewModel();
    }

    @FXML
    private void initialize() {
        // Bindings bidireccionales entre la vista y el ViewModel
        txtUser.textProperty().bindBidirectional(viewModel.userProperty());
        txtPass.textProperty().bindBidirectional(viewModel.passProperty());
        txtPassVisible.textProperty().bindBidirectional(viewModel.passProperty());

        // Cargar usuario guardado si existe
        loadRememberedUser();

        // Listener para el checkbox "Recordarme"
        chkRememberMe.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (!isSelected) {
                // Si se desmarca, limpiar usuario guardado
                clearRememberedUser();
            }
        });

        // Limpiar error al escribir
        txtUser.textProperty().addListener((obs, old, newVal) -> lblError.setText(""));
        txtPass.textProperty().addListener((obs, old, newVal) -> lblError.setText(""));
        txtPassVisible.textProperty().addListener((obs, old, newVal) -> lblError.setText(""));

        // Animación de entrada
        playEntranceAnimation();
    }

    @FXML
    private void onLogin() {
        // Limpiar mensaje de error previo
        lblError.setText("");

        // Validar campos vacíos
        String user = viewModel.getUser();
        String pass = viewModel.getPass();

        if (user == null || user.trim().isEmpty()) {
            showError("Introduce el usuario");
            return;
        }

        if (pass == null || pass.trim().isEmpty()) {
            showError("Introduce la contraseña");
            return;
        }

        // Validar credenciales
        if (viewModel.validateLogin()) {
            // Guardar usuario si "Recordarme" está marcado
            if (chkRememberMe.isSelected()) {
                saveRememberedUser(user);
            } else {
                clearRememberedUser();
            }

            // Login exitoso - cambiar a la pantalla principal
            openMainView();
        } else {
            // Login fallido - mostrar mensaje de error con animación
            showError("Usuario o contraseña incorrectos");
            playShakeAnimation();
        }
    }

    @FXML
    private void onTogglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            // Mostrar contraseña
            txtPassVisible.setVisible(true);
            txtPassVisible.setManaged(true);
            txtPass.setVisible(false);
            txtPass.setManaged(false);
            txtPassVisible.requestFocus();
            txtPassVisible.end(); // Mover cursor al final

            // Cambiar icono a ojo cerrado/tachado
            eyeIcon.setContent(EYE_CLOSED);
        } else {
            // Ocultar contraseña
            txtPass.setVisible(true);
            txtPass.setManaged(true);
            txtPassVisible.setVisible(false);
            txtPassVisible.setManaged(false);
            txtPass.requestFocus();
            txtPass.end(); // Mover cursor al final

            // Cambiar icono a ojo abierto
            eyeIcon.setContent(EYE_OPEN);
        }
    }

    @FXML
    private void onRequestAccess() {
        // Mostrar alerta informativa
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Solicitar acceso");
        alert.setHeaderText("Función en desarrollo");
        alert.setContentText("Esta funcionalidad estará disponible en próximas versiones.");

        // Aplicar tema oscuro al diálogo (opcional, mejora visual)
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("alert-dialog");

        alert.showAndWait();
    }

    private void showError(String message) {
        lblError.setText(message);
    }

    private void playEntranceAnimation() {
        // Fade in
        FadeTransition fade = new FadeTransition(Duration.millis(300), loginCard);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        // Translate (deslizar desde arriba)
        TranslateTransition translate = new TranslateTransition(Duration.millis(300), loginCard);
        translate.setFromY(20);
        translate.setToY(0);

        // Ejecutar ambas animaciones
        fade.play();
        translate.play();
    }

    private void playShakeAnimation() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), loginCard);
        shake.setFromX(0);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.play();
    }

    private void loadRememberedUser() {
        Preferences prefs = Preferences.userNodeForPackage(LoginViewController.class);
        String rememberedUser = prefs.get(PREF_REMEMBERED_USER, "");
        if (!rememberedUser.isEmpty()) {
            txtUser.setText(rememberedUser);
            chkRememberMe.setSelected(true);
        }
    }

    private void saveRememberedUser(String user) {
        Preferences prefs = Preferences.userNodeForPackage(LoginViewController.class);
        prefs.put(PREF_REMEMBERED_USER, user);
    }

    private void clearRememberedUser() {
        Preferences prefs = Preferences.userNodeForPackage(LoginViewController.class);
        prefs.remove(PREF_REMEMBERED_USER);
    }

    private void openMainView() {
        try {
            // Obtener el Stage actual desde cualquier nodo de la escena
            Stage stage = (Stage) txtUser.getScene().getWindow();

            // Cargar el FXML de la vista principal
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/main_view.fxml"));

            // Crear nueva escena SIN tamaño fijo para que se adapte
            Scene scene = new Scene(loader.load());

            // Aplicar la hoja de estilos CSS
            scene.getStylesheets().add(
                    getClass().getResource("/css/app.css").toExternalForm());

            // Cambiar la escena en el Stage
            stage.setScene(scene);
            stage.setTitle("TPS Studio");

            // Establecer tamaños mínimos
            // Panel izq (240) + Canvas mínimo (500) + Panel der (380) + márgenes = 1150px
            stage.setMinWidth(1150);
            stage.setMinHeight(700);

            // MAXIMIZAR la ventana para aprovechar todo el espacio
            javafx.application.Platform.runLater(() -> {
                stage.setMaximized(true);
            });

        } catch (Exception e) {
            lblError.setText("Error al cargar la aplicación");
            e.printStackTrace();
        }
    }
}
