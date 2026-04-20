package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
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
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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

    private static final double MAX_CONTROL_WIDTH = 200.0;

    private final Canvas canvas;

    private Runnable onPropertyChanged;
    private Runnable onCanvasRedrawNeeded;
    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;

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

    public void setFuenteDatos(FuenteDatos fuenteDatos) {
        this.fuenteDatos = fuenteDatos;
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
    public void updatePositionFields(Elemento elemento) {
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

        // Sección Datos Variables (justo después de la etiqueta, antes de posición)
        if (fuenteDatos != null && fuenteDatos.tieneRegistros()) {
            addSeccionDatosVariablesTexto(props, texto);
            props.getChildren().add(new Separator());
        }

        // Posición / Tamaño (X/Y/W/H)
        addPositionSizeControls(props, texto);

        props.getChildren().add(new Separator());

        Label lblTexto = new Label("Texto");
        lblTexto.getStyleClass().add("prop-label");

        TextArea txtContenido = new TextArea(texto.getContenido());
        txtContenido.setPromptText("Contenido");
        txtContenido.setMaxWidth(MAX_CONTROL_WIDTH);
        txtContenido.setPrefRowCount(3);
        txtContenido.setWrapText(true);
        txtContenido.textProperty().addListener((obs, old, newVal) -> {
            texto.setContenido(newVal);
            notifyCanvasRedraw();
        });

        CheckBox chkSaltoLinea = new CheckBox("Pasar a la línea inferior si no cabe");
        chkSaltoLinea.setSelected(texto.isSaltoLinea());
        chkSaltoLinea.getStyleClass().add("prop-checkbox");
        chkSaltoLinea.selectedProperty().addListener((obs, old, newVal) -> {
            texto.setSaltoLinea(newVal);
            notifyCanvasRedraw();
        });

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

        Label lblTamaño = new Label("Tamaño:");
        lblTamaño.getStyleClass().add("prop-label-small");

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

        Label lblAlineacion = new Label("Alineación:");
        lblAlineacion.getStyleClass().add("prop-label-small");

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
        lblEstilo.getStyleClass().add("prop-label-small");

        CheckBox chkNegrita = new CheckBox("Negrita");
        chkNegrita.setSelected(texto.isNegrita());
        chkNegrita.getStyleClass().add("prop-checkbox");
        chkNegrita.setMaxWidth(MAX_CONTROL_WIDTH);
        chkNegrita.selectedProperty().addListener((obs, old, newVal) -> {
            texto.setNegrita(newVal);
            notifyCanvasRedraw();
        });

        CheckBox chkCursiva = new CheckBox("Cursiva");
        chkCursiva.setSelected(texto.isCursiva());
        chkCursiva.getStyleClass().add("prop-checkbox");
        chkCursiva.setMaxWidth(MAX_CONTROL_WIDTH);
        chkCursiva.selectedProperty().addListener((obs, old, newVal) -> {
            texto.setCursiva(newVal);
            notifyCanvasRedraw();
        });

        props.getChildren().addAll(
                lblTexto, txtContenido, chkSaltoLinea,
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

        // Sección Datos Variables (justo después de la etiqueta)
        if (fuenteDatos != null && fuenteDatos.tieneRegistros()) {
            addSeccionDatosVariablesImagen(props, imagen);
            props.getChildren().add(new Separator());
        }

        // Reemplazar Imagen (justo debajo de Datos Variables)
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

        props.getChildren().addAll(btnReemplazar, new Separator());

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

    // ===================== SECCIÓN DATOS VARIABLES =====================

    /* Añade al panel de texto la sección para vincular a una columna del Excel. */
    private void addSeccionDatosVariablesTexto(VBox props, TextoElemento texto) {
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
            if ("(sin vincular)".equals(newVal)) {
                texto.setColumnaVinculada(null);
            } else {
                texto.setColumnaVinculada(newVal);
            }
            notifyCanvasRedraw();
        });

        props.getChildren().addAll(lblSeccion, lblInfo, cmbColumna);
    }

    /* Añade al panel de imagen la sección para vincular a una columna del Excel. */
    private void addSeccionDatosVariablesImagen(VBox props, ImagenElemento imagen) {
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

        cmbColumna.valueProperty().addListener((obs, old, newVal) -> {
            if ("(sin vincular)".equals(newVal)) {
                imagen.setColumnaVinculada(null);
            } else {
                imagen.setColumnaVinculada(newVal);
            }
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
