package com.tpsstudio.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("/fxml/main_view.fxml")
        );

        Scene scene = new Scene(loader.load(), 1100, 700);

        stage.setTitle("TPS Studio");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
