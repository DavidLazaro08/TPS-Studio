package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.project.Etiqueta;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.service.EtiquetasManager;
import com.tpsstudio.util.AlertHelper;
import com.tpsstudio.util.AnimationHelper;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;

/**
 * Gestiona el botón de filtro por categorías y su popup desplegable.
 */
public class FiltroPopupManager {

    private final EtiquetasManager etiquetasManager;
    private final List<String> filtroActivo;
    private final Runnable onFiltroChanged;

    private javafx.stage.Popup filtroPopup;

    public FiltroPopupManager(EtiquetasManager etiquetasManager,
                              List<String> filtroActivo,
                              Runnable onFiltroChanged) {
        this.etiquetasManager = etiquetasManager;
        this.filtroActivo = filtroActivo;
        this.onFiltroChanged = onFiltroChanged;
    }

    // =====================================================
    // Botón de filtro
    // =====================================================

    public Button crearBoton(ObservableList<Proyecto> todosLosProyectos) {
        Button btn = new Button();
        actualizarIcono(btn);

        btn.getStyleClass().add("filter-btn");
        btn.setTooltip(new Tooltip("Filtrar por categoría"));
        btn.setOnAction(e -> {
            if (filtroPopup != null && filtroPopup.isShowing()) {
                ocultarConAnimacion();
            } else {
                mostrarPopup(btn, todosLosProyectos);
            }
        });

        return btn;
    }

    public void actualizarIcono(Button btn) {
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

    // =====================================================
    // Popup
    // =====================================================

    private void mostrarPopup(Button anchor, ObservableList<Proyecto> todos) {
        if (filtroPopup != null && filtroPopup.isShowing()) filtroPopup.hide();

        filtroPopup = new javafx.stage.Popup();
        filtroPopup.setAutoHide(true);

        VBox contenido = construirContenido(anchor, todos);
        contenido.setOpacity(0);
        filtroPopup.getContent().add(contenido);

        var ft = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(AnimationHelper.DURATION_MEDIUM),
                contenido
        );
        ft.setToValue(1);
        ft.play();

        javafx.geometry.Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        filtroPopup.show(anchor, b.getMaxX() + 10, b.getMinY() - 5);
    }

    private void ocultarConAnimacion() {
        if (filtroPopup == null || filtroPopup.getContent().isEmpty()) return;

        Node content = filtroPopup.getContent().get(0);
        content.setMouseTransparent(true);

        var ft = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(AnimationHelper.DURATION_FAST),
                content
        );
        ft.setToValue(0);
        ft.setOnFinished(ev -> filtroPopup.hide());
        ft.play();
    }

    private VBox construirContenido(Button anchor, ObservableList<Proyecto> todos) {
        VBox contenido = new VBox(8);
        contenido.getStyleClass().add("filter-popup");
        contenido.setPadding(new Insets(12));
        contenido.setStyle("-fx-background-color: #1a1b2e; -fx-background-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 12, 0, 0, 4);");
        contenido.setPrefWidth(210);

        ToggleButton btnTodos = new ToggleButton("★ Todos los proyectos");
        btnTodos.setMaxWidth(Double.MAX_VALUE);
        btnTodos.getStyleClass().add("filter-option-btn");
        btnTodos.setSelected(filtroActivo.isEmpty());
        btnTodos.setOnAction(ev -> {
            filtroActivo.clear();

            if (etiquetasManager != null) {
                etiquetasManager.setFiltroActivo(filtroActivo);
            }

            onFiltroChanged.run();
            ocultarConAnimacion();
        });
        contenido.getChildren().add(btnTodos);

        if (etiquetasManager != null && !etiquetasManager.getAll().isEmpty()) {
            contenido.getChildren().add(new Separator());

            Label lblCats = new Label("CATEGORÍAS");
            lblCats.setStyle("-fx-text-fill: #5a6090; -fx-font-size: 10px; -fx-font-weight: bold;");
            contenido.getChildren().add(lblCats);

            for (Etiqueta cat : etiquetasManager.getAll()) {
                contenido.getChildren().add(construirFilaCategoria(cat, btnTodos, anchor, todos));
            }
        }

        contenido.getChildren().add(new Separator());

        Button btnNuevaCat = new Button("+ Nueva categoría");
        btnNuevaCat.getStyleClass().add("btn-dialog-action");
        btnNuevaCat.setMaxWidth(Double.MAX_VALUE);
        btnNuevaCat.setOnAction(ev -> {
            TextInputDialog dlg = new TextInputDialog();
            AlertHelper.applyStyle(dlg);
            dlg.initOwner(anchor.getScene().getWindow());
            dlg.setTitle("Nueva Categoría");
            dlg.setHeaderText(null);
            dlg.setContentText("Nombre:");

            dlg.showAndWait().ifPresent(nombre -> {
                if (!nombre.isBlank() && etiquetasManager != null) {
                    etiquetasManager.crear(nombre, null);
                    filtroPopup.hide();
                    mostrarPopup(anchor, todos);
                }
            });
        });
        contenido.getChildren().add(btnNuevaCat);

        return contenido;
    }

    private HBox construirFilaCategoria(Etiqueta cat,
                                        ToggleButton btnTodos,
                                        Button anchor,
                                        ObservableList<Proyecto> todos) {
        Circle dot = new Circle(5);

        try {
            dot.setFill(Color.web(cat.getColor()));
        } catch (Exception ex) {
            dot.setFill(Color.GRAY);
        }

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

            if (etiquetasManager != null) {
                etiquetasManager.setFiltroActivo(filtroActivo);
            }

            btnTodos.setSelected(filtroActivo.isEmpty());
            onFiltroChanged.run();
        });

        Label btnDelete = new Label("✕");
        btnDelete.setStyle("-fx-text-fill: #E74C6C; -fx-cursor: hand; -fx-font-size: 11px; " +
                "-fx-font-weight: bold; -fx-padding: 0 4px;");
        btnDelete.setOnMouseClicked(ev -> {
            Alert alert = AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Eliminar Categoría");
            alert.setHeaderText(null);
            alert.setContentText("¿Seguro que quieres eliminar la categoría '" + cat.getNombre() + "'?");

            if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                if (etiquetasManager.eliminar(cat.getId())) {
                    filtroActivo.remove(cat.getId());
                    etiquetasManager.setFiltroActivo(filtroActivo);
                    onFiltroChanged.run();
                    filtroPopup.hide();
                    mostrarPopup(anchor, todos);
                }
            }
        });

        HBox fila = new HBox(8, dot, chk, btnDelete);
        fila.setAlignment(Pos.CENTER_LEFT);

        return fila;
    }
}