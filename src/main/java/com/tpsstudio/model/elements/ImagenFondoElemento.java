package com.tpsstudio.model.elements;

import com.tpsstudio.model.enums.FondoFitMode;
import javafx.scene.image.Image;

/**
 * Elemento de fondo de la tarjeta.
 *
 * Representa la imagen base del diseño y se ajusta según el modo elegido:
 * con sangre o al tamaño final CR80.
 */
public class ImagenFondoElemento extends Elemento {

    private String rutaArchivo;
    private Image imagen;
    private FondoFitMode fitMode;

    // =====================================================
    // Constructor
    // =====================================================

    public ImagenFondoElemento(String rutaArchivo,
                               Image imagen,
                               double cardWidth,
                               double cardHeight,
                               FondoFitMode fitMode) {

        super("[Fondo]", 0, 0, cardWidth, cardHeight);

        this.rutaArchivo = rutaArchivo;
        this.imagen = imagen;
        this.fitMode = fitMode != null ? fitMode : FondoFitMode.BLEED;

        this.locked = true;
    }

    // =====================================================
    // Ajuste de tamaño
    // =====================================================

    public void ajustarATamaño(double cardWidth, double cardHeight, double bleedPx) {
        if (fitMode == FondoFitMode.BLEED) {
            this.x = -bleedPx;
            this.y = -bleedPx;
            this.width = cardWidth + (bleedPx * 2);
            this.height = cardHeight + (bleedPx * 2);

        } else {
            this.x = 0;
            this.y = 0;
            this.width = cardWidth;
            this.height = cardHeight;
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
    }

    // =====================================================
    // Modo de ajuste
    // =====================================================

    public FondoFitMode getFitMode() {
        return fitMode;
    }

    public void setFitMode(FondoFitMode fitMode) {
        this.fitMode = fitMode;
    }

    // =====================================================
    // Orden visual
    // =====================================================

    public int getZOrder() {
        return -1000;
    }
}