package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.util.TPSToast;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.animation.PauseTransition;
import javafx.geometry.BoundingBox;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import java.util.function.Consumer;

/**
 * Gestor de la interacción del usuario con el canvas.
 * Maneja eventos de ratón, selección, drag & drop y redimensionado.
 */
public class CanvasInteractionHandler {

    private enum DragMode { NONE, MOVE, RESIZE_NW, RESIZE_NE, RESIZE_SW, RESIZE_SE, RESIZE_E }

    private final Canvas canvas;
    private final EditorCanvasManager manager;

    // Estado interno de drag
    private DragMode currentDragMode = DragMode.NONE;
    private double dragStartX, dragStartY;
    private double elementStartX, elementStartY, elementStartW, elementStartH;
    private boolean wasDragged = false;

    // Tooltips UX
    private final Tooltip guideTooltip;
    private final PauseTransition tooltipDelay;
    private String currentTooltipTarget = null;
    private double lastScreenX, lastScreenY;

    // Cuentagotas
    private boolean eyedropperActive = false;
    private Consumer<Color> onColorPickedCallback;

    public CanvasInteractionHandler(Canvas canvas, EditorCanvasManager manager) {
        this.canvas = canvas;
        this.manager = manager;

        this.guideTooltip = new Tooltip();
        this.guideTooltip.getStyleClass().add("canvas-guide-tooltip");
        this.tooltipDelay = new PauseTransition(Duration.millis(400));
        this.tooltipDelay.setOnFinished(ev -> {
            if (currentTooltipTarget != null) guideTooltip.show(canvas, lastScreenX + 15, lastScreenY + 15);
        });

        setupHandlers();
    }

    private void setupHandlers() {
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseMoved(this::handleMouseMoved);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseExited(e -> { tooltipDelay.stop(); guideTooltip.hide(); });
    }

    private void handleMousePressed(MouseEvent e) {
        if (eyedropperActive) { pickColorAt(e.getX(), e.getY()); return; }
        
        wasDragged = false;
        Proyecto p = manager.getProyectoActual();
        if (p == null) return;

        // Hitbox del botón del HUD (Editar datos)
        BoundingBox btnHud = manager.getBtnClienteHitbox();
        if (btnHud != null && btnHud.contains(e.getX(), e.getY())) {
            manager.notifyClientDataRequested();
            return;
        }

        if (manager.getCurrentMode() != AppMode.DESIGN) return;

        double cardX = manager.getCardX();
        double cardY = manager.getCardY();
        double zoom = manager.getZoomLevel();

        // 1. Mirar si pulsamos un handle de redimensionado del elemento seleccionado
        Elemento sel = manager.getElementoSeleccionado();
        if (sel != null) {
            DragMode mode = getDragMode(e, sel, cardX, cardY, zoom);
            if (mode != DragMode.NONE) {
                initDrag(e, mode, sel);
                canvas.requestFocus();
                return;
            }
        }

        // 2. Intentar seleccionar un nuevo elemento
        double relX = (e.getX() - cardX) / zoom;
        double relY = (e.getY() - cardY) / zoom;
        Elemento hit = null;
        for (int i = p.getElementosActuales().size() - 1; i >= 0; i--) {
            Elemento elem = p.getElementosActuales().get(i);
            if (elem.contains(relX, relY)) { hit = elem; break; }
        }

        if (hit != null) {
            manager.setElementoSeleccionado(hit);
            initDrag(e, DragMode.MOVE, hit);
        } else {
            manager.setElementoSeleccionado(null);
            currentDragMode = DragMode.NONE;
        }
        manager.dibujarCanvas();
        canvas.requestFocus();
    }

    private void handleMouseDragged(MouseEvent e) {
        Elemento sel = manager.getElementoSeleccionado();
        if (sel == null || currentDragMode == DragMode.NONE || sel.isLocked()) return;
        
        wasDragged = true;
        double zoom = manager.getZoomLevel();
        double dx = (e.getX() - dragStartX) / zoom;
        double dy = (e.getY() - dragStartY) / zoom;

        if (currentDragMode == DragMode.MOVE) {
            sel.setX(elementStartX + dx);
            sel.setY(elementStartY + dy);
        } else {
            handleResize(sel, dx, dy);
        }
        
        manager.notifyElementTransformed();
        manager.dibujarCanvas();
    }

    private void handleMouseMoved(MouseEvent e) {
        lastScreenX = e.getScreenX(); lastScreenY = e.getScreenY();
        gestionarHoverGuias(e);

        if (eyedropperActive) {
            manager.setMousePosition(e.getX(), e.getY());
            return;
        }

        if (manager.getProyectoActual() == null || manager.getCurrentMode() != AppMode.DESIGN) {
            canvas.setCursor(Cursor.DEFAULT); return;
        }

        Elemento sel = manager.getElementoSeleccionado();
        if (sel == null || sel.isLocked()) { canvas.setCursor(Cursor.DEFAULT); return; }

        DragMode mode = getDragMode(e, sel, manager.getCardX(), manager.getCardY(), manager.getZoomLevel());
        canvas.setCursor(getCursorForMode(mode));
    }

    private void handleMouseReleased(MouseEvent e) {
        if (wasDragged) manager.notifyCanvasChanged();
        currentDragMode = DragMode.NONE;
        wasDragged = false;
    }

    private void initDrag(MouseEvent e, DragMode mode, Elemento el) {
        currentDragMode = mode;
        dragStartX = e.getX(); dragStartY = e.getY();
        elementStartX = el.getX(); elementStartY = el.getY();
        elementStartW = el.getWidth(); elementStartH = el.getHeight();
    }

    private void handleResize(Elemento sel, double dx, double dy) {
        double newW = elementStartW, newH = elementStartH, newX = elementStartX, newY = elementStartY;
        boolean keepProp = (sel instanceof ImagenElemento && ((ImagenElemento) sel).isMantenerProporcion());
        double ratio = (elementStartH > 0) ? elementStartW / elementStartH : 1.0;

        switch (currentDragMode) {
            case RESIZE_E -> newW = elementStartW + dx;
            case RESIZE_SE -> { newW = elementStartW + dx; newH = elementStartH + dy; if (keepProp) newH = newW / ratio; }
            case RESIZE_SW -> { newW = elementStartW - dx; newH = elementStartH + dy; if (keepProp) newH = newW / ratio; newX = elementStartX + (elementStartW - newW); }
            case RESIZE_NE -> { newW = elementStartW + dx; newH = elementStartH - dy; if (keepProp) newH = newW / ratio; newY = elementStartY + (elementStartH - newH); }
            case RESIZE_NW -> { newW = elementStartW - dx; newH = elementStartH - dy; if (keepProp) newH = newW / ratio; newX = elementStartX + (elementStartW - newW); newY = elementStartY + (elementStartH - newH); }
        }

        // Límites mínimos
        if (newW < 10) { newW = 10; if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) newX = elementStartX + (elementStartW - 10); if (keepProp) newH = 10 / ratio; }
        if (newH < 10) { newH = 10; if (keepProp) { newW = 10 * ratio; if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) newX = elementStartX + (elementStartW - newW); } if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_NE) newY = elementStartY + (elementStartH - 10); }

        sel.setWidth(newW); sel.setHeight(newH);
        if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) sel.setX(newX);
        if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_NE) sel.setY(newY);
    }

    private DragMode getDragMode(MouseEvent e, Elemento el, double cardX, double cardY, double zoom) {
        double mx = e.getX(), my = e.getY();
        double ex = cardX + (el.getX() * zoom), ey = cardY + (el.getY() * zoom);
        double ew = el.getWidth() * zoom, eh = el.getHeight() * zoom;
        double hit = EditorCanvasManager.HANDLE_SIZE + 4;

        if (el instanceof TextoElemento) {
            if (Math.abs(mx - (ex + ew)) <= hit && Math.abs(my - (ey + eh/2)) <= hit) return DragMode.RESIZE_E;
            return DragMode.NONE;
        }
        if (Math.abs(mx - ex) <= hit && Math.abs(my - ey) <= hit) return DragMode.RESIZE_NW;
        if (Math.abs(mx - (ex + ew)) <= hit && Math.abs(my - ey) <= hit) return DragMode.RESIZE_NE;
        if (Math.abs(mx - ex) <= hit && Math.abs(my - (ey + eh)) <= hit) return DragMode.RESIZE_SW;
        if (Math.abs(mx - (ex + ew)) <= hit && Math.abs(my - (ey + eh)) <= hit) return DragMode.RESIZE_SE;
        return DragMode.NONE;
    }

    private Cursor getCursorForMode(DragMode mode) {
        return switch (mode) {
            case RESIZE_NW -> Cursor.NW_RESIZE; case RESIZE_NE -> Cursor.NE_RESIZE;
            case RESIZE_SW -> Cursor.SW_RESIZE; case RESIZE_SE -> Cursor.SE_RESIZE;
            case RESIZE_E -> Cursor.E_RESIZE; default -> Cursor.DEFAULT;
        };
    }

    private void gestionarHoverGuias(MouseEvent e) {
        if (!manager.isMostrarGuias() || manager.getCurrentMode() != AppMode.DESIGN || currentDragMode != DragMode.NONE) {
            currentTooltipTarget = null; tooltipDelay.stop(); guideTooltip.hide(); return;
        }
        double zoom = manager.getZoomLevel();
        double sw = EditorCanvasManager.getScaledWidth(manager.getProyectoActual()) * zoom;
        double sh = EditorCanvasManager.getScaledHeight(manager.getProyectoActual()) * zoom;
        double cardX = manager.getCardX(), cardY = manager.getCardY();
        double mx = e.getX(), my = e.getY(), hit = 4.0;
        double bleed = EditorCanvasManager.BLEED_MARGIN * zoom, safety = EditorCanvasManager.SAFETY_MARGIN * zoom;

        String hover = null;
        if (isNear(mx, my, cardX - bleed, cardY - bleed, sw + bleed * 2, sh + bleed * 2, hit)) hover = "Zona de sangrado (área que será recortada)";
        else if (isNear(mx, my, cardX, cardY, sw, sh, hit)) hover = "Corte final";
        else if (isNear(mx, my, cardX + safety, cardY + safety, sw - safety * 2, sh - safety * 2, hit)) hover = "Margen de seguridad";

        if (hover != null) {
            if (!hover.equals(currentTooltipTarget)) { currentTooltipTarget = hover; guideTooltip.setText(hover); tooltipDelay.playFromStart(); }
        } else { currentTooltipTarget = null; tooltipDelay.stop(); guideTooltip.hide(); }
    }

    private boolean isNear(double mx, double my, double rx, double ry, double rw, double rh, double e) {
        boolean L = Math.abs(mx - rx) <= e && my >= ry - e && my <= ry + rh + e;
        boolean R = Math.abs(mx - (rx + rw)) <= e && my >= ry - e && my <= ry + rh + e;
        boolean T = Math.abs(my - ry) <= e && mx >= rx - e && mx <= rx + rw + e;
        boolean B = Math.abs(my - (ry + rh)) <= e && mx >= rx - e && mx <= rx + rw + e;
        return L || R || T || B;
    }

    public void activateEyedropper(Consumer<Color> callback) {
        this.eyedropperActive = true; this.onColorPickedCallback = callback;
        canvas.setCursor(Cursor.CROSSHAIR);
        if (canvas.getScene() != null) TPSToast.mostrar(canvas.getScene().getWindow(), "Modo Cuentagotas Activado", "Haz clic en un color para capturarlo", TPSToast.Tipo.EXITO);
    }

    public void deactivateEyedropper() {
        this.eyedropperActive = false;
        canvas.setCursor(Cursor.DEFAULT);
    }

    public boolean isEyedropperActive() {
        return eyedropperActive;
    }

    private void pickColorAt(double x, double y) {
        javafx.scene.image.Image snap = canvas.snapshot(null, null);
        javafx.scene.image.PixelReader pr = snap.getPixelReader();
        int ix = (int) Math.max(0, Math.min(snap.getWidth() - 1, x));
        int iy = (int) Math.max(0, Math.min(snap.getHeight() - 1, y));
        if (onColorPickedCallback != null) onColorPickedCallback.accept(pr.getColor(ix, iy));
        manager.deactivateEyedropper();
    }
}
