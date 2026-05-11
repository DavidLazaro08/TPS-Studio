package com.tpsstudio.service;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.enums.TipoTroquel;
import com.tpsstudio.model.project.Proyecto;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para validar el estado de un diseño antes de su exportación.
 * Basado estrictamente en las reglas y dimensiones reales del Canvas (EditorCanvasManager).
 */
public class DesignValidatorService {

    // Dimensiones del canvas en el editor (px)
    private static final double CARD_WIDTH   = 342.4;
    private static final double CARD_HEIGHT  = 215.92;
    private static final double BLEED_MARGIN = 8.0;
    private static final double SAFETY_MARGIN = 12.0;

    // Resolución mínima recomendada para impresión CR80 a 300 DPI
    // CR80: 85,60 mm × 53,98 mm  + sangre 2mm c/lado → 89,60 × 57,98 mm
    // a 300 DPI: (mm / 25.4) * 300
    private static final double MIN_PRINT_W = (89.60 / 25.4) * 300; // ≈ 1058 px
    private static final double MIN_PRINT_H = (57.98 / 25.4) * 300; // ≈ 685 px

    /**
     * Evalúa el proyecto actual y devuelve una lista de advertencias.
     */
    public List<String> validarDiseno(Proyecto proyecto) {
        List<String> avisos = new ArrayList<>();

        if (proyecto == null) {
            avisos.add("No hay ningún proyecto activo.");
            return avisos;
        }

        // Revisamos el frente
        validarCara(proyecto.getElementosFrente(), proyecto.getFondoFrente(), true, proyecto.getTipoTroquel(), avisos, proyecto);

        // Revisamos el dorso (solo si hay elementos o fondo)
        boolean hasDorso = proyecto.getFondoDorso() != null || !proyecto.getElementosDorso().isEmpty();
        if (hasDorso) {
            validarCara(proyecto.getElementosDorso(), proyecto.getFondoDorso(), false, proyecto.getTipoTroquel(), avisos, proyecto);
        }

        return avisos;
    }

    private void validarCara(List<Elemento> elementos, ImagenFondoElemento fondo, boolean esFrente, TipoTroquel tipoTroquel, List<String> avisos, Proyecto proyecto) {
        String capa = esFrente ? "FRENTE" : "DORSO";
        
        double currentCardWidth = (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) ? CARD_HEIGHT : CARD_WIDTH;
        double currentCardHeight = (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) ? CARD_WIDTH : CARD_HEIGHT;

        // 1. VALIDACIÓN DEL FONDO (RESOLUCIÓN Y PROPORCIÓN)
        if (fondo != null && fondo.getImagen() != null) {
            double realW = fondo.getImagen().getWidth();
            double realH = fondo.getImagen().getHeight();

            // 1a. Resolución mínima para impresión a 300 DPI (con un pequeño margen de tolerancia del 2% para evitar falsos positivos)
            double minW = (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) ? MIN_PRINT_H : MIN_PRINT_W;
            double minH = (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) ? MIN_PRINT_W : MIN_PRINT_H;
            
            // Tolerancia para no ser excesivamente estricto con imágenes que están casi en el límite
            double tolerance = 0.98; 
            if (realW < (minW * tolerance) || realH < (minH * tolerance)) {
                avisos.add("[" + capa + "] El fondo tiene una resolución baja para impresión "
                        + String.format("(%.0f×%.0f px detectados, el óptimo es: %.0f×%.0f px). ", realW, realH, minW, minH)
                        + "La calidad de impresión podría verse comprometida.");
            }

            // 1b. Proporción de la imagen vs proporción CR80 (85.60 x 53.98 mm → ratio ≈ 1.585)
            // Se verifica la proporción de la imagen original. Si difiere >20% del ratio CR80,
            // la imagen se distorsionará visiblemente al estirarse para cubrir la tarjeta.
            if (realH > 0) {
                double ratioImg   = realW / realH;
                double ratioCR80  = (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL) ? (53.98 / 85.60) : (85.60 / 53.98);
                double diferencia = Math.abs(ratioImg - ratioCR80) / ratioCR80; // % de desviación
                if (diferencia > 0.20) {
                    avisos.add("[" + capa + "] La proporción de la imagen de fondo "
                            + String.format("(%.0f×%.0f px, ratio %.2f)", realW, realH, ratioImg)
                            + " no coincide con la tarjeta CR80 (ratio " + String.format("%.2f", ratioCR80) + "). Al ajustarse automáticamente la imagen se distorsionará. "
                            + "Use el 'Editor Externo' para recortarla al formato correcto antes de importarla.");
                }
            }
        }

        // 2. CÁLCULO DEL ÁREA DEL TROQUEL (Si existe)
        double tx = -1, ty = -1, tw = 0, th = 0;
        boolean hayTroquel = (tipoTroquel != null && tipoTroquel != TipoTroquel.NINGUNO);
        if (hayTroquel) {
            double cx = currentCardWidth / 2.0;
            double cy = 18.0; // 18px ~ 4.5mm desde arriba
            if (tipoTroquel == TipoTroquel.CIRCULAR) {
                tw = 20.0; th = 20.0;
                tx = cx - 10.0; ty = cy - 10.0;
            } else if (tipoTroquel == TipoTroquel.ALARGADO) {
                tw = 56.0; th = 12.0;
                tx = cx - 28.0; ty = cy - 6.0;
            }
        }

        // 3. VALIDACIÓN DE ELEMENTOS
        for (Elemento elem : elementos) {
            if (!elem.isVisible()) continue;

            double ex = elem.getX(); 
            double ey = elem.getY(); 
            double ew = elem.getWidth();
            double eh = elem.getHeight();

            String name = elem.getEtiqueta() != null && !elem.getEtiqueta().isEmpty() ? elem.getEtiqueta() : elem.getNombre();

            // A) Límites Críticos (Fuera del canvas general)
            boolean saleIzquierda = ex < -BLEED_MARGIN;
            boolean saleArriba = ey < -BLEED_MARGIN;
            boolean saleDerecha = (ex + ew) > (currentCardWidth + BLEED_MARGIN);
            boolean saleAbajo = (ey + eh) > (currentCardHeight + BLEED_MARGIN);

            String tipoLabel = "El elemento";
            if (elem instanceof TextoElemento) tipoLabel = "El texto";
            else if (elem instanceof ImagenElemento) tipoLabel = "La imagen";
            else if (elem instanceof com.tpsstudio.model.elements.ElementoCodigo) tipoLabel = "El código (QR/Barras)";

            if (saleIzquierda || saleArriba || saleDerecha || saleAbajo) {
                avisos.add("[" + capa + "] " + tipoLabel + " '" + name + "' sale fuera del área de impresión.");
            } else {
                // Chequeo de zona de sangrado (solo si no se sale totalmente)
                if (ex < 0 || ey < 0 || (ex + ew) > currentCardWidth || (ey + eh) > currentCardHeight) {
                    avisos.add("[" + capa + "] " + tipoLabel + " '" + name + "' invade la zona de sangrado (será recortado).");
                } else {
                    // Margen de seguridad (solo avisos leves)
                    if (ex < SAFETY_MARGIN || ey < SAFETY_MARGIN ||
                       (ex + ew) > (currentCardWidth - SAFETY_MARGIN) ||
                       (ey + eh) > (currentCardHeight - SAFETY_MARGIN)) {
                        
                        // Para códigos, el margen de seguridad es más importante para la lectura
                        if (elem instanceof com.tpsstudio.model.elements.ElementoCodigo) {
                            avisos.add("[" + capa + "] El código '" + name + "' está muy cerca del borde. Podría tener problemas de lectura al imprimir.");
                        } else {
                            avisos.add("[" + capa + "] (Aviso leve) " + tipoLabel + " '" + name + "' está fuera del margen de seguridad.");
                        }
                    }
                }
            }

            // B) Colisión con el Troquel
            if (hayTroquel) {
                // Intersección básica de rectángulos (Bounding Box)
                boolean intersecaX = ex < tx + tw && ex + ew > tx;
                boolean intersecaY = ey < ty + th && ey + eh > ty;
                
                if (intersecaX && intersecaY) {
                    avisos.add("[" + capa + "] CRÍTICO: El elemento '" + name + "' está debajo del área de troquelado (" + tipoTroquel.getDescripcion() + ") y será perforado físicamente.");
                }
            }
        }
    }
}
