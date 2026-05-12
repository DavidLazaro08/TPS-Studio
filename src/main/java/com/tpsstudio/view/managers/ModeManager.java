package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.enums.TipoCodigo;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.service.EtiquetasManager;
import com.tpsstudio.service.ProjectManager;
import com.tpsstudio.util.AnimationHelper;
import com.tpsstudio.view.managers.design.LayersPanelManager;
import com.tpsstudio.view.managers.design.ProductionViewManager;
import com.tpsstudio.view.managers.design.ToolboxManager;
import com.tpsstudio.view.managers.design.VariableDataManager;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Gestiona el cambio entre los modos principales de la interfaz:
 * Diseño y Producción.
 *
 * También coordina los paneles laterales delegando el trabajo específico
 * en managers más pequeños.
 */
public class ModeManager {

    private AppMode currentMode;

    // =====================================================
    // Dependencias y contenedores
    // =====================================================

    private EtiquetasManager etiquetasManager;
    private ProjectManager projectManager;

    private final VBox leftPanel;
    private final VBox rightPanel;
    private final PropertiesPanelController propertiesPanelController;

    // =====================================================
    // Callbacks hacia MainViewController
    // =====================================================

    private Runnable onAddText;
    private Runnable onAddImage;
    private Runnable onAddBackground;
    private Runnable onNewCR80;
    private Runnable onExport;
    private Runnable onPrint;
    private Runnable onValidateDesign;
    private Runnable onCanvasRedraw;

    private Consumer<Elemento> onElementSelected;
    private Consumer<Proyecto> onProjectSelected;
    private Consumer<Proyecto> onEditProject;
    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;
    private Consumer<Elemento> onToggleLock;
    private Consumer<com.tpsstudio.model.elements.FormaElemento.TipoForma> onAddShape;
    private Consumer<TipoCodigo> onAddCode;

    // =====================================================
    // Estado del panel derecho
    // =====================================================

    private boolean isPropertiesActive = true;
    private Node propertiesNode;
    private Node datosNode;

    // =====================================================
    // Managers delegados
    // =====================================================

    private LayersPanelManager layersPanelManager;
    private ToolboxManager toolboxManager;
    private ProductionViewManager productionViewManager;
    private VariableDataManager variableDataManager;

    private Button btnValidar;
    private javafx.animation.Timeline periodicReminder;
    private javafx.animation.Timeline pulseAnimation;

    // =====================================================
    // Constructor
    // =====================================================

    public ModeManager(VBox leftPanel, VBox rightPanel, PropertiesPanelController propertiesPanelController) {
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.propertiesPanelController = propertiesPanelController;
        this.currentMode = AppMode.DESIGN;

        initializeDelegatedManagers();
    }

    private void initializeDelegatedManagers() {
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

        this.variableDataManager = new VariableDataManager(
                projectManager,
                proj -> { if (onEditProject != null) onEditProject.accept(proj); },
                () -> { if (onCanvasRedraw != null) onCanvasRedraw.run(); }
        );
    }

    // =====================================================
    // Inyección de managers
    // =====================================================

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
        if (productionViewManager != null) productionViewManager.setProjectManager(projectManager);

        // VariableDataManager necesita el ProjectManager cuando ya está disponible.
        this.variableDataManager = new VariableDataManager(
                projectManager,
                proj -> { if (onEditProject != null) onEditProject.accept(proj); },
                () -> { if (onCanvasRedraw != null) onCanvasRedraw.run(); }
        );
    }

    // =====================================================
    // Callback setters
    // =====================================================

    public void setOnAddText(Runnable cb) { this.onAddText = cb; }
    public void setOnAddImage(Runnable cb) { this.onAddImage = cb; }
    public void setOnAddBackground(Runnable cb) { this.onAddBackground = cb; }
    public void setOnNewCR80(Runnable cb) { this.onNewCR80 = cb; }
    public void setOnExport(Runnable cb) { this.onExport = cb; }
    public void setOnPrint(Runnable cb) { this.onPrint = cb; }
    public void setOnElementSelected(Consumer<Elemento> cb) { this.onElementSelected = cb; }
    public void setOnProjectSelected(Consumer<Proyecto> cb) { this.onProjectSelected = cb; }
    public void setOnEditProject(Consumer<Proyecto> cb) { this.onEditProject = cb; }
    public void setOnEditExternal(Consumer<ImagenFondoElemento> cb) { this.onEditExternal = cb; }
    public void setOnReload(Consumer<ImagenFondoElemento> cb) { this.onReload = cb; }
    public void setOnToggleLock(Consumer<Elemento> cb) { this.onToggleLock = cb; }
    public void setOnAddShape(Consumer<com.tpsstudio.model.elements.FormaElemento.TipoForma> cb) { this.onAddShape = cb; }
    public void setOnAddCode(Consumer<TipoCodigo> cb) { this.onAddCode = cb; }
    public void setOnValidateDesign(Runnable cb) { this.onValidateDesign = cb; }
    public void setOnCanvasRedraw(Runnable cb) { this.onCanvasRedraw = cb; }

    public AppMode getCurrentMode() { return currentMode; }

    // =====================================================
    // Cambio de modo y construcción de paneles
    // =====================================================

    public void switchMode(AppMode newMode, Proyecto proyecto, Elemento selectedElement, ObservableList<Proyecto> projects) {
        this.currentMode = newMode;
        leftPanel.getChildren().clear();

        if (newMode == AppMode.DESIGN) {
            rightPanel.getChildren().clear();
            buildDesignModePanels(proyecto, selectedElement);
        } else {
            productionViewManager.buildProductionModePanels(projects, proyecto);
        }

        AnimationHelper.applyFadeTransition(leftPanel, 550, 0.3, 1.0);
        AnimationHelper.applyFadeTransition(rightPanel, 550, 0.3, 1.0);

        if (onCanvasRedraw != null) onCanvasRedraw.run();
    }

    private void buildDesignModePanels(Proyecto proyecto, Elemento selectedElement) {
        VBox toolbox = toolboxManager.buildToolboxPanel();
        this.btnValidar = toolboxManager.getBtnValidar();

        leftPanel.getChildren().addAll(
                toolbox,
                new Separator(),
                layersPanelManager.buildLayersPanel(proyecto, selectedElement)
        );

        refreshPropertiesPanel(selectedElement, proyecto);
        this.datosNode = variableDataManager.buildPanel(proyecto);

        rightPanel.getChildren().setAll(isPropertiesActive ? propertiesNode : datosNode);
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

    // =====================================================
    // Refresco de paneles
    // =====================================================

    public void refreshPropertiesPanel(Elemento selectedElement, Proyecto proyecto) {
        VBox properties = propertiesPanelController.buildPanel(selectedElement, proyecto);
        ScrollPane scrollProps = new ScrollPane(properties);

        scrollProps.setFitToWidth(true);
        scrollProps.getStyleClass().add("panel-scroll-view");
        VBox.setVgrow(scrollProps, Priority.ALWAYS);

        this.propertiesNode = scrollProps;

        if (isPropertiesActive && currentMode == AppMode.DESIGN) rightPanel.getChildren().setAll(propertiesNode);
    }

    public void refreshLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        layersPanelManager.refreshLayersPanel(proyecto, selectedElement);
    }

    public void rebuildLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        layersPanelManager.rebuildLayersPanel(proyecto, selectedElement, null);
    }

    // =====================================================
    // Validación visual del diseño
    // =====================================================

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
            if (periodicReminder != null) {
                periodicReminder.stop();
                periodicReminder = null;
            }

            btnValidar.getStyleClass().remove("has-warnings");

            if (pulseAnimation != null) pulseAnimation.stop();
        }
    }
}