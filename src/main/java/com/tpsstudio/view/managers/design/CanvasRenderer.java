package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.util.ImageUtils;
import com.tpsstudio.util.TextUtils;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Motor de renderizado para el canvas del editor.
 * Se encarga exclusivamente de dibujar la tarjeta, elementos y guías.
 */
public class CanvasRenderer {
    
    // Estado de animación para el cuentagotas
    private double eyedropperX, eyedropperY, eyedropperPulse;
    private boolean isEyedropperActive;

    private static Image imagenSilueta = null;
    static {
        try (var stream = CanvasRenderer.class.getResourceAsStream("/img/silueta.png")) {
            if (stream != null) imagenSilueta = new Image(stream);
        } catch (Exception ignored) {}
    }

    public void render(GraphicsContext gc, Proyecto proyecto, double canvasW, double canvasH, 
                       double zoom, boolean mostrarGuias, AppMode currentMode, 
                       Elemento seleccion, FuenteDatos fuenteDatos, double hudOpacity,
                       boolean eyedropperActive, double ex, double ey, double ep) {
        
        this.isEyedropperActive = eyedropperActive;
        this.eyedropperX = ex; this.eyedropperY = ey; this.eyedropperPulse = ep;
        
        gc.clearRect(0, 0, canvasW, canvasH);

        if (proyecto == null) {
            drawEmptyState(gc, canvasW, canvasH);
            return;
        }

        // Cálculos de centrado
        double scaledW = EditorCanvasManager.getScaledWidth(proyecto) * zoom;
        double scaledH = EditorCanvasManager.getScaledHeight(proyecto) * zoom;
        double cardX = (canvasW / 2) - (scaledW / 2);
        double cardY = (canvasH / 2) - (scaledH / 2);

        gc.save();

        // 1. Guías de sangrado (exterior)
        if (mostrarGuias) {
            double bleed = EditorCanvasManager.BLEED_MARGIN * zoom;
            drawGuideRect(gc, cardX - bleed, cardY - bleed, scaledW + bleed * 2, scaledH + bleed * 2, "#d48a8a", true);
        }

        // 2. Fondo o blanco
        ImagenFondoElemento fondo = proyecto.getFondoActual();
        if (fondo != null && fondo.getImagen() != null) {
            gc.drawImage(fondo.getImagen(), cardX + (fondo.getX() * zoom), cardY + (fondo.getY() * zoom), 
                         fondo.getWidth() * zoom, fondo.getHeight() * zoom);
        } else {
            gc.setFill(Color.WHITE);
            gc.fillRect(cardX, cardY, scaledW, scaledH);
        }

        // 3. Borde de la tarjeta
        drawGuideRect(gc, cardX, cardY, scaledW, scaledH, "#c4c0c2", false);

        // 4. Margen de seguridad (interior)
        if (mostrarGuias) {
            double safety = EditorCanvasManager.SAFETY_MARGIN * zoom;
            drawGuideRect(gc, cardX + safety, cardY + safety, scaledW - safety * 2, scaledH - safety * 2, "#4a9b7c", true);
        }

        // 5. Elementos
        for (Elemento elem : proyecto.getElementosActuales()) {
            if (!elem.isVisible()) continue;
            drawElement(gc, elem, cardX, cardY, zoom, fuenteDatos, seleccion, currentMode);
        }

        // 6. Troquel (agujero)
        if (mostrarGuias) drawHolePunch(gc, proyecto, cardX, cardY, scaledW, zoom);

        // 7. HUD Informativo (Proyecto/Cliente)
        if (hudOpacity > 0.0) drawHUD(gc, proyecto, canvasW, hudOpacity, currentMode);

        // 8. Información de dimensiones inferior
        drawFooterInfo(gc, proyecto, cardX, cardY, scaledW, scaledH, zoom);
        
        // 9. Efecto Cuentagotas (si está activo)
        if (isEyedropperActive) drawEyedropperPulse(gc);

        gc.restore();
    }

    private void drawElement(GraphicsContext gc, Elemento elem, double cardX, double cardY, 
                             double zoom, FuenteDatos fuenteDatos, Elemento seleccion, AppMode mode) {
        double ex = cardX + (elem.getX() * zoom);
        double ey = cardY + (elem.getY() * zoom);
        double ew = elem.getWidth() * zoom;
        double eh = elem.getHeight() * zoom;

        if (elem instanceof TextoElemento t) {
            drawText(gc, t, ex, ey, ew, eh, zoom, fuenteDatos);
        } else if (elem instanceof ImagenElemento i) {
            drawImage(gc, i, ex, ey, ew, eh, proyectoParaFotos(elem));
        } else if (elem instanceof FormaElemento f) {
            drawShape(gc, f, ex, ey, ew, eh, zoom);
        } else if (elem instanceof ElementoCodigo c) {
            drawCode(gc, c, ex, ey, ew, eh, zoom, fuenteDatos);
        }

        // Handles de selección
        if (mode == AppMode.DESIGN && elem == seleccion) {
            drawSelectionBox(gc, elem, ex, ey, ew, eh);
        }
    }

    private void drawText(GraphicsContext gc, TextoElemento t, double ex, double ey, double ew, double eh, double zoom, FuenteDatos bd) {
        String content = t.getContenido();
        if (t.getColumnaVinculada() != null && bd != null) {
            String val = bd.getValor(t.getColumnaVinculada());
            if (val != null) content = val;
        }

        FontWeight w = t.isNegrita() ? FontWeight.BOLD : FontWeight.NORMAL;
        javafx.scene.text.FontPosture p = t.isCursiva() ? javafx.scene.text.FontPosture.ITALIC : javafx.scene.text.FontPosture.REGULAR;
        
        double fontSize = t.getFontSize();
        gc.setFill(Color.web(t.getColor()));
        gc.setFont(Font.font(t.getFontFamily(), w, p, fontSize * zoom));

        if (t.isAutoAjustar() && !t.isSaltoLinea()) {
            javafx.scene.text.Text helper = new javafx.scene.text.Text(content);
            helper.setFont(gc.getFont());
            double mw = helper.getLayoutBounds().getWidth();
            if (mw > ew && ew > 0) {
                fontSize = Math.max(4.0, t.getFontSize() * (ew / mw));
                gc.setFont(Font.font(t.getFontFamily(), w, p, fontSize * zoom));
            }
        }

        List<String> lines = TextUtils.computeLines(content, t.isSaltoLinea(), gc.getFont(), ew);
        double lineHeight = fontSize * zoom * 1.2;
        
        // Calcular altura total para centrar verticalmente
        double totalTextHeight = lines.size() * lineHeight;
        double offsetY = Math.max(0, (eh - totalTextHeight) / 2);
        double currentY = ey + offsetY + (fontSize * zoom * 0.85); // 0.85 para ajustar el baseline visual al centro

        for (String line : lines) {
            double tx = ex;
            if ("CENTER".equals(t.getAlineacion())) {
                tx = ex + (ew - TextUtils.getTextWidth(line, gc.getFont())) / 2;
            } else if ("RIGHT".equals(t.getAlineacion())) {
                tx = ex + ew - TextUtils.getTextWidth(line, gc.getFont());
            }
            gc.fillText(line, tx, currentY);
            currentY += lineHeight;
        }
    }

    private void drawImage(GraphicsContext gc, ImagenElemento i, double ex, double ey, double ew, double eh, Proyecto p) {
        Image img = i.getImagen();
        if (i.getColumnaVinculada() != null && p != null) {
            Image varImg = resolverImagenVariable(p, i.getColumnaVinculada());
            if (varImg != null) img = varImg;
        }

        if (img != null) {
            gc.setGlobalAlpha(i.getOpacity());
            gc.drawImage(img, ex, ey, ew, eh);
            gc.setGlobalAlpha(1.0);
        } else {
            drawPlaceholder(gc, ex, ey, ew, eh, "🖼 Imagen");
        }
    }

    private void drawCode(GraphicsContext gc, ElementoCodigo c, double ex, double ey, double ew, double eh, double zoom, FuenteDatos bd) {
        String text = c.getContenido();
        if (c.esDinamico() && bd != null) {
            String val = bd.getValor(c.getColumnaVinculada());
            if (val != null) text = val;
        }
        
        Image img = c.getImagen(text);
        if (img != null) {
            gc.drawImage(img, ex, ey, ew, eh);
            if (!c.getTipo().isEs2D() && c.isMostrarTexto()) {
                drawBarcodeText(gc, c, text, ex, ey, ew, eh, zoom);
            }
        } else {
            drawPlaceholder(gc, ex, ey, ew, eh, c.getTipo().toString());
        }
    }

    private void drawBarcodeText(GraphicsContext gc, ElementoCodigo c, String text, double ex, double ey, double ew, double eh, double zoom) {
        double fSize = c.getFontSize();
        FontWeight w = c.isNegrita() ? FontWeight.BOLD : FontWeight.NORMAL;
        gc.setFont(Font.font("Arial", w, c.isCursiva() ? javafx.scene.text.FontPosture.ITALIC : javafx.scene.text.FontPosture.REGULAR, fSize));
        gc.setFill(Color.web(c.getColorFondo()));
        gc.fillRect(ex, ey + eh, ew, fSize + 5);
        gc.setFill(Color.web(c.getColorCodigo()));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(c.getTextoProcesado(text), ex + ew / 2, ey + eh + fSize + 2);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawShape(GraphicsContext gc, FormaElemento f, double ex, double ey, double ew, double eh, double zoom) {
        gc.setLineWidth(Math.max(1.0, f.getGrosorBorde()));
        double oldAlpha = gc.getGlobalAlpha();
        gc.setGlobalAlpha(f.getOpacidad());

        switch (f.getTipoForma()) {
            case RECTANGULO -> {
                double arc = f.getRadioCurvatura() * zoom;
                if (f.isConRelleno()) {
                    gc.setFill(Color.web(f.getColorRelleno()));
                    gc.fillRoundRect(ex, ey, ew, eh, arc, arc);
                }
                if (f.isConBorde()) {
                    gc.setStroke(Color.web(f.getColorBorde()));
                    gc.strokeRoundRect(ex, ey, ew, eh, arc, arc);
                }
            }
            case ELIPSE -> {
                if (f.isConRelleno()) {
                    gc.setFill(Color.web(f.getColorRelleno()));
                    gc.fillOval(ex, ey, ew, eh);
                }
                if (f.isConBorde()) {
                    gc.setStroke(Color.web(f.getColorBorde()));
                    gc.strokeOval(ex, ey, ew, eh);
                }
            }
            case LINEA -> {
                if (f.isConBorde()) {
                    gc.setStroke(Color.web(f.getColorBorde()));
                    gc.strokeLine(ex, ey + eh / 2, ex + ew, ey + eh / 2);
                }
            }
        }
        gc.setGlobalAlpha(oldAlpha);
    }

    private void drawSelectionBox(GraphicsContext gc, Elemento elem, double ex, double ey, double ew, double eh) {
        gc.setStroke(Color.web("#4a9b7c"));
        gc.setLineWidth(2);
        gc.setLineDashes(3, 3);
        gc.strokeRect(ex - 1, ey - 1, ew + 2, eh + 2);
        gc.setLineDashes();

        double dim = EditorCanvasManager.HANDLE_SIZE;
        gc.setGlobalAlpha(0.8); gc.setFill(Color.WHITE);
        if (elem instanceof TextoElemento) {
            drawHandle(gc, ex + ew - (dim / 2), ey + (eh / 2) - (dim / 2), dim);
        } else {
            drawHandle(gc, ex - (dim / 2), ey - (dim / 2), dim);
            drawHandle(gc, ex + ew - (dim / 2), ey - (dim / 2), dim);
            drawHandle(gc, ex - (dim / 2), ey + eh - (dim / 2), dim);
            drawHandle(gc, ex + ew - (dim / 2), ey + eh - (dim / 2), dim);
        }
        gc.setGlobalAlpha(1.0);
    }

    private void drawHandle(GraphicsContext gc, double x, double y, double dim) {
        gc.fillRect(x, y, dim, dim);
        gc.setStroke(Color.web("#4a9b7c")); gc.setLineWidth(2);
        gc.strokeRect(x, y, dim, dim);
    }

    private void drawHUD(GraphicsContext gc, Proyecto p, double canvasW, double opacity, AppMode mode) {
        gc.save();
        gc.setGlobalAlpha(opacity);
        double centroX = canvasW / 2;
        double staticTopY = 35;

        // 1. Textos Superiores (Proyecto / Cliente) en modo Producción
        // Solo si NO es vertical, para evitar que se solapen con la tarjeta
        if (mode == AppMode.PRODUCTION && p.getOrientacion() != Orientacion.VERTICAL) {
            gc.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 18));
            gc.setFill(Color.WHITE);
            String pName = "PROYECTO | " + (p.getNombre() != null ? p.getNombre().toUpperCase() : "SIN NOMBRE");
            gc.fillText(pName, centroX - (TextUtils.getTextWidth(pName, gc.getFont()) / 2), staticTopY);

            gc.setFont(javafx.scene.text.Font.font("System", 14));
            gc.setFill(Color.LIGHTGRAY);
            
            String cName = "Cliente: " + (p.getMetadata() != null && p.getMetadata().getClienteInfo() != null 
                            ? p.getMetadata().getClienteInfo().getNombreEmpresa() : "Sin Asignar");
            String link = " 🔗 Editar datos";
            double totalW = TextUtils.getTextWidth(cName + link, gc.getFont());
            gc.fillText(cName, centroX - (totalW / 2), staticTopY + 25);
            
            gc.setFill(Color.web("#8181f7"));
            gc.fillText(link, centroX - (totalW / 2) + TextUtils.getTextWidth(cName, gc.getFont()), staticTopY + 25);
        }
        gc.restore();
    }

    private void drawHolePunch(GraphicsContext gc, Proyecto p, double cardX, double cardY, double scaledW, double zoom) {
        if (p.getTipoTroquel() == null || p.getTipoTroquel() == TipoTroquel.NINGUNO) return;
        gc.setGlobalAlpha(0.6); gc.setFill(Color.web("#e74c3c")); gc.setStroke(Color.web("#c0392b")); gc.setLineWidth(1.5);
        double cx = cardX + (scaledW / 2);
        double cy = cardY + (18 * zoom);
        if (p.getTipoTroquel() == TipoTroquel.CIRCULAR) {
            double r = 10 * zoom; gc.fillOval(cx - r, cy - r, r * 2, r * 2); gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
        } else if (p.getTipoTroquel() == TipoTroquel.ALARGADO) {
            double w = 56 * zoom, h = 12 * zoom;
            gc.fillRoundRect(cx - w/2, cy - h/2, w, h, 10 * zoom, 10 * zoom);
            gc.strokeRoundRect(cx - w/2, cy - h/2, w, h, 10 * zoom, 10 * zoom);
        }
        gc.setGlobalAlpha(1.0);
    }

    private void drawFooterInfo(GraphicsContext gc, Proyecto p, double cardX, double cardY, double sw, double sh, double zoom) {
        gc.setFill(Color.web("#a0a5cc")); gc.setFont(Font.font("Arial", 11));
        double mw = (p.getOrientacion() == Orientacion.VERTICAL) ? EditorCanvasManager.CR80_HEIGHT_MM : EditorCanvasManager.CR80_WIDTH_MM;
        double mh = (p.getOrientacion() == Orientacion.VERTICAL) ? EditorCanvasManager.CR80_WIDTH_MM : EditorCanvasManager.CR80_HEIGHT_MM;
        String info = String.format("CR80: %.2f × %.2f mm | Con sangre: %.2f × %.2f mm", mw, mh, mw + 4.0, mh + 4.0);
        double bleed = EditorCanvasManager.BLEED_MARGIN * zoom;
        gc.fillText(info, cardX + sw/2 - TextUtils.getTextWidth(info, gc.getFont())/2, cardY + sh + bleed + 25);
    }

    private void drawGuideRect(GraphicsContext gc, double x, double y, double w, double h, String color, boolean dash) {
        gc.setStroke(Color.web(color)); gc.setLineWidth(1);
        if (dash) gc.setLineDashes(5, 5); else gc.setLineDashes();
        gc.strokeRect(x, y, w, h);
    }

    private void drawEmptyState(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#9a9598")); gc.setFont(Font.font("System", 16));
        String msg = "Seleccione un proyecto o cree uno nuevo";
        gc.fillText(msg, (w - TextUtils.getTextWidth(msg, gc.getFont())) / 2, h / 2);
    }

    private void drawPlaceholder(GraphicsContext gc, double ex, double ey, double ew, double eh, String text) {
        if (imagenSilueta != null) {
            gc.setGlobalAlpha(0.35); gc.drawImage(imagenSilueta, ex, ey, ew, eh); gc.setGlobalAlpha(1.0);
        } else {
            gc.setFill(Color.web("#3a3637")); gc.fillRect(ex, ey, ew, eh);
            gc.setStroke(Color.web("#6a6568")); gc.setLineWidth(1); gc.setLineDashes(4, 4); gc.strokeRect(ex, ey, ew, eh);
            gc.setLineDashes(); gc.setFill(Color.web("#6a6568")); gc.setFont(Font.font("Arial", 11));
            gc.fillText(text, ex + 6, ey + ew / 2);
        }
    }

    private Image resolverImagenVariable(Proyecto p, String col) {
        if (p == null || p.getMetadata() == null || p.getMetadata().getRutaFotos() == null) return null;
        // Lógica simplificada: en una implementación real, esto consultaría la FuenteDatos
        return null; // El EditorCanvasManager pasará el valor resuelto si es posible
    }

    private Proyecto proyectoParaFotos(Elemento e) { return null; /* Inyectar si es necesario */ }

    private void drawEyedropperPulse(GraphicsContext gc) {
        gc.save();
        double r1 = 5 + (eyedropperPulse * 20);
        double r2 = 2 + (eyedropperPulse * 12);
        double alpha = 1.0 - eyedropperPulse;

        gc.setStroke(Color.WHITE); gc.setLineWidth(2); gc.setGlobalAlpha(alpha);
        gc.strokeOval(eyedropperX - r1, eyedropperY - r1, r1 * 2, r1 * 2);
        
        gc.setStroke(Color.web("#8181f7")); gc.setLineWidth(1.5); gc.setGlobalAlpha(alpha * 0.7);
        gc.strokeOval(eyedropperX - r2, eyedropperY - r2, r2 * 2, r2 * 2);
        gc.restore();
    }
}
