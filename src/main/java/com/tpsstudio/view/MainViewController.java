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
}
