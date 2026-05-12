package com.tpsstudio.util;

import com.tpsstudio.view.managers.EditorCanvasManager;

/**
 * Utilidad para convertir entre píxeles del lienzo y milímetros reales (CR80).
 */
public class UnitConverter {

    // Ratio: 530px / 85.6mm = 6.1915...
    public static final double PX_PER_MM = EditorCanvasManager.CARD_WIDTH / 85.6;
    
    // Ratio para fuentes (aproximado para que 12pt parezca un 12pt real)
    // 1pt = 1/72 inch, 1 inch = 25.4mm.
    // Pixels de un punto en nuestro lienzo = (25.4 / 72) * PX_PER_MM
    public static final double PT_TO_PX = (25.4 / 72.0) * PX_PER_MM;

    public static double pxToMm(double px) {
        return px / PX_PER_MM;
    }

    public static double mmToPx(double mm) {
        return mm * PX_PER_MM;
    }

    public static String formatMm(double px) {
        return String.format(java.util.Locale.US, "%.1f mm", pxToMm(px));
    }
    
    public static double ptToPx(double pt) {
        return pt * PT_TO_PX;
    }
    
    public static double pxToPt(double px) {
        return px / PT_TO_PX;
    }
}
