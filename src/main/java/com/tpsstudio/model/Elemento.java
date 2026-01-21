package com.tpsstudio.model;

/**
 * Clase base abstracta para elementos gráficos en la tarjeta CR80
 */
public abstract class Elemento {

    protected double x; // Posición X en píxeles
    protected double y; // Posición Y en píxeles
    protected double width; // Ancho en píxeles
    protected double height; // Alto en píxeles
    protected boolean visible; // Visible en el canvas
    protected boolean locked; // Bloqueado (no editable)
    protected String nombre; // Nombre del elemento

    public Elemento(String nombre, double x, double y, double width, double height) {
        this.nombre = nombre;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.visible = true;
        this.locked = false;
    }

    // Getters y setters
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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /**
     * Verifica si un punto está dentro del elemento
     */
    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
