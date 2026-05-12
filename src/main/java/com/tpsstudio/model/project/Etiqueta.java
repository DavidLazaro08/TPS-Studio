package com.tpsstudio.model.project;

import java.util.UUID;

/**
 * Categoría o etiqueta utilizada para organizar proyectos.
 *
 * Cada etiqueta tiene un identificador único, un nombre visible y un color
 * asociado para mostrarla en la interfaz.
 */
public class Etiqueta {

    private final String id;
    private String nombre;
    private String color;

    // =====================================================
    // Constructores
    // =====================================================

    public Etiqueta() {
        this.id = UUID.randomUUID().toString();
    }

    public Etiqueta(String nombre, String color) {
        this.id = UUID.randomUUID().toString();
        this.nombre = nombre;
        this.color = color;
    }

    // =====================================================
    // Getters y setters
    // =====================================================

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getColor() {
        return color != null ? color : "#6C63FF";
    }

    public void setColor(String color) {
        this.color = color;
    }

    // =====================================================
    // Representación
    // =====================================================

    @Override
    public String toString() {
        return nombre;
    }
}