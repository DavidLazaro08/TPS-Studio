package com.tpsstudio.model.elements;

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

        // Ajustar tamaño a proporción de foto carnet (32mm × 26mm ≈ 121px × 98px)
        if (imagen != null) {
            this.originalWidth = imagen.getWidth();
            this.originalHeight = imagen.getHeight();
            // Tamaño por defecto: foto carnet
            double maxW = 121; // 32mm a ~96 DPI
            double maxH = 98; // 26mm a ~96 DPI

            // Calcular escala para mantener proporción
            double scaleX = maxW / originalWidth;
            double scaleY = maxH / originalHeight;
            double scale = Math.min(scaleX, scaleY);

            this.width = originalWidth * scale;
            this.height = originalHeight * scale;
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
        // Actualizar dimensiones originales
        if (imagen != null) {
            this.originalWidth = imagen.getWidth();
            this.originalHeight = imagen.getHeight();

            // Mantener tamaño de foto carnet con proporción
            double maxW = 121;
            double maxH = 98;
            double scaleX = maxW / originalWidth;
            double scaleY = maxH / originalHeight;
            double scale = Math.min(scaleX, scaleY);

            this.width = originalWidth * scale;
            this.height = originalHeight * scale;
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
