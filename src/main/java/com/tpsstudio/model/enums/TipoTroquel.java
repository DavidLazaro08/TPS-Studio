package com.tpsstudio.model.enums;

/**
 * Tipos de perforación física disponibles para la tarjeta.
 *
 * Se usan para mostrar guías visuales en el editor y para validar que
 * ningún elemento importante quede dentro de la zona troquelada.
 */
public enum TipoTroquel {

    NINGUNO("Sin troquel"),
    CIRCULAR("Troquel Circular"),
    ALARGADO("Troquel Alargado");

    private final String descripcion;

    // =====================================================
    // Constructor
    // =====================================================

    TipoTroquel(String descripcion) {
        this.descripcion = descripcion;
    }

    // =====================================================
    // Getter
    // =====================================================

    public String getDescripcion() {
        return descripcion;
    }

    // =====================================================
    // Representación
    // =====================================================

    @Override
    public String toString() {
        return descripcion;
    }
}