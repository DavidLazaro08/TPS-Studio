package com.tpsstudio.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Clase principal de la aplicación TPS Studio
 */
public class TPSStudio extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                TPSStudio.class.getResource("/fxml/login_view.fxml"));

        Scene scene = new Scene(loader.load(), 760, 580);
        scene.getStylesheets().add(
                TPSStudio.class.getResource("/css/app.css").toExternalForm());

        stage.setTitle("TPS Studio");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void stop() throws Exception {
        com.tpsstudio.util.ImageUtils.limpiarCache();
        super.stop();
    }
}
