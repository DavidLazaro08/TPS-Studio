package com.tpsstudio.model;

/**
 * Modelo simple para representar un proyecto/trabajo de tarjeta CR80
 */
public class Proyecto {

    private final int id;
    private String nombre;
    private final String tipo; // "CR80"
    private boolean mostrandoFrente; // true = frente, false = dorso

    private static int contadorId = 1;

    public Proyecto(String nombre) {
        this.id = contadorId++;
        this.nombre = nombre;
        this.tipo = "CR80";
        this.mostrandoFrente = true;
    }

    // Getters y setters
    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public boolean isMostrandoFrente() {
        return mostrandoFrente;
    }

    public void setMostrandoFrente(boolean mostrandoFrente) {
        this.mostrandoFrente = mostrandoFrente;
    }

    @Override
    public String toString() {
        return nombre + " (" + tipo + ")";
    }
}
