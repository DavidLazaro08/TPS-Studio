package com.tpsstudio.model.elements;

import com.tpsstudio.model.enums.FondoFitMode;
import javafx.scene.image.Image;

/**
 * Elemento de fondo para la tarjeta CR80
 * Siempre bloqueado, siempre al fondo (z-order -1000)
 */
public class ImagenFondoElemento extends Elemento {

    private String rutaArchivo;
    private Image imagen;
    private FondoFitMode fitMode; // BLEED = con sangre, FINAL = sin sangre

    public ImagenFondoElemento(String rutaArchivo, Image imagen, double cardWidth, double cardHeight,
            FondoFitMode fitMode) {
        super("[Fondo]", 0, 0, cardWidth, cardHeight);
        this.rutaArchivo = rutaArchivo;
        this.imagen = imagen;
        this.fitMode = fitMode != null ? fitMode : FondoFitMode.BLEED; // Por defecto con sangrado
        this.locked = true; // Siempre bloqueado
    }

    /**
     * Ajusta el tamaño del fondo según el modo de ajuste
     */
    public void ajustarATamaño(double cardWidth, double cardHeight, double bleedPx) {
        if (fitMode == FondoFitMode.BLEED) {
            // Con sangrado: cubre CR80 + bleed
            this.x = -bleedPx;
            this.y = -bleedPx;
            this.width = cardWidth + (bleedPx * 2);
            this.height = cardHeight + (bleedPx * 2);
        } else {
            // Sin sangrado: solo CR80 final
            this.x = 0;
            this.y = 0;
            this.width = cardWidth;
            this.height = cardHeight;
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
    }

    public FondoFitMode getFitMode() {
        return fitMode;
    }

    public void setFitMode(FondoFitMode fitMode) {
        this.fitMode = fitMode;
    }

    /**
     * Z-order siempre al fondo
     */
    public int getZOrder() {
        return -1000;
    }
}
