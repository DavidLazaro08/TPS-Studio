package com.tpsstudio.util;

import javafx.animation.*;
import javafx.scene.layout.Region;
import javafx.scene.Node;
import javafx.util.Duration;

/* Centraliza las animaciones de paneles y canvas del editor.
 * Mantiene el MainViewController libre de lógica de transición. */

public class AnimationHelper {

    public static final double DURATION_OPEN  = 550;
    public static final double DURATION_CLOSE = 400;

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
        shiftCanvas(target, targetX, DURATION_OPEN);
    }

    public static void shiftCanvas(Node target, double targetX, double durationMs) {
        // Detener animación previa si existe en este nodo
        Object active = target.getProperties().get("activeShift");
        if (active instanceof Transition t) {
            t.stop();
        }

        TranslateTransition transition = new TranslateTransition(Duration.millis(durationMs), target);
        transition.setToX(targetX);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        
        target.getProperties().put("activeShift", transition);
        transition.setOnFinished(e -> target.getProperties().remove("activeShift"));
        
        transition.play();
    }

    /**
     * Muestra u oculta un submenú tipo acordeón.
     * Usa toggle directo de managed/visible para garantizar que el VBox padre
     * recalcule el layout correctamente y empuje los elementos hacia abajo.
     * Se añade un fade suave para mejorar la experiencia visual.
     */
    public static void animateAccordion(Node node, boolean show) {
        if (!(node instanceof Region region)) return;

        if (show) {
            // Primero hacemos el nodo gestionado y visible para que el layout lo incluya
            region.setManaged(true);
            region.setVisible(true);
            region.setOpacity(0.0);

            // Fade de entrada lento y suave
            FadeTransition fade = new FadeTransition(Duration.millis(420), region);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setInterpolator(Interpolator.EASE_BOTH);
            fade.play();
        } else {
            FadeTransition fade = new FadeTransition(Duration.millis(280), region);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setInterpolator(Interpolator.EASE_IN);
            fade.setOnFinished(e -> {
                region.setVisible(false);
                region.setManaged(false);
            });
            fade.play();
        }
    }
}
