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
import com.tpsstudio.util.ImageUtils;
import com.tpsstudio.service.DesignValidatorService;
import com.tpsstudio.service.ImpresionService;
import com.tpsstudio.model.print.SalidaImpresion;
import com.tpsstudio.model.print.SalidaImpresoraDirecta;
import com.tpsstudio.model.print.SalidaPDFSistema;
import com.tpsstudio.model.print.TrabajoImpresion;
import com.tpsstudio.util.TPSToast;
import com.tpsstudio.view.controllers.sub.ElementActionsController;
import com.tpsstudio.view.controllers.sub.ProjectActionsController;
import com.tpsstudio.view.dialogs.ImpresionDialog;
import com.tpsstudio.viewmodel.MainViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import com.tpsstudio.view.managers.ShortcutManager;
import java.io.File;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
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

    // Estado para el chip de proyecto colapsable (Cerrado por defecto)
    private boolean isProjectChipCollapsed = true;

    // =====================================================
    // Inicialización
    // =====================================================
    @FXML
    private void initialize() {
        setupCanvas();
        initUI();

        lblProyectoActivo.setOnMouseClicked(e -> {
            isProjectChipCollapsed = !isProjectChipCollapsed;
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

    @FXML
    private void onLogout() {
        // Confirmar cierre de sesión con diálogo estándar
        Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cerrar Sesión");
        alert.setHeaderText("Vas a salir de la sesión actual.");
        alert.setContentText("¿Estás seguro de que quieres volver al login?");

        // Estilo nativo para el diálogo

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                // 1. Limpiar sesión en el servicio
                com.tpsstudio.service.AuthService.getInstance().logout();

                // 2. Obtener Stage y Escena actual
                Stage stage = (Stage) leftPanel.getScene().getWindow();
                Scene scene = leftPanel.getScene();

                // Capturar el root actual (Main View) para la animación de salida
                javafx.scene.Parent mainView = scene.getRoot();

                // 3. Cargar la vista de Login (todavía invisible)
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                        getClass().getResource("/fxml/login_view.fxml"));
                javafx.scene.Parent loginView = loader.load();
                loginView.setOpacity(0);
                loginView.setScaleX(1.05);
                loginView.setScaleY(1.05);

                // 4. Crear contenedor de transición
                javafx.scene.layout.StackPane transitionContainer = new javafx.scene.layout.StackPane();
                transitionContainer.getStyleClass().add("transition-overlay");

                // Intercambiamos el root por el contenedor temporal
                scene.setRoot(transitionContainer);
                transitionContainer.getChildren().addAll(loginView, mainView);

                // 5. SECUENCIA DE ANIMACIÓN
                javafx.application.Platform.runLater(() -> {
                    Duration duration = Duration.millis(300);

                    // --- Salida (Main) ---
                    javafx.animation.FadeTransition fadeMain = new javafx.animation.FadeTransition(duration, mainView);
                    fadeMain.setFromValue(1.0);
                    fadeMain.setToValue(0.0);

                    fadeMain.setOnFinished(e -> {
                        // LIMPIEZA CLAVE: Desvinculamos el loginView del contenedor antes de ponerlo
                        // como ROOT
                        transitionContainer.getChildren().clear();

                        // Reset de ventana: unmaximize y restaurar tamaño de contenido
                        stage.setMaximized(false);
                        stage.setMinWidth(0);
                        stage.setMinHeight(0);

                        // Aplicar LoginView como root definitivo
                        scene.setRoot(loginView);

                        // Reaplicar CSS al Login (asegurarnos de que hereda el estilo)
                        if (!scene.getStylesheets().contains(getClass().getResource("/css/app.css").toExternalForm())) {
                            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
                        }

                        // Forzar el tamaño exacto del contenido (760x580)
                        stage.setWidth(776); // Aproximación del marco de Windows (760 + decoraciones)
                        stage.setHeight(619); // Aproximación del marco de Windows (580 + decoraciones)

                        // Ajuste fino: sizeToScene es más preciso si el contenido está listo
                        javafx.application.Platform.runLater(() -> {
                            stage.sizeToScene();
                            stage.centerOnScreen();
                        });

                        // --- Entrada (Login) ---
                        javafx.animation.FadeTransition fadeLogin = new javafx.animation.FadeTransition(duration,
                                loginView);
                        fadeLogin.setFromValue(0.0);
                        fadeLogin.setToValue(1.0);

                        javafx.animation.ScaleTransition scaleLogin = new javafx.animation.ScaleTransition(duration,
                                loginView);
                        scaleLogin.setFromX(1.05);
                        scaleLogin.setFromY(1.05);
                        scaleLogin.setToX(1.0);
                        scaleLogin.setToY(1.0);

                        new javafx.animation.ParallelTransition(fadeLogin, scaleLogin).play();
                    });

                    fadeMain.play();
                });

            } catch (Exception e) {
                e.printStackTrace();
                // Fallback de emergencia: recarga total
                try {
                    Stage stage = (Stage) leftPanel.getScene().getWindow();
                    javafx.scene.Parent root = new javafx.fxml.FXMLLoader(
                            getClass().getResource("/fxml/login_view.fxml")).load();
                    javafx.scene.Scene newScene = new javafx.scene.Scene(root, 760, 580);
                    newScene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
                    stage.setScene(newScene);
                    stage.setMaximized(false);
                    stage.sizeToScene();
                    stage.centerOnScreen();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private void setupCanvas() {

        // -------------------------------------------------
        // ProjectManager (estado del proyecto y cambios)
        // -------------------------------------------------
        projectManager = new ProjectManager();

        // Inicializar gestor de categorías con el usuario actual
        String currentUser = com.tpsstudio.service.AuthService.getInstance().getCurrentUser();
        etiquetasManager = new EtiquetasManager(currentUser);

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
                TPSToast.mostrar(owner, mensaje, null, TPSToast.Tipo.EXITO);
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
                this::ensurePropertiesPanelVisible,
                this::mostrarDialogoFitMode);

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

        propertiesPanelController.setOnEditExternal(this::abrirEditorExterno);
        propertiesPanelController.setOnReload(this::recargarFondo);
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
                    if (sel != TipoTroquel.NINGUNO && !toggleGuias.isSelected()) {
                        toggleGuias.setSelected(true);
                        canvasManager.setMostrarGuias(true);
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
            ejecutarCrossFade(() -> {
                viewModel.setProyectoActual(proyecto);
                projectManager.setProyectoActual(proyecto);
                canvasManager.setProyectoActual(proyecto);

                // Adjust zoom if the project is vertical
                if (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) {
                    ajustarZoomVerticalSiNecesario();
                }

                checkDesignWarnings();
                dibujarCanvas();
            });
        });

        modeManager.setOnEditExternal(this::abrirEditorExterno);
        modeManager.setOnReload(this::recargarFondo);

        modeManager.setOnToggleLock(elemento -> {
            elemento.setLocked(!elemento.isLocked());
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                // Reconstruir para que el icono de candado se actualice en la celda
                modeManager.rebuildLayersPanel(viewModel.getProyectoActual(), viewModel.getElementoSeleccionado());
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
        // Zoom
        // -------------------------------------------------
        actualizarZoom();

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
            actualizarZoom();
        }
    }

    @FXML
    private void onZoomOut() {
        if (viewModel.getZoomLevel() > 0.5) {
            viewModel.setZoomLevel(viewModel.getZoomLevel() - 0.1);
            actualizarZoom();
        }
    }

    private void actualizarZoom() {
        lblZoom.setText(String.format("%.0f%%", viewModel.getZoomLevel() * 100));
        canvasManager.setZoomLevel(viewModel.getZoomLevel());
        posicionarSelectorCara();
        dibujarCanvas();
    }

    // =====================================================
    // Toggles / opciones visuales
    // =====================================================

    @FXML
    private void onToggleGuias() {
        canvasManager.setMostrarGuias(toggleGuias.isSelected());
        dibujarCanvas();
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

    /*
     * Muestra un diálogo para elegir cómo se ajusta el fondo a la tarjeta:
     * - Con sangre (BLEED): cubre CR80 + sangrado (2mm por lado)
     * - Sin sangre (FINAL): cubre solo el tamaño final CR80
     *
     * Si el usuario marca "No volver a preguntar", se guarda la preferencia en el
     * proyecto.
     */
    private FondoFitMode mostrarDialogoFitMode() {

        Dialog<FondoFitMode> dialog = new Dialog<>();
        dialog.setTitle("Modo de Ajuste del Fondo");
        dialog.setHeaderText("¿Cómo desea ajustar el fondo a la tarjeta?");
        dialog.initOwner(canvas.getScene().getWindow());
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/dialogs.css").toExternalForm());

        // -------------------------------------------------
        // Botones
        // -------------------------------------------------
        ButtonType btnBleed = new ButtonType("Con sangre", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnFinal = new ButtonType("Sin sangre", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnBleed, btnFinal, btnCancelar);

        // -------------------------------------------------
        // Contenido visual
        // -------------------------------------------------
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label lblExplicacion = new Label("El fondo puede ajustarse de dos formas:");
        lblExplicacion.getStyleClass().add("lbl-section");

        VBox opcionBleed = new VBox(5);
        Label lblBleedTitulo = new Label("✓ Con sangre (CR80 + 2mm por lado)");
        lblBleedTitulo.getStyleClass().add("lbl-section");
        Label lblBleedDesc = new Label("Cubre el área completa incluyendo 2mm de sangrado por lado (89.60 × 57.98 mm)");
        lblBleedDesc.getStyleClass().add("lbl-hint");
        Label lblBleedUso = new Label("Recomendado para fondos que se extienden hasta el borde");
        lblBleedUso.getStyleClass().add("lbl-hint");
        opcionBleed.getChildren().addAll(lblBleedTitulo, lblBleedDesc, lblBleedUso);

        VBox opcionFinal = new VBox(5);
        Label lblFinalTitulo = new Label("✓ Sin sangre (CR80 final)");
        lblFinalTitulo.getStyleClass().add("lbl-section");
        Label lblFinalDesc = new Label("Cubre solo el área final de la tarjeta (85.60 × 53.98 mm)");
        lblFinalDesc.getStyleClass().add("lbl-hint");
        Label lblFinalUso = new Label("Útil para fondos que no deben llegar al borde");
        lblFinalUso.getStyleClass().add("lbl-hint");
        opcionFinal.getChildren().addAll(lblFinalTitulo, lblFinalDesc, lblFinalUso);

        CheckBox chkNoPreguntar = new CheckBox("No volver a preguntar en este proyecto");
        chkNoPreguntar.getStyleClass().add("lbl-hint");

        content.getChildren().addAll(
                lblExplicacion, new Separator(),
                opcionBleed, new Separator(),
                opcionFinal, new Separator(),
                chkNoPreguntar);

        dialog.getDialogPane().setContent(content);

        // -------------------------------------------------
        // Conversión de resultado + guardado de preferencia
        // -------------------------------------------------
        dialog.setResultConverter(buttonType -> {

            if (chkNoPreguntar.isSelected() && viewModel.getProyectoActual() != null) {
                viewModel.getProyectoActual().setNoVolverAPreguntarFondo(true);

                if (buttonType == btnBleed) {
                    viewModel.getProyectoActual().setFondoFitModePreferido(FondoFitMode.BLEED);
                } else if (buttonType == btnFinal) {
                    viewModel.getProyectoActual().setFondoFitModePreferido(FondoFitMode.FINAL);
                }
            }

            if (buttonType == btnBleed)
                return FondoFitMode.BLEED;
            if (buttonType == btnFinal)
                return FondoFitMode.FINAL;

            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    /*
     * Abre el archivo del fondo en el editor externo configurado (si existe),
     * o en el editor predeterminado del sistema.
     *
     * Nota: el lanzamiento se hace desacoplado para evitar problemas al guardar
     * (por ejemplo, Photoshop "bloqueado" por herencias de handles del proceso
     * Java).
     */
    private void abrirEditorExterno(ImagenFondoElemento fondo) {
        new com.tpsstudio.service.ExternalEditorService(viewModel.getProyectoActual()).abrirEditor(fondo);
    }

    /*
     * Recarga la imagen del fondo desde el disco.
     * Se usa tras editar el archivo en un programa externo (Photoshop, etc.).
     */
    private void recargarFondo(ImagenFondoElemento fondo) {

        if (fondo == null || fondo.getRutaArchivo() == null) {
            Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.WARNING);
            alert.setTitle("Advertencia");
            alert.setHeaderText("No se puede recargar");
            alert.setContentText("El fondo no tiene una ruta de archivo asociada.");
            alert.showAndWait();
            return;
        }

        File file = new File(fondo.getRutaArchivo());
        if (!file.exists()) {
            Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Archivo no encontrado");
            alert.setContentText(
                    "El archivo " + file.getName() + " no existe en el disco.\n" +
                            "Se mantendrá la versión anterior en memoria.");
            alert.showAndWait();
            return;
        }

        try {
            // Cargar sin bloquear el archivo (evita problemas tras editar con apps
            // externas)
            Image nuevaImagen = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());
            if (nuevaImagen == null) {
                throw new Exception("No se pudo cargar la imagen (resultado null)");
            }

            fondo.setImagen(nuevaImagen);

            // Preguntar modo de ajuste (sangre vs final).
            // Esto es útil si el usuario cambió el tamaño / recortó en el editor externo.
            FondoFitMode nuevoModo = mostrarDialogoFitMode();
            if (nuevoModo != null) {
                fondo.setFitMode(nuevoModo);
            }

            fondo.ajustarATamaño(
                    EditorCanvasManager.CARD_WIDTH,
                    EditorCanvasManager.CARD_HEIGHT,
                    EditorCanvasManager.BLEED_MARGIN);

            dibujarCanvas();

            Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.INFORMATION);
            alert.setTitle("Éxito");
            alert.setHeaderText("Fondo recargado");
            alert.setContentText("La imagen se ha recargado y ajustado correctamente.");
            alert.showAndWait();

        } catch (Exception ex) {
            ex.printStackTrace();

            Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No se pudo recargar la imagen");
            alert.setContentText(
                    "Error al cargar el archivo: " + ex.getMessage() + "\n" +
                            "Se mantendrá la versión anterior en memoria.");
            alert.showAndWait();
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
        ejecutarCrossFade(() -> {
            viewModel.getProyectoActual().setMostrandoFrente(true);
            viewModel.setElementoSeleccionado(null);
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), null);
            }
            dibujarCanvas();
        });
    }

    @FXML
    private void onShowDorso() {
        if (viewModel.getProyectoActual() == null)
            return;
        ejecutarCrossFade(() -> {
            viewModel.getProyectoActual().setMostrandoFrente(false);
            viewModel.setElementoSeleccionado(null);
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(viewModel.getProyectoActual(), null);
            }
            dibujarCanvas();
        });
    }

    @FXML
    private void onToggleOrientacion() {
        Proyecto p = viewModel.getProyectoActual();
        if (p == null)
            return;

        ejecutarGiroTransition(() -> {
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
        });
    }

    /**
     * Transición específica para el giro de la tarjeta.
     * Captura el estado actual y lo rota 90 grados mientras se desvanece.
     */
    private void ejecutarGiroTransition(Runnable accionCambio) {
        if (canvas == null || canvasContainer == null) {
            accionCambio.run();
            return;
        }

        try {
            // 1. Captura transparente
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.TRANSPARENT);
            javafx.scene.image.WritableImage snapshot = canvas.snapshot(params, null);

            javafx.scene.image.ImageView tempView = new javafx.scene.image.ImageView(snapshot);
            tempView.setMouseTransparent(true);
            tempView.setManaged(false); // Posicionamiento manual para evitar saltos de layout

            // Posición exacta actual
            javafx.geometry.Bounds bounds = canvas.getBoundsInParent();
            tempView.setLayoutX(bounds.getMinX());
            tempView.setLayoutY(bounds.getMinY());

            canvasContainer.getChildren().add(tempView);

            // 2. Animación combinada: Rotación + Desvanecimiento
            // Determinamos el sentido del giro ANTES de ejecutar el cambio
            Proyecto proyectoActual = viewModel.getProyectoActual();
            if (proyectoActual == null)
                return;
            boolean aVertical = (proyectoActual.getOrientacion() == com.tpsstudio.model.enums.Orientacion.HORIZONTAL);
            double anguloFinal = aVertical ? 90 : -90;

            // EJECUTAR EL CAMBIO YA (Simultáneo a la animación como prefiere el usuario)
            accionCambio.run();

            javafx.animation.RotateTransition rt = new javafx.animation.RotateTransition(
                    javafx.util.Duration.millis(450), tempView);
            rt.setByAngle(anguloFinal);

            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(450),
                    tempView);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);

            javafx.animation.ParallelTransition parallel = new javafx.animation.ParallelTransition(rt, ft);
            parallel.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
            parallel.setOnFinished(e -> canvasContainer.getChildren().remove(tempView));
            parallel.play();

        } catch (Exception e) {
            accionCambio.run();
        }
    }

    /**
     * Realiza una transición de fundido cruzado (cross-fade) entre el estado actual
     * del canvas y el nuevo estado tras ejecutar la acción de cambio.
     */
    private void ejecutarCrossFade(Runnable accionCambio) {
        if (canvas == null || canvasContainer == null) {
            accionCambio.run();
            return;
        }

        try {
            // Si el canvas no está en escena o es el primer proyecto, hacemos un fade-in
            // tradicional
            if (canvas.getScene() == null || viewModel.getProyectoActual() == null) {
                accionCambio.run();
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                        javafx.util.Duration.millis(450), canvas);
                ft.setFromValue(0.0);
                ft.setToValue(1.0);
                ft.play();
                return;
            }

            // 1. Capturar el estado actual del canvas
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.TRANSPARENT);
            javafx.scene.image.WritableImage snapshot = canvas.snapshot(params, null);

            javafx.scene.image.ImageView tempView = new javafx.scene.image.ImageView(snapshot);
            tempView.setMouseTransparent(true);
            tempView.setManaged(false); // Crítico: evita que el ImageView se mueva si el panel derecho se abre/cierra

            // Fijar posición absoluta inicial basada en los bounds del canvas
            javafx.geometry.Bounds bounds = canvas.getBoundsInParent();
            tempView.setLayoutX(bounds.getMinX());
            tempView.setLayoutY(bounds.getMinY());

            canvasContainer.getChildren().add(tempView);

            // 2. Ejecutar el cambio real
            accionCambio.run();

            // 3. Animar el desvanecimiento
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(450),
                    tempView);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
            ft.setOnFinished(e -> canvasContainer.getChildren().remove(tempView));
            ft.play();

        } catch (Exception e) {
            accionCambio.run();
        }
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
                actualizarZoom();
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
                    if (isProjectChipCollapsed) {
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
                if (isProjectChipCollapsed) {
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