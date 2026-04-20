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
}
