package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.view.dialogs.EditarProyectoDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gestiona qué se muestra en los paneles laterales según el modo activo
 * (Diseño / Producción).
 *
 * - Diseño: herramientas + capas (izquierda) y propiedades (derecha)
 * - Producción: lista de proyectos (izquierda) y exportación (derecha)
 */
public class ModeManager {

    // Estado actual (Diseño / Producción)
    private AppMode currentMode;

    // Contenedores físicos de UI (se rellenan dinámicamente)
    private final VBox leftPanel;
    private final VBox rightPanel;

    // Controlador del panel de propiedades (solo aplica en modo Diseño)
    private final PropertiesPanelController propertiesPanelController;

    // Referencia opcional al ProjectManager (solo si se usa el helper interno)
    private com.tpsstudio.service.ProjectManager projectManager;

    // Callbacks hacia el controlador principal (MainViewController)
    private Runnable onAddText;
    private Runnable onAddImage;
    private Runnable onAddBackground;
    private Runnable onNewCR80;
    private Runnable onExport;

    private Consumer<Elemento> onElementSelected;
    private Consumer<Proyecto> onProjectSelected;
    private Consumer<Proyecto> onEditProject;

    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;
    private Consumer<Elemento> onToggleLock;

    private Runnable onCanvasRedraw;

    // Se guarda para poder refrescar solo la parte de "Capas" sin rehacer todo el panel izquierdo
    private VBox layersPanel;

    public ModeManager(VBox leftPanel, VBox rightPanel, PropertiesPanelController propertiesPanelController) {
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.propertiesPanelController = propertiesPanelController;
        this.currentMode = AppMode.DESIGN;
    }

    // ===================== SETTERS DE CALLBACKS =====================

    public void setOnAddText(Runnable callback) {
        this.onAddText = callback;
    }

    public void setOnAddImage(Runnable callback) {
        this.onAddImage = callback;
    }

    public void setOnAddBackground(Runnable callback) {
        this.onAddBackground = callback;
    }

    public void setOnNewCR80(Runnable callback) {
        this.onNewCR80 = callback;
    }

    public void setOnExport(Runnable callback) {
        this.onExport = callback;
    }

    public void setOnElementSelected(Consumer<Elemento> callback) {
        this.onElementSelected = callback;
    }

    public void setOnProjectSelected(Consumer<Proyecto> callback) {
        this.onProjectSelected = callback;
    }

    public void setOnEditProject(Consumer<Proyecto> callback) {
        this.onEditProject = callback;
    }

    public void setOnEditExternal(Consumer<ImagenFondoElemento> callback) {
        this.onEditExternal = callback;
    }

    public void setOnReload(Consumer<ImagenFondoElemento> callback) {
        this.onReload = callback;
    }

    public void setOnToggleLock(Consumer<Elemento> callback) {
        this.onToggleLock = callback;
    }

    public void setOnCanvasRedraw(Runnable callback) {
        this.onCanvasRedraw = callback;
    }

    public void setProjectManager(com.tpsstudio.service.ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    // ===================== MODO ACTUAL =====================

    public AppMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Cambia el modo de la interfaz y reconstruye los paneles laterales.
     * Nota: el canvas se repinta al final para evitar inconsistencias visuales.
     */
    public void switchMode(AppMode newMode, Proyecto proyecto, Elemento selectedElement, ObservableList<Proyecto> projects) {
        this.currentMode = newMode;

        // Limpieza completa de paneles
        leftPanel.getChildren().clear();
        rightPanel.getChildren().clear();

        // Reconstrucción según modo
        if (newMode == AppMode.DESIGN) {
            buildDesignModePanels(proyecto, selectedElement);
        } else {
            buildProductionModePanels(projects, proyecto);
        }

        // Repintado final (por seguridad)
        if (onCanvasRedraw != null) {
            onCanvasRedraw.run();
        }
    }

    // ===================== MODO DISEÑO =====================

    private void buildDesignModePanels(Proyecto proyecto, Elemento selectedElement) {
        // Panel izquierdo: herramientas + capas
        VBox toolbox = buildToolboxPanel();
        layersPanel = buildLayersPanel(proyecto, selectedElement);
        leftPanel.getChildren().addAll(toolbox, new Separator(), layersPanel);

        // Panel derecho: propiedades del elemento seleccionado (o vacío si no hay selección)
        VBox properties = propertiesPanelController.buildPanel(selectedElement, proyecto);
        rightPanel.getChildren().setAll(properties);
    }

    /**
     * Refresca solo el panel de capas. Útil cuando se añaden/eliminan elementos o cambia el lado (frente/dorso).
     */
    public void refreshLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        if (layersPanel == null) return;

        VBox newLayers = buildLayersPanel(proyecto, selectedElement);

        int index = leftPanel.getChildren().indexOf(layersPanel);
        if (index != -1) {
            leftPanel.getChildren().set(index, newLayers);
            layersPanel = newLayers;
        }
    }

    private VBox buildToolboxPanel() {
        VBox toolbox = new VBox(8);
        toolbox.setPadding(new Insets(12));

        Label lblToolbox = new Label("Herramientas");
        lblToolbox.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        Button btnTexto = new Button("T Texto");
        btnTexto.setMaxWidth(Double.MAX_VALUE);
        btnTexto.getStyleClass().add("toolbox-btn");
        btnTexto.setOnAction(e -> {
            if (onAddText != null) onAddText.run();
        });

        Button btnImagen = new Button("🖼 Imagen");
        btnImagen.setMaxWidth(Double.MAX_VALUE);
        btnImagen.getStyleClass().add("toolbox-btn");
        btnImagen.setOnAction(e -> {
            if (onAddImage != null) onAddImage.run();
        });

        Button btnFondo = new Button("🎨 Fondo");
        btnFondo.setMaxWidth(Double.MAX_VALUE);
        btnFondo.getStyleClass().add("toolbox-btn");
        btnFondo.setOnAction(e -> {
            if (onAddBackground != null) onAddBackground.run();
        });

        // Reservado para futuro (formas)
        Button btnRectangulo = new Button("▭ Rectángulo");
        btnRectangulo.setMaxWidth(Double.MAX_VALUE);
        btnRectangulo.getStyleClass().add("toolbox-btn");
        btnRectangulo.setDisable(true);

        Button btnElipse = new Button("○ Elipse");
        btnElipse.setMaxWidth(Double.MAX_VALUE);
        btnElipse.getStyleClass().add("toolbox-btn");
        btnElipse.setDisable(true);

        toolbox.getChildren().addAll(lblToolbox, btnTexto, btnImagen, btnFondo, btnRectangulo, btnElipse);
        return toolbox;
    }

    /**
     * Construye la lista de capas (fondo + elementos). El fondo se muestra arriba si existe.
     */
    private VBox buildLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12));

        Label lblCapas = new Label("Capas");
        lblCapas.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        ListView<Elemento> listCapas = new ListView<>();
        listCapas.getStyleClass().add("project-list");
        listCapas.setPrefHeight(200);

        if (proyecto != null) {
            ObservableList<Elemento> allElements = FXCollections.observableArrayList();

            // Fondo (si hay) + elementos del lado actual
            ImagenFondoElemento fondo = proyecto.getFondoActual();
            if (fondo != null) allElements.add(fondo);
            allElements.addAll(proyecto.getElementosActuales());

            listCapas.setItems(allElements);

            // Si nos pasan el elemento seleccionado, intentamos reflejarlo en la lista
            if (selectedElement != null) {
                listCapas.getSelectionModel().select(selectedElement);
            }

            listCapas.setCellFactory(lv -> new ListCell<Elemento>() {
                @Override
                protected void updateItem(Elemento item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        setText(null);
                        setContextMenu(null);
                        return;
                    }

                    String lockIcon = item.isLocked() ? "🔒 " : "";
                    setText(lockIcon + item.toString());

                    // Menú contextual según tipo
                    ContextMenu contextMenu = new ContextMenu();

                    if (item instanceof ImagenFondoElemento) {
                        MenuItem menuEditar = new MenuItem("Editar imagen externa...");
                        menuEditar.setOnAction(e -> {
                            if (onEditExternal != null) onEditExternal.accept((ImagenFondoElemento) item);
                        });

                        MenuItem menuRecargar = new MenuItem("Recargar desde disco");
                        menuRecargar.setOnAction(e -> {
                            if (onReload != null) onReload.accept((ImagenFondoElemento) item);
                        });

                        MenuItem menuLock = new MenuItem(item.isLocked() ? "Desbloquear" : "Bloquear");
                        menuLock.setOnAction(e -> {
                            if (onToggleLock != null) onToggleLock.accept(item);
                            // Esto ayuda a que el icono 🔒 se vea actualizado sin tener que reconstruir paneles
                            listCapas.refresh();
                        });

                        contextMenu.getItems().addAll(menuEditar, menuRecargar, new SeparatorMenuItem(), menuLock);

                    } else {
                        MenuItem menuEliminar = new MenuItem("Eliminar");
                        menuEliminar.setOnAction(e -> {
                            proyecto.getElementosActuales().remove(item);

                            // Si borramos lo seleccionado, limpiamos selección en UI
                            if (onElementSelected != null) onElementSelected.accept(null);

                            // Refrescamos la lista (y normalmente el canvas se repinta vía controller)
                            listCapas.refresh();
                        });

                        contextMenu.getItems().add(menuEliminar);
                    }

                    setContextMenu(contextMenu);
                }
            });

            // Selección de capas -> se lo comunicamos al controlador
            listCapas.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (onElementSelected != null) onElementSelected.accept(newVal);
            });
        }

        panel.getChildren().addAll(lblCapas, listCapas);
        return panel;
    }

    // ===================== MODO PRODUCCIÓN =====================

    private void buildProductionModePanels(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = buildProjectListPanel(projects, currentProject);
        leftPanel.getChildren().add(projectPanel);

        VBox exportPanel = buildExportPanel();
        exportPanel.setStyle("-fx-background-color: #1e1b1c;");

        ScrollPane scrollPane = new ScrollPane(exportPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #1e1b1c; -fx-background-color: #1e1b1c; -fx-padding: 0;");
        scrollPane.setPadding(Insets.EMPTY);

        rightPanel.getChildren().add(scrollPane);
    }

    private VBox buildProjectListPanel(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = new VBox(8);
        projectPanel.setPadding(new Insets(12));

        Label lblTrabajos = new Label("Trabajos");
        lblTrabajos.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        ListView<Proyecto> listProyectos = new ListView<>();
        listProyectos.setItems(projects);
        listProyectos.getStyleClass().add("project-list");
        listProyectos.setPrefHeight(400);

        // Si hay proyecto actual, lo seleccionamos al abrir el modo producción
        if (currentProject != null) {
            listProyectos.getSelectionModel().select(currentProject);
        }

        listProyectos.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && onProjectSelected != null) onProjectSelected.accept(newVal);
        });

        // Doble click para editar
        listProyectos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Proyecto seleccionado = listProyectos.getSelectionModel().getSelectedItem();
                if (seleccionado != null && seleccionado.getMetadata() != null) {
                    if (onEditProject != null) {
                        // Lo normal: que el MainViewController gestione el flujo
                        onEditProject.accept(seleccionado);
                    } else {
                        // Alternativa: si no hay callback, tiramos del helper interno (si hay projectManager)
                        abrirDialogoEditarProyecto(seleccionado);
                    }
                }
            }
        });

        Button btnNuevoCR80 = new Button("+ Nuevo CR80");
        btnNuevoCR80.getStyleClass().add("primary-btn");
        btnNuevoCR80.setMaxWidth(Double.MAX_VALUE);
        btnNuevoCR80.setOnAction(e -> {
            if (onNewCR80 != null) onNewCR80.run();
        });

        projectPanel.getChildren().addAll(lblTrabajos, listProyectos, btnNuevoCR80);
        return projectPanel;
    }

    /**
     * Helper interno por si se quiere que ModeManager resuelva el diálogo.
     * Si usas callbacks (lo ideal), este método puede quedarse sin usar.
     */
    private void abrirDialogoEditarProyecto(Proyecto proyecto) {
        if (projectManager == null) return;

        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto);
        Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            projectManager.eliminarProyecto(proyecto);
            return;
        }

        if (resultado.isPresent()) {
            projectManager.editarProyecto(proyecto, resultado.get());
        }
    }

    /**
     * Panel de exportación. De momento son placeholders, pero deja el hueco preparado.
     */
    private VBox buildExportPanel() {
        VBox exportPanel = new VBox(15);
        exportPanel.setPadding(new Insets(30));
        exportPanel.setFillWidth(true);

        Label lblExport = new Label("Exportación");
        lblExport.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label lblInfoExp = new Label("Formato: PNG/PDF (pendiente)");
        lblInfoExp.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblDpi = new Label("DPI: 300 (pendiente)");
        lblDpi.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblGuias = new Label("Incluir guías: No (pendiente)");
        lblGuias.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblSide = new Label("Exportar: Frente (pendiente)");
        lblSide.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Button btnDoExport = new Button("Exportar");
        btnDoExport.getStyleClass().add("success-btn");
        btnDoExport.setMaxWidth(200.0);
        btnDoExport.setOnAction(e -> {
            if (onExport != null) onExport.run();
        });

        exportPanel.getChildren().addAll(
                lblExport,
                lblInfoExp,
                lblDpi,
                lblGuias,
                lblSide,
                new Separator(),
                btnDoExport
        );

        return exportPanel;
    }
}
