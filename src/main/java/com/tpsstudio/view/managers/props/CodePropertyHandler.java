package com.tpsstudio.view.managers.props;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ElementoCodigo;
import com.tpsstudio.model.enums.TipoCodigo;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.List;

public class CodePropertyHandler extends BasePropertyHandler {

    public CodePropertyHandler(Canvas canvas, EditorCanvasManager canvasManager, 
                               Runnable onCanvasRedraw, Runnable onPropertyChanged) {
        super(canvas, canvasManager, onCanvasRedraw, onPropertyChanged);
    }

    @Override
    public void buildPanel(VBox container, Elemento elemento) {
        if (!(elemento instanceof ElementoCodigo codigo)) return;

        // 2. Etiqueta
        addEtiquetaControl(container, codigo, "Ej: QR WEB, CODIGO SOCIO...");

        // 3. Contenido
        Label lblContenido = new Label("Contenido:");
        lblContenido.getStyleClass().add("prop-label-small");
        TextField txtContenido = new TextField(codigo.getContenido());
        txtContenido.setMaxWidth(Double.MAX_VALUE);
        txtContenido.textProperty().addListener((obs, old, newVal) -> {
            codigo.setContenido(newVal);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        if (fuenteDatos != null && fuenteDatos.tieneRegistros()) {
            addSeccionDatosVariables(container, codigo, lblContenido, txtContenido);
            container.getChildren().add(new Separator());
        }

        container.getChildren().addAll(lblContenido, txtContenido);

        // 4. Posición y Tamaño
        addPositionSizeControls(container, codigo);
        container.getChildren().add(new Separator());

        // 5. Colores
        addColorControlWithEyedropper(container, "Color del Código:", codigo.getColorCodigo(), null, hex -> {
            codigo.setColorCodigo(hex); if (onCanvasRedraw != null) onCanvasRedraw.run();
        });
        addColorControlWithEyedropper(container, "Color de Fondo:", codigo.getColorFondo(), null, hex -> {
            codigo.setColorFondo(hex); if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        // 6. Margen
        Label lblMargen = new Label("Margen (Quiet Zone):");
        Spinner<Integer> spMargen = new Spinner<>(0, 50, codigo.getMargen());
        spMargen.setMaxWidth(Double.MAX_VALUE);
        spMargen.valueProperty().addListener((obs, o, n) -> {
            codigo.setMargen(n); if (onCanvasRedraw != null) onCanvasRedraw.run();
        });
        container.getChildren().addAll(new Separator(), lblMargen, spMargen);

        // 7. Específicos según tipo
        if (codigo.getTipo() == TipoCodigo.QR) {
            addQRControls(container, codigo);
        } else {
            addBarcodeControls(container, codigo);
        }
    }

    private void addQRControls(VBox container, ElementoCodigo codigo) {
        Label lblError = new Label("Nivel de corrección:");
        ComboBox<String> cmbError = new ComboBox<>(FXCollections.observableArrayList("L (7%)", "M (15%)", "Q (25%)", "H (30%)"));
        cmbError.setMaxWidth(Double.MAX_VALUE);
        
        String actual = codigo.getNivelCorreccion();
        for (String item : cmbError.getItems()) {
            if (item.startsWith(actual)) { cmbError.setValue(item); break; }
        }

        cmbError.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                codigo.setNivelCorreccion(newVal.substring(0, 1));
                if (onCanvasRedraw != null) onCanvasRedraw.run();
            }
        });
        container.getChildren().addAll(new Separator(), lblError, cmbError);
    }

    private void addBarcodeControls(VBox container, ElementoCodigo codigo) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        CheckBox chk = new CheckBox("Texto");
        chk.setSelected(codigo.isMostrarTexto());
        chk.selectedProperty().addListener((obs, o, n) -> {
            codigo.setMostrarTexto(n);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
            if (onPropertyChanged != null) onPropertyChanged.run();
        });

        row.getChildren().add(chk);

        if (codigo.isMostrarTexto()) {
            Spinner<Integer> spSize = new Spinner<>(6, 24, codigo.getFontSize());
            spSize.setPrefWidth(60);
            spSize.valueProperty().addListener((obs, o, n) -> {
                codigo.setFontSize(n); if (onCanvasRedraw != null) onCanvasRedraw.run();
            });

            ToggleButton btnBold = new ToggleButton("B");
            btnBold.getStyleClass().addAll("prop-toggle-btn", "btn-first");
            btnBold.setSelected(codigo.isNegrita());
            btnBold.setMinWidth(32); btnBold.setPrefWidth(32);
            btnBold.setStyle("-fx-font-weight: bold;");
            btnBold.setOnAction(e -> { codigo.setNegrita(btnBold.isSelected()); if (onCanvasRedraw != null) onCanvasRedraw.run(); });

            ToggleButton btnItalic = new ToggleButton("I");
            btnItalic.getStyleClass().addAll("prop-toggle-btn", "btn-last");
            btnItalic.setSelected(codigo.isCursiva());
            btnItalic.setMinWidth(32); btnItalic.setPrefWidth(32);
            btnItalic.setStyle("-fx-font-style: italic; -fx-font-family: 'Georgia', 'Serif';");
            btnItalic.setOnAction(e -> { codigo.setCursiva(btnItalic.isSelected()); if (onCanvasRedraw != null) onCanvasRedraw.run(); });

            HBox styles = new HBox(0, btnBold, btnItalic);
            styles.getStyleClass().add("prop-segmented-group");

            row.getChildren().addAll(spSize, styles);
        }
        container.getChildren().addAll(new Separator(), row);
    }

    private void addSeccionDatosVariables(VBox container, ElementoCodigo codigo, Label lblContenido, TextField txtContenido) {
        Label lblSeccion = new Label("Datos Variables");
        lblSeccion.getStyleClass().add("prop-label");

        ComboBox<String> cmbColumna = new ComboBox<>();
        cmbColumna.setMaxWidth(Double.MAX_VALUE);
        List<String> options = new ArrayList<>();
        options.add("(sin vincular)");
        options.addAll(fuenteDatos.getColumnas());
        cmbColumna.setItems(FXCollections.observableArrayList(options));

        String actual = codigo.getColumnaVinculada();
        cmbColumna.setValue(actual != null ? actual : "(sin vincular)");
        
        txtContenido.setDisable(actual != null);
        if (actual != null) lblContenido.setText("Contenido (vinculado):");

        cmbColumna.valueProperty().addListener((obs, old, newVal) -> {
            boolean vinculando = !"(sin vincular)".equals(newVal);
            codigo.setColumnaVinculada(vinculando ? newVal : null);
            txtContenido.setDisable(vinculando);
            lblContenido.setText(vinculando ? "Contenido (vinculado):" : "Contenido:");
            codigo.invalidarCache();
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        container.getChildren().addAll(lblSeccion, cmbColumna);
    }
}
