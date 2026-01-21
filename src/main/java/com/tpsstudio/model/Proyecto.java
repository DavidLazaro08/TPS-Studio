package com.tpsstudio.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Modelo simple para representar un proyecto/trabajo de tarjeta CR80
 */
public class Proyecto {

    private final int id;
    private String nombre;
    private final String tipo; // "CR80"
    private boolean mostrandoFrente; // true = frente, false = dorso

    // Lista de elementos gráficos en la tarjeta
    private final ObservableList<Elemento> elementosFrente;
    private final ObservableList<Elemento> elementosDorso;

    private static int contadorId = 1;

    public Proyecto(String nombre) {
        this.id = contadorId++;
        this.nombre = nombre;
        this.tipo = "CR80";
        this.mostrandoFrente = true;
        this.elementosFrente = FXCollections.observableArrayList();
        this.elementosDorso = FXCollections.observableArrayList();
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

    /**
     * Obtiene la lista de elementos de la cara actual
     */
    public ObservableList<Elemento> getElementosActuales() {
        return mostrandoFrente ? elementosFrente : elementosDorso;
    }

    public ObservableList<Elemento> getElementosFrente() {
        return elementosFrente;
    }

    public ObservableList<Elemento> getElementosDorso() {
        return elementosDorso;
    }

    @Override
    public String toString() {
        return nombre + " (" + tipo + ")";
    }
}
