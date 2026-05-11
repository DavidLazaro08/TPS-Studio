package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ElementoCodigo;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.enums.TipoCodigo;
import com.tpsstudio.model.project.Etiqueta;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.service.EtiquetasManager;
import com.tpsstudio.service.ProjectManager;
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
import java.util.List;
import java.util.ArrayList;

import com.tpsstudio.view.managers.design.LayersPanelManager;
import com.tpsstudio.view.managers.design.ToolboxManager;
import com.tpsstudio.view.managers.design.ProductionViewManager;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.css.PseudoClass;

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

    // Gestores inyectados mediante setter
    private EtiquetasManager etiquetasManager;
    private ProjectManager projectManager;

    // Contenedores físicos de UI (se rellenan dinámicamente)
    private final VBox leftPanel;
    private final VBox rightPanel;

    // Controlador del panel de propiedades (solo aplica en modo Diseño)
    private final PropertiesPanelController propertiesPanelController;

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
    private Consumer<com.tpsstudio.model.elements.FormaElemento.TipoForma> onAddShape;
    private Consumer<TipoCodigo> onAddCode;

    // Se guarda para poder refrescar solo la parte de "Capas" sin rehacer todo el
    // panel izquierdo
    private VBox layersPanel;
    private ListView<Elemento> layersListView;

    // Panel de datos variables (null si no hay fuente de datos activa)
    private VBox datosPanel;

    // Indicador de qué "pestaña" del panel derecho está activa
    private boolean isPropertiesActive = true;
    private javafx.scene.Node propertiesNode;
    private javafx.scene.Node datosNode;

    // Gestores delegados
    private LayersPanelManager layersPanelManager;
    private ToolboxManager toolboxManager;
    private ProductionViewManager productionViewManager;

    // Botón de validación para aplicar animaciones
    private Button btnValidar;
    private javafx.animation.Timeline periodicReminder;
    private javafx.animation.Timeline pulseAnimation;

    public ModeManager(VBox leftPanel, VBox rightPanel, PropertiesPanelController propertiesPanelController) {
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.propertiesPanelController = propertiesPanelController;
        this.currentMode = AppMode.DESIGN;

        // Inicializar gestores delegados
        this.layersPanelManager = new LayersPanelManager(
            leftPanel,
            () -> { if (onCanvasRedraw != null) onCanvasRedraw.run(); },
            elem -> { if (onElementSelected != null) onElementSelected.accept(elem); },
            fondo -> { if (onEditExternal != null) onEditExternal.accept(fondo); },
            elem -> { if (onToggleLock != null) onToggleLock.accept(elem); }
        );

        this.toolboxManager = new ToolboxManager(
            () -> { if (onAddText != null) onAddText.run(); },
            () -> { if (onAddImage != null) onAddImage.run(); },
            () -> { if (onAddBackground != null) onAddBackground.run(); },
            code -> { if (onAddCode != null) onAddCode.accept(code); },
            shape -> { if (onAddShape != null) onAddShape.accept(shape); },
            () -> { if (onValidateDesign != null) onValidateDesign.run(); }
        );

        this.productionViewManager = new ProductionViewManager(
            leftPanel,
            etiquetasManager,
            projectManager,
            proj -> { if (onProjectSelected != null) onProjectSelected.accept(proj); },
            proj -> { if (onEditProject != null) onEditProject.accept(proj); },
            () -> { if (onNewCR80 != null) onNewCR80.run(); }
        );
    }

    /** Inyecta el gestor de categorías después de la construcción. */
    public void setEtiquetasManager(EtiquetasManager etiquetasManager) {
        this.etiquetasManager = etiquetasManager;
        if (productionViewManager != null) {
            productionViewManager.setEtiquetasManager(etiquetasManager);
            if (etiquetasManager != null) {
                productionViewManager.setFiltroActivo(etiquetasManager.getFiltroActivo());
            }
        }
    }

    public void setProjectManager(ProjectManager projectManager) {
        this.projectManager = projectManager;
        if (productionViewManager != null) {
            productionViewManager.setProjectManager(projectManager);
        }
    }

    // ===================== SETTERS DE CALLBACKS =====================

    public void setOnAddText(Runnable callback) { this.onAddText = callback; }
    public void setOnAddImage(Runnable callback) { this.onAddImage = callback; }
    public void setOnAddBackground(Runnable callback) { this.onAddBackground = callback; }
    public void setOnNewCR80(Runnable callback) { this.onNewCR80 = callback; }
    public void setOnExport(Runnable callback) { this.onExport = callback; }
    public void setOnPrint(Runnable callback) { this.onPrint = callback; }

    public void setOnElementSelected(Consumer<Elemento> callback) { this.onElementSelected = callback; }
    public void setOnProjectSelected(Consumer<Proyecto> callback) { this.onProjectSelected = callback; }
    public void setOnEditProject(Consumer<Proyecto> callback) { this.onEditProject = callback; }
    public void setOnEditExternal(Consumer<ImagenFondoElemento> callback) { this.onEditExternal = callback; }
    public void setOnReload(Consumer<ImagenFondoElemento> callback) { this.onReload = callback; }
    public void setOnToggleLock(Consumer<Elemento> callback) { this.onToggleLock = callback; }
    public void setOnAddShape(Consumer<com.tpsstudio.model.elements.FormaElemento.TipoForma> callback) { this.onAddShape = callback; }
    public void setOnAddCode(Consumer<TipoCodigo> callback) { this.onAddCode = callback; }
    public void setOnValidateDesign(Runnable callback) { this.onValidateDesign = callback; }
    public void setOnCanvasRedraw(Runnable callback) { this.onCanvasRedraw = callback; }

    // ===================== MODO ACTUAL =====================

    public AppMode getCurrentMode() {
        return currentMode;
    }

    public void switchMode(AppMode newMode, Proyecto proyecto, Elemento selectedElement,
            ObservableList<Proyecto> projects) {
        this.currentMode = newMode;

        leftPanel.getChildren().clear();
        if (newMode == AppMode.DESIGN) {
            rightPanel.getChildren().clear();
            buildDesignModePanels(proyecto, selectedElement);
        } else {
            buildProductionModePanels(projects, proyecto);
        }

        AnimationHelper.applyFadeTransition(leftPanel, 550, 0.3, 1.0);
        AnimationHelper.applyFadeTransition(rightPanel, 550, 0.3, 1.0);

        if (onCanvasRedraw != null) onCanvasRedraw.run();
    }

    private void buildDesignModePanels(Proyecto proyecto, Elemento selectedElement) {
        VBox toolbox = toolboxManager.buildToolboxPanel();
        this.btnValidar = toolboxManager.getBtnValidar();
        
        layersPanel = layersPanelManager.buildLayersPanel(proyecto, selectedElement);
        this.layersListView = layersPanelManager.getLayersListView();
        leftPanel.getChildren().addAll(toolbox, new Separator(), layersPanel);

        refreshPropertiesPanel(selectedElement, proyecto);

        if (projectManager != null && projectManager.getFuenteDatos() != null) {
            datosPanel = buildDatosVariablesPanel(projectManager.getFuenteDatos(), proyecto);
            VBox.setVgrow(datosPanel, Priority.ALWAYS);
            this.datosNode = datosPanel;
        } else {
            datosPanel = buildEmptyDatosVariablesPanel(proyecto);
            this.datosNode = datosPanel;
        }

        rightPanel.getChildren().setAll(isPropertiesActive ? propertiesNode : datosNode);
    }

    private void buildProductionModePanels(ObservableList<Proyecto> projects, Proyecto currentProject) {
        productionViewManager.buildProductionModePanels(projects, currentProject);
    }

    public void setRightPanelTabActiva(boolean isProperties) {
        if (this.isPropertiesActive == isProperties) return;
        this.isPropertiesActive = isProperties;
        if (currentMode == AppMode.DESIGN) {
            Node targetNode = isPropertiesActive ? propertiesNode : datosNode;
            if (targetNode != null) {
                VBox.setVgrow(targetNode, Priority.ALWAYS);
                rightPanel.getChildren().setAll(targetNode);
                AnimationHelper.applyFadeTransition(rightPanel, 300, 0.6, 1.0);
            }
        }
    }

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

    public void refreshLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        layersPanelManager.refreshLayersPanel(proyecto, selectedElement);
    }

    public void rebuildLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        layersPanelManager.rebuildLayersPanel(proyecto, selectedElement, layersPanel);
        this.layersPanel = (VBox) layersPanelManager.getLayersListView().getParent(); 
        this.layersListView = layersPanelManager.getLayersListView();
    }

    // ===================== DATOS VARIABLES (Legacy - Pendiente Fase 4) =====================

    private VBox buildDatosVariablesPanel(FuenteDatos datos, Proyecto proyecto) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label lblTitulo = new Label(datos.getNombreArchivo());
        lblTitulo.getStyleClass().add("panel-title");
        lblTitulo.setMaxWidth(Double.MAX_VALUE);

        Button btnCambiarBD = new Button("⚙ Cambiar BD...");
        btnCambiarBD.getStyleClass().add("toolbox-btn");
        btnCambiarBD.setMaxWidth(Double.MAX_VALUE);
        btnCambiarBD.setOnAction(e -> { if (onEditProject != null) onEditProject.accept(proyecto); });

        Label lblContador = new Label(calcularContador(datos));
        lblContador.getStyleClass().add("toolbar-label");

        Button btnAnterior = new Button("◄ Anterior");
        btnAnterior.getStyleClass().add("toolbox-btn");
        btnAnterior.setMaxWidth(Double.MAX_VALUE);
        btnAnterior.setDisable(!datos.tieneRegistros() || datos.getIndiceActual() <= 0);

        Button btnSiguiente = new Button("Siguiente ►");
        btnSiguiente.getStyleClass().add("toolbox-btn");
        btnSiguiente.setMaxWidth(Double.MAX_VALUE);
        btnSiguiente.setDisable(!datos.tieneRegistros() || datos.getIndiceActual() >= datos.getTotalRegistros() - 1);

        HBox navBox = new HBox(8, btnAnterior, btnSiguiente);
        HBox.setHgrow(btnAnterior, Priority.ALWAYS);
        HBox.setHgrow(btnSiguiente, Priority.ALWAYS);

        VBox vistaRegistro = construirVistaRegistro(datos);
        ScrollPane scrollRegistro = new ScrollPane(vistaRegistro);
        scrollRegistro.setFitToWidth(true);
        scrollRegistro.getStyleClass().add("panel-scroll-view");
        VBox.setVgrow(scrollRegistro, Priority.ALWAYS);

        btnAnterior.setOnAction(e -> {
            datos.anterior();
            lblContador.setText(calcularContador(datos));
            btnAnterior.setDisable(datos.getIndiceActual() <= 0);
            btnSiguiente.setDisable(datos.getIndiceActual() >= datos.getTotalRegistros() - 1);
            actualizarVistaRegistro(vistaRegistro, datos);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        btnSiguiente.setOnAction(e -> {
            datos.siguiente();
            lblContador.setText(calcularContador(datos));
            btnAnterior.setDisable(datos.getIndiceActual() <= 0);
            btnSiguiente.setDisable(datos.getIndiceActual() >= datos.getTotalRegistros() - 1);
            actualizarVistaRegistro(vistaRegistro, datos);
            if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        panel.getChildren().addAll(lblTitulo, btnCambiarBD, new Separator(), lblContador, navBox, new Separator(), scrollRegistro);
        return panel;
    }

    private VBox construirVistaRegistro(FuenteDatos datos) {
        VBox contenedor = new VBox(6);
        contenedor.setPadding(new Insets(4, 0, 4, 0));
        rellenarVistaRegistro(contenedor, datos);
        return contenedor;
    }

    private void actualizarVistaRegistro(VBox contenedor, FuenteDatos datos) {
        contenedor.getChildren().clear();
        rellenarVistaRegistro(contenedor, datos);
    }

    private void rellenarVistaRegistro(VBox contenedor, FuenteDatos datos) {
        Map<String, String> registro = datos.getRegistroActual();
        if (registro == null) {
            contenedor.getChildren().add(new Label("(sin registros)"));
            return;
        }
        for (String columna : datos.getColumnas()) {
            String valor = registro.getOrDefault(columna, "");
            Label lblCol = new Label(columna);
            lblCol.getStyleClass().add("dato-columna");
            Label lblVal = new Label(valor.isEmpty() ? "—" : valor);
            lblVal.getStyleClass().add("dato-valor");
            lblVal.setWrapText(true);
            contenedor.getChildren().add(new VBox(2, lblCol, lblVal));
        }
    }

    private VBox buildEmptyDatosVariablesPanel(Proyecto proyecto) {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(30));
        panel.setAlignment(Pos.CENTER);
        Label lbl = new Label("No hay base de datos vinculada");
        lbl.getStyleClass().add("panel-placeholder");
        Button btn = new Button("+ Vincular Base de Datos");
        btn.getStyleClass().add("primary-btn");
        btn.setOnAction(e -> { if (onEditProject != null) onEditProject.accept(proyecto); });
        panel.getChildren().addAll(lbl, btn);
        return panel;
    }

    private String calcularContador(FuenteDatos datos) {
        if (!datos.tieneRegistros()) return "(sin registros)";
        return "Registro " + datos.getPosicionActual() + " / " + datos.getTotalRegistros();
    }

    public void setValidationWarning(boolean hasWarning) {
        if (btnValidar == null) return;
        if (hasWarning) {
            if (periodicReminder != null) return;
            periodicReminder = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(10), e -> {
                    btnValidar.getStyleClass().add("has-warnings");
                    if (pulseAnimation == null) pulseAnimation = AnimationHelper.createPulseAnimation(btnValidar);
                    pulseAnimation.play();
                }),
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(10).add(javafx.util.Duration.seconds(8)), e -> {
                    btnValidar.getStyleClass().remove("has-warnings");
                    if (pulseAnimation != null) pulseAnimation.stop();
                })
            );
            periodicReminder.setCycleCount(javafx.animation.Animation.INDEFINITE);
            periodicReminder.play();
        } else {
            if (periodicReminder != null) { periodicReminder.stop(); periodicReminder = null; }
            btnValidar.getStyleClass().remove("has-warnings");
            if (pulseAnimation != null) pulseAnimation.stop();
        }
    }
}
