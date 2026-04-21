package com.tpsstudio.util;

import javafx.animation.*;
import javafx.scene.layout.Region;
import javafx.scene.Node;
import javafx.util.Duration;

/* Centraliza las animaciones de paneles y canvas del editor.
 * Mantiene el MainViewController libre de lógica de transición. */

public class AnimationHelper {

    private static final double DURATION_OPEN  = 550;
    private static final double DURATION_CLOSE = 400;

    private AnimationHelper() {}

    /* Anima la apertura o cierre de un panel lateral (fade + slide horizontal). */
    public static void togglePanel(Region panel, boolean show) {
        if (show) {
            panel.setVisible(true);
            panel.setManaged(true);

            FadeTransition fade = new FadeTransition(Duration.millis(DURATION_OPEN), panel);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setInterpolator(Interpolator.EASE_BOTH);

            TranslateTransition slide = new TranslateTransition(Duration.millis(DURATION_OPEN), panel);
            slide.setFromX(panel.getPrefWidth());
            slide.setToX(0);
            slide.setInterpolator(Interpolator.EASE_BOTH);

            new ParallelTransition(fade, slide).play();
        } else {
            FadeTransition fade = new FadeTransition(Duration.millis(DURATION_CLOSE), panel);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setInterpolator(Interpolator.EASE_IN);

            TranslateTransition slide = new TranslateTransition(Duration.millis(DURATION_CLOSE), panel);
            slide.setFromX(0);
            slide.setToX(panel.getPrefWidth());
            slide.setInterpolator(Interpolator.EASE_IN);

            ParallelTransition anim = new ParallelTransition(fade, slide);
            anim.setOnFinished(e -> {
                panel.setVisible(false);
                panel.setManaged(false);
                panel.setTranslateX(0);
            });
            anim.play();
        }
    }

    /* Desplaza suavemente un nodo (el canvas) para compensar la apertura del panel. */
    public static void shiftCanvas(Node target, double targetX) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(DURATION_OPEN), target);
        transition.setToX(targetX);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        transition.play();
    }

    /**
     * Anima la aparición de un submenú tipo acordeón.
     * Além del fundido (Fade), anima la altura para que el desplazamiento
     * del resto de elementos sea totalmente fluido.
     */
    public static void animateAccordion(Node node, boolean show) {
        if (!(node instanceof Region region)) return;

        // Asegurar que puede encogerse hasta 0
        region.setMinHeight(0);

        if (show) {
            // 1. Preparar el nodo con altura 0 y hacerlo 'managed' (pero invisible al ocupar 0)
            region.setOpacity(0.0);
            region.setPrefHeight(0);
            region.setMaxHeight(0);
            region.setManaged(true);
            region.setVisible(true);
            
            // 2. Esperar al siguiente pulso de layout para medir la altura real ya con el CSS aplicado
            javafx.application.Platform.runLater(() -> {
                double targetH = region.prefHeight(-1);
                
                // Fallback si por algún motivo el cálculo da 0 (evita el salto brusco al final)
                if (targetH <= 0) targetH = 120; 

                // Clip para un recorte limpio
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
                clip.widthProperty().bind(region.widthProperty());
                clip.setHeight(0);
                region.setClip(clip);

                Timeline openTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO, 
                        new KeyValue(region.prefHeightProperty(), 0),
                        new KeyValue(region.maxHeightProperty(), 0),
                        new KeyValue(region.opacityProperty(), 0),
                        new KeyValue(clip.heightProperty(), 0)
                    ),
                    new KeyFrame(Duration.millis(DURATION_OPEN),
                        new KeyValue(region.prefHeightProperty(), targetH, Interpolator.EASE_BOTH),
                        new KeyValue(region.maxHeightProperty(), targetH, Interpolator.EASE_BOTH),
                        new KeyValue(region.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(clip.heightProperty(), targetH, Interpolator.EASE_BOTH)
                    )
                );
                openTimeline.setOnFinished(e -> {
                    region.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    region.setMaxHeight(Double.MAX_VALUE);
                    region.setClip(null);
                });
                openTimeline.play();
            });
        } else {
            double startH = region.getHeight();
            region.setPrefHeight(startH);
            region.setMaxHeight(startH);

            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            clip.widthProperty().bind(region.widthProperty());
            clip.setHeight(startH);
            region.setClip(clip);

            Timeline closeTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, 
                    new KeyValue(region.prefHeightProperty(), startH),
                    new KeyValue(region.maxHeightProperty(), startH),
                    new KeyValue(region.opacityProperty(), 1.0),
                    new KeyValue(clip.heightProperty(), startH)
                ),
                new KeyFrame(Duration.millis(DURATION_CLOSE),
                    new KeyValue(region.prefHeightProperty(), 0, Interpolator.EASE_BOTH),
                    new KeyValue(region.maxHeightProperty(), 0, Interpolator.EASE_BOTH),
                    new KeyValue(region.opacityProperty(), 0, Interpolator.EASE_BOTH),
                    new KeyValue(clip.heightProperty(), 0, Interpolator.EASE_BOTH)
                )
            );
            closeTimeline.setOnFinished(e -> {
                region.setVisible(false);
                region.setManaged(false);
                region.setClip(null);
            });
            closeTimeline.play();
        }
    }
}
