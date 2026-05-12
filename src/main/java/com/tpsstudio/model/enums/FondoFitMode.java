package com.tpsstudio.model.enums;

/**
 * Modo de ajuste del fondo de la tarjeta.
 *
 * Define si la imagen de fondo cubre únicamente el tamaño final CR80
 * o también la zona de sangrado.
 */
public enum FondoFitMode {

    BLEED("Con sangre (CR80 + 2mm sangrado)", "89.60 × 57.98 mm"),
    FINAL("Sin sangre (CR80 final)", "85.60 × 53.98 mm");

    private final String displayName;
    private final String dimensions;

    // =====================================================
    // Constructor
    // =====================================================

    FondoFitMode(String displayName, String dimensions) {
        this.displayName = displayName;
        this.dimensions = dimensions;
    }

    // =====================================================
    // Getters
    // =====================================================

    public String getDisplayName() {
        return displayName;
    }

    public String getDimensions() {
        return dimensions;
    }

    public String getDescription() {
        return displayName + " - " + dimensions;
    }
}