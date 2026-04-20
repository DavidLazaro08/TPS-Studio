package com.tpsstudio.util;

import javafx.animation.Interpolator;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Notificaciones tipo "toast" para TPS Studio.
 * Los estilos están en /css/toast.css — ningún setStyle() en este archivo.
 */
public class TPSToast {

    private static final String CSS = TPSToast.class
            .getResource("/css/toast.css").toExternalForm();

    public enum Tipo {
        EXITO("✔", "toast-exito", "toast-icon-exito"),
        INFO("ℹ",  "toast-info",  "toast-icon-info"),
        AVISO("⚠", "toast-aviso", "toast-icon-aviso"),
        ERROR("✖", "toast-error", "toast-icon-error");

        final String icono;
        final String claseRaiz;
        final String claseIcono;

        Tipo(String icono, String claseRaiz, String claseIcono) {
            this.icono     = icono;
            this.claseRaiz = claseRaiz;
            this.claseIcono = claseIcono;
        }
    }

    /**
     * Muestra un toast no bloqueante en la parte inferior de la ventana.
     *
     * @param owner    Ventana propietaria (para posicionamiento)
     * @param titulo   Texto principal
     * @param subtexto Texto secundario (puede ser null)
     * @param tipo     Tipo visual: EXITO, INFO, AVISO, ERROR
     * @param duracion Segundos visibles antes de desvanecerse
     */
    public static void mostrar(Window owner, String titulo, String subtexto, Tipo tipo, double duracion) {
        Platform.runLater(() -> {
            Stage toast = new Stage();
            toast.initOwner(owner);
            toast.initModality(Modality.NONE);
            toast.initStyle(StageStyle.TRANSPARENT);

            // Badge de icono
            Label lblIcono = new Label(tipo.icono);
            lblIcono.getStyleClass().addAll("toast-icon", tipo.claseIcono);
            lblIcono.setAlignment(Pos.CENTER);

            // Texto principal
            Label lblTitulo = new Label(titulo);
            lblTitulo.getStyleClass().add("toast-title");
            lblTitulo.setWrapText(false);

            VBox textBox = new VBox(2, lblTitulo);
            textBox.setAlignment(Pos.CENTER_LEFT);

            if (subtexto != null && !subtexto.isBlank()) {
                Label lblSub = new Label(subtexto);
                lblSub.getStyleClass().add("toast-subtitle");
                lblSub.setWrapText(true);
                textBox.getChildren().add(lblSub);
            }

            HBox root = new HBox(14, lblIcono, textBox);
            root.setAlignment(Pos.CENTER_LEFT);
            root.getStyleClass().addAll("toast-root", tipo.claseRaiz);
            root.setPrefWidth(400);
            root.setMaxWidth(460);
            root.setEffect(new DropShadow(18, Color.rgb(0, 0, 0, 0.60)));

            root.setOnMouseClicked(e -> cerrarToast(toast, root));

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(CSS);
            toast.setScene(scene);

            // Posición: parte inferior central de la ventana
            if (owner != null) {
                toast.setX(owner.getX() + (owner.getWidth() / 2) - 200);
                toast.setY(owner.getY() + owner.getHeight() - 115);
            }

            root.setOpacity(0);
            root.setTranslateY(8);
            toast.show();

            // Entrada: fade + slide hacia arriba
            FadeTransition fadeIn = new FadeTransition(Duration.millis(280), root);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(280), root);
            slideIn.setFromY(8);
            slideIn.setToY(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);

            new ParallelTransition(fadeIn, slideIn).play();

            // Auto-cierre
            PauseTransition pausa = new PauseTransition(Duration.seconds(duracion));
            pausa.setOnFinished(e -> cerrarToast(toast, root));
            pausa.play();
        });
    }

    /** Cierre suave */
    private static void cerrarToast(Stage toast, HBox root) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
        fadeOut.setFromValue(root.getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setInterpolator(Interpolator.EASE_IN);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), root);
        slideOut.setToY(6);
        slideOut.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition salida = new ParallelTransition(fadeOut, slideOut);
        salida.setOnFinished(e -> toast.close());
        salida.play();
    }

    /** Duración por defecto: 4.5 segundos */
    public static void mostrar(Window owner, String titulo, String subtexto, Tipo tipo) {
        mostrar(owner, titulo, subtexto, tipo, 4.5);
    }

    /** Versión compacta sin subtexto */
    public static void mostrar(Window owner, String titulo, Tipo tipo) {
        mostrar(owner, titulo, null, tipo);
    }
}
