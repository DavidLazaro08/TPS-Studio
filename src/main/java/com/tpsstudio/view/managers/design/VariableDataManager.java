package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.service.ProjectManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Gestiona el panel de Datos Variables en modo Diseño.
 *
 * Permite navegar por los registros de una fuente de datos vinculada,
 * consultar sus valores y editarlos de forma rápida desde el panel lateral.
 */
public class VariableDataManager {

    private final ProjectManager projectManager;
    private final Consumer<Proyecto> onEditProject;
    private final Runnable onCanvasRedraw;

    public VariableDataManager(ProjectManager projectManager,
                               Consumer<Proyecto> onEditProject,
                               Runnable onCanvasRedraw) {
        this.projectManager = projectManager;
        this.onEditProject = onEditProject;
        this.onCanvasRedraw = onCanvasRedraw;
    }

    // =====================================================
    // Construcción principal
    // =====================================================

    public VBox buildPanel(Proyecto proyecto) {
        if (projectManager == null || projectManager.getFuenteDatos() == null) {
            return buildEmptyPanel(proyecto);
        }

        return buildNavigationPanel(projectManager.getFuenteDatos(), proyecto);
    }

    private VBox buildNavigationPanel(FuenteDatos datos, Proyecto proyecto) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label lblTitulo = new Label(datos.getNombreArchivo());
        lblTitulo.getStyleClass().add("panel-title");
        lblTitulo.setMaxWidth(Double.MAX_VALUE);

        Button btnCambiarBD = new Button("⚙ Configurar BD...");
        btnCambiarBD.getStyleClass().add("toolbox-btn");
        btnCambiarBD.setMaxWidth(Double.MAX_VALUE);
        btnCambiarBD.setOnAction(e -> {
            if (onEditProject != null) onEditProject.accept(proyecto);
        });

        Label lblContador = new Label(calcularContador(datos));
        lblContador.getStyleClass().add("toolbar-label");

        Button btnAnterior = new Button("◄ Anterior");
        btnAnterior.getStyleClass().add("toolbox-btn");
        btnAnterior.setMaxWidth(Double.MAX_VALUE);
        btnAnterior.setDisable(datos.getIndiceActual() <= 0);

        Button btnSiguiente = new Button("Siguiente ►");
        btnSiguiente.getStyleClass().add("toolbox-btn");
        btnSiguiente.setMaxWidth(Double.MAX_VALUE);
        btnSiguiente.setDisable(datos.getIndiceActual() >= datos.getTotalRegistros() - 1);

        HBox navBox = new HBox(8, btnAnterior, btnSiguiente);
        HBox.setHgrow(btnAnterior, Priority.ALWAYS);
        HBox.setHgrow(btnSiguiente, Priority.ALWAYS);

        VBox vistaRegistro = new VBox(6);
        vistaRegistro.setPadding(new Insets(4, 0, 4, 0));
        rellenarVistaRegistro(vistaRegistro, datos);

        ScrollPane scrollRegistro = new ScrollPane(vistaRegistro);
        scrollRegistro.setFitToWidth(true);
        scrollRegistro.getStyleClass().add("panel-scroll-view");
        VBox.setVgrow(scrollRegistro, Priority.ALWAYS);

        btnAnterior.setOnAction(e -> {
            datos.anterior();
            actualizarNavegacion(datos, lblContador, btnAnterior, btnSiguiente, vistaRegistro);
        });

        btnSiguiente.setOnAction(e -> {
            datos.siguiente();
            actualizarNavegacion(datos, lblContador, btnAnterior, btnSiguiente, vistaRegistro);
        });

        panel.getChildren().addAll(
                lblTitulo,
                btnCambiarBD,
                new Separator(),
                lblContador,
                navBox,
                new Separator(),
                scrollRegistro
        );

        return panel;
    }

    // =====================================================
    // Navegación y edición de registros
    // =====================================================

    private void actualizarNavegacion(FuenteDatos datos, Label lblContador, Button btnAnt, Button btnSig, VBox contenedor) {
        lblContador.setText(calcularContador(datos));
        btnAnt.setDisable(datos.getIndiceActual() <= 0);
        btnSig.setDisable(datos.getIndiceActual() >= datos.getTotalRegistros() - 1);

        contenedor.getChildren().clear();
        rellenarVistaRegistro(contenedor, datos);

        if (onCanvasRedraw != null) onCanvasRedraw.run();
    }

    private void rellenarVistaRegistro(VBox contenedor, FuenteDatos datos) {
        Map<String, String> registro = datos.getRegistroActual();

        if (registro == null) {
            contenedor.getChildren().add(new Label("(sin registros)"));
            return;
        }

        for (String columna : datos.getColumnas()) {
            String valor = registro.getOrDefault(columna, "");

            Label lblCol = new Label(columna);
            lblCol.getStyleClass().add("dato-columna");

            Label lblVal = new Label(valor.isEmpty() ? "—" : valor);
            lblVal.getStyleClass().add("dato-valor");
            lblVal.setWrapText(true);
            lblVal.setTooltip(new Tooltip("Doble clic para editar valor"));
            lblVal.setCursor(Cursor.HAND);

            StackPane stack = new StackPane(lblVal);
            stack.setAlignment(Pos.CENTER_LEFT);

            lblVal.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    TextField txt = new TextField(valor);
                    txt.getStyleClass().add("dato-edit-field");
                    stack.getChildren().setAll(txt);
                    txt.requestFocus();
                    txt.selectAll();

                    final boolean[] done = {false};

                    Runnable commit = () -> {
                        if (done[0]) return;
                        done[0] = true;

                        String nuevo = txt.getText().trim();

                        if (!nuevo.equals(valor)) {
                            datos.actualizarValorActual(columna, nuevo);
                            if (projectManager != null) projectManager.guardarFuenteDatosActual();
                            if (onCanvasRedraw != null) onCanvasRedraw.run();
                        }

                        lblVal.setText(nuevo.isEmpty() ? "—" : nuevo);
                        stack.getChildren().setAll(lblVal);
                    };

                    txt.setOnAction(ev -> commit.run());
                    txt.focusedProperty().addListener((obs, o, n) -> {
                        if (!n) commit.run();
                    });
                }
            });

            VBox campo = new VBox(2, lblCol, stack);
            campo.getStyleClass().add("dato-campo");
            contenedor.getChildren().add(campo);
        }
    }

    // =====================================================
    // Estado sin base de datos
    // =====================================================

    private VBox buildEmptyPanel(Proyecto proyecto) {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(30));
        panel.setAlignment(Pos.CENTER);

        Label lbl = new Label("No hay base de datos vinculada");
        lbl.getStyleClass().add("panel-placeholder");

        Button btn = new Button("+ Vincular Base de Datos");
        btn.getStyleClass().add("primary-btn");
        btn.setOnAction(e -> {
            if (onEditProject != null) onEditProject.accept(proyecto);
        });

        panel.getChildren().addAll(lbl, btn);
        return panel;
    }

    private String calcularContador(FuenteDatos datos) {
        if (!datos.tieneRegistros()) return "(sin registros)";
        return "Registro " + datos.getPosicionActual() + " / " + datos.getTotalRegistros();
    }
}