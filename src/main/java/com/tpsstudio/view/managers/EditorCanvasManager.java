package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.*;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.List;

/**
 * Gestor del lienzo (Canvas) principal del editor.
 * 
 * Su trabajo es doble:
 * 1. Pintar todo: el fondo, las guías, sangrados y los elementos (texto/fotos).
 * 2. Controlar el ratón: detectar clics para seleccionar, mover y
 * redimensionar.
 * 
 * Antes esto estaba en MainViewController, pero lo sacamos aquí para que el
 * código respire.
 */
public class EditorCanvasManager {

    // Enum para saber qué estamos haciendo con el ratón (Mover, estirar de una
    // esquina...)
    private enum DragMode {
        NONE, MOVE, RESIZE_NW, RESIZE_NE, RESIZE_SW, RESIZE_SE, RESIZE_E // E usado solo para estirar texto a lo ancho
    }

    // El Lienzo de JavaFX donde pintamos
    private final Canvas canvas;

    // Constantes físicas de una tarjeta CR80 (tamaño tarjeta de crédito estándar)
    public static final double CR80_WIDTH_MM = 85.60;
    public static final double CR80_HEIGHT_MM = 53.98;
    public static final double SCALE = 4.0; // Factor de escala (4 pixeles = 1 mm para que se vea bien en pantalla)
    public static final double CARD_WIDTH = CR80_WIDTH_MM * SCALE; // ~342 px
    public static final double CARD_HEIGHT = CR80_HEIGHT_MM * SCALE; // ~216 px
    public static final double SAFETY_MARGIN = 3.0 * SCALE; // Margen de seguridad interno (3mm)
    public static final double BLEED_MARGIN = 2.0 * SCALE; // Sangrado externo para corte (2mm)
    // Dimensiones totales contando el sangrado extra
    public static final double CARD_WITH_BLEED_WIDTH = (CR80_WIDTH_MM + 4.0) * SCALE;
    public static final double CARD_WITH_BLEED_HEIGHT = (CR80_HEIGHT_MM + 4.0) * SCALE;
    public static final double HANDLE_SIZE = 8.0; // Tamaño de los cuadraditos para redimensionar

    // Estado externo (lo que nos "pasan" desde fuera)
    private Proyecto proyectoActual;
    private Elemento elementoSeleccionado;
    private double zoomLevel;
    private boolean mostrarGuias;
    private AppMode currentMode;

    // Estado interno para controlar el arrastre (Drag & Drop)
    private DragMode currentDragMode = DragMode.NONE;
    private double dragStartX, dragStartY;
    // Guardamos posición inicial del elemento antes de moverlo para calcular el
    // desplazamiento delta
    private double elementStartX, elementStartY, elementStartW, elementStartH;

    // Callbacks para avisar al Controlador principal de cambios
    private Runnable onElementSelected;
    private Runnable onCanvasChanged;

    /**
     * Constructor
     */
    public EditorCanvasManager(Canvas canvas) {
        this.canvas = canvas;
        this.zoomLevel = 1.3; // Zoom inicial cómodo
        this.mostrarGuias = false;
        this.currentMode = AppMode.PRODUCTION;
        setupMouseHandlers();
    }

    // ========== SETTERS (Para recibir datos de fuera) ==========

    public void setProyectoActual(Proyecto proyecto) {
        this.proyectoActual = proyecto;
    }

    public void setElementoSeleccionado(Elemento elemento) {
        this.elementoSeleccionado = elemento;
    }

    public void setZoomLevel(double zoom) {
        this.zoomLevel = zoom;
    }

    public void setMostrarGuias(boolean mostrar) {
        this.mostrarGuias = mostrar;
    }

    public void setCurrentMode(AppMode mode) {
        this.currentMode = mode;
    }

    // ========== CALLBACKS (Para avisar hacia fuera) ==========

    public void setOnElementSelected(Runnable callback) {
        this.onElementSelected = callback;
    }

    public void setOnCanvasChanged(Runnable callback) {
        this.onCanvasChanged = callback;
    }

    // ========== CONFIGURACIÓN DEL RATÓN ==========

    public void setupMouseHandlers() {
        canvas.setOnMousePressed(this::onCanvasMousePressed);
        canvas.setOnMouseDragged(this::onCanvasMouseDragged);
        canvas.setOnMouseMoved(this::onCanvasMouseMoved);
        canvas.setOnMouseReleased(this::onCanvasMouseReleased);
    }

    // ========== RENDERIZADO (PINTAR) ==========

    public void dibujarCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        // 1. Limpiar todo el lienzo start
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (proyectoActual == null) {
            gc.setFill(Color.web("#9a9598"));
            gc.fillText("Seleccione un proyecto o cree uno nuevo",
                    canvas.getWidth() / 2 - 120, canvas.getHeight() / 2);
            return;
        }

        // Calcular coordenadas para centrar la tarjeta en el canvas
        double scaledWidth = CARD_WIDTH * zoomLevel;
        double scaledHeight = CARD_HEIGHT * zoomLevel;
        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        double cardX = centerX - (scaledWidth / 2);
        double cardY = centerY - (scaledHeight / 2);

        gc.save();

        // 2) Zona de Sangrado (Bleed) - Línea roja discontinua externa
        if (mostrarGuias) {
            double bleedScaled = BLEED_MARGIN * zoomLevel;
            gc.setStroke(Color.web("#d48a8a"));
            gc.setLineWidth(1);
            gc.setLineDashes(5, 5);
            gc.strokeRect(cardX - bleedScaled, cardY - bleedScaled,
                    scaledWidth + (bleedScaled * 2), scaledHeight + (bleedScaled * 2));
        }

        // 3) Fondo / Imagen de la tarjeta
        ImagenFondoElemento fondo = proyectoActual.getFondoActual();
        if (fondo != null && fondo.getImagen() != null) {
            double fx = cardX + (fondo.getX() * zoomLevel);
            double fy = cardY + (fondo.getY() * zoomLevel);
            double fw = fondo.getWidth() * zoomLevel;
            double fh = fondo.getHeight() * zoomLevel;
            gc.drawImage(fondo.getImagen(), fx, fy, fw, fh);
        } else {
            // Si no hay fondo, pintamos blanco
            gc.setFill(Color.WHITE);
            gc.fillRect(cardX, cardY, scaledWidth, scaledHeight);
        }

        // 4) Borde físico de la tarjeta CR80 (Gris suave)
        gc.setStroke(Color.web("#c4c0c2"));
        gc.setLineWidth(1);
        gc.setLineDashes();
        gc.strokeRect(cardX, cardY, scaledWidth, scaledHeight);

        // 5) Margen de Seguridad (Safety) - Verde discontinuo interno
        if (mostrarGuias) {
            double safetyScaled = SAFETY_MARGIN * zoomLevel;
            gc.setStroke(Color.web("#4a9b7c"));
            gc.setLineDashes(3, 3);
            gc.strokeRect(cardX + safetyScaled, cardY + safetyScaled,
                    scaledWidth - (safetyScaled * 2), scaledHeight - (safetyScaled * 2));
        }

        // 6) Pintar Elementos (Capas superiores)
        List<Elemento> elementos = proyectoActual.getElementosActuales();
        for (Elemento elem : elementos) {
            if (!elem.isVisible())
                continue;

            double ex = cardX + (elem.getX() * zoomLevel);
            double ey = cardY + (elem.getY() * zoomLevel);
            double ew = elem.getWidth() * zoomLevel;
            double eh = elem.getHeight() * zoomLevel;

            if (elem instanceof TextoElemento texto) {
                // Renderizado de Texto
                gc.setFill(Color.web(texto.getColor()));

                var weight = texto.isNegrita() ? javafx.scene.text.FontWeight.BOLD
                        : javafx.scene.text.FontWeight.NORMAL;
                var posture = texto.isCursiva() ? javafx.scene.text.FontPosture.ITALIC
                        : javafx.scene.text.FontPosture.REGULAR;

                gc.setFont(Font.font(texto.getFontFamily(), weight, posture, texto.getFontSize() * zoomLevel));

                // Calcular alineación (Izquierda, Centro, Derecha)
                double textX = ex;
                javafx.scene.text.Text tempText = new javafx.scene.text.Text(texto.getContenido());
                tempText.setFont(gc.getFont());
                double textWidth = tempText.getLayoutBounds().getWidth();

                if (texto.getAlineacion().equals("CENTER")) {
                    textX = ex + (ew - textWidth) / 2;
                } else if (texto.getAlineacion().equals("RIGHT")) {
                    textX = ex + ew - textWidth;
                }

                gc.fillText(texto.getContenido(), textX, ey + (texto.getFontSize() * zoomLevel));

            } else if (elem instanceof ImagenElemento imgElem) {
                // Renderizado de Imágenes flotantes
                Image img = imgElem.getImagen();
                if (img != null) {
                    gc.setGlobalAlpha(imgElem.getOpacity());
                    gc.drawImage(img, ex, ey, ew, eh);
                    gc.setGlobalAlpha(1.0); // Restaurar opacidad
                }
            }

            // 7. Si está seleccionado, dibujar marco y tiradores (handles)
            if (elementoSeleccionado != null && elem == elementoSeleccionado) {
                // Cuadro de selección - verde azulado vibrante con línea discontinua
                gc.setStroke(Color.web("#4a9b7c"));
                gc.setLineWidth(2);
                gc.setLineDashes(3, 3);
                gc.strokeRect(ex - 1, ey - 1, ew + 2, eh + 2);

                // Tiradores (handles) para redimensionar
                double dim = HANDLE_SIZE;
                gc.setLineDashes(); // Sin discontinuidad para handles

                if (elem instanceof TextoElemento) {
                    // Texto: Solo tirador derecho (para ensanchar caja)
                    gc.setGlobalAlpha(0.8);
                    gc.setFill(Color.WHITE);
                    gc.fillRect(ex + ew - (dim / 2), ey + (eh / 2) - (dim / 2), dim, dim);
                    gc.setGlobalAlpha(1.0);

                    gc.setStroke(Color.web("#4a9b7c"));
                    gc.setLineWidth(2);
                    gc.strokeRect(ex + ew - (dim / 2), ey + (eh / 2) - (dim / 2), dim, dim);
                } else {
                    // Imagen: 4 esquinas
                    gc.setGlobalAlpha(0.8);
                    gc.setFill(Color.WHITE);

                    // NW (Arriba-Izq)
                    gc.fillRect(ex - (dim / 2), ey - (dim / 2), dim, dim);
                    // NE (Arriba-Der)
                    gc.fillRect(ex + ew - (dim / 2), ey - (dim / 2), dim, dim);
                    // SW (Abajo-Izq)
                    gc.fillRect(ex - (dim / 2), ey + eh - (dim / 2), dim, dim);
                    // SE (Abajo-Der)
                    gc.fillRect(ex + ew - (dim / 2), ey + eh - (dim / 2), dim, dim);

                    gc.setGlobalAlpha(1.0);

                    // Bordes de los handles
                    gc.setStroke(Color.web("#4a9b7c"));
                    gc.setLineWidth(2);
                    gc.strokeRect(ex - (dim / 2), ey - (dim / 2), dim, dim);
                    gc.strokeRect(ex + ew - (dim / 2), ey - (dim / 2), dim, dim);
                    gc.strokeRect(ex - (dim / 2), ey + eh - (dim / 2), dim, dim);
                    gc.strokeRect(ex + ew - (dim / 2), ey + eh - (dim / 2), dim, dim);
                }
            }
        }

        // 8) Texto informativo inferior
        gc.setLineDashes();

        String lado = proyectoActual.isMostrandoFrente() ? "FRENTE" : "DORSO";
        gc.setFill(Color.web("#e8e6e7"));
        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 14));
        gc.fillText(lado, cardX, cardY - 25);

        gc.setFill(Color.web("#9a9598"));
        gc.setFont(Font.font("Arial", 11));
        String infoDimensiones = String.format(
                "CR80: %.2f × %.2f mm | Con sangre: %.2f × %.2f mm",
                CR80_WIDTH_MM, CR80_HEIGHT_MM, CR80_WIDTH_MM + 4.0, CR80_HEIGHT_MM + 4.0);

        double bleedScaled = BLEED_MARGIN * zoomLevel;
        gc.fillText(infoDimensiones, cardX + scaledWidth - 380, cardY + scaledHeight + bleedScaled + 20);

        gc.restore();
    }

    // ========== EVENTOS DE RATÓN (LÓGICA) ==========

    private void onCanvasMousePressed(MouseEvent e) {
        if (proyectoActual == null || currentMode != AppMode.DESIGN)
            return;

        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        double scaledWidth = CARD_WIDTH * zoomLevel;
        double scaledHeight = CARD_HEIGHT * zoomLevel;
        double cardX = centerX - (scaledWidth / 2);
        double cardY = centerY - (scaledHeight / 2);

        // 1. ¿Hemos clicado en un tirador (handle) de un elemento ya seleccionado?
        if (elementoSeleccionado != null) {
            DragMode mode = getDragModeForMouseEvent(e, elementoSeleccionado, cardX, cardY);
            if (mode != DragMode.NONE) {
                // Sí, iniciamos modo redimensionar
                currentDragMode = mode;
                dragStartX = e.getX();
                dragStartY = e.getY();
                elementStartX = elementoSeleccionado.getX();
                elementStartY = elementoSeleccionado.getY();
                elementStartW = elementoSeleccionado.getWidth();
                elementStartH = elementoSeleccionado.getHeight();
                canvas.requestFocus();
                return; // Consumido, no buscamos seleccionar otro
            }
        }

        // 2. Si no es resize, ¿estamos clicando encima de algún elemento para
        // seleccionarlo?
        double relX = (e.getX() - cardX) / zoomLevel;
        double relY = (e.getY() - cardY) / zoomLevel;

        Elemento nuevoSeleccionado = null;
        // Iteramos al revés para seleccionar el que esté más "arriba" (pintado al
        // final)
        for (int i = proyectoActual.getElementosActuales().size() - 1; i >= 0; i--) {
            Elemento elem = proyectoActual.getElementosActuales().get(i);
            if (elem.contains(relX, relY)) {
                nuevoSeleccionado = elem;
                break;
            }
        }

        if (nuevoSeleccionado != null) {
            elementoSeleccionado = nuevoSeleccionado;

            // Avisar selección
            if (onElementSelected != null) {
                onElementSelected.run();
            }

            // Preparar para mover
            currentDragMode = DragMode.MOVE;
            dragStartX = e.getX();
            dragStartY = e.getY();
            elementStartX = elementoSeleccionado.getX();
            elementStartY = elementoSeleccionado.getY();

            // Avisar para rebuild de paneles
            if (onCanvasChanged != null) {
                onCanvasChanged.run();
            }

            dibujarCanvas();
        } else {
            // Clic en vacío -> Deseleccionar
            if (elementoSeleccionado != null) {
                elementoSeleccionado = null;

                if (onCanvasChanged != null) {
                    onCanvasChanged.run();
                }

                dibujarCanvas();
            }
            currentDragMode = DragMode.NONE;
        }

        canvas.requestFocus();
    }

    private void onCanvasMouseDragged(MouseEvent e) {
        if (elementoSeleccionado == null || currentDragMode == DragMode.NONE || elementoSeleccionado.isLocked())
            return;

        double dx = (e.getX() - dragStartX) / zoomLevel;
        double dy = (e.getY() - dragStartY) / zoomLevel;

        if (currentDragMode == DragMode.MOVE) {
            // Mover elemento
            elementoSeleccionado.setX(elementStartX + dx);
            elementoSeleccionado.setY(elementStartY + dy);
        } else {
            // Redimensionar elemento
            double newW = elementStartW;
            double newH = elementStartH;
            double newX = elementStartX;
            double newY = elementStartY;

            // Variables para mantener proporción
            boolean keepProportion = false;
            double ratio = 1.0;
            if (elementoSeleccionado instanceof ImagenElemento
                    && ((ImagenElemento) elementoSeleccionado).isMantenerProporcion()) {
                keepProportion = true;
                if (elementStartH > 0)
                    ratio = elementStartW / elementStartH;
            }

            // Calcular nuevo tamaño según de qué esquina tiramos
            switch (currentDragMode) {
                case RESIZE_E: // Solo ancho (Texto)
                    newW = elementStartW + dx;
                    break;
                case RESIZE_SE:
                    newW = elementStartW + dx;
                    newH = elementStartH + dy;
                    if (keepProportion)
                        newH = newW / ratio;
                    break;
                case RESIZE_SW:
                    newW = elementStartW - dx;
                    newH = elementStartH + dy;
                    if (keepProportion)
                        newH = newW / ratio;
                    newX = elementStartX + (elementStartW - newW);
                    break;
                case RESIZE_NE:
                    newW = elementStartW + dx;
                    newH = elementStartH - dy;
                    if (keepProportion)
                        newH = newW / ratio;
                    newY = elementStartY + (elementStartH - newH);
                    break;
                case RESIZE_NW:
                    newW = elementStartW - dx;
                    newH = elementStartH - dy;
                    if (keepProportion)
                        newH = newW / ratio;
                    newX = elementStartX + (elementStartW - newW);
                    newY = elementStartY + (elementStartH - newH);
                    break;
                default:
                    break;
            }

            // Evitar que el elemento desaparezca (mínimo 10px)
            if (newW < 10) {
                newW = 10;
                // Corregir posición si tiramos de la izquierda
                if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) {
                    newX = elementStartX + (elementStartW - newW);
                }
                if (keepProportion)
                    newH = newW / ratio;
            }
            if (newH < 10) {
                newH = 10;
                if (keepProportion) {
                    newW = newH * ratio;
                    if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) {
                        newX = elementStartX + (elementStartW - newW);
                    }
                }
                // Corregir posición si tiramos de arriba
                if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_NE) {
                    newY = elementStartY + (elementStartH - newH);
                }
            }

            elementoSeleccionado.setWidth(newW);
            elementoSeleccionado.setHeight(newH);

            // Actualizar posición (solo necesario si tiramos de izquierda o arriba)
            if (currentDragMode == DragMode.RESIZE_SW ||
                    currentDragMode == DragMode.RESIZE_NW ||
                    currentDragMode == DragMode.RESIZE_NE) {

                if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW)
                    elementoSeleccionado.setX(newX);
                if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_NE)
                    elementoSeleccionado.setY(newY);
            }

            // Notificar cambio (para que se actualice el panel de propiedades)
            if (onCanvasChanged != null) {
                onCanvasChanged.run();
            }
        }

        dibujarCanvas();
    }

    private void onCanvasMouseMoved(MouseEvent e) {
        if (proyectoActual == null || elementoSeleccionado == null) {
            canvas.setCursor(Cursor.DEFAULT);
            return;
        }

        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        double scaledWidth = CARD_WIDTH * zoomLevel;
        double scaledHeight = CARD_HEIGHT * zoomLevel;
        double cardX = centerX - (scaledWidth / 2);
        double cardY = centerY - (scaledHeight / 2);

        // Cambiar el cursor del ratón según por donde pase
        DragMode mode = getDragModeForMouseEvent(e, elementoSeleccionado, cardX, cardY);

        switch (mode) {
            case RESIZE_NW:
                canvas.setCursor(Cursor.NW_RESIZE);
                break;
            case RESIZE_NE:
                canvas.setCursor(Cursor.NE_RESIZE);
                break;
            case RESIZE_SW:
                canvas.setCursor(Cursor.SW_RESIZE);
                break;
            case RESIZE_SE:
                canvas.setCursor(Cursor.SE_RESIZE);
                break;
            case RESIZE_E:
                canvas.setCursor(Cursor.E_RESIZE);
                break;
            default:
                canvas.setCursor(Cursor.DEFAULT);
                break;
        }
    }

    private void onCanvasMouseReleased(MouseEvent e) {
        // Nada especial por ahora al soltar clic
    }

    // ========== MÉTODOS AUXILIARES ==========

    private DragMode getDragModeForMouseEvent(MouseEvent e, Elemento elem, double cardX, double cardY) {
        double mx = e.getX();
        double my = e.getY();

        double ex = cardX + (elem.getX() * zoomLevel);
        double ey = cardY + (elem.getY() * zoomLevel);
        double ew = elem.getWidth() * zoomLevel;
        double eh = elem.getHeight() * zoomLevel;

        // Hitbox un poco más grande (handle + 4px) para facilitar clic
        double hit = HANDLE_SIZE + 4;

        if (elem instanceof TextoElemento) {
            // Solo handle derecho/centro para texto
            double hx = ex + ew;
            double hy = ey + (eh / 2);
            if (Math.abs(mx - hx) <= hit && Math.abs(my - hy) <= hit) {
                return DragMode.RESIZE_E;
            }
        } else {
            // Esquinas para imágenes
            // NW (Arriba-Izq)
            if (Math.abs(mx - ex) <= hit && Math.abs(my - ey) <= hit)
                return DragMode.RESIZE_NW;
            // NE (Arriba-Der)
            if (Math.abs(mx - (ex + ew)) <= hit && Math.abs(my - ey) <= hit)
                return DragMode.RESIZE_NE;
            // SW (Abajo-Izq)
            if (Math.abs(mx - ex) <= hit && Math.abs(my - (ey + eh)) <= hit)
                return DragMode.RESIZE_SW;
            // SE (Abajo-Der)
            if (Math.abs(mx - (ex + ew)) <= hit && Math.abs(my - (ey + eh)) <= hit)
                return DragMode.RESIZE_SE;
        }
        return DragMode.NONE;
    }

    // ========== GETTERS ==========

    public Elemento getElementoSeleccionado() {
        return elementoSeleccionado;
    }
}
