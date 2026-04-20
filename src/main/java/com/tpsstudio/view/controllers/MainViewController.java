package com.tpsstudio.view.controllers;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.*;
import com.tpsstudio.service.ProjectManager;
import com.tpsstudio.view.managers.EditorCanvasManager;
import com.tpsstudio.view.managers.ModeManager;
import com.tpsstudio.view.managers.PropertiesPanelController;
import com.tpsstudio.view.dialogs.EditarProyectoDialog;
import com.tpsstudio.view.dialogs.NuevoProyectoDialog;
import com.tpsstudio.service.SettingsManager;
import com.tpsstudio.util.AnimationHelper;
import com.tpsstudio.util.ImageUtils;
import com.tpsstudio.util.TPSToast;
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
    private ToggleButton toggleFrenteDorso;
    @FXML
    private ToggleButton toggleGuias;
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

    // =====================================================
    // Inicialización
    // =====================================================
    @FXML
    private void initialize() {
        setupCanvas();
        initUI();

        projectManager.cargarProyectosRecientes(8);

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

    private void setupCanvas() {

        // -------------------------------------------------
        // ProjectManager (estado del proyecto y cambios)
        // -------------------------------------------------
        projectManager = new ProjectManager();

        projectManager.setOnProjectChanged(() -> {
            viewModel.setProyectoActual(projectManager.getProyectoActual());
            if (canvasManager != null) {
                canvasManager.setProyectoActual(viewModel.getProyectoActual());
            }
            sincronizarFuenteDatos();
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                buildEditPanels();
            }
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

        // -------------------------------------------------
        // ModeManager (montaje de paneles + acciones de UI)
        // -------------------------------------------------
        modeManager = new ModeManager(leftPanel, rightPanel, propertiesPanelController);

        modeManager.setOnAddText(this::onAñadirTexto);
        modeManager.setOnAddImage(this::onAñadirImagen);
        modeManager.setOnAddBackground(this::onAñadirFondo);

        modeManager.setOnNewCR80(this::onNuevoCR80);
        modeManager.setOnExport(this::onExportarProyecto);

        modeManager.setOnElementSelected(elemento -> {
            viewModel.setElementoSeleccionado(elemento);
            canvasManager.setElementoSeleccionado(elemento);

            if (elemento != null) {
                ensurePropertiesPanelVisible();
            }

            modeManager.switchMode(viewModel.getCurrentMode(), viewModel.getProyectoActual(),
                    viewModel.getElementoSeleccionado(), projectManager.getProyectos());
            dibujarCanvas();
        });

        modeManager.setOnProjectSelected(proyecto -> {
            viewModel.setProyectoActual(proyecto);
            projectManager.setProyectoActual(proyecto);
            canvasManager.setProyectoActual(proyecto);
            dibujarCanvas();
        });

        modeManager.setOnEditExternal(this::abrirEditorExterno);
        modeManager.setOnReload(this::recargarFondo);

        modeManager.setOnToggleLock(elemento -> {
            elemento.setLocked(!elemento.isLocked());
            modeManager.switchMode(viewModel.getCurrentMode(), viewModel.getProyectoActual(),
                    viewModel.getElementoSeleccionado(), projectManager.getProyectos());
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

        dibujarCanvas();
    }
    // =====================================================
    // Cambio de modo (Design / Production)
    // =====================================================

    @FXML
    private void onModeEdit() {
        switchMode(AppMode.DESIGN);
    }

    @FXML
    private void onModeExport() {
        switchMode(AppMode.PRODUCTION);
    }

    private javafx.animation.Timeline designPulse;
    private javafx.scene.effect.ColorAdjust designColorAdjust = new javafx.scene.effect.ColorAdjust();

    private void switchMode(AppMode newMode) {
        viewModel.setCurrentMode(newMode);
        canvasManager.setCurrentMode(newMode);

        // En Producción, los paneles de diseño no aplican: los ocultamos
        if (newMode == AppMode.PRODUCTION) {
            if (bloqueContextual != null) {
                bloqueContextual.setVisible(false);
                bloqueContextual.setManaged(false);
            }
            // Si había algún panel abierto, lo cerramos
            cerrarPanelDerecho();
            
            // Iniciar parpadeo (respiración suave) en el botón Diseño invitando a pulsarlo
            if (btnModeEdit != null) {
                btnModeEdit.setEffect(designColorAdjust);
                if (designPulse == null) {
                    designPulse = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(Duration.ZERO, new javafx.animation.KeyValue(designColorAdjust.brightnessProperty(), 0.0, Interpolator.EASE_BOTH)),
                        new javafx.animation.KeyFrame(Duration.millis(1200), new javafx.animation.KeyValue(designColorAdjust.brightnessProperty(), 0.35, Interpolator.EASE_BOTH)),
                        new javafx.animation.KeyFrame(Duration.millis(2400), new javafx.animation.KeyValue(designColorAdjust.brightnessProperty(), 0.0, Interpolator.EASE_BOTH))
                    );
                    designPulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
                }
                designPulse.play();
            }

        } else {
            if (bloqueContextual != null) {
                bloqueContextual.setVisible(true);
                bloqueContextual.setManaged(true);
            }
            
            // Apagar y frenar la respiración del botón Diseño
            if (designPulse != null) {
                designPulse.stop();
            }
            designColorAdjust.setBrightness(0.0);
            if (btnModeEdit != null) {
                btnModeEdit.setEffect(null);
            }
        }

        modeManager.switchMode(newMode, viewModel.getProyectoActual(),
                viewModel.getElementoSeleccionado(), projectManager.getProyectos());
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
            Alert alert = new Alert(Alert.AlertType.WARNING);
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
        if (!file.exists() && viewModel.getProyectoActual() != null && viewModel.getProyectoActual().getMetadata() != null) {

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
            Alert alert = new Alert(Alert.AlertType.ERROR);
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

                    Alert aviso = new Alert(Alert.AlertType.INFORMATION);
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
                Alert aviso = new Alert(Alert.AlertType.INFORMATION);
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

            Alert alert = new Alert(Alert.AlertType.ERROR);
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

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Éxito");
            alert.setHeaderText("Fondo recargado");
            alert.setContentText("La imagen se ha recargado y ajustado correctamente.");
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

    // =====================================================
    // Acciones de barra superior (Proyectos / exportación)
    // =====================================================

    @FXML
    private void onNuevoProyecto() {
        mostrarDialogoNuevoProyecto();
    }

    @FXML
    private void onAbrirProyecto() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Abrir Proyecto");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Archivos TPS", "*.tps"));

        File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (file != null) {
            projectManager.abrirProyectoDesdeArchivo(file);
        }
    }

    @FXML
    private void onGuardarProyecto() {
        // Guarda el proyecto actual (JSON / .tps)
        projectManager.guardarProyecto();
    }

    @FXML
    private void onExportarProyecto() {
        if (viewModel.getProyectoActual() == null) {
            new Alert(Alert.AlertType.WARNING, "Selecciona un proyecto antes de exportar.").showAndWait();
            return;
        }

        com.tpsstudio.model.project.FuenteDatos fd = projectManager.getFuenteDatos();
        int totalRegistros = (fd != null) ? fd.getTotalRegistros() : 1;

        // 1. Diálogo de configuración de exportación
        com.tpsstudio.view.dialogs.ExportDialog exportDialog = new com.tpsstudio.view.dialogs.ExportDialog(
                canvas.getScene().getWindow(), totalRegistros, viewModel.getProyectoActual().getNombre());
        java.util.Optional<com.tpsstudio.view.dialogs.ExportDialog.ExportConfig> cfg = exportDialog.showAndWait();
        if (cfg.isEmpty() || cfg.get() == null)
            return;

        com.tpsstudio.view.dialogs.ExportDialog.ExportConfig config = cfg.get();

        // 2. Resolver filas a exportar (solo si exportarRegistros es true)
        java.util.List<Integer> filas = new java.util.ArrayList<>();
        if (config.exportarRegistros()) {
            try {
                filas = com.tpsstudio.view.dialogs.ExportDialog.parseRangoFilas(config.rangoFilas(), totalRegistros);
            } catch (IllegalArgumentException ex) {
                new Alert(Alert.AlertType.ERROR, "El rango de registros no es válido:\n" + ex.getMessage())
                        .showAndWait();
                return;
            }
            if (filas.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Ningún registro válido seleccionado para Mail-Merge.")
                        .showAndWait();
                return;
            }
        }

        // 3. Elegir dónde guardar (usamos este base name para los generados)
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Seleccionar ubicación para la exportación");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName(viewModel.getProyectoActual().getNombre().replaceAll("[^a-zA-Z0-9._-]", "_") + ".pdf");
        File destino = fc.showSaveDialog(canvas.getScene().getWindow());
        if (destino == null)
            return;

        // 4. Generar PDFs en hilo de fondo
        com.tpsstudio.service.PDFExportService pdfService = new com.tpsstudio.service.PDFExportService(viewModel.getProyectoActual(),
                fd);

        final java.util.List<Integer> filasFinal = filas;
        final File basePath = destino;
        final Window ownerWindow = canvas.getScene().getWindow();

        new Thread(() -> {
            try {
                String baseUri = basePath.getAbsolutePath().replaceAll("(?i)\\.pdf$", "");
                int archivosGenerados = 0;

                // A) PDF Mail-Merge
                if (config.exportarRegistros()) {
                    File fMerge = new File(baseUri + "_registros.pdf");
                    pdfService.exportar(config, filasFinal, fMerge);
                    archivosGenerados++;
                }

                // B) Prueba A4
                if (config.configPrueba() != null) {
                    File fPrueba = new File(baseUri + "_prueba.pdf");
                    pdfService.generarPruebaA4(config.configPrueba(), fPrueba);
                    archivosGenerados++;
                }

                // C) PDF Imprenta
                if (config.exportarImprenta()) {
                    File fImprenta = new File(baseUri + "_imprenta.pdf");
                    pdfService.exportarImprenta(fImprenta);
                    archivosGenerados++;
                }

                // Notificar éxito al usuario usando la alerta base Toast
                int totalGenerados = archivosGenerados;
                Platform.runLater(() -> TPSToast.mostrar(
                        ownerWindow,
                        "Exportación completada (" + totalGenerados + " archivos generados)",
                        null,
                        TPSToast.Tipo.EXITO));

            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Error al exportar");
                    err.setHeaderText("No se pudo completar la exportación");
                    err.setContentText(ex.getMessage());
                    err.showAndWait();
                });
            }
        }, "pdf-export-thread").start();
    }

    @FXML
    private void onToggleFrenteDorso() {
        if (viewModel.getProyectoActual() == null)
            return;

        viewModel.getProyectoActual().setMostrandoFrente(toggleFrenteDorso.isSelected());
        toggleFrenteDorso.setText(toggleFrenteDorso.isSelected() ? "Frente" : "Dorso");

        // Cambiar de cara invalida la selección actual
        viewModel.setElementoSeleccionado(null);

        if (viewModel.getCurrentMode() == AppMode.DESIGN) {
            buildEditPanels();
        }

        dibujarCanvas();
    }

    @FXML
    private void onNuevoCR80() {
        mostrarDialogoNuevoProyecto();
    }

    private void mostrarDialogoNuevoProyecto() {
        Window owner = canvas.getScene() != null ? canvas.getScene().getWindow() : null;
        NuevoProyectoDialog dialog = new NuevoProyectoDialog(owner);
        java.util.Optional<ProyectoMetadata> result = dialog.showAndWait();

        if (result.isPresent()) {
            ProyectoMetadata metadata = result.get();
            Proyecto nuevo = projectManager.crearProyectoDesdeMetadata(metadata);

            if (nuevo != null) {
                // Mostrar alerta de éxito visual en el Controller
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.initOwner(owner);
                    alert.setTitle("Proyecto Creado");
                    alert.setHeaderText("Proyecto creado y configurado con éxito");
                    alert.setContentText(
                            "Se ha generado la estructura completa para el proyecto:\n\n" +
                                    "Carpeta principal:\n" + metadata.getCarpetaProyecto() + "\n\n" +
                                    "Subcarpetas creadas automáticamente:\n" +
                                    "• Fotos\n" +
                                    "• Fondos\n" +
                                    "• Base de Datos (BBDD)");

                    String css = getClass().getResource("/css/dialogs.css").toExternalForm();
                    alert.getDialogPane().getStylesheets().add(css);

                    if (owner != null) {
                        alert.setOnShown(e -> {
                            javafx.stage.Stage stage = (javafx.stage.Stage) alert.getDialogPane().getScene().getWindow();
                            stage.setX(owner.getX() + (owner.getWidth()  - stage.getWidth())  / 2.0);
                            stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0);
                        });
                    }
                    alert.showAndWait();
                });
            }
        }
    }

    // =====================================================
    // Acciones de edición (añadir / eliminar elementos)
    // =====================================================

    private void onAñadirTexto() {
        TextoElemento texto = projectManager.añadirTexto();
        if (texto != null) {
            viewModel.setElementoSeleccionado(texto);
            canvasManager.setElementoSeleccionado(texto);
            ensurePropertiesPanelVisible();
        }
    }

    private void onAñadirImagen() {
        ImagenElemento imagen = projectManager.añadirImagenPlaceholder();
        if (imagen != null) {
            viewModel.setElementoSeleccionado(imagen);
            canvasManager.setElementoSeleccionado(imagen);
            ensurePropertiesPanelVisible();

            // Avisar al usuario si se detectó y vinculó columna de foto automáticamente
            if (imagen.getColumnaVinculada() != null) {
                notificarColumnaAutoVinculada(imagen.getColumnaVinculada());
            }
        }
    }

    /*
     * Muestra un toast con retraso cuando la auto-detección vincula columna de
     * foto.
     */
    private void notificarColumnaAutoVinculada(String columna) {
        PauseTransition delay = new PauseTransition(Duration.seconds(1.2));
        delay.setOnFinished(e -> TPSToast.mostrar(
                canvas.getScene().getWindow(),
                "✔ Columna \"" + columna + "\" vinculada automáticamente",
                "La imagen cambiará al navegar por los registros. Puedes cambiarla en Propiedades.",
                TPSToast.Tipo.EXITO,
                5.5));
        delay.play();
    }

    private void onEliminarElemento() {
        if (projectManager.eliminarElemento(viewModel.getElementoSeleccionado())) {

            canvasManager.setElementoSeleccionado(null);
        }
    }

    private void onAñadirFondo() {
        if (viewModel.getProyectoActual() == null) return;

        ImagenFondoElemento fondoExistente = viewModel.getProyectoActual().getFondoActual();
        if (fondoExistente != null && !confirmarReemplazoFondo()) {
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Seleccionar Imagen de Fondo");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (file == null) return;

        FondoFitMode fitMode;
        if (viewModel.getProyectoActual().isNoVolverAPreguntarFondo() && viewModel.getProyectoActual().getFondoFitModePreferido() != null) {
            fitMode = viewModel.getProyectoActual().getFondoFitModePreferido();
        } else {
            fitMode = mostrarDialogoFitMode();
            if (fitMode == null) return;
        }

        ImagenFondoElemento fondo = projectManager.añadirFondoDesdeArchivo(file, fitMode);

        if (fondo != null) {
            viewModel.setElementoSeleccionado(fondo);
            canvasManager.setElementoSeleccionado(fondo);
        }
    }

    private boolean confirmarReemplazoFondo() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reemplazar Fondo");
        alert.setHeaderText("¡Ojo! Ya tienes un fondo puesto.");
        alert.setContentText("¿Seguro que quieres cambiarlo por uno nuevo?");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
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
        boolean panelVisible = (togglePropiedades != null && togglePropiedades.isSelected()) ||
                (toggleDatosVariables != null && toggleDatosVariables.isSelected());

        if (canvasContainer.getWidth() <= 0) {
            Platform.runLater(this::adjustCanvasCentering);
            return;
        }

        double targetX = panelVisible ? -(rightPanel.getPrefWidth() / 2.0) : 0;
        AnimationHelper.shiftCanvas(canvas, targetX);
    }

    /* Abre el diálogo para editar o eliminar un proyecto existente. */

    private void abrirDialogoEditarProyecto(Proyecto proyecto) {

        Window owner = canvas.getScene().getWindow();
        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto, owner);
        java.util.Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            projectManager.eliminarProyecto(proyecto);
            return;
        }

        if (resultado.isPresent()) {
            ProyectoMetadata nuevaMetadata = resultado.get();
            projectManager.editarProyecto(proyecto, nuevaMetadata);

            // Si la BD vinculada cambió, recargar la fuente de datos y reconstruir paneles
            projectManager.cargarFuenteDatos(nuevaMetadata.getRutaBBDD());
            if (viewModel.getCurrentMode() == AppMode.DESIGN) {
                buildEditPanels();
            }
        }
    }
}