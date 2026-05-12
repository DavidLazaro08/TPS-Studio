package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.util.AnimationHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;

import java.util.function.BiConsumer;

/**
 * Fábrica de celdas para el ListView de proyectos en la vista de Producción.
 *
 * <p>Responsabilidad única: construir y configurar el layout visual de cada celda,
 * incluyendo animaciones de hover, selección y la barra activa lateral pulsante.</p>
 *
 * @see ProductionViewManager
 */
public class ProjectListCellFactory {

    private final BiConsumer<Proyecto, Node> onShowOptionsMenu;

    /**
     * @param onShowOptionsMenu callback invocado al pulsar el botón ⋯;
     *                          recibe el proyecto y el nodo ancla para posicionar el popup.
     */
    public ProjectListCellFactory(BiConsumer<Proyecto, Node> onShowOptionsMenu) {
        this.onShowOptionsMenu = onShowOptionsMenu;
    }

    /**
     * Devuelve la {@code Callback} lista para {@code ListView.setCellFactory()}.
     */
    public Callback<ListView<Proyecto>, ListCell<Proyecto>> build() {
        return lv -> new ListCell<>() {

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
                    setGraphic(buildCellContent(item));
                    setText(null);
                    setPadding(new Insets(6, 8, 0, 8));
                }
            }

            private VBox buildCellContent(Proyecto item) {
                // --- Barra lateral activa ---
                Region activeBar = new Region();
                activeBar.setPrefWidth(4);
                activeBar.setMinWidth(4);
                activeBar.setMaxWidth(4);
                activeBar.setMaxHeight(Double.MAX_VALUE);
                activeBar.getStyleClass().add("project-active-bar");
                activeBar.setOpacity(0.0);
                activeBar.setVisible(false);

                // --- Texto: nombre + cliente ---
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
                VBox textContainer = new VBox(2, lblName, lblEmpresa);
                textContainer.setAlignment(Pos.CENTER_LEFT);

                // --- Badge de tipo y botón de opciones ---
                Label lblBadge = new Label(item.getTipo());
                lblBadge.getStyleClass().add("project-badge");

                Label btnOptions = new Label("⋯");
                btnOptions.setStyle("-fx-text-fill: #a0a5cc; -fx-cursor: hand; -fx-font-size: 26px; -fx-font-weight: bold; -fx-padding: 0;");
                btnOptions.setVisible(false);
                btnOptions.setPickOnBounds(true);
                btnOptions.setOnMousePressed(e -> {
                    if (onShowOptionsMenu != null) onShowOptionsMenu.accept(item, btnOptions);
                    e.consume();
                });

                VBox rightActionBox = new VBox(-6, btnOptions, lblBadge);
                rightActionBox.setAlignment(Pos.CENTER);
                VBox.setMargin(btnOptions, new Insets(0, 0, 4, 0));

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox textAndBadge = new HBox(10, textContainer, spacer, rightActionBox);
                textAndBadge.setAlignment(Pos.CENTER_LEFT);
                textAndBadge.setPadding(new Insets(0, 12, 0, 12));
                HBox.setHgrow(textAndBadge, Priority.ALWAYS);

                HBox contentRow = new HBox(0, activeBar, textAndBadge);
                contentRow.setAlignment(Pos.CENTER_LEFT);
                contentRow.setMaxWidth(Double.MAX_VALUE);
                contentRow.setMaxHeight(Double.MAX_VALUE);

                // --- Overlays de hover y selección ---
                Region hoverOverlay    = buildOverlay("project-hover-overlay");
                Region selectedOverlay = buildOverlay("project-selected-overlay");

                // --- Card con clip redondeado ---
                StackPane card = new StackPane(hoverOverlay, selectedOverlay, contentRow);
                card.getStyleClass().add("project-card");
                card.setPrefHeight(62);
                card.setMinHeight(62);

                Rectangle clip = new Rectangle();
                clip.setArcWidth(16);
                clip.setArcHeight(16);
                card.layoutBoundsProperty().addListener((obs, old, b) -> {
                    clip.setWidth(b.getWidth());
                    clip.setHeight(b.getHeight());
                });
                card.setClip(clip);

                // --- Separador inferior ---
                Region separator = new Region();
                separator.getStyleClass().add("project-separator");
                separator.setPrefHeight(1);
                separator.setMaxWidth(Double.MAX_VALUE);
                VBox.setMargin(separator, new Insets(0, 8, 0, 8));

                // --- Animaciones ---
                var hoverIn  = fadeTransition(AnimationHelper.DURATION_FAST, hoverOverlay, 0.6);
                var hoverOut = fadeTransition(200, hoverOverlay, 0.0);

                if (isSelected()) {
                    fadeTransition(AnimationHelper.DURATION_SLOW, selectedOverlay, 1.0).play();
                    activeBar.setVisible(true);
                    if (pulse == null) {
                        pulse = buildPulseTimeline(activeBar);
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
                card.setOnMouseExited(e -> {
                    if (!isSelected()) hoverOut.playFromStart();
                    btnOptions.setVisible(false);
                });

                return new VBox(0, card, separator);
            }
        };
    }

    // =========================================================
    // Helpers privados de construcción
    // =========================================================

    private Region buildOverlay(String styleClass) {
        Region overlay = new Region();
        overlay.getStyleClass().add(styleClass);
        overlay.setOpacity(0.0);
        overlay.setMouseTransparent(true);
        overlay.setMaxWidth(Double.MAX_VALUE);
        overlay.setMaxHeight(Double.MAX_VALUE);
        return overlay;
    }

    private javafx.animation.FadeTransition fadeTransition(double millis, Node node, double toValue) {
        var ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(millis), node);
        ft.setToValue(toValue);
        return ft;
    }

    private javafx.animation.Timeline buildPulseTimeline(Region bar) {
        var t = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                new javafx.animation.KeyValue(bar.opacityProperty(), 0.35, javafx.animation.Interpolator.EASE_BOTH)),
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1.2),
                new javafx.animation.KeyValue(bar.opacityProperty(), 1.0, javafx.animation.Interpolator.EASE_BOTH)),
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2.4),
                new javafx.animation.KeyValue(bar.opacityProperty(), 0.35, javafx.animation.Interpolator.EASE_BOTH))
        );
        t.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        return t;
    }
}
