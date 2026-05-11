package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ElementoCodigo;
import com.tpsstudio.model.enums.TipoCodigo;
import com.tpsstudio.model.elements.FormaElemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.service.SettingsManager;
import com.tpsstudio.util.ImageUtils;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Construye el panel de propiedades de la parte derecha.
 * El contenido cambia según el elemento seleccionado:
 * - Texto: contenido, fuente, color, etc.
 * - Imagen: tamaño, opacidad, reemplazo...
 * - Fondo: modo de ajuste, recargar, editor externo...
 */
public class PropertiesPanelController {

    private static final double MAX_CONTROL_WIDTH = Double.MAX_VALUE;

    private final Canvas canvas;

    private Runnable onPropertyChanged;
    private Runnable onCanvasRedrawNeeded;
    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;
    private Runnable onDownloadTemplate;

    // Referencias a campos de posición para actualizaciones en tiempo real
    private TextField txtX, txtY, txtW, txtH;

    // Fuente de datos activa (puede ser null si no hay Excel vinculado)
    private FuenteDatos fuenteDatos;

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

    public void setOnDownloadTemplate(Runnable callback) {
        this.onDownloadTemplate = callback;
    }

    public void setFuenteDatos(com.tpsstudio.model.project.FuenteDatos fuenteDatos) {
        this.fuenteDatos = fuenteDatos;
    }

    /**
     * Construye el panel completo en función del elemento seleccionado.
     * Nota: ahora mismo "proyecto" no se usa, pero lo dejamos por si luego hace falta.
     */
    public VBox buildPanel(Elemento elemento, Proyecto proyecto) {
        VBox props = new VBox(10);
        props.setPadding(new Insets(16, 16, 16, 16));
        props.setFillWidth(true);
        props.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        Label lblProps = new Label("Propiedades");
        lblProps.getStyleClass().add("panel-title");

        if (elemento == null) {
            Label placeholder = new Label("Seleccione un elemento");
            placeholder.getStyleClass().add("panel-placeholder");
            props.getChildren().addAll(lblProps, placeholder);
            return props;
        }

        if (elemento instanceof ImagenFondoElemento fondo) {
            buildBackgroundPanel(props, lblProps, fondo);
        } else if (elemento instanceof TextoElemento texto) {
            buildTextPanel(props, lblProps, texto);
        } else if (elemento instanceof ImagenElemento imagen) {
            buildImagePanel(props, lblProps, imagen);
        } else if (elemento instanceof FormaElemento forma) {
            buildFormaPanel(props, lblProps, forma);
        } else if (elemento instanceof ElementoCodigo codigo) {
            buildCodigoPanel(props, lblProps, codigo);
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
    private void addPositionSizeControls(VBox props, Elemento elemento) {

        Label lblPos = new Label("Posición y Tamaño");
        lblPos.getStyleClass().add("prop-label");

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(8);

        Label lblX = new Label("X:");
        lblX.getStyleClass().add("prop-label-small");
        txtX = createNumberField(elemento.getX(), "X", elemento::setX);
        txtX.setPrefWidth(65);

        Label lblY = new Label("Y:");
        lblY.getStyleClass().add("prop-label-small");
        txtY = createNumberField(elemento.getY(), "Y", elemento::setY);
        txtY.setPrefWidth(65);

        Label lblW = new Label("Ancho:");
        lblW.getStyleClass().add("prop-label-small");
        txtW = createNumberField(elemento.getWidth(), "Ancho", elemento::setWidth);
        txtW.setPrefWidth(65);

        Label lblH = new Label("Alto:");
        lblH.getStyleClass().add("prop-label-small");
        txtH = createNumberField(elemento.getHeight(), "Alto", elemento::setHeight);
        txtH.setPrefWidth(65);

        grid.add(lblX, 0, 0); grid.add(txtX, 1, 0);
        grid.add(lblW, 2, 0); grid.add(txtW, 3, 0);

        grid.add(lblY, 0, 1); grid.add(txtY, 1, 1);
        grid.add(lblH, 2, 1); grid.add(txtH, 3, 1);

        props.getChildren().addAll(lblPos, grid);
    }
    
    /**
     * Actualiza los campos de texto con los valores recientes (para arrastrar en tiempo real)
     */
    public void updatePositionFields(com.tpsstudio.model.elements.Elemento elemento) {
        if (elemento == null) return;
        if (txtX != null && !txtX.isFocused()) txtX.setText(String.format(java.util.Locale.US, "%.0f", elemento.getX()));
        if (txtY != null && !txtY.isFocused()) txtY.setText(String.format(java.util.Locale.US, "%.0f", elemento.getY()));
        if (txtW != null && !txtW.isFocused()) txtW.setText(String.format(java.util.Locale.US, "%.0f", elemento.getWidth()));
        if (txtH != null && !txtH.isFocused()) txtH.setText(String.format(java.util.Locale.US, "%.0f", elemento.getHeight()));
    }

    /**
     * Bloque reutilizable: "Etiqueta (opcional)" para dar nombre lógico al elemento.
     * Esto es lo que luego te sirve para capas/listas, o para identificar cada objeto.
     */
    private void addEtiquetaControl(VBox props, String etiquetaActual, Consumer<String> setEtiqueta, String prompt) {
        Label lblEtiqueta = new Label("Etiqueta (opcional):");
        lblEtiqueta.getStyleClass().add("prop-label");

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
        lblInfo.getStyleClass().add("prop-label");

        Label lblDim = new Label(String.format("Dimensiones: %.0f × %.0f px", fondo.getWidth(), fondo.getHeight()));
        lblDim.getStyleClass().add("prop-label-small");
        lblDim.setMaxWidth(MAX_CONTROL_WIDTH);
        lblDim.setWrapText(true);

        Label lblModo = new Label("Modo de ajuste:");
        lblModo.getStyleClass().add("prop-label");

        ToggleGroup modoGroup = new ToggleGroup();

        RadioButton rbBleed = new RadioButton("Con sangre (CR80 + sangrado)");
        rbBleed.setToggleGroup(modoGroup);
        rbBleed.setSelected(fondo.getFitMode() == FondoFitMode.BLEED);
        rbBleed.getStyleClass().add("prop-radio");
        rbBleed.setMaxWidth(MAX_CONTROL_WIDTH);
        rbBleed.setWrapText(true);

        RadioButton rbFinal = new RadioButton("Sin sangre (CR80 final)");
        rbFinal.setToggleGroup(modoGroup);
        rbFinal.setSelected(fondo.getFitMode() == FondoFitMode.FINAL);
        rbFinal.getStyleClass().add("prop-radio");
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

        Button btnReemplazar = new Button("🖼  Reemplazar Fondo");
        btnReemplazar.setMaxWidth(MAX_CONTROL_WIDTH);
        btnReemplazar.getStyleClass().add("prop-action-btn");
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

        Button btnEditarExterno = new Button("✏  Editor Externo");
        btnEditarExterno.setMaxWidth(Double.MAX_VALUE);
        btnEditarExterno.getStyleClass().add("prop-action-btn");
        btnEditarExterno.setOnAction(e -> {
            if (onEditExternal != null) {
                onEditExternal.accept(fondo);
            }
        });

        Button btnConfigEditor = new Button("⚙");
        btnConfigEditor.setTooltip(new Tooltip("Configurar editor externo..."));
        btnConfigEditor.getStyleClass().add("prop-action-btn");
        btnConfigEditor.setPrefWidth(40);
        btnConfigEditor.setOnAction(e -> {
            Alert info = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
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

                    Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.INFORMATION);
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

        Button btnRecargar = new Button("🔄  Recargar");
        btnRecargar.setMaxWidth(MAX_CONTROL_WIDTH);
        btnRecargar.getStyleClass().add("prop-action-btn");
        btnRecargar.setOnAction(e -> {
            if (onReload != null) {
                onReload.accept(fondo);
            }
        });

        Button btnDescargarPlantilla = new Button("Descargar Plantilla CR80");
        btnDescargarPlantilla.setMaxWidth(MAX_CONTROL_WIDTH);
        btnDescargarPlantilla.getStyleClass().add("primary-btn");
        btnDescargarPlantilla.setOnAction(e -> {
            if (onDownloadTemplate != null) {
                onDownloadTemplate.run();
            }
        });

        props.getChildren().addAll(
                lblProps, lblInfo, lblDim,
                new Separator(),
                lblModo, rbBleed, rbFinal,
                new Separator(),
                btnReemplazar, cajaEdicion, btnRecargar,
                new Separator(),
                btnDescargarPlantilla
        );
    }

    // ===================== PANEL TEXTO =====================

    private void buildTextPanel(VBox props, Label lblProps, TextoElemento texto) {
        props.getChildren().add(lblProps);

        // Etiqueta del elemento (nombre lógico)
        addEtiquetaControl(props, texto.getEtiqueta(), texto::setEtiqueta, "Ej: NOMBRE, Nº SOCIO...");

        // Controles de Texto - se crean aquí para poder pasarlos a Datos Variables
        Label lblTexto = new Label("Texto:");
        lblTexto.getStyleClass().add("prop-label-small");

        TextArea txtContenido = new TextArea(texto.getContenido());
        txtContenido.setPromptText("Contenido del texto...");
        txtContenido.setMaxWidth(MAX_CONTROL_WIDTH);
        txtContenido.setPrefRowCount(3);
        txtContenido.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        txtContenido.setWrapText(true);
        txtContenido.textProperty().addListener((obs, old, newVal) -> {
            texto.setContenido(newVal);
            notifyCanvasRedraw();
        });

        // Aplicar estado inicial según vinculación actual
        boolean vinculadoInicial = texto.getColumnaVinculada() != null;
        txtContenido.setDisable(vinculadoInicial);
        if (vinculadoInicial) {
            lblTexto.setText("Texto (vinculado a la base de datos):");
        }

        // Sección Datos Variables (justo después de la etiqueta, antes de posición)
        if (fuenteDatos != null && fuenteDatos.tieneRegistros()) {
            addSeccionDatosVariablesTexto(props, texto, lblTexto, txtContenido);
            props.getChildren().add(new Separator());
        }

        // Posición / Tamaño (X/Y/W/H)
        addPositionSizeControls(props, texto);

        props.getChildren().add(new Separator());

        CheckBox chkSaltoLinea = new CheckBox("Pasar a la línea inferior si no cabe");
        chkSaltoLinea.setSelected(texto.isSaltoLinea());
        chkSaltoLinea.getStyleClass().add("prop-checkbox");
        chkSaltoLinea.selectedProperty().addListener((obs, old, newVal) -> {
            texto.setSaltoLinea(newVal);
            notifyCanvasRedraw();
        });

        // ---- Fuente ----
        Label lblFuente = new Label("Fuente:");
        lblFuente.getStyleClass().add("prop-label-small");

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

        // ---- Tamaño ----
        Label lblTamaño = new Label("Tamaño:");
        lblTamaño.getStyleClass().add("prop-label-small");

        Spinner<Integer> spnTamaño = new Spinner<>(8, 72, (int) texto.getFontSize());
        spnTamaño.setEditable(true);
        spnTamaño.setPrefWidth(65);
        spnTamaño.setMinWidth(65);
        spnTamaño.setMaxWidth(65);
        spnTamaño.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                texto.setFontSize(newVal);
                notifyCanvasRedraw();
            }
        });
        
        VBox colTamaño = new VBox(4, lblTamaño, spnTamaño);

        // ---- Estilo: B / I ----
        Label lblEstilo = new Label("Estilo:");
        lblEstilo.getStyleClass().add("prop-label-small");

        ToggleButton btnBold = new ToggleButton("B");
        btnBold.setTooltip(new Tooltip("Negrita"));
        btnBold.getStyleClass().addAll("prop-toggle-btn", "btn-first");
        btnBold.setSelected(texto.isNegrita());
        btnBold.setStyle("-fx-font-weight: bold;");
        btnBold.setOnAction(e -> { texto.setNegrita(btnBold.isSelected()); notifyCanvasRedraw(); });

        ToggleButton btnItalic = new ToggleButton("I");
        btnItalic.setTooltip(new Tooltip("Cursiva"));
        btnItalic.getStyleClass().addAll("prop-toggle-btn", "btn-last");
        btnItalic.setSelected(texto.isCursiva());
        btnItalic.setStyle("-fx-font-style: italic; -fx-font-family: 'Georgia', 'Serif';");
        btnItalic.setOnAction(e -> { texto.setCursiva(btnItalic.isSelected()); notifyCanvasRedraw(); });

        HBox groupEstilo = new HBox(0, btnBold, btnItalic);
        groupEstilo.getStyleClass().add("prop-segmented-group");

        VBox colEstilo = new VBox(4, lblEstilo, groupEstilo);

        // ---- Alineación ----
        Label lblAlineacion = new Label("Alineación:");
        lblAlineacion.getStyleClass().add("prop-label-small");

        ToggleGroup groupAlign = new ToggleGroup();
        ToggleButton btnLeft   = new ToggleButton("\u2261");
        ToggleButton btnCenter = new ToggleButton("\u2263");
        ToggleButton btnRight  = new ToggleButton("\u2262");

        btnLeft.setTooltip(new Tooltip("Izquierda"));
        btnLeft.setToggleGroup(groupAlign);
        btnLeft.getStyleClass().addAll("prop-toggle-btn", "btn-first");
        btnLeft.setSelected("LEFT".equals(texto.getAlineacion()));
        btnLeft.setOnAction(e -> { texto.setAlineacion("LEFT"); notifyCanvasRedraw(); });

        btnCenter.setTooltip(new Tooltip("Centrado"));
        btnCenter.setToggleGroup(groupAlign);
        btnCenter.getStyleClass().add("prop-toggle-btn");
        btnCenter.setSelected("CENTER".equals(texto.getAlineacion()));
        btnCenter.setOnAction(e -> { texto.setAlineacion("CENTER"); notifyCanvasRedraw(); });

        btnRight.setTooltip(new Tooltip("Derecha"));
        btnRight.setToggleGroup(groupAlign);
        btnRight.getStyleClass().add("prop-toggle-btn");
        btnRight.setSelected("RIGHT".equals(texto.getAlineacion()));
        btnRight.setOnAction(e -> { texto.setAlineacion("RIGHT"); notifyCanvasRedraw(); });

        HBox groupAlineacion = new HBox(0, btnLeft, btnCenter, btnRight);
        groupAlineacion.getStyleClass().add("prop-segmented-group");

        VBox colAlineacion = new VBox(4, lblAlineacion, groupAlineacion);

        // Fila combinada para Tamaño, Estilo y Alineación (espacio reducido)
        HBox filaHerramientas = new HBox(8, colTamaño, colEstilo, colAlineacion);
        filaHerramientas.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);

        // ---- Color ----
        Label lblColor = new Label("Color:");
        lblColor.getStyleClass().add("prop-label-small");

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

        props.getChildren().addAll(
                lblTexto, txtContenido, chkSaltoLinea,
                lblFuente, cmbFuente,
                filaHerramientas,
                lblColor, cpColor
        );
    }

    // ===================== PANEL IMAGEN =====================

    private void buildImagePanel(VBox props, Label lblProps, ImagenElemento imagen) {
        props.getChildren().add(lblProps);

        // Etiqueta del elemento (nombre lógico)
        addEtiquetaControl(props, imagen.getEtiqueta(), imagen::setEtiqueta, "Ej: FOTO, LOGO...");

        // Sección Datos Variables (justo después de la etiqueta)
        Label lblAviso = new Label("💡 Para ver las imágenes, estas deben estar en la carpeta 'Fotos' de tu proyecto con mismo nombre y extensión que en la base de datos.");
        lblAviso.getStyleClass().add("prop-info-message");
        lblAviso.setMaxWidth(MAX_CONTROL_WIDTH);
        lblAviso.setVisible(false);
        lblAviso.setManaged(false);

        Button btnReemplazar = new Button("🖼  Reemplazar Imagen");
        btnReemplazar.setMaxWidth(MAX_CONTROL_WIDTH);
        btnReemplazar.getStyleClass().add("prop-action-btn");

        if (fuenteDatos != null && fuenteDatos.tieneRegistros()) {
            addSeccionDatosVariablesImagen(props, imagen, btnReemplazar, lblAviso);
            props.getChildren().add(new Separator());
        }

        // Reemplazar Imagen (justo debajo de Datos Variables)
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

        props.getChildren().addAll(btnReemplazar, lblAviso, new Separator());

        // Posición / Tamaño (X/Y/W/H)
        addPositionSizeControls(props, imagen);

        props.getChildren().add(new Separator());

        Label lblOpacidad = new Label("Opacidad:");
        lblOpacidad.getStyleClass().add("prop-label");

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
        chkProporcion.getStyleClass().add("prop-checkbox");
        chkProporcion.setMaxWidth(MAX_CONTROL_WIDTH);
        chkProporcion.selectedProperty().addListener((obs, old, newVal) -> {
            imagen.setMantenerProporcion(newVal);
            notifyCanvasRedraw();
        });

        props.getChildren().addAll(lblOpacidad, sldOpacidad, chkProporcion);
    }

    // ===================== PANEL FORMA =====================

    private void buildFormaPanel(VBox props, Label lblProps, FormaElemento forma) {
        props.getChildren().add(lblProps);

        // Etiqueta
        addEtiquetaControl(props, forma.getEtiqueta(), forma::setEtiqueta, "Ej: RECUADRO, MARCO...");

        // Posición y Tamaño
        addPositionSizeControls(props, forma);

        props.getChildren().add(new Separator());

        Label lblEstilo = new Label("Estilo de Forma");
        lblEstilo.getStyleClass().add("prop-label");

        // Borde activo
        CheckBox chkBorde = new CheckBox("Borde activo");
        chkBorde.setSelected(forma.isConBorde());
        chkBorde.getStyleClass().add("prop-checkbox");

        // Color de Borde
        Label lblBorde = new Label("Color del Borde:");
        lblBorde.getStyleClass().add("prop-label-small");
        ColorPicker cpBorde = new ColorPicker(Color.web(forma.getColorBorde()));
        cpBorde.setMaxWidth(MAX_CONTROL_WIDTH);
        cpBorde.setDisable(!forma.isConBorde());

        // Grosor de Borde
        Label lblGrosor = new Label("Grosor del Borde:");
        lblGrosor.getStyleClass().add("prop-label-small");
        Spinner<Double> spnGrosor = new Spinner<>(0.5, 20.0, forma.getGrosorBorde(), 0.5);
        spnGrosor.setEditable(true);
        spnGrosor.setMaxWidth(MAX_CONTROL_WIDTH);
        spnGrosor.setDisable(!forma.isConBorde());

        chkBorde.selectedProperty().addListener((obs, old, newVal) -> {
            forma.setConBorde(newVal);
            cpBorde.setDisable(!newVal);
            spnGrosor.setDisable(!newVal);
            notifyCanvasRedraw();
        });

        cpBorde.valueProperty().addListener((obs, old, newVal) -> {
            forma.setColorBorde(String.format("#%02X%02X%02X",
                    (int) (newVal.getRed() * 255),
                    (int) (newVal.getGreen() * 255),
                    (int) (newVal.getBlue() * 255)
            ));
            notifyCanvasRedraw();
        });

        spnGrosor.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                forma.setGrosorBorde(newVal);
                notifyCanvasRedraw();
            }
        });

        props.getChildren().addAll(lblEstilo, chkBorde, lblBorde, cpBorde, lblGrosor, spnGrosor);

        // --- Opacidad ---
        Label lblOpacidad = new Label("Opacidad:");
        lblOpacidad.getStyleClass().add("prop-label-small");
        Slider sldOpacidad = new Slider(0, 1, forma.getOpacidad());
        sldOpacidad.valueProperty().addListener((obs, old, newVal) -> {
            forma.setOpacidad(newVal.doubleValue());
            notifyCanvasRedraw();
        });
        props.getChildren().addAll(lblOpacidad, sldOpacidad);

        // --- Redondeado (Solo Rectángulos) ---
        if (forma.getTipoForma() == FormaElemento.TipoForma.RECTANGULO) {
            Label lblRadio = new Label("Redondeado de Esquinas:");
            lblRadio.getStyleClass().add("prop-label-small");
            Slider sldRadio = new Slider(0, 100, forma.getRadioCurvatura());
            sldRadio.valueProperty().addListener((obs, old, newVal) -> {
                forma.setRadioCurvatura(newVal.doubleValue());
                notifyCanvasRedraw();
            });
            props.getChildren().addAll(lblRadio, sldRadio);
        }

        // Relleno (solo para Rectángulo y Elipse)
        if (forma.getTipoForma() != FormaElemento.TipoForma.LINEA) {
            props.getChildren().add(new Separator());

            CheckBox chkRelleno = new CheckBox("Relleno activo");
            chkRelleno.setSelected(forma.isConRelleno());
            chkRelleno.getStyleClass().add("prop-checkbox");

            Label lblRelleno = new Label("Color de Relleno:");
            lblRelleno.getStyleClass().add("prop-label-small");
            ColorPicker cpRelleno = new ColorPicker(Color.web(forma.getColorRelleno()));
            cpRelleno.setMaxWidth(MAX_CONTROL_WIDTH);
            cpRelleno.setDisable(!forma.isConRelleno());

            chkRelleno.selectedProperty().addListener((obs, old, newVal) -> {
                forma.setConRelleno(newVal);
                cpRelleno.setDisable(!newVal);
                notifyCanvasRedraw();
            });

            cpRelleno.valueProperty().addListener((obs, old, newVal) -> {
                forma.setColorRelleno(String.format("#%02X%02X%02X",
                        (int) (newVal.getRed() * 255),
                        (int) (newVal.getGreen() * 255),
                        (int) (newVal.getBlue() * 255)
                ));
                notifyCanvasRedraw();
            });

            props.getChildren().addAll(chkRelleno, lblRelleno, cpRelleno);
        }
    }

    // ===================== PANEL QR =====================

    // ===================== PANEL CÓDIGOS (QR / BARRAS) =====================

    private void buildCodigoPanel(VBox props, Label lblProps, ElementoCodigo codigo) {
        props.getChildren().add(lblProps);

        // Selector de Tipo de Código
        Label lblTipo = new Label("Tipo de Código:");
        lblTipo.getStyleClass().add("prop-label-small");
        ComboBox<TipoCodigo> cmbTipo = new ComboBox<>(FXCollections.observableArrayList(TipoCodigo.values()));
        cmbTipo.setMaxWidth(MAX_CONTROL_WIDTH);
        cmbTipo.setValue(codigo.getTipo());
        cmbTipo.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && newVal != old) {
                codigo.setTipo(newVal);
                notifyCanvasRedraw();
                // Forzar reconstrucción completa del panel porque cambian los campos
                if (onPropertyChanged != null) onPropertyChanged.run();
            }
        });

        // Etiqueta del elemento
        addEtiquetaControl(props, codigo.getEtiqueta(), codigo::setEtiqueta, "Ej: QR WEB, CODIGO SOCIO...");

        Label lblContenido = new Label("Contenido:");
        lblContenido.getStyleClass().add("prop-label-small");

        TextField txtContenido = new TextField(codigo.getContenido());
        txtContenido.setPromptText("Introduzca los datos...");
        txtContenido.setMaxWidth(MAX_CONTROL_WIDTH);
        txtContenido.textProperty().addListener((obs, old, newVal) -> {
            codigo.setContenido(newVal);
            notifyCanvasRedraw();
        });

        // Aplicar estado inicial según vinculación
        boolean vinculadoInicial = codigo.getColumnaVinculada() != null;
        txtContenido.setDisable(vinculadoInicial);
        if (vinculadoInicial) {
            lblContenido.setText("Contenido (vinculado a base de datos):");
        }

        // Sección Datos Variables
        VBox varBox = new VBox(4);
        if (fuenteDatos != null && fuenteDatos.tieneRegistros()) {
            addSeccionDatosVariablesCodigo(varBox, codigo, lblContenido, txtContenido);
            varBox.getChildren().add(new Separator());
        }

        // Posición y Tamaño
        VBox posSizeBox = new VBox();
        addPositionSizeControls(posSizeBox, codigo);

        // Colores
        Label lblColorCodigo = new Label("Color del Código:");
        lblColorCodigo.getStyleClass().add("prop-label-small");
        ColorPicker cpColor = new ColorPicker(Color.web(codigo.getColorCodigo()));
        cpColor.setMaxWidth(MAX_CONTROL_WIDTH);
        cpColor.valueProperty().addListener((obs, old, newVal) -> {
            codigo.setColorCodigo(colorToHex(newVal));
            notifyCanvasRedraw();
        });

        Label lblColorFondo = new Label("Color de Fondo:");
        lblColorFondo.getStyleClass().add("prop-label-small");
        ColorPicker cpFondo = new ColorPicker(Color.web(codigo.getColorFondo()));
        cpFondo.setMaxWidth(MAX_CONTROL_WIDTH);
        cpFondo.valueProperty().addListener((obs, old, newVal) -> {
            codigo.setColorFondo(colorToHex(newVal));
            notifyCanvasRedraw();
        });

        // Margen
        Label lblMargen = new Label("Margen (Quiet Zone):");
        lblMargen.getStyleClass().add("prop-label-small");
        Spinner<Integer> spMargen = new Spinner<>(0, 50, codigo.getMargen());
        spMargen.setMaxWidth(MAX_CONTROL_WIDTH);
        spMargen.valueProperty().addListener((obs, old, newVal) -> {
            codigo.setMargen(newVal);
            notifyCanvasRedraw();
        });

        props.getChildren().addAll(lblTipo, cmbTipo, new Separator(), lblContenido, txtContenido, varBox, posSizeBox, new Separator(), lblColorCodigo, cpColor, lblColorFondo, cpFondo, lblMargen, spMargen);

        // Controles específicos de QR
        if (codigo.getTipo() == TipoCodigo.QR) {
            Label lblError = new Label("Nivel de corrección:");
            lblError.getStyleClass().add("prop-label-small");
            ComboBox<String> cmbError = new ComboBox<>(FXCollections.observableArrayList("L (7%)", "M (15%)", "Q (25%)", "H (30%)"));
            cmbError.setMaxWidth(MAX_CONTROL_WIDTH);
            
            String nivelActual = codigo.getNivelCorreccion();
            for (String item : cmbError.getItems()) {
                if (item.startsWith(nivelActual)) { cmbError.setValue(item); break; }
            }

            cmbError.valueProperty().addListener((obs, old, newVal) -> {
                if (newVal != null) {
                    codigo.setNivelCorreccion(newVal.substring(0, 1));
                    notifyCanvasRedraw();
                }
            });
            props.getChildren().addAll(lblError, cmbError);
        } else {
            // Controles específicos de códigos de barras (1D)
            HBox textConfigRow = new HBox(8);
            textConfigRow.setAlignment(Pos.CENTER_LEFT);

            // 1. Checkbox compacto
            CheckBox chkTexto = new CheckBox("Texto");
            chkTexto.getStyleClass().add("prop-checkbox");
            chkTexto.setSelected(codigo.isMostrarTexto());
            chkTexto.setMinWidth(70);
            chkTexto.selectedProperty().addListener((obs, old, newVal) -> {
                codigo.setMostrarTexto(newVal);
                notifyCanvasRedraw();
                if (onPropertyChanged != null) onPropertyChanged.run();
            });

            textConfigRow.getChildren().add(chkTexto);

            // 2. Controles de estilo (solo si está activo)
            if (codigo.isMostrarTexto()) {
                // Tamaño (Spinner idéntico al de textos)
                Spinner<Integer> spSize = new Spinner<>(6, 24, codigo.getFontSize());
                spSize.setPrefWidth(60);
                spSize.setMinWidth(60);
                spSize.setMaxWidth(60);
                spSize.valueProperty().addListener((obs, old, newVal) -> {
                    codigo.setFontSize(newVal);
                    notifyCanvasRedraw();
                });

                // Botones B e I (Segmented Group como en textos)
                ToggleButton btnBold = new ToggleButton("B");
                btnBold.getStyleClass().addAll("prop-toggle-btn", "btn-first");
                btnBold.setSelected(codigo.isNegrita());
                btnBold.setPrefWidth(30);
                btnBold.setStyle("-fx-font-weight: bold;");
                btnBold.setOnAction(e -> { codigo.setNegrita(btnBold.isSelected()); notifyCanvasRedraw(); });

                ToggleButton btnItalic = new ToggleButton("I");
                btnItalic.getStyleClass().addAll("prop-toggle-btn", "btn-last");
                btnItalic.setSelected(codigo.isCursiva());
                btnItalic.setPrefWidth(30);
                btnItalic.setStyle("-fx-font-style: italic; -fx-font-family: 'Georgia', 'Serif';");
                btnItalic.setOnAction(e -> { codigo.setCursiva(btnItalic.isSelected()); notifyCanvasRedraw(); });

                HBox styleButtons = new HBox(0, btnBold, btnItalic);
                styleButtons.getStyleClass().add("prop-segmented-group");

                textConfigRow.getChildren().addAll(spSize, styleButtons);
            }
            
            props.getChildren().add(textConfigRow);
        }
    }

    private void addSeccionDatosVariablesCodigo(VBox box, ElementoCodigo codigo, Label lblContenido, TextField txtContenido) {
        Label lblSeccion = new Label("Vincular Columna");
        lblSeccion.getStyleClass().add("prop-label-small");

        ComboBox<String> cmbColumna = new ComboBox<>();
        cmbColumna.setMaxWidth(MAX_CONTROL_WIDTH);
        List<String> opciones = new ArrayList<>();
        opciones.add("(sin vincular)");
        opciones.addAll(fuenteDatos.getColumnas());
        cmbColumna.setItems(FXCollections.observableArrayList(opciones));

        String actual = codigo.getColumnaVinculada();
        cmbColumna.setValue(actual != null ? actual : "(sin vincular)");

        cmbColumna.valueProperty().addListener((obs, old, newVal) -> {
            boolean isVinculado = !"(sin vincular)".equals(newVal);
            codigo.setColumnaVinculada(isVinculado ? newVal : null);
            txtContenido.setDisable(isVinculado);
            lblContenido.setText(isVinculado ? "Contenido (vinculado):" : "Contenido:");
            codigo.invalidarCache();
            notifyCanvasRedraw();
        });

        box.getChildren().addAll(lblSeccion, cmbColumna);
    }

    private String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }

    // ===================== SECCIÓN DATOS VARIABLES =====================

    /* Añade al panel de texto la sección para vincular a una columna del Excel.
     * Recibe lblTexto y txtContenido para actualizar su estado según la vinculación. */
    private void addSeccionDatosVariablesTexto(VBox props, TextoElemento texto, Label lblTexto, TextArea txtContenido) {
        Label lblSeccion = new Label("Datos Variables");
        lblSeccion.getStyleClass().add("prop-label");

        Label lblInfo = new Label("Columna del Excel:");
        lblInfo.getStyleClass().add("prop-label-small");

        ComboBox<String> cmbColumna = new ComboBox<>();
        cmbColumna.setMaxWidth(MAX_CONTROL_WIDTH);

        List<String> opciones = new ArrayList<>();
        opciones.add("(sin vincular)");
        opciones.addAll(fuenteDatos.getColumnas());
        cmbColumna.setItems(FXCollections.observableArrayList(opciones));

        String actual = texto.getColumnaVinculada();
        cmbColumna.setValue(actual != null ? actual : "(sin vincular)");

        cmbColumna.valueProperty().addListener((obs, old, newVal) -> {
            boolean isVinculado = !"(sin vincular)".equals(newVal);
            if (!isVinculado) {
                texto.setColumnaVinculada(null);
            } else {
                texto.setColumnaVinculada(newVal);
            }

            // Actualizar estado del cuadro de texto dinámicamente
            txtContenido.setDisable(isVinculado);
            lblTexto.setText(isVinculado ? "Texto (vinculado a la base de datos):" : "Texto:");

            notifyCanvasRedraw();
        });

        props.getChildren().addAll(lblSeccion, lblInfo, cmbColumna);
    }

    /* Añade al panel de imagen la sección para vincular a una columna del Excel. */
    private void addSeccionDatosVariablesImagen(VBox props, ImagenElemento imagen, Button btnReemplazar, Label lblAviso) {
        Label lblSeccion = new Label("Datos Variables");
        lblSeccion.getStyleClass().add("prop-label");

        Label lblInfo = new Label("Columna del Excel (nombre de archivo):");
        lblInfo.getStyleClass().add("prop-label-small");
        lblInfo.setMaxWidth(MAX_CONTROL_WIDTH);
        lblInfo.setWrapText(true);

        ComboBox<String> cmbColumna = new ComboBox<>();
        cmbColumna.setMaxWidth(MAX_CONTROL_WIDTH);

        List<String> opciones = new ArrayList<>();
        opciones.add("(sin vincular)");
        opciones.addAll(fuenteDatos.getColumnas());
        cmbColumna.setItems(FXCollections.observableArrayList(opciones));

        String actual = imagen.getColumnaVinculada();
        cmbColumna.setValue(actual != null ? actual : "(sin vincular)");

        // Lógica de visibilidad inicial
        boolean vinculado = actual != null;
        btnReemplazar.setVisible(!vinculado);
        btnReemplazar.setManaged(!vinculado);
        lblAviso.setVisible(vinculado);
        lblAviso.setManaged(vinculado);

        cmbColumna.valueProperty().addListener((obs, old, newVal) -> {
            boolean isVinculado = !"(sin vincular)".equals(newVal);
            if (!isVinculado) {
                imagen.setColumnaVinculada(null);
            } else {
                imagen.setColumnaVinculada(newVal);
            }

            // Actualizar visibilidad dinámicamente
            btnReemplazar.setVisible(!isVinculado);
            btnReemplazar.setManaged(!isVinculado);
            lblAviso.setVisible(isVinculado);
            lblAviso.setManaged(isVinculado);

            notifyCanvasRedraw();
        });

        props.getChildren().addAll(lblSeccion, lblInfo, cmbColumna);
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
