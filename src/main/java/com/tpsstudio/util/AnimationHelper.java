package com.tpsstudio.util;

import javafx.animation.*;
import javafx.scene.layout.Region;
import javafx.scene.Node;
import javafx.util.Duration;

/* Centraliza las animaciones de paneles y canvas del editor.
 * Mantiene el MainViewController libre de lógica de transición. */

public class AnimationHelper {

    // Duraciones unificadas para una experiencia coherente y premium
    public static final double DURATION_SLOW   = 550; // Paneles principales y desplazamientos de canvas
    public static final double DURATION_MEDIUM = 350; // Acordeones y cambios de pestañas
    public static final double DURATION_FAST   = 200; // Micro-transiciones

    // Aliases para compatibilidad y semántica
    public static final double DURATION_OPEN  = DURATION_SLOW;
    public static final double DURATION_CLOSE = DURATION_SLOW;

    private AnimationHelper() {}

    /* Anima la apertura o cierre de un panel lateral (fade + slide horizontal). */
    public static void togglePanel(Region panel, boolean show) {
        double duration = show ? DURATION_OPEN : DURATION_CLOSE;
        
        if (show) {
            panel.setVisible(true);
            panel.setManaged(true);

            FadeTransition fade = new FadeTransition(Duration.millis(duration), panel);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setInterpolator(Interpolator.EASE_BOTH);

            TranslateTransition slide = new TranslateTransition(Duration.millis(duration), panel);
            slide.setFromX(panel.getPrefWidth());
            slide.setToX(0);
            slide.setInterpolator(Interpolator.EASE_BOTH);

            new ParallelTransition(fade, slide).play();
        } else {
            FadeTransition fade = new FadeTransition(Duration.millis(duration), panel);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setInterpolator(Interpolator.EASE_BOTH); // Cambiado a BOTH para frenado suave

            TranslateTransition slide = new TranslateTransition(Duration.millis(duration), panel);
            slide.setFromX(0);
            slide.setToX(panel.getPrefWidth());
            slide.setInterpolator(Interpolator.EASE_BOTH); // Cambiado a BOTH para frenado suave

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
        shiftCanvas(target, targetX, DURATION_SLOW);
    }

    public static void shiftCanvas(Node target, double targetX, double durationMs) {
        // Detener animación previa si existe en este nodo para evitar saltos
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
     */
    public static void animateAccordion(Node node, boolean show) {
        if (!(node instanceof Region region)) return;

        double duration = show ? DURATION_MEDIUM : DURATION_MEDIUM * 0.8;

        if (show) {
            region.setManaged(true);
            region.setVisible(true);
            region.setOpacity(0.0);

            FadeTransition fade = new FadeTransition(Duration.millis(duration), region);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setInterpolator(Interpolator.EASE_BOTH);
            fade.play();
        } else {
            FadeTransition fade = new FadeTransition(Duration.millis(duration), region);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setInterpolator(Interpolator.EASE_BOTH);
            fade.setOnFinished(e -> {
                region.setVisible(false);
                region.setManaged(false);
            });
            fade.play();
        }
    }
}
