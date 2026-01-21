package com.tpsstudio.view;

import com.tpsstudio.model.Proyecto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.paint.Color;

public class MainViewController {

    @FXML
    private ListView<Proyecto> listProyectos;

    @FXML
    private Canvas canvas;

    @FXML
    private Label lblZoom;

    @FXML
    private ToggleButton toggleFrenteDorso;

    @FXML
    private ToggleButton toggleGuias;

    // Lista observable de proyectos
    private final ObservableList<Proyecto> proyectos = FXCollections.observableArrayList();

    // Proyecto actualmente seleccionado
    private Proyecto proyectoActual;

    // Nivel de zoom (100% = 1.0)
    private double zoomLevel = 1.0;

    // Dimensiones CR80 en píxeles (escala 4px = 1mm)
    private static final double CR80_WIDTH_MM = 85.60;
    private static final double CR80_HEIGHT_MM = 53.98;
    private static final double SCALE = 4.0;

    private static final double CARD_WIDTH = CR80_WIDTH_MM * SCALE; // 342.4 px
    private static final double CARD_HEIGHT = CR80_HEIGHT_MM * SCALE; // 215.92 px

    // Márgenes de seguridad y sangrado (3mm = 12px)
    private static final double SAFETY_MARGIN = 3.0 * SCALE; // 12 px
    private static final double BLEED_MARGIN = 3.0 * SCALE; // 12 px

    @FXML
    private void initialize() {
        // Configurar lista de proyectos
        listProyectos.setItems(proyectos);

        // Listener para selección de proyecto
        listProyectos.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        proyectoActual = newVal;
                        dibujarCanvas();
                    }
                });

        // Dibujar canvas inicial (vacío)
        dibujarCanvas();
    }

    /**
     * Dibuja el canvas con la tarjeta CR80 y las guías visuales
     */
    private void dibujarCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Limpiar canvas
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Si no hay proyecto seleccionado, mostrar mensaje
        if (proyectoActual == null) {
            gc.setFill(Color.web("#9a9598"));
            gc.fillText("Seleccione un proyecto o cree uno nuevo",
                    canvas.getWidth() / 2 - 120, canvas.getHeight() / 2);
            return;
        }

        // Calcular posición centrada de la tarjeta con zoom
        double scaledWidth = CARD_WIDTH * zoomLevel;
        double scaledHeight = CARD_HEIGHT * zoomLevel;

        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        double cardX = centerX - (scaledWidth / 2);
        double cardY = centerY - (scaledHeight / 2);

        // Guardar estado para aplicar zoom
        gc.save();

        // 1. Dibujar zona de sangrado (si guías están activas)
        if (toggleGuias.isSelected()) {
            double bleedScaled = BLEED_MARGIN * zoomLevel;
            gc.setStroke(Color.web("#d48a8a"));
            gc.setLineWidth(1);
            gc.setLineDashes(5, 5);
            gc.strokeRect(
                    cardX - bleedScaled,
                    cardY - bleedScaled,
                    scaledWidth + (bleedScaled * 2),
                    scaledHeight + (bleedScaled * 2));
        }

        // 2. Dibujar tarjeta CR80 (blanco)
        gc.setFill(Color.WHITE);
        gc.fillRect(cardX, cardY, scaledWidth, scaledHeight);

        // Borde de la tarjeta
        gc.setStroke(Color.web("#c4c0c2"));
        gc.setLineWidth(1);
        gc.setLineDashes(); // Sin discontinuidad
        gc.strokeRect(cardX, cardY, scaledWidth, scaledHeight);

        // 3. Dibujar guías de seguridad (si guías están activas)
        if (toggleGuias.isSelected()) {
            double safetyScaled = SAFETY_MARGIN * zoomLevel;
            gc.setStroke(Color.web("#4a6b7c"));
            gc.setLineWidth(1);
            gc.setLineDashes(3, 3);
            gc.strokeRect(
                    cardX + safetyScaled,
                    cardY + safetyScaled,
                    scaledWidth - (safetyScaled * 2),
                    scaledHeight - (safetyScaled * 2));
        }

        // 4. Texto informativo
        gc.setFill(Color.web("#9a9598"));
        gc.setLineDashes();
        String lado = proyectoActual.isMostrandoFrente() ? "Frente" : "Dorso";
        gc.fillText("CR80: 85.60 × 53.98 mm - " + lado, cardX, cardY - 10);

        gc.restore();
    }

    // ========== ACCIONES DE TOOLBAR ==========

    @FXML
    private void onNuevoProyecto() {
        // TODO: Implementar diálogo de nuevo proyecto
        System.out.println("Nuevo proyecto (placeholder)");
    }

    @FXML
    private void onAbrirProyecto() {
        // TODO: Implementar diálogo de abrir
        System.out.println("Abrir proyecto (placeholder)");
    }

    @FXML
    private void onGuardarProyecto() {
        // TODO: Implementar guardado
        System.out.println("Guardar proyecto (placeholder)");
    }

    @FXML
    private void onExportarProyecto() {
        // TODO: Implementar exportación
        System.out.println("Exportar proyecto (placeholder)");
    }

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
        dibujarCanvas();
    }

    @FXML
    private void onToggleFrenteDorso() {
        if (proyectoActual != null) {
            proyectoActual.setMostrandoFrente(toggleFrenteDorso.isSelected());
            toggleFrenteDorso.setText(toggleFrenteDorso.isSelected() ? "Frente" : "Dorso");
            dibujarCanvas();
        }
    }

    @FXML
    private void onToggleGuias() {
        dibujarCanvas();
    }

    @FXML
    private void onNuevoCR80() {
        // Crear nuevo proyecto
        int numero = proyectos.size() + 1;
        Proyecto nuevoProyecto = new Proyecto("Tarjeta CR80 #" + numero);

        // Añadir a la lista
        proyectos.add(nuevoProyecto);

        // Seleccionar automáticamente
        listProyectos.getSelectionModel().select(nuevoProyecto);
    }
}
