package com.tpsstudio.view.managers.props;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.util.function.Consumer;

/**
 * Clase base para los gestores de propiedades.
 * Proporciona métodos de utilidad para crear controles comunes y gestionar el cuentagotas.
 */
public abstract class BasePropertyHandler {

    protected final Canvas canvas;
    protected final EditorCanvasManager canvasManager;
    protected final Runnable onCanvasRedraw;
    protected final Runnable onPropertyChanged;
    protected FuenteDatos fuenteDatos;

    // Propiedad compartida para el estado del cuentagotas
    protected static final BooleanProperty eyedropperActive = new SimpleBooleanProperty(false);

    // Referencias para actualización en tiempo real (X, Y, W, H)
    protected TextField txtX, txtY, txtW, txtH;

    public BasePropertyHandler(Canvas canvas, EditorCanvasManager canvasManager, 
                               Runnable onCanvasRedraw, Runnable onPropertyChanged) {
        this.canvas = canvas;
        this.canvasManager = canvasManager;
        this.onCanvasRedraw = onCanvasRedraw;
        this.onPropertyChanged = onPropertyChanged;
    }

    public void setFuenteDatos(FuenteDatos fuenteDatos) {
        this.fuenteDatos = fuenteDatos;
    }

    /**
     * Método principal que cada Handler debe implementar para construir su UI específica.
     */
    public abstract void buildPanel(VBox container, Elemento elemento);

    /**
     * Actualiza los campos numéricos de posición en tiempo real (ej: al arrastrar en el canvas).
     */
    public void updatePositionFields(Elemento elemento) {
        if (elemento == null) return;
        updateField(txtX, elemento.getX());
        updateField(txtY, elemento.getY());
        updateField(txtW, elemento.getWidth());
        updateField(txtH, elemento.getHeight());
    }

    private void updateField(TextField field, double value) {
        if (field != null && !field.isFocused()) {
            field.setText(String.format(java.util.Locale.US, "%.0f", value));
        }
    }

    // ===================== UTILIDADES DE UI =====================

    protected TextField createNumberField(double initialValue, String prompt, Consumer<Double> onValidChange) {
        TextField tf = new TextField(String.format(java.util.Locale.US, "%.0f", initialValue));
        tf.setPromptText(prompt);
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.textProperty().addListener((obs, old, newVal) -> {
            try {
                double value = Double.parseDouble(newVal);
                onValidChange.accept(value);
                if (onCanvasRedraw != null) onCanvasRedraw.run();
            } catch (NumberFormatException ignored) {}
        });
        return tf;
    }

    protected void addPositionSizeControls(VBox container, Elemento elemento) {
        Label lblPos = new Label("Posición y Tamaño");
        lblPos.getStyleClass().add("prop-label");

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8); grid.setVgap(8);

        txtX = createNumberField(elemento.getX(), "X", elemento::setX);
        txtY = createNumberField(elemento.getY(), "Y", elemento::setY);
        txtW = createNumberField(elemento.getWidth(), "Ancho", elemento::setWidth);
        txtH = createNumberField(elemento.getHeight(), "Alto", elemento::setHeight);

        Label lblX = new Label("X:");
        lblX.setMinWidth(20);
        Label lblY = new Label("Y:");
        lblY.setMinWidth(20);
        Label lblW = new Label("Ancho:");
        lblW.setMinWidth(45);
        Label lblH = new Label("Alto:");
        lblH.setMinWidth(45);

        grid.add(lblX, 0, 0); grid.add(txtX, 1, 0);
        grid.add(lblW, 2, 0); grid.add(txtW, 3, 0);
        grid.add(lblY, 0, 1); grid.add(txtY, 1, 1);
        grid.add(lblH, 2, 1); grid.add(txtH, 3, 1);

        // Aplicar estilos CSS a las etiquetas del grid
        grid.getChildren().stream()
            .filter(n -> n instanceof Label)
            .forEach(n -> n.getStyleClass().add("prop-label-small"));

        container.getChildren().addAll(lblPos, grid);
    }

    protected void addEtiquetaControl(VBox container, Elemento elemento, String prompt) {
        Label lbl = new Label("Etiqueta (opcional):");
        lbl.getStyleClass().add("prop-label");

        TextField txt = new TextField(elemento.getEtiqueta() != null ? elemento.getEtiqueta() : "");
        txt.setPromptText(prompt);
        txt.setMaxWidth(Double.MAX_VALUE);
        txt.textProperty().addListener((obs, old, newVal) -> {
            elemento.setEtiqueta(newVal.isEmpty() ? null : newVal);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        container.getChildren().addAll(lbl, txt, new Separator());
    }

    protected void addColorControlWithEyedropper(VBox box, String labelText, String hexInitial, 
                                                 BooleanProperty disableProp, Consumer<String> onColorChange) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("prop-label-small");

        ColorPicker cp = new ColorPicker(Color.web(hexInitial));
        cp.setPrefWidth(140);
        if (disableProp != null) cp.disableProperty().bind(disableProp.not());

        Button btnEyedropper = new Button("⌖");
        btnEyedropper.getStyleClass().add("prop-toggle-btn");
        btnEyedropper.setMinWidth(30);
        if (disableProp != null) btnEyedropper.disableProperty().bind(disableProp.not());

        // Lógica visual del cuentagotas
        btnEyedropper.styleProperty().bind(
            javafx.beans.binding.Bindings.when(eyedropperActive)
                .then("-fx-background-color: #221e3a; -fx-text-fill: #c4bcec; -fx-border-color: #3b3570;")
                .otherwise("-fx-background-color: rgba(255,255,255,0.05);")
        );

        btnEyedropper.setOnAction(e -> {
            if (eyedropperActive.get()) {
                canvasManager.deactivateEyedropper();
                eyedropperActive.set(false);
            } else {
                eyedropperActive.set(true);
                canvasManager.activateEyedropper(color -> {
                    cp.setValue(color);
                    onColorChange.accept(colorToHex(color));
                    eyedropperActive.set(false);
                });
            }
        });

        cp.valueProperty().addListener((obs, old, newVal) -> onColorChange.accept(colorToHex(newVal)));

        HBox row = new HBox(8, cp, btnEyedropper);
        row.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(lbl, row);
    }

    protected String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }
}
