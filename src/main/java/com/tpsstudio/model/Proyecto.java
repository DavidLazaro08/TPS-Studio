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

    // Fondos (uno por cara)
    private ImagenFondoElemento fondoFrente;
    private ImagenFondoElemento fondoDorso;

    // Preferencia de modo de ajuste para fondos
    private FondoFitMode fondoFitModePreferido;
    private boolean noVolverAPreguntarFondo;

    // Metadatos del proyecto (ubicación, cliente, etc.)
    private ProyectoMetadata metadata;

    private static int contadorId = 1;

    public Proyecto(String nombre) {
        this.id = contadorId++;
        this.nombre = nombre;
        this.tipo = "CR80";
        this.mostrandoFrente = true;
        this.elementosFrente = FXCollections.observableArrayList();
        this.elementosDorso = FXCollections.observableArrayList();
        this.fondoFrente = null;
        this.fondoDorso = null;
        this.fondoFitModePreferido = null;
        this.noVolverAPreguntarFondo = false;
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

    /**
     * Obtiene el fondo de la cara actual
     */
    public ImagenFondoElemento getFondoActual() {
        return mostrandoFrente ? fondoFrente : fondoDorso;
    }

    /**
     * Establece el fondo de la cara actual
     */
    public void setFondoActual(ImagenFondoElemento fondo) {
        if (mostrandoFrente) {
            fondoFrente = fondo;
        } else {
            fondoDorso = fondo;
        }
    }

    public ImagenFondoElemento getFondoFrente() {
        return fondoFrente;
    }

    public void setFondoFrente(ImagenFondoElemento fondoFrente) {
        this.fondoFrente = fondoFrente;
    }

    public ImagenFondoElemento getFondoDorso() {
        return fondoDorso;
    }

    public void setFondoDorso(ImagenFondoElemento fondoDorso) {
        this.fondoDorso = fondoDorso;
    }

    public FondoFitMode getFondoFitModePreferido() {
        return fondoFitModePreferido;
    }

    public void setFondoFitModePreferido(FondoFitMode fondoFitModePreferido) {
        this.fondoFitModePreferido = fondoFitModePreferido;
    }

    public boolean isNoVolverAPreguntarFondo() {
        return noVolverAPreguntarFondo;
    }

    public void setNoVolverAPreguntarFondo(boolean noVolverAPreguntarFondo) {
        this.noVolverAPreguntarFondo = noVolverAPreguntarFondo;
    }

    public ProyectoMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ProyectoMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return nombre + " (" + tipo + ")";
    }
}
