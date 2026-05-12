package com.tpsstudio.view.controllers;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.*;
import com.tpsstudio.service.EtiquetasManager;
import com.tpsstudio.service.ProjectManager;
import com.tpsstudio.view.managers.EditorCanvasManager;
import com.tpsstudio.view.managers.ModeManager;
import com.tpsstudio.view.managers.PropertiesPanelController;
import com.tpsstudio.view.dialogs.EditarProyectoDialog;
import com.tpsstudio.view.dialogs.NuevoProyectoDialog;
import com.tpsstudio.service.SettingsManager;
import com.tpsstudio.util.AnimationHelper;
import com.tpsstudio.service.DesignValidatorService;
import com.tpsstudio.service.ImpresionService;
import com.tpsstudio.model.print.SalidaImpresion;
import com.tpsstudio.model.print.SalidaImpresoraDirecta;
import com.tpsstudio.model.print.SalidaPDFSistema;
import com.tpsstudio.model.print.TrabajoImpresion;
import com.tpsstudio.util.TPSToast;
import com.tpsstudio.view.controllers.sub.ElementActionsController;
import com.tpsstudio.view.controllers.sub.ProjectActionsController;
import com.tpsstudio.view.controllers.sub.SessionController;
import com.tpsstudio.view.managers.design.CanvasAnimationManager;
import com.tpsstudio.view.dialogs.ImpresionDialog;
import com.tpsstudio.viewmodel.MainViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import com.tpsstudio.view.managers.ShortcutManager;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Controlador principal de TPS Studio.
 *
 * Coordina la pantalla principal de trabajo: canvas central, paneles laterales,
 * lista de proyectos, modos de uso y comunicación entre managers.
 *
 * La lógica de negocio se delega en ProjectManager y la gestión directa del canvas
 * en EditorCanvasManager.
 */
public class MainViewController {

    // =====================================================
    // FXML
    // =====================================================

    @FXML
    private VBox leftPanel;
    @FXML
    private VBox rightPanel;
    @FXML
    private Canvas canvas;
    @FXML
    private StackPane canvasContainer;
    @FXML
    private Label lblZoom;
    @FXML
    private ToggleButton toggleGuias;
    @FXML
    private HBox selectorCaraBox;
    @FXML
    private ToggleButton btnCaraFrente;
    @FXML
    private ToggleButton btnCaraDorso;
    @FXML
    private Label lblProyectoActivo;
    @FXML
    private Pane canvasOverlay;
    @FXML
    private ComboBox<TipoTroquel> cmbTroquelToolbar;
    @FXML
    private ToggleButton btnModeEdit;
    @FXML
    private ToggleButton btnModeExport;
    @FXML
    private ToggleButton togglePropiedades;
    @FXML
    private ToggleButton toggleDatosVariables;
    @FXML
    private javafx.scene.layout.HBox bloqueContextual;
    @FXML
    private Label lblCurrentUser;

    // =====================================================
    // Managers y estado principal
    // =====================================================

    private EditorCanvasManager canvasManager;
    private PropertiesPanelController propertiesPanelController;
    private ModeManager modeManager;
    private ProjectManager projectManager;
    private ShortcutManager shortcutManager;

    private final MainViewModel viewModel = new MainViewModel();

    private ProjectActionsController projectActionsController;
    private ElementActionsController elementActionsController;
    private SessionController sessionController;

    private EtiquetasManager etiquetasManager;
    private CanvasAnimationManager animationManager;

    private javafx.animation.Timeline designPulse;
    private javafx.scene.effect.ColorAdjust designColorAdjust = new javafx.scene.effect.ColorAdjust();

    // =====================================================
    // Inicialización
    // =====================================================

    @FXML
    private void initialize() {
        setupCanvas();
        initUI();
        setupBindings();

        lblProyectoActivo.setOnMouseClicked(e -> {
            viewModel.setProjectChipCollapsed(!viewModel.isProjectChipCollapsed());
            actualizarLabelProyecto(true);
        });

        projectManager.cargarProyectosRecientes(8);

        lblCurrentUser.setText("Sesión: " + com.tpsstudio.service.AuthService.getInstance().getCurrentUser());

        shortcutManager = new ShortcutManager(this, viewModel);
        canvasContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                shortcutManager.setup(newScene);
            }
        });

        // La aplicación arranca en modo Producción.
        switchMode(AppMode.PRODUCTION);

        sessionController = new SessionController(leftPanel);
    }

    private void initUI() {
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.5));
        shadow.setRadius(15);
        shadow.setSpread(0.1);
        rightPanel.setEffect(shadow);

        rightPanel.setVisible(false);
        rightPanel.setManaged(false);
    }

    private void setupBindings() {
        lblZoom.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                        () -> String.format("%.0f%%", viewModel.getZoomLevel() * 100),
                        viewModel.zoomLevelProperty()
                )
        );

        toggleGuias.selectedProperty().bindBidirectional(viewModel.mostrarGuiasProperty());

        viewModel.mostrarGuiasProperty().addListener((obs, old, nw) -> {
            if (canvasManager != null) {
                canvasManager.setMostrarGuias(nw);
                dibujarCanvas();
            }
        });

        viewModel.zoomLevelProperty().addListener((obs, old, nw) -> {
            if (canvasManager != null) {
                canvasManager.setZoomLevel(nw.doubleValue());
                posicionarSelectorCara();
                dibujarCanvas();
            }
        });
    }

    @FXML
    private void onLogout() {
        sessionController.logout();
    }

    private void setupCanvas() {

        // -------------------------------------------------
        // Gestión de proyectos
        // -------------------------------------------------

        projectManager = new ProjectManager();

        String currentUser = com.tpsstudio.service.AuthService.getInstance().getCurrentUser();
        etiquetasManager = new EtiquetasManager(currentUser);
        animationManager = new CanvasAnimationManager(canvas, canvasContainer);

        projectActionsController = new ProjectActionsController(
                viewModel, projectManager, canvas, this::dibujarCanvas, etiquetasManager);

        projectManager.setOnProjectChanged(() -> {
            viewModel.setProyectoActual(projectManager.getProyectoActual());
            if (canvasManager != null) {
                canvasManager.setProyectoActual(viewModel.getProyectoActual());
            }
            sincronizarFuenteDatos();
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                buildEditPanels();
            }
            actualizarLabelProyecto(false);
            dibujarCanvas();
        });

        projectManager.setOnElementAdded(() -> {
            modeManager.rebuildLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            dibujarCanvas();
        });

        projectManager.setOnNotificacion((tipo, mensaje) -> {
            Window owner = canvas.getScene() != null ? canvas.getScene().getWindow() : null;
            if ("error".equals(tipo)) {
                TPSToast.mostrar(owner, mensaje, null, TPSToast.Tipo.ERROR);
            } else {
                TPSToast.mostrarRelativo(canvasContainer, mensaje, null, TPSToast.Tipo.EXITO);
            }
        });

        // -------------------------------------------------
        // Canvas
        // -------------------------------------------------

        canvasManager = new EditorCanvasManager(canvas);
        canvasManager.setProyectoActual(viewModel.getProyectoActual());
        canvasManager.setZoomLevel(viewModel.getZoomLevel());
        canvasManager.setCurrentMode(viewModel.getCurrentMode());
        canvasManager.setMostrarGuias(true);

        elementActionsController = new ElementActionsController(
                viewModel, projectManager, canvasManager, canvas,
                this::dibujarCanvas,
                this::ensurePropertiesPanelVisible);

        // -------------------------------------------------
        // Panel de propiedades
        // -------------------------------------------------

        propertiesPanelController = new PropertiesPanelController(canvas, canvasManager);

        propertiesPanelController.setOnPropertyChanged(() -> {
            // Refresco ligero para no perder el foco en campos editables.
            modeManager.refreshPropertiesPanel(
                    viewModel.getElementoSeleccionado(), viewModel.getProyectoActual());
        });

        propertiesPanelController.setOnCanvasRedrawNeeded(() -> {
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            }
            dibujarCanvas();
        });

        propertiesPanelController.setOnEditExternal(elementActionsController::abrirEditorExterno);
        propertiesPanelController.setOnReload(elementActionsController::recargarFondo);
        propertiesPanelController.setOnDownloadTemplate(this::onDescargarPlantilla);

        // -------------------------------------------------
        // Modo de trabajo y acciones de UI
        // -------------------------------------------------

        modeManager = new ModeManager(leftPanel, rightPanel, propertiesPanelController);
        modeManager.setEtiquetasManager(etiquetasManager);

        modeManager.setOnAddText(this::onAñadirTexto);
        modeManager.setOnAddImage(this::onAñadirImagen);
        modeManager.setOnAddBackground(this::onAñadirFondo);
        modeManager.setOnAddShape(this::onAñadirForma);
        modeManager.setOnAddCode(this::onAñadirCodigo);

        modeManager.setOnValidateDesign(this::onValidarDiseno);

        modeManager.setOnNewCR80(this::onNuevoCR80);
        modeManager.setOnExport(this::onExportarProyecto);

        if (cmbTroquelToolbar != null) {
            cmbTroquelToolbar.getItems().addAll(TipoTroquel.values());
            cmbTroquelToolbar.getSelectionModel().select(TipoTroquel.NINGUNO);
            cmbTroquelToolbar.setOnAction(e -> {
                if (viewModel.getProyectoActual() != null) {
                    TipoTroquel sel = cmbTroquelToolbar.getValue();
                    viewModel.getProyectoActual().setTipoTroquel(sel);

                    if (sel != TipoTroquel.NINGUNO && !viewModel.isMostrarGuias()) {
                        viewModel.setMostrarGuias(true);
                    }

                    dibujarCanvas();
                }
            });
        }

        modeManager.setOnPrint(this::onImprimirProyecto);

        modeManager.setOnElementSelected(elemento -> {
            viewModel.setElementoSeleccionado(elemento);
            canvasManager.setElementoSeleccionado(elemento);

            if (elemento != null) {
                ensurePropertiesPanelVisible();
            }

            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            }
            dibujarCanvas();
        });

        modeManager.setOnProjectSelected(proyecto -> {
            animationManager.ejecutarCrossFade(() -> {
                viewModel.setProyectoActual(proyecto);
                projectManager.setProyectoActual(proyecto);
                canvasManager.setProyectoActual(proyecto);

                if (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) {
                    ajustarZoomVerticalSiNecesario();
                }

                checkDesignWarnings();
                dibujarCanvas();
            }, proyecto);
        });

        modeManager.setOnEditExternal(elementActionsController::abrirEditorExterno);
        modeManager.setOnReload(elementActionsController::recargarFondo);

        modeManager.setOnToggleLock(elemento -> {
            elemento.setLocked(!elemento.isLocked());
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            }
            dibujarCanvas();
        });

        modeManager.setOnCanvasRedraw(this::dibujarCanvas);
        modeManager.setOnEditProject(this::abrirDialogoEditarProyecto);
        modeManager.setProjectManager(projectManager);

        // -------------------------------------------------
        // Sincronización entre canvas y paneles
        // -------------------------------------------------

        canvasManager.setOnClientDataRequested(() -> {
            if (viewModel.getProyectoActual() != null) {
                abrirDialogoEditarProyecto(viewModel.getProyectoActual());
            }
        });

        canvasManager.setOnElementSelected(() -> {
            viewModel.setElementoSeleccionado(canvasManager.getElementoSeleccionado());
            ensurePropertiesPanelVisible();

            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            }
        });

        canvasManager.setOnElementTransformed(() -> {
            viewModel.setElementoSeleccionado(canvasManager.getElementoSeleccionado());
            if (propertiesPanelController != null && viewModel.getElementoSeleccionado() != null) {
                propertiesPanelController.updatePositionFields(viewModel.getElementoSeleccionado());
            }
            checkDesignWarnings();
        });

        canvasManager.setOnCanvasChanged(() -> {
            viewModel.setElementoSeleccionado(canvasManager.getElementoSeleccionado());

            if (propertiesPanelController != null && viewModel.getElementoSeleccionado() != null) {
                propertiesPanelController.updatePositionFields(viewModel.getElementoSeleccionado());
            }

            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            }
            checkDesignWarnings();
            canvasManager.dibujarCanvas();
        });

        // -------------------------------------------------
        // Entrada de usuario
        // -------------------------------------------------

        canvasManager.setupMouseHandlers();

        canvas.setOnScroll(event -> {
            if (event.getDeltaY() > 0) {
                onZoomIn();
            } else if (event.getDeltaY() < 0) {
                onZoomOut();
            }
            event.consume();
        });

        canvas.setFocusTraversable(true);

        // -------------------------------------------------
        // Ajustes finales de layout
        // -------------------------------------------------

        posicionarSelectorCara();

        javafx.scene.shape.Rectangle clipRect = new javafx.scene.shape.Rectangle();
        clipRect.widthProperty().bind(canvasContainer.widthProperty());
        clipRect.heightProperty().bind(canvasContainer.heightProperty());
        canvasContainer.setClip(clipRect);

        canvasContainer.widthProperty().addListener((obs, old, nw) -> posicionarSelectorCara());
        canvasContainer.heightProperty().addListener((obs, old, nw) -> posicionarSelectorCara());

        if (canvasOverlay != null) {
            canvasOverlay.prefWidthProperty().bind(canvasContainer.widthProperty());
            canvasOverlay.prefHeightProperty().bind(canvasContainer.heightProperty());
        }

        if (selectorCaraBox != null)
            selectorCaraBox.widthProperty().addListener((obs, old, nw) -> posicionarSelectorCara());

        dibujarCanvas();
    }

    private void posicionarSelectorCara() {
        if (selectorCaraBox != null && canvasContainer != null) {
            double zoom = viewModel.getZoomLevel();

            double baseWidth = EditorCanvasManager.CARD_WIDTH;
            double baseHeight = EditorCanvasManager.CARD_HEIGHT;

            if (viewModel.getProyectoActual() != null &&
                    viewModel.getProyectoActual().getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) {
                baseWidth = EditorCanvasManager.CARD_HEIGHT;
                baseHeight = EditorCanvasManager.CARD_WIDTH;
            }

            double cardScaledWidth = baseWidth * zoom;
            double cardScaledHeight = baseHeight * zoom;
            double bleedScaled = EditorCanvasManager.BLEED_MARGIN * zoom;

            double cw = canvasContainer.getWidth();
            double ch = canvasContainer.getHeight();

            double cardX = (cw / 2) - (cardScaledWidth / 2);
            double cardY = (ch / 2) - (cardScaledHeight / 2);

            double topEdge = cardY - bleedScaled;

            double offset = 65;
            selectorCaraBox.setLayoutX(cardX - bleedScaled - 20);
            selectorCaraBox.setLayoutY(topEdge - offset);
        }
    }

    private void checkDesignWarnings() {
        if (viewModel.getProyectoActual() == null)
            return;
        com.tpsstudio.service.DesignValidatorService validator = new com.tpsstudio.service.DesignValidatorService();
        java.util.List<String> avisos = validator.validarDiseno(viewModel.getProyectoActual());
        modeManager.setValidationWarning(!avisos.isEmpty());
    }

    private void onValidarDiseno() {
        projectActionsController.validarDiseno();
    }

    private void onDescargarPlantilla() {
        projectActionsController.descargarPlantilla();
    }

    // =====================================================
    // Cambio de modo
    // =====================================================

    @FXML
    private void onModeEdit() {
        if (!btnModeEdit.isSelected()) {
            btnModeEdit.setSelected(true);
        }
        switchMode(AppMode.DESIGN);
    }

    @FXML
    private void onModeExport() {
        if (!btnModeExport.isSelected()) {
            btnModeExport.setSelected(true);
        }
        switchMode(AppMode.PRODUCTION);
    }

    private void switchMode(AppMode newMode) {
        viewModel.setCurrentMode(newMode);
        canvasManager.setCurrentMode(newMode);

        if (newMode == AppMode.PRODUCTION) {
            if (bloqueContextual != null && bloqueContextual.isVisible()) {
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                        Duration.millis(AnimationHelper.DURATION_MEDIUM), bloqueContextual);
                ft.setToValue(0.0);
                ft.setOnFinished(e -> {
                    bloqueContextual.setVisible(false);
                    bloqueContextual.setManaged(false);
                });
                ft.play();
            }

            cerrarPanelDerecho();

            Platform.runLater(() -> {
                PauseTransition delay = new PauseTransition(Duration.millis(100));
                delay.setOnFinished(ev -> adjustCanvasCentering());
                delay.play();
            });

            if (btnModeEdit != null) {
                btnModeEdit.setEffect(designColorAdjust);
                if (designPulse == null) {
                    designPulse = new javafx.animation.Timeline(
                            new javafx.animation.KeyFrame(Duration.ZERO,
                                    new javafx.animation.KeyValue(designColorAdjust.brightnessProperty(), 0.0,
                                            Interpolator.EASE_BOTH)),
                            new javafx.animation.KeyFrame(Duration.millis(1200),
                                    new javafx.animation.KeyValue(designColorAdjust.brightnessProperty(), 0.35,
                                            Interpolator.EASE_BOTH)),
                            new javafx.animation.KeyFrame(Duration.millis(2400), new javafx.animation.KeyValue(
                                    designColorAdjust.brightnessProperty(), 0.0, Interpolator.EASE_BOTH)));
                    designPulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
                }
                designPulse.playFromStart();
            }

        } else {
            if (bloqueContextual != null && !bloqueContextual.isVisible()) {
                bloqueContextual.setOpacity(0.0);
                bloqueContextual.setVisible(true);
                bloqueContextual.setManaged(true);
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                        Duration.millis(AnimationHelper.DURATION_MEDIUM), bloqueContextual);
                ft.setToValue(1.0);
                ft.play();
            }

            if (designPulse != null) {
                designPulse.stop();
            }
            if (btnModeEdit != null) {
                btnModeEdit.setEffect(null);
            }
        }

        modeManager.switchMode(newMode, viewModel.getProyectoActual(),
                viewModel.getElementoSeleccionado(), projectManager.getProyectos());
        actualizarLabelProyecto(false);
    }

    // =====================================================
    // Zoom
    // =====================================================

    @FXML
    private void onZoomIn() {
        if (viewModel.getZoomLevel() < 2.0) {
            viewModel.setZoomLevel(viewModel.getZoomLevel() + 0.1);
        }
    }

    @FXML
    private void onZoomOut() {
        if (viewModel.getZoomLevel() > 0.5) {
            viewModel.setZoomLevel(viewModel.getZoomLevel() - 0.1);
        }
    }

    // =====================================================
    // Opciones visuales
    // =====================================================

    @FXML
    private void onToggleGuias() {
        // Gestionado mediante binding bidireccional en setupBindings().
    }

    // =====================================================
    // Refresco de paneles
    // =====================================================

    private void buildEditPanels() {
        sincronizarFuenteDatos();
        modeManager.switchMode(AppMode.DESIGN, viewModel.getProyectoActual(),
                viewModel.getElementoSeleccionado(), projectManager.getProyectos());
    }

    // =====================================================
    // Canvas
    // =====================================================

    private void dibujarCanvas() {
        posicionarSelectorCara();
        if (selectorCaraBox != null) {
            boolean hasProject = viewModel.getProyectoActual() != null;
            boolean shouldShow = hasProject && viewModel.getCurrentMode() == AppMode.DESIGN;

            if (shouldShow && !selectorCaraBox.isVisible()) {
                selectorCaraBox.setOpacity(0.0);
                selectorCaraBox.setVisible(true);
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                        Duration.millis(AnimationHelper.DURATION_MEDIUM), selectorCaraBox);
                ft.setToValue(1.0);
                ft.play();
            } else if (!shouldShow && selectorCaraBox.isVisible() && selectorCaraBox.getOpacity() > 0) {
                selectorCaraBox.setOpacity(0.0);
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                        Duration.millis(AnimationHelper.DURATION_MEDIUM), selectorCaraBox);
                ft.setFromValue(1.0);
                ft.setToValue(0.0);
                ft.setOnFinished(e -> selectorCaraBox.setVisible(false));
                ft.play();
            }

            if (hasProject) {
                boolean isFrente = viewModel.getProyectoActual().isMostrandoFrente();
                btnCaraFrente.setSelected(isFrente);
                btnCaraDorso.setSelected(!isFrente);
            }
        }
        canvasManager.dibujarCanvas();
    }

    private void sincronizarFuenteDatos() {
        com.tpsstudio.model.project.FuenteDatos fd = projectManager.getFuenteDatos();
        canvasManager.setFuenteDatos(fd);
        propertiesPanelController.setFuenteDatos(fd);
    }

    // =====================================================
    // Helpers de interfaz
    // =====================================================

    private void ensurePropertiesPanelVisible() {
        if (viewModel.getCurrentMode() != AppMode.DESIGN)
            return;

        if (togglePropiedades != null && !togglePropiedades.isSelected()) {
            togglePropiedades.setSelected(true);
            if (toggleDatosVariables != null) {
                toggleDatosVariables.setSelected(false);
            }
            modeManager.setRightPanelTabActiva(true);

            if (!rightPanel.isVisible()) {
                togglePanel(rightPanel, true);
            }

            adjustCanvasCentering();
        }

        if (viewModel.getElementoSeleccionado() != null && togglePropiedades.isSelected()) {
            modeManager.refreshPropertiesPanel(viewModel.getElementoSeleccionado(), viewModel.getProyectoActual());
        }
    }

    private void cerrarPanelDerecho() {
        if (togglePropiedades != null)
            togglePropiedades.setSelected(false);
        if (toggleDatosVariables != null)
            toggleDatosVariables.setSelected(false);
        if (rightPanel.isVisible()) {
            togglePanel(rightPanel, false);
        }
    }

    // =====================================================
    // Acciones de proyecto
    // =====================================================

    @FXML
    private void onNuevoProyecto() {
        projectActionsController.nuevoProyecto();
    }

    @FXML
    private void onAbrirProyecto() {
        projectActionsController.abrirProyecto();
    }

    @FXML
    private void onGuardarProyecto() {
        projectActionsController.guardarProyecto();
    }

    @FXML
    private void onExportarProyecto() {
        projectActionsController.exportarProyecto();
    }

    @FXML
    private void onImprimirProyecto() {
        projectActionsController.imprimirProyecto();
    }

    @FXML
    private void onNuevoCR80() {
        projectActionsController.nuevoProyecto();
    }

    @FXML
    private void onShowFrente() {
        if (viewModel.getProyectoActual() == null)
            return;
        animationManager.ejecutarCrossFade(() -> {
            viewModel.getProyectoActual().setMostrandoFrente(true);
            viewModel.setElementoSeleccionado(null);
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), null);
            }
            dibujarCanvas();
        }, viewModel.getProyectoActual());
    }

    @FXML
    private void onShowDorso() {
        if (viewModel.getProyectoActual() == null)
            return;
        animationManager.ejecutarCrossFade(() -> {
            viewModel.getProyectoActual().setMostrandoFrente(false);
            viewModel.setElementoSeleccionado(null);
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), null);
            }
            dibujarCanvas();
        }, viewModel.getProyectoActual());
    }

    @FXML
    private void onToggleOrientacion() {
        Proyecto p = viewModel.getProyectoActual();
        if (p == null)
            return;

        animationManager.ejecutarGiroTransition(() -> {
            com.tpsstudio.model.enums.Orientacion nueva = (p
                    .getOrientacion() == com.tpsstudio.model.enums.Orientacion.HORIZONTAL)
                    ? com.tpsstudio.model.enums.Orientacion.VERTICAL
                    : com.tpsstudio.model.enums.Orientacion.HORIZONTAL;

            p.setOrientacion(nueva);
            if (p.getMetadata() != null) {
                p.getMetadata().setOrientacion(nueva);
            }

            canvasManager.refrescarFondosTrasCarga();

            if (nueva == com.tpsstudio.model.enums.Orientacion.VERTICAL) {
                ajustarZoomVerticalSiNecesario();
            }
            dibujarCanvas();
        }, p);
    }

    private void ajustarZoomVerticalSiNecesario() {
        if (canvasContainer == null || canvasContainer.getHeight() <= 0)
            return;

        double currentZoom = viewModel.getZoomLevel();
        double cardHeightScaled = EditorCanvasManager.CARD_WIDTH * currentZoom;
        double maxHeight = canvasContainer.getHeight() - 80;

        if (cardHeightScaled > maxHeight) {
            double targetZoom = maxHeight / EditorCanvasManager.CARD_WIDTH;
            targetZoom = Math.floor(targetZoom * 10) / 10.0;
            if (targetZoom < 0.5)
                targetZoom = 0.5;

            if (targetZoom < currentZoom) {
                viewModel.setZoomLevel(targetZoom);
            }
        }
    }

    // =====================================================
    // Acciones de edición
    // =====================================================

    private void onAñadirTexto() {
        elementActionsController.anadirTexto();
    }

    private void onAñadirImagen() {
        elementActionsController.anadirImagen();
    }

    private void onAñadirForma(FormaElemento.TipoForma tipo) {
        elementActionsController.anadirForma(tipo);
    }

    private void onAñadirCodigo(TipoCodigo tipo) {
        elementActionsController.anadirCodigo(tipo);
    }

    public void onEliminarElemento() {
        elementActionsController.eliminarElemento();
    }

    private void onAñadirFondo() {
        elementActionsController.anadirFondo();
    }

    // =====================================================
    // Panel lateral derecho
    // =====================================================

    @FXML
    private void onTogglePropiedades() {
        if (togglePropiedades.isSelected()) {
            if (toggleDatosVariables != null)
                toggleDatosVariables.setSelected(false);
            modeManager.setRightPanelTabActiva(true);
            if (!rightPanel.isVisible())
                togglePanel(rightPanel, true);
        } else {
            if (toggleDatosVariables == null || !toggleDatosVariables.isSelected()) {
                togglePanel(rightPanel, false);
            }
        }
        adjustCanvasCentering();
    }

    @FXML
    private void onToggleDatosVariables() {
        if (toggleDatosVariables.isSelected()) {
            if (togglePropiedades != null)
                togglePropiedades.setSelected(false);
            modeManager.setRightPanelTabActiva(false);
            if (!rightPanel.isVisible())
                togglePanel(rightPanel, true);
        } else {
            if (togglePropiedades == null || !togglePropiedades.isSelected()) {
                togglePanel(rightPanel, false);
            }
        }
        adjustCanvasCentering();
    }

    private void togglePanel(Region panel, boolean show) {
        AnimationHelper.togglePanel(panel, show);
    }

    private void adjustCanvasCentering() {
        boolean panelVisible = viewModel.getCurrentMode() == AppMode.DESIGN &&
                ((togglePropiedades != null && togglePropiedades.isSelected()) ||
                        (toggleDatosVariables != null && toggleDatosVariables.isSelected()));

        if (canvasContainer.getWidth() <= 0) {
            Platform.runLater(this::adjustCanvasCentering);
            return;
        }

        double targetX = panelVisible ? -(rightPanel.getPrefWidth() / 2.0) : 0;
        double duration = panelVisible ? AnimationHelper.DURATION_OPEN : AnimationHelper.DURATION_CLOSE;

        AnimationHelper.shiftCanvas(canvas, targetX, duration);
        if (canvasOverlay != null) {
            AnimationHelper.shiftCanvas(canvasOverlay, targetX, duration);
        }
    }

    private void abrirDialogoEditarProyecto(Proyecto proyecto) {
        boolean changed = projectActionsController.editarProyecto(proyecto);
        if (changed) {
            actualizarLabelProyecto(false);
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                buildEditPanels();
            }
        }
    }

    private void actualizarLabelProyecto(boolean animate) {
        if (lblProyectoActivo != null) {
            if (viewModel.getProyectoActual() != null) {
                String nombre = viewModel.getProyectoActual().getNombre();
                String inicial = nombre.isEmpty() ? "P" : nombre.substring(0, 1).toUpperCase();

                if (lblProyectoActivo.getScene() == null || !animate) {
                    if (viewModel.isProjectChipCollapsed()) {
                        lblProyectoActivo.setText(inicial);
                        lblProyectoActivo.setPrefWidth(28);
                        lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER);
                        lblProyectoActivo
                                .setStyle("-fx-background-color: #0c0d16; -fx-text-fill: #4c5171; -fx-padding: 0;");
                    } else {
                        lblProyectoActivo.setText("Proyecto · " + nombre);
                        lblProyectoActivo.setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
                        lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        lblProyectoActivo.setStyle("");
                    }
                    return;
                }

                javafx.animation.Timeline timeline = new javafx.animation.Timeline();
                if (viewModel.isProjectChipCollapsed()) {
                    lblProyectoActivo.setText("Proyecto · " + nombre);
                    lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    lblProyectoActivo
                            .setStyle("-fx-background-color: #0c0d16; -fx-text-fill: #4c5171; -fx-padding: 0 0 0 15;");

                    double currentWidth = lblProyectoActivo.getWidth();
                    if (currentWidth > 30) {
                        lblProyectoActivo.setPrefWidth(currentWidth);
                    }

                    javafx.animation.KeyValue kv = new javafx.animation.KeyValue(lblProyectoActivo.prefWidthProperty(),
                            28, javafx.animation.Interpolator.EASE_BOTH);
                    javafx.animation.KeyFrame kf = new javafx.animation.KeyFrame(
                            javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), kv);
                    timeline.getKeyFrames().add(kf);

                    timeline.setOnFinished(e -> {
                        lblProyectoActivo.setText(inicial);
                        lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER);
                        lblProyectoActivo
                                .setStyle("-fx-background-color: #0c0d16; -fx-text-fill: #4c5171; -fx-padding: 0;");
                    });
                } else {
                    lblProyectoActivo.setText("Proyecto · " + nombre);
                    lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    lblProyectoActivo.setStyle("");

                    lblProyectoActivo.setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
                    lblProyectoActivo.applyCss();
                    lblProyectoActivo.layout();
                    double targetWidth = lblProyectoActivo.prefWidth(-1);

                    lblProyectoActivo.setPrefWidth(28);

                    javafx.animation.KeyValue kv = new javafx.animation.KeyValue(lblProyectoActivo.prefWidthProperty(),
                            targetWidth, javafx.animation.Interpolator.EASE_BOTH);
                    javafx.animation.KeyFrame kf = new javafx.animation.KeyFrame(
                            javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), kv);
                    timeline.getKeyFrames().add(kf);

                    timeline.setOnFinished(
                            e -> lblProyectoActivo.setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE));
                }
                timeline.play();

            } else {
                lblProyectoActivo.setText("");
            }
        }
    }
}