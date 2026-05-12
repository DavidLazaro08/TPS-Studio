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

/* Controlador principal de TPS Studio.
 * Coordina la pantalla de trabajo: canvas central, paneles laterales y lista de proyectos.
 *
 * Nota:
 * - La lógica de negocio de proyectos vive en ProjectManager.
 * - El renderizado y eventos del canvas se delegan en EditorCanvasManager.
 * - El montaje de paneles y modos se gestiona con ModeManager. */

public class MainViewController {

    // =====================================================
    // FXML (Componentes)
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
    private Label lblCurrentUser; // Label para mostrar el usuario activo

    // =====================================================
    // =====================================================
    // Managers (coordinación)
    // =====================================================
    // maneja renderizado y eventos de mouse
    private EditorCanvasManager canvasManager;
    // maneja el panel de propiedades
    private PropertiesPanelController propertiesPanelController;
    // maneja cambio de modo y construcción de paneles
    private ModeManager modeManager;
    // maneja lógica de negocio de proyectos y elementos
    private ProjectManager projectManager;
    // maneja los atajos de teclado
    private ShortcutManager shortcutManager;

    // ViewModel: estado observable de la aplicación
    private final MainViewModel viewModel = new MainViewModel();

    // Sub-controlador de acciones de proyecto (crear, abrir, guardar, exportar,
    // imprimir)
    private ProjectActionsController projectActionsController;

    // Sub-controlador de acciones de elementos (añadir texto, imagen, forma, fondo;
    // eliminar)
    private ElementActionsController elementActionsController;

    // Gestor de categorías/etiquetas (por usuario)
    private EtiquetasManager etiquetasManager;

    // Gestor de animaciones del canvas (Refactorización Phase 7)
    private CanvasAnimationManager animationManager;

    // Sub-controlador de sesión (Refactorización Phase 7)
    private SessionController sessionController;

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

        // Mostrar el usuario actual en el perfil
        lblCurrentUser.setText("Sesión: " + com.tpsstudio.service.AuthService.getInstance().getCurrentUser());

        // Configurar atajos de teclado cuando la escena esté lista
        shortcutManager = new ShortcutManager(this, viewModel);
        canvasContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                shortcutManager.setup(newScene);
            }
        });

        // Arrancamos en Producción: sin canvas ni paneles de diseño
        switchMode(AppMode.PRODUCTION);

        // Inicializar controlador de sesión usando el panel izquierdo como ancla
        sessionController = new SessionController(leftPanel);
    }

    private void initUI() {
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.5));
        shadow.setRadius(15);
        shadow.setSpread(0.1);
        rightPanel.setEffect(shadow);

        // Panel lateral cerrado al inicio
        rightPanel.setVisible(false);
        rightPanel.setManaged(false);
    }

    private void setupBindings() {
        // Binding del texto del Zoom
        lblZoom.textProperty().bind(
            javafx.beans.binding.Bindings.createStringBinding(
                () -> String.format("%.0f%%", viewModel.getZoomLevel() * 100),
                viewModel.zoomLevelProperty()
            )
        );

        // Binding bidireccional para las guías
        toggleGuias.selectedProperty().bindBidirectional(viewModel.mostrarGuiasProperty());

        // Listener para propagar el cambio de guías al CanvasManager
        viewModel.mostrarGuiasProperty().addListener((obs, old, nw) -> {
            if (canvasManager != null) {
                canvasManager.setMostrarGuias(nw);
                dibujarCanvas();
            }
        });

        // Listener para propagar el zoom al CanvasManager
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
        // ProjectManager (estado del proyecto y cambios)
        // -------------------------------------------------
        projectManager = new ProjectManager();

        // Inicializar gestores de apoyo
        String currentUser = com.tpsstudio.service.AuthService.getInstance().getCurrentUser();
        etiquetasManager = new EtiquetasManager(currentUser);
        animationManager = new CanvasAnimationManager(canvas, canvasContainer);

        // Inicializar sub-controlador de acciones de proyecto
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
            // Fuerza la reconstrucción de la lista de capas cuando se añade un elemento
            modeManager.rebuildLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            dibujarCanvas();
        });

        // Registrar callback de notificaciones (SERVICE → UI como toast)
        projectManager.setOnNotificacion((tipo, mensaje) -> {
            Window owner = canvas.getScene() != null ? canvas.getScene().getWindow() : null;
            if ("error".equals(tipo)) {
                TPSToast.mostrar(owner, mensaje, null, TPSToast.Tipo.ERROR);
            } else {
                TPSToast.mostrarRelativo(canvasContainer, mensaje, null, TPSToast.Tipo.EXITO);
            }
        });

        // -------------------------------------------------
        // EditorCanvasManager (render y eventos del canvas)
        // -------------------------------------------------
        canvasManager = new EditorCanvasManager(canvas);
        canvasManager.setProyectoActual(viewModel.getProyectoActual());
        canvasManager.setZoomLevel(viewModel.getZoomLevel());
        canvasManager.setCurrentMode(viewModel.getCurrentMode());
        canvasManager.setMostrarGuias(true);

        // Inicializar sub-controlador de acciones de elementos
        elementActionsController = new ElementActionsController(
                viewModel, projectManager, canvasManager, canvas,
                this::dibujarCanvas,
                this::ensurePropertiesPanelVisible);

        // -------------------------------------------------
        // Panel de propiedades (edición del elemento seleccionado)
        // -------------------------------------------------
        propertiesPanelController = new PropertiesPanelController(canvas, canvasManager);

        propertiesPanelController.setOnPropertyChanged(() -> {
            // Solo refrescar el panel de propiedades, NO reconstruir todo el modo.
            // Esto preserva el foco en campos como "Etiqueta" y evita el parpadeo.
            modeManager.refreshPropertiesPanel(
                    viewModel.getElementoSeleccionado(), viewModel.getProyectoActual());
        });

        propertiesPanelController.setOnCanvasRedrawNeeded(() -> {
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                // Refresco ligero: actualiza el texto de la celda (nombre + etiqueta)
                // sin reconstruir el panel ni perder el foco del campo de texto
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            }
            dibujarCanvas();
        });

        propertiesPanelController.setOnEditExternal(elementActionsController::abrirEditorExterno);
        propertiesPanelController.setOnReload(elementActionsController::recargarFondo);
        propertiesPanelController.setOnDownloadTemplate(this::onDescargarPlantilla);

        // -------------------------------------------------
        // ModeManager (montaje de paneles + acciones de UI)
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

        // Inicializar ComboBox de Troquel en la barra superior
        if (cmbTroquelToolbar != null) {
            cmbTroquelToolbar.getItems().addAll(TipoTroquel.values());
            cmbTroquelToolbar.getSelectionModel().select(TipoTroquel.NINGUNO);
            cmbTroquelToolbar.setOnAction(e -> {
                if (viewModel.getProyectoActual() != null) {
                    TipoTroquel sel = cmbTroquelToolbar.getValue();
                    viewModel.getProyectoActual().setTipoTroquel(sel);

                    // Asegurar que las guías están visibles si se selecciona un troquel
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

                // Adjust zoom if the project is vertical
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
                // Refresco ligero sin reconstruir todo el panel
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            }
            dibujarCanvas();
        });

        modeManager.setOnCanvasRedraw(this::dibujarCanvas);
        modeManager.setOnEditProject(this::abrirDialogoEditarProyecto);

        modeManager.setProjectManager(projectManager);

        // -------------------------------------------------
        // Sincronización Canvas ↔ UI (callbacks del canvas)
        // -------------------------------------------------

        canvasManager.setOnClientDataRequested(() -> {
            if (viewModel.getProyectoActual() != null) {
                abrirDialogoEditarProyecto(viewModel.getProyectoActual());
            }
        });

        canvasManager.setOnElementSelected(() -> {
            viewModel.setElementoSeleccionado(canvasManager.getElementoSeleccionado());
            ensurePropertiesPanelVisible();
            // Sincronizar selección en la lista de capas sin reconstruir (solo resalta la
            // capa)
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
            // Actualizar posición sin reconstruir paneles (evita parpadeo y pérdida de
            // foco)
            if (propertiesPanelController != null && viewModel.getElementoSeleccionado() != null) {
                propertiesPanelController.updatePositionFields(viewModel.getElementoSeleccionado());
            }
            // Propagar deselección al panel de capas (cuando se hace click en el vacío del
            // canvas)
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
            }
            checkDesignWarnings();
            canvasManager.dibujarCanvas();
        });

        // -------------------------------------------------
        // Input (mouse y teclado)
        // -------------------------------------------------

        // Importante: configurar mouse handlers para drag & resize
        canvasManager.setupMouseHandlers();

        // Habilitar zoom con la rueda del ratón
        canvas.setOnScroll(event -> {
            if (event.getDeltaY() > 0) {
                onZoomIn();
            } else if (event.getDeltaY() < 0) {
                onZoomOut();
            }
            event.consume();
        });

        canvas.setFocusTraversable(true);
        // Los atajos se gestionan ahora a través de ShortcutManager en la Scene


        // -------------------------------------------------
        // Zoom e Inicialización de UI
        // -------------------------------------------------
        posicionarSelectorCara();

        // Clip para evitar que el canvas se desborde sobre el panel izquierdo
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

            // Posición de la tarjeta absoluta dentro del contenedor (asumiendo centrado)
            double cardX = (cw / 2) - (cardScaledWidth / 2);
            double cardY = (ch / 2) - (cardScaledHeight / 2);

            // Borde superior de la tarjeta con sangre
            double topEdge = cardY - bleedScaled;

            double offset = 65;
            // Restaurar la posición "perfecta" original a la izquierda
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
    // Cambio de modo (Design / Production)
    // =====================================================

    @FXML
    private void onModeEdit() {
        // Aseguramos que siempre haya un modo activo (no permitimos deselección)
        if (!btnModeEdit.isSelected()) {
            btnModeEdit.setSelected(true);
        }
        switchMode(AppMode.DESIGN);
    }

    @FXML
    private void onModeExport() {
        // Aseguramos que siempre haya un modo activo (no permitimos deselección)
        if (!btnModeExport.isSelected()) {
            btnModeExport.setSelected(true);
        }
        switchMode(AppMode.PRODUCTION);
    }

    private javafx.animation.Timeline designPulse;
    private javafx.scene.effect.ColorAdjust designColorAdjust = new javafx.scene.effect.ColorAdjust();

    private void switchMode(AppMode newMode) {
        viewModel.setCurrentMode(newMode);
        canvasManager.setCurrentMode(newMode);

        // En Producción, los paneles de diseño no aplican: los ocultamos
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

            // Forzar un segundo ajuste tras un breve delay para asegurar que el layout se
            // ha asentado
            javafx.application.Platform.runLater(() -> {
                javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(Duration.millis(100)); // Aumentado
                                                                                                                     // para
                                                                                                                     // mayor
                                                                                                                     // estabilidad
                delay.setOnFinished(ev -> adjustCanvasCentering());
                delay.play();
            });

            // Iniciar parpadeo (respiración suave) en el botón Diseño invitando a pulsarlo
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
                designPulse.playFromStart(); // Sincronizado
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

            // Apagar y frenar la respiración del botón Diseño
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
    // Toggles / opciones visuales
    // =====================================================

    @FXML
    private void onToggleGuias() {
        // Ya se gestiona mediante binding bidireccional en setupBindings()
    }

    // =====================================================
    // Refresco de paneles de edición (sin cambiar modo global)
    // =====================================================

    private void buildEditPanels() {
        sincronizarFuenteDatos();
        modeManager.switchMode(AppMode.DESIGN, viewModel.getProyectoActual(),
                viewModel.getElementoSeleccionado(), projectManager.getProyectos());
    }

    // =====================================================
    // Canvas
    // =====================================================

    /*
     * Dibuja el canvas delegando en EditorCanvasManager.
     */
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
                selectorCaraBox.setOpacity(0.0); // Prevenimos multiples animaciones superpuestas
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

    /* Propaga la FuenteDatos activa a los managers que la necesitan. */
    private void sincronizarFuenteDatos() {
        com.tpsstudio.model.project.FuenteDatos fd = projectManager.getFuenteDatos();
        canvasManager.setFuenteDatos(fd);
        propertiesPanelController.setFuenteDatos(fd);
    }

    // =====================================================
    // Helpers
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

            // Igual que cuando se abre con el botón de la barra:
            // desplazar la tarjeta para que no quede tapada por el panel
            adjustCanvasCentering();
        }

        // Refrescar el contenido del panel de propiedades para el elemento seleccionado
        if (viewModel.getElementoSeleccionado() != null && togglePropiedades.isSelected()) {
            modeManager.refreshPropertiesPanel(viewModel.getElementoSeleccionado(), viewModel.getProyectoActual());
        }
    }

    // Cierra el panel derecho y resetea los toggles
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
    // Acciones de barra superior (Proyectos / exportación)
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

    /**
     * Abre el diálogo de impresión y, si el usuario confirma, genera un PDF
     * temporal
     * con el mismo motor que la exportación y lo envía al sistema operativo.
     */
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
            // Cambiar orientación
            com.tpsstudio.model.enums.Orientacion nueva = (p
                    .getOrientacion() == com.tpsstudio.model.enums.Orientacion.HORIZONTAL)
                            ? com.tpsstudio.model.enums.Orientacion.VERTICAL
                            : com.tpsstudio.model.enums.Orientacion.HORIZONTAL;

            p.setOrientacion(nueva);
            if (p.getMetadata() != null) {
                p.getMetadata().setOrientacion(nueva);
            }

            // Ajustar fondos existentes a las nuevas dimensiones base
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
        double cardHeightScaled = EditorCanvasManager.CARD_WIDTH * currentZoom; // En vertical, la altura base es
                                                                                // CARD_WIDTH
        double maxHeight = canvasContainer.getHeight() - 80; // Margen superior e inferior

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
    // Acciones de edición (añadir / eliminar elementos)
    // =====================================================

    private void onAñadirTexto() {
        elementActionsController.añadirTexto();
    }

    private void onAñadirImagen() {
        elementActionsController.añadirImagen();
    }

    private void onAñadirForma(FormaElemento.TipoForma tipo) {
        elementActionsController.añadirForma(tipo);
    }

    private void onAñadirCodigo(TipoCodigo tipo) {
        elementActionsController.añadirCodigo(tipo);
    }

    public void onEliminarElemento() {
        elementActionsController.eliminarElemento();
    }

    private void onAñadirFondo() {
        elementActionsController.añadirFondo();
    }

    // =====================================================
    // Panel lateral (mostrar/ocultar)
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

    /*
     * Anima la apertura/cierre del pane
     * lateral (overlay).
     * Fade + slide horizontal. Timing refinado para una transición más suave.
     */
    private void togglePanel(Region panel, boolean show) {
        AnimationHelper.togglePanel(panel, show);
    }

    /*
     * Centra visualmente el canvas cuando se abre/cierra el panel lateral.
     * Usa la misma duración que togglePanel para que el desplazamiento y la
     * apertura del panel se vean sincronizados.
     */
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

    /* Abre el diálogo para editar o eliminar un proyecto existente. */
    private void abrirDialogoEditarProyecto(Proyecto proyecto) {
        boolean changed = projectActionsController.editarProyecto(proyecto);
        if (changed) {
            actualizarLabelProyecto(false);
            // Si la BD vinculada pudo haber cambiado, reconstruir paneles
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

                // Si la escena no está lista O indicamos no animar, configuramos el estado
                // inicial de golpe
                if (lblProyectoActivo.getScene() == null || !animate) {
                    if (viewModel.isProjectChipCollapsed()) {
                        lblProyectoActivo.setText(inicial);
                        lblProyectoActivo.setPrefWidth(28);
                        lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER);
                        lblProyectoActivo
                                .setStyle("-fx-background-color: #0c0d16; -fx-text-fill: #4c5171; -fx-padding: 0;"); // Muy
                                                                                                                     // oscuro
                    } else {
                        lblProyectoActivo.setText("Proyecto · " + nombre);
                        lblProyectoActivo.setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
                        lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        lblProyectoActivo.setStyle("");
                    }
                    return;
                }

                // Transición suave
                javafx.animation.Timeline timeline = new javafx.animation.Timeline();
                if (viewModel.isProjectChipCollapsed()) {
                    // Animación de CERRAR: mantener el texto completo para que se recorte desde la
                    // derecha
                    lblProyectoActivo.setText("Proyecto · " + nombre);
                    lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    lblProyectoActivo
                            .setStyle("-fx-background-color: #0c0d16; -fx-text-fill: #4c5171; -fx-padding: 0 0 0 15;");

                    // Fijar ancho actual para que empiece a encoger
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
                        // Al terminar de cerrar, ponemos la inicial y centramos
                        lblProyectoActivo.setText(inicial);
                        lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER);
                        lblProyectoActivo
                                .setStyle("-fx-background-color: #0c0d16; -fx-text-fill: #4c5171; -fx-padding: 0;");
                    });
                } else {
                    // Animación de ABRIR: preparar el texto completo desde el principio
                    lblProyectoActivo.setText("Proyecto · " + nombre);
                    lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    lblProyectoActivo.setStyle("");

                    // Medimos cuánto va a ocupar expandido
                    lblProyectoActivo.setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
                    lblProyectoActivo.applyCss();
                    lblProyectoActivo.layout();
                    double targetWidth = lblProyectoActivo.prefWidth(-1);

                    // Empezamos desde el cuadradito
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