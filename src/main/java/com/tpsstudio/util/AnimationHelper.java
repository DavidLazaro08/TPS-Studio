package com.tpsstudio.util;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Utilidad centralizada para animaciones de la interfaz.
 *
 * Agrupa transiciones de paneles, canvas, acordeones y pequeños efectos visuales
 * para evitar duplicar lógica en los controladores.
 */
public class AnimationHelper {

    public static final double DURATION_SLOW = 550;
    public static final double DURATION_MEDIUM = 350;
    public static final double DURATION_FAST = 200;

    public static final double DURATION_OPEN = DURATION_SLOW;
    public static final double DURATION_CLOSE = DURATION_SLOW;

    private AnimationHelper() {
    }

    // =====================================================
    // Paneles laterales
    // =====================================================

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
            fade.setInterpolator(Interpolator.EASE_BOTH);

            TranslateTransition slide = new TranslateTransition(Duration.millis(duration), panel);
            slide.setFromX(0);
            slide.setToX(panel.getPrefWidth());
            slide.setInterpolator(Interpolator.EASE_BOTH);

            ParallelTransition anim = new ParallelTransition(fade, slide);
            anim.setOnFinished(e -> {
                panel.setVisible(false);
                panel.setManaged(false);
                panel.setTranslateX(0);
            });

            anim.play();
        }
    }

    // =====================================================
    // Desplazamiento del canvas
    // =====================================================

    public static void shiftCanvas(Node target, double targetX) {
        shiftCanvas(target, targetX, DURATION_SLOW);
    }

    public static void shiftCanvas(Node target, double targetX, double durationMs) {
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

    // =====================================================
    // Acordeones
    // =====================================================

    public static void animateAccordion(Node node, boolean show) {
        if (!(node instanceof Region region)) {
            return;
        }

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

    // =====================================================
    // Efectos auxiliares
    // =====================================================

    public static Timeline createPulseAnimation(Node target) {
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(target.scaleXProperty(), 1.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.ZERO,
                        new KeyValue(target.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(800),
                        new KeyValue(target.scaleXProperty(), 1.05, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(800),
                        new KeyValue(target.scaleYProperty(), 1.05, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1600),
                        new KeyValue(target.scaleXProperty(), 1.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1600),
                        new KeyValue(target.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
        );

        pulse.setCycleCount(Animation.INDEFINITE);
        return pulse;
    }

    public static void applyFadeTransition(Node target, double durationMs, double from, double to) {
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), target);
        fade.setFromValue(from);
        fade.setToValue(to);
        fade.setInterpolator(Interpolator.EASE_BOTH);
        fade.play();
    }
}