package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.util.AnimationHelper;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.geometry.Bounds;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Gestor especializado en las animaciones y transiciones visuales del Canvas.
 * Centraliza la lógica de snapshots y transiciones (CrossFade, Rotación) para limpiar el controlador.
 */
public class CanvasAnimationManager {

    private final Canvas canvas;
    private final StackPane canvasContainer;

    public CanvasAnimationManager(Canvas canvas, StackPane canvasContainer) {
        this.canvas = canvas;
        this.canvasContainer = canvasContainer;
    }

    /**
     * Realiza una transición de fundido cruzado (cross-fade) entre el estado actual 
     * del canvas y el nuevo estado tras ejecutar la acción de cambio.
     */
    public void ejecutarCrossFade(Runnable accionCambio, Proyecto proyectoActual) {
        if (canvas == null || canvasContainer == null) {
            accionCambio.run();
            return;
        }

        try {
            // Si el canvas no está en escena o es el primer proyecto, hacemos un fade-in tradicional
            if (canvas.getScene() == null || proyectoActual == null) {
                accionCambio.run();
                FadeTransition ft = new FadeTransition(Duration.millis(450), canvas);
                ft.setFromValue(0.0);
                ft.setToValue(1.0);
                ft.play();
                return;
            }

            // 1. Capturar el estado actual del canvas
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage snapshot = canvas.snapshot(params, null);

            ImageView tempView = new ImageView(snapshot);
            tempView.setMouseTransparent(true);
            tempView.setManaged(false); // Crítico: evita que el ImageView se mueva si el panel derecho se abre/cierra

            // Fijar posición absoluta inicial basada en los bounds del canvas
            Bounds bounds = canvas.getBoundsInParent();
            tempView.setLayoutX(bounds.getMinX());
            tempView.setLayoutY(bounds.getMinY());

            canvasContainer.getChildren().add(tempView);

            // 2. Ejecutar el cambio real
            accionCambio.run();

            // 3. Animar el desvanecimiento
            FadeTransition ft = new FadeTransition(Duration.millis(450), tempView);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setInterpolator(Interpolator.EASE_BOTH);
            ft.setOnFinished(e -> canvasContainer.getChildren().remove(tempView));
            ft.play();

        } catch (Exception e) {
            accionCambio.run();
        }
    }

    /**
     * Transición específica para el giro de la tarjeta.
     * Captura el estado actual y lo rota 90 grados mientras se desvanece.
     */
    public void ejecutarGiroTransition(Runnable accionCambio, Proyecto proyectoActual) {
        if (canvas == null || canvasContainer == null || proyectoActual == null) {
            accionCambio.run();
            return;
        }

        try {
            // 1. Captura transparente
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage snapshot = canvas.snapshot(params, null);

            ImageView tempView = new ImageView(snapshot);
            tempView.setMouseTransparent(true);
            tempView.setManaged(false);

            Bounds bounds = canvas.getBoundsInParent();
            tempView.setLayoutX(bounds.getMinX());
            tempView.setLayoutY(bounds.getMinY());

            canvasContainer.getChildren().add(tempView);

            // 2. Determinar sentido del giro antes del cambio
            boolean aVertical = (proyectoActual.getOrientacion() == com.tpsstudio.model.enums.Orientacion.HORIZONTAL);
            double anguloFinal = aVertical ? 90 : -90;

            // 3. Ejecutar el cambio real
            accionCambio.run();

            // 4. Animación combinada
            RotateTransition rt = new RotateTransition(Duration.millis(450), tempView);
            rt.setByAngle(anguloFinal);

            FadeTransition ft = new FadeTransition(Duration.millis(450), tempView);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);

            ParallelTransition parallel = new ParallelTransition(rt, ft);
            parallel.setInterpolator(Interpolator.EASE_BOTH);
            parallel.setOnFinished(e -> canvasContainer.getChildren().remove(tempView));
            parallel.play();

        } catch (Exception e) {
            accionCambio.run();
        }
    }
}
