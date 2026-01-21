package com.tpsstudio.model;

import javafx.scene.image.Image;

/**
 * Elemento de imagen en la tarjeta CR80
 */
public class ImagenElemento extends Elemento {

    private String rutaArchivo;
    private Image imagen;
    private double opacity; // 0.0 - 1.0
    private boolean mantenerProporcion;
    private double originalWidth;
    private double originalHeight;

    public ImagenElemento(String nombre, double x, double y, String rutaArchivo, Image imagen) {
        super(nombre, x, y, 100, 100); // Tamaño por defecto
        this.rutaArchivo = rutaArchivo;
        this.imagen = imagen;
        this.opacity = 1.0;
        this.mantenerProporcion = true;

        // Ajustar tamaño al de la imagen si es posible
        if (imagen != null) {
            this.originalWidth = imagen.getWidth();
            this.originalHeight = imagen.getHeight();
            this.width = Math.min(imagen.getWidth(), 200);
            this.height = Math.min(imagen.getHeight(), 200);
        }
    }

    // Getters y setters
    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public Image getImagen() {
        return imagen;
    }

    public void setImagen(Image imagen) {
        this.imagen = imagen;
        // Actualizar dimensiones
        if (imagen != null) {
            this.originalWidth = imagen.getWidth();
            this.originalHeight = imagen.getHeight();
            this.width = Math.min(imagen.getWidth(), 200);
            this.height = Math.min(imagen.getHeight(), 200);
        }
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
    }

    public boolean isMantenerProporcion() {
        return mantenerProporcion;
    }

    public void setMantenerProporcion(boolean mantenerProporcion) {
        this.mantenerProporcion = mantenerProporcion;
    }

    public double getOriginalWidth() {
        return originalWidth;
    }

    public double getOriginalHeight() {
        return originalHeight;
    }
}
