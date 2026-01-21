package com.tpsstudio.model;

import javafx.scene.image.Image;

/**
 * Elemento de fondo para la tarjeta CR80
 * Siempre bloqueado, siempre al fondo (z-order -1000)
 */
public class ImagenFondoElemento extends Elemento {

    private String rutaArchivo;
    private Image imagen;
    private boolean ajustarASangrado; // true = con bleed, false = solo CR80

    public ImagenFondoElemento(String rutaArchivo, Image imagen, double cardWidth, double cardHeight) {
        super("[Fondo]", 0, 0, cardWidth, cardHeight);
        this.rutaArchivo = rutaArchivo;
        this.imagen = imagen;
        this.ajustarASangrado = true; // Por defecto con sangrado
        this.locked = true; // Siempre bloqueado
    }

    /**
     * Ajusta el tamaño del fondo al tamaño de la tarjeta
     */
    public void ajustarATamaño(double cardWidth, double cardHeight, double bleedPx) {
        this.x = ajustarASangrado ? -bleedPx : 0;
        this.y = ajustarASangrado ? -bleedPx : 0;
        this.width = ajustarASangrado ? cardWidth + (bleedPx * 2) : cardWidth;
        this.height = ajustarASangrado ? cardHeight + (bleedPx * 2) : cardHeight;
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
    }

    public boolean isAjustarASangrado() {
        return ajustarASangrado;
    }

    public void setAjustarASangrado(boolean ajustarASangrado) {
        this.ajustarASangrado = ajustarASangrado;
    }

    /**
     * Z-order siempre al fondo
     */
    public int getZOrder() {
        return -1000;
    }
}
