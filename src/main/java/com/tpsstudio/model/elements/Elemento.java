package com.tpsstudio.model.elements;

/**
 * Clase base para todos los elementos gráficos colocados sobre la tarjeta.
 *
 * Define posición, tamaño, visibilidad, bloqueo y nombre común para textos,
 * imágenes, formas, fondos y códigos.
 */
public abstract class Elemento {

    protected double x;
    protected double y;
    protected double width;
    protected double height;

    protected boolean visible;
    protected boolean locked;

    protected String nombre;
    protected String etiqueta;

    // =====================================================
    // Constructor
    // =====================================================

    public Elemento(String nombre, double x, double y, double width, double height) {
        this.nombre = nombre;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.visible = true;
        this.locked = false;
    }

    // =====================================================
    // Posición y tamaño
    // =====================================================

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    // =====================================================
    // Estado
    // =====================================================

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    // =====================================================
    // Identificación
    // =====================================================

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public void setEtiqueta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    // =====================================================
    // Utilidades
    // =====================================================

    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    @Override
    public String toString() {
        if (etiqueta != null && !etiqueta.isEmpty()) {
            return etiqueta;
        }

        return nombre;
    }
}