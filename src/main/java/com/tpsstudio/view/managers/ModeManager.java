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
 * ¡El Director de Escena! (Stage Manager)
 * Controla qué se muestra en los paneles laterales según si estamos Diseñando o
 * Produciendo.
 * 
 * - MODO DISEÑO: Muestra herramientas (texto, imagen) y lista de capas.
 * - MODO PRODUCCIÓN: Muestra lista de proyectos y opciones de exportación.
 * 
 * Orquesta el cambio de vestuario de la interfaz.
 */
public class ModeManager {

    // Estado actual: ¿Diseñando o Produciendo?
    private AppMode currentMode;

    // Referencias a los contenedores físicos de la UI
    private final VBox leftPanel;
    private final VBox rightPanel;

    // Colaboradores
    private final PropertiesPanelController propertiesPanelController;
    private com.tpsstudio.service.ProjectManager projectManager;

    // Cables de comunicación (Eventos hacia arriba)
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
     * @param leftPanel                 Panel izquierdo (a rellenar dinámicamente)
     * @param rightPanel                Panel derecho (a rellenar dinámicamente)
     * @param propertiesPanelController Controlador de propiedades (se usa en
     *                                  diseño)
     */
    public ModeManager(VBox leftPanel, VBox rightPanel, PropertiesPanelController propertiesPanelController) {
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.propertiesPanelController = propertiesPanelController;
        this.currentMode = AppMode.DESIGN;
    }

    // ========== CONEXIONES (SETTERS DE CALLBACKS) ==========

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

    // ========== CONTROL DE ESCENA (MODOS) ==========

    public AppMode getCurrentMode() {
        return currentMode;
    }

    /**
     * El Gran Cambio: Pasa de Diseño a Producción (o viceversa).
     * Limpia los paneles laterales y los reconstruye con los widgets adecuados.
     */
    public void switchMode(AppMode newMode, Proyecto proyecto, Elemento selectedElement,
            ObservableList<Proyecto> projects) {
        this.currentMode = newMode;

        // Limpieza total
        leftPanel.getChildren().clear();
        rightPanel.getChildren().clear();

        // Reconstrucción según el guion
        if (newMode == AppMode.DESIGN) {
            buildDesignModePanels(proyecto, selectedElement);
        } else {
            buildProductionModePanels(projects, proyecto);
        }

        // Forzar repintado por si acaso
        if (onCanvasRedraw != null) {
            onCanvasRedraw.run();
        }
    }

    // ========== ESCENARIO 1: MODO DISEÑO ==========

    private VBox layersPanel; // Guardamos referencia para refrescos rápidos

    /**
     * Monta el set de Diseño:
     * - Izquierda: Herramientas y Capas
     * - Derecha: Propiedades del elemento
     */
    private void buildDesignModePanels(Proyecto proyecto, Elemento selectedElement) {
        // IZQUIERDA
        VBox toolbox = buildToolboxPanel();
        layersPanel = buildLayersPanel(proyecto, selectedElement);
        leftPanel.getChildren().addAll(toolbox, new Separator(), layersPanel);

        // DERECHA
        VBox properties = propertiesPanelController.buildPanel(selectedElement, proyecto);
        rightPanel.getChildren().setAll(properties);
    }

    /**
     * Truco de magia: Actualiza solo el panel de capas sin tocar el resto.
     * Útil cuando movemos capas o cambiamos visibilidad.
     */
    public void refreshLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        if (leftPanel.getChildren().size() >= 3) {
            // Asumimos estructura fija: [0]Toolbox, [1]Separator, [2]Layers
            VBox newLayers = buildLayersPanel(proyecto, selectedElement);

            // Reemplazo quirúrgico
            int index = leftPanel.getChildren().indexOf(layersPanel);
            if (index != -1) {
                leftPanel.getChildren().set(index, newLayers);
                layersPanel = newLayers;
            }
        }
    }

    /**
     * Caja de Herramientas: Botones para añadir cosas.
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

        // Placeholders para futuro
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
     * Panel de Capas: Lista de elementos en el lienzo.
     * Incluye menú contextual para bloquear, borrar, etc.
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
            // Unificamos fondo y elementos en una sola lista visual
            ObservableList<Elemento> allElements = FXCollections.observableArrayList();
            ImagenFondoElemento fondo = proyecto.getFondoActual();
            if (fondo != null) {
                allElements.add(fondo);
            }
            allElements.addAll(proyecto.getElementosActuales());

            listCapas.setItems(allElements);

            // Personalización de celda (Iconos y Click derecho)
            listCapas.setCellFactory(lv -> new ListCell<Elemento>() {
                @Override
                protected void updateItem(Elemento item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setContextMenu(null);
                    } else {
                        String lockIcon = item.isLocked() ? "🔒 " : "";
                        setText(lockIcon + item.toString());

                        // Menú Click Derecho
                        ContextMenu contextMenu = new ContextMenu();

                        if (item instanceof ImagenFondoElemento) {
                            // Opciones exclusivas de Fondo
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
                            // Elementos normales
                            MenuItem menuEliminar = new MenuItem("Eliminar");
                            menuEliminar.setOnAction(e -> {
                                if (proyecto != null) {
                                    proyecto.getElementosActuales().remove(item);
                                    if (onElementSelected != null) {
                                        onElementSelected.accept(null);
                                    }
                                }
                            });
                            contextMenu.getItems().add(menuEliminar);
                        }

                        setContextMenu(contextMenu);
                    }
                }
            });

            // Selección
            listCapas.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (onElementSelected != null) {
                    onElementSelected.accept(newVal);
                }
            });
        }

        layersPanel.getChildren().addAll(lblCapas, listCapas);
        return layersPanel;
    }

    // ========== ESCENARIO 2: MODO PRODUCCIÓN ==========

    /**
     * Monta el set de Producción:
     * - Izquierda: Lista de Proyectos (Portfolio)
     * - Derecha: Opciones de Exportación
     */
    private void buildProductionModePanels(ObservableList<Proyecto> projects, Proyecto currentProject) {
        // IZQUIERDA
        VBox projectPanel = buildProjectListPanel(projects, currentProject);
        leftPanel.getChildren().add(projectPanel);

        // DERECHA (Con scroll por si hay muchas opciones)
        VBox exportPanel = buildExportPanel();

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
     * Panel de Lista de Proyectos
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

        // Selección de proyecto
        listProyectos.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && onProjectSelected != null) {
                onProjectSelected.accept(newVal);
            }
        });

        // Doble Clic para editar metadatos
        listProyectos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Proyecto proyectoSeleccionado = listProyectos.getSelectionModel().getSelectedItem();
                if (proyectoSeleccionado != null && proyectoSeleccionado.getMetadata() != null) {
                    if (onEditProject != null) {
                        onEditProject.accept(proyectoSeleccionado);
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
     * Helper para abrir el diálogo de edición (Delegado al click handler)
     */
    private void abrirDialogoEditarProyecto(Proyecto proyecto) {
        // ... Lógica delegada al ProjectManager o invocada desde callbacks ...
        // Este método se deja como referencia o para uso futuro interno
        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto);
        java.util.Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            projectManager.eliminarProyecto(proyecto);
        } else if (resultado.isPresent()) {
            ProyectoMetadata nuevaMetadata = resultado.get();
            projectManager.editarProyecto(proyecto, nuevaMetadata);
        }
    }

    /**
     * Panel de Exportación (Futuro)
     */
    private VBox buildExportPanel() {
        VBox exportPanel = new VBox(15);
        exportPanel.setPadding(new Insets(30));
        exportPanel.setFillWidth(true);

        Label lblExport = new Label("Exportación");
        lblExport.setStyle("-fx-text-fill: #e8e6e7; -fx-font-size: 14px; -fx-font-weight: bold;");

        // Placeholders informativos
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
            if (onExport != null)
                onExport.run();
        });

        exportPanel.getChildren().addAll(lblExport, lblInfoExp, lblDpi, lblGuias, lblSide, new Separator(),
                btnDoExport);
        return exportPanel;
    }
}
