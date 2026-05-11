package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ElementoCodigo;
import com.tpsstudio.model.elements.FormaElemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.util.AnimationHelper;
import com.tpsstudio.util.ImageUtils;
import com.tpsstudio.util.TextUtils;
import com.tpsstudio.util.TPSToast;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javafx.scene.control.Tooltip;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Gestor del canvas de edición de tarjetas CR80.
 *
 * <p>Encapsula toda la lógica de renderizado y de interacción con el ratón
 * sobre el {@link javafx.scene.canvas.Canvas} central de la aplicación.
 * Este manager fue extraído de {@link com.tpsstudio.view.controllers.MainViewController}
 * para cumplir el principio de responsabilidad única (SRP).</p>
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li><b>Renderizado:</b> dibuja el fondo de tarjeta, guías de sangrado,
 *       elementos gráficos (texto, imagen) y la cabecera de información de proyecto.</li>
 *   <li><b>Interacción:</b> gestiona selección, arrastre y redimensionado de elementos
 *       mediante eventos de ratón ({@code onMousePressed}, {@code onMouseDragged}, etc.).</li>
 *   <li><b>HUD contextual:</b> muestra información de proyecto/cliente en modo Producción,
 *       con animación de opacidad para no interferir con el trabajo de diseño.</li>
 * </ul>
 *
 * <p><b>Constantes de referencia:</b><br/>
 * Las constantes públicas {@link #CARD_WIDTH}, {@link #CARD_HEIGHT},
 * {@link #CR80_WIDTH_MM}, {@link #CR80_HEIGHT_MM} y {@link #BLEED_MARGIN}
 * representan las dimensiones estándar de la tarjeta CR80 en px y mm,
 * y son compartidas con {@link com.tpsstudio.service.PDFExportService} para
 * garantizar coherencia entre vista y exportación.</p>
 *
 * <p><b>Callbacks:</b><br/>
 * Se comunica con el controlador principal mediante callbacks inyectados
 * ({@link #setOnElementSelected(Runnable)}, {@link #setOnCanvasChanged(Runnable)})
 * manteniendo el desacoplamiento con la capa de vista.</p>
 *
 * @see com.tpsstudio.view.controllers.MainViewController
 * @see com.tpsstudio.service.PDFExportService
 * @see com.tpsstudio.model.project.Proyecto
 */
public class EditorCanvasManager {

    /* Imágen de silueta para el placeholder. Se carga una vez desde recursos. */
    private static Image imagenSilueta = null;

    static {
        try (var stream = EditorCanvasManager.class.getResourceAsStream("/img/silueta.png")) {
            if (stream != null) {
                imagenSilueta = new Image(stream);
            }
        } catch (Exception ignored) {
            // sin silueta personalizada: se usará el rectángulo gris
        }
    }

    /* Qué estamos haciendo durante el drag.
     * En texto solo se permite estirar el ancho (E). */

    private enum DragMode {
        NONE, MOVE, RESIZE_NW, RESIZE_NE, RESIZE_SW, RESIZE_SE, RESIZE_E
    }

    private final Canvas canvas;

    // Medidas CR80 (mm) y conversión a pixels
    public static final double CR80_WIDTH_MM = 85.60;
    public static final double CR80_HEIGHT_MM = 53.98;
    public static final double SCALE = 4.0; // 1mm => 4px (aprox)
    public static final double CARD_WIDTH = CR80_WIDTH_MM * SCALE;
    public static final double CARD_HEIGHT = CR80_HEIGHT_MM * SCALE;

    // Guías
    public static final double SAFETY_MARGIN = 3.0 * SCALE; // 3mm
    public static final double BLEED_MARGIN = 2.0 * SCALE;  // 2mm

    public static final double HANDLE_SIZE = 8.0;

    private double getCardWidth() {
        if (proyectoActual != null && proyectoActual.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) {
            return CARD_HEIGHT;
        }
        return CARD_WIDTH;
    }

    private double getCardHeight() {
        if (proyectoActual != null && proyectoActual.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) {
            return CARD_WIDTH;
        }
        return CARD_HEIGHT;
    }

    // Estado externo (lo controla el controlador principal)
    private Proyecto proyectoActual;
    private Elemento elementoSeleccionado;
    private double zoomLevel;
    private boolean mostrarGuias;
    private AppMode currentMode;
    private FuenteDatos fuenteDatos;

    // Estado interno de drag
    private DragMode currentDragMode = DragMode.NONE;
    private double dragStartX, dragStartY;
    private double elementStartX, elementStartY, elementStartW, elementStartH;
    private boolean wasDragged = false;

    // Callbacks (avisos hacia fuera)
    private Runnable onElementSelected;
    private Runnable onCanvasChanged;
    private Runnable onElementTransformed;
    private Runnable onClientDataRequested;

    // Campos figurados
    private javafx.geometry.BoundingBox btnClienteHitbox;
    
    // Tooltip UX
    private Tooltip guideTooltip;
    private PauseTransition tooltipDelay;
    private String currentTooltipTarget = null;
    private double lastScreenX, lastScreenY;

    // --- Estado del Cuentagotas ---
    private boolean eyedropperActive = false;
    private java.util.function.Consumer<Color> onColorPickedCallback;

    public void setOnClientDataRequested(Runnable callback) {
        this.onClientDataRequested = callback;
    }

    public EditorCanvasManager(Canvas canvas) {
        this.canvas = canvas;
        this.zoomLevel = 1.3;
        this.mostrarGuias = false;
        this.currentMode = AppMode.PRODUCTION;

        guideTooltip = new Tooltip();
        guideTooltip.getStyleClass().add("canvas-guide-tooltip");
        tooltipDelay = new PauseTransition(Duration.millis(400));
        tooltipDelay.setOnFinished(ev -> {
            if (currentTooltipTarget != null) {
                guideTooltip.show(canvas, lastScreenX + 15, lastScreenY + 15);
            }
        });

        setupMouseHandlers();
    }

    // ===================== SETTERS =====================

    public void setProyectoActual(Proyecto proyecto) {
        this.proyectoActual = proyecto;
        refrescarFondosTrasCarga();
    }

    /**
     * Fuerza a los fondos (frente y dorso) a recalcular sus dimensiones
     * basándose en la orientación actual del proyecto. 
     * Útil al cargar un proyecto o cambiar de orientación.
     */
    public void refrescarFondosTrasCarga() {
        if (proyectoActual == null) return;
        
        double w = getCardWidth();
        double h = getCardHeight();
        
        if (proyectoActual.getFondoFrente() != null) {
            proyectoActual.getFondoFrente().ajustarATamaño(w, h, BLEED_MARGIN);
        }
        if (proyectoActual.getFondoDorso() != null) {
            proyectoActual.getFondoDorso().ajustarATamaño(w, h, BLEED_MARGIN);
        }
    }

    public void setElementoSeleccionado(Elemento elemento) {
        this.elementoSeleccionado = elemento;
    }

    public void setZoomLevel(double zoom) {
        this.zoomLevel = zoom;
    }

    public void setFuenteDatos(com.tpsstudio.model.project.FuenteDatos fuenteDatos) {
        this.fuenteDatos = fuenteDatos;
    }

    public void setMostrarGuias(boolean mostrar) {
        this.mostrarGuias = mostrar;
    }

    private double hudOpacity = 0.0;
    private javafx.animation.Timeline hudFadeTimeline;

    public void setCurrentMode(AppMode mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            
            if (hudFadeTimeline != null) {
                hudFadeTimeline.stop();
            }
            
            double targetOpacity = (mode == AppMode.PRODUCTION) ? 1.0 : 0.0;
            
            hudFadeTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, 
                    new javafx.animation.KeyValue(hudOpacityProperty(), hudOpacity, javafx.animation.Interpolator.EASE_BOTH)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(AnimationHelper.DURATION_SLOW), 
                    new javafx.animation.KeyValue(hudOpacityProperty(), targetOpacity, javafx.animation.Interpolator.EASE_BOTH))
            );
            
            hudFadeTimeline.play();
        } else {
            // Inicialización directa si no hay cambio
            hudOpacity = (mode == AppMode.PRODUCTION) ? 1.0 : 0.0;
            if (hudOpacityProp != null) hudOpacityProp.set(hudOpacity);
        }
    }

    private javafx.beans.property.DoubleProperty hudOpacityProp;
    private javafx.beans.property.DoubleProperty hudOpacityProperty() {
        if (hudOpacityProp == null) {
            hudOpacityProp = new javafx.beans.property.SimpleDoubleProperty(hudOpacity);
            hudOpacityProp.addListener((obs, oldVal, newVal) -> {
                hudOpacity = newVal.doubleValue();
                dibujarCanvas();
            });
        }
        return hudOpacityProp;
    }

    // ===================== CALLBACKS =====================

    public void setOnElementSelected(Runnable callback) {
        this.onElementSelected = callback;
    }

    public void setOnCanvasChanged(Runnable callback) {
        this.onCanvasChanged = callback;
    }

    public void setOnElementTransformed(Runnable callback) {
        this.onElementTransformed = callback;
    }

    // ===================== RATÓN =====================

    public void setupMouseHandlers() {
        canvas.setOnMousePressed(this::onCanvasMousePressed);
        canvas.setOnMouseDragged(this::onCanvasMouseDragged);
        canvas.setOnMouseMoved(this::onCanvasMouseMoved);
        canvas.setOnMouseReleased(this::onCanvasMouseReleased);
        canvas.setOnMouseExited(e -> {
            tooltipDelay.stop();
            guideTooltip.hide();
        });
    }

    // ===================== DIBUJO =====================

    public void dibujarCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (proyectoActual == null) {
            gc.setFill(Color.web("#9a9598"));
            gc.setFont(Font.font("System", 16)); // Texto más grande y legible
            String msg = "Seleccione un proyecto o cree uno nuevo";
            
            // Centrado preciso
            javafx.scene.text.Text t = new javafx.scene.text.Text(msg);
            t.setFont(gc.getFont());
            double tw = t.getLayoutBounds().getWidth();
            
            gc.fillText(msg, (canvas.getWidth() - tw) / 2, canvas.getHeight() / 2);
            return;
        }

        // Centrar la tarjeta en el canvas
        double scaledWidth = getCardWidth() * zoomLevel;
        double scaledHeight = getCardHeight() * zoomLevel;
        double cardX = (canvas.getWidth() / 2) - (scaledWidth / 2);
        double cardY = (canvas.getHeight() / 2) - (scaledHeight / 2);

        gc.save();

        // 1) Guía de sangrado (exterior)
        if (mostrarGuias) {
            double bleedScaled = BLEED_MARGIN * zoomLevel;
            gc.setStroke(Color.web("#d48a8a"));
            gc.setLineWidth(1);
            gc.setLineDashes(5, 5);
            gc.strokeRect(cardX - bleedScaled, cardY - bleedScaled,
                    scaledWidth + (bleedScaled * 2), scaledHeight + (bleedScaled * 2));
        }

        // 2) Fondo (si existe) o blanco
        ImagenFondoElemento fondo = proyectoActual.getFondoActual();
        if (fondo != null && fondo.getImagen() != null) {
            double fx = cardX + (fondo.getX() * zoomLevel);
            double fy = cardY + (fondo.getY() * zoomLevel);
            double fw = fondo.getWidth() * zoomLevel;
            double fh = fondo.getHeight() * zoomLevel;
            gc.drawImage(fondo.getImagen(), fx, fy, fw, fh);
        } else {
            gc.setFill(Color.WHITE);
            gc.fillRect(cardX, cardY, scaledWidth, scaledHeight);
        }

        // 3) Borde de la tarjeta
        gc.setStroke(Color.web("#c4c0c2"));
        gc.setLineWidth(1);
        gc.setLineDashes();
        gc.strokeRect(cardX, cardY, scaledWidth, scaledHeight);

        // 4) Margen de seguridad (interior)
        if (mostrarGuias) {
            double safetyScaled = SAFETY_MARGIN * zoomLevel;
            gc.setStroke(Color.web("#4a9b7c"));
            gc.setLineDashes(3, 3);
            gc.strokeRect(cardX + safetyScaled, cardY + safetyScaled,
                    scaledWidth - (safetyScaled * 2), scaledHeight - (safetyScaled * 2));
        }

        // 5) Elementos
        List<Elemento> elementos = proyectoActual.getElementosActuales();
        for (Elemento elem : elementos) {
            if (!elem.isVisible()) continue;

            double ex = cardX + (elem.getX() * zoomLevel);
            double ey = cardY + (elem.getY() * zoomLevel);
            double ew = elem.getWidth() * zoomLevel;
            double eh = elem.getHeight() * zoomLevel;

            if (elem instanceof TextoElemento texto) {
                gc.setFill(Color.web(texto.getColor()));

                var weight = texto.isNegrita()
                        ? javafx.scene.text.FontWeight.BOLD
                        : javafx.scene.text.FontWeight.NORMAL;

                var posture = texto.isCursiva()
                        ? javafx.scene.text.FontPosture.ITALIC
                        : javafx.scene.text.FontPosture.REGULAR;

                gc.setFont(Font.font(texto.getFontFamily(), weight, posture, texto.getFontSize() * zoomLevel));

                // Si hay columna vinculada, mostrar el valor del registro actual
                String contenidoFinal = texto.getContenido();
                if (texto.getColumnaVinculada() != null && fuenteDatos != null) {
                    String valorVariable = fuenteDatos.getValor(texto.getColumnaVinculada());
                    if (valorVariable != null) contenidoFinal = valorVariable;
                }

                // =======================================================
                // Lógica de Renderizado y Auto-ajuste
                // =======================================================
                double effectiveFontSize = texto.getFontSize();
                String renderText = contenidoFinal != null ? contenidoFinal : "";

                // Si el auto-ajuste está activo y NO hay salto de línea, calculamos el escalado visual
                if (texto.isAutoAjustar() && !texto.isSaltoLinea()) {
                    javafx.scene.text.Text helper = new javafx.scene.text.Text(renderText);
                    helper.setFont(Font.font(texto.getFontFamily(), weight, posture, texto.getFontSize() * zoomLevel));
                    double measuredWidth = helper.getLayoutBounds().getWidth();
                    
                    if (measuredWidth > ew && ew > 0) {
                        double ratio = ew / measuredWidth;
                        effectiveFontSize = Math.max(4.0, texto.getFontSize() * ratio);
                        // Actualizamos la fuente del contexto solo para este dibujado
                        gc.setFont(Font.font(texto.getFontFamily(), weight, posture, effectiveFontSize * zoomLevel));
                    }
                }

                // Cálculo de líneas (usa el font actual del gc, que puede estar escalado)
                java.util.List<String> finalLines = TextUtils.computeLines(renderText, texto.isSaltoLinea(), gc.getFont(), ew);

                double lineHeight = effectiveFontSize * zoomLevel * 1.2;
                double maxLineWidth = 0;

                for (String line : finalLines) {
                    javafx.scene.text.Text tempText = new javafx.scene.text.Text(line);
                    tempText.setFont(gc.getFont());
                    double lw = tempText.getLayoutBounds().getWidth();
                    if (lw > maxLineWidth) maxLineWidth = lw;
                }

                // Cómputo de la dimensión exacta en espacio "puro/real" sin zoom (solo si no hay auto-ajuste)
                double requiredWidth = (maxLineWidth / zoomLevel) + 2.0;
                double requiredHeight = (finalLines.size() * (effectiveFontSize * 1.2)) + (effectiveFontSize * 0.3);

                boolean dimensionsChanged = false;

                // Solo ajustamos dimensiones de caja si el auto-ajuste está DESACTIVADO
                if (!texto.isAutoAjustar()) {
                    // Ancho (solo si no hay salto de línea)
                    if (!texto.isSaltoLinea()) {
                        if (Math.abs(texto.getWidth() - requiredWidth) > 1.0) {
                            texto.setWidth(requiredWidth);
                            ew = requiredWidth * zoomLevel;
                            dimensionsChanged = true;
                        }
                    }

                    // Alto (siempre ajusta si no hay auto-ajuste)
                    if (Math.abs(texto.getHeight() - requiredHeight) > 1.0) {
                        texto.setHeight(requiredHeight);
                        dimensionsChanged = true;
                    }
                }

                if (dimensionsChanged && elementoSeleccionado == texto && onElementTransformed != null) {
                    onElementTransformed.run();
                }

                // Renderizado final de las líneas
                double currentY = ey + (effectiveFontSize * zoomLevel);
                for (String line : finalLines) {
                    double textX = ex;
                    javafx.scene.text.Text tempText = new javafx.scene.text.Text(line);
                    tempText.setFont(gc.getFont());
                    double textWidth = tempText.getLayoutBounds().getWidth();

                    if ("CENTER".equals(texto.getAlineacion())) {
                        textX = ex + (ew - textWidth) / 2;
                    } else if ("RIGHT".equals(texto.getAlineacion())) {
                        textX = ex + ew - textWidth;
                    }

                    gc.fillText(line, textX, currentY);
                    currentY += lineHeight;
                }

            } else if (elem instanceof ImagenElemento imgElem) {
                Image img = imgElem.getImagen();

                // Si hay columna vinculada, intentar cargar la imagen del registro actual
                if (imgElem.getColumnaVinculada() != null && fuenteDatos != null) {
                    String nombreArchivo = fuenteDatos.getValor(imgElem.getColumnaVinculada());
                    img = resolverImagenVariable(nombreArchivo);
                    if (img == null) img = imgElem.getImagen(); // fallback sin romper
                }

                if (img != null) {
                    gc.setGlobalAlpha(imgElem.getOpacity());
                    gc.drawImage(img, ex, ey, ew, eh);
                    gc.setGlobalAlpha(1.0);
                } else {
                    // Placeholder cuando no hay imagen cargada
                    if (imagenSilueta != null) {
                        gc.setGlobalAlpha(0.35);
                        gc.drawImage(imagenSilueta, ex, ey, ew, eh);
                        gc.setGlobalAlpha(1.0);
                    } else {
                        // Fallback gris si no hay silueta en recursos
                        gc.setFill(Color.web("#3a3637"));
                        gc.fillRect(ex, ey, ew, eh);
                        gc.setStroke(Color.web("#6a6568"));
                        gc.setLineWidth(1);
                        gc.setLineDashes(4, 4);
                        gc.strokeRect(ex, ey, ew, eh);
                        gc.setLineDashes();
                        gc.setFill(Color.web("#6a6568"));
                        gc.setFont(Font.font("Arial", 11));
                        gc.fillText("🖼 Imagen", ex + 6, ey + ew / 2);
                    }
                }
            } else if (elem instanceof FormaElemento forma) {
                dibujarForma(gc, forma, ex, ey, ew, eh);

            } else if (elem instanceof ElementoCodigo codigo) {
                // Dibujar el código (QR o Barras)
                String texto = codigo.getContenido();
                if (codigo.esDinamico() && fuenteDatos != null) {
                    String valor = fuenteDatos.getValor(codigo.getColumnaVinculada());
                    if (valor != null) texto = valor;
                }
                
                Image img = codigo.getImagen(texto);
                if (img != null) {
                    gc.drawImage(img, ex, ey, ew, eh);
                    
                    // Dibujar texto human-readable si es 1D y está activo
                    if (!codigo.getTipo().isEs2D() && codigo.isMostrarTexto()) {
                        double fontSize = codigo.getFontSize();
                        FontWeight weight = codigo.isNegrita() ? FontWeight.BOLD : FontWeight.NORMAL;
                        Font font = Font.font("Arial", weight, codigo.isCursiva() ? javafx.scene.text.FontPosture.ITALIC : javafx.scene.text.FontPosture.REGULAR, fontSize);
                        
                        // 1. Dibujar fondo para el texto (para que parezca parte de la etiqueta)
                        gc.setFill(Color.web(codigo.getColorFondo()));
                        gc.fillRect(ex, ey + eh, ew, fontSize + 5);
                        
                        // 2. Dibujar texto centrado
                        gc.setFill(Color.web(codigo.getColorCodigo()));
                        gc.setFont(font);
                        gc.setTextAlign(TextAlignment.CENTER);
                        gc.fillText(codigo.getTextoProcesado(texto), ex + ew / 2, ey + eh + fontSize + 2);
                        
                        // IMPORTANTE: Resetear alineación para no romper el resto del canvas
                        gc.setTextAlign(TextAlignment.LEFT);
                    }
                } else {
                    // Placeholder visual si falla la generación
                    gc.setFill(Color.web("#2c2a2b"));
                    gc.fillRect(ex, ey, ew, eh);
                    gc.setStroke(Color.web("#5a5758"));
                    gc.setLineWidth(1);
                    gc.setLineDashes(4, 4);
                    gc.strokeRect(ex, ey, ew, eh);
                    gc.setLineDashes();
                    gc.setFill(Color.web("#7a7578"));
                    gc.setFont(Font.font("Arial", Math.max(9, ew * 0.15)));
                    gc.fillText(codigo.getTipo().toString(), ex + ew * 0.2, ey + eh * 0.6);
                }
            }

            // Selección + handles (Solo visibles en modo diseño)
            if (currentMode == AppMode.DESIGN && elementoSeleccionado != null && elem == elementoSeleccionado) {
                gc.setStroke(Color.web("#4a9b7c"));
                gc.setLineWidth(2);
                gc.setLineDashes(3, 3);
                gc.strokeRect(ex - 1, ey - 1, ew + 2, eh + 2);

                gc.setLineDashes();

                double dim = HANDLE_SIZE;

                if (elem instanceof TextoElemento) {
                    // Texto: handle solo en el lateral derecho (ancho)
                    gc.setGlobalAlpha(0.8);
                    gc.setFill(Color.WHITE);
                    gc.fillRect(ex + ew - (dim / 2), ey + (eh / 2) - (dim / 2), dim, dim);
                    gc.setGlobalAlpha(1.0);
                    gc.setStroke(Color.web("#4a9b7c"));
                    gc.setLineWidth(2);
                    gc.strokeRect(ex + ew - (dim / 2), ey + (eh / 2) - (dim / 2), dim, dim);
                } else {
                    // Imagen y Formas: handles en las 4 esquinas
                    gc.setGlobalAlpha(0.8);
                    gc.setFill(Color.WHITE);
                    gc.fillRect(ex - (dim / 2), ey - (dim / 2), dim, dim);               // NW
                    gc.fillRect(ex + ew - (dim / 2), ey - (dim / 2), dim, dim);          // NE
                    gc.fillRect(ex - (dim / 2), ey + eh - (dim / 2), dim, dim);          // SW
                    gc.fillRect(ex + ew - (dim / 2), ey + eh - (dim / 2), dim, dim);     // SE
                    gc.setGlobalAlpha(1.0);
                    gc.setStroke(Color.web("#4a9b7c"));
                    gc.setLineWidth(2);
                    gc.strokeRect(ex - (dim / 2), ey - (dim / 2), dim, dim);
                    gc.strokeRect(ex + ew - (dim / 2), ey - (dim / 2), dim, dim);
                    gc.strokeRect(ex - (dim / 2), ey + eh - (dim / 2), dim, dim);
                    gc.strokeRect(ex + ew - (dim / 2), ey + eh - (dim / 2), dim, dim);
                }
            }
        }

        // 5.5) Visualización del troquel (Hole Punch)
        if (mostrarGuias && proyectoActual.getTipoTroquel() != null && proyectoActual.getTipoTroquel() != TipoTroquel.NINGUNO) {
            gc.setGlobalAlpha(0.6);
            gc.setFill(Color.web("#e74c3c")); // Rojo semitransparente para indicar "agujero"
            gc.setStroke(Color.web("#c0392b"));
            gc.setLineWidth(1.5);

            double cx = cardX + (scaledWidth / 2); // Centro X
            double cy = cardY + (18 * zoomLevel);  // Centro Y (18px = ~4.5mm desde arriba)

            if (proyectoActual.getTipoTroquel() == TipoTroquel.CIRCULAR) {
                // Circular: 20x20px (~5mm)
                double radius = 10 * zoomLevel;
                gc.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
                gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
                
                // Cruzeta central
                gc.setLineWidth(0.5);
                gc.strokeLine(cx, cy - radius - 5, cx, cy + radius + 5);
                gc.strokeLine(cx - radius - 5, cy, cx + radius + 5, cy);
            } else if (proyectoActual.getTipoTroquel() == TipoTroquel.ALARGADO) {
                // Alargado: 56x12px (~14x3mm)
                double w = 56 * zoomLevel;
                double h = 12 * zoomLevel;
                gc.fillRoundRect(cx - (w / 2), cy - (h / 2), w, h, 10 * zoomLevel, 10 * zoomLevel);
                gc.strokeRoundRect(cx - (w / 2), cy - (h / 2), w, h, 10 * zoomLevel, 10 * zoomLevel);
                
                // Cruzeta central
                gc.setLineWidth(0.5);
                gc.strokeLine(cx, cy - (h/2) - 5, cx, cy + (h/2) + 5);
                gc.strokeLine(cx - (w/2) - 5, cy, cx + (w/2) + 5, cy);
            }
            gc.setGlobalAlpha(1.0);
        }

        // 6) Texto informativo de la UI
        gc.setLineDashes();
        
        // --- 6.1: FRENTE / DORSO a la izquierda ---
        // (Renderizado de texto eliminado: ahora se usa el selector interactivo HBox sobre el lienzo)

        // --- 6.2: PROYECTO y CLIENTE CENTRADOS DE FORMA ESTÁTICA ARRIBA ---
        // (Se animan suavemente por código leyendo hudOpacity, entre modo diseño y producción)
        boolean isVertical = (proyectoActual != null && proyectoActual.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL);
        if (hudOpacity > 0.0 && !isVertical) {
            gc.save();
            gc.setGlobalAlpha(hudOpacity);
            
            String pName = "PROYECTO | " + (proyectoActual.getNombre() != null ? proyectoActual.getNombre() : "S/N");
            String cName = "Cliente: ";
            if (proyectoActual.getMetadata() != null && proyectoActual.getMetadata().getClienteInfo() != null 
                && proyectoActual.getMetadata().getClienteInfo().getNombreEmpresa() != null
                && !proyectoActual.getMetadata().getClienteInfo().getNombreEmpresa().isBlank()) {
                cName += proyectoActual.getMetadata().getClienteInfo().getNombreEmpresa();
            } else {
                cName += "Sin Asignar";
            }

            double centroX = canvas.getWidth() / 2;
            double staticTopY = 35; // Fijo 35px desde el tope superior absoluto de la ventana

            // Render "PROYECTO | NOMBRE"
            gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.NORMAL, 18));
            javafx.scene.text.Text tP = new javafx.scene.text.Text(pName);
            tP.setFont(gc.getFont());
            double wP = tP.getLayoutBounds().getWidth();
            
            gc.setFill(Color.web("#e8e6e7"));
            gc.fillText(pName, centroX - (wP / 2), staticTopY);

            // Render "Cliente: xxx" y Botón
            gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.NORMAL, 14));
            javafx.scene.text.Text tC = new javafx.scene.text.Text(cName);
            tC.setFont(gc.getFont());
            double wC = tC.getLayoutBounds().getWidth();
            
            String btnTxt = "  ✎ Editar datos";
            javafx.scene.text.Text tB = new javafx.scene.text.Text(btnTxt);
            tB.setFont(gc.getFont());
            double wB = tB.getLayoutBounds().getWidth();

            double totalW = wC + wB;
            double startCX = centroX - (totalW / 2);

            gc.setFill(Color.web("#9a9598"));
            gc.fillText(cName, startCX, staticTopY + 25);

            gc.setFill(Color.web("#8b78d4")); // Índigo-violeta acorde a la paleta actual
            gc.fillText(btnTxt, startCX + wC, staticTopY + 25);
            
            // Registrar hit box para el click de Editar datos (solamente si es clickeable)
            if (hudOpacity >= 0.95) {
                this.btnClienteHitbox = new javafx.geometry.BoundingBox(
                        startCX + wC, staticTopY + 10, wB, 22
                );
            } else {
                this.btnClienteHitbox = null;
            }
            
            gc.restore();
        } else {
            // Desregistrar hit box si no estamos en producción para evitar clicks fantasma
            this.btnClienteHitbox = null;
        }

        gc.setFill(Color.web("#a0a5cc"));
        gc.setFont(Font.font("Arial", 11));

        double mmW = (proyectoActual != null && proyectoActual.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) ? CR80_HEIGHT_MM : CR80_WIDTH_MM;
        double mmH = (proyectoActual != null && proyectoActual.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) ? CR80_WIDTH_MM : CR80_HEIGHT_MM;
        String infoDimensiones = String.format(
                "CR80: %.2f × %.2f mm | Con sangre: %.2f × %.2f mm",
                mmW, mmH, mmW + 4.0, mmH + 4.0
        );

        double bleedScaled = BLEED_MARGIN * zoomLevel;
        
        javafx.scene.text.Text tmpText = new javafx.scene.text.Text(infoDimensiones);
        tmpText.setFont(gc.getFont());
        double textWidth = tmpText.getLayoutBounds().getWidth();
        double centerTextX = cardX + (scaledWidth / 2) - (textWidth / 2);
        
        gc.fillText(infoDimensiones, centerTextX, cardY + scaledHeight + bleedScaled + 25);

        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    // ===================== EVENTOS DE RATÓN =====================

    private void onCanvasMousePressed(MouseEvent e) {
        if (eyedropperActive) {
            pickColorAt(e.getX(), e.getY());
            return;
        }
        
        wasDragged = false;
        
        // Interpretar click en el botón figurado de Cliente
        if (btnClienteHitbox != null && btnClienteHitbox.contains(e.getX(), e.getY())) {
            if (onClientDataRequested != null) onClientDataRequested.run();
            return;
        }

        if (proyectoActual == null || currentMode != AppMode.DESIGN) return;

        double scaledWidth = getCardWidth() * zoomLevel;
        double scaledHeight = getCardHeight() * zoomLevel;
        double cardX = (canvas.getWidth() / 2) - (scaledWidth / 2);
        double cardY = (canvas.getHeight() / 2) - (scaledHeight / 2);

        // 1) Primero: si ya hay seleccionado, mirar si hemos pulsado un handle
        if (elementoSeleccionado != null) {
            DragMode mode = getDragModeForMouseEvent(e, elementoSeleccionado, cardX, cardY);
            if (mode != DragMode.NONE) {
                currentDragMode = mode;
                dragStartX = e.getX();
                dragStartY = e.getY();

                elementStartX = elementoSeleccionado.getX();
                elementStartY = elementoSeleccionado.getY();
                elementStartW = elementoSeleccionado.getWidth();
                elementStartH = elementoSeleccionado.getHeight();

                canvas.requestFocus();
                return;
            }
        }

        // 2) Si no es handle, intentar seleccionar un elemento
        double relX = (e.getX() - cardX) / zoomLevel;
        double relY = (e.getY() - cardY) / zoomLevel;

        Elemento nuevoSeleccionado = null;

        // Se recorre al revés para seleccionar el de "arriba"
        for (int i = proyectoActual.getElementosActuales().size() - 1; i >= 0; i--) {
            Elemento elem = proyectoActual.getElementosActuales().get(i);
            if (elem.contains(relX, relY)) {
                nuevoSeleccionado = elem;
                break;
            }
        }

        if (nuevoSeleccionado != null) {
            elementoSeleccionado = nuevoSeleccionado;

            if (onElementSelected != null) onElementSelected.run();

            currentDragMode = DragMode.MOVE;
            dragStartX = e.getX();
            dragStartY = e.getY();
            elementStartX = elementoSeleccionado.getX();
            elementStartY = elementoSeleccionado.getY();

            if (onCanvasChanged != null) onCanvasChanged.run();

            dibujarCanvas();
        } else {
            // Click en vacío: deseleccionar
            if (elementoSeleccionado != null) {
                elementoSeleccionado = null;
                if (onCanvasChanged != null) onCanvasChanged.run();
                dibujarCanvas();
            }
            currentDragMode = DragMode.NONE;
        }

        canvas.requestFocus();
    }

    private void onCanvasMouseDragged(MouseEvent e) {
        if (elementoSeleccionado == null) return;
        if (currentDragMode == DragMode.NONE) return;
        if (elementoSeleccionado.isLocked()) return;
        
        wasDragged = true;

        double dx = (e.getX() - dragStartX) / zoomLevel;
        double dy = (e.getY() - dragStartY) / zoomLevel;

        if (currentDragMode == DragMode.MOVE) {
            elementoSeleccionado.setX(elementStartX + dx);
            elementoSeleccionado.setY(elementStartY + dy);
            dibujarCanvas();
            return;
        }

        // Redimensionado
        double newW = elementStartW;
        double newH = elementStartH;
        double newX = elementStartX;
        double newY = elementStartY;

        boolean keepProportion = false;
        double ratio = 1.0;

        if (elementoSeleccionado instanceof ImagenElemento
                && ((ImagenElemento) elementoSeleccionado).isMantenerProporcion()) {
            keepProportion = true;
            if (elementStartH > 0) ratio = elementStartW / elementStartH;
        }

        switch (currentDragMode) {
            case RESIZE_E:
                newW = elementStartW + dx;
                break;

            case RESIZE_SE:
                newW = elementStartW + dx;
                newH = elementStartH + dy;
                if (keepProportion) newH = newW / ratio;
                break;

            case RESIZE_SW:
                newW = elementStartW - dx;
                newH = elementStartH + dy;
                if (keepProportion) newH = newW / ratio;
                newX = elementStartX + (elementStartW - newW);
                break;

            case RESIZE_NE:
                newW = elementStartW + dx;
                newH = elementStartH - dy;
                if (keepProportion) newH = newW / ratio;
                newY = elementStartY + (elementStartH - newH);
                break;

            case RESIZE_NW:
                newW = elementStartW - dx;
                newH = elementStartH - dy;
                if (keepProportion) newH = newW / ratio;
                newX = elementStartX + (elementStartW - newW);
                newY = elementStartY + (elementStartH - newH);
                break;

            default:
                break;
        }

        // Mínimos para que no se colapse
        if (newW < 10) {
            newW = 10;
            if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) {
                newX = elementStartX + (elementStartW - newW);
            }
            if (keepProportion) newH = newW / ratio;
        }

        if (newH < 10) {
            newH = 10;
            if (keepProportion) {
                newW = newH * ratio;
                if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) {
                    newX = elementStartX + (elementStartW - newW);
                }
            }
            if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_NE) {
                newY = elementStartY + (elementStartH - newH);
            }
        }

        elementoSeleccionado.setWidth(newW);
        elementoSeleccionado.setHeight(newH);

        // Ajuste de posición cuando estiramos desde izquierda/arriba
        if (currentDragMode == DragMode.RESIZE_SW ||
                currentDragMode == DragMode.RESIZE_NW ||
                currentDragMode == DragMode.RESIZE_NE) {

            if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_SW) {
                elementoSeleccionado.setX(newX);
            }
            if (currentDragMode == DragMode.RESIZE_NW || currentDragMode == DragMode.RESIZE_NE) {
                elementoSeleccionado.setY(newY);
            }
        }

        if (onElementTransformed != null) onElementTransformed.run();
        dibujarCanvas();
    }

    private void onCanvasMouseMoved(MouseEvent e) {
        lastScreenX = e.getScreenX();
        lastScreenY = e.getScreenY();
        gestionarHoverGuias(e);

        if (proyectoActual == null) {
            canvas.setCursor(Cursor.DEFAULT);
            return;
        }
        if (currentMode != AppMode.DESIGN) {
            canvas.setCursor(Cursor.DEFAULT);
            return;
        }
        if (elementoSeleccionado == null || elementoSeleccionado.isLocked()) {
            canvas.setCursor(Cursor.DEFAULT);
            return;
        }

        double scaledWidth = getCardWidth() * zoomLevel;
        double scaledHeight = getCardHeight() * zoomLevel;
        double cardX = (canvas.getWidth() / 2) - (scaledWidth / 2);
        double cardY = (canvas.getHeight() / 2) - (scaledHeight / 2);

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
        // Al soltar, se cierra el drag sí o sí
        if (wasDragged) {
            if (onCanvasChanged != null) onCanvasChanged.run();
        }
        currentDragMode = DragMode.NONE;
        wasDragged = false;
    }

    // ===================== AUXILIARES =====================

    private DragMode getDragModeForMouseEvent(MouseEvent e, Elemento elem, double cardX, double cardY) {
        double mx = e.getX();
        double my = e.getY();

        double ex = cardX + (elem.getX() * zoomLevel);
        double ey = cardY + (elem.getY() * zoomLevel);
        double ew = elem.getWidth() * zoomLevel;
        double eh = elem.getHeight() * zoomLevel;

        // Hitbox un poco más amplia para que no sea desesperante acertar
        double hit = HANDLE_SIZE + 4;

        if (elem instanceof TextoElemento) {
            double hx = ex + ew;
            double hy = ey + (eh / 2);
            if (Math.abs(mx - hx) <= hit && Math.abs(my - hy) <= hit) {
                return DragMode.RESIZE_E;
            }
            return DragMode.NONE;
        }

        // Esquinas para imágenes
        if (Math.abs(mx - ex) <= hit && Math.abs(my - ey) <= hit) return DragMode.RESIZE_NW;
        if (Math.abs(mx - (ex + ew)) <= hit && Math.abs(my - ey) <= hit) return DragMode.RESIZE_NE;
        if (Math.abs(mx - ex) <= hit && Math.abs(my - (ey + eh)) <= hit) return DragMode.RESIZE_SW;
        if (Math.abs(mx - (ex + ew)) <= hit && Math.abs(my - (ey + eh)) <= hit) return DragMode.RESIZE_SE;

        return DragMode.NONE;
    }

    private void gestionarHoverGuias(MouseEvent e) {
        if (!mostrarGuias || currentMode != AppMode.DESIGN || currentDragMode != DragMode.NONE) {
            currentTooltipTarget = null;
            tooltipDelay.stop();
            guideTooltip.hide();
            return;
        }

        double scaledWidth = getCardWidth() * zoomLevel;
        double scaledHeight = getCardHeight() * zoomLevel;
        double cardX = (canvas.getWidth() / 2) - (scaledWidth / 2);
        double cardY = (canvas.getHeight() / 2) - (scaledHeight / 2);
        double mx = e.getX();
        double my = e.getY();
        double hit = 4.0; // Tolerancia en pixels
        String hoverDeteccion = null;

        double bleedScaled = BLEED_MARGIN * zoomLevel;
        double safetyScaled = SAFETY_MARGIN * zoomLevel;

        // 1. Rectángulo de Sangrado
        if (esBorde(mx, my, cardX - bleedScaled, cardY - bleedScaled, scaledWidth + bleedScaled * 2, scaledHeight + bleedScaled * 2, hit)) {
            hoverDeteccion = "Zona de sangrado (área que será recortada en impresión)";
        } 
        // 2. Rectángulo de Corte Final
        else if (esBorde(mx, my, cardX, cardY, scaledWidth, scaledHeight, hit)) {
            hoverDeteccion = "Corte final";
        }
        // 3. Rectángulo de Seguridad
        else if (esBorde(mx, my, cardX + safetyScaled, cardY + safetyScaled, scaledWidth - safetyScaled * 2, scaledHeight - safetyScaled * 2, hit)) {
            hoverDeteccion = "Margen de seguridad (zona donde no deben colocarse textos importantes)";
        }

        if (hoverDeteccion != null) {
            if (!hoverDeteccion.equals(currentTooltipTarget)) {
                currentTooltipTarget = hoverDeteccion;
                guideTooltip.setText(hoverDeteccion);
                tooltipDelay.playFromStart();
            }
        } else {
            currentTooltipTarget = null;
            tooltipDelay.stop();
            guideTooltip.hide();
        }
    }

    private boolean esBorde(double mx, double my, double rx, double ry, double rw, double rh, double error) {
        boolean onLeft = Math.abs(mx - rx) <= error && my >= ry - error && my <= ry + rh + error;
        boolean onRight = Math.abs(mx - (rx + rw)) <= error && my >= ry - error && my <= ry + rh + error;
        boolean onTop = Math.abs(my - ry) <= error && mx >= rx - error && mx <= rx + rw + error;
        boolean onBottom = Math.abs(my - (ry + rh)) <= error && mx >= rx - error && mx <= rx + rw + error;
        return onLeft || onRight || onTop || onBottom;
    }

    // ===================== GETTERS =====================

    public Elemento getElementoSeleccionado() {
        return elementoSeleccionado;
    }

    // ===================== CUENTAGOTAS =====================

    public void activateEyedropper(java.util.function.Consumer<Color> callback) {
        this.eyedropperActive = true;
        this.onColorPickedCallback = callback;
        canvas.setCursor(Cursor.CROSSHAIR);
        
        // Usar el sistema de Toast oficial con estilo EXITO (verde) como pidió el usuario
        if (canvas.getScene() != null && canvas.getScene().getWindow() != null) {
            TPSToast.mostrar(canvas.getScene().getWindow(), 
                "Modo Cuentagotas Activado", 
                "Haz clic en cualquier color del diseño para capturarlo", 
                TPSToast.Tipo.EXITO);
        }
    }

    public void deactivateEyedropper() {
        this.eyedropperActive = false;
        canvas.setCursor(Cursor.DEFAULT);
    }

    private void pickColorAt(double x, double y) {
        // 1. Tomar snapshot del canvas actual
        Image snapshot = canvas.snapshot(null, null);
        javafx.scene.image.PixelReader pr = snapshot.getPixelReader();
        
        // 2. Obtener color (asegurando límites)
        int ix = (int) Math.max(0, Math.min(snapshot.getWidth() - 1, x));
        int iy = (int) Math.max(0, Math.min(snapshot.getHeight() - 1, y));
        Color picked = pr.getColor(ix, iy);
        
        // 3. Notificar y desactivar
        if (onColorPickedCallback != null) {
            onColorPickedCallback.accept(picked);
        }
        
        eyedropperActive = false;
        canvas.setCursor(Cursor.DEFAULT);
    }

    /* Intenta cargar la imagen cuyo nombre de archivo viene del Excel.
     * Busca en la carpeta Fotos/ del proyecto.
     * Devuelve null si el archivo no existe o si no hay metadata de proyecto. */
    private Image resolverImagenVariable(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.isBlank()) return null;
        if (proyectoActual == null || proyectoActual.getMetadata() == null) return null;

        String rutaFotos = proyectoActual.getMetadata().getRutaFotos();
        if (rutaFotos == null) return null;

        Path rutaAbsoluta = Paths.get(rutaFotos, nombreArchivo);
        if (!Files.exists(rutaAbsoluta)) return null;

        try {
            return ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Dibuja un FormaElemento (rectángulo, elipse o línea) en el GraphicsContext dado.
     * Las coordenadas ex/ey/ew/eh ya están escaladas con el zoomLevel del llamante.
     */
    private void dibujarForma(javafx.scene.canvas.GraphicsContext gc,
                              FormaElemento forma,
                              double ex, double ey, double ew, double eh) {
        double grosor = Math.max(1.0, forma.getGrosorBorde());
        gc.setLineWidth(grosor);
        gc.setLineDashes();

        // Aplicar opacidad
        double oldAlpha = gc.getGlobalAlpha();
        gc.setGlobalAlpha(forma.getOpacidad());

        switch (forma.getTipoForma()) {
            case RECTANGULO -> {
                double arc = forma.getRadioCurvatura() * zoomLevel;
                if (forma.isConRelleno()) {
                    gc.setFill(Color.web(forma.getColorRelleno()));
                    gc.fillRoundRect(ex, ey, ew, eh, arc, arc);
                }
                if (forma.isConBorde()) {
                    gc.setStroke(Color.web(forma.getColorBorde()));
                    gc.strokeRoundRect(ex, ey, ew, eh, arc, arc);
                }
            }
            case ELIPSE -> {
                if (forma.isConRelleno()) {
                    gc.setFill(Color.web(forma.getColorRelleno()));
                    gc.fillOval(ex, ey, ew, eh);
                }
                if (forma.isConBorde()) {
                    gc.setStroke(Color.web(forma.getColorBorde()));
                    gc.strokeOval(ex, ey, ew, eh);
                }
            }
            case LINEA -> {
                if (forma.isConBorde()) {
                    gc.setStroke(Color.web(forma.getColorBorde()));
                    gc.strokeLine(ex, ey + eh / 2, ex + ew, ey + eh / 2);
                }
            }
        }
        
        // Resetear opacidad
        gc.setGlobalAlpha(oldAlpha);
    }
}
