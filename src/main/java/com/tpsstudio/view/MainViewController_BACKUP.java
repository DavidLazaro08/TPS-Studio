package com.tpsstudio.view;

import com.tpsstudio.model.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;

import java.io.File;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;

public class MainViewController_BACKUP {

    // ========== FXML Components ==========
    @FXML
    private VBox leftPanel;
    @FXML
    private VBox rightPanel;
    @FXML
    private Canvas canvas;
    @FXML
    private ListView<Proyecto> listProyectos;
    @FXML
    private Label lblZoom;
    @FXML
    private ToggleButton toggleFrenteDorso;
    @FXML
    private ToggleButton toggleGuias;
    @FXML
    private ToggleButton btnModeEdit;
    @FXML
    private ToggleButton btnModeExport;
    @FXML
    private ToggleButton togglePropiedades;

    // ========== State Variables ==========
    private AppMode currentMode = AppMode.PRODUCTION;
    private final ObservableList<Proyecto> proyectos = FXCollections.observableArrayList();
    private Proyecto proyectoActual;
    private Elemento elementoSeleccionado;
    private double zoomLevel = 1.3; // 130% zoom inicial

    // Drag & drop state
    private double dragStartX, dragStartY;
    private double elementStartX, elementStartY;
    private double elementStartW, elementStartH; // Para redimensionar

    // Enum para controlar el modo de arrastre
    private enum DragMode {
        NONE, MOVE, RESIZE_NW, RESIZE_NE, RESIZE_SW, RESIZE_SE, RESIZE_E // E para solo ancho (texto)
    }

    private DragMode currentDragMode = DragMode.NONE;
    private static final double HANDLE_SIZE = 8.0;

    // Flag para evitar bucles en listeners de texto
    private boolean isUpdatingTextFields = false;

    // CR80 dimensions (4px = 1mm)
    private static final double CR80_WIDTH_MM = 85.60;
    private static final double CR80_HEIGHT_MM = 53.98;
    private static final double SCALE = 4.0; // 4 píxeles = 1 mm
    private static final double CARD_WIDTH = CR80_WIDTH_MM * SCALE; // 342.4 px
    private static final double CARD_HEIGHT = CR80_HEIGHT_MM * SCALE; // 215.92 px

    // Bleed: 2mm por lado (estándar de preimpresión)
    private static final double BLEED_MM = 2.0;
    private static final double BLEED_MARGIN = BLEED_MM * SCALE; // 8 px

    // Safety margin: 2mm dentro del borde final
    private static final double SAFETY_MM = 2.0;
    private static final double SAFETY_MARGIN = SAFETY_MM * SCALE; // 8 px

    // Dimensiones totales con sangrado
    private static final double CARD_WITH_BLEED_WIDTH = CR80_WIDTH_MM + (BLEED_MM * 2); // 89.60 mm
    private static final double CARD_WITH_BLEED_HEIGHT = CR80_HEIGHT_MM + (BLEED_MM * 2); // 57.98 mm

    @FXML
    private void initialize() {
        setupCanvas();
        switchMode(AppMode.PRODUCTION); // Start in PRODUCTION mode

        // Initialize panel states and apply shadows
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.5));
        shadow.setRadius(15);
        shadow.setSpread(0.1);

        rightPanel.setEffect(shadow);

        // Ensure initial state matches toggle buttons
        togglePanel(rightPanel, togglePropiedades.isSelected(), false);
    }

    private void setupCanvas() {
        // Mouse events for selection and drag
        canvas.setOnMousePressed(this::onCanvasMousePressed);
        canvas.setOnMouseDragged(this::onCanvasMouseDragged);
        canvas.setOnMouseReleased(this::onCanvasMouseReleased);
        canvas.setOnMouseMoved(this::onCanvasMouseMoved); // Nuevo para cursores

        // Keyboard events
        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE && elementoSeleccionado != null) {
                onEliminarElemento();
            }
        });

        // Inicializar label de zoom con el valor correcto
        actualizarZoom();

        dibujarCanvas();
    }

    // ========== MODE SWITCHING ==========

    @FXML
    private void onModeEdit() {
        switchMode(AppMode.DESIGN);
    }

    @FXML
    private void onModeExport() {
        switchMode(AppMode.PRODUCTION);
    }

    private void switchMode(AppMode newMode) {
        currentMode = newMode;

        // Clear panels
        leftPanel.getChildren().clear();
        rightPanel.getChildren().clear();

        if (newMode == AppMode.DESIGN) {
            buildEditPanels();
        } else {
            buildExportPanels();
        }

        dibujarCanvas();
    }

    // ========== BUILD EDIT PANELS ==========

    private void buildEditPanels() {
        // CRITICAL: Clear panels first to prevent duplication
        leftPanel.getChildren().clear();
        rightPanel.getChildren().clear();

        // LEFT: Toolbox + Layers
        VBox toolbox = new VBox(8);
        toolbox.setPadding(new Insets(12));

        Label lblToolbox = new Label("Herramientas");
        lblToolbox.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        Button btnTexto = new Button("T Texto");
        btnTexto.setOnAction(e -> onAñadirTexto());
        btnTexto.setMaxWidth(Double.MAX_VALUE);
        btnTexto.getStyleClass().add("toolbox-btn");

        Button btnImagen = new Button("🖼 Imagen");
        btnImagen.setOnAction(e -> onAñadirImagen());
        btnImagen.setMaxWidth(Double.MAX_VALUE);
        btnImagen.getStyleClass().add("toolbox-btn");

        Button btnFondo = new Button("🎨 Fondo");
        btnFondo.setOnAction(e -> onAñadirFondo());
        btnFondo.setMaxWidth(Double.MAX_VALUE);
        btnFondo.getStyleClass().add("toolbox-btn");

        Button btnRectangulo = new Button("▭ Rectángulo");
        btnRectangulo.setMaxWidth(Double.MAX_VALUE);
        btnRectangulo.getStyleClass().add("toolbox-btn");
        btnRectangulo.setDisable(true); // Placeholder

        Button btnElipse = new Button("○ Elipse");
        btnElipse.setMaxWidth(Double.MAX_VALUE);
        btnElipse.getStyleClass().add("toolbox-btn");
        btnElipse.setDisable(true); // Placeholder

        toolbox.getChildren().addAll(lblToolbox, btnTexto, btnImagen, btnFondo, btnRectangulo, btnElipse);

        // Layers panel
        VBox layersPanel = new VBox(8);
        layersPanel.setPadding(new Insets(12, 12, 12, 12));

        Label lblCapas = new Label("Capas");
        lblCapas.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        ListView<Elemento> listCapas = new ListView<>();
        listCapas.getStyleClass().add("project-list");
        listCapas.setPrefHeight(200);

        if (proyectoActual != null) {
            // Combine background + normal elements
            ObservableList<Elemento> allElements = FXCollections.observableArrayList();
            ImagenFondoElemento fondo = proyectoActual.getFondoActual();
            if (fondo != null) {
                allElements.add(fondo);
            }
            allElements.addAll(proyectoActual.getElementosActuales());

            listCapas.setItems(allElements);

            // Custom cell factory to show lock icon for background
            listCapas.setCellFactory(lv -> new ListCell<Elemento>() {
                @Override
                protected void updateItem(Elemento item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setContextMenu(null);
                    } else {
                        String lockIcon = item.isLocked() ? "🔒 " : "";
                        setText(lockIcon + item.getNombre());

                        // Context menu for background element
                        if (item instanceof ImagenFondoElemento) {
                            ContextMenu contextMenu = new ContextMenu();

                            MenuItem menuEditar = new MenuItem("Editar imagen externa...");
                            menuEditar.setOnAction(e -> abrirEditorExterno((ImagenFondoElemento) item));

                            MenuItem menuRecargar = new MenuItem("Recargar desde disco");
                            menuRecargar.setOnAction(e -> recargarFondo((ImagenFondoElemento) item));

                            MenuItem menuLock = new MenuItem(item.isLocked() ? "Desbloquear" : "Bloquear");
                            menuLock.setOnAction(e -> {
                                item.setLocked(!item.isLocked());
                                buildEditPanels();
                                dibujarCanvas();
                            });

                            contextMenu.getItems().addAll(menuEditar, menuRecargar, new SeparatorMenuItem(), menuLock);
                            setContextMenu(contextMenu);
                        } else {
                            setContextMenu(null);
                        }
                    }
                }
            });

            listCapas.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                elementoSeleccionado = newVal;
                if (newVal != null) {
                    ensurePropertiesPanelVisible();
                }
                buildEditPanels(); // Refresh to update properties
                dibujarCanvas();
            });
        }

        layersPanel.getChildren().addAll(lblCapas, listCapas);

        leftPanel.getChildren().addAll(toolbox, new Separator(), layersPanel);

        // RIGHT: Properties
        buildPropertiesPanel();
    }

    private void buildPropertiesPanel() {
        VBox props = new VBox(8);
        props.setPadding(new Insets(30)); // Padding aumentado visualmente
        props.setFillWidth(true); // Los hijos ocupan todo el ancho (hasta el padding)
        props.setAlignment(javafx.geometry.Pos.TOP_LEFT); // CRÍTICO: Alineación izquierda

        // CRÍTICO: Limitar ancho máximo de controles individuales
        // Reducido a 200px para aumentar los márgenes visuales y evitar sensación de
        // "apretado"
        final double MAX_CONTROL_WIDTH = 200.0;

        Label lblProps = new Label("Propiedades");
        lblProps.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        if (elementoSeleccionado == null) {
            Label placeholder = new Label("Seleccione un elemento");
            placeholder.setStyle("-fx-text-fill: #6a6568; -fx-font-size: 12px; -fx-font-style: italic;");
            props.getChildren().addAll(lblProps, placeholder);
        } else if (elementoSeleccionado instanceof ImagenFondoElemento) {
            // Background-specific properties
            ImagenFondoElemento fondo = (ImagenFondoElemento) elementoSeleccionado;

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
                fondo.ajustarATamaño(CARD_WIDTH, CARD_HEIGHT, BLEED_MARGIN);
                buildEditPanels();
                dibujarCanvas();
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
                        Image img = new Image(file.toURI().toString());
                        fondo.setImagen(img);
                        fondo.setRutaArchivo(file.getAbsolutePath());
                        fondo.ajustarATamaño(CARD_WIDTH, CARD_HEIGHT, BLEED_MARGIN);
                        dibujarCanvas();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            Button btnEditarExterno = new Button("Editar Externa...");
            btnEditarExterno.setMaxWidth(MAX_CONTROL_WIDTH);
            btnEditarExterno.getStyleClass().add("toolbox-btn");
            btnEditarExterno.setOnAction(e -> abrirEditorExterno(fondo));

            Button btnRecargar = new Button("Recargar");
            btnRecargar.setMaxWidth(MAX_CONTROL_WIDTH);
            btnRecargar.getStyleClass().add("toolbox-btn");
            btnRecargar.setOnAction(e -> recargarFondo(fondo));

            props.getChildren().addAll(lblProps, lblInfo, lblDim,
                    new Separator(), lblModo, rbBleed, rbFinal, new Separator(),
                    btnReemplazar, btnEditarExterno, btnRecargar);
        } else {
            // Position and size
            Label lblPos = new Label("Posición y Tamaño");
            lblPos.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

            TextField txtX = new TextField(String.format("%.0f", elementoSeleccionado.getX()));
            TextField txtY = new TextField(String.format("%.0f", elementoSeleccionado.getY()));
            TextField txtW = new TextField(String.format("%.0f", elementoSeleccionado.getWidth()));
            TextField txtH = new TextField(String.format("%.0f", elementoSeleccionado.getHeight()));

            txtX.setPromptText("X");
            txtY.setPromptText("Y");
            txtW.setPromptText("Ancho");
            txtH.setPromptText("Alto");

            txtX.setMaxWidth(MAX_CONTROL_WIDTH);
            txtY.setMaxWidth(MAX_CONTROL_WIDTH);
            txtW.setMaxWidth(MAX_CONTROL_WIDTH);
            txtH.setMaxWidth(MAX_CONTROL_WIDTH);

            // Text-specific properties
            if (elementoSeleccionado instanceof TextoElemento) {
                TextoElemento texto = (TextoElemento) elementoSeleccionado;

                Label lblTexto = new Label("Texto");
                lblTexto.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

                TextField txtContenido = new TextField(texto.getContenido());
                txtContenido.setPromptText("Contenido");
                txtContenido.setMaxWidth(MAX_CONTROL_WIDTH);
                txtContenido.textProperty().addListener((obs, old, newVal) -> {
                    texto.setContenido(newVal);
                    dibujarCanvas();
                });

                // Fuente (usando fuentes del sistema)
                Label lblFuente = new Label("Fuente:");
                lblFuente.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

                ComboBox<String> cmbFuente = new ComboBox<>();
                cmbFuente.getItems().addAll(javafx.scene.text.Font.getFamilies());
                cmbFuente.setValue(texto.getFontFamily());
                cmbFuente.setMaxWidth(MAX_CONTROL_WIDTH);
                cmbFuente.valueProperty().addListener((obs, old, newVal) -> {
                    if (newVal != null) {
                        texto.setFontFamily(newVal);
                        dibujarCanvas();
                    }
                });

                // Tamaño
                Label lblTamaño = new Label("Tamaño:");
                lblTamaño.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

                Spinner<Integer> spnTamaño = new Spinner<>(8, 72, (int) texto.getFontSize());
                spnTamaño.setEditable(true);
                spnTamaño.setMaxWidth(MAX_CONTROL_WIDTH);
                spnTamaño.valueProperty().addListener((obs, old, newVal) -> {
                    texto.setFontSize(newVal);
                    dibujarCanvas();
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
                    dibujarCanvas();
                });

                // Alineación
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
                        dibujarCanvas();
                    }
                });

                // Estilo (Negrita/Cursiva)
                Label lblEstilo = new Label("Estilo:");
                lblEstilo.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");

                CheckBox chkNegrita = new CheckBox("Negrita");
                chkNegrita.setSelected(texto.isNegrita());
                chkNegrita.setStyle("-fx-text-fill: #e8e6e7;");
                chkNegrita.setMaxWidth(MAX_CONTROL_WIDTH);
                chkNegrita.selectedProperty().addListener((obs, old, newVal) -> {
                    texto.setNegrita(newVal);
                    dibujarCanvas();
                });

                CheckBox chkCursiva = new CheckBox("Cursiva");
                chkCursiva.setSelected(texto.isCursiva());
                chkCursiva.setStyle("-fx-text-fill: #e8e6e7;");
                chkCursiva.setMaxWidth(MAX_CONTROL_WIDTH);
                chkCursiva.selectedProperty().addListener((obs, old, newVal) -> {
                    texto.setCursiva(newVal);
                    dibujarCanvas();
                });

                props.getChildren().addAll(lblProps, lblPos, txtX, txtY, txtW, txtH,
                        new Separator(), lblTexto, txtContenido,
                        lblFuente, cmbFuente, lblTamaño, spnTamaño,
                        lblColor, cpColor, lblAlineacion, cmbAlineacion,
                        lblEstilo, chkNegrita, chkCursiva);
            } else if (elementoSeleccionado instanceof ImagenElemento) {
                // Image-specific properties
                ImagenElemento imagen = (ImagenElemento) elementoSeleccionado;

                Label lblImagen = new Label("Imagen");
                lblImagen.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

                Label lblDimOrig = new Label(String.format("Original: %.0f × %.0f px",
                        imagen.getOriginalWidth(), imagen.getOriginalHeight()));
                lblDimOrig.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 11px;");
                lblDimOrig.setMaxWidth(MAX_CONTROL_WIDTH);
                lblDimOrig.setWrapText(true);

                // Opacidad
                Label lblOpacidad = new Label("Opacidad:");
                lblOpacidad.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

                Slider sldOpacidad = new Slider(0, 100, imagen.getOpacity() * 100);
                sldOpacidad.setShowTickLabels(true);
                sldOpacidad.setShowTickMarks(true);
                sldOpacidad.setMajorTickUnit(25);
                sldOpacidad.setMaxWidth(MAX_CONTROL_WIDTH);
                sldOpacidad.valueProperty().addListener((obs, old, newVal) -> {
                    imagen.setOpacity(newVal.doubleValue() / 100.0);
                    dibujarCanvas();
                });

                // Mantener proporción
                CheckBox chkProporcion = new CheckBox("Mantener proporción");
                chkProporcion.setSelected(imagen.isMantenerProporcion());
                chkProporcion.setStyle("-fx-text-fill: #e8e6e7;");
                chkProporcion.setMaxWidth(MAX_CONTROL_WIDTH);
                chkProporcion.selectedProperty().addListener((obs, old, newVal) -> {
                    imagen.setMantenerProporcion(newVal);
                });

                // Botón reemplazar
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
                            Image img = new Image(file.toURI().toString());
                            imagen.setImagen(img);
                            imagen.setRutaArchivo(file.getAbsolutePath());
                            // OriginalW/H updated inside setImagen automatically? No, setImagen usually
                            // updates it but ImagenElemento.setImagen DOES update them.
                            // I checked ImagenElemento.java, setImagen DOES update originalWidth/Height.
                            buildPropertiesPanel();
                            dibujarCanvas();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                });

                props.getChildren().addAll(lblProps, lblPos, txtX, txtY, txtW, txtH,
                        new Separator(), lblImagen, lblDimOrig,
                        lblOpacidad, sldOpacidad, chkProporcion, btnReemplazar);
            } else {
                props.getChildren().addAll(lblProps, lblPos, txtX, txtY, txtW, txtH);
            }

            // Listeners for position/size
            txtX.textProperty().addListener((obs, old, newVal) -> {
                if (isUpdatingTextFields)
                    return;
                try {
                    elementoSeleccionado.setX(Double.parseDouble(newVal));
                    dibujarCanvas();
                } catch (NumberFormatException ignored) {
                }
            });
            txtY.textProperty().addListener((obs, old, newVal) -> {
                if (isUpdatingTextFields)
                    return;
                try {
                    elementoSeleccionado.setY(Double.parseDouble(newVal));
                    dibujarCanvas();
                } catch (NumberFormatException ignored) {
                }
            });

            // Logic for proportional updates
            txtW.textProperty().addListener((obs, old, newVal) -> {
                if (isUpdatingTextFields)
                    return;
                try {
                    double newWidth = Double.parseDouble(newVal);
                    elementoSeleccionado.setWidth(newWidth);

                    if (elementoSeleccionado instanceof ImagenElemento
                            && ((ImagenElemento) elementoSeleccionado).isMantenerProporcion()) {
                        isUpdatingTextFields = true;
                        double ratio = ((ImagenElemento) elementoSeleccionado).getOriginalWidth()
                                / ((ImagenElemento) elementoSeleccionado).getOriginalHeight();
                        // Fallback/Safety in case ratio is valid
                        if (ratio > 0) {
                            double newHeight = newWidth / ratio;
                            elementoSeleccionado.setHeight(newHeight);
                            txtH.setText(String.format("%.0f", newHeight));
                        }
                        isUpdatingTextFields = false;
                    }
                    dibujarCanvas();
                } catch (NumberFormatException ignored) {
                }
            });

            txtH.textProperty().addListener((obs, old, newVal) -> {
                if (isUpdatingTextFields)
                    return;
                try {
                    double newHeight = Double.parseDouble(newVal);
                    elementoSeleccionado.setHeight(newHeight);

                    if (elementoSeleccionado instanceof ImagenElemento
                            && ((ImagenElemento) elementoSeleccionado).isMantenerProporcion()) {
                        isUpdatingTextFields = true;
                        double ratio = ((ImagenElemento) elementoSeleccionado).getOriginalWidth()
                                / ((ImagenElemento) elementoSeleccionado).getOriginalHeight();
                        // Fallback/Safety
                        if (ratio > 0) {
                            double newWidth = newHeight * ratio;
                            elementoSeleccionado.setWidth(newWidth);
                            txtW.setText(String.format("%.0f", newWidth));
                        }
                        isUpdatingTextFields = false;
                    }
                    dibujarCanvas();
                } catch (NumberFormatException ignored) {
                }
            });
        }

        // CRÍTICO: Configuración defensiva del ScrollPane
        ScrollPane scrollPane = new ScrollPane(props);
        scrollPane.setFitToWidth(true); // Content fills viewport width
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #141012; -fx-background-color: #141012; -fx-padding: 0;");
        scrollPane.setPadding(javafx.geometry.Insets.EMPTY); // Remove all padding

        rightPanel.getChildren().clear();
        rightPanel.getChildren().add(scrollPane);
        rightPanel.setAlignment(javafx.geometry.Pos.TOP_LEFT); // Ensure container aligns left
        rightPanel.setPadding(javafx.geometry.Insets.EMPTY);
    }

    // ========== BUILD EXPORT PANELS ==========

    private void buildExportPanels() {
        // CRITICAL: Clear panels first to prevent duplication
        leftPanel.getChildren().clear();
        rightPanel.getChildren().clear();

        // LEFT: Project list (reuse from original)
        VBox projectPanel = new VBox(8);
        projectPanel.setPadding(new Insets(12));

        Label lblTrabajos = new Label("Trabajos");
        lblTrabajos.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        listProyectos = new ListView<>();
        listProyectos.setItems(proyectos);
        listProyectos.getStyleClass().add("project-list");
        listProyectos.setPrefHeight(400);
        listProyectos.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                proyectoActual = newVal;
                dibujarCanvas();
            }
        });

        Button btnNuevoCR80 = new Button("+ Nuevo CR80");
        btnNuevoCR80.setOnAction(e -> onNuevoCR80());
        btnNuevoCR80.getStyleClass().add("primary-btn");
        btnNuevoCR80.setMaxWidth(Double.MAX_VALUE);

        projectPanel.getChildren().addAll(lblTrabajos, listProyectos, btnNuevoCR80);
        leftPanel.getChildren().add(projectPanel);

        // RIGHT: Export controls
        VBox exportPanel = new VBox(15);
        exportPanel.setPadding(new Insets(30)); // Padding aumentado (antes 12)
        exportPanel.setFillWidth(true); // Permitir llenar hasta el padding

        Label lblExport = new Label("Exportación");
        lblExport.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label lblInfoExp = new Label("Formato: PNG/PDF (placeholder)");
        lblInfoExp.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblDpi = new Label("DPI: 300 (placeholder)");
        lblDpi.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblGuias = new Label("Incluir guías: No (placeholder)");
        lblGuias.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblSide = new Label("Exportar: Frente (placeholder)");
        lblSide.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Button btnDoExport = new Button("Exportar");
        btnDoExport.getStyleClass().add("success-btn"); // Estilo verde/destacado
        // Aplicar el mismo límite de ancho que en propiedades para consistencia visual
        btnDoExport.setMaxWidth(200.0);
        btnDoExport.setOnAction(e -> onExportarProyecto());

        exportPanel.getChildren().addAll(lblExport, lblInfoExp, lblDpi, lblGuias, lblSide, new Separator(),
                btnDoExport);

        // Envolver en ScrollPane para consistencia
        // Establecer el mismo color de fondo para el VBox que para el ScrollPane
        // para evitar el efecto de "dos tonos" si el contenido es corto
        exportPanel.setStyle("-fx-background-color: #1e1b1c;");

        // Envolver en ScrollPane para consistencia
        ScrollPane scrollPane = new ScrollPane(exportPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #1e1b1c; -fx-background-color: #1e1b1c; -fx-padding: 0;");
        scrollPane.setPadding(javafx.geometry.Insets.EMPTY);

        rightPanel.getChildren().add(scrollPane);
    }

    // ========== CANVAS DRAWING ==========

    private void dibujarCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (proyectoActual == null) {
            gc.setFill(Color.web("#9a9598"));
            gc.fillText("Seleccione un proyecto o cree uno nuevo",
                    canvas.getWidth() / 2 - 120, canvas.getHeight() / 2);
            return;
        }

        double scaledWidth = CARD_WIDTH * zoomLevel;
        double scaledHeight = CARD_HEIGHT * zoomLevel;
        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        double cardX = centerX - (scaledWidth / 2);
        double cardY = centerY - (scaledHeight / 2);

        gc.save();

        // 1. Bleed zone (sangrado: 2mm por lado)
        if (toggleGuias.isSelected()) {
            double bleedScaled = BLEED_MARGIN * zoomLevel;
            gc.setStroke(Color.web("#d48a8a")); // Rojo claro
            gc.setLineWidth(1);
            gc.setLineDashes(5, 5);
            gc.strokeRect(cardX - bleedScaled, cardY - bleedScaled,
                    scaledWidth + (bleedScaled * 2), scaledHeight + (bleedScaled * 2));
        }

        // 2. Background (if exists)
        ImagenFondoElemento fondo = proyectoActual.getFondoActual();
        if (fondo != null && fondo.getImagen() != null) {
            double fx = cardX + (fondo.getX() * zoomLevel);
            double fy = cardY + (fondo.getY() * zoomLevel);
            double fw = fondo.getWidth() * zoomLevel;
            double fh = fondo.getHeight() * zoomLevel;
            gc.drawImage(fondo.getImagen(), fx, fy, fw, fh);
        } else {
            // Card (white background only if no background image)
            gc.setFill(Color.WHITE);
            gc.fillRect(cardX, cardY, scaledWidth, scaledHeight);
        }

        // 3. Card border (CR80 final)
        gc.setStroke(Color.web("#c4c0c2"));
        gc.setLineWidth(1);
        gc.setLineDashes();
        gc.strokeRect(cardX, cardY, scaledWidth, scaledHeight);

        // 4. Safety guides (margen de seguridad: 2mm dentro del borde)
        if (toggleGuias.isSelected()) {
            double safetyScaled = SAFETY_MARGIN * zoomLevel;
            gc.setStroke(Color.web("#4a9b7c")); // Verde azulado
            gc.setLineDashes(3, 3);
            gc.strokeRect(cardX + safetyScaled, cardY + safetyScaled,
                    scaledWidth - (safetyScaled * 2), scaledHeight - (safetyScaled * 2));
        }

        // Draw elements
        for (Elemento elem : proyectoActual.getElementosActuales()) {
            if (!elem.isVisible())
                continue;

            double ex = cardX + (elem.getX() * zoomLevel);
            double ey = cardY + (elem.getY() * zoomLevel);
            double ew = elem.getWidth() * zoomLevel;
            double eh = elem.getHeight() * zoomLevel;

            if (elem instanceof TextoElemento) {
                TextoElemento texto = (TextoElemento) elem;
                gc.setFill(Color.web(texto.getColor()));

                // Aplicar estilo de fuente (negrita/cursiva)
                javafx.scene.text.FontWeight weight = texto.isNegrita() ? javafx.scene.text.FontWeight.BOLD
                        : javafx.scene.text.FontWeight.NORMAL;
                javafx.scene.text.FontPosture posture = texto.isCursiva() ? javafx.scene.text.FontPosture.ITALIC
                        : javafx.scene.text.FontPosture.REGULAR;

                gc.setFont(Font.font(texto.getFontFamily(), weight, posture, texto.getFontSize() * zoomLevel));

                // Aplicar alineación
                double textX = ex;
                javafx.scene.text.Text tempText = new javafx.scene.text.Text(texto.getContenido());
                tempText.setFont(gc.getFont());
                double textWidth = tempText.getLayoutBounds().getWidth();

                if (texto.getAlineacion().equals("CENTER")) {
                    textX = ex + (ew - textWidth) / 2;
                } else if (texto.getAlineacion().equals("RIGHT")) {
                    textX = ex + ew - textWidth;
                }

                gc.fillText(texto.getContenido(), textX, ey + (texto.getFontSize() * zoomLevel));
            } else if (elem instanceof ImagenElemento) {
                ImagenElemento img = (ImagenElemento) elem;
                if (img.getImagen() != null) {
                    // Aplicar opacidad
                    gc.setGlobalAlpha(img.getOpacity());
                    gc.drawImage(img.getImagen(), ex, ey, ew, eh);
                    gc.setGlobalAlpha(1.0); // Restaurar opacidad
                }
            }

            // Selection box
            if (elem == elementoSeleccionado) {
                gc.setStroke(Color.web("#4a6b7c"));
                gc.setLineWidth(2);
                gc.setLineDashes();
                gc.strokeRect(ex - 2, ey - 2, ew + 4, eh + 4);

                // Dibujar Handles (Manijas) de redimensionamiento
                gc.setFill(Color.WHITE);
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(1);

                double dim = HANDLE_SIZE;

                if (elem instanceof TextoElemento) {
                    // Texto: Solo ancho -> Handle a la derecha (Middle-East)
                    gc.fillRect(ex + ew - (dim / 2), ey + (eh / 2) - (dim / 2), dim, dim);
                    gc.strokeRect(ex + ew - (dim / 2), ey + (eh / 2) - (dim / 2), dim, dim);

                } else {
                    // Imagen: 4 esquinas
                    // NW
                    gc.fillRect(ex - (dim / 2), ey - (dim / 2), dim, dim);
                    gc.strokeRect(ex - (dim / 2), ey - (dim / 2), dim, dim);
                    // NE
                    gc.fillRect(ex + ew - (dim / 2), ey - (dim / 2), dim, dim);
                    gc.strokeRect(ex + ew - (dim / 2), ey - (dim / 2), dim, dim);
                    // SW
                    gc.fillRect(ex - (dim / 2), ey + eh - (dim / 2), dim, dim);
                    gc.strokeRect(ex - (dim / 2), ey + eh - (dim / 2), dim, dim);
                    // SE
                    gc.fillRect(ex + ew - (dim / 2), ey + eh - (dim / 2), dim, dim);
                    gc.strokeRect(ex + ew - (dim / 2), ey + eh - (dim / 2), dim, dim);
                }
            }
        }

        // Info text - reorganizado para mejor legibilidad
        gc.setLineDashes();

        // 1. Lado (FRENTE/DORSO) - Arriba izquierda, en negrita, más separado
        String lado = proyectoActual.isMostrandoFrente() ? "FRENTE" : "DORSO";
        gc.setFill(Color.web("#e8e6e7")); // Más claro y visible
        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 14));
        gc.fillText(lado, cardX, cardY - 25); // Más separado (antes -15)

        // 2. Dimensiones - Abajo derecha, texto normal, más abajo para no cortarse
        gc.setFill(Color.web("#9a9598"));
        gc.setFont(Font.font("Arial", 11));
        String infoDimensiones = String.format("CR80: %.2f × %.2f mm | Con sangre: %.2f × %.2f mm",
                CR80_WIDTH_MM, CR80_HEIGHT_MM, CARD_WITH_BLEED_WIDTH, CARD_WITH_BLEED_HEIGHT);
        // Calcular posición con sangrado incluido para evitar solapamiento
        double bleedScaled = BLEED_MARGIN * zoomLevel;
        gc.fillText(infoDimensiones, cardX + scaledWidth - 380, cardY + scaledHeight + bleedScaled + 20);

        gc.restore();
    }

    // ========== MOUSE EVENTS ==========

    private void onCanvasMousePressed(MouseEvent e) {
        if (proyectoActual == null || currentMode != AppMode.DESIGN)
            return;

        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        double scaledWidth = CARD_WIDTH * zoomLevel;
        double scaledHeight = CARD_HEIGHT * zoomLevel;
        double cardX = centerX - (scaledWidth / 2);
        double cardY = centerY - (scaledHeight / 2);

        // Ajustar coordenadas del mouse relativas al canvas/zoom
        // Pero para detección de handles usamos coord de pantalla (canvas) si es
        // posible
        // O mejor, calculamos la pos de los handles y checkeamos distancias.

        // Primero, chequear si clicamos en un handle del elemento seleccionado actual
        if (elementoSeleccionado != null) {
            DragMode mode = getDragModeForMouseEvent(e, elementoSeleccionado, cardX, cardY);
            if (mode != DragMode.NONE) {
                currentDragMode = mode;
                dragStartX = e.getX(); // Screen coords
                dragStartY = e.getY();
                elementStartX = elementoSeleccionado.getX();
                elementStartY = elementoSeleccionado.getY();
                elementStartW = elementoSeleccionado.getWidth();
                elementStartH = elementoSeleccionado.getHeight();
                canvas.requestFocus();
                return; // Consumido por resize
            }
        }

        // Si no es resize, buscar selección
        double relX = (e.getX() - cardX) / zoomLevel;
        double relY = (e.getY() - cardY) / zoomLevel;

        Elemento nuevoSeleccionado = null;
        for (int i = proyectoActual.getElementosActuales().size() - 1; i >= 0; i--) {
            Elemento elem = proyectoActual.getElementosActuales().get(i);
            if (elem.contains(relX, relY)) {
                nuevoSeleccionado = elem;
                break;
            }
        }

        if (nuevoSeleccionado != null) {
            elementoSeleccionado = nuevoSeleccionado;
            // Auto-abrir propiedades si está cerrado
            ensurePropertiesPanelVisible();

            currentDragMode = DragMode.MOVE;
            dragStartX = e.getX();
            dragStartY = e.getY();
            elementStartX = elementoSeleccionado.getX();
            elementStartY = elementoSeleccionado.getY();
            buildEditPanels();
            dibujarCanvas();
        } else {
            // Deseleccionar si clicamos fuera
            if (elementoSeleccionado != null) {
                elementoSeleccionado = null;
                buildEditPanels();
                dibujarCanvas();
            }
            currentDragMode = DragMode.NONE;
        }

        canvas.requestFocus();
    }

    private void onCanvasMouseDragged(MouseEvent e) {
        if (elementoSeleccionado == null || currentDragMode == DragMode.NONE || elementoSeleccionado.isLocked())
            return;

        double dx = (e.getX() - dragStartX) / zoomLevel;
        double dy = (e.getY() - dragStartY) / zoomLevel;

        if (currentDragMode == DragMode.MOVE) {
            elementoSeleccionado.setX(elementStartX + dx);
            elementoSeleccionado.setY(elementStartY + dy);
        } else {
            // Lógica de redimensionamiento
            double newW = elementStartW;
            double newH = elementStartH;
            double newX = elementStartX;
            double newY = elementStartY;

            // Variables para cálculo proporcional
            boolean keepProportion = false;
            double ratio = 1.0;
            if (elementoSeleccionado instanceof ImagenElemento
                    && ((ImagenElemento) elementoSeleccionado).isMantenerProporcion()) {
                keepProportion = true;
                if (elementStartH > 0)
                    ratio = elementStartW / elementStartH;
            }

            // Ajustar según el handle
            // Nota: dx/dy ya están ajustados por el zoom level

            switch (currentDragMode) {
                case RESIZE_E: // Solo ancho (Texto)
                    newW = elementStartW + dx;
                    break;

                case RESIZE_SE:
                    newW = elementStartW + dx;
                    newH = elementStartH + dy;
                    if (keepProportion) {
                        // Usamos el ancho como driver principal (comportamiento estándar)
                        // o mejor, el que tenga mayor desplazamiento relativo?
                        // Por simplicidad: newH = newW / ratio
                        newH = newW / ratio;
                    }
                    break;

                case RESIZE_SW:
                    newW = elementStartW - dx; // Arrastrar a izquierda (dx neg) aumenta ancho
                    newH = elementStartH + dy;
                    if (keepProportion) {
                        newH = newW / ratio;
                    }
                    // Al crecer a la izquierda, X debe moverse
                    newX = elementStartX + (elementStartW - newW);
                    break;

                case RESIZE_NE:
                    newW = elementStartW + dx;
                    newH = elementStartH - dy; // Arrastrar arriba (dy neg) aumenta alto
                    if (keepProportion) {
                        // En este caso, si arrastro arriba, espero que cambie alto.
                        // Pero si arrastro derecha, cambia ancho.
                        // Usemos Width como driver consistente
                        newH = newW / ratio;
                    }
                    // Al crecer arriba, Y debe moverse
                    newY = elementStartY + (elementStartH - newH);
                    break;

                case RESIZE_NW:
                    newW = elementStartW - dx;
                    newH = elementStartH - dy;
                    if (keepProportion) {
                        newH = newW / ratio;
                    }
                    newX = elementStartX + (elementStartW - newW);
                    newY = elementStartY + (elementStartH - newH);
                    break;

                default:
                    break;
            }

            // Aplicar restricciones mínimas (ej. 10px)
            if (newW < 10) {
                newW = 10;
                // Si limitamos W, recalcular X si es un handle izquierdo
                if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) {
                    newX = elementStartX + (elementStartW - newW);
                }
                // Si prop, recalcular H
                if (keepProportion)
                    newH = newW / ratio;
            }
            if (newH < 10) {
                newH = 10;
                // Si limitamos H y es prop, recalcular W (y X si aplica)
                if (keepProportion) {
                    newW = newH * ratio;
                    if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) {
                        newX = elementStartX + (elementStartW - newW);
                    }
                }
                // Si limitamos H, recalcular Y si es un handle superior
                if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_NE) {
                    newY = elementStartY + (elementStartH - newH);
                }
            }

            elementoSeleccionado.setWidth(newW);
            elementoSeleccionado.setHeight(newH);

            // Actualizar posición solo si es necesario (handles izquierdos/superiores)
            if (currentDragMode == DragMode.RESIZE_SW ||
                    currentDragMode == DragMode.RESIZE_NW ||
                    currentDragMode == DragMode.RESIZE_NE) {

                // Solo si el handle implica movimiento de origen
                if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW)
                    elementoSeleccionado.setX(newX);
                if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_NE)
                    elementoSeleccionado.setY(newY);
            }

            // Re-render en tiempo real
            buildEditPanels(); // Actualizar valores en panel
            dibujarCanvas();
        }

        dibujarCanvas();
    }

    private void onCanvasMouseMoved(MouseEvent e) {
        if (proyectoActual == null || elementoSeleccionado == null) {
            canvas.setCursor(javafx.scene.Cursor.DEFAULT);
            return;
        }

        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        double scaledWidth = CARD_WIDTH * zoomLevel;
        double scaledHeight = CARD_HEIGHT * zoomLevel;
        double cardX = centerX - (scaledWidth / 2);
        double cardY = centerY - (scaledHeight / 2);

        DragMode mode = getDragModeForMouseEvent(e, elementoSeleccionado, cardX, cardY);

        switch (mode) {
            case RESIZE_NW:
                canvas.setCursor(javafx.scene.Cursor.NW_RESIZE);
                break;
            case RESIZE_NE:
                canvas.setCursor(javafx.scene.Cursor.NE_RESIZE);
                break;
            case RESIZE_SW:
                canvas.setCursor(javafx.scene.Cursor.SW_RESIZE);
                break;
            case RESIZE_SE:
                canvas.setCursor(javafx.scene.Cursor.SE_RESIZE);
                break;
            case RESIZE_E:
                canvas.setCursor(javafx.scene.Cursor.E_RESIZE);
                break;
            default:
                canvas.setCursor(javafx.scene.Cursor.DEFAULT);
                break;
        }
    }

    private DragMode getDragModeForMouseEvent(MouseEvent e, Elemento elem, double cardX, double cardY) {
        double mx = e.getX();
        double my = e.getY();

        double ex = cardX + (elem.getX() * zoomLevel);
        double ey = cardY + (elem.getY() * zoomLevel);
        double ew = elem.getWidth() * zoomLevel;
        double eh = elem.getHeight() * zoomLevel;

        // Hitbox un poco más grande para facilitar clic
        double hit = HANDLE_SIZE + 4;

        if (elem instanceof TextoElemento) {
            // Solo handle derecho/centro
            double hx = ex + ew;
            double hy = ey + (eh / 2);
            if (Math.abs(mx - hx) <= hit && Math.abs(my - hy) <= hit) {
                return DragMode.RESIZE_E;
            }
        } else {
            // Esquinas
            // NW
            if (Math.abs(mx - ex) <= hit && Math.abs(my - ey) <= hit)
                return DragMode.RESIZE_NW;
            // NE
            if (Math.abs(mx - (ex + ew)) <= hit && Math.abs(my - ey) <= hit)
                return DragMode.RESIZE_NE;
            // SW
            if (Math.abs(mx - ex) <= hit && Math.abs(my - (ey + eh)) <= hit)
                return DragMode.RESIZE_SW;
            // SE
            if (Math.abs(mx - (ex + ew)) <= hit && Math.abs(my - (ey + eh)) <= hit)
                return DragMode.RESIZE_SE;
        }
        return DragMode.NONE;
    }

    private void ensurePropertiesPanelVisible() {
        if (togglePropiedades != null && !togglePropiedades.isSelected()) {
            togglePropiedades.setSelected(true);
            onTogglePropiedades();
        }
    }

    private void onCanvasMouseReleased(MouseEvent e) {
        // Nothing special for now
    }

    /**
     * Muestra un diálogo para seleccionar el modo de ajuste del fondo
     */
    private FondoFitMode mostrarDialogoFitMode() {
        Dialog<FondoFitMode> dialog = new Dialog<>();
        dialog.setTitle("Modo de Ajuste del Fondo");
        dialog.setHeaderText("¿Cómo desea ajustar el fondo a la tarjeta?");

        // Botones
        ButtonType btnBleed = new ButtonType("Con sangre", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnFinal = new ButtonType("Sin sangre", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnBleed, btnFinal, btnCancelar);

        // Contenido
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label lblExplicacion = new Label(
                "El fondo puede ajustarse de dos formas:");
        lblExplicacion.setStyle("-fx-font-size: 13px;");

        VBox opcionBleed = new VBox(5);
        Label lblBleedTitulo = new Label("✓ Con sangre (CR80 + 2mm por lado)");
        lblBleedTitulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label lblBleedDesc = new Label("Cubre el área completa incluyendo 2mm de sangrado por lado (89.60 × 57.98 mm)");
        lblBleedDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        Label lblBleedUso = new Label("Recomendado para fondos que se extienden hasta el borde");
        lblBleedUso.setStyle("-fx-font-size: 11px; -fx-font-style: italic;");
        opcionBleed.getChildren().addAll(lblBleedTitulo, lblBleedDesc, lblBleedUso);

        VBox opcionFinal = new VBox(5);
        Label lblFinalTitulo = new Label("✓ Sin sangre (CR80 final)");
        lblFinalTitulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label lblFinalDesc = new Label("Cubre solo el área final de la tarjeta (85.60 × 53.98 mm)");
        lblFinalDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        Label lblFinalUso = new Label("Útil para fondos que no deben llegar al borde");
        lblFinalUso.setStyle("-fx-font-size: 11px; -fx-font-style: italic;");
        opcionFinal.getChildren().addAll(lblFinalTitulo, lblFinalDesc, lblFinalUso);

        CheckBox chkNoPreguntar = new CheckBox("No volver a preguntar en este proyecto");
        chkNoPreguntar.setStyle("-fx-font-size: 11px;");

        content.getChildren().addAll(lblExplicacion, new Separator(),
                opcionBleed, new Separator(), opcionFinal, new Separator(), chkNoPreguntar);
        dialog.getDialogPane().setContent(content);

        // Convertir resultado
        dialog.setResultConverter(buttonType -> {
            if (chkNoPreguntar.isSelected() && proyectoActual != null) {
                proyectoActual.setNoVolverAPreguntarFondo(true);
                if (buttonType == btnBleed) {
                    proyectoActual.setFondoFitModePreferido(FondoFitMode.BLEED);
                } else if (buttonType == btnFinal) {
                    proyectoActual.setFondoFitModePreferido(FondoFitMode.FINAL);
                }
            }

            if (buttonType == btnBleed) {
                return FondoFitMode.BLEED;
            } else if (buttonType == btnFinal) {
                return FondoFitMode.FINAL;
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    /**
     * Abre la imagen del fondo en el editor externo del sistema
     */
    private void abrirEditorExterno(ImagenFondoElemento fondo) {
        if (fondo == null || fondo.getRutaArchivo() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Advertencia");
            alert.setHeaderText("No se puede abrir el editor externo");
            alert.setContentText("El fondo no tiene una ruta de archivo asociada.");
            alert.showAndWait();
            return;
        }

        File file = new File(fondo.getRutaArchivo());
        if (!file.exists()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Archivo no encontrado");
            alert.setContentText("El archivo " + file.getName() + " no existe en el disco.");
            alert.showAndWait();
            return;
        }

        try {
            // Mostrar aviso
            Alert aviso = new Alert(Alert.AlertType.INFORMATION);
            aviso.setTitle("Editor Externo");
            aviso.setHeaderText("Abriendo editor externo...");
            aviso.setContentText(
                    "Se abrirá la imagen en el editor predeterminado del sistema.\n" +
                            "Después de editar, guarde los cambios y use 'Recargar' para aplicarlos.");
            aviso.show();

            // Abrir con la aplicación predeterminada
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            desktop.open(file);

        } catch (Exception ex) {
            ex.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No se pudo abrir el editor externo");
            alert.setContentText("Error: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Recarga la imagen del fondo desde el disco
     */
    private void recargarFondo(ImagenFondoElemento fondo) {
        if (fondo == null || fondo.getRutaArchivo() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Advertencia");
            alert.setHeaderText("No se puede recargar");
            alert.setContentText("El fondo no tiene una ruta de archivo asociada.");
            alert.showAndWait();
            return;
        }

        File file = new File(fondo.getRutaArchivo());
        if (!file.exists()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Archivo no encontrado");
            alert.setContentText(
                    "El archivo " + file.getName() + " no existe en el disco.\n" +
                            "Se mantendrá la versión anterior en memoria.");
            alert.showAndWait();
            return;
        }

        try {
            // Recargar imagen
            Image nuevaImagen = new Image(file.toURI().toString());
            fondo.setImagen(nuevaImagen);
            fondo.ajustarATamaño(CARD_WIDTH, CARD_HEIGHT, BLEED_MARGIN);

            // Redibujar
            dibujarCanvas();

            // Confirmación
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Éxito");
            alert.setHeaderText("Fondo recargado");
            alert.setContentText("La imagen se ha recargado correctamente desde el disco.");
            alert.showAndWait();

        } catch (Exception ex) {
            ex.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No se pudo recargar la imagen");
            alert.setContentText(
                    "Error al cargar el archivo: " + ex.getMessage() + "\n" +
                            "Se mantendrá la versión anterior en memoria.");
            alert.showAndWait();
        }
    }

    // ========== TOOLBAR ACTIONS ==========

    @FXML
    private void onNuevoProyecto() {
        System.out.println("Nuevo (placeholder)");
    }

    @FXML
    private void onAbrirProyecto() {
        System.out.println("Abrir (placeholder)");
    }

    @FXML
    private void onGuardarProyecto() {
        System.out.println("Guardar (placeholder)");
    }

    @FXML
    private void onExportarProyecto() {
        System.out.println("Exportar (placeholder)");
    }

    @FXML
    private void onZoomIn() {
        if (zoomLevel < 2.0) {
            zoomLevel += 0.1;
            actualizarZoom();
        }
    }

    @FXML
    private void onZoomOut() {
        if (zoomLevel > 0.5) {
            zoomLevel -= 0.1;
            actualizarZoom();
        }
    }

    private void actualizarZoom() {
        lblZoom.setText(String.format("%.0f%%", zoomLevel * 100));
        dibujarCanvas();
    }

    @FXML
    private void onToggleFrenteDorso() {
        if (proyectoActual != null) {
            proyectoActual.setMostrandoFrente(toggleFrenteDorso.isSelected());
            toggleFrenteDorso.setText(toggleFrenteDorso.isSelected() ? "Frente" : "Dorso");
            elementoSeleccionado = null;
            if (currentMode == AppMode.DESIGN) {
                buildEditPanels();
            }
            dibujarCanvas();
        }
    }

    @FXML
    private void onToggleGuias() {
        dibujarCanvas();
    }

    @FXML
    private void onNuevoCR80() {
        int numero = proyectos.size() + 1;
        Proyecto nuevoProyecto = new Proyecto("Tarjeta CR80 #" + numero);
        proyectos.add(nuevoProyecto);
        if (listProyectos != null) {
            listProyectos.getSelectionModel().select(nuevoProyecto);
        }
        proyectoActual = nuevoProyecto;
        dibujarCanvas();
    }

    // ========== EDIT MODE ACTIONS ==========

    private void onAñadirTexto() {
        if (proyectoActual == null)
            return;

        int num = proyectoActual.getElementosActuales().size() + 1;
        // Ancho por defecto aumentado a 150 (antes 50 o 100) para facilitar edición de
        // nombres
        TextoElemento texto = new TextoElemento("Texto " + num, 50, 50);
        texto.setWidth(150); // Forzar ancho inicial más amplio
        proyectoActual.getElementosActuales().add(texto);
        elementoSeleccionado = texto;

        ensurePropertiesPanelVisible(); // Auto-mostrar propiedades al crear new element

        buildEditPanels();
        dibujarCanvas();
    }

    private void onAñadirImagen() {
        if (proyectoActual == null)
            return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Imagen");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (file != null) {
            try {
                Image img = new Image(file.toURI().toString());
                int num = proyectoActual.getElementosActuales().size() + 1;
                ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50,
                        file.getAbsolutePath(), img);
                proyectoActual.getElementosActuales().add(imgElem);
                elementoSeleccionado = imgElem;
                buildEditPanels();
                dibujarCanvas();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void onEliminarElemento() {
        if (proyectoActual != null && elementoSeleccionado != null) {
            proyectoActual.getElementosActuales().remove(elementoSeleccionado);
            elementoSeleccionado = null;
            buildEditPanels();
            dibujarCanvas();
        }
    }

    private void onAñadirFondo() {
        if (proyectoActual == null)
            return;

        // Check if background already exists
        ImagenFondoElemento fondoExistente = proyectoActual.getFondoActual();
        if (fondoExistente != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Reemplazar Fondo");
            alert.setHeaderText("Ya existe un fondo en esta cara");
            alert.setContentText("¿Desea reemplazar el fondo actual?");

            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Imagen de Fondo");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (file != null) {
            try {
                Image img = new Image(file.toURI().toString());

                // Determinar modo de ajuste
                FondoFitMode fitMode;
                if (proyectoActual.isNoVolverAPreguntarFondo() &&
                        proyectoActual.getFondoFitModePreferido() != null) {
                    // Usar preferencia guardada
                    fitMode = proyectoActual.getFondoFitModePreferido();
                } else {
                    // Mostrar diálogo
                    fitMode = mostrarDialogoFitMode();
                    if (fitMode == null) {
                        return; // Usuario canceló
                    }
                }

                ImagenFondoElemento nuevoFondo = new ImagenFondoElemento(
                        file.getAbsolutePath(), img, CARD_WIDTH, CARD_HEIGHT, fitMode);
                nuevoFondo.ajustarATamaño(CARD_WIDTH, CARD_HEIGHT, BLEED_MARGIN);

                proyectoActual.setFondoActual(nuevoFondo);
                elementoSeleccionado = nuevoFondo;
                buildEditPanels();
                dibujarCanvas();
            } catch (Exception ex) {
                ex.printStackTrace();
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Error");
                errorAlert.setHeaderText("No se pudo cargar la imagen");
                errorAlert.setContentText(ex.getMessage());
                errorAlert.showAndWait();
            }
        }
    }

    @FXML
    private void onTogglePropiedades() {
        togglePanel(rightPanel, togglePropiedades.isSelected(), false);
    }

    /**
     * Anima la visibilidad de un panel lateral (Overlay mode)
     */
    private void togglePanel(javafx.scene.layout.Region panel, boolean show, boolean isLeft) {
        // Asegurar que es visible e interactuable al empezar animación de mostrado
        if (show) {
            panel.setVisible(true);
            panel.setMouseTransparent(false);
        } else {
            panel.setMouseTransparent(true); // Bloquear interacción mientras se oculta
        }

        // Configurar sombra si no tiene
        if (panel.getEffect() == null) {
            javafx.scene.effect.DropShadow shadow = new javafx.scene.effect.DropShadow();
            shadow.setColor(Color.rgb(0, 0, 0, 0.5));
            shadow.setRadius(15);
            shadow.setSpread(0.1);
            panel.setEffect(shadow);
        }

        // Calcular desplazamiento
        double width = panel.getWidth() > 0 ? panel.getWidth() : panel.getPrefWidth();
        double startX = show ? (isLeft ? -width : width) : 0;
        double endX = show ? 0 : (isLeft ? -width : width);

        // Translate Transition
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), panel);
        tt.setFromX(startX);
        tt.setToX(endX);
        tt.setInterpolator(Interpolator.EASE_BOTH);

        // Fade Transition
        FadeTransition ft = new FadeTransition(Duration.millis(300), panel);
        ft.setFromValue(show ? 0.0 : 1.0);
        ft.setToValue(show ? 1.0 : 0.0);
        ft.setInterpolator(Interpolator.EASE_BOTH);

        // Parallel Transition
        ParallelTransition pt = new ParallelTransition(tt, ft);

        pt.setOnFinished(e -> {
            if (!show) {
                panel.setVisible(false);
            }
        });

        pt.play();
    }
}
