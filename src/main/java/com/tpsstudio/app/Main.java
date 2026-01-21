package com.tpsstudio.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("/fxml/login_view.fxml"));

        Scene scene = new Scene(loader.load(), 500, 400);
        scene.getStylesheets().add(
                Main.class.getResource("/css/app.css").toExternalForm());

        stage.setTitle("TPS Studio");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
