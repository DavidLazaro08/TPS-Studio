package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.project.Etiqueta;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.service.EtiquetasManager;
import com.tpsstudio.service.ProjectManager;
import com.tpsstudio.util.AlertHelper;
import com.tpsstudio.util.AnimationHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Orquestador de la vista de Producción (panel lateral de lista de proyectos).
 *
 * <p>Delega la construcción de celdas a {@link ProjectListCellFactory}
 * y la lógica del popup de filtro por categorías a {@link FiltroPopupManager}.
 * Su responsabilidad se limita a: ensamblar el panel, gestionar el estado de
 * filtrado, y exponer las acciones CRUD de proyectos (duplicar, eliminar).</p>
 *
 * <p>La interfaz pública ({@code buildProductionModePanels}, {@code setFiltroActivo},
 * {@code setEtiquetasManager}, {@code setProjectManager}) no ha cambiado
 * respecto a la versión anterior: ningún caller externo requiere modificaciones.</p>
 */
public class ProductionViewManager {

    private final VBox leftPanel;
    private EtiquetasManager etiquetasManager;
    private ProjectManager projectManager;

    // --- Estado de filtrado (compartido con FiltroPopupManager por referencia) ---
    private ObservableList<Proyecto> proyectosFiltrados;
    private String filtroTexto = "";
    private List<String> filtroActivo = new ArrayList<>();

    // --- Referencias UI necesarias para actualizaciones posteriores ---
    private Button btnFiltro;
    private FiltroPopupManager filtroPopupManager;
    private HBox panelOcultos;
    private Label lblOcultosText;
    private HBox filtroInfoBox;
    private Label lblFiltroActual;
    private javafx.animation.Timeline recordatorioTimer;

    // --- Callbacks hacia MainViewController ---
    private final Consumer<Proyecto> onProjectSelected;
    private final Consumer<Proyecto> onEditProject;
    private final Runnable onNewCR80;

    public ProductionViewManager(VBox leftPanel,
                                 EtiquetasManager etiquetasManager,
                                 ProjectManager projectManager,
                                 Consumer<Proyecto> onProjectSelected,
                                 Consumer<Proyecto> onEditProject,
                                 Runnable onNewCR80) {
        this.leftPanel          = leftPanel;
        this.etiquetasManager   = etiquetasManager;
        this.projectManager     = projectManager;
        this.onProjectSelected  = onProjectSelected;
        this.onEditProject      = onEditProject;
        this.onNewCR80          = onNewCR80;
    }

    public void setEtiquetasManager(EtiquetasManager etiquetasManager) {
        this.etiquetasManager = etiquetasManager;
    }

    public void setProjectManager(ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    /** Permite sincronizar el filtro desde fuera (ej: al inyectar EtiquetasManager). */
    public void setFiltroActivo(List<String> ids) {
        this.filtroActivo = new ArrayList<>(ids);
    }

    // =========================================================
    // Punto de entrada público
    // =========================================================

    public void buildProductionModePanels(ObservableList<Proyecto> projects, Proyecto currentProject) {
        leftPanel.getChildren().add(buildProjectListPanel(projects, currentProject));
    }

    // =========================================================
    // Construcción del panel
    // =========================================================

    private VBox buildProjectListPanel(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = new VBox(12);
        projectPanel.setPadding(new Insets(14, 12, 14, 12));
        VBox.setVgrow(projectPanel, Priority.ALWAYS);

        // --- Header con título y botón de filtro ---
        Label lblTrabajos = new Label("Gestión de Trabajos");
        lblTrabajos.getStyleClass().add("panel-title");
        Label lblSubtitulo = new Label("Administración y exportación");
        lblSubtitulo.getStyleClass().add("panel-placeholder");
        VBox titulos = new VBox(2, lblTrabajos, lblSubtitulo);
        HBox.setHgrow(titulos, Priority.ALWAYS);

        filtroPopupManager = new FiltroPopupManager(
                etiquetasManager, filtroActivo, () -> actualizarListaFiltrada(projects));
        btnFiltro = filtroPopupManager.crearBoton(projects);
        HBox header = new HBox(titulos, btnFiltro);
        header.setAlignment(Pos.CENTER_LEFT);

        // --- Inicializar lista filtrada ---
        proyectosFiltrados = FXCollections.observableArrayList();
        actualizarListaFiltrada(projects);

        // --- Campo de búsqueda por texto ---
        TextField txtBusqueda = new TextField();
        txtBusqueda.setPromptText("Buscar por nombre, cliente...");
        txtBusqueda.getStyleClass().add("search-field");
        txtBusqueda.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: white; " +
                "-fx-prompt-text-fill: #5a6090; -fx-background-radius: 12; " +
                "-fx-padding: 8 12; -fx-font-size: 11.5px;");
        txtBusqueda.textProperty().addListener((obs, old, val) -> {
            filtroTexto = val;
            actualizarListaFiltrada(projects);
        });
        txtBusqueda.setText(filtroTexto);

        // --- ListView de proyectos ---
        ListView<Proyecto> listProyectos = new ListView<>(proyectosFiltrados);
        listProyectos.getStyleClass().add("project-list");
        VBox.setVgrow(listProyectos, Priority.ALWAYS);
        listProyectos.maxHeightProperty().bind(leftPanel.heightProperty().subtract(200));

        var cellFactory = new ProjectListCellFactory(
                (item, anchor) -> mostrarProjectOptionsMenu(item, anchor, projects));
        listProyectos.setCellFactory(cellFactory.build());

        // Pre-seleccionar el proyecto activo
        if (currentProject != null) {
            proyectosFiltrados.stream()
                    .filter(p -> p.getId() == currentProject.getId())
                    .findFirst()
                    .ifPresent(eq -> javafx.application.Platform.runLater(() -> {
                        listProyectos.getSelectionModel().select(eq);
                        listProyectos.scrollTo(eq);
                    }));
        }

        listProyectos.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && onProjectSelected != null) onProjectSelected.accept(newVal);
        });
        listProyectos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Proyecto sel = listProyectos.getSelectionModel().getSelectedItem();
                if (sel != null && onEditProject != null) onEditProject.accept(sel);
            }
        });

        // --- Botón nuevo proyecto ---
        Button btnNuevoCR80 = new Button("+ NUEVO PROYECTO CR80");
        btnNuevoCR80.getStyleClass().add("new-project-btn");
        btnNuevoCR80.setStyle("-fx-font-size: 11.5px;");
        btnNuevoCR80.setMaxWidth(Double.MAX_VALUE);
        btnNuevoCR80.setOnAction(e -> { if (onNewCR80 != null) onNewCR80.run(); });

        // --- Paneles de estado (ocultos y filtro activo) ---
        panelOcultos  = buildPanelOcultos(projects);
        filtroInfoBox = buildFiltroInfoBox();
        actualizarVisibilidadOcultos(projects);

        projectPanel.getChildren().addAll(
                header, txtBusqueda, filtroInfoBox, listProyectos, panelOcultos, btnNuevoCR80);
        actualizarListaFiltrada(projects);
        return projectPanel;
    }

    // =========================================================
    // Menú de opciones de proyecto (Editar / Duplicar / Eliminar)
    // =========================================================

    private void mostrarProjectOptionsMenu(Proyecto item, Node anchor, ObservableList<Proyecto> projects) {
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);

        String[] labels = {"Editar", "Duplicar", "Eliminar"};
        String[] icons  = {"✎", "❐", "✖"};
        String[] colors = {"#c8cde8", "#c8cde8", "#ff6b6b"};

        VBox content = new VBox(0);
        content.setStyle("-fx-background-color: #1a1b2e; -fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 15, 0, 0, 6); " +
                "-fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 10; -fx-padding: 5;");
        content.setMinWidth(140);

        for (int i = 0; i < labels.length; i++) {
            final String labelText = labels[i];
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 15, 8, 15));
            row.setStyle("-fx-cursor: hand; -fx-background-radius: 6;");

            Label lblIcon = new Label(icons[i]);
            lblIcon.setPrefWidth(22);
            lblIcon.setAlignment(Pos.CENTER);
            lblIcon.setStyle("-fx-text-fill: " + colors[i] + "; -fx-opacity: 0.9; " +
                    "-fx-font-size: 16px; -fx-font-weight: bold;");

            Label lblText = new Label(labelText);
            lblText.setStyle("-fx-text-fill: " + colors[i] + "; -fx-font-size: 13px; -fx-font-weight: 500;");

            row.getChildren().addAll(lblIcon, lblText);
            row.setOnMouseEntered(e -> row.setStyle(
                    "-fx-background-color: rgba(108, 99, 255, 0.15); -fx-cursor: hand; -fx-background-radius: 6;"));
            row.setOnMouseExited(e -> row.setStyle(
                    "-fx-background-color: transparent; -fx-background-radius: 6;"));
            row.setOnMouseClicked(e -> {
                popup.hide();
                switch (labelText) {
                    case "Editar"    -> { if (onEditProject != null) onEditProject.accept(item); }
                    case "Duplicar"  -> duplicarProyectoUI(item, projects);
                    case "Eliminar"  -> eliminarProyectoUI(item, projects);
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
        var ft = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), content);
        ft.setToValue(1);
        ft.play();

        javafx.geometry.Point2D sidebarEdge = leftPanel.localToScreen(leftPanel.getWidth(), 0);
        javafx.geometry.Point2D anchorPos   = anchor.localToScreen(0, -40);
        if (sidebarEdge != null && anchorPos != null) {
            popup.show(anchor.getScene().getWindow(), sidebarEdge.getX(), anchorPos.getY());
        }
    }

    // =========================================================
    // Acciones CRUD de proyectos
    // =========================================================

    private void duplicarProyectoUI(Proyecto item, ObservableList<Proyecto> projects) {
        if (projectManager == null) return;
        Alert alert = AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Duplicar Proyecto");
        alert.setHeaderText("¿Quieres crear una copia de este proyecto?");
        alert.setContentText("Se creará una copia de '" + item.getNombre() + "'.");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (projectManager.duplicarProyecto(item) != null) actualizarListaFiltrada(projects);
        }
    }

    private void eliminarProyectoUI(Proyecto item, ObservableList<Proyecto> projects) {
        if (projectManager == null) return;
        Alert alert = AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Eliminar del Historial");
        alert.setHeaderText("¿Quitar '" + item.getNombre() + "' de la lista?");
        alert.setContentText("Solo ocultará el proyecto del historial.");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            projectManager.eliminarProyecto(item);
            javafx.application.Platform.runLater(() -> actualizarListaFiltrada(projects));
        }
    }

    // =========================================================
    // Filtrado y estado de la lista
    // =========================================================

    private void actualizarListaFiltrada(ObservableList<Proyecto> todos) {
        if (proyectosFiltrados == null || todos == null) return;
        proyectosFiltrados.clear();
        String term = filtroTexto.toLowerCase().trim();

        for (Proyecto p : todos) {
            boolean coincideCategoria = filtroActivo.isEmpty() ||
                    filtroActivo.stream().anyMatch(id -> p.getEtiquetaIds().contains(id));

            if (!coincideCategoria) continue;

            if (term.isEmpty()) {
                proyectosFiltrados.add(p);
            } else {
                String nombre = p.getNombre().toLowerCase();
                String cliente = (p.getMetadata() != null && p.getMetadata().getClienteInfo() != null
                        && p.getMetadata().getClienteInfo().getNombreEmpresa() != null)
                        ? p.getMetadata().getClienteInfo().getNombreEmpresa().toLowerCase() : "";
                if (nombre.contains(term) || cliente.contains(term)) proyectosFiltrados.add(p);
            }
        }

        actualizarFiltroInfoBox();
        actualizarVisibilidadOcultos(todos);
        if (filtroPopupManager != null) filtroPopupManager.actualizarIcono(btnFiltro);
    }

    private void actualizarFiltroInfoBox() {
        if (filtroInfoBox == null || lblFiltroActual == null || etiquetasManager == null) return;
        if (filtroActivo.isEmpty()) {
            filtroInfoBox.setVisible(false);
            filtroInfoBox.setManaged(false);
            return;
        }
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

    private void actualizarVisibilidadOcultos(ObservableList<Proyecto> todos) {
        if (panelOcultos == null || lblOcultosText == null) return;
        int ocultos = todos.size() - proyectosFiltrados.size();
        if (ocultos > 0) {
            lblOcultosText.setText(ocultos + (ocultos == 1 ? " proyecto oculto" : " proyectos ocultos"));
            panelOcultos.setStyle("-fx-background-color: rgba(108, 99, 255, 0.08); " +
                    "-fx-background-radius: 4; -fx-padding: 6 0;");
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
        recordatorioTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(3), ev -> {
                    if (panelOcultos != null && panelOcultos.isVisible()) {
                        var ft = new javafx.animation.FadeTransition(
                                javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM), panelOcultos);
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

    // =========================================================
    // Construcción de paneles de estado auxiliares
    // =========================================================

    private HBox buildPanelOcultos(ObservableList<Proyecto> projects) {
        HBox panel = new HBox(5);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(4, 0, 8, 0));
        panel.setVisible(false);
        panel.setManaged(false);

        lblOcultosText = new Label();
        lblOcultosText.setStyle("-fx-text-fill: #a0a5cc; -fx-font-size: 11px;");

        Label sep = new Label("-");
        sep.setStyle("-fx-text-fill: #5a6090; -fx-font-size: 11px;");

        Hyperlink linkVerTodos = new Hyperlink("Ver todos");
        linkVerTodos.setStyle("-fx-text-fill: #6c63ff; -fx-font-size: 11px; -fx-padding: 0; " +
                "-fx-border-color: transparent; -fx-underline: false;");
        linkVerTodos.setOnAction(e -> {
            filtroActivo.clear();
            if (etiquetasManager != null) etiquetasManager.setFiltroActivo(filtroActivo);
            actualizarListaFiltrada(projects);
            if (filtroPopupManager != null) filtroPopupManager.actualizarIcono(btnFiltro);
        });

        panel.getChildren().addAll(lblOcultosText, sep, linkVerTodos);
        return panel;
    }

    private HBox buildFiltroInfoBox() {
        HBox box = new HBox(8);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(6, 10, 6, 10));
        box.setVisible(false);
        box.setManaged(false);
        box.setStyle("-fx-background-color: rgba(108, 99, 255, 0.05); " +
                "-fx-border-color: rgba(108, 99, 255, 0.15); -fx-border-width: 0 0 1 0;");

        lblFiltroActual = new Label();
        lblFiltroActual.setStyle("-fx-text-fill: #6c63ff; -fx-font-weight: bold; -fx-font-size: 11px;");
        lblFiltroActual.setWrapText(true);
        HBox.setHgrow(lblFiltroActual, Priority.ALWAYS);

        Label lblIcon = new Label("📂");
        lblIcon.setStyle("-fx-font-size: 12px; -fx-padding: 2 0 0 0;");

        box.getChildren().addAll(lblIcon, lblFiltroActual);
        return box;
    }
}
