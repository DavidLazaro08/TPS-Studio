package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.project.Etiqueta;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.service.EtiquetasManager;
import com.tpsstudio.service.ProjectManager;
import com.tpsstudio.util.AnimationHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gestor especializado para la vista de Producción (Lista de Proyectos y Gestión).
 */
public class ProductionViewManager {

    private final VBox leftPanel;
    private EtiquetasManager etiquetasManager;
    private ProjectManager projectManager;

    // Estado de filtrado
    private ObservableList<Proyecto> proyectosFiltrados;
    private String filtroTexto = "";
    private List<String> filtroActivo = new ArrayList<>();
    private boolean omitirConfirmacionBorradoEtiqueta = false;

    // UI Components
    private Button btnFiltro;
    private javafx.stage.Popup filtroPopup;
    private HBox panelOcultos;
    private Label lblOcultosText;
    private HBox filtroInfoBox;
    private Label lblFiltroActual;
    private javafx.animation.Timeline recordatorioTimer;

    // Callbacks
    private final Consumer<Proyecto> onProjectSelected;
    private final Consumer<Proyecto> onEditProject;
    private final Runnable onNewCR80;

    public ProductionViewManager(VBox leftPanel,
                                EtiquetasManager etiquetasManager,
                                ProjectManager projectManager,
                                Consumer<Proyecto> onProjectSelected,
                                Consumer<Proyecto> onEditProject,
                                Runnable onNewCR80) {
        this.leftPanel = leftPanel;
        this.etiquetasManager = etiquetasManager;
        this.projectManager = projectManager;
        this.onProjectSelected = onProjectSelected;
        this.onEditProject = onEditProject;
        this.onNewCR80 = onNewCR80;
    }

    public void setEtiquetasManager(EtiquetasManager etiquetasManager) {
        this.etiquetasManager = etiquetasManager;
    }

    public void setProjectManager(ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    /** Permite sincronizar el filtro desde fuera (ej: al inyectar EtiquetasManager) */
    public void setFiltroActivo(List<String> ids) {
        this.filtroActivo = new ArrayList<>(ids);
    }

    public void buildProductionModePanels(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = buildProjectListPanel(projects, currentProject);
        leftPanel.getChildren().add(projectPanel);
    }

    private VBox buildProjectListPanel(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = new VBox(12);
        projectPanel.setPadding(new Insets(14, 12, 14, 12));
        VBox.setVgrow(projectPanel, Priority.ALWAYS);

        Label lblTrabajos = new Label("Gestión de Trabajos");
        lblTrabajos.getStyleClass().add("panel-title");
        Label lblSubtitulo = new Label("Administración y exportación");
        lblSubtitulo.getStyleClass().add("panel-placeholder");
        VBox titulos = new VBox(2, lblTrabajos, lblSubtitulo);
        HBox.setHgrow(titulos, Priority.ALWAYS);

        this.btnFiltro = crearBotonFiltro(projects);
        HBox header = new HBox(titulos, btnFiltro);
        header.setAlignment(Pos.CENTER_LEFT);

        proyectosFiltrados = FXCollections.observableArrayList();
        actualizarListaFiltrada(projects);

        TextField txtBusqueda = new TextField();
        txtBusqueda.setPromptText("Buscar por nombre, cliente...");
        txtBusqueda.getStyleClass().add("search-field");
        txtBusqueda.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: white; -fx-prompt-text-fill: #5a6090; -fx-background-radius: 12; -fx-padding: 8 12; -fx-font-size: 11.5px;");
        txtBusqueda.textProperty().addListener((obs, old, val) -> {
            filtroTexto = val;
            actualizarListaFiltrada(projects);
        });
        txtBusqueda.setText(filtroTexto);

        ListView<Proyecto> listProyectos = new ListView<>(proyectosFiltrados);
        listProyectos.getStyleClass().add("project-list");
        VBox.setVgrow(listProyectos, Priority.ALWAYS);
        listProyectos.maxHeightProperty().bind(leftPanel.heightProperty().subtract(200));

        listProyectos.setCellFactory(lv -> new ListCell<Proyecto>() {
            private javafx.animation.Timeline pulse;

            @Override
            protected void updateItem(Proyecto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setPadding(Insets.EMPTY);
                    if (pulse != null) { pulse.stop(); pulse = null; }
                } else {
                    Region activeBar = new Region();
                    activeBar.setPrefWidth(4);
                    activeBar.setMinWidth(4);
                    activeBar.setMaxWidth(4);
                    activeBar.setMaxHeight(Double.MAX_VALUE);
                    activeBar.getStyleClass().add("project-active-bar");
                    activeBar.setOpacity(0.0);
                    activeBar.setVisible(false);

                    VBox textContainer = new VBox(2);
                    textContainer.setAlignment(Pos.CENTER_LEFT);
                    Label lblName = new Label(item.getNombre());
                    lblName.getStyleClass().add("project-cell-name");
                    lblName.setMaxWidth(180);
                    lblName.setEllipsisString("…");
                    HBox.setHgrow(lblName, Priority.SOMETIMES);
                    
                    Label lblEmpresa = new Label("");
                    lblEmpresa.getStyleClass().add("project-cell-type");
                    if (item.getMetadata() != null && item.getMetadata().getClienteInfo() != null) {
                        String empresa = item.getMetadata().getClienteInfo().getNombreEmpresa();
                        if (empresa != null && !empresa.trim().isEmpty()) {
                            lblEmpresa.setText("Cliente: " + empresa);
                        }
                    }
                    textContainer.getChildren().addAll(lblName, lblEmpresa);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    Label lblBadge = new Label(item.getTipo());
                    lblBadge.getStyleClass().add("project-badge");

                    HBox contentRow = new HBox(0);
                    contentRow.setAlignment(Pos.CENTER_LEFT);
                    contentRow.setMaxWidth(Double.MAX_VALUE);
                    contentRow.setMaxHeight(Double.MAX_VALUE);
                    
                    HBox textAndBadge = new HBox(10);
                    textAndBadge.setAlignment(Pos.CENTER_LEFT);
                    textAndBadge.setPadding(new Insets(0, 12, 0, 12));
                    HBox.setHgrow(textAndBadge, Priority.ALWAYS);
                    
                    Label btnOptions = new Label("⋯");
                    btnOptions.setStyle("-fx-text-fill: #a0a5cc; -fx-cursor: hand; -fx-font-size: 26px; -fx-font-weight: bold; -fx-padding: 0;");
                    btnOptions.setVisible(false); 
                    btnOptions.setPickOnBounds(true);
                    btnOptions.setOnMousePressed(e -> {
                        mostrarProjectOptionsMenu(item, btnOptions, projects);
                        e.consume();
                    });

                    VBox rightActionBox = new VBox(-6);
                    rightActionBox.setAlignment(Pos.CENTER);
                    rightActionBox.getChildren().addAll(btnOptions, lblBadge);
                    VBox.setMargin(btnOptions, new Insets(0, 0, 4, 0));

                    textAndBadge.getChildren().addAll(textContainer, spacer, rightActionBox);
                    contentRow.getChildren().addAll(activeBar, textAndBadge);

                    Region hoverOverlay = new Region();
                    hoverOverlay.getStyleClass().add("project-hover-overlay");
                    hoverOverlay.setOpacity(0.0);
                    hoverOverlay.setMouseTransparent(true);
                    hoverOverlay.setMaxWidth(Double.MAX_VALUE);
                    hoverOverlay.setMaxHeight(Double.MAX_VALUE);

                    Region selectedOverlay = new Region();
                    selectedOverlay.getStyleClass().add("project-selected-overlay");
                    selectedOverlay.setOpacity(0.0);
                    selectedOverlay.setMouseTransparent(true);
                    selectedOverlay.setMaxWidth(Double.MAX_VALUE);
                    selectedOverlay.setMaxHeight(Double.MAX_VALUE);

                    StackPane card = new StackPane();
                    card.getStyleClass().add("project-card");
                    card.setPrefHeight(62);
                    card.setMinHeight(62);
                    card.getChildren().addAll(hoverOverlay, selectedOverlay, contentRow);

                    Rectangle clip = new Rectangle();
                    clip.setArcWidth(16);
                    clip.setArcHeight(16);
                    card.layoutBoundsProperty().addListener((obs, old, b) -> {
                        clip.setWidth(b.getWidth());
                        clip.setHeight(b.getHeight());
                    });
                    card.setClip(clip);

                    Region separator = new Region();
                    separator.getStyleClass().add("project-separator");
                    separator.setPrefHeight(1);
                    separator.setMaxWidth(Double.MAX_VALUE);
                    VBox.setMargin(separator, new Insets(0, 8, 0, 8));

                    VBox cellLayout = new VBox(0);
                    cellLayout.getChildren().addAll(card, separator);
                    setGraphic(cellLayout);
                    setText(null);
                    setPadding(new Insets(6, 8, 0, 8));

                    javafx.animation.FadeTransition hoverIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_FAST), hoverOverlay);
                    hoverIn.setToValue(0.6);
                    javafx.animation.FadeTransition hoverOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), hoverOverlay);
                    hoverOut.setToValue(0.0);

                    if (isSelected()) {
                        javafx.animation.FadeTransition selectIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_SLOW), selectedOverlay);
                        selectIn.setFromValue(0.0);
                        selectIn.setToValue(1.0);
                        selectIn.play();

                        activeBar.setVisible(true);
                        if (pulse == null) {
                            pulse = new javafx.animation.Timeline(
                                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, new javafx.animation.KeyValue(activeBar.opacityProperty(), 0.35, javafx.animation.Interpolator.EASE_BOTH)),
                                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1.2), new javafx.animation.KeyValue(activeBar.opacityProperty(), 1.0, javafx.animation.Interpolator.EASE_BOTH)),
                                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2.4), new javafx.animation.KeyValue(activeBar.opacityProperty(), 0.35, javafx.animation.Interpolator.EASE_BOTH))
                            );
                            pulse.setCycleCount(javafx.animation.Timeline.INDEFINITE);
                            pulse.play();
                        }
                    } else {
                        activeBar.setVisible(false);
                        if (pulse != null) { pulse.stop(); pulse = null; }
                    }

                    card.setOnMouseEntered(e -> { 
                        if (!isSelected()) hoverIn.playFromStart(); 
                        btnOptions.setVisible(true);
                    });
                    card.setOnMouseExited(e  -> { 
                        if (!isSelected()) hoverOut.playFromStart(); 
                        btnOptions.setVisible(false);
                    });
                }
            }
        });

        if (currentProject != null) {
            Proyecto equivalente = proyectosFiltrados.stream()
                    .filter(p -> p.getId() == currentProject.getId())
                    .findFirst()
                    .orElse(null);
            
            if (equivalente != null) {
                javafx.application.Platform.runLater(() -> {
                    listProyectos.getSelectionModel().select(equivalente);
                    listProyectos.scrollTo(equivalente);
                });
            }
        }

        listProyectos.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && onProjectSelected != null)
                onProjectSelected.accept(newVal);
        });

        listProyectos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Proyecto seleccionado = listProyectos.getSelectionModel().getSelectedItem();
                if (seleccionado != null && onEditProject != null) {
                    onEditProject.accept(seleccionado);
                }
            }
        });

        Button btnNuevoCR80 = new Button("+ NUEVO PROYECTO CR80");
        btnNuevoCR80.getStyleClass().add("new-project-btn");
        btnNuevoCR80.setStyle("-fx-font-size: 11.5px;");
        btnNuevoCR80.setMaxWidth(Double.MAX_VALUE);
        btnNuevoCR80.setOnAction(e -> { if (onNewCR80 != null) onNewCR80.run(); });

        panelOcultos = new HBox(5);
        panelOcultos.setAlignment(Pos.CENTER);
        panelOcultos.setPadding(new Insets(4, 0, 8, 0));
        panelOcultos.setVisible(false);
        panelOcultos.setManaged(false);

        lblOcultosText = new Label();
        lblOcultosText.setStyle("-fx-text-fill: #a0a5cc; -fx-font-size: 11px;");

        Hyperlink linkVerTodos = new Hyperlink("Ver todos");
        linkVerTodos.setStyle("-fx-text-fill: #6c63ff; -fx-font-size: 11px; -fx-padding: 0; -fx-border-color: transparent; -fx-underline: false;");
        linkVerTodos.setOnAction(e -> {
            filtroActivo.clear();
            if (etiquetasManager != null) etiquetasManager.setFiltroActivo(filtroActivo);
            actualizarListaFiltrada(projects);
            actualizarIconoFiltro(btnFiltro);
        });

        panelOcultos.getChildren().addAll(lblOcultosText, new Label("-"), linkVerTodos);
        ((Label)panelOcultos.getChildren().get(1)).setStyle("-fx-text-fill: #5a6090; -fx-font-size: 11px;");

        filtroInfoBox = new HBox(8);
        filtroInfoBox.setAlignment(Pos.TOP_LEFT);
        filtroInfoBox.setPadding(new Insets(6, 10, 6, 10));
        filtroInfoBox.setVisible(false);
        filtroInfoBox.setManaged(false);
        filtroInfoBox.setStyle("-fx-background-color: rgba(108, 99, 255, 0.05); -fx-border-color: rgba(108, 99, 255, 0.15); -fx-border-width: 0 0 1 0;");
        
        lblFiltroActual = new Label();
        lblFiltroActual.setStyle("-fx-text-fill: #6c63ff; -fx-font-weight: bold; -fx-font-size: 11px;");
        lblFiltroActual.setWrapText(true);
        HBox.setHgrow(lblFiltroActual, Priority.ALWAYS);

        Label lblIcon = new Label("📂");
        lblIcon.setStyle("-fx-font-size: 12px; -fx-padding: 2 0 0 0;");
        
        filtroInfoBox.getChildren().addAll(lblIcon, lblFiltroActual);

        actualizarVisibilidadOcultos(projects);
        projectPanel.getChildren().addAll(header, txtBusqueda, filtroInfoBox, listProyectos, panelOcultos, btnNuevoCR80);
        actualizarListaFiltrada(projects);
        
        return projectPanel;
    }

    private Button crearBotonFiltro(ObservableList<Proyecto> todosLosProyectos) {
        Button btn = new Button();
        actualizarIconoFiltro(btn);
        btn.getStyleClass().add("filter-btn");
        btn.setTooltip(new Tooltip("Filtrar por categoría"));

        btn.setOnAction(e -> {
            if (filtroPopup != null && filtroPopup.isShowing()) {
                if (!filtroPopup.getContent().isEmpty() && filtroPopup.getContent().get(0) instanceof VBox) {
                    VBox content = (VBox) filtroPopup.getContent().get(0);
                    content.setMouseTransparent(true);
                    javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_FAST), content);
                    ft.setToValue(0);
                    ft.setOnFinished(ev -> filtroPopup.hide());
                    ft.play();
                } else {
                    filtroPopup.hide();
                }
            } else {
                mostrarFiltroPopup(btn, todosLosProyectos);
            }
        });
        return btn;
    }

    private void actualizarIconoFiltro(Button btn) {
        if (btn == null) return;
        int activas = filtroActivo.size();
        if (activas > 0) {
            btn.setText("🏷️ " + activas);
            btn.setStyle("-fx-text-fill: #6c63ff; -fx-font-weight: bold;");
        } else {
            btn.setText("🏷️");
            btn.setStyle("");
        }
    }

    private void mostrarFiltroPopup(Button anchor, ObservableList<Proyecto> todosLosProyectos) {
        if (filtroPopup != null && filtroPopup.isShowing()) filtroPopup.hide();
        filtroPopup = new javafx.stage.Popup();
        filtroPopup.setAutoHide(true);

        VBox contenido = new VBox(8);
        contenido.getStyleClass().add("filter-popup");
        contenido.setPadding(new Insets(12));
        contenido.setStyle("-fx-background-color: #1a1b2e; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 12, 0, 0, 4);");
        contenido.setPrefWidth(210);
        contenido.setOpacity(0);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), contenido);
        ft.setToValue(1);
        ft.play();

        ToggleButton btnTodos = new ToggleButton("★ Todos los proyectos");
        btnTodos.setMaxWidth(Double.MAX_VALUE);
        btnTodos.getStyleClass().add("filter-option-btn");
        btnTodos.setSelected(filtroActivo.isEmpty());
        btnTodos.setOnAction(ev -> {
            filtroActivo.clear();
            if (etiquetasManager != null) etiquetasManager.setFiltroActivo(filtroActivo);
            actualizarListaFiltrada(todosLosProyectos);
            contenido.setMouseTransparent(true);
            javafx.animation.FadeTransition hideFt = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), contenido);
            hideFt.setToValue(0);
            hideFt.setOnFinished(e -> filtroPopup.hide());
            hideFt.play();
        });
        contenido.getChildren().add(btnTodos);

        if (etiquetasManager != null && !etiquetasManager.getAll().isEmpty()) {
            contenido.getChildren().add(new Separator());
            Label lblCats = new Label("CATEGORÍAS");
            lblCats.setStyle("-fx-text-fill: #5a6090; -fx-font-size: 10px; -fx-font-weight: bold;");
            contenido.getChildren().add(lblCats);

            for (Etiqueta cat : etiquetasManager.getAll()) {
                HBox fila = new HBox(8);
                fila.setAlignment(Pos.CENTER_LEFT);
                Circle dot = new Circle(5);
                try { dot.setFill(Color.web(cat.getColor())); } catch (Exception ex) { dot.setFill(Color.GRAY); }

                CheckBox chk = new CheckBox(cat.getNombre());
                chk.setSelected(filtroActivo.contains(cat.getId()));
                chk.setMaxWidth(Double.MAX_VALUE);
                chk.setStyle("-fx-text-fill: #c8cde8;");
                HBox.setHgrow(chk, Priority.ALWAYS);

                chk.selectedProperty().addListener((obs, old, val) -> {
                    if (val) {
                        if (!filtroActivo.contains(cat.getId())) filtroActivo.add(cat.getId());
                    } else {
                        filtroActivo.remove(cat.getId());
                    }
                    if (etiquetasManager != null) etiquetasManager.setFiltroActivo(filtroActivo);
                    actualizarListaFiltrada(todosLosProyectos);
                    btnTodos.setSelected(filtroActivo.isEmpty());
                });

                Label btnDelete = new Label("✕");
                btnDelete.setStyle("-fx-text-fill: #E74C6C; -fx-cursor: hand; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 0 4px;");
                btnDelete.setOnMouseClicked(ev -> {
                    Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Eliminar Categoría");
                    alert.setHeaderText(null);
                    alert.setContentText("¿Seguro que quieres eliminar la categoría '" + cat.getNombre() + "'?");
                    if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        if (etiquetasManager.eliminar(cat.getId())) {
                            filtroActivo.remove(cat.getId());
                            etiquetasManager.setFiltroActivo(filtroActivo);
                            actualizarListaFiltrada(todosLosProyectos);
                            filtroPopup.hide();
                            mostrarFiltroPopup(anchor, todosLosProyectos);
                        }
                    }
                });

                fila.getChildren().addAll(dot, chk, btnDelete);
                contenido.getChildren().add(fila);
            }
        }

        contenido.getChildren().add(new Separator());
        Button btnNuevaCat = new Button("+ Nueva categoría");
        btnNuevaCat.getStyleClass().add("btn-dialog-action");
        btnNuevaCat.setMaxWidth(Double.MAX_VALUE);
        btnNuevaCat.setOnAction(ev -> {
            TextInputDialog dlg = new TextInputDialog();
            com.tpsstudio.util.AlertHelper.applyStyle(dlg);
            dlg.initOwner(anchor.getScene().getWindow());
            dlg.setTitle("Nueva Categoría");
            dlg.setHeaderText(null);
            dlg.setContentText("Nombre:");
            dlg.showAndWait().ifPresent(nombre -> {
                if (!nombre.isBlank() && etiquetasManager != null) {
                    etiquetasManager.crear(nombre, null);
                    filtroPopup.hide();
                    mostrarFiltroPopup(anchor, todosLosProyectos);
                }
            });
        });
        contenido.getChildren().add(btnNuevaCat);

        filtroPopup.getContent().add(contenido);
        javafx.geometry.Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        filtroPopup.show(anchor, b.getMaxX() + 10, b.getMinY() - 5);
    }

    private void actualizarListaFiltrada(ObservableList<Proyecto> todos) {
        if (proyectosFiltrados == null || todos == null) return;
        proyectosFiltrados.clear();
        String term = filtroTexto.toLowerCase().trim();

        for (Proyecto p : todos) {
            boolean coincideCategoria = filtroActivo.isEmpty();
            if (!coincideCategoria) {
                for (String id : filtroActivo) {
                    if (p.getEtiquetaIds().contains(id)) {
                        coincideCategoria = true;
                        break;
                    }
                }
            }

            if (coincideCategoria) {
                if (term.isEmpty()) {
                    proyectosFiltrados.add(p);
                } else {
                    String nombre = p.getNombre().toLowerCase();
                    String cliente = (p.getMetadata() != null && p.getMetadata().getClienteInfo() != null) 
                        ? (p.getMetadata().getClienteInfo().getNombreEmpresa() != null ? p.getMetadata().getClienteInfo().getNombreEmpresa().toLowerCase() : "") 
                        : "";
                    if (nombre.contains(term) || (cliente != null && cliente.contains(term))) {
                        proyectosFiltrados.add(p);
                    }
                }
            }
        }
        
        if (filtroInfoBox != null && lblFiltroActual != null && etiquetasManager != null) {
            if (filtroActivo.isEmpty()) {
                filtroInfoBox.setVisible(false);
                filtroInfoBox.setManaged(false);
            } else {
                StringBuilder sb = new StringBuilder();
                for (String id : filtroActivo) {
                    Etiqueta et = etiquetasManager.findById(id);
                    if (et != null) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(et.getNombre());
                    }
                }
                lblFiltroActual.setText(sb.toString().toUpperCase());
                filtroInfoBox.setVisible(true);
                filtroInfoBox.setManaged(true);
            }
        }
        actualizarVisibilidadOcultos(todos);
        actualizarIconoFiltro(btnFiltro);
    }

    private void mostrarProjectOptionsMenu(Proyecto item, Node anchor, ObservableList<Proyecto> projects) {
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);

        VBox content = new VBox(0);
        content.setStyle("-fx-background-color: #1a1b2e; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 15, 0, 0, 6); -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 10; -fx-padding: 5;");
        content.setMinWidth(140);
        
        String[] labels = {"Editar", "Duplicar", "Eliminar"};
        String[] icons = {"✎", "❐", "✖"};
        String[] colors = {"#c8cde8", "#c8cde8", "#ff6b6b"};
        
        for (int i = 0; i < labels.length; i++) {
            final String labelText = labels[i];
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 15, 8, 15));
            row.setStyle("-fx-cursor: hand; -fx-background-radius: 6;");
            
            Label lblIcon = new Label(icons[i]);
            lblIcon.setPrefWidth(22);
            lblIcon.setAlignment(Pos.CENTER);
            lblIcon.setStyle("-fx-text-fill: " + colors[i] + "; -fx-opacity: 0.9; -fx-font-size: 16px; -fx-font-weight: bold;");
            
            Label lblText = new Label(labelText);
            lblText.setStyle("-fx-text-fill: " + colors[i] + "; -fx-font-size: 13px; -fx-font-weight: 500;");
            
            row.getChildren().addAll(lblIcon, lblText);
            row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: rgba(108, 99, 255, 0.15); -fx-cursor: hand; -fx-background-radius: 6;"));
            row.setOnMouseExited(e -> row.setStyle("-fx-background-color: transparent; -fx-background-radius: 6;"));
            
            row.setOnMouseClicked(e -> {
                popup.hide();
                if (labelText.equals("Editar")) {
                    if (onEditProject != null) onEditProject.accept(item);
                } else if (labelText.equals("Duplicar")) {
                    duplicarProyectoUI(item, projects);
                } else if (labelText.equals("Eliminar")) {
                    eliminarProyectoUI(item, projects);
                }
            });
            
            content.getChildren().add(row);
            if (i == 1) {
                Separator sep = new Separator();
                sep.setPadding(new Insets(4, 0, 4, 0));
                sep.setOpacity(0.1);
                content.getChildren().add(sep);
            }
        }

        popup.getContent().add(content);
        content.setOpacity(0);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), content);
        ft.setToValue(1);
        ft.play();

        javafx.geometry.Point2D sidebarEdge = leftPanel.localToScreen(leftPanel.getWidth(), 0);
        javafx.geometry.Point2D anchorPos = anchor.localToScreen(0, -40);
        if (sidebarEdge != null && anchorPos != null) {
            popup.show(anchor.getScene().getWindow(), sidebarEdge.getX(), anchorPos.getY());
        }
    }

    private void duplicarProyectoUI(Proyecto item, ObservableList<Proyecto> projects) {
        if (projectManager == null) return;
        Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Duplicar Proyecto");
        alert.setHeaderText("¿Quieres crear una copia de este proyecto?");
        alert.setContentText("Se creará una copia de '" + item.getNombre() + "'.");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (projectManager.duplicarProyecto(item) != null) actualizarListaFiltrada(projects);
        }
    }

    private void eliminarProyectoUI(Proyecto item, ObservableList<Proyecto> projects) {
        if (projectManager == null) return;
        Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Eliminar del Historial");
        alert.setHeaderText("¿Quitar '" + item.getNombre() + "' de la lista?");
        alert.setContentText("Solo ocultará el proyecto del historial.");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            projectManager.eliminarProyecto(item);
            javafx.application.Platform.runLater(() -> actualizarListaFiltrada(projects));
        }
    }

    private void actualizarVisibilidadOcultos(ObservableList<Proyecto> todos) {
        if (panelOcultos == null || lblOcultosText == null) return;
        int ocultos = todos.size() - proyectosFiltrados.size();
        if (ocultos > 0) {
            lblOcultosText.setText(ocultos + (ocultos == 1 ? " proyecto oculto" : " proyectos ocultos"));
            panelOcultos.setStyle("-fx-background-color: rgba(108, 99, 255, 0.08); -fx-background-radius: 4; -fx-padding: 6 0;");
            panelOcultos.setVisible(true);
            panelOcultos.setManaged(true);
            iniciarRecordatorioSutil();
        } else {
            if (recordatorioTimer != null) recordatorioTimer.stop();
            panelOcultos.setVisible(false);
            panelOcultos.setManaged(false);
        }
    }

    private void iniciarRecordatorioSutil() {
        if (recordatorioTimer != null) recordatorioTimer.stop();
        recordatorioTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.minutes(3), ev -> {
            if (panelOcultos != null && panelOcultos.isVisible()) {
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), panelOcultos);
                ft.setFromValue(1.0);
                ft.setToValue(0.4);
                ft.setCycleCount(4);
                ft.setAutoReverse(true);
                ft.play();
            }
        }));
        recordatorioTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        recordatorioTimer.play();
    }
}
