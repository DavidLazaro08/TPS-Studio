package com.tpsstudio.view.managers.props;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.List;

public class TextPropertyHandler extends BasePropertyHandler {

    public TextPropertyHandler(Canvas canvas, EditorCanvasManager canvasManager, 
                               Runnable onCanvasRedraw, Runnable onPropertyChanged) {
        super(canvas, canvasManager, onCanvasRedraw, onPropertyChanged);
    }

    @Override
    public void buildPanel(VBox container, Elemento elemento) {
        if (!(elemento instanceof TextoElemento texto)) return;

        // 1. Etiqueta (nombre lógico)
        addEtiquetaControl(container, texto, "Ej: NOMBRE, Nº SOCIO...");

        // 2. Contenido del texto
        Label lblContenido = new Label("Texto:");
        lblContenido.getStyleClass().add("prop-label-small");

        TextArea txtContenido = new TextArea(texto.getContenido());
        txtContenido.setPromptText("Contenido del texto...");
        txtContenido.setMaxWidth(Double.MAX_VALUE);
        txtContenido.setPrefRowCount(3);
        txtContenido.setWrapText(true);
        txtContenido.textProperty().addListener((obs, old, newVal) -> {
            texto.setContenido(newVal);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        // Vincular a Base de Datos si existe
        if (fuenteDatos != null && fuenteDatos.tieneRegistros()) {
            addSeccionDatosVariables(container, texto, lblContenido, txtContenido);
            container.getChildren().add(new Separator());
        }

        container.getChildren().addAll(lblContenido, txtContenido);

        // 3. Posición y Tamaño
        addPositionSizeControls(container, texto);
        container.getChildren().add(new Separator());

        // 4. Comportamiento (Salto de línea / Auto-ajuste)
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

        container.getChildren().addAll(chkSalto, chkAutoFit);

        // 5. Estilo de Fuente
        addFontControls(container, texto);

        // 6. Color
        addColorControlWithEyedropper(container, "Color del Texto:", texto.getColor(), null, hex -> {
            texto.setColor(hex);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });
    }

    private void addFontControls(VBox container, TextoElemento texto) {
        Label lblFuente = new Label("Fuente:");
        lblFuente.getStyleClass().add("prop-label-small");

        ComboBox<String> cmbFuente = new ComboBox<>(FXCollections.observableArrayList(javafx.scene.text.Font.getFamilies()));
        cmbFuente.setValue(texto.getFontFamily());
        cmbFuente.setMaxWidth(Double.MAX_VALUE);
        cmbFuente.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                texto.setFontFamily(newVal);
                if (onCanvasRedraw != null) onCanvasRedraw.run();
            }
        });

        // Tamaño, Negrita, Cursiva, Alineación (Fila combinada)
        HBox tools = new HBox(8);
        tools.setAlignment(Pos.BOTTOM_LEFT);

        Spinner<Integer> spnSize = new Spinner<>(8, 72, (int) texto.getFontSize());
        spnSize.setEditable(true);
        spnSize.setPrefWidth(65);
        spnSize.valueProperty().addListener((obs, o, n) -> {
            texto.setFontSize(n);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        // Estilo B/I
        ToggleButton btnBold = new ToggleButton("B");
        btnBold.getStyleClass().addAll("prop-toggle-btn", "btn-first");
        btnBold.setSelected(texto.isNegrita());
        btnBold.setStyle("-fx-font-weight: bold;");
        btnBold.setOnAction(e -> { texto.setNegrita(btnBold.isSelected()); if (onCanvasRedraw != null) onCanvasRedraw.run(); });

        ToggleButton btnItalic = new ToggleButton("I");
        btnItalic.getStyleClass().addAll("prop-toggle-btn", "btn-last");
        btnItalic.setSelected(texto.isCursiva());
        btnItalic.setStyle("-fx-font-style: italic; -fx-font-family: 'Georgia', 'Serif';");
        btnItalic.setOnAction(e -> { texto.setCursiva(btnItalic.isSelected()); if (onCanvasRedraw != null) onCanvasRedraw.run(); });

        HBox styles = new HBox(0, btnBold, btnItalic);
        styles.getStyleClass().add("prop-segmented-group");

        // Alineación
        ToggleGroup alignGroup = new ToggleGroup();
        ToggleButton bL = createAlignBtn("\u2261", "LEFT", texto, alignGroup, "btn-first");
        ToggleButton bC = createAlignBtn("\u2263", "CENTER", texto, alignGroup, "");
        ToggleButton bR = createAlignBtn("\u2262", "RIGHT", texto, alignGroup, "btn-last");
        HBox aligns = new HBox(0, bL, bC, bR);
        aligns.getStyleClass().add("prop-segmented-group");

        tools.getChildren().addAll(new VBox(4, new Label("Tamaño:"), spnSize), styles, aligns);
        container.getChildren().addAll(lblFuente, cmbFuente, tools);
    }

    private ToggleButton createAlignBtn(String icon, String align, TextoElemento texto, ToggleGroup g, String css) {
        ToggleButton b = new ToggleButton(icon);
        b.setToggleGroup(g);
        if (!css.isEmpty()) b.getStyleClass().add(css);
        b.getStyleClass().add("prop-toggle-btn");
        b.setSelected(align.equals(texto.getAlineacion()));
        b.setOnAction(e -> { texto.setAlineacion(align); if (onCanvasRedraw != null) onCanvasRedraw.run(); });
        return b;
    }

    private void addSeccionDatosVariables(VBox container, TextoElemento texto, Label lblTexto, TextArea txtContenido) {
        VBox section = new VBox(4);
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
        
        // Estado inicial
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
}
