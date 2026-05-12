package com.tpsstudio.view.controllers.sub;

import com.tpsstudio.service.AuthService;
import com.tpsstudio.util.AlertHelper;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Sub-controlador encargado de gestionar la sesión del usuario y las transiciones
 * de nivel superior (como la vuelta al Login).
 */
public class SessionController {

    private final Parent rootNode; // Nodo para obtener la escena/ventana

    public SessionController(Parent rootNode) {
        this.rootNode = rootNode;
    }

    /**
     * Ejecuta el proceso de cierre de sesión con confirmación y animación.
     */
    public void logout() {
        Alert alert = AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cerrar Sesión");
        alert.setHeaderText("Vas a salir de la sesión actual.");
        alert.setContentText("¿Estás seguro de que quieres volver al login?");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            ejecutarTransicionSalida();
        }
    }

    private void ejecutarTransicionSalida() {
        try {
            // 1. Limpiar sesión en el servicio
            AuthService.getInstance().logout();

            // 2. Obtener Stage y Escena actual
            Stage stage = (Stage) rootNode.getScene().getWindow();
            Scene scene = rootNode.getScene();
            Parent mainView = scene.getRoot();

            // 3. Cargar la vista de Login (invisible al inicio)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login_view.fxml"));
            Parent loginView = loader.load();
            loginView.setOpacity(0);
            loginView.setScaleX(1.05);
            loginView.setScaleY(1.05);

            // 4. Contenedor de transición
            StackPane transitionContainer = new StackPane();
            transitionContainer.getStyleClass().add("transition-overlay");
            scene.setRoot(transitionContainer);
            transitionContainer.getChildren().addAll(loginView, mainView);

            // 5. Animación
            Platform.runLater(() -> {
                Duration duration = Duration.millis(300);

                FadeTransition fadeMain = new FadeTransition(duration, mainView);
                fadeMain.setFromValue(1.0);
                fadeMain.setToValue(0.0);

                fadeMain.setOnFinished(e -> {
                    transitionContainer.getChildren().clear();
                    
                    // Reset de ventana
                    stage.setMaximized(false);
                    stage.setMinWidth(0);
                    stage.setMinHeight(0);

                    scene.setRoot(loginView);

                    // Reaplicar CSS
                    String css = getClass().getResource("/css/app.css").toExternalForm();
                    if (!scene.getStylesheets().contains(css)) {
                        scene.getStylesheets().add(css);
                    }

                    stage.setWidth(776); 
                    stage.setHeight(619);

                    Platform.runLater(() -> {
                        stage.sizeToScene();
                        stage.centerOnScreen();
                    });

                    // Entrada del Login
                    FadeTransition fadeLogin = new FadeTransition(duration, loginView);
                    fadeLogin.setFromValue(0.0);
                    fadeLogin.setToValue(1.0);

                    ScaleTransition scaleLogin = new ScaleTransition(duration, loginView);
                    scaleLogin.setFromX(1.05);
                    scaleLogin.setFromY(1.05);
                    scaleLogin.setToX(1.0);
                    scaleLogin.setToY(1.0);

                    new ParallelTransition(fadeLogin, scaleLogin).play();
                });

                fadeMain.play();
            });

        } catch (Exception e) {
            e.printStackTrace();
            fallbackLogout();
        }
    }

    private void fallbackLogout() {
        try {
            Stage stage = (Stage) rootNode.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login_view.fxml"));
            Scene newScene = new Scene(root, 760, 580);
            newScene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setScene(newScene);
            stage.setMaximized(false);
            stage.sizeToScene();
            stage.centerOnScreen();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
