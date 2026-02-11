package com.tpsstudio.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/* ViewModel principal de la aplicación.
 * Se encarga de mantener el estado de la interfaz que no tiene cabida
 * en los Managers ni en los Controllers.
 *
 * Por ahora solo gestiona el texto de la barra de estado inferior
 * ("Listo", "Guardado...", etc.). */

public class MainViewModel {

    // Texto que se muestra en la barra de estado
    private final StringProperty statusText;

    public MainViewModel() {
        this.statusText = new SimpleStringProperty("TPS Studio listo");
    }

    // Property getter (binding con la UI)
    public StringProperty statusTextProperty() {
        return statusText;
    }

    // Getter normal
    public String getStatusText() {
        return statusText.get();
    }

    // Setter normal
    public void setStatusText(String text) {
        statusText.set(text);
    }
}
