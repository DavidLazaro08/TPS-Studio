package com.tpsstudio.model.project;

import java.util.UUID;

/**
 * Categoría/Etiqueta para organizar proyectos.
 *
 * <p>Representa una categoría definida por el usuario (ej: "FERIA", "CORPORATIVO")
 * que puede asignarse a uno o varios proyectos. Las etiquetas son globales al usuario
 * y se persisten en las preferencias del sistema junto con la selección de filtro activa.</p>
 *
 * <p>Un {@link Proyecto} almacena una lista de IDs de etiquetas asignadas
 * ({@code etiquetaIds}), lo que permite retrocompatibilidad: proyectos guardados
 * antes de esta funcionalidad simplemente tendrán la lista vacía.</p>
 */
public class Etiqueta {

    private final String id;
    private String nombre;
    private String color; // Hex, ej: "#6C63FF"

    /** Constructor completo para deserialización Gson */
    public Etiqueta() {
        this.id = UUID.randomUUID().toString();
    }

    public Etiqueta(String nombre, String color) {
        this.id = UUID.randomUUID().toString();
        this.nombre = nombre;
        this.color = color;
    }

    public String getId() { return id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getColor() { return color != null ? color : "#6C63FF"; }
    public void setColor(String color) { this.color = color; }

    @Override
    public String toString() { return nombre; }
}
