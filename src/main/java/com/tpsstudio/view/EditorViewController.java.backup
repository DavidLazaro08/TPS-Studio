package com.tpsstudio.view;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class EditorViewController {

    @FXML
    private Canvas canvas;

    // Dimensiones CR80 en píxeles (escala 4px = 1mm)
    private static final double CR80_WIDTH_MM = 85.60;
    private static final double CR80_HEIGHT_MM = 53.98;
    private static final double SCALE = 4.0; // 4 píxeles por milímetro

    private static final double CARD_WIDTH = CR80_WIDTH_MM * SCALE; // 342.4 px
    private static final double CARD_HEIGHT = CR80_HEIGHT_MM * SCALE; // 215.92 px

    // Márgenes de seguridad y sangrado (3mm = 12px)
    private static final double SAFETY_MARGIN = 3.0 * SCALE; // 12 px
    private static final double BLEED_MARGIN = 3.0 * SCALE; // 12 px

    @FXML
    private void initialize() {
        // Dibujar el canvas cuando se carga la vista
        drawCanvas();
    }

    /**
     * Dibuja el canvas con la tarjeta CR80 y las guías visuales
     */
    private void drawCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Limpiar canvas
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Calcular posición centrada de la tarjeta
        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        double cardX = centerX - (CARD_WIDTH / 2);
        double cardY = centerY - (CARD_HEIGHT / 2);

        // 1. Dibujar zona de sangrado (exterior, rojo suave)
        gc.setStroke(Color.web("#d48a8a"));
        gc.setLineWidth(1);
        gc.setLineDashes(5, 5);
        gc.strokeRect(
                cardX - BLEED_MARGIN,
                cardY - BLEED_MARGIN,
                CARD_WIDTH + (BLEED_MARGIN * 2),
                CARD_HEIGHT + (BLEED_MARGIN * 2));

        // 2. Dibujar tarjeta CR80 (blanco)
        gc.setFill(Color.WHITE);
        gc.fillRect(cardX, cardY, CARD_WIDTH, CARD_HEIGHT);

        // Borde de la tarjeta (gris claro)
        gc.setStroke(Color.web("#c4c0c2"));
        gc.setLineWidth(1);
        gc.setLineDashes(); // Sin discontinuidad
        gc.strokeRect(cardX, cardY, CARD_WIDTH, CARD_HEIGHT);

        // 3. Dibujar guías de seguridad (interior, azul petróleo)
        gc.setStroke(Color.web("#4a6b7c"));
        gc.setLineWidth(1);
        gc.setLineDashes(3, 3);
        gc.strokeRect(
                cardX + SAFETY_MARGIN,
                cardY + SAFETY_MARGIN,
                CARD_WIDTH - (SAFETY_MARGIN * 2),
                CARD_HEIGHT - (SAFETY_MARGIN * 2));

        // 4. Texto informativo (opcional, para referencia)
        gc.setFill(Color.web("#9a9598"));
        gc.setLineDashes(); // Sin discontinuidad
        gc.fillText("CR80: 85.60 × 53.98 mm", cardX, cardY - 10);
    }

    @FXML
    private void onClose() {
        try {
            // Volver a la vista principal
            Stage stage = (Stage) canvas.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/main_view.fxml"));

            Scene scene = new Scene(loader.load(), 1100, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/css/app.css").toExternalForm());

            stage.setScene(scene);
            stage.setTitle("TPS Studio");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
