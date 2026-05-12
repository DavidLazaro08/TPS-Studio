package com.tpsstudio.view.managers.props;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.service.SettingsManager;
import com.tpsstudio.util.ImageUtils;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.collections.FXCollections;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel de propiedades para imágenes normales y fondos de tarjeta.
 */
public class ImagePropertyHandler extends BasePropertyHandler {

    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;
    private Runnable onDownloadTemplate;

    public ImagePropertyHandler(Canvas canvas, EditorCanvasManager canvasManager,
                                Runnable onCanvasRedraw, Runnable onPropertyChanged) {
        super(canvas, canvasManager, onCanvasRedraw, onPropertyChanged);
    }

    public void setCallbacks(Consumer<ImagenFondoElemento> onEditExternal,
                             Consumer<ImagenFondoElemento> onReload,
                             Runnable onDownloadTemplate) {
        this.onEditExternal = onEditExternal;
        this.onReload = onReload;
        this.onDownloadTemplate = onDownloadTemplate;
    }

    @Override
    public void buildPanel(VBox container, Elemento elemento) {
        if (elemento instanceof ImagenFondoElemento fondo) {
            buildBackgroundPanel(container, fondo);
        } else if (elemento instanceof ImagenElemento imagen) {
            buildImagePanel(container, imagen);
        }
    }

    // =====================================================
    // Fondo de tarjeta
    // =====================================================

    private void buildBackgroundPanel(VBox container, ImagenFondoElemento fondo) {
        Label lblInfo = new Label("Fondo de la tarjeta");
        lblInfo.getStyleClass().add("prop-label");

        Label lblDim = new Label(String.format("Dimensiones: %.0f × %.0f px", fondo.getWidth(), fondo.getHeight()));
        lblDim.getStyleClass().add("prop-label-small");

        ToggleGroup modoGroup = new ToggleGroup();
        RadioButton rbBleed = createModoRadio("Con sangre (CR80 + sangrado)", fondo, FondoFitMode.BLEED, modoGroup);
        RadioButton rbFinal = createModoRadio("Sin sangre (CR80 final)", fondo, FondoFitMode.FINAL, modoGroup);

        Button btnReemplazar = createActionBtn("🖼  Reemplazar Fondo", e -> reemplazarImagen(fondo));

        Button btnEditar = createActionBtn("✏  Editor Externo", e -> {
            if (onEditExternal != null) onEditExternal.accept(fondo);
        });
        HBox.setHgrow(btnEditar, Priority.ALWAYS);

        Button btnConfig = createActionBtn("⚙", e -> configurarEditor());
        btnConfig.setPrefWidth(40);

        HBox cajaEdicion = new HBox(5, btnEditar, btnConfig);

        Button btnRecargar = createActionBtn("🔄  Recargar", e -> {
            if (onReload != null) onReload.accept(fondo);
        });

        Button btnPlantilla = createActionBtn("Descargar Plantilla CR80", e -> {
            if (onDownloadTemplate != null) onDownloadTemplate.run();
        });
        btnPlantilla.getStyleClass().add("primary-btn");

        container.getChildren().addAll(
                lblInfo,
                lblDim,
                new Separator(),
                new Label("Modo de ajuste:"),
                rbBleed,
                rbFinal,
                new Separator(),
                btnReemplazar,
                cajaEdicion,
                btnRecargar,
                new Separator(),
                btnPlantilla
        );
    }

    private RadioButton createModoRadio(String text, ImagenFondoElemento fondo, FondoFitMode modo, ToggleGroup g) {
        RadioButton rb = new RadioButton(text);
        rb.setToggleGroup(g);
        rb.setSelected(fondo.getFitMode() == modo);
        rb.setOnAction(e -> {
            fondo.setFitMode(modo);
            fondo.ajustarATamaño(EditorCanvasManager.CARD_WIDTH, EditorCanvasManager.CARD_HEIGHT, EditorCanvasManager.BLEED_MARGIN);
            if (onPropertyChanged != null) onPropertyChanged.run();
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });
        return rb;
    }

    // =====================================================
    // Imagen normal
    // =====================================================

    private void buildImagePanel(VBox container, ImagenElemento imagen) {
        addEtiquetaControl(container, imagen, "Ej: FOTO, LOGO...");

        Label lblAviso = new Label("💡 Coloca las fotos en la carpeta 'Fotos' del proyecto.");
        lblAviso.getStyleClass().add("prop-info-message");
        lblAviso.setManaged(false);
        lblAviso.setVisible(false);

        Button btnReemplazar = createActionBtn("🖼  Reemplazar Imagen", e -> reemplazarImagen(imagen));

        if (fuenteDatos != null && fuenteDatos.tieneRegistros()) {
            addSeccionDatosVariablesImagen(container, imagen, btnReemplazar, lblAviso);
            container.getChildren().add(new Separator());
        }

        container.getChildren().addAll(btnReemplazar, lblAviso, new Separator());

        addPositionSizeControls(container, imagen);
        container.getChildren().add(new Separator());

        Label lblOp = new Label("Opacidad:");
        Slider sldOp = new Slider(0, 100, imagen.getOpacity() * 100);
        sldOp.valueProperty().addListener((obs, o, n) -> {
            imagen.setOpacity(n.doubleValue() / 100.0);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        CheckBox chkProp = new CheckBox("Mantener proporción");
        chkProp.setSelected(imagen.isMantenerProporcion());
        chkProp.selectedProperty().addListener((obs, o, n) -> {
            imagen.setMantenerProporcion(n);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        container.getChildren().addAll(lblOp, sldOp, chkProp);
    }

    private void addSeccionDatosVariablesImagen(VBox container, ImagenElemento imagen, Button btnReemplazar, Label lblAviso) {
        Label lblSeccion = new Label("Datos Variables");
        lblSeccion.getStyleClass().add("prop-label");

        ComboBox<String> cmbColumna = new ComboBox<>();
        cmbColumna.setMaxWidth(Double.MAX_VALUE);

        List<String> options = new ArrayList<>();
        options.add("(sin vincular)");
        options.addAll(fuenteDatos.getColumnas());
        cmbColumna.setItems(FXCollections.observableArrayList(options));

        String actual = imagen.getColumnaVinculada();
        cmbColumna.setValue(actual != null ? actual : "(sin vincular)");

        boolean vinculado = actual != null;
        btnReemplazar.setVisible(!vinculado);
        btnReemplazar.setManaged(!vinculado);
        lblAviso.setVisible(vinculado);
        lblAviso.setManaged(vinculado);

        cmbColumna.valueProperty().addListener((obs, old, newVal) -> {
            boolean vinculando = !"(sin vincular)".equals(newVal);
            imagen.setColumnaVinculada(vinculando ? newVal : null);

            btnReemplazar.setVisible(!vinculando);
            btnReemplazar.setManaged(!vinculando);
            lblAviso.setVisible(vinculando);
            lblAviso.setManaged(vinculando);

            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        container.getChildren().addAll(lblSeccion, cmbColumna);
    }

    // =====================================================
    // Acciones
    // =====================================================

    private Button createActionBtn(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("prop-action-btn");
        b.setOnAction(handler);
        return b;
    }

    private void reemplazarImagen(Elemento elem) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar Imagen");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = fc.showOpenDialog(canvas.getScene().getWindow());
        if (file == null) return;

        try {
            Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());

            if (img != null) {
                if (elem instanceof ImagenFondoElemento f) {
                    f.setImagen(img);
                    f.setRutaArchivo(file.getAbsolutePath());
                    f.ajustarATamaño(EditorCanvasManager.CARD_WIDTH, EditorCanvasManager.CARD_HEIGHT, EditorCanvasManager.BLEED_MARGIN);
                } else if (elem instanceof ImagenElemento i) {
                    i.setImagen(img);
                    i.setRutaArchivo(file.getAbsolutePath());
                }

                if (onPropertyChanged != null) onPropertyChanged.run();
                if (onCanvasRedraw != null) onCanvasRedraw.run();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void configurarEditor() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar Ejecutable del Editor");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ejecutables", "*.exe", "*.bat", "*.cmd", "*.app"));

        File editor = fc.showOpenDialog(canvas.getScene().getWindow());

        if (editor != null) {
            new SettingsManager().setExternalEditorPath(editor.getAbsolutePath());
            com.tpsstudio.util.TPSToast.mostrar(
                    canvas.getScene().getWindow(),
                    "Editor Configurado",
                    editor.getName(),
                    com.tpsstudio.util.TPSToast.Tipo.EXITO
            );
        }
    }
}