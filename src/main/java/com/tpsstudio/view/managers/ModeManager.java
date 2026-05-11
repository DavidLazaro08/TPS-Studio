package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ElementoQR;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.project.Etiqueta;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.service.EtiquetasManager;
import com.tpsstudio.util.AnimationHelper;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Pane;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.image.ImageView;
import java.util.Collections;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.css.PseudoClass;
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

    // Gestor de categorías/etiquetas del usuario (inyectado mediante setter)
    private EtiquetasManager etiquetasManager;
    // IDs de categorías activas en el filtro (vacío = Todos)
    private java.util.List<String> filtroActivo = new java.util.ArrayList<>();
    // Lista filtrada que se muestra en el ListView
    private ObservableList<Proyecto> proyectosFiltrados;
    // UI para mostrar cuántos proyectos hay ocultos
    private HBox panelOcultos;
    private Label lblOcultosText;
    private javafx.stage.Popup filtroPopup;
    private Button btnFiltro; // Referencia para actualizar el icono/estado
    private boolean omitirConfirmacionBorradoEtiqueta = false;
    private HBox filtroInfoBox;
    private Label lblFiltroActual;
    private String filtroTexto = "";

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

    // Callback para añadir código QR
    private Runnable onAddQR;

    // Se guarda para poder refrescar solo la parte de "Capas" sin rehacer todo el
    // panel izquierdo
    private VBox layersPanel;
    private ListView<Elemento> layersListView;

    // Panel de datos variables (null si no hay fuente de datos activa)
    private VBox datosPanel;

    // Indicador de qué "pestaña" del panel derecho está activa
    private boolean isPropertiesActive = true;
    private boolean isUpdatingSelection = false;
    private javafx.scene.Node propertiesNode;
    private javafx.scene.Node datosNode;

    // Estado de expansión del submenú de formas
    private boolean shapesExpanded = false;

    // Botón de validación para aplicar animaciones
    private Button btnValidar;
    private javafx.animation.Timeline periodicReminder;
    private javafx.animation.Timeline pulseAnimation;

    public ModeManager(VBox leftPanel, VBox rightPanel, PropertiesPanelController propertiesPanelController) {
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.propertiesPanelController = propertiesPanelController;
        this.currentMode = AppMode.DESIGN;
    }

    /** Inyecta el gestor de categorías después de la construcción. */
    public void setEtiquetasManager(EtiquetasManager etiquetasManager) {
        this.etiquetasManager = etiquetasManager;
        if (etiquetasManager != null) {
            filtroActivo = new java.util.ArrayList<>(etiquetasManager.getFiltroActivo());
        }
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

    /**
     * Activa el recordatorio periódico de advertencia de diseño.
     * El botón parpadeará/cambiará de color solo durante unos segundos cada cierto tiempo
     * para no generar ruido visual constante.
     */
    public void setValidationWarning(boolean hasWarning) {
        if (btnValidar == null) return;
        
        if (hasWarning) {
            // Si ya hay un recordatorio en marcha, no hacemos nada
            if (periodicReminder != null) return;
            
            // Creamos un ciclo de 10 minutos
            // El primer aviso no es inmediato (espera los 10 min) para no molestar al editar
            periodicReminder = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(10), e -> startWarningVisuals()),
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(10).add(javafx.util.Duration.seconds(8)), e -> stopWarningVisuals())
            );
            periodicReminder.setCycleCount(javafx.animation.Animation.INDEFINITE);
            periodicReminder.play();
        } else {
            // Si el diseño es correcto, matamos el recordatorio y limpiamos visuales
            if (periodicReminder != null) {
                periodicReminder.stop();
                periodicReminder = null;
            }
            stopWarningVisuals();
        }
    }

    private void startWarningVisuals() {
        if (btnValidar == null) return;
        if (!btnValidar.getStyleClass().contains("has-warnings")) {
            btnValidar.getStyleClass().add("has-warnings");
        }
        if (pulseAnimation == null) {
            pulseAnimation = com.tpsstudio.util.AnimationHelper.createPulseAnimation(btnValidar);
        }
        pulseAnimation.play();
    }

    private void stopWarningVisuals() {
        if (btnValidar == null) return;
        btnValidar.getStyleClass().remove("has-warnings");
        if (pulseAnimation != null) {
            pulseAnimation.stop();
            btnValidar.setScaleX(1.0);
            btnValidar.setScaleY(1.0);
        }
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

    public void setOnAddQR(Runnable callback) {
        this.onAddQR = callback;
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
        if (newMode == AppMode.DESIGN) {
            rightPanel.getChildren().clear();
        }

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
                VBox.setVgrow(targetNode, Priority.ALWAYS);
                ejecutarTransicionSutil(rightPanel, () -> {
                    rightPanel.getChildren().setAll(targetNode);
                });
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
            ejecutarTransicionSutil(rightPanel, () -> {
                rightPanel.getChildren().setAll(propertiesNode);
            });
        }
    }

    /**
     * Realiza una transición de fundido muy sutil para el panel derecho,
     * dando feedback visual sin interferir con el layout.
     */
    private void ejecutarTransicionSutil(VBox panel, Runnable action) {
        if (panel == null || panel.getScene() == null) {
            action.run();
            return;
        }

        // Ejecutar el cambio instantáneamente
        action.run();
        
        // Aplicar un pequeño "shimmer" o fundido de entrada muy rápido
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(300), panel);
        fadeIn.setFromValue(0.6);
        fadeIn.setToValue(1.0);
        fadeIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        fadeIn.play();
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
     * Actualiza SOLO la selección en el ListView existente.
     * NO reconstruye el panel — preserva el foco y evita animaciones innecesarias.
     * Usar para: cambios de selección (clic en canvas, clic en lista).
     */
    public void refreshLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        if (layersListView != null) {
            isUpdatingSelection = true;
            try {
                // Sincronizar la lista de elementos (incluyendo el fondo virtual)
                layersListView.setItems(getCapasDeProyecto(proyecto));

                layersListView.getSelectionModel().clearSelection();
                if (selectedElement != null) {
                    layersListView.getSelectionModel().select(selectedElement);
                    layersListView.scrollTo(selectedElement);
                }
                // Forzar refresco visual de las celdas
                layersListView.refresh();
            } finally {
                isUpdatingSelection = false;
            }
        }
    }

    /**
     * Reconstruye el panel de capas por completo.
     * Usar SOLO para cambios estructurales: añadir/eliminar capa, cambiar cara.
     */
    public void rebuildLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        if (layersPanel != null && leftPanel != null) {
            VBox newLayers = buildLayersPanel(proyecto, selectedElement);
            int index = leftPanel.getChildren().indexOf(layersPanel);
            if (index != -1) {
                leftPanel.getChildren().set(index, newLayers);
                this.layersPanel = newLayers;
            }
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

        // ---- Código QR ----
        Button btnQR = makeToolButton("⦀", "tool-icon", "Código QR", "tool-label", "tool-button");
        btnQR.setOnAction(e -> { if (onAddQR != null) onAddQR.run(); });

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
        btnValidar = makeToolButton("✓", "tool-icon", "Validar Diseño", "tool-label", "validate-button");
        btnValidar.setOnAction(e -> { if (onValidateDesign != null) onValidateDesign.run(); });

        toolbox.getChildren().addAll(
                header,
                btnTexto,
                btnImagen,
                btnFondo,
                btnQR,
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

        this.layersListView = new ListView<>();
        layersListView.getStyleClass().add("layers-list");
        VBox.setVgrow(layersListView, Priority.ALWAYS);

        final Proyecto currentProj = proyecto;
        if (currentProj != null) {
            layersListView.setItems(getCapasDeProyecto(proyecto));

            if (selectedElement != null) {
                isUpdatingSelection = true;
                try {
                    layersListView.getSelectionModel().select(selectedElement);
                    layersListView.scrollTo(selectedElement);
                } finally {
                    isUpdatingSelection = false;
                }
            }

            layersListView.setCellFactory(lv -> new ListCell<Elemento>() {
                private javafx.animation.Timeline pulse;
                
                // Nodos persistentes para evitar recreación en cada updateItem
                private final Region activeBar = new Region();
                private final Label lblIcon = new Label();
                private final Label lblNombre = new Label();
                private final HBox actions = new HBox(4);
                private final Region hoverOverlay = new Region();
                private final Region selectedOverlay = new Region();
                private final HBox row = new HBox();
                private final StackPane card = new StackPane();
                private final Region sep = new Region();
                private final VBox cellLayout = new VBox();
                
                private javafx.animation.FadeTransition hIn;
                private javafx.animation.FadeTransition hOut;
                private final ContextMenu cm = new ContextMenu();

                {
                    // INICIALIZACIÓN ÚNICA DE LA ESTRUCTURA DE LA CELDA
                    activeBar.setPrefWidth(3);
                    activeBar.setMinWidth(3);
                    activeBar.setMaxWidth(3);
                    activeBar.setMaxHeight(Double.MAX_VALUE);
                    activeBar.getStyleClass().add("layer-active-bar");
                    
                    lblIcon.getStyleClass().add("layer-item-icon");
                    lblIcon.setMinWidth(28);
                    lblIcon.setAlignment(Pos.CENTER);
                    
                    lblNombre.getStyleClass().add("layer-item-text");
                    lblNombre.setMinWidth(0);
                    lblNombre.setPrefWidth(1);
                    lblNombre.setMaxWidth(Double.MAX_VALUE);
                    lblNombre.setEllipsisString("…");
                    HBox.setHgrow(lblNombre, Priority.ALWAYS);
                    
                    actions.setAlignment(Pos.CENTER_RIGHT);
                    actions.setMinWidth(Region.USE_PREF_SIZE);
                    // Ocultos por defecto: sólo aparecen en hover o cuando la capa está activa
                    actions.setVisible(false);
                    actions.managedProperty().bind(actions.visibleProperty());
                    
                    HBox content = new HBox(8, lblIcon, lblNombre, actions);
                    content.setAlignment(Pos.CENTER_LEFT);
                    content.setPadding(new Insets(0, 10, 0, 10));
                    HBox.setHgrow(content, Priority.ALWAYS);
                    
                    row.getChildren().addAll(activeBar, content);
                    row.setAlignment(Pos.CENTER_LEFT);
                    
                    hoverOverlay.getStyleClass().add("layer-hover-overlay");
                    hoverOverlay.setOpacity(0.0);
                    hoverOverlay.setMouseTransparent(true);
                    
                    selectedOverlay.getStyleClass().add("layer-selected-overlay");
                    selectedOverlay.setOpacity(0.0);
                    selectedOverlay.setMouseTransparent(true);
                    
                    card.getChildren().addAll(hoverOverlay, selectedOverlay, row);
                    card.getStyleClass().add("layer-item-card");
                    card.setPrefHeight(42);
                    card.setMinHeight(42);
                    
                    sep.getStyleClass().add("layer-item-separator");
                    sep.setPrefHeight(1);
                    // Margen izquierdo = activeBar(4) + padding izquierdo(12) = 16px, para alinear con el texto
                    VBox.setMargin(sep, new Insets(0, 12, 0, 44));
                    
                    cellLayout.getChildren().addAll(card, sep);
                    
                    hIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(150), hoverOverlay);
                    hIn.setToValue(0.6);
                    hOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(150), hoverOverlay);
                    hOut.setToValue(0.0);

                    // LÓGICA DE SELECCIÓN ROBUSTA (PseudoClass + Listener)
                    selectedProperty().addListener((obs, old, isSelected) -> {
                        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), isSelected);
                        
                        if (isSelected) {
                            selectedOverlay.setOpacity(1.0);
                            activeBar.setVisible(true);
                            activeBar.setOpacity(1.0);
                            actions.setVisible(true);
                            lblNombre.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
                            lblIcon.setStyle("-fx-text-fill: white;");
                            
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
                            selectedOverlay.setOpacity(0.0);
                            activeBar.setVisible(false);
                            actions.setVisible(false);
                            lblNombre.setStyle("-fx-font-weight: normal; -fx-text-fill: #a0a5cc;");
                            lblIcon.setStyle("-fx-text-fill: #a0a5cc;");
                            if (pulse != null) { pulse.stop(); pulse = null; }
                        }
                    });

                    card.setOnMouseEntered(e -> {
                        hIn.playFromStart();
                        if (!isSelected()) actions.setVisible(true);
                    });
                    card.setOnMouseExited(e -> {
                        if (!isSelected()) {
                            hOut.playFromStart();
                            actions.setVisible(false);
                        }
                    });
                }

                @Override
                protected void updateItem(Elemento item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        if (pulse != null) { pulse.stop(); pulse = null; }
                        // Limpieza radical para celdas vacías
                        selectedOverlay.setOpacity(0.0);
                        activeBar.setVisible(false);
                        actions.setVisible(false);
                    } else {
                        // Reset de hover (siempre al inicio)
                        hoverOverlay.setOpacity(0.0);
                        
                        // Actualizar contenido
                        String iconStr = "·";
                        if (item instanceof ImagenFondoElemento) iconStr = "⬚";
                        else if (item instanceof TextoElemento)  iconStr = "T";
                        else if (item instanceof ImagenElemento) iconStr = "▣";
                        else if (item instanceof ElementoQR)     iconStr = "⦀";
                        else iconStr = "⬒";
                        
                        lblIcon.setText(iconStr);
                        lblNombre.setText(item.toString());
                        
                        // Botones de acción (reordenar + eliminar)
                        actions.getChildren().clear();
                        if (item.isLocked()) {
                            Label lock = new Label("🔒");
                            lock.setStyle("-fx-font-size: 10px;");
                            actions.getChildren().add(lock);
                        }
                        if (!(item instanceof ImagenFondoElemento) && !item.isLocked()) {
                            Button btnUp = new Button("▲");
                            btnUp.getStyleClass().add("layer-action-btn");
                            btnUp.setOnAction(e -> {
                                int idx = currentProj.getElementosActuales().indexOf(item);
                                if (idx > 0) {
                                    java.util.Collections.swap(currentProj.getElementosActuales(), idx, idx - 1);
                                    if (onCanvasRedraw != null) onCanvasRedraw.run();
                                    rebuildLayersPanel(currentProj, item);
                                }
                            });
                            Button btnDown = new Button("▼");
                            btnDown.getStyleClass().add("layer-action-btn");
                            btnDown.setOnAction(e -> {
                                int idx = currentProj.getElementosActuales().indexOf(item);
                                if (idx < currentProj.getElementosActuales().size() - 1) {
                                    java.util.Collections.swap(currentProj.getElementosActuales(), idx, idx + 1);
                                    if (onCanvasRedraw != null) onCanvasRedraw.run();
                                    rebuildLayersPanel(currentProj, item);
                                }
                            });
                            Button btnDel = new Button("✕");
                            btnDel.getStyleClass().add("layer-action-btn-del");
                            btnDel.setOnAction(e -> {
                                currentProj.getElementosActuales().remove(item);
                                if (onCanvasRedraw != null) onCanvasRedraw.run();
                                rebuildLayersPanel(currentProj, null);
                            });
                            actions.getChildren().addAll(btnUp, btnDown, btnDel);
                        }

                        // Sincronizar estado visual inicial de la celda reciclada
                        boolean isSelected = isSelected();
                        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), isSelected);
                        
                        if (isSelected) {
                            selectedOverlay.setOpacity(1.0);
                            activeBar.setVisible(true);
                            activeBar.setOpacity(1.0);
                            actions.setVisible(true);
                            lblNombre.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
                            lblIcon.setStyle("-fx-text-fill: white;");
                        } else {
                            selectedOverlay.setOpacity(0.0);
                            activeBar.setVisible(false);
                            actions.setVisible(false);
                            lblNombre.setStyle("-fx-font-weight: normal; -fx-text-fill: #a0a5cc;");
                            lblIcon.setStyle("-fx-text-fill: #a0a5cc;");
                        }

                        // Menú contextual
                        cm.getItems().clear();
                        if (item instanceof ImagenFondoElemento) {
                            MenuItem mEdit = new MenuItem("Editar fondo...");
                            mEdit.setOnAction(e -> { if (onEditExternal != null) onEditExternal.accept((ImagenFondoElemento)item); });
                            cm.getItems().add(mEdit);
                        } else {
                            MenuItem mLock = new MenuItem(item.isLocked() ? "Desbloquear" : "Bloquear");
                            mLock.setOnAction(e -> { if (onToggleLock != null) onToggleLock.accept(item); });
                            MenuItem mDel = new MenuItem("Eliminar capa");
                            mDel.setStyle("-fx-text-fill: #ff5555;");
                            mDel.setOnAction(e -> {
                                currentProj.getElementosActuales().remove(item);
                                if (onCanvasRedraw != null) onCanvasRedraw.run();
                                rebuildLayersPanel(currentProj, null);
                            });
                            cm.getItems().addAll(mLock, new SeparatorMenuItem(), mDel);
                        }
                        setContextMenu(cm);
                        setGraphic(cellLayout);
                    }
                }
            });

            // Borrado rápido con teclado
            layersListView.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                    Elemento sel = layersListView.getSelectionModel().getSelectedItem();
                    if (sel != null && !(sel instanceof ImagenFondoElemento)) {
                        currentProj.getElementosActuales().remove(sel);
                        if (onCanvasRedraw != null) onCanvasRedraw.run();
                        rebuildLayersPanel(currentProj, null);
                    }
                }
            });

            layersListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (!isUpdatingSelection && onElementSelected != null) {
                    onElementSelected.accept(newVal);
                }
            });
        }

        panel.getChildren().addAll(lblCapas, layersListView);
        return panel;
    }

    /**
     * Helper para obtener la lista de capas completa (Fondo + Elementos) para la cara actual.
     */
    private ObservableList<Elemento> getCapasDeProyecto(Proyecto proyecto) {
        ObservableList<Elemento> capas = FXCollections.observableArrayList();
        if (proyecto != null) {
            ImagenFondoElemento fondo = proyecto.getFondoActual();
            if (fondo != null) {
                capas.add(fondo);
            }
            capas.addAll(proyecto.getElementosActuales());
        }
        return capas;
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
            lblValor.setCursor(javafx.scene.Cursor.HAND);
            lblValor.setTooltip(new Tooltip("Doble clic para editar"));
            
            // Contenedor para el valor (para poder cambiar entre Label y TextField)
            StackPane valorStack = new StackPane(lblValor);
            valorStack.setAlignment(Pos.CENTER_LEFT);

            // Doble click para editar
            lblValor.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    TextField txtEdit = new TextField(valor);
                    txtEdit.getStyleClass().add("dato-edit-field");
                    txtEdit.setMaxWidth(Double.MAX_VALUE);
                    
                    valorStack.getChildren().setAll(txtEdit);
                    txtEdit.requestFocus();
                    txtEdit.selectAll();

                    // Evitar múltiples commits
                    final boolean[] committed = {false};
                    
                    Runnable commit = () -> {
                        if (committed[0]) return;
                        committed[0] = true;
                        
                        String nuevoValor = txtEdit.getText().trim();
                        if (!nuevoValor.equals(valor)) {
                            datos.actualizarValorActual(columna, nuevoValor);
                            if (projectManager != null) {
                                projectManager.guardarFuenteDatosActual();
                            }
                            if (onCanvasRedraw != null) onCanvasRedraw.run();
                        }
                        lblValor.setText(nuevoValor.isEmpty() ? "—" : nuevoValor);
                        valorStack.getChildren().setAll(lblValor);
                    };

                    txtEdit.setOnAction(ev -> commit.run());
                    txtEdit.focusedProperty().addListener((obs, old, focus) -> {
                        if (!focus) commit.run();
                    });
                }
            });

            VBox campo = new VBox(2, lblColumna, valorStack);
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
    }

    private VBox buildProjectListPanel(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = new VBox(12);
        projectPanel.setPadding(new Insets(14, 12, 14, 12));
        VBox.setVgrow(projectPanel, Priority.ALWAYS);

        // Cabecera con filtro de categorías a la derecha
        Label lblTrabajos = new Label("Gestión de Trabajos");
        lblTrabajos.getStyleClass().add("panel-title");
        Label lblSubtitulo = new Label("Administración y exportación");
        lblSubtitulo.getStyleClass().add("panel-placeholder");
        VBox titulos = new VBox(2, lblTrabajos, lblSubtitulo);
        HBox.setHgrow(titulos, Priority.ALWAYS);

        // Botón de filtro de categorías
        btnFiltro = crearBotonFiltro(projects);

        HBox header = new HBox(titulos, btnFiltro);
        header.setAlignment(Pos.CENTER_LEFT);

        // --- BARRA DE BÚSQUEDA ---
        proyectosFiltrados = javafx.collections.FXCollections.observableArrayList();
        actualizarListaFiltrada(projects);

        TextField txtBusqueda = new TextField();
        txtBusqueda.setPromptText("Buscar por nombre, cliente...");
        txtBusqueda.getStyleClass().add("search-field");
        txtBusqueda.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: white; -fx-prompt-text-fill: #5a6090; -fx-background-radius: 12; -fx-padding: 8 12; -fx-font-size: 11.5px;");
        txtBusqueda.textProperty().addListener((obs, old, val) -> {
            filtroTexto = val;
            actualizarListaFiltrada(projects);
        });
        txtBusqueda.setText(filtroTexto); // Restaurar si venimos de otro modo

        // Lista de proyectos — filtrada o completa
        ListView<Proyecto> listProyectos = new ListView<>(proyectosFiltrados);
        listProyectos.getStyleClass().add("project-list");
        VBox.setVgrow(listProyectos, Priority.ALWAYS);
        // Vinculamos el maxHeight al panel padre para que el ListView no crezca infinito
        // y active su scrollbar cuando el contenido supera el espacio disponible
        listProyectos.maxHeightProperty().bind(leftPanel.heightProperty().subtract(200));

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
                    lblName.setMaxWidth(180); // Límite para no empujar iconos
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
                    
                    // Botón de OPCIONES (⋯) - Solo visible en hover
                    Label btnOptions = new Label("⋯");
                    btnOptions.setStyle("-fx-text-fill: #a0a5cc; -fx-cursor: hand; -fx-font-size: 26px; -fx-font-weight: bold; -fx-padding: 0;");
                    btnOptions.setVisible(false); 
                    
                    btnOptions.setPickOnBounds(true); // Hacer que todo el área del icono sea clicable
                    btnOptions.setOnMousePressed(e -> {
                        mostrarProjectOptionsMenu(item, btnOptions, projects);
                        e.consume(); // Evitar que el click seleccione la fila
                    });

                    // Agrupar botón ⋯ y etiqueta CR80 en vertical ( ⋯ centrado encima de CR80 )
                    VBox rightActionBox = new VBox(-6); // Espaciado ajustado para subir los puntos
                    rightActionBox.setAlignment(Pos.CENTER); // Centrado relativo
                    rightActionBox.getChildren().addAll(btnOptions, lblBadge);
                    VBox.setMargin(btnOptions, new Insets(0, 0, 4, 0)); // Margen extra para separar de la etiqueta abajo

                    textAndBadge.getChildren().addAll(textContainer, spacer, rightActionBox);
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
                    javafx.animation.FadeTransition hoverIn  = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_FAST), hoverOverlay);
                    hoverIn.setToValue(0.6);
                    javafx.animation.FadeTransition hoverOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), hoverOverlay);
                    hoverOut.setToValue(0.0);

                    // ── Estado seleccionado ───────────────────────────────────────
                    if (isSelected()) {
                        // Ya seleccionado: aparecer con fade-in suave del selectedOverlay
                        javafx.animation.FadeTransition selectIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_SLOW), selectedOverlay);
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

                    card.setOnMouseEntered(e -> { 
                        if (!isSelected()) hoverIn.playFromStart(); 
                        btnOptions.setVisible(true);
                    });
                    card.setOnMouseExited(e  -> { 
                        if (!isSelected()) hoverOut.playFromStart(); 
                        btnOptions.setVisible(false);
                    });
                }
            }

        });

        if (currentProject != null) {
            // Buscamos el proyecto equivalente por ID en la lista filtrada para asegurar la selección visual
            Proyecto equivalente = proyectosFiltrados.stream()
                    .filter(p -> p.getId() == currentProject.getId())
                    .findFirst()
                    .orElse(null);
            
            if (equivalente != null) {
                // Usamos runLater para asegurar que el ListView esté listo para procesar la selección
                javafx.application.Platform.runLater(() -> {
                    listProyectos.getSelectionModel().select(equivalente);
                    listProyectos.scrollTo(equivalente);
                });
            }
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

        // Panel de proyectos ocultos
        panelOcultos = new HBox(5);
        panelOcultos.setAlignment(Pos.CENTER);
        panelOcultos.setPadding(new Insets(4, 0, 8, 0));
        panelOcultos.setVisible(false);
        panelOcultos.setManaged(false);

        lblOcultosText = new Label();
        lblOcultosText.setStyle("-fx-text-fill: #a0a5cc; -fx-font-size: 11px;");

        Hyperlink linkVerTodos = new Hyperlink("Ver todos");
        linkVerTodos.setStyle("-fx-text-fill: #6c63ff; -fx-font-size: 11px; -fx-padding: 0; -fx-border-color: transparent; -fx-underline: false;");
        linkVerTodos.setOnAction(e -> {
            filtroActivo.clear();
            if (etiquetasManager != null) etiquetasManager.setFiltroActivo(filtroActivo);
            actualizarListaFiltrada(projects);
            actualizarIconoFiltro(btnFiltro);
        });
        this.btnFiltro = btnFiltro;

        panelOcultos.getChildren().addAll(lblOcultosText, new Label("-"), linkVerTodos);
        // Ajustar color del guion
        ((Label)panelOcultos.getChildren().get(1)).setStyle("-fx-text-fill: #5a6090; -fx-font-size: 11px;");

        // Cabecera dinámica de filtro
        filtroInfoBox = new HBox(8);
        filtroInfoBox.setAlignment(Pos.TOP_LEFT);
        filtroInfoBox.setPadding(new Insets(6, 10, 6, 10));
        filtroInfoBox.setVisible(false);
        filtroInfoBox.setManaged(false);
        filtroInfoBox.setStyle("-fx-background-color: rgba(108, 99, 255, 0.05); -fx-border-color: rgba(108, 99, 255, 0.15); -fx-border-width: 0 0 1 0;");
        
        lblFiltroActual = new Label();
        lblFiltroActual.setStyle("-fx-text-fill: #6c63ff; -fx-font-weight: bold; -fx-font-size: 11px;");
        lblFiltroActual.setWrapText(true);
        HBox.setHgrow(lblFiltroActual, Priority.ALWAYS);

        Label lblIcon = new Label("📂");
        lblIcon.setStyle("-fx-font-size: 12px; -fx-padding: 2 0 0 0;"); // Un poco de padding arriba para centrar con la primera línea
        
        filtroInfoBox.getChildren().addAll(lblIcon, lblFiltroActual);

        // Refrescar estado inicial
        actualizarVisibilidadOcultos(projects);

        projectPanel.getChildren().addAll(header, txtBusqueda, filtroInfoBox, listProyectos, panelOcultos, btnNuevoCR80);
        
        // Sincronizar estado inicial (especialmente útil al volver del modo diseño)
        actualizarListaFiltrada(projects);
        
        return projectPanel;
    }

    // =========================================================
    // Filtro de Categorías
    // =========================================================

    /** Construye la lista filtrada según las categorías activas. */
    private ObservableList<Proyecto> construirListaFiltrada(ObservableList<Proyecto> todos) {
        ObservableList<Proyecto> filtrados = FXCollections.observableArrayList();
        if (filtroActivo.isEmpty() || etiquetasManager == null) {
            filtrados.addAll(todos);
            return filtrados;
        }
        for (Proyecto p : todos) {
            for (String id : filtroActivo) {
                if (p.getEtiquetaIds().contains(id)) {
                    filtrados.add(p);
                    break;
                }
            }
        }
        return filtrados;
    }

    /** Crea el botón de filtro y su popup. */
    private Button crearBotonFiltro(ObservableList<Proyecto> todosLosProyectos) {
        Button btn = new Button();
        this.btnFiltro = btn; // Guardar referencia
        actualizarIconoFiltro(btn);
        btn.getStyleClass().add("filter-btn");
        btn.setTooltip(new Tooltip("Filtrar por categoría"));

        btn.setOnAction(e -> {
            if (filtroPopup != null && filtroPopup.isShowing()) {
                if (!filtroPopup.getContent().isEmpty() && filtroPopup.getContent().get(0) instanceof VBox) {
                    VBox content = (VBox) filtroPopup.getContent().get(0);
                    content.setMouseTransparent(true);
                    content.setCache(true);
                    content.setCacheHint(javafx.scene.CacheHint.SPEED);
                    javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_FAST), content);
                    ft.setToValue(0);
                    ft.setOnFinished(ev -> filtroPopup.hide());
                    ft.play();
                } else {
                    filtroPopup.hide();
                }
            } else {
                mostrarFiltroPopup(btn, todosLosProyectos);
            }
        });
        return btn;
    }

    private void actualizarIconoFiltro(Button btn) {
        if (btn == null) return;
        int activas = filtroActivo.size();
        if (activas > 0) {
            btn.setText("🏷️ " + activas);
            btn.setStyle("-fx-text-fill: #6c63ff; -fx-font-weight: bold;");
        } else {
            btn.setText("🏷️");
            btn.setStyle("");
        }
    }

    /** Muestra el popup inline con las opciones de filtro. */
    private void mostrarFiltroPopup(Button anchor, ObservableList<Proyecto> todosLosProyectos) {
        if (filtroPopup != null && filtroPopup.isShowing()) {
            filtroPopup.hide();
        }
        filtroPopup = new javafx.stage.Popup();
        filtroPopup.setAutoHide(true);

        VBox contenido = new VBox(8);
        contenido.getStyleClass().add("filter-popup");
        contenido.setPadding(new Insets(12));
        contenido.setStyle("-fx-background-color: #1a1b2e; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 12, 0, 0, 4);");
        contenido.setPrefWidth(210);
        contenido.setOpacity(0);
        contenido.setCache(true);
        contenido.setCacheHint(javafx.scene.CacheHint.SPEED);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), contenido);
        ft.setToValue(1);
        ft.play();

        // --- Opción: TODOS ---
        ToggleButton btnTodos = new ToggleButton("★ Todos los proyectos");
        btnTodos.setMaxWidth(Double.MAX_VALUE);
        btnTodos.getStyleClass().add("filter-option-btn");
        btnTodos.setSelected(filtroActivo.isEmpty());
        btnTodos.setOnAction(ev -> {
            filtroActivo.clear();
            if (etiquetasManager != null) etiquetasManager.setFiltroActivo(filtroActivo);
            actualizarListaFiltrada(todosLosProyectos);
            
            // Animación de salida antes de esconder
            contenido.setMouseTransparent(true);
            javafx.animation.FadeTransition hideFt = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), contenido);
            hideFt.setToValue(0);
            hideFt.setOnFinished(e -> filtroPopup.hide());
            hideFt.play();
        });
        contenido.getChildren().add(btnTodos);

        if (etiquetasManager != null && !etiquetasManager.getAll().isEmpty()) {
            contenido.getChildren().add(new Separator());
            Label lblCats = new Label("CATEGORÍAS");
            lblCats.setStyle("-fx-text-fill: #5a6090; -fx-font-size: 10px; -fx-font-weight: bold;");
            contenido.getChildren().add(lblCats);

            for (Etiqueta cat : etiquetasManager.getAll()) {
                HBox fila = new HBox(8);
                fila.setAlignment(Pos.CENTER_LEFT);

                Circle dot = new Circle(5);
                try { dot.setFill(Color.web(cat.getColor())); } catch (Exception ex) { dot.setFill(Color.GRAY); }

                CheckBox chk = new CheckBox(cat.getNombre());
                chk.setSelected(filtroActivo.contains(cat.getId()));
                chk.setMaxWidth(Double.MAX_VALUE);
                chk.setStyle("-fx-text-fill: #c8cde8;");
                HBox.setHgrow(chk, Priority.ALWAYS);

                chk.selectedProperty().addListener((obs, old, val) -> {
                    if (val) {
                        if (!filtroActivo.contains(cat.getId())) filtroActivo.add(cat.getId());
                    } else {
                        filtroActivo.remove(cat.getId());
                    }
                    if (etiquetasManager != null) etiquetasManager.setFiltroActivo(filtroActivo);
                    actualizarListaFiltrada(todosLosProyectos);
                    // Desmarcar "Todos" si hay alguna categoría activa
                    btnTodos.setSelected(filtroActivo.isEmpty());
                });

                Label btnDelete = new Label("✕");
                btnDelete.setTooltip(new Tooltip("Eliminar categoría"));
                btnDelete.setStyle("-fx-text-fill: #E74C6C; -fx-cursor: hand; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 0 4px;");
                btnDelete.setOnMouseClicked(ev -> {
                    if (omitirConfirmacionBorradoEtiqueta) {
                        ejecutarEliminacionEtiqueta(cat, anchor, todosLosProyectos);
                        return;
                    }

                    Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Eliminar Categoría");
                    alert.setHeaderText(null);
                    
                    String msg = "¿Seguro que quieres eliminar la categoría '" + cat.getNombre() + "'?\n\n"
                               + "ADVERTENCIA: Los proyectos que usen esta etiqueta dejarán de estar asociados a ella y no podrás filtrarlos hasta que les asignes una nueva.";

                    Label lblMsg = new Label(msg);
                    lblMsg.setWrapText(true);
                    lblMsg.setMaxWidth(380);
                    lblMsg.setStyle("-fx-line-spacing: 5;");
                    
                    CheckBox chkOmitir = new CheckBox("No volver a mostrar este mensaje");
                    chkOmitir.setStyle("-fx-font-size: 11px; -fx-text-fill: #5a6090;");
                    
                    VBox alertContent = new VBox(15, lblMsg, chkOmitir);
                    alertContent.setPrefWidth(400);
                    alert.getDialogPane().setContent(alertContent);

                    if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        if (chkOmitir.isSelected()) omitirConfirmacionBorradoEtiqueta = true;
                        ejecutarEliminacionEtiqueta(cat, anchor, todosLosProyectos);
                    }
                });

                fila.getChildren().addAll(dot, chk, btnDelete);
                contenido.getChildren().add(fila);
            }
        }

        contenido.getChildren().add(new Separator());
        Button btnNuevaCat = new Button("+ Nueva categoría");
        btnNuevaCat.getStyleClass().add("btn-dialog-action");
        btnNuevaCat.setMaxWidth(Double.MAX_VALUE);
        btnNuevaCat.setOnAction(ev -> {
            TextInputDialog dlg = new TextInputDialog();
            com.tpsstudio.util.AlertHelper.applyStyle(dlg);
            dlg.initOwner(anchor.getScene().getWindow());
            dlg.setTitle("Nueva Categoría");
            dlg.setHeaderText(null);
            dlg.setContentText("Nombre:");
            dlg.showAndWait().ifPresent(nombre -> {
                if (!nombre.isBlank() && etiquetasManager != null) {
                    etiquetasManager.crear(nombre, null);
                    filtroPopup.hide();
                    // Reabrir para refrescar
                    mostrarFiltroPopup(anchor, todosLosProyectos);
                }
            });
        });
        contenido.getChildren().add(btnNuevaCat);

        filtroPopup.getContent().add(contenido);

        javafx.geometry.Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        // Posicionar a la derecha del botón, fuera de la lista
        filtroPopup.show(anchor, b.getMaxX() + 10, b.getMinY() - 5);
    }

    private void ejecutarEliminacionEtiqueta(Etiqueta cat, Button anchor, ObservableList<Proyecto> todosLosProyectos) {
        if (etiquetasManager != null && etiquetasManager.eliminar(cat.getId())) {
            filtroActivo.remove(cat.getId());
            etiquetasManager.setFiltroActivo(filtroActivo);
            actualizarListaFiltrada(todosLosProyectos);
            if (filtroPopup != null) filtroPopup.hide();
            mostrarFiltroPopup(anchor, todosLosProyectos);
        }
    }

    /** Actualiza la lista filtrada en el ListView activo y la etiqueta de proyectos ocultos. */
    private void actualizarListaFiltrada(ObservableList<Proyecto> todos) {
        if (proyectosFiltrados == null) return;
        proyectosFiltrados.clear();
        
        String term = filtroTexto.toLowerCase().trim();

        for (Proyecto p : todos) {
            // 1. Filtrado por categorías
            boolean coincideCategoria = filtroActivo.isEmpty();
            if (!coincideCategoria) {
                for (String id : filtroActivo) {
                    if (p.getEtiquetaIds().contains(id)) {
                        coincideCategoria = true;
                        break;
                    }
                }
            }

            // 2. Filtrado por texto (si coincide categoría)
            if (coincideCategoria) {
                if (term.isEmpty()) {
                    proyectosFiltrados.add(p);
                } else {
                    String nombre = p.getNombre().toLowerCase();
                    String cliente = "";
                    if (p.getMetadata() != null && p.getMetadata().getClienteInfo() != null) {
                        String emp = p.getMetadata().getClienteInfo().getNombreEmpresa();
                        if (emp != null) cliente = emp.toLowerCase();
                    }
                    
                    if (nombre.contains(term) || cliente.contains(term)) {
                        proyectosFiltrados.add(p);
                    }
                }
            }
        }
        
        // Actualizar cabecera de filtro
        if (filtroInfoBox != null && lblFiltroActual != null && etiquetasManager != null) {
            if (filtroActivo.isEmpty()) {
                filtroInfoBox.setVisible(false);
                filtroInfoBox.setManaged(false);
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < filtroActivo.size(); i++) {
                    String id = filtroActivo.get(i);
                    com.tpsstudio.model.project.Etiqueta et = etiquetasManager.findById(id);
                    if (et != null) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(et.getNombre());
                    }
                }
                lblFiltroActual.setText(sb.toString().toUpperCase());
                filtroInfoBox.setVisible(true);
                filtroInfoBox.setManaged(true);
            }
        }
        actualizarVisibilidadOcultos(todos);
        actualizarIconoFiltro(btnFiltro);
    }

    private void duplicarProyectoUI(Proyecto item, ObservableList<Proyecto> projects) {
        if (projectManager == null) return;
        
        // Confirmación rápida
        Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Duplicar Proyecto");
        alert.setHeaderText("¿Quieres crear una copia de este proyecto?");
        alert.setContentText("Se creará una nueva carpeta con todos los archivos de '" + item.getNombre() + "'.");
        
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            Proyecto copia = projectManager.duplicarProyecto(item);
            if (copia != null) {
                actualizarListaFiltrada(projects);
            }
        }
    }

    private void eliminarProyectoUI(Proyecto item, ObservableList<Proyecto> projects) {
        if (projectManager == null) return;
        
        Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Eliminar del Historial");
        alert.setHeaderText("¿Quitar '" + item.getNombre() + "' de la lista?");
        alert.setContentText("Esta acción no borrará los archivos de tu ordenador, solo ocultará el proyecto del historial del programa.");
        
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            projectManager.eliminarProyecto(item);
            javafx.application.Platform.runLater(() -> actualizarListaFiltrada(projects));
        }
    }

    private void mostrarProjectOptionsMenu(Proyecto item, Node anchor, ObservableList<Proyecto> projects) {
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);

        VBox content = new VBox(0);
        content.setStyle("-fx-background-color: #1a1b2e; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 15, 0, 0, 6); -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 10; -fx-padding: 5;");
        content.setMinWidth(140);
        
        String[] labels = {"Editar", "Duplicar", "Eliminar"};
        String[] icons = {"✎", "❐", "✖"}; // Símbolos más compatibles que emojis
        String[] colors = {"#c8cde8", "#c8cde8", "#ff6b6b"};
        
        for (int i = 0; i < labels.length; i++) {
            final String labelText = labels[i];
            final String iconText = icons[i];
            final String color = colors[i];
            
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 15, 8, 15));
            row.setStyle("-fx-cursor: hand; -fx-background-radius: 6;");
            
            Label lblIcon = new Label(iconText);
            lblIcon.setPrefWidth(22); // Ancho fijo para alinear textos
            lblIcon.setAlignment(Pos.CENTER);
            lblIcon.setStyle("-fx-text-fill: " + color + "; -fx-opacity: 0.9; -fx-font-size: 16px; -fx-font-weight: bold;");
            
            Label lblText = new Label(labelText);
            lblText.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: 500;");
            
            row.getChildren().addAll(lblIcon, lblText);
            
            row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: rgba(108, 99, 255, 0.15); -fx-cursor: hand; -fx-background-radius: 6;"));
            row.setOnMouseExited(e -> row.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;"));
            
            row.setOnMouseClicked(e -> {
                popup.hide();
                if (labelText.equals("Editar")) {
                     if (onEditProject != null) onEditProject.accept(item);
                } else if (labelText.equals("Duplicar")) {
                     duplicarProyectoUI(item, projects);
                } else if (labelText.equals("Eliminar")) {
                     eliminarProyectoUI(item, projects);
                }
            });
            
            content.getChildren().add(row);
            if (i == 1) { // Separador antes de eliminar
                javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
                sep.setPadding(new Insets(4, 0, 4, 0));
                sep.setOpacity(0.1);
                content.getChildren().add(sep);
            }
        }

        popup.getContent().add(content);
        
        // Transición de entrada suave
        content.setOpacity(0);
        content.setCache(true);
        content.setCacheHint(javafx.scene.CacheHint.SPEED);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), content);
        ft.setToValue(1);
        ft.play();

        // Posicionamiento alineado con el borde del menú lateral (referencia absoluta del panel)
        // Offset 0 para que pegue exactamente al borde
        javafx.geometry.Point2D sidebarEdge = leftPanel.localToScreen(leftPanel.getWidth(), 0);
        javafx.geometry.Point2D anchorPos = anchor.localToScreen(0, -40);
        
        if (sidebarEdge != null && anchorPos != null) {
            popup.show(anchor.getScene().getWindow(), sidebarEdge.getX(), anchorPos.getY());
        }
    }

    private void actualizarVisibilidadOcultos(ObservableList<Proyecto> todos) {
        if (panelOcultos == null || lblOcultosText == null) return;
        
        int ocultos = todos.size() - proyectosFiltrados.size();
        if (ocultos > 0) {
            lblOcultosText.setText(ocultos + (ocultos == 1 ? " proyecto oculto" : " proyectos ocultos"));
            panelOcultos.setStyle("-fx-background-color: rgba(108, 99, 255, 0.08); -fx-background-radius: 4; -fx-padding: 6 0;");
            panelOcultos.setVisible(true);
            panelOcultos.setManaged(true);
            
            // Si estamos en Producción, recordar sutilmente cada cierto tiempo
            if (currentMode == AppMode.PRODUCTION) {
                iniciarRecordatorioSutil();
            }
        } else {
            if (recordatorioTimer != null) recordatorioTimer.stop();
            panelOcultos.setVisible(false);
            panelOcultos.setManaged(false);
        }
    }

    private javafx.animation.Timeline recordatorioTimer;
    private void iniciarRecordatorioSutil() {
        if (recordatorioTimer != null) recordatorioTimer.stop();
        
        // Cada 3 minutos, un pequeño parpadeo lila en la barra de estado
        recordatorioTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.minutes(3), ev -> {
            if (panelOcultos != null && panelOcultos.isVisible() && currentMode == AppMode.PRODUCTION) {
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), panelOcultos);
                ft.setFromValue(1.0);
                ft.setToValue(0.4);
                ft.setCycleCount(4);
                ft.setAutoReverse(true);
                ft.play();
            }
        }));
        recordatorioTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        recordatorioTimer.play();
    }

    /**
     * Helper interno por si se quiere que ModeManager resuelva el diálogo.
     * Si usas callbacks (lo ideal), este método puede quedarse sin usar.
     */
    private void abrirDialogoEditarProyecto(Proyecto proyecto) {
        if (projectManager == null)
            return;

        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto, null, etiquetasManager);
        Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            projectManager.eliminarProyecto(proyecto);
            return;
        }

        if (resultado.isPresent()) {
            projectManager.editarProyecto(proyecto, resultado.get());
        }
    }
}
