package com.tpsstudio.view;

import com.tpsstudio.viewmodel.MainViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class MainViewController implements Initializable {

    @FXML
    private Label lblStatus;

    private final MainViewModel viewModel = new MainViewModel();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        lblStatus.textProperty().bind(viewModel.statusTextProperty());
    }

    @FXML
    private void onTestAction() {
        String timeString = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        viewModel.setStatusText("Binding OK: " + timeString);
    }

    @FXML
    private void onOpenEditor() {
        try {
            javafx.stage.Stage stage = (javafx.stage.Stage) lblStatus.getScene().getWindow();

            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/fxml/editor_view.fxml"));

            javafx.scene.Scene scene = new javafx.scene.Scene(loader.load(), 1100, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/css/app.css").toExternalForm());

            stage.setScene(scene);
            stage.setTitle("TPS Studio - Editor CR80");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
