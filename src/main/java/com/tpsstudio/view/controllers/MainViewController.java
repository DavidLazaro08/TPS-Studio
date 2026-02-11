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

    // =====================================================
    // Estado
    // =====================================================
    private AppMode currentMode = AppMode.PRODUCTION;
    private Proyecto proyectoActual;
    private Elemento elementoSeleccionado;
    private double zoomLevel = 1.4; // 130% zoom inicial

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

    // =====================================================
    // Inicialización
    // =====================================================
    @FXML
    private void initialize() {
        setupCanvas();
        switchMode(AppMode.PRODUCTION); // Start in PRODUCTION mode

        initUI();
        initProjectList();

        projectManager.cargarProyectosRecientes(8);

        Platform.runLater(() -> adjustCanvasCentering(true));
    }

    private void initUI() {
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.5));
        shadow.setRadius(15);
        shadow.setSpread(0.1);

        rightPanel.setEffect(shadow);

        // Estado inicial del panel de propiedades (según el toggle)
        togglePanel(rightPanel, togglePropiedades.isSelected(), false);
    }

    private void initProjectList() {
        if (listProyectos == null) return;

        listProyectos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Proyecto proyectoSeleccionado = listProyectos.getSelectionModel().getSelectedItem();
                if (proyectoSeleccionado != null && proyectoSeleccionado.getMetadata() != null) {
                    abrirDialogoEditarProyecto(proyectoSeleccionado);
                }
            }
        });
    }

    private void setupCanvas() {

        // -------------------------------------------------
        // ProjectManager (estado del proyecto y cambios)
        // -------------------------------------------------
        projectManager = new ProjectManager();

        projectManager.setOnProjectChanged(() -> {
            proyectoActual = projectManager.getProyectoActual();
            canvasManager.setProyectoActual(proyectoActual);
            dibujarCanvas();
        });

        projectManager.setOnElementAdded(() -> {
            // Fuerza la reconstrucción de paneles cuando cambia la estructura del diseño
            buildEditPanels();
            dibujarCanvas();
        });

        // -------------------------------------------------
        // EditorCanvasManager (render y eventos del canvas)
        // -------------------------------------------------
        canvasManager = new EditorCanvasManager(canvas);
        canvasManager.setProyectoActual(proyectoActual);
        canvasManager.setZoomLevel(zoomLevel);
        canvasManager.setCurrentMode(currentMode);
        canvasManager.setMostrarGuias(true); // Guías activadas por defecto

        // -------------------------------------------------
        // Panel de propiedades (edición del elemento seleccionado)
        // -------------------------------------------------
        propertiesPanelController = new PropertiesPanelController(canvas);

        propertiesPanelController.setOnPropertyChanged(() ->
                modeManager.switchMode(currentMode, proyectoActual, elementoSeleccionado, projectManager.getProyectos())
        );

        propertiesPanelController.setOnCanvasRedrawNeeded(() -> {
            // SOLO refrescar lista de capas y redibujar canvas (evita perder foco en propiedades)
            if (currentMode == AppMode.DESIGN) {
                modeManager.refreshLayersPanel(proyectoActual, elementoSeleccionado);
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
            elementoSeleccionado = elemento;
            canvasManager.setElementoSeleccionado(elemento); // sincroniza selección con el canvas

            if (elemento != null) {
                ensurePropertiesPanelVisible();
            }

            modeManager.switchMode(currentMode, proyectoActual, elementoSeleccionado, projectManager.getProyectos());
            dibujarCanvas();
        });

        modeManager.setOnProjectSelected(proyecto -> {
            proyectoActual = proyecto;

            // Importante: mantener sincronizados los tres (UI / ProjectManager / Canvas)
            projectManager.setProyectoActual(proyecto);
            canvasManager.setProyectoActual(proyectoActual);

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

        // -------------------------------------------------
        // Sincronización Canvas ↔ UI (callbacks del canvas)
        // -------------------------------------------------
        canvasManager.setOnElementSelected(() -> {
            elementoSeleccionado = canvasManager.getElementoSeleccionado();
            ensurePropertiesPanelVisible();
            // Ya está sincronizado porque viene del canvasManager
        });

        canvasManager.setOnCanvasChanged(() -> {
            elementoSeleccionado = canvasManager.getElementoSeleccionado();

            // Fuerza refresco de paneles tras drag/resize u otros cambios en el canvas
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
            if (event.getCode() == KeyCode.DELETE && elementoSeleccionado != null) {
                onEliminarElemento();
            }
        });

        // -------------------------------------------------
        // Zoom / layout (centrado y recorte)
        // -------------------------------------------------
        actualizarZoom(); // Inicializa label con el valor correcto y aplica zoom al canvas

        canvasContainer.widthProperty().addListener((obs, oldVal, newVal) -> {
            adjustCanvasCentering(togglePropiedades.isSelected());
            // Actualizar clip si es necesario (el binding suele ser suficiente)
        });

        // Importante: aplicar máscara de recorte (Clip) al contenedor del canvas
        // Esto asegura que si el canvas se mueve a la izquierda, se "meta por debajo"
        // del panel izquierdo (desaparezca) en lugar de montarse encima.
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

    private void switchMode(AppMode newMode) {
        currentMode = newMode;

        // Importante: el CanvasManager debe saber en qué modo estamos (edit/producción)
        canvasManager.setCurrentMode(newMode);

        // Reconstruye paneles laterales según modo y selección
        modeManager.switchMode(newMode, proyectoActual, elementoSeleccionado, projectManager.getProyectos());
    }

// =====================================================
// Zoom
// =====================================================

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

        // Reajustar centrado porque el ancho visual del canvas ha cambiado
        adjustCanvasCentering(togglePropiedades.isSelected());
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
        // Nota: aunque el modo global sea PRODUCTION, aquí forzamos el montaje de paneles
        // de edición para mantener la UI coherente tras cambios (capas, selección, etc.).
        modeManager.switchMode(AppMode.DESIGN, proyectoActual, elementoSeleccionado, projectManager.getProyectos());
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

// =====================================================
// Helpers
// =====================================================

    private void ensurePropertiesPanelVisible() {
        if (togglePropiedades != null && !togglePropiedades.isSelected()) {
            togglePropiedades.setSelected(true);
            onTogglePropiedades();
        }
    }
    /*
     * Muestra un diálogo para elegir cómo se ajusta el fondo a la tarjeta:
     * - Con sangre (BLEED): cubre CR80 + sangrado (2mm por lado)
     * - Sin sangre (FINAL): cubre solo el tamaño final CR80
     *
     * Si el usuario marca "No volver a preguntar", se guarda la preferencia en el proyecto.
     */
    private FondoFitMode mostrarDialogoFitMode() {

        Dialog<FondoFitMode> dialog = new Dialog<>();
        dialog.setTitle("Modo de Ajuste del Fondo");
        dialog.setHeaderText("¿Cómo desea ajustar el fondo a la tarjeta?");

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

        content.getChildren().addAll(
                lblExplicacion, new Separator(),
                opcionBleed, new Separator(),
                opcionFinal, new Separator(),
                chkNoPreguntar
        );

        dialog.getDialogPane().setContent(content);

        // -------------------------------------------------
        // Conversión de resultado + guardado de preferencia
        // -------------------------------------------------
        dialog.setResultConverter(buttonType -> {

            if (chkNoPreguntar.isSelected() && proyectoActual != null) {
                proyectoActual.setNoVolverAPreguntarFondo(true);

                if (buttonType == btnBleed) {
                    proyectoActual.setFondoFitModePreferido(FondoFitMode.BLEED);
                } else if (buttonType == btnFinal) {
                    proyectoActual.setFondoFitModePreferido(FondoFitMode.FINAL);
                }
            }

            if (buttonType == btnBleed) return FondoFitMode.BLEED;
            if (buttonType == btnFinal) return FondoFitMode.FINAL;

            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    /*
     * Abre el archivo del fondo en el editor externo configurado (si existe),
     * o en el editor predeterminado del sistema.
     *
     * Nota: el lanzamiento se hace desacoplado para evitar problemas al guardar
     * (por ejemplo, Photoshop "bloqueado" por herencias de handles del proceso Java).
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

        // Si la ruta guardada no existe, intentamos localizarla dentro del proyecto (carpeta /Fondos)
        if (!file.exists() && proyectoActual != null && proyectoActual.getMetadata() != null) {

            String projectDir = proyectoActual.getMetadata().getCarpetaProyecto();
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
                if (fondo == proyectoActual.getFondoFrente()) {
                    suffix = "_FRENTE";
                } else if (fondo == proyectoActual.getFondoDorso()) {
                    suffix = "_DORSO";
                }

                File optionB = new File(fondosDir, nameNoExt + suffix + ext);

                // Elegir la ruta que exista y actualizarla en el objeto (para que "Recargar" funcione bien)
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
                            "Buscado en: " + file.getAbsolutePath()
            );
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
                                    "Cuando guardes los cambios en el editor, pulsa 'Recargar' aquí para ver el resultado."
                    );
                    aviso.show();

                    // Lanzamiento desacoplado (Windows)
                    String[] cmd = {"cmd", "/c", "start", "\"\"", customEditor, file.getAbsolutePath()};
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
                                "Cuando guardes los cambios, pulsa 'Recargar' aquí para ver el resultado."
                );
                aviso.show();

                // Intento desacoplado también para el editor por defecto
                try {
                    String[] cmd = {"cmd", "/c", "start", "\"\"", file.getAbsolutePath()};
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
                            "Se mantendrá la versión anterior en memoria."
            );
            alert.showAndWait();
            return;
        }

        try {
            // Cargar sin bloquear el archivo (evita problemas tras editar con apps externas)
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
                    EditorCanvasManager.BLEED_MARGIN
            );

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
                            "Se mantendrá la versión anterior en memoria."
            );
            alert.showAndWait();
        }
    }

// =====================================================
// Acciones de barra superior (Proyectos / exportación)
// =====================================================

    @FXML
    private void onNuevoProyecto() {
        // Crea un proyecto con diálogo completo y estructura de carpetas
        Proyecto nuevoProyecto = projectManager.nuevoProyecto(canvas.getScene().getWindow());
        if (nuevoProyecto != null && listProyectos != null) {
            listProyectos.getSelectionModel().select(nuevoProyecto);
        }
    }

    @FXML
    private void onAbrirProyecto() {
        // Abre un proyecto desde archivo .tps
        Proyecto proyectoCargado = projectManager.abrirProyecto(canvas.getScene().getWindow());
        if (proyectoCargado != null && listProyectos != null) {
            listProyectos.getSelectionModel().select(proyectoCargado);
        }
    }

    @FXML
    private void onGuardarProyecto() {
        // Guarda el proyecto actual (JSON / .tps)
        projectManager.guardarProyecto();
    }

    @FXML
    private void onExportarProyecto() {
        System.out.println("Exportar (placeholder)");
    }

    @FXML
    private void onToggleFrenteDorso() {
        if (proyectoActual == null) return;

        proyectoActual.setMostrandoFrente(toggleFrenteDorso.isSelected());
        toggleFrenteDorso.setText(toggleFrenteDorso.isSelected() ? "Frente" : "Dorso");

        // Cambiar de cara invalida la selección actual
        elementoSeleccionado = null;

        if (currentMode == AppMode.DESIGN) {
            buildEditPanels();
        }

        dibujarCanvas();
    }

    @FXML
    private void onNuevoCR80() {
        // Nota: de momento es el mismo flujo que "Nuevo proyecto".
        // Si más adelante hay formatos (acreditación, XXL...), aquí se diferencia.
        Proyecto nuevoProyecto = projectManager.nuevoProyecto(canvas.getScene().getWindow());
        if (nuevoProyecto != null && listProyectos != null) {
            listProyectos.getSelectionModel().select(nuevoProyecto);
        }
    }

// =====================================================
// Acciones de edición (añadir / eliminar elementos)
// =====================================================

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
                this::mostrarDialogoFitMode
        );

        if (fondo != null) {
            elementoSeleccionado = fondo;
            canvasManager.setElementoSeleccionado(fondo);
        }
    }

// =====================================================
// Panel de propiedades (mostrar/ocultar)
// =====================================================

    @FXML
    private void onTogglePropiedades() {
        togglePanel(rightPanel, togglePropiedades.isSelected(), false);

        // Centrar canvas: al abrir el panel, se desplaza a la izquierda
        adjustCanvasCentering(togglePropiedades.isSelected());
    }

    /*
     * Anima la visibilidad de un panel lateral (overlay).
     * show = true  -> aparece con fade + slide
     * show = false -> desaparece con fade + slide y se desactiva (managed=false)
     */
    private void togglePanel(Region panel, boolean show, boolean isLeft) {

        if (show) {
            panel.setVisible(true);
            panel.setManaged(true);

            FadeTransition fade = new FadeTransition(Duration.millis(200), panel);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);

            TranslateTransition slide = new TranslateTransition(Duration.millis(200), panel);
            double distance = 50;
            slide.setFromX(isLeft ? -distance : distance);
            slide.setToX(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            new ParallelTransition(fade, slide).play();
            return;
        }

        FadeTransition fade = new FadeTransition(Duration.millis(150), panel);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

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

    /* Ajusta el centrado del canvas cuando se abre/cierra el panel de propiedades.
     * Importante: se anima el CANVAS (no el contenedor) para no invadir el panel izquierdo. */

    private void adjustCanvasCentering(boolean propertiesPanelVisible) {

        // Si el layout aún no está listo, reintentamos en el siguiente frame
        if (canvasContainer.getWidth() <= 0) {
            Platform.runLater(() -> adjustCanvasCentering(propertiesPanelVisible));
            return;
        }

        TranslateTransition transition = new TranslateTransition(Duration.millis(200), canvas);

        if (propertiesPanelVisible) {
            // Centrar visualmente en el espacio restante (mitad del ancho del panel derecho)
            double idealShift = -rightPanel.getPrefWidth() / 2;

            // Permitimos que se desplace más, porque el CLIP evita que "manche" el panel izquierdo
            transition.setToX(idealShift);
        } else {
            transition.setToX(0);
        }

        transition.setInterpolator(Interpolator.EASE_OUT);
        transition.play();
    }

    /* Abre el diálogo para editar o eliminar un proyecto existente. */

    private void abrirDialogoEditarProyecto(Proyecto proyecto) {

        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto);
        java.util.Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            projectManager.eliminarProyecto(proyecto);
            return;
        }

        if (resultado.isPresent()) {
            ProyectoMetadata nuevaMetadata = resultado.get();
            projectManager.editarProyecto(proyecto, nuevaMetadata);
        }
    }
}