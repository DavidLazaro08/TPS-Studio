package com.tpsstudio.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class MainViewModel {

    private final StringProperty statusText;

    public MainViewModel() {
        this.statusText = new SimpleStringProperty("TPS Studio listo");
    }

    // Property getter (para binding)
    public StringProperty statusTextProperty() {
        return statusText;
    }

    // Getter tradicional
    public String getStatusText() {
        return statusText.get();
    }

    // Setter tradicional
    public void setStatusText(String text) {
        statusText.set(text);
    }
}
