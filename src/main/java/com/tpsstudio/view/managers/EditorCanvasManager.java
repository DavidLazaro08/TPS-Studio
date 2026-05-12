package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.enums.Orientacion;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.util.AnimationHelper;
import com.tpsstudio.view.managers.design.CanvasInteractionHandler;
import com.tpsstudio.view.managers.design.CanvasRenderer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.BoundingBox;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Gestor coordinar del canvas.
 * Mantiene el estado y delega el dibujo al Renderer y la interacción al InteractionHandler.
 */
public class EditorCanvasManager {

    // Constantes CR80
    public static final double CR80_WIDTH_MM = 85.60;
    public static final double CR80_HEIGHT_MM = 53.98;
    public static final double SCALE = 4.0;
    public static final double CARD_WIDTH = CR80_WIDTH_MM * SCALE;
    public static final double CARD_HEIGHT = CR80_HEIGHT_MM * SCALE;
    public static final double SAFETY_MARGIN = 3.0 * SCALE;
    public static final double BLEED_MARGIN = 2.0 * SCALE;
    public static final double HANDLE_SIZE = 8.0;

    private final Canvas canvas;
    private final CanvasRenderer renderer;
    private final CanvasInteractionHandler interactionHandler;

    // Estado
    private Proyecto proyectoActual;
    private Elemento elementoSeleccionado;
    private double zoomLevel = 1.3;
    private boolean mostrarGuias = false;
    private AppMode currentMode = AppMode.PRODUCTION;
    private FuenteDatos fuenteDatos;
    private double hudOpacity = 1.0;
    private Timeline hudFadeTimeline;
    private DoubleProperty hudOpacityProp;

    // Estado de animación para Cuentagotas
    private javafx.animation.AnimationTimer eyedropperTimer;
    private double eyedropperPulse = 0.0;
    private long lastPulseUpdate = 0;
    private double mouseX = 0, mouseY = 0;

    // Callbacks
    private Runnable onElementSelected;
    private Runnable onCanvasChanged;
    private Runnable onElementTransformed;
    private Runnable onClientDataRequested;

    public EditorCanvasManager(Canvas canvas) {
        this.canvas = canvas;
        this.renderer = new CanvasRenderer();
        this.interactionHandler = new CanvasInteractionHandler(canvas, this);
    }

    // ===================== LÓGICA DE DIBUJO (DELEGADA) =====================
    
    public void dibujarCanvas() {
        boolean eyedropperActive = interactionHandler != null && interactionHandler.isEyedropperActive();
        renderer.render(canvas.getGraphicsContext2D(), proyectoActual, canvas.getWidth(), canvas.getHeight(),
                       zoomLevel, mostrarGuias, currentMode, elementoSeleccionado, fuenteDatos, hudOpacity,
                       eyedropperActive, mouseX, mouseY, eyedropperPulse);
    }

    // ===================== MÉTODOS DE ESTADO =====================

    public void setProyectoActual(Proyecto proyecto) {
        this.proyectoActual = proyecto;
        refrescarFondosTrasCarga();
        dibujarCanvas();
    }

    public void refrescarFondosTrasCarga() {
        if (proyectoActual != null) {
            double w = getScaledWidth(proyectoActual), h = getScaledHeight(proyectoActual);
            if (proyectoActual.getFondoFrente() != null) proyectoActual.getFondoFrente().ajustarATamaño(w, h, BLEED_MARGIN);
            if (proyectoActual.getFondoDorso() != null) proyectoActual.getFondoDorso().ajustarATamaño(w, h, BLEED_MARGIN);
        }
    }

    public void setupMouseHandlers() {
        // Ahora se hace automáticamente en el constructor a través de CanvasInteractionHandler.
        // Se mantiene el método vacío para compatibilidad con MainViewController.
    }

    public void setCurrentMode(AppMode mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            if (hudFadeTimeline != null) hudFadeTimeline.stop();
            double target = (mode == AppMode.PRODUCTION) ? 1.0 : 0.0;
            hudFadeTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(hudOpacityProperty(), hudOpacity)),
                new KeyFrame(Duration.millis(AnimationHelper.DURATION_SLOW), new KeyValue(hudOpacityProperty(), target))
            );
            hudFadeTimeline.play();
        }
    }

    private DoubleProperty hudOpacityProperty() {
        if (hudOpacityProp == null) {
            hudOpacityProp = new SimpleDoubleProperty(hudOpacity);
            hudOpacityProp.addListener((obs, o, n) -> { hudOpacity = n.doubleValue(); dibujarCanvas(); });
        }
        return hudOpacityProp;
    }

    // ===================== GETTERS DE POSICIÓN =====================

    public double getCardX() { return (canvas.getWidth() / 2) - (getScaledWidth(proyectoActual) * zoomLevel / 2); }
    public double getCardY() { return (canvas.getHeight() / 2) - (getScaledHeight(proyectoActual) * zoomLevel / 2); }
    public static double getScaledWidth(Proyecto p) { return (p != null && p.getOrientacion() == Orientacion.VERTICAL) ? CARD_HEIGHT : CARD_WIDTH; }
    public static double getScaledHeight(Proyecto p) { return (p != null && p.getOrientacion() == Orientacion.VERTICAL) ? CARD_WIDTH : CARD_HEIGHT; }

    // ===================== ACCIONES (DELEGADAS AL HANDLER) =====================
    
    public void setMousePosition(double x, double y) {
        this.mouseX = x; this.mouseY = y;
        if (interactionHandler != null && interactionHandler.isEyedropperActive()) dibujarCanvas();
    }

    public void activateEyedropper(Consumer<Color> callback) { 
        interactionHandler.activateEyedropper(callback); 
        startEyedropperPulse();
    }
    
    public void deactivateEyedropper() { 
        interactionHandler.deactivateEyedropper(); 
        stopEyedropperPulse();
    }

    private void startEyedropperPulse() {
        if (eyedropperTimer != null) eyedropperTimer.stop();
        lastPulseUpdate = System.nanoTime();
        eyedropperTimer = new javafx.animation.AnimationTimer() {
            @Override
            public void handle(long now) {
                double elapsedSeconds = (now - lastPulseUpdate) / 1_000_000_000.0;
                lastPulseUpdate = now;
                
                eyedropperPulse += elapsedSeconds * 0.8; // Velocidad de crecimiento
                if (eyedropperPulse > 1.0) eyedropperPulse = 0.0;
                dibujarCanvas();
            }
        };
        eyedropperTimer.start();
    }

    private void stopEyedropperPulse() {
        if (eyedropperTimer != null) {
            eyedropperTimer.stop();
            eyedropperTimer = null;
        }
        eyedropperPulse = 0.0;
        dibujarCanvas();
    }
    
    // ===================== NOTIFICACIONES (PUENTE) =====================

    public void notifyCanvasChanged() { if (onCanvasChanged != null) onCanvasChanged.run(); }
    public void notifyElementSelected() { if (onElementSelected != null) onElementSelected.run(); }
    public void notifyElementTransformed() { if (onElementTransformed != null) onElementTransformed.run(); }
    public void notifyClientDataRequested() { if (onClientDataRequested != null) onClientDataRequested.run(); }

    // ===================== SETTERS / GETTERS =====================

    public void setElementoSeleccionado(Elemento e) { this.elementoSeleccionado = e; if (onElementSelected != null) onElementSelected.run(); }
    public Elemento getElementoSeleccionado() { return elementoSeleccionado; }
    public void setZoomLevel(double zoom) { this.zoomLevel = zoom; dibujarCanvas(); }
    public double getZoomLevel() { return zoomLevel; }
    public void setMostrarGuias(boolean m) { this.mostrarGuias = m; dibujarCanvas(); }
    public boolean isMostrarGuias() { return mostrarGuias; }
    public Proyecto getProyectoActual() { return proyectoActual; }
    public AppMode getCurrentMode() { return currentMode; }
    public void setFuenteDatos(FuenteDatos fd) { this.fuenteDatos = fd; dibujarCanvas(); }
    public void setOnElementSelected(Runnable r) { this.onElementSelected = r; }
    public void setOnCanvasChanged(Runnable r) { this.onCanvasChanged = r; }
    public void setOnElementTransformed(Runnable r) { this.onElementTransformed = r; }
    public void setOnClientDataRequested(Runnable r) { this.onClientDataRequested = r; }
    
    // Hitbox figurada para el botón del HUD
    public BoundingBox getBtnClienteHitbox() {
        if (hudOpacity < 0.95 || proyectoActual == null) return null;
        // La misma lógica de posición que en el Renderer para que coincida el clic
        double centroX = canvas.getWidth() / 2;
        // Simplificado para el ejemplo; en una implementación real se calcularía el ancho exacto del texto
        return new BoundingBox(centroX, 45, 120, 22);
    }
}
