package com.tpsstudio.service;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
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
        validarCara(proyecto.getElementosFrente(), proyecto.getFondoFrente(), true, avisos);

        // Revisamos el dorso (solo si hay elementos o fondo)
        boolean hasDorso = proyecto.getFondoDorso() != null || !proyecto.getElementosDorso().isEmpty();
        if (hasDorso) {
            validarCara(proyecto.getElementosDorso(), proyecto.getFondoDorso(), false, avisos);
        }

        return avisos;
    }

    private void validarCara(List<Elemento> elementos, ImagenFondoElemento fondo, boolean esFrente, List<String> avisos) {
        String capa = esFrente ? "FRENTE" : "DORSO";

        // 1. VALIDACIÓN DEL FONDO (RESOLUCIÓN Y PROPORCIÓN)
        if (fondo != null && fondo.getImagen() != null) {
            double realW = fondo.getImagen().getWidth();
            double realH = fondo.getImagen().getHeight();

            // 1a. Resolución mínima para impresión a 300 DPI
            if (realW < MIN_PRINT_W || realH < MIN_PRINT_H) {
                avisos.add("[" + capa + "] El fondo tiene una resolución baja para impresión "
                        + String.format("(%.0f×%.0f px detectados, mínimo recomendado: %.0f×%.0f px a 300 dpi). ", realW, realH, MIN_PRINT_W, MIN_PRINT_H)
                        + "Puede verse pixelado al imprimir. Considere usar el botón 'Editor Externo' para ajustarlo.");
            }

            // 1b. Proporción de la imagen vs proporción CR80 (85.60 x 53.98 mm → ratio ≈ 1.585)
            // Se verifica la proporción de la imagen original. Si difiere >20% del ratio CR80,
            // la imagen se distorsionará visiblemente al estirarse para cubrir la tarjeta.
            if (realH > 0) {
                double ratioImg   = realW / realH;
                double ratioCR80  = 85.60 / 53.98; // ≈ 1.585
                double diferencia = Math.abs(ratioImg - ratioCR80) / ratioCR80; // % de desviación
                if (diferencia > 0.20) {
                    avisos.add("[" + capa + "] La proporción de la imagen de fondo "
                            + String.format("(%.0f×%.0f px, ratio %.2f)", realW, realH, ratioImg)
                            + " no coincide con la tarjeta CR80 (ratio 1.58). Al ajustarse automáticamente la imagen se distorsionará. "
                            + "Use el 'Editor Externo' para recortarla al formato correcto antes de importarla.");
                }
            }
        }

        // 2. VALIDACIÓN DE ELEMENTOS
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
            boolean saleDerecha = (ex + ew) > (CARD_WIDTH + BLEED_MARGIN);
            boolean saleAbajo = (ey + eh) > (CARD_HEIGHT + BLEED_MARGIN);

            if (elem instanceof TextoElemento) {
                if (saleIzquierda || saleArriba || saleDerecha || saleAbajo) {
                    avisos.add("[" + capa + "] El texto '" + name + "' sale fuera del área de impresión.");
                } else {
                    if (ex < 0 || ey < 0 || (ex + ew) > CARD_WIDTH || (ey + eh) > CARD_HEIGHT) {
                        avisos.add("[" + capa + "] El texto '" + name + "' invade la zona de sangrado (será recortado).");
                    } else {
                        if (ex < SAFETY_MARGIN || ey < SAFETY_MARGIN ||
                           (ex + ew) > (CARD_WIDTH - SAFETY_MARGIN) ||
                           (ey + eh) > (CARD_HEIGHT - SAFETY_MARGIN)) {
                            avisos.add("[" + capa + "] (Aviso leve) El texto '" + name + "' está fuera del margen de seguridad.");
                        }
                    }
                }
            } else if (elem instanceof ImagenElemento) {
                if (saleIzquierda || saleArriba || saleDerecha || saleAbajo) {
                    avisos.add("[" + capa + "] La imagen '" + name + "' o su caja de selección se sale excesivamente fuera de los límites.");
                }
            }
        }
    }
}
