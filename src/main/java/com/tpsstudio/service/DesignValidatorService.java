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
 * Servicio encargado de validar el diseño antes de exportarlo o imprimirlo.
 *
 * Comprueba resolución del fondo, proporción CR80, zona de sangre,
 * margen de seguridad y posibles conflictos con el troquel.
 */
public class DesignValidatorService {

    // Dimensiones base del canvas en el editor.
    private static final double CARD_WIDTH = 342.4;
    private static final double CARD_HEIGHT = 215.92;
    private static final double BLEED_MARGIN = 8.0;
    private static final double SAFETY_MARGIN = 12.0;

    /*
     * Resolución mínima recomendada para CR80 con sangre a 300 DPI:
     * 89,60 × 57,98 mm.
     */
    private static final double MIN_PRINT_W = (89.60 / 25.4) * 300;
    private static final double MIN_PRINT_H = (57.98 / 25.4) * 300;

    // =====================================================
    // Validación principal
    // =====================================================

    public List<String> validarDiseno(Proyecto proyecto) {
        List<String> avisos = new ArrayList<>();

        if (proyecto == null) {
            avisos.add("No hay ningún proyecto activo.");
            return avisos;
        }

        validarCara(
                proyecto.getElementosFrente(),
                proyecto.getFondoFrente(),
                true,
                proyecto.getTipoTroquel(),
                avisos,
                proyecto
        );

        boolean hasDorso = proyecto.getFondoDorso() != null || !proyecto.getElementosDorso().isEmpty();

        if (hasDorso) {
            validarCara(
                    proyecto.getElementosDorso(),
                    proyecto.getFondoDorso(),
                    false,
                    proyecto.getTipoTroquel(),
                    avisos,
                    proyecto
            );
        }

        return avisos;
    }

    // =====================================================
    // Validación por cara
    // =====================================================

    private void validarCara(List<Elemento> elementos,
                             ImagenFondoElemento fondo,
                             boolean esFrente,
                             TipoTroquel tipoTroquel,
                             List<String> avisos,
                             Proyecto proyecto) {

        String capa = esFrente ? "FRENTE" : "DORSO";

        double currentCardWidth =
                (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL)
                        ? CARD_HEIGHT
                        : CARD_WIDTH;

        double currentCardHeight =
                (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL)
                        ? CARD_WIDTH
                        : CARD_HEIGHT;

        // -------------------------------------------------
        // Fondo: resolución y proporción
        // -------------------------------------------------

        if (fondo != null && fondo.getImagen() != null) {
            double realW = fondo.getImagen().getWidth();
            double realH = fondo.getImagen().getHeight();

            double minW =
                    (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL)
                            ? MIN_PRINT_H
                            : MIN_PRINT_W;

            double minH =
                    (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL)
                            ? MIN_PRINT_W
                            : MIN_PRINT_H;

            double tolerance = 0.98;

            if (realW < (minW * tolerance) || realH < (minH * tolerance)) {
                avisos.add("[" + capa + "] El fondo tiene una resolución baja para impresión "
                        + String.format(
                        "(%.0f×%.0f px detectados, el óptimo es: %.0f×%.0f px). ",
                        realW, realH, minW, minH
                )
                        + "La calidad de impresión podría verse comprometida.");
            }

            if (realH > 0) {
                double ratioImg = realW / realH;

                double ratioCR80 =
                        (proyecto != null && proyecto.getOrientacion() == com.tpsstudio.model.enums.Orientacion.VERTICAL)
                                ? (53.98 / 85.60)
                                : (85.60 / 53.98);

                double diferencia = Math.abs(ratioImg - ratioCR80) / ratioCR80;

                if (diferencia > 0.20) {
                    avisos.add("[" + capa + "] La proporción de la imagen de fondo "
                            + String.format("(%.0f×%.0f px, ratio %.2f)", realW, realH, ratioImg)
                            + " no coincide con la tarjeta CR80 (ratio "
                            + String.format("%.2f", ratioCR80)
                            + "). Al ajustarse automáticamente la imagen se distorsionará. "
                            + "Use el 'Editor Externo' para recortarla al formato correcto antes de importarla.");
                }
            }
        }

        // -------------------------------------------------
        // Área de troquel
        // -------------------------------------------------

        double tx = -1;
        double ty = -1;
        double tw = 0;
        double th = 0;

        boolean hayTroquel = tipoTroquel != null && tipoTroquel != TipoTroquel.NINGUNO;

        if (hayTroquel) {
            double cx = currentCardWidth / 2.0;
            double cy = 18.0;

            if (tipoTroquel == TipoTroquel.CIRCULAR) {
                tw = 20.0;
                th = 20.0;
                tx = cx - 10.0;
                ty = cy - 10.0;

            } else if (tipoTroquel == TipoTroquel.ALARGADO) {
                tw = 56.0;
                th = 12.0;
                tx = cx - 28.0;
                ty = cy - 6.0;
            }
        }

        // -------------------------------------------------
        // Elementos del diseño
        // -------------------------------------------------

        for (Elemento elem : elementos) {
            if (!elem.isVisible()) continue;

            double ex = elem.getX();
            double ey = elem.getY();
            double ew = elem.getWidth();
            double eh = elem.getHeight();

            String name = elem.getEtiqueta() != null && !elem.getEtiqueta().isEmpty()
                    ? elem.getEtiqueta()
                    : elem.getNombre();

            boolean saleIzquierda = ex < -BLEED_MARGIN;
            boolean saleArriba = ey < -BLEED_MARGIN;
            boolean saleDerecha = (ex + ew) > (currentCardWidth + BLEED_MARGIN);
            boolean saleAbajo = (ey + eh) > (currentCardHeight + BLEED_MARGIN);

            String tipoLabel = "El elemento";

            if (elem instanceof TextoElemento) {
                tipoLabel = "El texto";
            } else if (elem instanceof ImagenElemento) {
                tipoLabel = "La imagen";
            } else if (elem instanceof com.tpsstudio.model.elements.ElementoCodigo) {
                tipoLabel = "El código (QR/Barras)";
            }

            if (saleIzquierda || saleArriba || saleDerecha || saleAbajo) {
                avisos.add("[" + capa + "] " + tipoLabel + " '" + name + "' sale fuera del área de impresión.");

            } else {
                if (ex < 0 || ey < 0 || (ex + ew) > currentCardWidth || (ey + eh) > currentCardHeight) {
                    avisos.add("[" + capa + "] " + tipoLabel + " '" + name + "' invade la zona de sangrado (será recortado).");

                } else {
                    if (ex < SAFETY_MARGIN || ey < SAFETY_MARGIN
                            || (ex + ew) > (currentCardWidth - SAFETY_MARGIN)
                            || (ey + eh) > (currentCardHeight - SAFETY_MARGIN)) {

                        if (elem instanceof com.tpsstudio.model.elements.ElementoCodigo) {
                            avisos.add("[" + capa + "] El código '" + name + "' está muy cerca del borde. Podría tener problemas de lectura al imprimir.");
                        } else {
                            avisos.add("[" + capa + "] (Aviso leve) " + tipoLabel + " '" + name + "' está fuera del margen de seguridad.");
                        }
                    }
                }
            }

            if (hayTroquel) {
                boolean intersecaX = ex < tx + tw && ex + ew > tx;
                boolean intersecaY = ey < ty + th && ey + eh > ty;

                if (intersecaX && intersecaY) {
                    avisos.add("[" + capa + "] CRÍTICO: El elemento '" + name
                            + "' está debajo del área de troquelado ("
                            + tipoTroquel.getDescripcion()
                            + ") y será perforado físicamente.");
                }
            }
        }
    }
}