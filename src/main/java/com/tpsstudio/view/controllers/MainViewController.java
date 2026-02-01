package com.tpsstudio.view.controllers;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.*;
import com.tpsstudio.service.ProjectManager;
import com.tpsstudio.view.managers.EditorCanvasManager;
import com.tpsstudio.view.managers.ModeManager;
import com.tpsstudio.view.managers.PropertiesPanelController;
import com.tpsstudio.view.dialogs.EditarProyectoDialog;
import com.tpsstudio.service.SettingsManager;
import com.tpsstudio.util.ImageUtils;
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
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;

public class MainViewController {

    // ========== FXML Components ==========
    @FXML
    private VBox leftPanel;
    @FXML
    private VBox rightPanel;
    @FXML
    private Canvas canvas;
    @FXML
    private StackPane canvasContainer;
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
    private Proyecto proyectoActual;
    private Elemento elementoSeleccionado;
    private double zoomLevel = 1.3; // 130% zoom inicial

    // EditorCanvasManager - maneja renderizado y eventos de mouse
    private EditorCanvasManager canvasManager;

    // PropertiesPanelController - maneja el panel de propiedades
    private PropertiesPanelController propertiesPanelController;

    // ModeManager - maneja cambio de modo y construcción de paneles
    private ModeManager modeManager;

    // ProjectManager - maneja lógica de negocio de proyectos y elementos
    private ProjectManager projectManager;

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

        // Cargar últimos 8 proyectos recientes automáticamente
        projectManager.cargarProyectosRecientes(8);
        // NO seleccionar ningún proyecto por defecto - canvas vacío hasta que usuario
        // seleccione

        // Centrar canvas inicialmente (panel de propiedades abierto por defecto)
        Platform.runLater(() -> adjustCanvasCentering(true));

        // Añadir listener de doble clic para editar proyecto
        if (listProyectos != null) {
            listProyectos.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    Proyecto proyectoSeleccionado = listProyectos.getSelectionModel().getSelectedItem();
                    if (proyectoSeleccionado != null && proyectoSeleccionado.getMetadata() != null) {
                        abrirDialogoEditarProyecto(proyectoSeleccionado);
                    }
                }
            });
        }
    }

    private void setupCanvas() {
        // Inicializar ProjectManager
        projectManager = new ProjectManager();
        projectManager.setOnProjectChanged(() -> {
            proyectoActual = projectManager.getProyectoActual();
            canvasManager.setProyectoActual(proyectoActual);
            dibujarCanvas();
        });
        projectManager.setOnElementAdded(() -> {
            buildEditPanels();
            dibujarCanvas();
        });

        // Inicializar EditorCanvasManager
        canvasManager = new EditorCanvasManager(canvas);
        canvasManager.setProyectoActual(proyectoActual);
        canvasManager.setZoomLevel(zoomLevel);
        canvasManager.setCurrentMode(currentMode);
        canvasManager.setMostrarGuias(true); // Guías activadas por defecto

        // Inicializar PropertiesPanelController
        propertiesPanelController = new PropertiesPanelController(canvas);
        propertiesPanelController.setOnPropertyChanged(
                () -> modeManager.switchMode(currentMode, proyectoActual, elementoSeleccionado,
                        projectManager.getProyectos()));
        propertiesPanelController.setOnCanvasRedrawNeeded(() -> {
            // SOLO refrescar lista de capas y redibujar canvas (evita perder foco en
            // propiedades)
            if (currentMode == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(proyectoActual, elementoSeleccionado);
            }
            dibujarCanvas();
        });
        propertiesPanelController.setOnEditExternal(this::abrirEditorExterno);
        propertiesPanelController.setOnReload(this::recargarFondo);

        // Inicializar ModeManager
        modeManager = new ModeManager(leftPanel, rightPanel, propertiesPanelController);
        modeManager.setOnAddText(this::onAñadirTexto);
        modeManager.setOnAddImage(this::onAñadirImagen);
        modeManager.setOnAddBackground(this::onAñadirFondo);
        modeManager.setOnNewCR80(this::onNuevoCR80);
        modeManager.setOnExport(this::onExportarProyecto);
        modeManager.setOnElementSelected(elemento -> {
            elementoSeleccionado = elemento;
            canvasManager.setElementoSeleccionado(elemento); // Sync to canvas manager
            if (elemento != null) {
                ensurePropertiesPanelVisible();
            }
            modeManager.switchMode(currentMode, proyectoActual, elementoSeleccionado, projectManager.getProyectos());
            dibujarCanvas();
        });
        modeManager.setOnProjectSelected(proyecto -> {
            proyectoActual = proyecto;
            projectManager.setProyectoActual(proyecto); // CRÍTICO: Sync to ProjectManager
            canvasManager.setProyectoActual(proyectoActual); // Sync to canvas manager
            dibujarCanvas();
        });
        modeManager.setOnEditExternal(this::abrirEditorExterno);
        modeManager.setOnReload(this::recargarFondo);
        modeManager.setOnToggleLock(elemento -> {
            elemento.setLocked(!elemento.isLocked());
            modeManager.switchMode(currentMode, proyectoActual, elementoSeleccionado, projectManager.getProyectos());
            dibujarCanvas();
        });
        modeManager.setOnCanvasRedraw(this::dibujarCanvas);
        modeManager.setOnEditProject(this::abrirDialogoEditarProyecto);
        modeManager.setProjectManager(projectManager);

        // Callbacks para sincronizar estado
        canvasManager.setOnElementSelected(() -> {
            elementoSeleccionado = canvasManager.getElementoSeleccionado();
            ensurePropertiesPanelVisible();
            // Ya está sincronizado porque viene del canvasManager
        });

        canvasManager.setOnCanvasChanged(() -> {
            elementoSeleccionado = canvasManager.getElementoSeleccionado();
            buildEditPanels();
            canvasManager.dibujarCanvas();
        });

        // CRÍTICO: Configurar mouse handlers para drag & resize
        canvasManager.setupMouseHandlers();

        // Keyboard events
        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE && elementoSeleccionado != null) {
                onEliminarElemento();
            }
        });

        // Inicializar label de zoom con el valor correcto
        actualizarZoom();

        // Initialize width listener for responsive centering
        canvasContainer.widthProperty().addListener((obs, oldVal, newVal) -> {
            adjustCanvasCentering(togglePropiedades.isSelected());
            // Actualizar clip si es necesario (el binding suele ser suficiente)
        });

        // CRÍTICO: Aplicar máscara de recorte (Clip) al contenedor del canvas
        // Esto asegura que si el canvas se mueve a la izquierda, se "meta por debajo"
        // del panel izquierdo (desaparezca) en lugar de montarse encima.
        javafx.scene.shape.Rectangle clipRect = new javafx.scene.shape.Rectangle();
        clipRect.widthProperty().bind(canvasContainer.widthProperty());
        clipRect.heightProperty().bind(canvasContainer.heightProperty());
        canvasContainer.setClip(clipRect);

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

    // ========== ZOOM CONTROLS ==========

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
        canvasManager.setZoomLevel(zoomLevel);
        dibujarCanvas();

        // Re-ajustar centrado porque el ancho visual del canvas ha cambiado
        adjustCanvasCentering(togglePropiedades.isSelected());
    }

    // ========== TOGGLE CONTROLS ==========

    @FXML
    private void onToggleGuias() {
        canvasManager.setMostrarGuias(toggleGuias.isSelected());
        dibujarCanvas();
    }

    // ========== MODE SWITCHING ==========

    private void switchMode(AppMode newMode) {
        currentMode = newMode;
        canvasManager.setCurrentMode(newMode); // CRÍTICO: Sync mode to canvas manager
        modeManager.switchMode(newMode, proyectoActual, elementoSeleccionado, projectManager.getProyectos());
    }

    // ========== BUILD EDIT PANELS ==========

    private void buildEditPanels() {
        modeManager.switchMode(AppMode.DESIGN, proyectoActual, elementoSeleccionado, projectManager.getProyectos());
    }

    // ========== CANVAS RENDERING ==========

    /**
     * Dibuja el canvas delegando al EditorCanvasManager
     */
    private void dibujarCanvas() {
        canvasManager.dibujarCanvas();
    }

    // ========== HELPER METHODS ==========

    private void ensurePropertiesPanelVisible() {
        if (togglePropiedades != null && !togglePropiedades.isSelected()) {
            togglePropiedades.setSelected(true);
            onTogglePropiedades();
        }
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

        // 1. Intentar ruta tal cual
        File file = new File(fondo.getRutaArchivo());

        // 2. Si no existe, intentar búsqueda inteligente en carpeta del proyecto
        if (!file.exists() && proyectoActual != null && proyectoActual.getMetadata() != null) {
            String projectDir = proyectoActual.getMetadata().getCarpetaProyecto();
            if (projectDir != null) {
                File fondosDir = new File(projectDir, "Fondos");
                String originalName = file.getName();

                // Opción A: Buscar en carpeta Fondos con mismo nombre
                File optionA = new File(fondosDir, originalName);

                // Opción B: Construir nombre con sufijo _FRENTE o _DORSO
                String nameNoExt = originalName;
                String ext = "";
                int dotIndex = originalName.lastIndexOf('.');
                if (dotIndex > 0) {
                    nameNoExt = originalName.substring(0, dotIndex);
                    ext = originalName.substring(dotIndex); // incluye el punto
                }

                // Determinar el sufijo correcto comparando con la referencia en el proyecto
                String suffix = "";
                if (fondo == proyectoActual.getFondoFrente()) {
                    suffix = "_FRENTE";
                } else if (fondo == proyectoActual.getFondoDorso()) {
                    suffix = "_DORSO";
                }

                File optionB = new File(fondosDir, nameNoExt + suffix + ext);

                // Verificar cuál existe
                if (optionB.exists()) {
                    file = optionB;
                    // Actualizar la ruta en el objeto para que 'Recargar' funcione luego
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
            alert.setContentText("El archivo " + file.getName() + " no existe en el disco.\n\n" +
                    "Buscado en: " + file.getAbsolutePath());
            alert.showAndWait();
            return;
        }

        try {
            SettingsManager settings = new SettingsManager();
            String customEditor = settings.getExternalEditorPath();
            boolean opened = false;

            if (customEditor != null) {
                File editorFile = new File(customEditor);
                if (editorFile.exists()) {
                    // Mostrar aviso
                    Alert aviso = new Alert(Alert.AlertType.INFORMATION);
                    aviso.setTitle("Editando Externamente");
                    aviso.setHeaderText("Abriendo con " + settings.getExternalEditorName() + "...");
                    aviso.setContentText(
                            "Puedes editar la imagen mientras TPS Studio permanece abierto.\n" +
                                    "Cuando guardes los cambios en el editor, pulsa 'Recargar' aquí para ver el resultado.");
                    aviso.show();

                    // LANZAMIENTO DESACOPLADO (DETACHED)
                    // Usamos 'cmd /c start "" "exe" "file"' para romper la herencia de handles
                    // Esto evita que Photoshop herede bloqueos de Java y falle al guardar
                    String[] cmd = { "cmd", "/c", "start", "\"\"", customEditor, file.getAbsolutePath() };
                    new ProcessBuilder(cmd).start();
                    opened = true;
                }
            }

            if (!opened) {
                // Mostrar aviso default
                Alert aviso = new Alert(Alert.AlertType.INFORMATION);
                aviso.setTitle("Editando Externamente");
                aviso.setHeaderText("Abriendo editor predeterminado...");
                aviso.setContentText(
                        "Puedes editar la imagen mientras TPS Studio permanece abierto.\n" +
                                "Cuando guardes los cambios, pulsa 'Recargar' aquí para ver el resultado.");
                aviso.show();

                // Intentar lanzamiento desacoplado también para el default
                try {
                    String[] cmd = { "cmd", "/c", "start", "\"\"", file.getAbsolutePath() };
                    new ProcessBuilder(cmd).start();
                } catch (Exception e) {
                    // Fallback a Desktop si falla el cmd (raro en Windows)
                    java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                    desktop.open(file);
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
            // Recargar imagen SIN BLOQUEO
            Image nuevaImagen = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());
            if (nuevaImagen == null) {
                throw new Exception("No se pudo cargar la imagen (null return)");
            }

            fondo.setImagen(nuevaImagen);

            // Preguntar modo de ajuste (Sangre vs Final)
            // Esto permite reajustar si el usuario cambió el tamaño en el editor externo
            FondoFitMode nuevoModo = mostrarDialogoFitMode();
            if (nuevoModo != null) {
                fondo.setFitMode(nuevoModo);
            }

            fondo.ajustarATamaño(EditorCanvasManager.CARD_WIDTH, EditorCanvasManager.CARD_HEIGHT,
                    EditorCanvasManager.BLEED_MARGIN);

            // Redibujar
            dibujarCanvas();

            // Confirmación
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

    // ========== TOOLBAR ACTIONS ==========

    @FXML
    private void onNuevoProyecto() {
        // Crear proyecto con diálogo completo y estructura de carpetas
        Proyecto nuevoProyecto = projectManager.nuevoProyecto(canvas.getScene().getWindow());
        if (nuevoProyecto != null && listProyectos != null) {
            listProyectos.getSelectionModel().select(nuevoProyecto);
        }
    }

    @FXML
    private void onAbrirProyecto() {
        // Abrir proyecto desde archivo .tps
        Proyecto proyectoCargado = projectManager.abrirProyecto(canvas.getScene().getWindow());
        if (proyectoCargado != null && listProyectos != null) {
            listProyectos.getSelectionModel().select(proyectoCargado);
        }
    }

    @FXML
    private void onGuardarProyecto() {
        // Guardar proyecto actual en JSON
        projectManager.guardarProyecto();
    }

    @FXML
    private void onExportarProyecto() {
        System.out.println("Exportar (placeholder)");
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
    private void onNuevoCR80() {
        // Crear proyecto con diálogo completo (mismo que onNuevoProyecto)
        Proyecto nuevoProyecto = projectManager.nuevoProyecto(canvas.getScene().getWindow());
        if (nuevoProyecto != null && listProyectos != null) {
            listProyectos.getSelectionModel().select(nuevoProyecto);
        }
    }

    // ========== EDIT MODE ACTIONS ==========

    private void onAñadirTexto() {
        TextoElemento texto = projectManager.añadirTexto();
        if (texto != null) {
            elementoSeleccionado = texto;
            canvasManager.setElementoSeleccionado(texto);
            ensurePropertiesPanelVisible();
        }
    }

    private void onAñadirImagen() {
        ImagenElemento imagen = projectManager.añadirImagen(canvas.getScene().getWindow());
        if (imagen != null) {
            elementoSeleccionado = imagen;
            canvasManager.setElementoSeleccionado(imagen);
        }
    }

    private void onEliminarElemento() {
        if (projectManager.eliminarElemento(elementoSeleccionado)) {
            elementoSeleccionado = null;
            canvasManager.setElementoSeleccionado(null);
        }
    }

    private void onAñadirFondo() {
        ImagenFondoElemento fondo = projectManager.añadirFondo(
                canvas.getScene().getWindow(),
                this::mostrarDialogoFitMode);
        if (fondo != null) {
            elementoSeleccionado = fondo;
            canvasManager.setElementoSeleccionado(fondo);
        }
    }

    @FXML
    private void onTogglePropiedades() {
        togglePanel(rightPanel, togglePropiedades.isSelected(), false);
        // Centrar canvas: moverlo a la izquierda cuando el panel está abierto
        adjustCanvasCentering(togglePropiedades.isSelected());
    }

    /**
     * Anima la visibilidad de un panel lateral (Overlay mode)
     */
    private void togglePanel(Region panel, boolean show, boolean isLeft) {
        if (show) {
            panel.setVisible(true);
            panel.setManaged(true);

            // Fade in
            FadeTransition fade = new FadeTransition(Duration.millis(200), panel);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);

            // Slide in
            TranslateTransition slide = new TranslateTransition(Duration.millis(200), panel);
            double distance = 50;
            slide.setFromX(isLeft ? -distance : distance);
            slide.setToX(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition parallel = new ParallelTransition(fade, slide);
            parallel.play();
        } else {
            // Fade out
            FadeTransition fade = new FadeTransition(Duration.millis(150), panel);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);

            // Slide out
            TranslateTransition slide = new TranslateTransition(Duration.millis(150), panel);
            double distance = 50;
            slide.setFromX(0);
            slide.setToX(isLeft ? -distance : distance);
            slide.setInterpolator(Interpolator.EASE_IN);

            ParallelTransition parallel = new ParallelTransition(fade, slide);
            parallel.setOnFinished(e -> {
                panel.setVisible(false);
                panel.setManaged(false);
            });
            parallel.play();
        }
    }

    /**
     * Ajusta el centrado del canvas cuando se abre/cierra el panel de propiedades
     */
    private void adjustCanvasCentering(boolean propertiesPanelVisible) {
        // Necesitamos el layout actual, si no está listo, esperamos
        if (canvasContainer.getWidth() <= 0) {
            // Reintentar un poco más tarde cuando el layout esté listo
            Platform.runLater(() -> adjustCanvasCentering(propertiesPanelVisible));
            return;
        }

        // CRÍTICO: Animamos el CANVAS, no el contenedor.
        // El contenedor debe quedarse quieto para no invadir el panel izquierdo.
        TranslateTransition transition = new TranslateTransition(Duration.millis(200), canvas);

        if (propertiesPanelVisible) {
            // Queremos centrar visualmente en el espacio restante (mover mitad del panel
            // derecho)
            double idealShift = -rightPanel.getPrefWidth() / 2;

            // A diferencia de antes, ahora PERMITIMOS que se mueva más allá del margen
            // seguro
            // porque el CLIP se encargará de que no manche el panel izquierdo.
            transition.setToX(idealShift);

        } else {
            // Panel cerrado: centrar canvas (volver a 0)
            transition.setToX(0);
        }

        transition.setInterpolator(Interpolator.EASE_OUT);
        transition.play();
    }

    /**
     * Abre el diálogo para editar un proyecto existente
     */
    private void abrirDialogoEditarProyecto(Proyecto proyecto) {
        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto);
        java.util.Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            // Usuario quiere eliminar el proyecto
            projectManager.eliminarProyecto(proyecto);
        } else if (resultado.isPresent()) {
            // Usuario guardó cambios
            ProyectoMetadata nuevaMetadata = resultado.get();
            projectManager.editarProyecto(proyecto, nuevaMetadata);
        }
    }
}
