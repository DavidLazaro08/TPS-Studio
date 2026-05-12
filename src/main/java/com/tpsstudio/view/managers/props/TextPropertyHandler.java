package com.tpsstudio.view.managers.props;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel de propiedades específico para elementos de texto.
 */
public class TextPropertyHandler extends BasePropertyHandler {

    public TextPropertyHandler(Canvas canvas, EditorCanvasManager canvasManager,
                               Runnable onCanvasRedraw, Runnable onPropertyChanged) {
        super(canvas, canvasManager, onCanvasRedraw, onPropertyChanged);
    }

    @Override
    public void buildPanel(VBox container, Elemento elemento) {
        if (!(elemento instanceof TextoElemento texto)) return;

        addEtiquetaControl(container, texto, "Ej: NOMBRE, Nº SOCIO...");

        // Contenido principal del texto
        Label lblContenido = new Label("Texto:");
        lblContenido.getStyleClass().add("prop-label-small");

        TextField txtContenido = new TextField(texto.getContenido());
        txtContenido.setPromptText("Contenido del texto...");
        txtContenido.setMaxWidth(Double.MAX_VALUE);
        txtContenido.setPrefHeight(24);
        txtContenido.textProperty().addListener((obs, old, newVal) -> {
            texto.setContenido(newVal);
            recalculateWidth(texto);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        if (fuenteDatos != null && fuenteDatos.tieneRegistros()) {
            addSeccionDatosVariables(container, texto, lblContenido, txtContenido);
            container.getChildren().add(new Separator());
        }

        container.getChildren().addAll(lblContenido, txtContenido);

        addPositionSizeControls(container, texto);
        container.getChildren().add(new Separator());

        // Comportamiento del texto dentro de su caja.
        CheckBox chkSalto = new CheckBox("Pasar a la línea inferior si no cabe");
        CheckBox chkAutoFit = new CheckBox("Auto-ajustar al ancho");

        chkSalto.setSelected(texto.isSaltoLinea());
        chkSalto.selectedProperty().addListener((obs, old, newVal) -> {
            texto.setSaltoLinea(newVal);
            if (newVal && texto.isAutoAjustar()) {
                texto.setAutoAjustar(false);
                chkAutoFit.setSelected(false);
            }
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        chkAutoFit.setSelected(texto.isAutoAjustar());
        chkAutoFit.selectedProperty().addListener((obs, old, newVal) -> {
            texto.setAutoAjustar(newVal);
            if (newVal && texto.isSaltoLinea()) {
                texto.setSaltoLinea(false);
                chkSalto.setSelected(false);
            }
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        container.getChildren().addAll(chkSalto, chkAutoFit, new Separator());

        addFontControls(container, texto);

        addColorControlWithEyedropper(container, "Color del Texto:", texto.getColor(), null, hex -> {
            texto.setColor(hex);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });
    }

    // =====================================================
    // Fuente y estilo
    // =====================================================

    private void addFontControls(VBox container, TextoElemento texto) {
        Label lblFuente = new Label("Fuente:");
        lblFuente.getStyleClass().add("prop-label-small");

        ComboBox<String> cmbFuente = new ComboBox<>(FXCollections.observableArrayList(javafx.scene.text.Font.getFamilies()));
        cmbFuente.setValue(texto.getFontFamily());
        cmbFuente.setMaxWidth(Double.MAX_VALUE);
        cmbFuente.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                texto.setFontFamily(newVal);
                recalculateWidth(texto);
                if (onCanvasRedraw != null) onCanvasRedraw.run();
            }
        });

        HBox tools = new HBox(8);
        tools.setAlignment(Pos.BOTTOM_LEFT);
        tools.setFillHeight(false);

        Spinner<Integer> spnSize = new Spinner<>(8, 72, (int) texto.getFontSize());
        spnSize.setEditable(true);
        spnSize.setPrefWidth(65);
        spnSize.valueProperty().addListener((obs, o, n) -> {
            texto.setFontSize(n);
            recalculateWidth(texto);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        ToggleButton btnBold = new ToggleButton("B");
        btnBold.getStyleClass().addAll("prop-toggle-btn", "btn-first");
        btnBold.setSelected(texto.isNegrita());
        btnBold.setMinWidth(32); btnBold.setPrefWidth(32);
        btnBold.setStyle("-fx-font-weight: bold;");
        btnBold.setOnAction(e -> {
            texto.setNegrita(btnBold.isSelected());
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        ToggleButton btnItalic = new ToggleButton("I");
        btnItalic.getStyleClass().addAll("prop-toggle-btn", "btn-last");
        btnItalic.setSelected(texto.isCursiva());
        btnItalic.setMinWidth(32); btnItalic.setPrefWidth(32);
        btnItalic.setStyle("-fx-font-style: italic; -fx-font-family: 'Georgia', 'Serif';");
        btnItalic.setOnAction(e -> {
            texto.setCursiva(btnItalic.isSelected());
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        HBox styles = new HBox(0, btnBold, btnItalic);
        styles.getStyleClass().add("prop-segmented-group");

        ToggleGroup alignGroup = new ToggleGroup();
        ToggleButton bL = createAlignBtn("\u2261", "LEFT", texto, alignGroup, "btn-first");
        ToggleButton bC = createAlignBtn("\u2263", "CENTER", texto, alignGroup, "");
        ToggleButton bR = createAlignBtn("\u2262", "RIGHT", texto, alignGroup, "btn-last");
        HBox aligns = new HBox(0, bL, bC, bR);
        aligns.getStyleClass().add("prop-segmented-group");

        VBox sizeBox = new VBox(4, new Label("Tamaño:"), spnSize);
        sizeBox.getChildren().get(0).getStyleClass().add("prop-label-small");

        tools.getChildren().addAll(sizeBox, styles, aligns);
        container.getChildren().addAll(lblFuente, cmbFuente, tools);
    }

    private ToggleButton createAlignBtn(String icon, String align, TextoElemento texto, ToggleGroup g, String css) {
        ToggleButton b = new ToggleButton(icon);
        b.setToggleGroup(g);
        if (!css.isEmpty()) b.getStyleClass().add(css);
        b.getStyleClass().add("prop-toggle-btn");
        b.setMinWidth(32); b.setPrefWidth(32);
        b.setSelected(align.equals(texto.getAlineacion()));
        b.setOnAction(e -> {
            texto.setAlineacion(align);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });
        return b;
    }

    // =====================================================
    // Datos variables
    // =====================================================

    private void addSeccionDatosVariables(VBox container, TextoElemento texto, Label lblTexto, TextField txtContenido) {
        Label lblSeccion = new Label("Datos Variables");
        lblSeccion.getStyleClass().add("prop-label");

        ComboBox<String> cmbColumna = new ComboBox<>();
        cmbColumna.setMaxWidth(Double.MAX_VALUE);

        List<String> options = new ArrayList<>();
        options.add("(sin vincular)");
        options.addAll(fuenteDatos.getColumnas());
        cmbColumna.setItems(FXCollections.observableArrayList(options));

        String actual = texto.getColumnaVinculada();
        cmbColumna.setValue(actual != null ? actual : "(sin vincular)");

        txtContenido.setDisable(actual != null);
        if (actual != null) lblTexto.setText("Texto (vinculado):");

        cmbColumna.valueProperty().addListener((obs, old, newVal) -> {
            boolean vinculando = !"(sin vincular)".equals(newVal);
            texto.setColumnaVinculada(vinculando ? newVal : null);
            txtContenido.setDisable(vinculando);
            lblTexto.setText(vinculando ? "Texto (vinculado):" : "Texto:");
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        container.getChildren().addAll(lblSeccion, cmbColumna);
    }

    // =====================================================
    // Ajuste automático de caja
    // =====================================================

    private void recalculateWidth(TextoElemento texto) {
        if (texto.isAutoAjustar() || texto.isSaltoLinea()) return;

        javafx.scene.text.Text helper = new javafx.scene.text.Text(texto.getContenido());
        helper.setFont(javafx.scene.text.Font.font(
                texto.getFontFamily(),
                texto.isNegrita() ? javafx.scene.text.FontWeight.BOLD : javafx.scene.text.FontWeight.NORMAL,
                texto.isCursiva() ? javafx.scene.text.FontPosture.ITALIC : javafx.scene.text.FontPosture.REGULAR,
                texto.getFontSize()
        ));

        double newWidth = helper.getLayoutBounds().getWidth() + 4;
        if (newWidth > 5) {
            texto.setWidth(newWidth);
            updatePositionFields(texto);
        }
    }
}