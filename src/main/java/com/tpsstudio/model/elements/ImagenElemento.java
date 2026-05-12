package com.tpsstudio.model.elements;

import javafx.scene.image.Image;

/**
 * Elemento de imagen colocado sobre la tarjeta.
 *
 * Puede funcionar como imagen fija o como imagen vinculada a una columna
 * de datos variables, por ejemplo para fotografías de acreditaciones.
 */
public class ImagenElemento extends Elemento {

    private String rutaArchivo;
    private Image imagen;

    private double opacity;
    private boolean mantenerProporcion;

    private double originalWidth;
    private double originalHeight;

    // null = imagen fija; valor = nombre de columna vinculada
    private String columnaVinculada;

    // =====================================================
    // Constructor
    // =====================================================

    public ImagenElemento(String nombre, double x, double y, String rutaArchivo, Image imagen) {
        super(nombre, x, y, 100, 100);

        this.rutaArchivo = rutaArchivo;
        this.imagen = imagen;
        this.opacity = 1.0;
        this.mantenerProporcion = true;

        /*
         * Si se recibe una imagen real, se ajusta inicialmente a un tamaño
         * cómodo de foto tipo carnet, manteniendo su proporción.
         */
        if (imagen != null) {
            this.originalWidth = imagen.getWidth();
            this.originalHeight = imagen.getHeight();

            double maxW = 121;
            double maxH = 98;

            double scaleX = maxW / originalWidth;
            double scaleY = maxH / originalHeight;
            double scale = Math.min(scaleX, scaleY);

            this.width = originalWidth * scale;
            this.height = originalHeight * scale;
        }
    }

    // =====================================================
    // Archivo e imagen
    // =====================================================

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

        if (imagen != null) {
            this.originalWidth = imagen.getWidth();
            this.originalHeight = imagen.getHeight();

            double maxW = 121;
            double maxH = 98;

            double scaleX = maxW / originalWidth;
            double scaleY = maxH / originalHeight;
            double scale = Math.min(scaleX, scaleY);

            this.width = originalWidth * scale;
            this.height = originalHeight * scale;
        }
    }

    // =====================================================
    // Visualización
    // =====================================================

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

    // =====================================================
    // Dimensiones originales
    // =====================================================

    public double getOriginalWidth() {
        return originalWidth;
    }

    public double getOriginalHeight() {
        return originalHeight;
    }

    // =====================================================
    // Datos variables
    // =====================================================

    public String getColumnaVinculada() {
        return columnaVinculada;
    }

    public void setColumnaVinculada(String columnaVinculada) {
        this.columnaVinculada = columnaVinculada;
    }
}