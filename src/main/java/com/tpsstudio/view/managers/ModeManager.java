package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.*;
import com.tpsstudio.view.dialogs.EditarProyectoDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Manages application mode switching and panel building for DESIGN and
 * PRODUCTION modes.
 * 
 * Responsibilities:
 * - Switch between DESIGN (editing) and PRODUCTION (export) modes
 * - Build left/right panels for each mode
 * - Coordinate with PropertiesPanelController for properties panel
 */
public class ModeManager {

    // Current application mode
    private AppMode currentMode;

    // Panel containers (references from MainViewController)
    private final VBox leftPanel;
    private final VBox rightPanel;

    // Reference to PropertiesPanelController for properties panel
    private final PropertiesPanelController propertiesPanelController;

    // Reference to ProjectManager for project operations
    private com.tpsstudio.service.ProjectManager projectManager;

    // Callbacks for actions
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

    /**
     * Constructor
     * 
     * @param leftPanel                 Left panel container
     * @param rightPanel                Right panel container
     * @param propertiesPanelController Controller for properties panel
     */
    public ModeManager(VBox leftPanel, VBox rightPanel, PropertiesPanelController propertiesPanelController) {
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.propertiesPanelController = propertiesPanelController;
        this.currentMode = AppMode.DESIGN;
    }

    // ========== CALLBACK SETTERS ==========

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

    public void setOnEditProject(Consumer<Proyecto> callback) {
        this.onEditProject = callback;
    }

    public void setProjectManager(com.tpsstudio.service.ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    // ========== MODE MANAGEMENT ==========

    public AppMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Switches to a new mode and rebuilds panels accordingly
     * 
     * @param newMode         New mode to switch to
     * @param proyecto        Current project
     * @param selectedElement Currently selected element
     * @param projects        List of all projects
     */
    public void switchMode(AppMode newMode, Proyecto proyecto, Elemento selectedElement,
            ObservableList<Proyecto> projects) {
        this.currentMode = newMode;

        // Clear panels
        leftPanel.getChildren().clear();
        rightPanel.getChildren().clear();

        if (newMode == AppMode.DESIGN) {
            buildDesignModePanels(proyecto, selectedElement);
        } else {
            buildProductionModePanels(projects, proyecto);
        }

        // Trigger canvas redraw
        if (onCanvasRedraw != null) {
            onCanvasRedraw.run();
        }
    }

    // ========== DESIGN MODE PANELS ==========

    /**
     * Builds all panels for DESIGN mode (toolbox, layers, properties)
     */
    private void buildDesignModePanels(Proyecto proyecto, Elemento selectedElement) {
        // LEFT: Toolbox + Layers
        VBox toolbox = buildToolboxPanel();
        VBox layers = buildLayersPanel(proyecto, selectedElement);
        leftPanel.getChildren().addAll(toolbox, new Separator(), layers);

        // RIGHT: Properties
        VBox properties = propertiesPanelController.buildPanel(selectedElement, proyecto);
        rightPanel.getChildren().setAll(properties);
    }

    /**
     * Builds the toolbox panel with element creation buttons
     */
    private VBox buildToolboxPanel() {
        VBox toolbox = new VBox(8);
        toolbox.setPadding(new Insets(12));

        Label lblToolbox = new Label("Herramientas");
        lblToolbox.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        Button btnTexto = new Button("T Texto");
        btnTexto.setOnAction(e -> {
            if (onAddText != null)
                onAddText.run();
        });
        btnTexto.setMaxWidth(Double.MAX_VALUE);
        btnTexto.getStyleClass().add("toolbox-btn");

        Button btnImagen = new Button("🖼 Imagen");
        btnImagen.setOnAction(e -> {
            if (onAddImage != null)
                onAddImage.run();
        });
        btnImagen.setMaxWidth(Double.MAX_VALUE);
        btnImagen.getStyleClass().add("toolbox-btn");

        Button btnFondo = new Button("🎨 Fondo");
        btnFondo.setOnAction(e -> {
            if (onAddBackground != null)
                onAddBackground.run();
        });
        btnFondo.setMaxWidth(Double.MAX_VALUE);
        btnFondo.getStyleClass().add("toolbox-btn");

        Button btnRectangulo = new Button("▭ Rectángulo");
        btnRectangulo.setMaxWidth(Double.MAX_VALUE);
        btnRectangulo.getStyleClass().add("toolbox-btn");
        btnRectangulo.setDisable(true); // Placeholder

        Button btnElipse = new Button("○ Elipse");
        btnElipse.setMaxWidth(Double.MAX_VALUE);
        btnElipse.getStyleClass().add("toolbox-btn");
        btnElipse.setDisable(true); // Placeholder

        toolbox.getChildren().addAll(lblToolbox, btnTexto, btnImagen, btnFondo, btnRectangulo, btnElipse);
        return toolbox;
    }

    /**
     * Builds the layers panel showing all elements in the project
     */
    private VBox buildLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        VBox layersPanel = new VBox(8);
        layersPanel.setPadding(new Insets(12, 12, 12, 12));

        Label lblCapas = new Label("Capas");
        lblCapas.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        ListView<Elemento> listCapas = new ListView<>();
        listCapas.getStyleClass().add("project-list");
        listCapas.setPrefHeight(200);

        if (proyecto != null) {
            // Combine background + normal elements
            ObservableList<Elemento> allElements = FXCollections.observableArrayList();
            ImagenFondoElemento fondo = proyecto.getFondoActual();
            if (fondo != null) {
                allElements.add(fondo);
            }
            allElements.addAll(proyecto.getElementosActuales());

            listCapas.setItems(allElements);

            // Custom cell factory to show lock icon for background
            listCapas.setCellFactory(lv -> new ListCell<Elemento>() {
                @Override
                protected void updateItem(Elemento item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setContextMenu(null);
                    } else {
                        String lockIcon = item.isLocked() ? "🔒 " : "";
                        setText(lockIcon + item.getNombre());

                        // Context menu for all elements
                        ContextMenu contextMenu = new ContextMenu();

                        // Background-specific options
                        if (item instanceof ImagenFondoElemento) {
                            MenuItem menuEditar = new MenuItem("Editar imagen externa...");
                            menuEditar.setOnAction(e -> {
                                if (onEditExternal != null) {
                                    onEditExternal.accept((ImagenFondoElemento) item);
                                }
                            });

                            MenuItem menuRecargar = new MenuItem("Recargar desde disco");
                            menuRecargar.setOnAction(e -> {
                                if (onReload != null) {
                                    onReload.accept((ImagenFondoElemento) item);
                                }
                            });

                            MenuItem menuLock = new MenuItem(item.isLocked() ? "Desbloquear" : "Bloquear");
                            menuLock.setOnAction(e -> {
                                if (onToggleLock != null) {
                                    onToggleLock.accept(item);
                                }
                            });

                            contextMenu.getItems().addAll(menuEditar, menuRecargar, new SeparatorMenuItem(), menuLock);
                        } else {
                            // Regular elements - just delete option
                            MenuItem menuEliminar = new MenuItem("Eliminar");
                            menuEliminar.setOnAction(e -> {
                                if (proyecto != null) {
                                    proyecto.getElementosActuales().remove(item);
                                    if (onElementSelected != null) {
                                        onElementSelected.accept(null); // Deselect
                                    }
                                }
                            });
                            contextMenu.getItems().add(menuEliminar);
                        }

                        setContextMenu(contextMenu);
                    }
                }
            });

            listCapas.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (onElementSelected != null) {
                    onElementSelected.accept(newVal);
                }
            });
        }

        layersPanel.getChildren().addAll(lblCapas, listCapas);
        return layersPanel;
    }

    // ========== PRODUCTION MODE PANELS ==========

    /**
     * Builds all panels for PRODUCTION mode (project list, export controls)
     */
    private void buildProductionModePanels(ObservableList<Proyecto> projects, Proyecto currentProject) {
        // LEFT: Project list
        VBox projectPanel = buildProjectListPanel(projects, currentProject);
        leftPanel.getChildren().add(projectPanel);

        // RIGHT: Export controls
        VBox exportPanel = buildExportPanel();

        // Wrap in ScrollPane for consistency
        exportPanel.setStyle("-fx-background-color: #1e1b1c;");
        ScrollPane scrollPane = new ScrollPane(exportPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #1e1b1c; -fx-background-color: #1e1b1c; -fx-padding: 0;");
        scrollPane.setPadding(javafx.geometry.Insets.EMPTY);

        rightPanel.getChildren().add(scrollPane);
    }

    /**
     * Builds the project list panel
     */
    private VBox buildProjectListPanel(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = new VBox(8);
        projectPanel.setPadding(new Insets(12));

        Label lblTrabajos = new Label("Trabajos");
        lblTrabajos.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        ListView<Proyecto> listProyectos = new ListView<>();
        listProyectos.setItems(projects);
        listProyectos.getStyleClass().add("project-list");
        listProyectos.setPrefHeight(400);
        listProyectos.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && onProjectSelected != null) {
                onProjectSelected.accept(newVal);
            }
        });

        // Añadir listener de doble clic para editar proyecto
        listProyectos.setOnMouseClicked(event -> {
            System.out.println("[DEBUG] Mouse clicked, count: " + event.getClickCount());
            if (event.getClickCount() == 2) {
                Proyecto proyectoSeleccionado = listProyectos.getSelectionModel().getSelectedItem();
                System.out.println("[DEBUG] Doble clic detectado, proyecto: "
                        + (proyectoSeleccionado != null ? proyectoSeleccionado.getNombre() : "null"));
                if (proyectoSeleccionado != null && proyectoSeleccionado.getMetadata() != null) {
                    System.out.println("[DEBUG] Llamando a onEditProject callback");
                    if (onEditProject != null) {
                        onEditProject.accept(proyectoSeleccionado);
                    } else {
                        System.out.println("[DEBUG] ERROR: onEditProject callback es null!");
                    }
                }
            }
        });

        Button btnNuevoCR80 = new Button("+ Nuevo CR80");
        btnNuevoCR80.setOnAction(e -> {
            if (onNewCR80 != null)
                onNewCR80.run();
        });
        btnNuevoCR80.getStyleClass().add("primary-btn");
        btnNuevoCR80.setMaxWidth(Double.MAX_VALUE);

        projectPanel.getChildren().addAll(lblTrabajos, listProyectos, btnNuevoCR80);
        return projectPanel;
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

    /**
     * Builds the export controls panel
     */
    private VBox buildExportPanel() {
        VBox exportPanel = new VBox(15);
        exportPanel.setPadding(new Insets(30));
        exportPanel.setFillWidth(true);

        Label lblExport = new Label("Exportación");
        lblExport.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label lblInfoExp = new Label("Formato: PNG/PDF (placeholder)");
        lblInfoExp.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblDpi = new Label("DPI: 300 (placeholder)");
        lblDpi.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblGuias = new Label("Incluir guías: No (placeholder)");
        lblGuias.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Label lblSide = new Label("Exportar: Frente (placeholder)");
        lblSide.setStyle("-fx-text-fill: #c4c0c2; -fx-font-size: 12px;");

        Button btnDoExport = new Button("Exportar");
        btnDoExport.getStyleClass().add("success-btn");
        btnDoExport.setMaxWidth(200.0);
        btnDoExport.setOnAction(e -> {
            if (onExport != null)
                onExport.run();
        });

        exportPanel.getChildren().addAll(lblExport, lblInfoExp, lblDpi, lblGuias, lblSide, new Separator(),
                btnDoExport);
        return exportPanel;
    }
}
