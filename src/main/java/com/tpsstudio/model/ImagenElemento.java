package com.tpsstudio.model;

import javafx.scene.image.Image;

/**
 * Elemento de imagen en la tarjeta CR80
 */
public class ImagenElemento extends Elemento {

    private String rutaArchivo;
    private Image imagen;

    public ImagenElemento(String nombre, double x, double y, String rutaArchivo, Image imagen) {
        super(nombre, x, y, 100, 100); // Tamaño por defecto
        this.rutaArchivo = rutaArchivo;
        this.imagen = imagen;

        // Ajustar tamaño al de la imagen si es posible
        if (imagen != null) {
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
            this.width = Math.min(imagen.getWidth(), 200);
            this.height = Math.min(imagen.getHeight(), 200);
        }
    }
}
