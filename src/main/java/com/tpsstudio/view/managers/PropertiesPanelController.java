package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.view.managers.props.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Controlador del panel de propiedades.
 *
 * Actúa como punto intermedio entre la interfaz principal y los handlers
 * específicos de cada tipo de elemento.
 */
public class PropertiesPanelController {

    private final Canvas canvas;
    private final EditorCanvasManager canvasManager;

    // Callbacks
    private Runnable onPropertyChanged;
    private Runnable onCanvasRedrawNeeded;
    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;
    private Runnable onDownloadTemplate;

    private FuenteDatos fuenteDatos;
    private BasePropertyHandler activeHandler;

    public PropertiesPanelController(Canvas canvas, EditorCanvasManager canvasManager) {
        this.canvas = canvas;
        this.canvasManager = canvasManager;
    }

    // =====================================================
    // Setters de callbacks
    // =====================================================

    public void setOnPropertyChanged(Runnable cb) { this.onPropertyChanged = cb; }
    public void setOnCanvasRedrawNeeded(Runnable cb) { this.onCanvasRedrawNeeded = cb; }
    public void setOnEditExternal(Consumer<ImagenFondoElemento> cb) { this.onEditExternal = cb; }
    public void setOnReload(Consumer<ImagenFondoElemento> cb) { this.onReload = cb; }
    public void setOnDownloadTemplate(Runnable cb) { this.onDownloadTemplate = cb; }
    public void setFuenteDatos(FuenteDatos fuenteDatos) { this.fuenteDatos = fuenteDatos; }

    // =====================================================
    // Construcción del panel
    // =====================================================

    public VBox buildPanel(Elemento elemento, Proyecto proyecto) {
        VBox container = new VBox(10);
        container.setPadding(new Insets(16));
        container.setFillWidth(true);
        container.setAlignment(Pos.TOP_LEFT);

        Label lblTitle = new Label("Propiedades");
        lblTitle.getStyleClass().add("panel-title");
        container.getChildren().add(lblTitle);

        if (elemento == null) {
            Label placeholder = new Label("Seleccione un elemento");
            placeholder.getStyleClass().add("panel-placeholder");
            container.getChildren().add(placeholder);
            this.activeHandler = null;
            return container;
        }

        this.activeHandler = selectHandler(elemento);

        if (activeHandler != null) {
            activeHandler.setFuenteDatos(fuenteDatos);
            activeHandler.buildPanel(container, elemento);
        }

        return container;
    }

    private BasePropertyHandler selectHandler(Elemento elemento) {
        if (elemento instanceof TextoElemento) {
            return new TextPropertyHandler(canvas, canvasManager, onCanvasRedrawNeeded, onPropertyChanged);

        } else if (elemento instanceof ImagenElemento || elemento instanceof ImagenFondoElemento) {
            ImagePropertyHandler handler = new ImagePropertyHandler(canvas, canvasManager, onCanvasRedrawNeeded, onPropertyChanged);
            handler.setCallbacks(onEditExternal, onReload, onDownloadTemplate);
            return handler;

        } else if (elemento instanceof ElementoCodigo) {
            return new CodePropertyHandler(canvas, canvasManager, onCanvasRedrawNeeded, onPropertyChanged);

        } else if (elemento instanceof FormaElemento) {
            return new ShapePropertyHandler(canvas, canvasManager, onCanvasRedrawNeeded, onPropertyChanged);
        }

        return null;
    }

    // =====================================================
    // Actualización desde el canvas
    // =====================================================

    public void updatePositionFields(Elemento elemento) {
        if (activeHandler != null) {
            activeHandler.updatePositionFields(elemento);
        }
    }
}