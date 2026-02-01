package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.*;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.function.Consumer;
import com.tpsstudio.service.SettingsManager;
import com.tpsstudio.util.ImageUtils;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Controller responsable de construir y gestionar el panel de propiedades
 * del elemento seleccionado en el editor.
 * 
 * Maneja la construcción dinámica del panel según el tipo de elemento
 * y los bindings bidireccionales entre la UI y las propiedades del elemento.
 */
public class PropertiesPanelController {

    // Ancho máximo de controles para mantener márgenes visuales
    private static final double MAX_CONTROL_WIDTH = 200.0;

    // Canvas reference para obtener la ventana padre en diálogos
    private final Canvas canvas;

    // Callbacks para notificar cambios al MainViewController
    private Runnable onPropertyChanged;
    private Runnable onCanvasRedrawNeeded;
    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;

    /**
     * Constructor
     * 
     * @param canvas Canvas del editor (usado para obtener la ventana padre)
     */
    public PropertiesPanelController(Canvas canvas) {
        this.canvas = canvas;
    }

    /**
     * Establece el callback que se ejecuta cuando una propiedad cambia
     * 
     * @param callback Runnable a ejecutar
     */
    public void setOnPropertyChanged(Runnable callback) {
        this.onPropertyChanged = callback;
    }

    /**
     * Establece el callback que se ejecuta cuando se necesita redibujar el canvas
     * 
     * @param callback Runnable a ejecutar
     */
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
     * Construye el panel de propiedades según el tipo de elemento seleccionado
     * 
     * @param elemento Elemento seleccionado (puede ser null)
     * @param proyecto Proyecto actual
     * @return VBox con el panel de propiedades
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
        } else if (elemento instanceof ImagenFondoElemento) {
            buildBackgroundPanel(props, lblProps, (ImagenFondoElemento) elemento);
        } else if (elemento instanceof TextoElemento) {
            buildTextPanel(props, lblProps, (TextoElemento) elemento);
        } else if (elemento instanceof ImagenElemento) {
            buildImagePanel(props, lblProps, (ImagenElemento) elemento);
        }

        return props;
    }

    /**
     * Construye el panel de propiedades para un elemento de fondo
     */
    private void buildBackgroundPanel(VBox props, Label lblProps, ImagenFondoElemento fondo) {
        Label lblInfo = new Label("Fondo de la tarjeta");
        lblInfo.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px; -fx-font-style: italic;");

        Label lblDim = new Label(String.format("Dimensiones: %.0f × %.0f px",
                fondo.getWidth(), fondo.getHeight()));
        lblDim.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");
        lblDim.setMaxWidth(MAX_CONTROL_WIDTH);
        lblDim.setWrapText(true);

        // Modo de ajuste
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
            fondo.ajustarATamaño(EditorCanvasManager.CARD_WIDTH, EditorCanvasManager.CARD_HEIGHT,
                    EditorCanvasManager.BLEED_MARGIN);
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
                    new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
            if (file != null) {
                try {
                    // Usar carga sin bloqueo
                    Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());
                    if (img != null) {
                        fondo.setImagen(img);
                        fondo.setRutaArchivo(file.getAbsolutePath());
                        fondo.ajustarATamaño(EditorCanvasManager.CARD_WIDTH, EditorCanvasManager.CARD_HEIGHT,
                                EditorCanvasManager.BLEED_MARGIN);
                        notifyCanvasRedraw();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        Button btnEditarExterno = new Button("Editor Externo");
        btnEditarExterno.setMaxWidth(Double.MAX_VALUE); // Expandir en el HBox
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
            // 1. Mostrar explicación
            Alert info = new Alert(Alert.AlertType.CONFIRMATION);
            info.setTitle("Configurar Editor Externo");
            info.setHeaderText("Vincula tu editor de imágenes favorito");
            info.setContentText(
                    "Selecciona el archivo ejecutable (.exe) de tu programa de edición preferido \n" +
                            "(por ejemplo: Photoshop, Illustrator, GIMP...).\n\n" +
                            "Esto permitirá abrir los fondos directamente en ese programa al pulsar 'Editar Externa'.");

            ButtonType btnBuscar = new ButtonType("Buscar Ejecutable...", ButtonBar.ButtonData.OK_DONE);
            ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
            info.getButtonTypes().setAll(btnBuscar, btnCancelar);

            info.showAndWait().ifPresent(response -> {
                if (response == btnBuscar) {
                    // 2. Abrir selector
                    FileChooser fc = new FileChooser();
                    fc.setTitle("Seleccionar Ejecutable del Editor");
                    fc.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("Ejecutables", "*.exe", "*.app", "*.bat", "*.cmd"));
                    File editor = fc.showOpenDialog(canvas.getScene().getWindow());
                    if (editor != null) {
                        new SettingsManager().setExternalEditorPath(editor.getAbsolutePath());
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Editor Configurado");
                        alert.setHeaderText(null);
                        alert.setContentText("¡Listo! Se usará: " + editor.getName());
                        alert.showAndWait();
                    }
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

        props.getChildren().addAll(lblProps, lblInfo, lblDim,
                new Separator(), lblModo, rbBleed, rbFinal, new Separator(),
                btnReemplazar, cajaEdicion, btnRecargar);
    }

    /**
     * Construye el panel de propiedades para un elemento de texto
     */
    private void buildTextPanel(VBox props, Label lblProps, TextoElemento texto) {
        // Etiqueta
        Label lblEtiqueta = new Label("Etiqueta (opcional):");
        lblEtiqueta.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        TextField txtEtiqueta = new TextField(texto.getEtiqueta() != null ? texto.getEtiqueta() : "");
        txtEtiqueta.setPromptText("Ej: NOMBRE, Nº SOCIO, etc.");
        txtEtiqueta.setMaxWidth(MAX_CONTROL_WIDTH);
        txtEtiqueta.textProperty().addListener((obs, old, newVal) -> {
            texto.setEtiqueta(newVal.isEmpty() ? null : newVal);
            // Forzar refresh de capas para mostrar nueva etiqueta
            if (onCanvasRedrawNeeded != null) {
                onCanvasRedrawNeeded.run();
            }
        });

        // Position and size
        Label lblPos = new Label("Posición y Tamaño");
        lblPos.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        TextField txtX = new TextField(String.format("%.0f", texto.getX()));
        TextField txtY = new TextField(String.format("%.0f", texto.getY()));
        TextField txtW = new TextField(String.format("%.0f", texto.getWidth()));
        TextField txtH = new TextField(String.format("%.0f", texto.getHeight()));

        txtX.setPromptText("X");
        txtY.setPromptText("Y");
        txtW.setPromptText("Ancho");
        txtH.setPromptText("Alto");

        txtX.setMaxWidth(MAX_CONTROL_WIDTH);
        txtY.setMaxWidth(MAX_CONTROL_WIDTH);
        txtW.setMaxWidth(MAX_CONTROL_WIDTH);
        txtH.setMaxWidth(MAX_CONTROL_WIDTH);

        // Listeners para posición y tamaño
        txtX.textProperty().addListener((obs, old, newVal) -> {
            try {
                texto.setX(Double.parseDouble(newVal));
                notifyCanvasRedraw();
            } catch (NumberFormatException ignored) {
            }
        });

        txtY.textProperty().addListener((obs, old, newVal) -> {
            try {
                texto.setY(Double.parseDouble(newVal));
                notifyCanvasRedraw();
            } catch (NumberFormatException ignored) {
            }
        });

        txtW.textProperty().addListener((obs, old, newVal) -> {
            try {
                texto.setWidth(Double.parseDouble(newVal));
                notifyCanvasRedraw();
            } catch (NumberFormatException ignored) {
            }
        });

        txtH.textProperty().addListener((obs, old, newVal) -> {
            try {
                texto.setHeight(Double.parseDouble(newVal));
                notifyCanvasRedraw();
            } catch (NumberFormatException ignored) {
            }
        });

        // Text content
        Label lblTexto = new Label("Texto");
        lblTexto.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        TextField txtContenido = new TextField(texto.getContenido());
        txtContenido.setPromptText("Contenido");
        txtContenido.setMaxWidth(MAX_CONTROL_WIDTH);
        txtContenido.textProperty().addListener((obs, old, newVal) -> {
            texto.setContenido(newVal);
            notifyCanvasRedraw();
        });

        // Font family
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

        // Font size
        Label lblTamaño = new Label("Tamaño:");
        lblTamaño.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

        Spinner<Integer> spnTamaño = new Spinner<>(8, 72, (int) texto.getFontSize());
        spnTamaño.setEditable(true);
        spnTamaño.setMaxWidth(MAX_CONTROL_WIDTH);
        spnTamaño.valueProperty().addListener((obs, old, newVal) -> {
            texto.setFontSize(newVal);
            notifyCanvasRedraw();
        });

        // Color
        Label lblColor = new Label("Color:");
        lblColor.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

        ColorPicker cpColor = new ColorPicker(Color.web(texto.getColor()));
        cpColor.setMaxWidth(MAX_CONTROL_WIDTH);
        cpColor.valueProperty().addListener((obs, old, newVal) -> {
            texto.setColor(String.format("#%02X%02X%02X",
                    (int) (newVal.getRed() * 255),
                    (int) (newVal.getGreen() * 255),
                    (int) (newVal.getBlue() * 255)));
            notifyCanvasRedraw();
        });

        // Alignment
        Label lblAlineacion = new Label("Alineación:");
        lblAlineacion.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

        ComboBox<String> cmbAlineacion = new ComboBox<>();
        cmbAlineacion.getItems().addAll("Izquierda", "Centro", "Derecha");
        String currentAlign = texto.getAlineacion();
        cmbAlineacion.setValue(
                "LEFT".equals(currentAlign) ? "Izquierda"
                        : "CENTER".equals(currentAlign) ? "Centro" : "Derecha");
        cmbAlineacion.setMaxWidth(MAX_CONTROL_WIDTH);
        cmbAlineacion.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                texto.setAlineacion(
                        "Izquierda".equals(newVal) ? "LEFT" : "Centro".equals(newVal) ? "CENTER" : "RIGHT");
                notifyCanvasRedraw();
            }
        });

        // Style (Bold/Italic)
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

        props.getChildren().addAll(lblProps, lblEtiqueta, txtEtiqueta, new Separator(),
                lblPos, txtX, txtY, txtW, txtH,
                new Separator(), lblTexto, txtContenido,
                lblFuente, cmbFuente, lblTamaño, spnTamaño,
                lblColor, cpColor, lblAlineacion, cmbAlineacion,
                lblEstilo, chkNegrita, chkCursiva);
    }

    /**
     * Construye el panel de propiedades para un elemento de imagen
     */
    private void buildImagePanel(VBox props, Label lblProps, ImagenElemento imagen) {
        // Etiqueta
        Label lblEtiqueta = new Label("Etiqueta (opcional):");
        lblEtiqueta.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        TextField txtEtiqueta = new TextField(imagen.getEtiqueta() != null ? imagen.getEtiqueta() : "");
        txtEtiqueta.setPromptText("Ej: FOTO CARNET, LOGO, etc.");
        txtEtiqueta.setMaxWidth(MAX_CONTROL_WIDTH);
        txtEtiqueta.textProperty().addListener((obs, old, newVal) -> {
            imagen.setEtiqueta(newVal.isEmpty() ? null : newVal);
            // Forzar refresh de capas para mostrar nueva etiqueta
            if (onCanvasRedrawNeeded != null) {
                onCanvasRedrawNeeded.run();
            }
        });

        // Position and size
        Label lblPos = new Label("Posición y Tamaño");
        lblPos.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        TextField txtX = new TextField(String.format("%.0f", imagen.getX()));
        TextField txtY = new TextField(String.format("%.0f", imagen.getY()));
        TextField txtW = new TextField(String.format("%.0f", imagen.getWidth()));
        TextField txtH = new TextField(String.format("%.0f", imagen.getHeight()));

        txtX.setPromptText("X");
        txtY.setPromptText("Y");
        txtW.setPromptText("Ancho");
        txtH.setPromptText("Alto");

        txtX.setMaxWidth(MAX_CONTROL_WIDTH);
        txtY.setMaxWidth(MAX_CONTROL_WIDTH);
        txtW.setMaxWidth(MAX_CONTROL_WIDTH);
        txtH.setMaxWidth(MAX_CONTROL_WIDTH);

        // Listeners para posición y tamaño
        txtX.textProperty().addListener((obs, old, newVal) -> {
            try {
                imagen.setX(Double.parseDouble(newVal));
                notifyCanvasRedraw();
            } catch (NumberFormatException ignored) {
            }
        });

        txtY.textProperty().addListener((obs, old, newVal) -> {
            try {
                imagen.setY(Double.parseDouble(newVal));
                notifyCanvasRedraw();
            } catch (NumberFormatException ignored) {
            }
        });

        txtW.textProperty().addListener((obs, old, newVal) -> {
            try {
                imagen.setWidth(Double.parseDouble(newVal));
                notifyCanvasRedraw();
            } catch (NumberFormatException ignored) {
            }
        });

        txtH.textProperty().addListener((obs, old, newVal) -> {
            try {
                imagen.setHeight(Double.parseDouble(newVal));
                notifyCanvasRedraw();
            } catch (NumberFormatException ignored) {
            }
        });

        // Image info
        Label lblImagen = new Label("Imagen");
        lblImagen.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblDimOrig = new Label(String.format("Original: %.0f × %.0f px",
                imagen.getOriginalWidth(), imagen.getOriginalHeight()));
        lblDimOrig.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");
        lblDimOrig.setMaxWidth(MAX_CONTROL_WIDTH);
        lblDimOrig.setWrapText(true);

        // Opacity
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

        // Maintain proportion
        CheckBox chkProporcion = new CheckBox("Mantener proporción");
        chkProporcion.setSelected(imagen.isMantenerProporcion());
        chkProporcion.setStyle("-fx-text-fill: #e8e6e7;");
        chkProporcion.setMaxWidth(MAX_CONTROL_WIDTH);
        chkProporcion.selectedProperty().addListener((obs, old, newVal) -> {
            imagen.setMantenerProporcion(newVal);
            notifyCanvasRedraw();
        });

        // Replace button
        Button btnReemplazar = new Button("Reemplazar Imagen");
        btnReemplazar.setMaxWidth(MAX_CONTROL_WIDTH);
        btnReemplazar.getStyleClass().add("toolbox-btn");
        btnReemplazar.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Reemplazar Imagen");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
            if (file != null) {
                try {
                    Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());
                    if (img != null) {
                        imagen.setImagen(img);
                        imagen.setRutaArchivo(file.getAbsolutePath());
                        // originalWidth and originalHeight are automatically updated by setImagen()
                        notifyCanvasRedraw();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        props.getChildren().addAll(lblProps, lblEtiqueta, txtEtiqueta, new Separator(),
                lblPos, txtX, txtY, txtW, txtH,
                new Separator(), lblImagen, lblDimOrig,
                lblOpacidad, sldOpacidad, chkProporcion, btnReemplazar);
    }

    /**
     * Notifica que una propiedad ha cambiado
     */
    private void notifyPropertyChanged() {
        if (onPropertyChanged != null) {
            onPropertyChanged.run();
        }
    }

    /**
     * Notifica que se necesita redibujar el canvas
     */
    private void notifyCanvasRedraw() {
        if (onCanvasRedrawNeeded != null) {
            onCanvasRedrawNeeded.run();
        }
    }
}
