package com.tpsstudio.view;

import com.tpsstudio.model.*;
import com.tpsstudio.service.ProjectManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
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
        propertiesPanelController.setOnCanvasRedrawNeeded(() -> dibujarCanvas());
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

        File file = new File(fondo.getRutaArchivo());
        if (!file.exists()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Archivo no encontrado");
            alert.setContentText("El archivo " + file.getName() + " no existe en el disco.");
            alert.showAndWait();
            return;
        }

        try {
            // Mostrar aviso
            Alert aviso = new Alert(Alert.AlertType.INFORMATION);
            aviso.setTitle("Editor Externo");
            aviso.setHeaderText("Abriendo editor externo...");
            aviso.setContentText(
                    "Se abrirá la imagen en el editor predeterminado del sistema.\n" +
                            "Después de editar, guarde los cambios y use 'Recargar' para aplicarlos.");
            aviso.show();

            // Abrir con la aplicación predeterminada
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            desktop.open(file);

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
            // Recargar imagen
            Image nuevaImagen = new Image(file.toURI().toString());
            fondo.setImagen(nuevaImagen);
            fondo.ajustarATamaño(EditorCanvasManager.CARD_WIDTH, EditorCanvasManager.CARD_HEIGHT,
                    EditorCanvasManager.BLEED_MARGIN);

            // Redibujar
            dibujarCanvas();

            // Confirmación
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Éxito");
            alert.setHeaderText("Fondo recargado");
            alert.setContentText("La imagen se ha recargado correctamente desde el disco.");
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
        System.out.println("Nuevo (placeholder)");
    }

    @FXML
    private void onAbrirProyecto() {
        System.out.println("Abrir (placeholder)");
    }

    @FXML
    private void onGuardarProyecto() {
        System.out.println("Guardar (placeholder)");
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
        Proyecto nuevoProyecto = projectManager.crearNuevoCR80();
        if (listProyectos != null) {
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
    }

    /**
     * Anima la visibilidad de un panel lateral (Overlay mode)
     */
    private void togglePanel(javafx.scene.layout.Region panel, boolean show, boolean isLeft) {
        // Asegurar que es visible e interactuable al empezar animación de mostrado
        if (show) {
            panel.setVisible(true);
            panel.setMouseTransparent(false);
        } else {
            panel.setMouseTransparent(true); // Bloquear interacción mientras se oculta
        }

        // Configurar sombra si no tiene
        if (panel.getEffect() == null) {
            javafx.scene.effect.DropShadow shadow = new javafx.scene.effect.DropShadow();
            shadow.setColor(Color.rgb(0, 0, 0, 0.5));
            shadow.setRadius(15);
            shadow.setSpread(0.1);
            panel.setEffect(shadow);
        }

        // Calcular desplazamiento
        double width = panel.getWidth() > 0 ? panel.getWidth() : panel.getPrefWidth();
        double startX = show ? (isLeft ? -width : width) : 0;
        double endX = show ? 0 : (isLeft ? -width : width);

        // Translate Transition
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), panel);
        tt.setFromX(startX);
        tt.setToX(endX);
        tt.setInterpolator(Interpolator.EASE_BOTH);

        // Fade Transition
        FadeTransition ft = new FadeTransition(Duration.millis(300), panel);
        ft.setFromValue(show ? 0.0 : 1.0);
        ft.setToValue(show ? 1.0 : 0.0);
        ft.setInterpolator(Interpolator.EASE_BOTH);

        // Parallel Transition
        ParallelTransition pt = new ParallelTransition(tt, ft);

        pt.setOnFinished(e -> {
            if (!show) {
                panel.setVisible(false);
            }
        });

        pt.play();
    }
}
