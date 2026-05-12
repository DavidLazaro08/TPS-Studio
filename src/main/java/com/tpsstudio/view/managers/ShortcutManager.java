package com.tpsstudio.view.managers;

import com.tpsstudio.view.controllers.MainViewController;
import com.tpsstudio.viewmodel.MainViewModel;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Gestiona los atajos de teclado de la aplicación.
 * Extraído de MainViewController para desacoplar la lógica de input.
 */
public class ShortcutManager {

    private final MainViewController controller;
    private final MainViewModel viewModel;

    public ShortcutManager(MainViewController controller, MainViewModel viewModel) {
        this.controller = controller;
        this.viewModel = viewModel;
    }

    /**
     * Configura los listeners de teclado en la escena.
     */
    public void setup(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Teclas globales
            if (event.getCode() == KeyCode.DELETE) {
                if (viewModel.getElementoSeleccionado() != null) {
                    controller.onEliminarElemento();
                    event.consume();
                }
            }
            
            // Aquí se pueden añadir combinaciones con modificadores (Ctrl, Shift, etc.)
            // if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
            //     controller.onGuardarProyecto();
            //     event.consume();
            // }
        });
    }
}
