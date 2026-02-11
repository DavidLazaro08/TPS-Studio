package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.service.SettingsManager;
import com.tpsstudio.util.ImageUtils;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.function.Consumer;

/**
 * Construye el panel de propiedades de la parte derecha.
 * El contenido cambia según el elemento seleccionado:
 * - Texto: contenido, fuente, color, etc.
 * - Imagen: tamaño, opacidad, reemplazo...
 * - Fondo: modo de ajuste, recargar, editor externo...
 */
public class PropertiesPanelController {

    private static final double MAX_CONTROL_WIDTH = 200.0;

    private final Canvas canvas;

    private Runnable onPropertyChanged;
    private Runnable onCanvasRedrawNeeded;
    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;

    public PropertiesPanelController(Canvas canvas) {
        this.canvas = canvas;
    }

    public void setOnPropertyChanged(Runnable callback) {
        this.onPropertyChanged = callback;
    }

    public void setOnCanvasRedrawNeeded(Runnable callback) {
        this.onCanvasRedrawNeeded = callback;
    }

    public void setOnEditExternal(Consumer<ImagenFondoElemento> callback) {
        this.onEditExternal = callback;
    }

    public void setOnReload(Consumer<ImagenFondoElemento> callback) {
        this.onReload = callback;
    }

    /**
     * Construye el panel completo en función del elemento seleccionado.
     * Nota: ahora mismo "proyecto" no se usa, pero lo dejamos por si luego hace falta.
     */
    public VBox buildPanel(Elemento elemento, Proyecto proyecto) {
        VBox props = new VBox(8);
        props.setPadding(new Insets(30));
        props.setFillWidth(true);
        props.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        Label lblProps = new Label("Propiedades");
        lblProps.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        if (elemento == null) {
            Label placeholder = new Label("Seleccione un elemento");
            placeholder.setStyle("-fx-text-fill: #6a6568; -fx-font-size: 12px; -fx-font-style: italic;");
            props.getChildren().addAll(lblProps, placeholder);
            return props;
        }

        if (elemento instanceof ImagenFondoElemento fondo) {
            buildBackgroundPanel(props, lblProps, fondo);
        } else if (elemento instanceof TextoElemento texto) {
            buildTextPanel(props, lblProps, texto);
        } else if (elemento instanceof ImagenElemento imagen) {
            buildImagePanel(props, lblProps, imagen);
        }

        return props;
    }

    // ===================== HELPERS (para no repetir código) =====================

    /**
     * Crea un TextField numérico (X/Y/Ancho/Alto) con listener seguro.
     * Si el usuario escribe algo no numérico, simplemente se ignora.
     */
    private TextField createNumberField(double initialValue, String prompt, Consumer<Double> onValidChange) {
        TextField tf = new TextField(String.format("%.0f", initialValue));
        tf.setPromptText(prompt);
        tf.setMaxWidth(MAX_CONTROL_WIDTH);

        tf.textProperty().addListener((obs, old, newVal) -> {
            try {
                double value = Double.parseDouble(newVal);
                onValidChange.accept(value);
                notifyCanvasRedraw();
            } catch (NumberFormatException ignored) {
                // Si escribe letras o deja vacío, no hacemos nada
            }
        });

        return tf;
    }

    /**
     * Añade al VBox el bloque de "Posición y Tamaño" (X, Y, Ancho, Alto).
     * Sirve tanto para TextoElemento como para ImagenElemento.
     */
    private void addPositionSizeControls(VBox props,
                                         double x, Consumer<Double> setX,
                                         double y, Consumer<Double> setY,
                                         double w, Consumer<Double> setW,
                                         double h, Consumer<Double> setH) {

        Label lblPos = new Label("Posición y Tamaño");
        lblPos.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        TextField txtX = createNumberField(x, "X", setX);
        TextField txtY = createNumberField(y, "Y", setY);
        TextField txtW = createNumberField(w, "Ancho", setW);
        TextField txtH = createNumberField(h, "Alto", setH);

        props.getChildren().addAll(lblPos, txtX, txtY, txtW, txtH);
    }

    /**
     * Bloque reutilizable: "Etiqueta (opcional)" para dar nombre lógico al elemento.
     * Esto es lo que luego te sirve para capas/listas, o para identificar cada objeto.
     */
    private void addEtiquetaControl(VBox props, String etiquetaActual, Consumer<String> setEtiqueta, String prompt) {
        Label lblEtiqueta = new Label("Etiqueta (opcional):");
        lblEtiqueta.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        TextField txtEtiqueta = new TextField(etiquetaActual != null ? etiquetaActual : "");
        txtEtiqueta.setPromptText(prompt);
        txtEtiqueta.setMaxWidth(MAX_CONTROL_WIDTH);

        txtEtiqueta.textProperty().addListener((obs, old, newVal) -> {
            setEtiqueta.accept(newVal.isEmpty() ? null : newVal);
            notifyCanvasRedraw();
        });

        props.getChildren().addAll(lblEtiqueta, txtEtiqueta, new Separator());
    }

    // ===================== PANEL FONDO =====================

    private void buildBackgroundPanel(VBox props, Label lblProps, ImagenFondoElemento fondo) {
        Label lblInfo = new Label("Fondo de la tarjeta");
        lblInfo.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px; -fx-font-style: italic;");

        Label lblDim = new Label(String.format("Dimensiones: %.0f × %.0f px", fondo.getWidth(), fondo.getHeight()));
        lblDim.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");
        lblDim.setMaxWidth(MAX_CONTROL_WIDTH);
        lblDim.setWrapText(true);

        Label lblModo = new Label("Modo de ajuste:");
        lblModo.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        ToggleGroup modoGroup = new ToggleGroup();

        RadioButton rbBleed = new RadioButton("Con sangre (CR80 + sangrado)");
        rbBleed.setToggleGroup(modoGroup);
        rbBleed.setSelected(fondo.getFitMode() == FondoFitMode.BLEED);
        rbBleed.setStyle("-fx-text-fill: #e8e6e7;");
        rbBleed.setMaxWidth(MAX_CONTROL_WIDTH);
        rbBleed.setWrapText(true);

        RadioButton rbFinal = new RadioButton("Sin sangre (CR80 final)");
        rbFinal.setToggleGroup(modoGroup);
        rbFinal.setSelected(fondo.getFitMode() == FondoFitMode.FINAL);
        rbFinal.setStyle("-fx-text-fill: #e8e6e7;");
        rbFinal.setMaxWidth(MAX_CONTROL_WIDTH);
        rbFinal.setWrapText(true);

        modoGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            if (newVal == rbBleed) {
                fondo.setFitMode(FondoFitMode.BLEED);
            } else {
                fondo.setFitMode(FondoFitMode.FINAL);
            }

            fondo.ajustarATamaño(
                    EditorCanvasManager.CARD_WIDTH,
                    EditorCanvasManager.CARD_HEIGHT,
                    EditorCanvasManager.BLEED_MARGIN
            );

            notifyPropertyChanged();
            notifyCanvasRedraw();
        });

        Button btnReemplazar = new Button("Reemplazar Fondo");
        btnReemplazar.setMaxWidth(MAX_CONTROL_WIDTH);
        btnReemplazar.getStyleClass().add("toolbox-btn");
        btnReemplazar.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Reemplazar Fondo");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif")
            );

            File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
            if (file == null) return;

            try {
                Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());
                if (img != null) {
                    fondo.setImagen(img);
                    fondo.setRutaArchivo(file.getAbsolutePath());

                    fondo.ajustarATamaño(
                            EditorCanvasManager.CARD_WIDTH,
                            EditorCanvasManager.CARD_HEIGHT,
                            EditorCanvasManager.BLEED_MARGIN
                    );

                    notifyPropertyChanged();
                    notifyCanvasRedraw();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        Button btnEditarExterno = new Button("Editor Externo");
        btnEditarExterno.setMaxWidth(Double.MAX_VALUE);
        btnEditarExterno.getStyleClass().add("toolbox-btn");
        btnEditarExterno.setOnAction(e -> {
            if (onEditExternal != null) {
                onEditExternal.accept(fondo);
            }
        });

        Button btnConfigEditor = new Button("⚙");
        btnConfigEditor.setTooltip(new Tooltip("Configurar editor externo..."));
        btnConfigEditor.getStyleClass().add("toolbox-btn");
        btnConfigEditor.setPrefWidth(40);
        btnConfigEditor.setOnAction(e -> {
            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
            info.setTitle("Configurar Editor Externo");
            info.setHeaderText("Vincula tu editor de imágenes");
            info.setContentText(
                    "Selecciona el ejecutable de tu programa de edición.\n" +
                            "Ejemplos: Photoshop, Illustrator, GIMP...\n\n" +
                            "Luego podrás abrir el fondo desde aquí."
            );

            ButtonType btnBuscar = new ButtonType("Buscar ejecutable...", ButtonBar.ButtonData.OK_DONE);
            ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
            info.getButtonTypes().setAll(btnBuscar, btnCancelar);

            info.showAndWait().ifPresent(response -> {
                if (response != btnBuscar) return;

                FileChooser fc = new FileChooser();
                fc.setTitle("Seleccionar Ejecutable del Editor");
                fc.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Ejecutables", "*.exe", "*.bat", "*.cmd", "*.app")
                );

                File editor = fc.showOpenDialog(canvas.getScene().getWindow());
                if (editor != null) {
                    new SettingsManager().setExternalEditorPath(editor.getAbsolutePath());

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Editor Configurado");
                    alert.setHeaderText(null);
                    alert.setContentText("Se usará: " + editor.getName());
                    alert.showAndWait();
                }
            });
        });

        HBox cajaEdicion = new HBox(5);
        cajaEdicion.setMaxWidth(MAX_CONTROL_WIDTH);
        HBox.setHgrow(btnEditarExterno, Priority.ALWAYS);
        cajaEdicion.getChildren().addAll(btnEditarExterno, btnConfigEditor);

        Button btnRecargar = new Button("Recargar");
        btnRecargar.setMaxWidth(MAX_CONTROL_WIDTH);
        btnRecargar.getStyleClass().add("toolbox-btn");
        btnRecargar.setOnAction(e -> {
            if (onReload != null) {
                onReload.accept(fondo);
            }
        });

        props.getChildren().addAll(
                lblProps, lblInfo, lblDim,
                new Separator(),
                lblModo, rbBleed, rbFinal,
                new Separator(),
                btnReemplazar, cajaEdicion, btnRecargar
        );
    }

    // ===================== PANEL TEXTO =====================

    private void buildTextPanel(VBox props, Label lblProps, TextoElemento texto) {
        props.getChildren().add(lblProps);

        // Etiqueta del elemento (nombre lógico)
        addEtiquetaControl(props, texto.getEtiqueta(), texto::setEtiqueta, "Ej: NOMBRE, Nº SOCIO...");

        // Posición / Tamaño (X/Y/W/H)
        addPositionSizeControls(
                props,
                texto.getX(), texto::setX,
                texto.getY(), texto::setY,
                texto.getWidth(), texto::setWidth,
                texto.getHeight(), texto::setHeight
        );

        props.getChildren().add(new Separator());

        Label lblTexto = new Label("Texto");
        lblTexto.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        TextField txtContenido = new TextField(texto.getContenido());
        txtContenido.setPromptText("Contenido");
        txtContenido.setMaxWidth(MAX_CONTROL_WIDTH);
        txtContenido.textProperty().addListener((obs, old, newVal) -> {
            texto.setContenido(newVal);
            notifyCanvasRedraw();
        });

        Label lblFuente = new Label("Fuente:");
        lblFuente.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

        ComboBox<String> cmbFuente = new ComboBox<>();
        cmbFuente.getItems().addAll(javafx.scene.text.Font.getFamilies());
        cmbFuente.setValue(texto.getFontFamily());
        cmbFuente.setMaxWidth(MAX_CONTROL_WIDTH);
        cmbFuente.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                texto.setFontFamily(newVal);
                notifyCanvasRedraw();
            }
        });

        Label lblTamaño = new Label("Tamaño:");
        lblTamaño.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

        Spinner<Integer> spnTamaño = new Spinner<>(8, 72, (int) texto.getFontSize());
        spnTamaño.setEditable(true);
        spnTamaño.setMaxWidth(MAX_CONTROL_WIDTH);
        spnTamaño.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                texto.setFontSize(newVal);
                notifyCanvasRedraw();
            }
        });

        Label lblColor = new Label("Color:");
        lblColor.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

        ColorPicker cpColor = new ColorPicker(Color.web(texto.getColor()));
        cpColor.setMaxWidth(MAX_CONTROL_WIDTH);
        cpColor.valueProperty().addListener((obs, old, newVal) -> {
            texto.setColor(String.format("#%02X%02X%02X",
                    (int) (newVal.getRed() * 255),
                    (int) (newVal.getGreen() * 255),
                    (int) (newVal.getBlue() * 255)
            ));
            notifyCanvasRedraw();
        });

        Label lblAlineacion = new Label("Alineación:");
        lblAlineacion.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

        ComboBox<String> cmbAlineacion = new ComboBox<>();
        cmbAlineacion.getItems().addAll("Izquierda", "Centro", "Derecha");

        String currentAlign = texto.getAlineacion();
        cmbAlineacion.setValue(
                "LEFT".equals(currentAlign) ? "Izquierda"
                        : "CENTER".equals(currentAlign) ? "Centro"
                        : "Derecha"
        );

        cmbAlineacion.setMaxWidth(MAX_CONTROL_WIDTH);
        cmbAlineacion.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                texto.setAlineacion(
                        "Izquierda".equals(newVal) ? "LEFT"
                                : "Centro".equals(newVal) ? "CENTER"
                                : "RIGHT"
                );
                notifyCanvasRedraw();
            }
        });

        Label lblEstilo = new Label("Estilo:");
        lblEstilo.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

        CheckBox chkNegrita = new CheckBox("Negrita");
        chkNegrita.setSelected(texto.isNegrita());
        chkNegrita.setStyle("-fx-text-fill: #e8e6e7;");
        chkNegrita.setMaxWidth(MAX_CONTROL_WIDTH);
        chkNegrita.selectedProperty().addListener((obs, old, newVal) -> {
            texto.setNegrita(newVal);
            notifyCanvasRedraw();
        });

        CheckBox chkCursiva = new CheckBox("Cursiva");
        chkCursiva.setSelected(texto.isCursiva());
        chkCursiva.setStyle("-fx-text-fill: #e8e6e7;");
        chkCursiva.setMaxWidth(MAX_CONTROL_WIDTH);
        chkCursiva.selectedProperty().addListener((obs, old, newVal) -> {
            texto.setCursiva(newVal);
            notifyCanvasRedraw();
        });

        props.getChildren().addAll(
                lblTexto, txtContenido,
                lblFuente, cmbFuente,
                lblTamaño, spnTamaño,
                lblColor, cpColor,
                lblAlineacion, cmbAlineacion,
                lblEstilo, chkNegrita, chkCursiva
        );
    }

    // ===================== PANEL IMAGEN =====================

    private void buildImagePanel(VBox props, Label lblProps, ImagenElemento imagen) {
        props.getChildren().add(lblProps);

        // Etiqueta del elemento (nombre lógico)
        addEtiquetaControl(props, imagen.getEtiqueta(), imagen::setEtiqueta, "Ej: FOTO, LOGO...");

        // Posición / Tamaño (X/Y/W/H)
        addPositionSizeControls(
                props,
                imagen.getX(), imagen::setX,
                imagen.getY(), imagen::setY,
                imagen.getWidth(), imagen::setWidth,
                imagen.getHeight(), imagen::setHeight
        );

        props.getChildren().add(new Separator());

        Label lblImagen = new Label("Imagen");
        lblImagen.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblDimOrig = new Label(String.format("Original: %.0f × %.0f px",
                imagen.getOriginalWidth(), imagen.getOriginalHeight()));
        lblDimOrig.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");
        lblDimOrig.setMaxWidth(MAX_CONTROL_WIDTH);
        lblDimOrig.setWrapText(true);

        Label lblOpacidad = new Label("Opacidad:");
        lblOpacidad.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Slider sldOpacidad = new Slider(0, 100, imagen.getOpacity() * 100);
        sldOpacidad.setShowTickLabels(true);
        sldOpacidad.setShowTickMarks(true);
        sldOpacidad.setMajorTickUnit(25);
        sldOpacidad.setMaxWidth(MAX_CONTROL_WIDTH);
        sldOpacidad.valueProperty().addListener((obs, old, newVal) -> {
            imagen.setOpacity(newVal.doubleValue() / 100.0);
            notifyCanvasRedraw();
        });

        CheckBox chkProporcion = new CheckBox("Mantener proporción");
        chkProporcion.setSelected(imagen.isMantenerProporcion());
        chkProporcion.setStyle("-fx-text-fill: #e8e6e7;");
        chkProporcion.setMaxWidth(MAX_CONTROL_WIDTH);
        chkProporcion.selectedProperty().addListener((obs, old, newVal) -> {
            imagen.setMantenerProporcion(newVal);
            notifyCanvasRedraw();
        });

        Button btnReemplazar = new Button("Reemplazar Imagen");
        btnReemplazar.setMaxWidth(MAX_CONTROL_WIDTH);
        btnReemplazar.getStyleClass().add("toolbox-btn");
        btnReemplazar.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Reemplazar Imagen");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif")
            );

            File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
            if (file == null) return;

            try {
                Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());
                if (img != null) {
                    imagen.setImagen(img);
                    imagen.setRutaArchivo(file.getAbsolutePath());

                    notifyPropertyChanged();
                    notifyCanvasRedraw();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        props.getChildren().addAll(
                lblImagen, lblDimOrig,
                lblOpacidad, sldOpacidad,
                chkProporcion,
                btnReemplazar
        );
    }

    // ===================== NOTIFICACIONES =====================

    private void notifyPropertyChanged() {
        if (onPropertyChanged != null) {
            onPropertyChanged.run();
        }
    }

    private void notifyCanvasRedraw() {
        if (onCanvasRedrawNeeded != null) {
            onCanvasRedrawNeeded.run();
        }
    }
}
