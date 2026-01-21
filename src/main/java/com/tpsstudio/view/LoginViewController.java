package com.tpsstudio.view;

import com.tpsstudio.viewmodel.LoginViewModel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginViewController {

    @FXML
    private TextField txtUser;

    @FXML
    private PasswordField txtPass;

    @FXML
    private Label lblError;

    private LoginViewModel viewModel;

    public LoginViewController() {
        this.viewModel = new LoginViewModel();
    }

    @FXML
    private void initialize() {
        // Bindings bidireccionales entre la vista y el ViewModel
        txtUser.textProperty().bindBidirectional(viewModel.userProperty());
        txtPass.textProperty().bindBidirectional(viewModel.passProperty());
    }

    @FXML
    private void onLogin() {
        // Limpiar mensaje de error previo
        lblError.setText("");

        // Validar credenciales
        if (viewModel.validateLogin()) {
            // Login exitoso - cambiar a la pantalla principal
            openMainView();
        } else {
            // Login fallido - mostrar mensaje de error
            lblError.setText("Credenciales incorrectas");
        }
    }

    private void openMainView() {
        try {
            // Obtener el Stage actual desde cualquier nodo de la escena
            Stage stage = (Stage) txtUser.getScene().getWindow();

            // Cargar el FXML de la vista principal
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/main_view.fxml"));

            // Crear nueva escena con la vista principal
            Scene scene = new Scene(loader.load(), 1100, 700);

            // Aplicar la hoja de estilos CSS
            scene.getStylesheets().add(
                    getClass().getResource("/css/app.css").toExternalForm());

            // Cambiar la escena en el Stage
            stage.setScene(scene);
            stage.setTitle("TPS Studio");

            // Ajustar tamaño y centrar ventana
            stage.sizeToScene();
            stage.centerOnScreen();

            // Establecer tamaños mínimos
            stage.setMinWidth(900);
            stage.setMinHeight(600);

        } catch (Exception e) {
            lblError.setText("Error al cargar la aplicación");
            e.printStackTrace();
        }
    }
}
