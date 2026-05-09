package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.view.dialogs.EditarProyectoDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import java.util.Collections;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import com.tpsstudio.util.AnimationHelper;

/**
 * Gestor del modo de la interfaz de usuario (Diseño / Producción).
 *
 * <p>Controla qué contenido se muestra en los paneles laterales izquierdo y derecho
 * en función del modo {@link com.tpsstudio.model.enums.AppMode} activo:</p>
 * <ul>
 *   <li><b>DESIGN</b>: herramientas + capas en el panel izquierdo; propiedades
 *       del elemento seleccionado o datos variables en el panel derecho.</li>
 *   <li><b>PRODUCTION</b>: lista de proyectos recientes en el panel izquierdo;
 *       opciones de exportación en el panel derecho.</li>
 * </ul>
 *
 * <p><b>Patrón de comunicación:</b><br/>
 * Para mantener el desacoplamiento con {@link com.tpsstudio.view.controllers.MainViewController},
 * usa callbacks ({@link Runnable}, {@link java.util.function.Consumer}) que el
 * controlador principal registra mediante los métodos {@code setOn...()}.
 * Esto evita referencias circulares y facilita la trazabilidad del flujo de eventos.</p>
 *
 * <p><b>Animaciones:</b><br/>
 * Al cambiar de modo, aplica {@link javafx.animation.FadeTransition} sobre los paneles
 * para una transición visual fluida, mejorando la experiencia de usuario.</p>
 *
 * @see com.tpsstudio.view.controllers.MainViewController
 * @see com.tpsstudio.model.enums.AppMode
 * @see PropertiesPanelController
 */
public class ModeManager {

    // Estado actual (Diseño / Producción)
    private AppMode currentMode;

    // Contenedores físicos de UI (se rellenan dinámicamente)
    private final VBox leftPanel;
    private final VBox rightPanel;

    // Controlador del panel de propiedades (solo aplica en modo Diseño)
    private final PropertiesPanelController propertiesPanelController;

    // Referencia opcional al ProjectManager (solo si se usa el helper interno)
    private com.tpsstudio.service.ProjectManager projectManager;

    // Callbacks hacia el controlador principal (MainViewController)
    private Runnable onAddText;
    private Runnable onAddImage;
    private Runnable onAddBackground;
    private Runnable onNewCR80;
    private Runnable onExport;
    private Runnable onPrint;

    private Consumer<Elemento> onElementSelected;
    private Consumer<Proyecto> onProjectSelected;
    private Consumer<Proyecto> onEditProject;

    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;
    private Consumer<Elemento> onToggleLock;

    private Runnable onValidateDesign;
    private Runnable onCanvasRedraw;

    // Nuevo callback para añadir formas
    private java.util.function.Consumer<com.tpsstudio.model.elements.FormaElemento.TipoForma> onAddShape;

    // Se guarda para poder refrescar solo la parte de "Capas" sin rehacer todo el
    // panel izquierdo
    private VBox layersPanel;

    // Panel de datos variables (null si no hay fuente de datos activa)
    private VBox datosPanel;

    // Indicador de qué "pestaña" del panel derecho está activa
    private boolean isPropertiesActive = true;
    private javafx.scene.Node propertiesNode;
    private javafx.scene.Node datosNode;

    // Estado de expansión del submenú de formas
    private boolean shapesExpanded = false;

    public ModeManager(VBox leftPanel, VBox rightPanel, PropertiesPanelController propertiesPanelController) {
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.propertiesPanelController = propertiesPanelController;
        this.currentMode = AppMode.DESIGN;
    }

    // ===================== SETTERS DE CALLBACKS =====================

    public void setOnAddText(Runnable callback) {
        this.onAddText = callback;
    }

    public void setOnAddImage(Runnable callback) {
        this.onAddImage = callback;
    }

    public void setOnAddBackground(Runnable callback) {
        this.onAddBackground = callback;
    }

    public void setOnNewCR80(Runnable callback) {
        this.onNewCR80 = callback;
    }

    public void setOnExport(Runnable callback) {
        this.onExport = callback;
    }

    public void setOnPrint(Runnable callback) {
        this.onPrint = callback;
    }

    public void setOnElementSelected(Consumer<Elemento> callback) {
        this.onElementSelected = callback;
    }

    public void setOnProjectSelected(Consumer<Proyecto> callback) {
        this.onProjectSelected = callback;
    }

    public void setOnEditProject(Consumer<Proyecto> callback) {
        this.onEditProject = callback;
    }

    public void setOnEditExternal(Consumer<ImagenFondoElemento> callback) {
        this.onEditExternal = callback;
    }

    public void setOnReload(Consumer<ImagenFondoElemento> callback) {
        this.onReload = callback;
    }

    public void setOnToggleLock(Consumer<Elemento> callback) {
        this.onToggleLock = callback;
    }

    public void setOnValidateDesign(Runnable callback) {
        this.onValidateDesign = callback;
    }

    public void setOnAddShape(java.util.function.Consumer<com.tpsstudio.model.elements.FormaElemento.TipoForma> callback) {
        this.onAddShape = callback;
    }

    public void setOnCanvasRedraw(Runnable callback) {
        this.onCanvasRedraw = callback;
    }

    public void setProjectManager(com.tpsstudio.service.ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    // ===================== MODO ACTUAL =====================

    public AppMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Cambia el modo de la interfaz y reconstruye los paneles laterales.
     * Nota: el canvas se repinta al final para evitar inconsistencias visuales.
     */
    public void switchMode(AppMode newMode, Proyecto proyecto, Elemento selectedElement,
            ObservableList<Proyecto> projects) {
        this.currentMode = newMode;

        // Limpieza completa de paneles
        leftPanel.getChildren().clear();
        rightPanel.getChildren().clear();

        // Reconstrucción según modo
        if (newMode == AppMode.DESIGN) {
            buildDesignModePanels(proyecto, selectedElement);
        } else {
            buildProductionModePanels(projects, proyecto);
        }

        // Animación suave de transición
        javafx.animation.FadeTransition fadeLeft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(550), leftPanel);
        fadeLeft.setFromValue(0.3);
        fadeLeft.setToValue(1.0);
        fadeLeft.play();

        javafx.animation.FadeTransition fadeRight = new javafx.animation.FadeTransition(javafx.util.Duration.millis(550), rightPanel);
        fadeRight.setFromValue(0.3);
        fadeRight.setToValue(1.0);
        fadeRight.play();

        // Repintado final (por seguridad)
        if (onCanvasRedraw != null) {
            onCanvasRedraw.run();
        }
    }

    // ===================== MODO DISEÑO =====================

    public void setRightPanelTabActiva(boolean isProperties) {
        if (this.isPropertiesActive == isProperties) return; // Evitar disparar animaciones si no cambia
        
        this.isPropertiesActive = isProperties;
        if (currentMode == AppMode.DESIGN) {
            javafx.scene.Node targetNode = isPropertiesActive ? propertiesNode : datosNode;
            
            if (targetNode != null) {
                // Animación suave de cambio de pestaña (Cross-fade)
                javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(350), rightPanel);
                fade.setFromValue(0.4);
                fade.setToValue(1.0);
                
                VBox.setVgrow(targetNode, Priority.ALWAYS); 
                rightPanel.getChildren().setAll(targetNode);
                fade.play();
            }
        }
    }

    /**
     * Refresca solo el panel de propiedades.
     */
    public void refreshPropertiesPanel(Elemento selectedElement, Proyecto proyecto) {
        VBox properties = propertiesPanelController.buildPanel(selectedElement, proyecto);
        ScrollPane scrollProps = new ScrollPane(properties);
        scrollProps.setFitToWidth(true);
        scrollProps.getStyleClass().add("panel-scroll-view");
        VBox.setVgrow(scrollProps, Priority.ALWAYS);
        this.propertiesNode = scrollProps;

        if (isPropertiesActive && currentMode == AppMode.DESIGN) {
            rightPanel.getChildren().setAll(propertiesNode);
        }
    }

    private void buildDesignModePanels(Proyecto proyecto, Elemento selectedElement) {
        leftPanel.getChildren().clear();
        rightPanel.getChildren().clear();

        // Panel izquierdo: herramientas + capas
        VBox toolbox = buildToolboxPanel();
        layersPanel = buildLayersPanel(proyecto, selectedElement);
        leftPanel.getChildren().addAll(toolbox, new Separator(), layersPanel);

        // Panel derecho: Nodos para "Propiedades" y "Datos Variables"

        // 1. Nodo de Propiedades
        refreshPropertiesPanel(selectedElement, proyecto);

        // 2. Nodo de Datos Variables
        if (projectManager != null && projectManager.getFuenteDatos() != null) {
            datosPanel = buildDatosVariablesPanel(projectManager.getFuenteDatos(), proyecto);
            // Sin ScrollPane extra: el VBox tiene vgrow=ALWAYS y la TextArea lleva scroll
            // interno
            VBox.setVgrow(datosPanel, Priority.ALWAYS);
            this.datosNode = datosPanel;
        } else {
            datosPanel = buildEmptyDatosVariablesPanel(proyecto);
            this.datosNode = datosPanel;
        }

        // Mostrar el nodo activo según el botón clickeado
        if (isPropertiesActive) {
            rightPanel.getChildren().setAll(propertiesNode);
        } else {
            rightPanel.getChildren().setAll(datosNode);
        }
    }

    /**
     * Refresca solo el panel de capas. Útil cuando se añaden/eliminan elementos o
     * cambia el lado (frente/dorso).
     */
    public void refreshLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        if (layersPanel == null)
            return;

        VBox newLayers = buildLayersPanel(proyecto, selectedElement);

        int index = leftPanel.getChildren().indexOf(layersPanel);
        if (index != -1) {
            leftPanel.getChildren().set(index, newLayers);
            layersPanel = newLayers;
        }
    }

    private VBox buildToolboxPanel() {
        VBox toolbox = new VBox(4);
        toolbox.setPadding(new Insets(14, 12, 14, 12)); // Mismo padding que buildProjectListPanel
        toolbox.getStyleClass().add("tools-panel");

        // Cabecera Equilibrada (como Gestión de Trabajos)
        VBox header = new VBox(2);
        header.setPadding(new Insets(0, 0, 8, 0)); // Margen bajo la cabecera
        Label lblToolbox = new Label("Herramientas");
        lblToolbox.getStyleClass().add("panel-title");
        Label lblSubtitulo = new Label("Seleccione un elemento para añadir al lienzo");
        lblSubtitulo.getStyleClass().add("panel-placeholder");
        header.getChildren().addAll(lblToolbox, lblSubtitulo);

        // Helper para crear el gráfico HBox [icono 32px | texto]
        // Este patrón garantiza que todos los iconos caen en la misma columna
        // y todos los textos empiezan en la misma X

        // ---- Texto ----
        Button btnTexto = makeToolButton("T", "tool-icon", "Texto", "tool-label", "tool-button");
        btnTexto.setOnAction(e -> { if (onAddText != null) onAddText.run(); });

        // ---- Imagen ----
        Button btnImagen = makeToolButton("▣", "tool-icon", "Imagen", "tool-label", "tool-button");
        btnImagen.setOnAction(e -> { if (onAddImage != null) onAddImage.run(); });

        // ---- Fondo ----
        Button btnFondo = makeToolButton("⬚", "tool-icon", "Fondo", "tool-label", "tool-button");
        btnFondo.setOnAction(e -> { if (onAddBackground != null) onAddBackground.run(); });

        // ---- Subherramientas de forma ----
        VBox shapesSubMenu = new VBox(1);
        shapesSubMenu.getStyleClass().add("tool-subtools");
        shapesSubMenu.setVisible(shapesExpanded);
        shapesSubMenu.setManaged(shapesExpanded);

        Button btnRectangulo = makeSubToolButton("▭", "Rectángulo");
        btnRectangulo.setOnAction(e -> { if (onAddShape != null) onAddShape.accept(com.tpsstudio.model.elements.FormaElemento.TipoForma.RECTANGULO); });

        Button btnElipse = makeSubToolButton("◯", "Elipse");
        btnElipse.setOnAction(e -> { if (onAddShape != null) onAddShape.accept(com.tpsstudio.model.elements.FormaElemento.TipoForma.ELIPSE); });

        Button btnLinea = makeSubToolButton("―", "Línea");
        btnLinea.setOnAction(e -> { if (onAddShape != null) onAddShape.accept(com.tpsstudio.model.elements.FormaElemento.TipoForma.LINEA); });

        shapesSubMenu.getChildren().addAll(btnRectangulo, btnElipse, btnLinea);

        // ---- Dibujar Forma (acordeón) ----
        Label iconExpander = new Label(shapesExpanded ? "▾" : "▸");
        iconExpander.getStyleClass().add("tool-icon");
        iconExpander.setMinWidth(16);
        iconExpander.setPrefWidth(16);
        iconExpander.setMaxWidth(16);

        Label iconFormas = new Label("⬒");
        iconFormas.getStyleClass().add("tool-icon");

        Label textFormas = new Label("Dibujar Forma");
        textFormas.getStyleClass().add("tool-label");
        HBox.setHgrow(textFormas, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox formasGraphic = new HBox(iconFormas, textFormas, spacer, iconExpander);
        formasGraphic.setAlignment(Pos.CENTER_LEFT);
        formasGraphic.setMaxWidth(Double.MAX_VALUE);

        Button btnToggleFormas = new Button();
        btnToggleFormas.setGraphic(formasGraphic);
        btnToggleFormas.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        btnToggleFormas.setMaxWidth(Double.MAX_VALUE);
        btnToggleFormas.getStyleClass().add("tool-button");
        btnToggleFormas.setOnAction(e -> {
            shapesExpanded = !shapesExpanded;
            iconExpander.setText(shapesExpanded ? "\u25BE" : "\u25B8");
            AnimationHelper.animateAccordion(shapesSubMenu, shapesExpanded);
        });

        // ---- Validar Diseño ----
        Button btnValidar = makeToolButton("✓", "tool-icon", "Validar Diseño", "tool-label", "validate-button");
        btnValidar.setOnAction(e -> { if (onValidateDesign != null) onValidateDesign.run(); });

        toolbox.getChildren().addAll(
                header,
                btnTexto,
                btnImagen,
                btnFondo,
                btnToggleFormas,
                shapesSubMenu,
                new Separator(),
                btnValidar
        );
        return toolbox;
    }

    /**
     * Crea un botón de herramienta principal con retícula fija:
     * [ icono 32px | texto ]
     * El icono y el texto se inyectan como un HBox gráfico GRAPHIC_ONLY,
     * garantizando que todos los textos comiencen en la misma X.
     */
    private Button makeToolButton(String iconText, String iconStyle,
                                   String labelText, String labelStyle,
                                   String buttonStyle) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add(iconStyle);

        Label label = new Label(labelText);
        label.getStyleClass().add(labelStyle);

        HBox graphic = new HBox(icon, label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setMaxWidth(Double.MAX_VALUE);

        Button btn = new Button();
        btn.setGraphic(graphic);
        btn.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add(buttonStyle);
        return btn;
    }

    /**
     * Crea un sub-botón (Rectángulo / Elipse / Línea) con retícula indentada:
     * [ icono 28px | texto ]
     */
    private Button makeSubToolButton(String iconText, String labelText) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add("tool-icon");

        Label label = new Label(labelText);
        label.getStyleClass().add("tool-label");

        HBox graphic = new HBox(icon, label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setMaxWidth(Double.MAX_VALUE);

        Button btn = new Button();
        btn.setGraphic(graphic);
        btn.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("tool-subbutton");
        return btn;
    }


    /**
     * Construye la lista de capas (fondo + elementos). El fondo se muestra arriba
     * si existe.
     */
    private VBox buildLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12)); // Restaurado el padding de 12px
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label lblCapas = new Label("Capas");
        lblCapas.getStyleClass().add("panel-title");

        ListView<Elemento> listCapas = new ListView<>();
        listCapas.getStyleClass().add("layers-list");
        VBox.setVgrow(listCapas, Priority.ALWAYS);

        if (proyecto != null) {
            ObservableList<Elemento> allElements = FXCollections.observableArrayList();

            // Fondo (si hay) + elementos del lado actual
            ImagenFondoElemento fondo = proyecto.getFondoActual();
            if (fondo != null) allElements.add(fondo);
            allElements.addAll(proyecto.getElementosActuales());

            listCapas.setItems(allElements);

            if (selectedElement != null) {
                listCapas.getSelectionModel().select(selectedElement);
            }

            // --- FACTORÍA DE CELDAS AVANZADA (ANIMACIONES + D&D) ---
            listCapas.setCellFactory(lv -> new ListCell<Elemento>() {
                private javafx.animation.Timeline pulse;

                @Override
                protected void updateItem(Elemento item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        setPadding(Insets.EMPTY);
                        if (pulse != null) { pulse.stop(); pulse = null; }
                    } else {
                        // 1. Barra de luz lateral
                        Region activeBar = new Region();
                        activeBar.setPrefWidth(3);
                        activeBar.setMinWidth(3);
                        activeBar.setMaxWidth(3);
                        activeBar.setMaxHeight(Double.MAX_VALUE);
                        activeBar.getStyleClass().add("layer-active-bar");
                        activeBar.setOpacity(0.0);
                        activeBar.setVisible(false);

                        // 2. Contenido (Icono + Texto)
                        String iconStr;
                        if (item instanceof com.tpsstudio.model.elements.ImagenFondoElemento) iconStr = "⬚";
                        else if (item instanceof com.tpsstudio.model.elements.TextoElemento)  iconStr = "T";
                        else if (item instanceof com.tpsstudio.model.elements.ImagenElemento) iconStr = "▣";
                        else if (item instanceof com.tpsstudio.model.elements.FormaElemento)  iconStr = "⬒";
                        else iconStr = "·";

                        String lockIcon = item.isLocked() ? " 🔒" : "";
                        String nombre = item.toString() + lockIcon;

                        Label lblIcon = new Label(iconStr);
                        lblIcon.getStyleClass().add("layer-item-icon");
                        lblIcon.setMinWidth(24);
                        lblIcon.setAlignment(Pos.CENTER);

                        Label lblNombre = new Label(nombre);
                        lblNombre.getStyleClass().add("layer-item-text");
                        lblNombre.setMinWidth(0);
                        lblNombre.setPrefWidth(1); // Forzar a que solo tome el espacio disponible
                        lblNombre.setMaxWidth(Double.MAX_VALUE);
                        lblNombre.setEllipsisString("…");
                        HBox.setHgrow(lblNombre, Priority.ALWAYS);
                        
                        if (isSelected()) {
                            getStyleClass().add("layer-item-selected");
                        } else {
                            getStyleClass().remove("layer-item-selected");
                        }

                        HBox actions = new HBox(4);
                        actions.setAlignment(Pos.CENTER_RIGHT);
                        actions.setMinWidth(Region.USE_PREF_SIZE); // No permitir que se encojan los botones
                        actions.setOpacity(0.0);
                        actions.setVisible(false);
                        actions.managedProperty().bind(actions.visibleProperty());

                        if (!(item instanceof ImagenFondoElemento)) {
                            Button btnUp = new Button("▲");
                            btnUp.getStyleClass().add("layer-action-btn");
                            btnUp.setOnAction(e -> {
                                int idx = proyecto.getElementosActuales().indexOf(item);
                                if (idx > 0) {
                                    Collections.swap(proyecto.getElementosActuales(), idx, idx - 1);
                                    if (onCanvasRedraw != null) onCanvasRedraw.run();
                                    buildDesignModePanels(proyecto, item);
                                }
                            });

                            Button btnDown = new Button("▼");
                            btnDown.getStyleClass().add("layer-action-btn");
                            btnDown.setOnAction(e -> {
                                int idx = proyecto.getElementosActuales().indexOf(item);
                                if (idx < proyecto.getElementosActuales().size() - 1) {
                                    Collections.swap(proyecto.getElementosActuales(), idx, idx + 1);
                                    if (onCanvasRedraw != null) onCanvasRedraw.run();
                                    buildDesignModePanels(proyecto, item);
                                }
                            });

                            Button btnDel = new Button("✕");
                            btnDel.getStyleClass().add("layer-action-btn-del");
                            btnDel.setOnAction(e -> {
                                proyecto.getElementosActuales().remove(item);
                                if (onCanvasRedraw != null) onCanvasRedraw.run();
                                buildDesignModePanels(proyecto, null);
                            });

                            actions.getChildren().addAll(btnUp, btnDown, btnDel);
                        }

                        HBox content = new HBox(8, lblIcon, lblNombre, actions);
                        content.setAlignment(Pos.CENTER_LEFT);
                        content.setPadding(new Insets(0, 10, 0, 10));
                        HBox.setHgrow(content, Priority.ALWAYS);

                        HBox row = new HBox(activeBar, content);
                        row.setAlignment(Pos.CENTER_LEFT);

                        // 3. Overlays de estado
                        Region hoverOverlay = new Region();
                        hoverOverlay.getStyleClass().add("layer-hover-overlay");
                        hoverOverlay.setOpacity(0.0);
                        hoverOverlay.setMouseTransparent(true);

                        Region selectedOverlay = new Region();
                        selectedOverlay.getStyleClass().add("layer-selected-overlay");
                        selectedOverlay.setOpacity(0.0);
                        selectedOverlay.setMouseTransparent(true);

                        StackPane card = new StackPane(hoverOverlay, selectedOverlay, row);
                        card.getStyleClass().add("layer-card");
                        card.setPrefHeight(40);
                        card.setMinHeight(40);

                        // Separador
                        Region sep = new Region();
                        sep.getStyleClass().add("layer-separator");
                        sep.setPrefHeight(1);


                        VBox cellLayout = new VBox(card, sep);
                        setGraphic(cellLayout);
                        setText(null);
                        setPadding(Insets.EMPTY);

                        // 4. Animaciones
                        javafx.animation.FadeTransition hIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(350), hoverOverlay);
                        hIn.setToValue(0.6);
                        javafx.animation.FadeTransition hOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), hoverOverlay);
                        hOut.setToValue(0.0);

                        if (isSelected()) {
                            javafx.animation.FadeTransition sIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(250), selectedOverlay);
                            sIn.setToValue(1.0);
                            sIn.play();

                            // Fade suave de los botones de acción
                            javafx.animation.FadeTransition actIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(500), actions);
                            actIn.setFromValue(0.0);
                            actIn.setToValue(1.0);
                            actions.setVisible(true);
                            actIn.play();

                            // Fade suave de la barra lateral
                            javafx.animation.FadeTransition barIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(400), activeBar);
                            barIn.setFromValue(0.0);
                            barIn.setToValue(0.7);
                            activeBar.setVisible(true);
                            barIn.play();

                            if (pulse == null) {
                                pulse = new javafx.animation.Timeline(
                                    new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, new javafx.animation.KeyValue(activeBar.opacityProperty(), 0.3, javafx.animation.Interpolator.EASE_BOTH)),
                                    new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1.2), new javafx.animation.KeyValue(activeBar.opacityProperty(), 1.0, javafx.animation.Interpolator.EASE_BOTH)),
                                    new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2.4), new javafx.animation.KeyValue(activeBar.opacityProperty(), 0.3, javafx.animation.Interpolator.EASE_BOTH))
                                );
                                pulse.setCycleCount(javafx.animation.Timeline.INDEFINITE);
                                pulse.play();
                            }
                        } else {
                            activeBar.setVisible(false);
                            if (pulse != null) { pulse.stop(); pulse = null; }
                        }

                        card.setOnMouseEntered(e -> { 
                            if (!isSelected()) {
                                hIn.playFromStart();
                                javafx.animation.FadeTransition aHIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), actions);
                                aHIn.setToValue(0.7);
                                actions.setVisible(true);
                                aHIn.play();
                            }
                        });
                        card.setOnMouseExited(e -> { 
                            if (!isSelected()) {
                                hOut.playFromStart();
                                javafx.animation.FadeTransition aHOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(250), actions);
                                aHOut.setToValue(0.0);
                                aHOut.setOnFinished(ev -> actions.setVisible(false));
                                aHOut.play();
                            }
                        });

                        card.setOnMousePressed(e -> {
                            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(100), card);
                            st.setToX(0.97);
                            st.setToY(0.97);
                            st.play();
                        });
                        card.setOnMouseReleased(e -> {
                            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(150), card);
                            st.setToX(1.0);
                            st.setToY(1.0);
                            st.play();
                        });

                        // 5. Drag & Drop (Reordenar)
                        card.setOnDragDetected(event -> {
                            if (item instanceof ImagenFondoElemento) return; // Fondo no se mueve
                            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
                            ClipboardContent cc = new ClipboardContent();
                            cc.putString(String.valueOf(allElements.indexOf(item)));
                            db.setContent(cc);
                            event.consume();
                        });

                        card.setOnDragOver(event -> {
                            if (event.getGestureSource() != card && event.getDragboard().hasString()) {
                                event.acceptTransferModes(TransferMode.MOVE);
                            }
                            event.consume();
                        });

                        card.setOnDragDropped(event -> {
                            Dragboard db = event.getDragboard();
                            if (db.hasString()) {
                                int sourceIdx = Integer.parseInt(db.getString());
                                int targetIdx = allElements.indexOf(item);

                                // No permitir mover nada debajo del fondo (que está en 0 si existe)
                                ImagenFondoElemento f = proyecto.getFondoActual();
                                int minIdx = (f != null) ? 1 : 0;

                                if (sourceIdx >= minIdx && targetIdx >= minIdx && sourceIdx != targetIdx) {
                                    Elemento sourceItem = allElements.get(sourceIdx);
                                    
                                    // Sincronizar con la lista real del proyecto
                                    ObservableList<Elemento> listaReal = proyecto.getElementosActuales();
                                    int realSourceIdx = sourceIdx - minIdx;
                                    int realTargetIdx = targetIdx - minIdx;
                                    
                                    if (realSourceIdx >= 0 && realTargetIdx >= 0) {
                                        Collections.swap(listaReal, realSourceIdx, realTargetIdx);
                                        if (onCanvasRedraw != null) onCanvasRedraw.run();
                                        // Refrescar panel para ver cambios
                                        buildDesignModePanels(proyecto, sourceItem);
                                    }
                                }
                                event.setDropCompleted(true);
                            }
                            event.consume();
                        });

                        // Menú contextual
                        ContextMenu cm = new ContextMenu();
                        if (item instanceof ImagenFondoElemento) {
                            MenuItem mEdit = new MenuItem("Editar fondo...");
                            mEdit.setOnAction(e -> { if (onEditExternal != null) onEditExternal.accept((ImagenFondoElemento)item); });
                            cm.getItems().add(mEdit);
                        } else {
                            MenuItem mDel = new MenuItem("Eliminar");
                            mDel.setOnAction(e -> {
                                proyecto.getElementosActuales().remove(item);
                                if (onCanvasRedraw != null) onCanvasRedraw.run();
                                buildDesignModePanels(proyecto, null);
                            });
                            cm.getItems().add(mDel);
                        }
                        setContextMenu(cm);
                    }
                }
            });

            // Borrado rápido con teclado
            listCapas.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                    Elemento sel = listCapas.getSelectionModel().getSelectedItem();
                    if (sel != null && !(sel instanceof ImagenFondoElemento)) {
                        proyecto.getElementosActuales().remove(sel);
                        if (onCanvasRedraw != null) onCanvasRedraw.run();
                        buildDesignModePanels(proyecto, null);
                    }
                }
            });

            listCapas.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (onElementSelected != null) onElementSelected.accept(newVal);
            });
        }

        panel.getChildren().addAll(lblCapas, listCapas);
        return panel;
    }

    /* Construye el panel de navegación y vista del registro activo. */
    private VBox buildDatosVariablesPanel(FuenteDatos datos, Proyecto proyecto) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        VBox.setVgrow(panel, Priority.ALWAYS);

        // Cabecera
        Label lblTitulo = new Label(datos.getNombreArchivo());
        lblTitulo.getStyleClass().add("panel-title");
        lblTitulo.setMaxWidth(Double.MAX_VALUE);
        lblTitulo.setWrapText(false);

        Button btnCambiarBD = new Button("⚙ Cambiar BD...");
        btnCambiarBD.getStyleClass().add("toolbox-btn");
        btnCambiarBD.setMaxWidth(Double.MAX_VALUE);
        btnCambiarBD.setOnAction(e -> {
            if (onEditProject != null && proyecto != null)
                onEditProject.accept(proyecto);
        });

        // Contador
        Label lblContador = new Label(calcularContador(datos));
        lblContador.getStyleClass().add("toolbar-label");

        // Navegación
        Button btnAnterior = new Button("◄ Anterior");
        btnAnterior.getStyleClass().add("toolbox-btn");
        btnAnterior.setMaxWidth(Double.MAX_VALUE);
        btnAnterior.setDisable(!datos.tieneRegistros() || datos.getIndiceActual() <= 0);

        Button btnSiguiente = new Button("Siguiente ►");
        btnSiguiente.getStyleClass().add("toolbox-btn");
        btnSiguiente.setMaxWidth(Double.MAX_VALUE);
        btnSiguiente.setDisable(!datos.tieneRegistros() || datos.getIndiceActual() >= datos.getTotalRegistros() - 1);

        HBox navBox = new HBox(8, btnAnterior, btnSiguiente);
        navBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(btnAnterior, Priority.ALWAYS);
        HBox.setHgrow(btnSiguiente, Priority.ALWAYS);

        // Vista de registro: pares COLUMNA → VALOR dentro de un ScrollPane
        VBox vistaRegistro = construirVistaRegistro(datos);

        ScrollPane scrollRegistro = new ScrollPane(vistaRegistro);
        scrollRegistro.setFitToWidth(true);
        scrollRegistro.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollRegistro.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollRegistro.getStyleClass().add("panel-scroll-view");
        VBox.setVgrow(scrollRegistro, Priority.ALWAYS);

        btnAnterior.setOnAction(e -> {
            datos.anterior();
            lblContador.setText(calcularContador(datos));
            btnAnterior.setDisable(datos.getIndiceActual() <= 0);
            btnSiguiente.setDisable(datos.getIndiceActual() >= datos.getTotalRegistros() - 1);
            actualizarVistaRegistro(vistaRegistro, datos);
            if (onCanvasRedraw != null)
                onCanvasRedraw.run();
        });

        btnSiguiente.setOnAction(e -> {
            datos.siguiente();
            lblContador.setText(calcularContador(datos));
            btnAnterior.setDisable(datos.getIndiceActual() <= 0);
            btnSiguiente.setDisable(datos.getIndiceActual() >= datos.getTotalRegistros() - 1);
            actualizarVistaRegistro(vistaRegistro, datos);
            if (onCanvasRedraw != null)
                onCanvasRedraw.run();
        });

        panel.getChildren().addAll(
                lblTitulo,
                btnCambiarBD,
                new Separator(),
                lblContador,
                navBox,
                new Separator(),
                scrollRegistro);

        return panel;
    }

    /* Crea el contenedor de pares COLUMNA/VALOR para el registro inicial. */
    private VBox construirVistaRegistro(FuenteDatos datos) {
        VBox contenedor = new VBox(6);
        contenedor.setPadding(new Insets(4, 0, 4, 0));
        rellenarVistaRegistro(contenedor, datos);
        return contenedor;
    }

    /* Limpia y vuelve a dibujar los pares tras un cambio de registro. */
    private void actualizarVistaRegistro(VBox contenedor, FuenteDatos datos) {
        contenedor.getChildren().clear();
        rellenarVistaRegistro(contenedor, datos);
    }

    /*
     * Añade al contenedor un bloque por cada columna con su valor en el registro
     * actual.
     */
    private void rellenarVistaRegistro(VBox contenedor, FuenteDatos datos) {
        Map<String, String> registro = datos.getRegistroActual();

        if (registro == null) {
            Label lblVacio = new Label("(sin registros)");
            lblVacio.getStyleClass().add("panel-placeholder");
            contenedor.getChildren().add(lblVacio);
            return;
        }

        for (String columna : datos.getColumnas()) {
            String valor = registro.getOrDefault(columna, "");

            Label lblColumna = new Label(columna);
            lblColumna.getStyleClass().add("dato-columna");
            lblColumna.setMaxWidth(Double.MAX_VALUE);

            Label lblValor = new Label(valor.isEmpty() ? "—" : valor);
            lblValor.getStyleClass().add("dato-valor");
            lblValor.setMaxWidth(Double.MAX_VALUE);
            lblValor.setWrapText(true);

            VBox campo = new VBox(2, lblColumna, lblValor);
            campo.setPadding(new Insets(6, 10, 6, 10));
            campo.getStyleClass().add("dato-campo");
            campo.setMaxWidth(Double.MAX_VALUE);

            contenedor.getChildren().add(campo);
        }
    }

    private VBox buildEmptyDatosVariablesPanel(Proyecto proyecto) {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(30));
        panel.setAlignment(Pos.CENTER);

        Label lblMensaje = new Label("No hay base de datos vinculada");
        lblMensaje.getStyleClass().add("panel-placeholder");

        Button btnVincular = new Button("+ Vincular Base de Datos");
        btnVincular.getStyleClass().add("primary-btn");
        btnVincular.setOnAction(e -> {
            if (onEditProject != null && proyecto != null) {
                onEditProject.accept(proyecto);
            }
        });

        panel.getChildren().addAll(lblMensaje, btnVincular);
        return panel;
    }

    private String calcularContador(FuenteDatos datos) {
        if (!datos.tieneRegistros())
            return "(sin registros)";
        return "Registro " + datos.getPosicionActual() + " / " + datos.getTotalRegistros();
    }

    // ===================== MODO PRODUCCIÓN =====================

    private void buildProductionModePanels(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = buildProjectListPanel(projects, currentProject);
        leftPanel.getChildren().add(projectPanel);

        VBox exportPanel = buildExportPanel();
        exportPanel.getStyleClass().add("panel-dark-bg");

        ScrollPane scrollPane = new ScrollPane(exportPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("panel-scroll-view");
        scrollPane.setPadding(Insets.EMPTY);

        rightPanel.getChildren().add(scrollPane);
    }

    private VBox buildProjectListPanel(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = new VBox(12);
        projectPanel.setPadding(new Insets(14, 12, 14, 12));
        VBox.setVgrow(projectPanel, Priority.ALWAYS);

        // Cabecera Equilibrada
        VBox header = new VBox(2);
        Label lblTrabajos = new Label("Gestión de Trabajos");
        lblTrabajos.getStyleClass().add("panel-title");
        Label lblSubtitulo = new Label("Administración y exportación");
        lblSubtitulo.getStyleClass().add("panel-placeholder");
        header.getChildren().addAll(lblTrabajos, lblSubtitulo);

        ListView<Proyecto> listProyectos = new ListView<>();
        listProyectos.setItems(projects);
        listProyectos.getStyleClass().add("project-list");
        VBox.setVgrow(listProyectos, Priority.ALWAYS);

        // --- FACTORÍA DE CELDAS COMPACTA CON EFECTO DE PULSO ---
        listProyectos.setCellFactory(lv -> new ListCell<Proyecto>() {
            private javafx.animation.Timeline pulse;

            @Override
            protected void updateItem(Proyecto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setPadding(Insets.EMPTY);
                    if (pulse != null) { pulse.stop(); pulse = null; }
                } else {
                    // ── Barra de luz IZQUIERDA ─────────────────────────────────────
                    javafx.scene.layout.Region activeBar = new javafx.scene.layout.Region();
                    activeBar.setPrefWidth(4);
                    activeBar.setMinWidth(4);
                    activeBar.setMaxWidth(4);
                    activeBar.setMaxHeight(Double.MAX_VALUE);
                    activeBar.getStyleClass().add("project-active-bar");
                    activeBar.setOpacity(0.0);
                    activeBar.setVisible(false);

                    // ── Texto ──────────────────────────────────────────────────────
                    VBox textContainer = new VBox(2);
                    textContainer.setAlignment(Pos.CENTER_LEFT);
                    Label lblName = new Label(item.getNombre());
                    lblName.getStyleClass().add("project-cell-name");
                    lblName.setMaxWidth(Double.MAX_VALUE);
                    lblName.setEllipsisString("…");
                    HBox.setHgrow(lblName, Priority.SOMETIMES);
                    Label lblEmpresa = new Label("");
                    lblEmpresa.getStyleClass().add("project-cell-type");
                    if (item.getMetadata() != null && item.getMetadata().getClienteInfo() != null) {
                        String empresa = item.getMetadata().getClienteInfo().getNombreEmpresa();
                        if (empresa != null && !empresa.trim().isEmpty()) {
                            lblEmpresa.setText("Cliente: " + empresa);
                        }
                    }
                    textContainer.getChildren().addAll(lblName, lblEmpresa);

                    javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    Label lblBadge = new Label(item.getTipo());
                    lblBadge.getStyleClass().add("project-badge");

                    // HBox de contenido (barra + texto + badge)
                    HBox contentRow = new HBox(0);
                    contentRow.setAlignment(Pos.CENTER_LEFT);
                    contentRow.setMaxWidth(Double.MAX_VALUE);
                    contentRow.setMaxHeight(Double.MAX_VALUE);
                    HBox textAndBadge = new HBox(10);
                    textAndBadge.setAlignment(Pos.CENTER_LEFT);
                    textAndBadge.setPadding(new Insets(0, 12, 0, 12));
                    HBox.setHgrow(textAndBadge, Priority.ALWAYS);
                    textAndBadge.getChildren().addAll(textContainer, spacer, lblBadge);
                    contentRow.getChildren().addAll(activeBar, textAndBadge);

                    // ── Hover overlay (muy sutil) ──────────────────────────────────
                    javafx.scene.layout.Region hoverOverlay = new javafx.scene.layout.Region();
                    hoverOverlay.getStyleClass().add("project-hover-overlay");
                    hoverOverlay.setOpacity(0.0);
                    hoverOverlay.setMouseTransparent(true);
                    hoverOverlay.setMaxWidth(Double.MAX_VALUE);
                    hoverOverlay.setMaxHeight(Double.MAX_VALUE);

                    // ── Selected overlay (para transición al seleccionar) ──────────
                    javafx.scene.layout.Region selectedOverlay = new javafx.scene.layout.Region();
                    selectedOverlay.getStyleClass().add("project-selected-overlay");
                    selectedOverlay.setOpacity(0.0);
                    selectedOverlay.setMouseTransparent(true);
                    selectedOverlay.setMaxWidth(Double.MAX_VALUE);
                    selectedOverlay.setMaxHeight(Double.MAX_VALUE);

                    // ── StackPane: overlays debajo → contentRow encima ────────────
                    javafx.scene.layout.StackPane card = new javafx.scene.layout.StackPane();
                    card.getStyleClass().add("project-card");
                    card.setPrefHeight(62);
                    card.setMinHeight(62);
                    card.getChildren().addAll(hoverOverlay, selectedOverlay, contentRow);

                    // Clip con esquinas redondeadas
                    javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
                    clip.setArcWidth(16);
                    clip.setArcHeight(16);
                    card.layoutBoundsProperty().addListener((obs, old, b) -> {
                        clip.setWidth(b.getWidth());
                        clip.setHeight(b.getHeight());
                    });
                    card.setClip(clip);

                    // ── Separador sutil (fuera de la tarjeta) ─────────────────────
                    javafx.scene.layout.Region separator = new javafx.scene.layout.Region();
                    separator.getStyleClass().add("project-separator");
                    separator.setPrefHeight(1);
                    separator.setMaxWidth(Double.MAX_VALUE);
                    VBox.setMargin(separator, new Insets(0, 8, 0, 8));

                    // ── Layout de celda ───────────────────────────────────────────
                    VBox cellLayout = new VBox(0);
                    cellLayout.getChildren().addAll(card, separator);
                    setGraphic(cellLayout);
                    setText(null);
                    setPadding(new Insets(6, 8, 0, 8));

                    // ── Animaciones ───────────────────────────────────────────────
                    // Hover: muy sutil (opacidad 0 → 0.6 del overlay)
                    javafx.animation.FadeTransition hoverIn  = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), hoverOverlay);
                    hoverIn.setToValue(0.6);
                    javafx.animation.FadeTransition hoverOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), hoverOverlay);
                    hoverOut.setToValue(0.0);

                    // ── Estado seleccionado ───────────────────────────────────────
                    if (isSelected()) {
                        // Ya seleccionado: aparecer con fade-in suave del selectedOverlay
                        javafx.animation.FadeTransition selectIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(550), selectedOverlay);
                        selectIn.setFromValue(0.0);
                        selectIn.setToValue(1.0);
                        selectIn.play();

                        activeBar.setVisible(true);
                        if (pulse == null) {
                            pulse = new javafx.animation.Timeline(
                                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                                    new javafx.animation.KeyValue(activeBar.opacityProperty(), 0.35, javafx.animation.Interpolator.EASE_BOTH)),
                                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1.2),
                                    new javafx.animation.KeyValue(activeBar.opacityProperty(), 1.0, javafx.animation.Interpolator.EASE_BOTH)),
                                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2.4),
                                    new javafx.animation.KeyValue(activeBar.opacityProperty(), 0.35, javafx.animation.Interpolator.EASE_BOTH))
                            );
                            pulse.setCycleCount(javafx.animation.Timeline.INDEFINITE);
                            pulse.play();
                        }

                    } else {
                        activeBar.setVisible(false);
                        if (pulse != null) { pulse.stop(); pulse = null; }
                    }

                    card.setOnMouseEntered(e -> { if (!isSelected()) hoverIn.playFromStart(); });
                    card.setOnMouseExited(e  -> { if (!isSelected()) hoverOut.playFromStart(); });
                }
            }

        });

        if (currentProject != null) {
            listProyectos.getSelectionModel().select(currentProject);
        }

        listProyectos.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && onProjectSelected != null)
                onProjectSelected.accept(newVal);
        });

        listProyectos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Proyecto seleccionado = listProyectos.getSelectionModel().getSelectedItem();
                if (seleccionado != null) {
                    if (onEditProject != null) onEditProject.accept(seleccionado);
                    else abrirDialogoEditarProyecto(seleccionado);
                }
            }
        });

        Button btnNuevoCR80 = new Button("+ NUEVO PROYECTO CR80");
        btnNuevoCR80.getStyleClass().add("new-project-btn");
        btnNuevoCR80.setStyle("-fx-font-size: 11.5px;");
        btnNuevoCR80.setMaxWidth(Double.MAX_VALUE);
        btnNuevoCR80.setOnAction(e -> {
            if (onNewCR80 != null) onNewCR80.run();
        });

        projectPanel.getChildren().addAll(header, listProyectos, btnNuevoCR80);
        return projectPanel;
    }

    /**
     * Helper interno por si se quiere que ModeManager resuelva el diálogo.
     * Si usas callbacks (lo ideal), este método puede quedarse sin usar.
     */
    private void abrirDialogoEditarProyecto(Proyecto proyecto) {
        if (projectManager == null)
            return;

        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto, null);
        Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            projectManager.eliminarProyecto(proyecto);
            return;
        }

        if (resultado.isPresent()) {
            projectManager.editarProyecto(proyecto, resultado.get());
        }
    }

    /**
     * Panel de exportación. De momento son placeholders, pero deja el hueco
     * preparado.
     */
    private VBox buildExportPanel() {
        VBox exportPanel = new VBox(15);
        exportPanel.setPadding(new Insets(30));
        exportPanel.setFillWidth(true);

        Label lblExport = new Label("Exportación");
        lblExport.getStyleClass().add("panel-title");

        Label lblInfoExp = new Label("Formato: PNG/PDF (pendiente)");
        lblInfoExp.getStyleClass().add("panel-placeholder");

        Label lblDpi = new Label("DPI: 300 (pendiente)");
        lblDpi.getStyleClass().add("panel-placeholder");

        Label lblGuias = new Label("Incluir guías: No (pendiente)");
        lblGuias.getStyleClass().add("panel-placeholder");

        Label lblSide = new Label("Exportar: Frente (pendiente)");
        lblSide.getStyleClass().add("panel-placeholder");

        Button btnDoExport = new Button("Exportar");
        btnDoExport.getStyleClass().add("success-btn");
        btnDoExport.setMaxWidth(200.0);
        btnDoExport.setOnAction(e -> {
            if (onExport != null)
                onExport.run();
        });

        Button btnImprimir = new Button("\uD83D\uDDA8 Imprimir\u2026");
        btnImprimir.getStyleClass().add("toolbox-btn");
        btnImprimir.setMaxWidth(200.0);
        btnImprimir.setOnAction(e -> {
            if (onPrint != null)
                onPrint.run();
        });

        exportPanel.getChildren().addAll(
                lblExport,
                lblInfoExp,
                lblDpi,
                lblGuias,
                lblSide,
                new Separator(),
                btnDoExport,
                btnImprimir);

        return exportPanel;
    }
}
