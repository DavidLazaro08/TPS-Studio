package com.tpsstudio.view.managers.props;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

/**
 * Clase base para los paneles de propiedades.
 *
 * Contiene controles comunes como posición, tamaño, etiqueta y selección de color.
 */
public abstract class BasePropertyHandler {

    protected final Canvas canvas;
    protected final EditorCanvasManager canvasManager;
    protected final Runnable onCanvasRedraw;
    protected final Runnable onPropertyChanged;
    protected FuenteDatos fuenteDatos;

    protected static final BooleanProperty eyedropperActive = new SimpleBooleanProperty(false);
    private boolean isInternalUpdate = false;

    protected TextField txtX, txtY, txtW, txtH;
    protected Label lblMmX, lblMmY, lblMmW, lblMmH;

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

    public abstract void buildPanel(VBox container, Elemento elemento);

    // =====================================================
    // Actualización de posición/tamaño
    // =====================================================

    public void updatePositionFields(Elemento elemento) {
        if (elemento == null) return;

        isInternalUpdate = true;
        try {
            updateField(txtX, elemento.getX());
            updateField(txtY, elemento.getY());
            updateField(txtW, elemento.getWidth());
            updateField(txtH, elemento.getHeight());

            if (lblMmX != null) lblMmX.setText(com.tpsstudio.util.UnitConverter.formatMm(elemento.getX()));
            if (lblMmY != null) lblMmY.setText(com.tpsstudio.util.UnitConverter.formatMm(elemento.getY()));
            if (lblMmW != null) lblMmW.setText(com.tpsstudio.util.UnitConverter.formatMm(elemento.getWidth()));
            if (lblMmH != null) lblMmH.setText(com.tpsstudio.util.UnitConverter.formatMm(elemento.getHeight()));
        } finally {
            isInternalUpdate = false;
        }
    }

    private void updateField(TextField field, double value) {
        if (field != null && !field.isFocused()) {
            field.setText(String.format(java.util.Locale.US, "%.0f", value));
        }
    }

    protected TextField createNumberField(double initialValue, String prompt, Consumer<Double> onValidChange) {
        TextField tf = new TextField(String.format(java.util.Locale.US, "%.0f", initialValue));
        tf.setPromptText(prompt);
        tf.setMaxWidth(Double.MAX_VALUE);

        tf.textProperty().addListener((obs, old, newVal) -> {
            if (isInternalUpdate) return;

            try {
                double value = Double.parseDouble(newVal);
                onValidChange.accept(value);
                if (onCanvasRedraw != null) onCanvasRedraw.run();
            } catch (NumberFormatException ignored) {
                // Mientras el usuario escribe, se ignoran valores incompletos o no válidos.
            }
        });

        return tf;
    }

    protected void addPositionSizeControls(VBox container, Elemento elemento) {
        Label lblPos = new Label("Posición y Tamaño");
        lblPos.getStyleClass().add("prop-label");

        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(0);
        grid.setPadding(new Insets(5, 0, 5, 0));

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setHalignment(HPos.LEFT);
        col0.setMinWidth(20);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPrefWidth(70);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHalignment(HPos.LEFT);
        col2.setMinWidth(55);

        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPrefWidth(70);

        grid.getColumnConstraints().addAll(col0, col1, col2, col3);

        txtX = createNumberField(elemento.getX(), "X", elemento::setX);
        txtY = createNumberField(elemento.getY(), "Y", elemento::setY);
        txtW = createNumberField(elemento.getWidth(), "Ancho", elemento::setWidth);
        txtH = createNumberField(elemento.getHeight(), "Alto", elemento::setHeight);

        String fieldStyle = "-fx-pref-width: 65px;";
        txtX.setStyle(fieldStyle); txtY.setStyle(fieldStyle);
        txtW.setStyle(fieldStyle); txtH.setStyle(fieldStyle);

        Label lblX = new Label("X:");
        Label lblY = new Label("Y:");
        Label lblW = new Label("Ancho:");
        Label lblH = new Label("Alto:");

        Insets rowSpacing = new Insets(6, 0, 0, 0);
        GridPane.setMargin(lblY, rowSpacing);
        GridPane.setMargin(txtY, rowSpacing);
        GridPane.setMargin(lblH, new Insets(6, 0, 0, 10));
        GridPane.setMargin(txtH, rowSpacing);
        GridPane.setMargin(lblW, new Insets(0, 0, 0, 10));

        lblMmX = new Label(com.tpsstudio.util.UnitConverter.formatMm(elemento.getX()));
        lblMmY = new Label(com.tpsstudio.util.UnitConverter.formatMm(elemento.getY()));
        lblMmW = new Label(com.tpsstudio.util.UnitConverter.formatMm(elemento.getWidth()));
        lblMmH = new Label(com.tpsstudio.util.UnitConverter.formatMm(elemento.getHeight()));

        Insets mmPadding = new Insets(1, 0, 2, 0);
        lblMmX.setPadding(mmPadding);
        lblMmY.setPadding(mmPadding);
        lblMmW.setPadding(mmPadding);
        lblMmH.setPadding(mmPadding);

        GridPane.setMargin(lblMmW, new Insets(0, 0, 0, 10));
        GridPane.setMargin(lblMmH, new Insets(0, 0, 0, 10));

        lblMmX.getStyleClass().add("prop-label-mm");
        lblMmY.getStyleClass().add("prop-label-mm");
        lblMmW.getStyleClass().add("prop-label-mm");
        lblMmH.getStyleClass().add("prop-label-mm");

        grid.add(lblX, 0, 0); grid.add(txtX, 1, 0);
        grid.add(lblW, 2, 0); grid.add(txtW, 3, 0);
        grid.add(lblMmX, 1, 1);
        grid.add(lblMmW, 3, 1);

        grid.add(lblY, 0, 2); grid.add(txtY, 1, 2);
        grid.add(lblH, 2, 2); grid.add(txtH, 3, 2);
        grid.add(lblMmY, 1, 3);
        grid.add(lblMmH, 3, 3);

        container.getChildren().addAll(lblPos, grid);
    }

    // =====================================================
    // Controles comunes
    // =====================================================

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
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }
}