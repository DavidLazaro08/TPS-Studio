package com.tpsstudio.view.controllers;

import com.tpsstudio.viewmodel.LoginViewModel;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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

/**
 * Controlador de la pantalla de login.
 *
 * Gestiona el acceso de usuario, la visibilidad de contraseña,
 * el recordatorio de usuario y el paso a la vista principal.
 */
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

    private static final String PREF_REMEMBERED_USER = "remembered_user";

    private static final String EYE_OPEN = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String EYE_CLOSED = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z";

    public LoginViewController() {
        this.viewModel = new LoginViewModel();
    }

    @FXML
    private void initialize() {
        txtUser.textProperty().bindBidirectional(viewModel.userProperty());
        txtPass.textProperty().bindBidirectional(viewModel.passProperty());
        txtPassVisible.textProperty().bindBidirectional(viewModel.passProperty());

        if (!com.tpsstudio.service.AuthService.getInstance().isActivated()) {
            lblError.setText("SISTEMA NO ACTIVADO. Por favor, solicita acceso o introduce tu licencia.");
            lblError.getStyleClass().add("error-label");
        }

        loadRememberedUser();

        chkRememberMe.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (!isSelected) {
                clearRememberedUser();
            }
        });

        txtUser.textProperty().addListener((obs, old, newVal) -> lblError.setText(""));
        txtPass.textProperty().addListener((obs, old, newVal) -> lblError.setText(""));
        txtPassVisible.textProperty().addListener((obs, old, newVal) -> lblError.setText(""));

        playEntranceAnimation();
    }

    // =====================================================
    // Acceso
    // =====================================================

    @FXML
    private void onLogin() {
        lblError.setText("");

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

        if (viewModel.validateLogin()) {
            if (chkRememberMe.isSelected()) {
                saveRememberedUser(user);
            } else {
                clearRememberedUser();
            }

            openMainView();

        } else {
            showError("Usuario o contraseña incorrectos");
            playShakeAnimation();
        }
    }

    @FXML
    private void onRequestAccess() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/activation_view.fxml"));
            javafx.scene.Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Activación de TPS Studio");
            stage.initOwner(txtUser.getScene().getWindow());
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);

            try (java.io.InputStream is = getClass().getResourceAsStream("/img/Icono_TPS.png")) {
                if (is != null) stage.getIcons().add(new javafx.scene.image.Image(is));
            } catch (Exception ignored) {
            }

            root.setOpacity(0.0);
            stage.setOnShown(e -> {
                FadeTransition ft = new FadeTransition(Duration.millis(300), root);
                ft.setToValue(1.0);
                ft.play();
            });

            stage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
            showError("No se pudo cargar la pantalla de activación");
        }
    }

    // =====================================================
    // Contraseña visible / oculta
    // =====================================================

    @FXML
    private void onTogglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            txtPassVisible.setVisible(true);
            txtPassVisible.setManaged(true);
            txtPass.setVisible(false);
            txtPass.setManaged(false);
            txtPassVisible.requestFocus();
            txtPassVisible.end();

            eyeIcon.setContent(EYE_CLOSED);

        } else {
            txtPass.setVisible(true);
            txtPass.setManaged(true);
            txtPassVisible.setVisible(false);
            txtPassVisible.setManaged(false);
            txtPass.requestFocus();
            txtPass.end();

            eyeIcon.setContent(EYE_OPEN);
        }
    }

    // =====================================================
    // Mensajes y animaciones
    // =====================================================

    private void showError(String message) {
        lblError.setText(message);
    }

    private void playEntranceAnimation() {
        FadeTransition fade = new FadeTransition(Duration.millis(300), loginCard);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        TranslateTransition translate = new TranslateTransition(Duration.millis(300), loginCard);
        translate.setFromY(20);
        translate.setToY(0);

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

    // =====================================================
    // Recordar usuario
    // =====================================================

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

    // =====================================================
    // Transición a vista principal
    // =====================================================

    private void openMainView() {
        try {
            Stage stage = (Stage) txtUser.getScene().getWindow();
            Scene scene = txtUser.getScene();

            javafx.scene.Parent loginView = scene.getRoot();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_view.fxml"));
            javafx.scene.Parent mainView = loader.load();
            mainView.setOpacity(0);

            javafx.scene.layout.StackPane transitionContainer = new javafx.scene.layout.StackPane();
            transitionContainer.getStyleClass().add("transition-overlay");

            scene.setRoot(transitionContainer);
            transitionContainer.getChildren().addAll(loginView, mainView);

            String css = getClass().getResource("/css/app.css").toExternalForm();
            if (!scene.getStylesheets().contains(css)) {
                scene.getStylesheets().add(css);
            }

            stage.setTitle("TPS Studio");

            javafx.application.Platform.runLater(() -> {
                Duration durationExit = Duration.millis(250);
                Duration durationEnter = Duration.millis(350);
                javafx.animation.Interpolator interpolator = javafx.animation.Interpolator.EASE_BOTH;

                FadeTransition fadeOut = new FadeTransition(durationExit, loginView);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setInterpolator(interpolator);

                javafx.animation.ScaleTransition scaleOut = new javafx.animation.ScaleTransition(durationExit, loginView);
                scaleOut.setFromX(1.0);
                scaleOut.setFromY(1.0);
                scaleOut.setToX(0.9);
                scaleOut.setToY(0.9);
                scaleOut.setInterpolator(interpolator);

                javafx.animation.ParallelTransition exitTransition =
                        new javafx.animation.ParallelTransition(fadeOut, scaleOut);

                FadeTransition fadeIn = new FadeTransition(durationEnter, mainView);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.setInterpolator(interpolator);
                fadeIn.setDelay(Duration.millis(50));

                exitTransition.setOnFinished(e -> {
                    stage.setMaximized(true);
                    stage.setMinWidth(1150);
                    stage.setMinHeight(700);
                    fadeIn.play();
                });

                fadeIn.setOnFinished(e -> {
                    transitionContainer.getChildren().clear();
                    scene.setRoot(mainView);
                });

                exitTransition.play();
            });

        } catch (Exception e) {
            lblError.setText("Error al cargar la aplicación");
            e.printStackTrace();
        }
    }
}