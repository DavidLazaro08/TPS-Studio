package com.tpsstudio.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * ViewModel principal (aunque de momento hace poquito).
 * Se encarga de guardar el estado de la UI que no tiene cabida en los Managers.
 * 
 * Ahora mismo solo gestiona el texto de la barra de estado.
 */
public class MainViewModel {

    // Texto que sale abajo a la izquierda ("Listo", "Guardado...", etc.)
    private final StringProperty statusText;

    public MainViewModel() {
        this.statusText = new SimpleStringProperty("TPS Studio listo");
    }

    // Property getter (para conectar con la UI automáticamente)
    public StringProperty statusTextProperty() {
        return statusText;
    }

    // Getter normal (para leer el valor)
    public String getStatusText() {
        return statusText.get();
    }

    // Setter normal (para cambiar el mensaje)
    public void setStatusText(String text) {
        statusText.set(text);
    }
}
