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

    // ViewModel: estado observable de la aplicación
    private final MainViewModel viewModel = new MainViewModel();

    // Sub-controlador de acciones de proyecto (crear, abrir, guardar, exportar, imprimir)
    private ProjectActionsController projectActionsController;

    // Sub-controlador de acciones de elementos (añadir texto, imagen, forma, fondo; eliminar)
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
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/login_view.fxml"));
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
                        // LIMPIEZA CLAVE: Desvinculamos el loginView del contenedor antes de ponerlo como ROOT
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
                        javafx.animation.FadeTransition fadeLogin = new javafx.animation.FadeTransition(duration, loginView);
                        fadeLogin.setFromValue(0.0);
                        fadeLogin.setToValue(1.0);
                        
                        javafx.animation.ScaleTransition scaleLogin = new javafx.animation.ScaleTransition(duration, loginView);
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
                    javafx.scene.Parent root = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/login_view.fxml")).load();
                    javafx.scene.Scene newScene = new javafx.scene.Scene(root, 760, 580);
                    newScene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
                    stage.setScene(newScene);
                    stage.setMaximized(false);
                    stage.sizeToScene();
                    stage.centerOnScreen();
                } catch (Exception ex) { ex.printStackTrace(); }
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
            // Fuerza la reconstrucción de paneles cuando cambia la estructura del diseño
            buildEditPanels();
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
        propertiesPanelController = new PropertiesPanelController(canvas);

        propertiesPanelController.setOnPropertyChanged(() -> modeManager.switchMode(
                viewModel.getCurrentMode(), viewModel.getProyectoActual(),
                viewModel.getElementoSeleccionado(), projectManager.getProyectos()));

        propertiesPanelController.setOnCanvasRedrawNeeded(() -> {
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
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
                
                if (togglePropiedades.isSelected()) {
                    modeManager.refreshPropertiesPanel(viewModel.getElementoSeleccionado(), viewModel.getProyectoActual());
                }
            }
            dibujarCanvas();
        });

        modeManager.setOnProjectSelected(proyecto -> {
            viewModel.setProyectoActual(proyecto);
            projectManager.setProyectoActual(proyecto);
            canvasManager.setProyectoActual(proyecto);
            
            // Animación de entrada para la tarjeta
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(450), canvas);
            ft.setFromValue(0.4);
            ft.setToValue(1.0);
            ft.play();
            
            dibujarCanvas();
        });

        modeManager.setOnEditExternal(this::abrirEditorExterno);
        modeManager.setOnReload(this::recargarFondo);

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
        });

        canvasManager.setOnElementTransformed(() -> {
            viewModel.setElementoSeleccionado(canvasManager.getElementoSeleccionado());
            if (propertiesPanelController != null && viewModel.getElementoSeleccionado() != null) {
                propertiesPanelController.updatePositionFields(viewModel.getElementoSeleccionado());
            }
        });

        canvasManager.setOnCanvasChanged(() -> {
            viewModel.setElementoSeleccionado(canvasManager.getElementoSeleccionado());
            buildEditPanels();
            canvasManager.dibujarCanvas();
        });

        // -------------------------------------------------
        // Input (mouse y teclado)
        // -------------------------------------------------

        // Importante: configurar mouse handlers para drag & resize
        canvasManager.setupMouseHandlers();

        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE && viewModel.getElementoSeleccionado() != null) {
                onEliminarElemento();
            }
        });

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
        if (selectorCaraBox != null) selectorCaraBox.widthProperty().addListener((obs, old, nw) -> posicionarSelectorCara());
        
        dibujarCanvas();
    }

    private void posicionarSelectorCara() {
        if (selectorCaraBox != null && canvasContainer != null) {
            double zoom = viewModel.getZoomLevel();
            double cardScaledWidth = EditorCanvasManager.CARD_WIDTH * zoom;
            double cardScaledHeight = EditorCanvasManager.CARD_HEIGHT * zoom;
            double bleedScaled = EditorCanvasManager.BLEED_MARGIN * zoom;
            
            double cw = canvasContainer.getWidth();
            double ch = canvasContainer.getHeight();
            
            // Posición de la tarjeta absoluta dentro del contenedor (asumiendo centrado)
            double cardX = (cw / 2) - (cardScaledWidth / 2);
            double cardY = (ch / 2) - (cardScaledHeight / 2);
            
            // Borde superior de la tarjeta con sangre
            double topEdge = cardY - bleedScaled;
            
            double offset = 50;
            // Restaurar la posición "perfecta" original a la izquierda
            selectorCaraBox.setLayoutX(cardX - bleedScaled - 20);
            selectorCaraBox.setLayoutY(topEdge - offset);
            
            // Dejar que el HBox tome el tamaño natural de sus botones
            selectorCaraBox.autosize();
        }
    }

    private void onValidarDiseno() {
        if (viewModel.getProyectoActual() == null)
            return;

        DesignValidatorService validator = new DesignValidatorService();
        java.util.List<String> avisos = validator.validarDiseno(viewModel.getProyectoActual());

        Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.INFORMATION);
        alert.setTitle("Validación de Diseño");
        alert.setHeaderText(avisos.isEmpty() ? "¡Diseño correcto!" : "Se han encontrado problemas potenciales:");

        if (avisos.isEmpty()) {
            alert.setContentText(
                    "No se han detectado problemas de resolución ni elementos fuera de las zonas seguras.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String aviso : avisos) {
                sb.append("• ").append(aviso).append("\n\n");
            }
            javafx.scene.control.TextArea textArea = new javafx.scene.control.TextArea(sb.toString().trim());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            textArea.setPrefHeight(250);

            javafx.scene.layout.VBox expContent = new javafx.scene.layout.VBox(textArea);
            expContent.setMaxWidth(Double.MAX_VALUE);
            javafx.scene.layout.VBox.setVgrow(textArea, javafx.scene.layout.Priority.ALWAYS);
            alert.getDialogPane().setContent(expContent);
            alert.getDialogPane().setPrefWidth(500);
        }

        alert.showAndWait();
    }

    private void onDescargarPlantilla() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Descargar Plantilla CR80");
        fileChooser.setInitialFileName("Plantilla_CR80_TPS.pdf");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));

        File file = fileChooser.showSaveDialog(canvas.getScene().getWindow());
        if (file == null)
            return;

        try {
            java.io.InputStream in = getClass().getResourceAsStream("/pdf/Plantilla_CR80_TPS.pdf");
            if (in != null) {
                java.nio.file.Files.copy(in, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                in.close();
                TPSToast.mostrar(canvas.getScene().getWindow(), "Plantilla descargada con éxito", null,
                        TPSToast.Tipo.EXITO);
            } else {
                TPSToast.mostrar(canvas.getScene().getWindow(),
                        "No se encontró el recurso interno /pdf/Plantilla_CR80_TPS.pdf", null, TPSToast.Tipo.ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            TPSToast.mostrar(canvas.getScene().getWindow(), "Error al guardar la plantilla.", null,
                    TPSToast.Tipo.ERROR);
        }
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
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(300), bloqueContextual);
                ft.setToValue(0.0);
                ft.setOnFinished(e -> {
                    bloqueContextual.setVisible(false);
                    bloqueContextual.setManaged(false);
                });
                ft.play();
            }
            cerrarPanelDerecho();
            
            // Forzar un segundo ajuste tras un breve delay para asegurar que el layout se ha asentado
            javafx.application.Platform.runLater(() -> {
                javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(Duration.millis(50));
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
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(400), bloqueContextual);
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
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(400), selectorCaraBox);
                ft.setToValue(1.0);
                ft.play();
            } else if (!shouldShow && selectorCaraBox.isVisible() && selectorCaraBox.getOpacity() > 0) {
                selectorCaraBox.setOpacity(0.0); // Prevenimos multiples animaciones superpuestas
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(300), selectorCaraBox);
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

        if (fondo == null || fondo.getRutaArchivo() == null) {
            Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.WARNING);
            alert.setTitle("Advertencia");
            alert.setHeaderText("No se puede abrir el editor externo");
            alert.setContentText("El fondo no tiene una ruta de archivo asociada.");
            alert.showAndWait();
            return;
        }

        // -------------------------------------------------
        // 1) Localizar el archivo real en disco
        // -------------------------------------------------
        File file = new File(fondo.getRutaArchivo());

        // Si la ruta guardada no existe, intentamos localizarla dentro del proyecto
        // (carpeta /Fondos)
        if (!file.exists() && viewModel.getProyectoActual() != null
                && viewModel.getProyectoActual().getMetadata() != null) {

            String projectDir = viewModel.getProyectoActual().getMetadata().getCarpetaProyecto();
            if (projectDir != null) {

                File fondosDir = new File(projectDir, "Fondos");
                String originalName = file.getName();

                // Opción A: mismo nombre en /Fondos
                File optionA = new File(fondosDir, originalName);

                // Opción B: nombre con sufijo _FRENTE o _DORSO
                String nameNoExt = originalName;
                String ext = "";
                int dotIndex = originalName.lastIndexOf('.');
                if (dotIndex > 0) {
                    nameNoExt = originalName.substring(0, dotIndex);
                    ext = originalName.substring(dotIndex); // incluye el punto
                }

                String suffix = "";
                if (fondo == viewModel.getProyectoActual().getFondoFrente()) {
                    suffix = "_FRENTE";
                } else if (fondo == viewModel.getProyectoActual().getFondoDorso()) {
                    suffix = "_DORSO";
                }

                File optionB = new File(fondosDir, nameNoExt + suffix + ext);

                // Elegir la ruta que exista y actualizarla en el objeto (para que "Recargar"
                // funcione bien)
                if (optionB.exists()) {
                    file = optionB;
                    fondo.setRutaArchivo(file.getAbsolutePath());
                } else if (optionA.exists()) {
                    file = optionA;
                    fondo.setRutaArchivo(file.getAbsolutePath());
                }
            }
        }

        if (!file.exists()) {
            Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Archivo no encontrado");
            alert.setContentText(
                    "El archivo " + file.getName() + " no existe en el disco.\n\n" +
                            "Buscado en: " + file.getAbsolutePath());
            alert.showAndWait();
            return;
        }

        // -------------------------------------------------
        // 2) Abrir con editor personalizado o predeterminado
        // -------------------------------------------------
        try {
            SettingsManager settings = new SettingsManager();
            String customEditor = settings.getExternalEditorPath();
            boolean opened = false;

            if (customEditor != null) {
                File editorFile = new File(customEditor);

                if (editorFile.exists()) {

                    Alert aviso = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.INFORMATION);
                    aviso.setTitle("Editando externamente");
                    aviso.setHeaderText("Abriendo con " + settings.getExternalEditorName() + "...");
                    aviso.setContentText(
                            "Puedes editar la imagen mientras TPS Studio permanece abierto.\n" +
                                    "Cuando guardes los cambios en el editor, pulsa 'Recargar' aquí para ver el resultado.");
                    aviso.show();

                    // Lanzamiento desacoplado (Windows)
                    String[] cmd = { "cmd", "/c", "start", "\"\"", customEditor, file.getAbsolutePath() };
                    new ProcessBuilder(cmd).start();
                    opened = true;
                }
            }

            if (!opened) {
                Alert aviso = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.INFORMATION);
                aviso.setTitle("Editando externamente");
                aviso.setHeaderText("Abriendo editor predeterminado...");
                aviso.setContentText(
                        "Puedes editar la imagen mientras TPS Studio permanece abierto.\n" +
                                "Cuando guardes los cambios, pulsa 'Recargar' aquí para ver el resultado.");
                aviso.show();

                // Intento desacoplado también para el editor por defecto
                try {
                    String[] cmd = { "cmd", "/c", "start", "\"\"", file.getAbsolutePath() };
                    new ProcessBuilder(cmd).start();
                } catch (Exception e) {
                    // Fallback a Desktop si falla el cmd (raro en Windows)
                    java.awt.Desktop.getDesktop().open(file);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();

            Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No se pudo abrir el editor externo");
            alert.setContentText("Error: " + ex.getMessage());
            alert.showAndWait();
        }
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
     * Abre el diálogo de impresión y, si el usuario confirma, genera un PDF temporal
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
        if (viewModel.getProyectoActual() == null) return;
        viewModel.getProyectoActual().setMostrandoFrente(true);
        viewModel.setElementoSeleccionado(null);
        if (viewModel.getCurrentMode() == AppMode.DESIGN) {
            modeManager.refreshLayersPanel(viewModel.getProyectoActual(), null);
        }
        dibujarCanvas();
    }

    @FXML
    private void onShowDorso() {
        if (viewModel.getProyectoActual() == null) return;
        viewModel.getProyectoActual().setMostrandoFrente(false);
        viewModel.setElementoSeleccionado(null);
        if (viewModel.getCurrentMode() == AppMode.DESIGN) {
            modeManager.refreshLayersPanel(viewModel.getProyectoActual(), null);
        }
        dibujarCanvas();
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

    private void onEliminarElemento() {
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
        projectActionsController.editarProyecto(proyecto);
        actualizarLabelProyecto(false);
        // Si la BD vinculada pudo haber cambiado, reconstruir paneles
        if (viewModel.getCurrentMode() == AppMode.DESIGN) {
            buildEditPanels();
        }
    }

    private void actualizarLabelProyecto(boolean animate) {
        if (lblProyectoActivo != null) {
            if (viewModel.getProyectoActual() != null) {
                String nombre = viewModel.getProyectoActual().getNombre();
                String inicial = nombre.isEmpty() ? "P" : nombre.substring(0, 1).toUpperCase();

                // Si la escena no está lista O indicamos no animar, configuramos el estado inicial de golpe
                if (lblProyectoActivo.getScene() == null || !animate) {
                    if (isProjectChipCollapsed) {
                        lblProyectoActivo.setText(inicial);
                        lblProyectoActivo.setPrefWidth(28);
                        lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER);
                        lblProyectoActivo.setStyle("-fx-background-color: #0c0d16; -fx-text-fill: #4c5171; -fx-padding: 0;"); // Muy oscuro
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
                    // Animación de CERRAR: mantener el texto completo para que se recorte desde la derecha
                    lblProyectoActivo.setText("Proyecto · " + nombre);
                    lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    lblProyectoActivo.setStyle("-fx-background-color: #0c0d16; -fx-text-fill: #4c5171; -fx-padding: 0 0 0 15;"); 
                    
                    // Fijar ancho actual para que empiece a encoger
                    double currentWidth = lblProyectoActivo.getWidth();
                    if (currentWidth > 30) {
                        lblProyectoActivo.setPrefWidth(currentWidth);
                    }
                    
                    javafx.animation.KeyValue kv = new javafx.animation.KeyValue(lblProyectoActivo.prefWidthProperty(), 28, javafx.animation.Interpolator.EASE_BOTH);
                    javafx.animation.KeyFrame kf = new javafx.animation.KeyFrame(javafx.util.Duration.millis(250), kv);
                    timeline.getKeyFrames().add(kf);
                    
                    timeline.setOnFinished(e -> {
                        // Al terminar de cerrar, ponemos la inicial y centramos
                        lblProyectoActivo.setText(inicial);
                        lblProyectoActivo.setAlignment(javafx.geometry.Pos.CENTER);
                        lblProyectoActivo.setStyle("-fx-background-color: #0c0d16; -fx-text-fill: #4c5171; -fx-padding: 0;");
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
                    
                    javafx.animation.KeyValue kv = new javafx.animation.KeyValue(lblProyectoActivo.prefWidthProperty(), targetWidth, javafx.animation.Interpolator.EASE_BOTH);
                    javafx.animation.KeyFrame kf = new javafx.animation.KeyFrame(javafx.util.Duration.millis(250), kv);
                    timeline.getKeyFrames().add(kf);
                    
                    timeline.setOnFinished(e -> lblProyectoActivo.setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE));
                }
                timeline.play();

            } else {
                lblProyectoActivo.setText("");
            }
            if (selectorCaraBox != null) {
                selectorCaraBox.autosize();
            }
        }
    }
}