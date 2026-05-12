package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.project.Proyecto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * Gestiona el panel de capas del modo Diseño.
 *
 * Permite seleccionar elementos, cambiar su orden, bloquearlos y eliminarlos.
 */
public class LayersPanelManager {

    private final VBox leftPanel;
    private ListView<Elemento> layersListView;
    private boolean isUpdatingSelection = false;

    // Callbacks hacia el controlador principal.
    private Runnable onCanvasRedraw;
    private Consumer<Elemento> onElementSelected;
    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<Elemento> onToggleLock;

    public LayersPanelManager(VBox leftPanel,
                              Runnable onCanvasRedraw,
                              Consumer<Elemento> onElementSelected,
                              Consumer<ImagenFondoElemento> onEditExternal,
                              Consumer<Elemento> onToggleLock) {
        this.leftPanel = leftPanel;
        this.onCanvasRedraw = onCanvasRedraw;
        this.onElementSelected = onElementSelected;
        this.onEditExternal = onEditExternal;
        this.onToggleLock = onToggleLock;
    }

    public ListView<Elemento> getLayersListView() {
        return layersListView;
    }

    public void rebuildLayersPanel(Proyecto proyecto, Elemento selectedElement, VBox layersPanelContainer) {
        if (layersPanelContainer != null && leftPanel != null) {
            VBox newLayers = buildLayersPanel(proyecto, selectedElement);
            int index = leftPanel.getChildren().indexOf(layersPanelContainer);
            if (index != -1) {
                leftPanel.getChildren().set(index, newLayers);
            }
        }
    }

    public VBox buildLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12));
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label lblCapas = new Label("Capas");
        lblCapas.getStyleClass().add("panel-title");

        this.layersListView = new ListView<>();
        layersListView.getStyleClass().add("layers-list");
        VBox.setVgrow(layersListView, Priority.ALWAYS);

        final Proyecto currentProj = proyecto;

        if (currentProj != null) {
            layersListView.setItems(getCapasDeProyecto(proyecto));

            if (selectedElement != null) {
                isUpdatingSelection = true;
                try {
                    layersListView.getSelectionModel().select(selectedElement);
                    layersListView.scrollTo(selectedElement);
                } finally {
                    isUpdatingSelection = false;
                }
            }

            layersListView.setCellFactory(lv -> new ListCell<Elemento>() {
                private javafx.animation.Timeline pulse;

                private final Region activeBar = new Region();
                private final Label lblIcon = new Label();
                private final Label lblNombre = new Label();
                private final HBox actions = new HBox(4);

                private final Region hoverOverlay = new Region();
                private final Region selectedOverlay = new Region();

                private final HBox row = new HBox();
                private final StackPane card = new StackPane();
                private final Region sep = new Region();
                private final VBox cellLayout = new VBox();

                private javafx.animation.FadeTransition hIn;
                private javafx.animation.FadeTransition hOut;

                private final ContextMenu cm = new ContextMenu();

                {
                    activeBar.setPrefWidth(3);
                    activeBar.setMinWidth(3);
                    activeBar.setMaxWidth(3);
                    activeBar.setMaxHeight(Double.MAX_VALUE);
                    activeBar.getStyleClass().add("layer-active-bar");

                    lblIcon.getStyleClass().add("layer-item-icon");
                    lblIcon.setMinWidth(28);
                    lblIcon.setAlignment(Pos.CENTER);

                    lblNombre.getStyleClass().add("layer-item-text");
                    lblNombre.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(lblNombre, Priority.ALWAYS);

                    actions.setAlignment(Pos.CENTER_RIGHT);
                    actions.setVisible(false);
                    actions.managedProperty().bind(actions.visibleProperty());

                    HBox content = new HBox(8, lblIcon, lblNombre, actions);
                    content.setAlignment(Pos.CENTER_LEFT);
                    content.setPadding(new Insets(0, 10, 0, 10));
                    HBox.setHgrow(content, Priority.ALWAYS);

                    row.getChildren().addAll(activeBar, content);
                    row.setAlignment(Pos.CENTER_LEFT);

                    hoverOverlay.getStyleClass().add("layer-hover-overlay");
                    hoverOverlay.setOpacity(0.0);
                    hoverOverlay.setMouseTransparent(true);

                    selectedOverlay.getStyleClass().add("layer-selected-overlay");
                    selectedOverlay.setOpacity(0.0);
                    selectedOverlay.setMouseTransparent(true);

                    card.getChildren().addAll(hoverOverlay, selectedOverlay, row);
                    card.getStyleClass().add("layer-item-card");
                    card.setPrefHeight(42);
                    card.setMinHeight(42);

                    sep.getStyleClass().add("layer-item-separator");
                    sep.setPrefHeight(1);
                    VBox.setMargin(sep, new Insets(0, 12, 0, 44));

                    cellLayout.getChildren().addAll(card, sep);

                    hIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(150), hoverOverlay);
                    hIn.setToValue(0.6);

                    hOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(150), hoverOverlay);
                    hOut.setToValue(0.0);

                    selectedProperty().addListener((obs, old, isSelected) -> {
                        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), isSelected);

                        if (isSelected) {
                            selectedOverlay.setOpacity(1.0);
                            activeBar.setVisible(true);
                            activeBar.setOpacity(1.0);
                            actions.setVisible(true);
                            lblNombre.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
                            lblIcon.setStyle("-fx-text-fill: white;");

                            if (pulse == null) {
                                pulse = new javafx.animation.Timeline(
                                        new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                                                new javafx.animation.KeyValue(activeBar.opacityProperty(), 0.3, javafx.animation.Interpolator.EASE_BOTH)),
                                        new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1.2),
                                                new javafx.animation.KeyValue(activeBar.opacityProperty(), 1.0, javafx.animation.Interpolator.EASE_BOTH)),
                                        new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2.4),
                                                new javafx.animation.KeyValue(activeBar.opacityProperty(), 0.3, javafx.animation.Interpolator.EASE_BOTH))
                                );
                                pulse.setCycleCount(javafx.animation.Timeline.INDEFINITE);
                                pulse.play();
                            }

                        } else {
                            selectedOverlay.setOpacity(0.0);
                            activeBar.setVisible(false);
                            actions.setVisible(false);
                            lblNombre.setStyle("-fx-font-weight: normal; -fx-text-fill: #a0a5cc;");
                            lblIcon.setStyle("-fx-text-fill: #a0a5cc;");

                            if (pulse != null) {
                                pulse.stop();
                                pulse = null;
                            }
                        }
                    });

                    card.setOnMouseEntered(e -> {
                        hIn.playFromStart();
                        if (!isSelected()) actions.setVisible(true);
                    });

                    card.setOnMouseExited(e -> {
                        if (!isSelected()) {
                            hOut.playFromStart();
                            actions.setVisible(false);
                        }
                    });
                }

                @Override
                protected void updateItem(Elemento item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);

                        if (pulse != null) {
                            pulse.stop();
                            pulse = null;
                        }

                        selectedOverlay.setOpacity(0.0);
                        activeBar.setVisible(false);
                        actions.setVisible(false);
                        return;
                    }

                    hoverOverlay.setOpacity(0.0);

                    lblIcon.setText(item instanceof ImagenFondoElemento ? "⬚"
                            : (item instanceof TextoElemento ? "T"
                            : (item instanceof ImagenElemento ? "▣"
                            : (item instanceof ElementoCodigo ? "⦀" : "⬒"))));

                    lblNombre.setText(item.toString());
                    actions.getChildren().clear();

                    if (item.isLocked()) {
                        Label lock = new Label("🔒");
                        lock.setStyle("-fx-font-size: 10px;");
                        actions.getChildren().add(lock);
                    }

                    if (!(item instanceof ImagenFondoElemento) && !item.isLocked()) {
                        Button btnUp = new Button("▲");
                        btnUp.getStyleClass().add("layer-action-btn");
                        btnUp.setOnAction(e -> {
                            int idx = currentProj.getElementosActuales().indexOf(item);
                            if (idx > 0) {
                                java.util.Collections.swap(currentProj.getElementosActuales(), idx, idx - 1);
                                if (onCanvasRedraw != null) onCanvasRedraw.run();
                                rebuildLayersPanel(currentProj, item, panel);
                            }
                        });

                        Button btnDown = new Button("▼");
                        btnDown.getStyleClass().add("layer-action-btn");
                        btnDown.setOnAction(e -> {
                            int idx = currentProj.getElementosActuales().indexOf(item);
                            if (idx < currentProj.getElementosActuales().size() - 1) {
                                java.util.Collections.swap(currentProj.getElementosActuales(), idx, idx + 1);
                                if (onCanvasRedraw != null) onCanvasRedraw.run();
                                rebuildLayersPanel(currentProj, item, panel);
                            }
                        });

                        Button btnDel = new Button("✕");
                        btnDel.getStyleClass().add("layer-action-btn-del");
                        btnDel.setOnAction(e -> {
                            currentProj.getElementosActuales().remove(item);
                            if (onCanvasRedraw != null) onCanvasRedraw.run();
                            rebuildLayersPanel(currentProj, null, panel);
                        });

                        actions.getChildren().addAll(btnUp, btnDown, btnDel);
                    }

                    boolean isSelected = isSelected();
                    pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), isSelected);

                    if (isSelected) {
                        selectedOverlay.setOpacity(1.0);
                        activeBar.setVisible(true);
                        activeBar.setOpacity(1.0);
                        actions.setVisible(true);
                        lblNombre.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
                        lblIcon.setStyle("-fx-text-fill: white;");
                    } else {
                        selectedOverlay.setOpacity(0.0);
                        activeBar.setVisible(false);
                        actions.setVisible(false);
                        lblNombre.setStyle("-fx-font-weight: normal; -fx-text-fill: #a0a5cc;");
                        lblIcon.setStyle("-fx-text-fill: #a0a5cc;");
                    }

                    cm.getItems().clear();

                    if (item instanceof ImagenFondoElemento) {
                        MenuItem mEdit = new MenuItem("Editar fondo...");
                        mEdit.setOnAction(e -> {
                            if (onEditExternal != null) onEditExternal.accept((ImagenFondoElemento) item);
                        });
                        cm.getItems().add(mEdit);

                    } else {
                        MenuItem mLock = new MenuItem(item.isLocked() ? "Desbloquear" : "Bloquear");
                        mLock.setOnAction(e -> {
                            if (onToggleLock != null) onToggleLock.accept(item);
                        });

                        MenuItem mDel = new MenuItem("Eliminar capa");
                        mDel.setStyle("-fx-text-fill: #ff5555;");
                        mDel.setOnAction(e -> {
                            currentProj.getElementosActuales().remove(item);
                            if (onCanvasRedraw != null) onCanvasRedraw.run();
                            rebuildLayersPanel(currentProj, null, panel);
                        });

                        cm.getItems().addAll(mLock, new SeparatorMenuItem(), mDel);
                    }

                    setContextMenu(cm);
                    setGraphic(cellLayout);
                }
            });

            layersListView.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                    Elemento sel = layersListView.getSelectionModel().getSelectedItem();
                    if (sel != null && !(sel instanceof ImagenFondoElemento)) {
                        currentProj.getElementosActuales().remove(sel);
                        if (onCanvasRedraw != null) onCanvasRedraw.run();
                        rebuildLayersPanel(currentProj, null, panel);
                    }
                }
            });

            layersListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (!isUpdatingSelection && onElementSelected != null) {
                    onElementSelected.accept(newVal);
                }
            });
        }

        panel.getChildren().addAll(lblCapas, layersListView);
        return panel;
    }

    // =====================================================
    // Refresco de capas
    // =====================================================

    public void refreshLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        if (layersListView != null) {
            isUpdatingSelection = true;
            try {
                layersListView.setItems(getCapasDeProyecto(proyecto));

                layersListView.getSelectionModel().clearSelection();
                if (selectedElement != null) {
                    layersListView.getSelectionModel().select(selectedElement);
                    layersListView.scrollTo(selectedElement);
                }

                layersListView.refresh();
            } finally {
                isUpdatingSelection = false;
            }
        }
    }

    public ObservableList<Elemento> getCapasDeProyecto(Proyecto proyecto) {
        ObservableList<Elemento> capas = FXCollections.observableArrayList();

        if (proyecto != null) {
            ImagenFondoElemento fondo = proyecto.getFondoActual();
            if (fondo != null) {
                capas.add(fondo);
            }
            capas.addAll(proyecto.getElementosActuales());
        }

        return capas;
    }
}