package com.tpsstudio.model;

/**
 * Modo de ajuste para el fondo de la tarjeta CR80
 */
public enum FondoFitMode {
    /**
     * Con sangrado (bleed): el fondo cubre CR80 + 2mm de sangrado por lado
     */
    BLEED("Con sangre (CR80 + 2mm sangrado)", "89.60 × 57.98 mm"),

    /**
     * Sin sangrado: el fondo cubre solo el área final CR80
     */
    FINAL("Sin sangre (CR80 final)", "85.60 × 53.98 mm");

    private final String displayName;
    private final String dimensions;

    FondoFitMode(String displayName, String dimensions) {
        this.displayName = displayName;
        this.dimensions = dimensions;
    }

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
