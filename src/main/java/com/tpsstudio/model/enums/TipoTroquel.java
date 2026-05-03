package com.tpsstudio.model.enums;

/**
 * Define los tipos de perforación (troquel) disponibles para una tarjeta física.
 * Se utiliza para generar guías visuales en el editor y realizar validaciones
 * geométricas previas a la impresión.
 */
public enum TipoTroquel {
    
    /**
     * Sin perforación. Se asume tarjeta sólida.
     */
    NINGUNO("Sin troquel"),
    
    /**
     * Perforación circular clásica (aprox. 5mm de diámetro),
     * habitualmente usada con pinzas o lanyards simples.
     */
    CIRCULAR("Troquel Circular"),
    
    /**
     * Perforación rectangular con bordes redondeados (aprox. 14x3mm),
     * estándar para lanyards con mosquetón plano.
     */
    ALARGADO("Troquel Alargado");

    private final String descripcion;

    TipoTroquel(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
