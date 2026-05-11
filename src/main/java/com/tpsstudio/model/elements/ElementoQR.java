package com.tpsstudio.model.elements;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.EnumMap;
import java.util.Map;

/**
 * Elemento QR para tarjetas CR80.
 *
 * <p>Soporta dos modos:</p>
 * <ul>
 *   <li><b>Estático:</b> el usuario escribe un texto fijo (ej. URL).</li>
 *   <li><b>Dinámico:</b> se vincula a una columna de la fuente de datos;
 *       el contenido cambia registro a registro en modo Producción.</li>
 * </ul>
 *
 * <p>La imagen QR se genera con ZXing y se cachea internamente.
 * El caché se invalida al cambiar {@code contenido}, colores o tamaño.</p>
 */
public class ElementoQR extends Elemento {

    // ─── Propiedades persistidas (Gson las serializa automáticamente) ─────────

    /** Texto codificado en el QR (modo estático). */
    private String contenido;

    /**
     * Nombre de la columna de la fuente de datos a vincular.
     * {@code null} → modo estático; de lo contrario → modo dinámico.
     */
    private String columnaVinculada;

    /** Color del módulo oscuro del QR en formato hex (#RRGGBB). */
    private String colorQR;

    /** Color del fondo del QR en formato hex (#RRGGBB). */
    private String colorFondo;

    /** Nivel de corrección de errores: L, M, Q, H. */
    private String nivelCorreccion;

    /** Margen exterior del QR (quiet zone), en módulos. */
    private int margen;

    // ─── Caché en memoria (transient → Gson no la serializa) ──────────────────

    /** Imagen JavaFX cacheada. Se regenera cuando cambian los datos. */
    private transient Image imagenQRCacheada;

    /**
     * Último texto con el que se generó el caché.
     * Permite detectar si hay que regenerar.
     */
    private transient String ultimoTextoGenerado;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Crea un ElementoQR con valores por defecto listos para usar.
     *
     * @param nombre  nombre de la capa (ej. "QR 1")
     * @param x       posición X en la tarjeta (px)
     * @param y       posición Y en la tarjeta (px)
     */
    public ElementoQR(String nombre, double x, double y) {
        // 60×60 px ≈ 15×15 mm a escala 4px/mm — tamaño razonable para un QR de tarjeta
        super(nombre, x, y, 60, 60);
        this.contenido        = "https://tpsstudio.com";
        this.columnaVinculada = null;
        this.colorQR          = "#000000";
        this.colorFondo       = "#FFFFFF";
        this.nivelCorreccion  = "M";
        this.margen           = 1;
    }

    // ─── Generación de imagen ─────────────────────────────────────────────────

    /**
     * Devuelve una imagen JavaFX del código QR para el texto dado.
     *
     * <p>El resultado se cachea: si {@code textoActual} coincide con el
     * último texto generado y la imagen existe, se reutiliza directamente
     * sin re-invocar ZXing.</p>
     *
     * @param textoActual texto a codificar (puede ser el contenido estático
     *                    o el valor dinámico del registro actual).
     * @return imagen lista para dibujar en el canvas, o {@code null} si falla.
     */
    public Image getImagenQR(String textoActual) {
        if (textoActual == null || textoActual.isBlank()) {
            return null;
        }

        // Reutilizar caché si el texto no cambió
        if (imagenQRCacheada != null && textoActual.equals(ultimoTextoGenerado)) {
            return imagenQRCacheada;
        }

        // Generar nueva imagen con ZXing
        imagenQRCacheada  = generarImagenQR(textoActual);
        ultimoTextoGenerado = textoActual;
        return imagenQRCacheada;
    }

    /**
     * Fuerza la invalidación del caché.
     * Necesario al cambiar colores, margen o nivel de corrección.
     */
    public void invalidarCache() {
        imagenQRCacheada    = null;
        ultimoTextoGenerado = null;
    }

    /**
     * Llama a ZXing para generar la {@link BitMatrix} y la convierte
     * a una {@link WritableImage} de JavaFX con los colores configurados.
     */
    private Image generarImagenQR(String texto) {
        try {
            QRCodeWriter writer = new QRCodeWriter();

            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.valueOf(nivelCorreccion));
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, margen);

            // Tamaño de generación interno: usamos 200px para máxima nitidez;
            // JavaFX escala al dibujar con drawImage sin pérdida de calidad.
            int size = 200;
            BitMatrix matrix = writer.encode(texto, BarcodeFormat.QR_CODE, size, size, hints);

            // Parsear colores hex → componentes ARGB
            int argbQR     = hexToArgb(colorQR);
            int argbFondo  = hexToArgb(colorFondo);

            WritableImage imagen = new WritableImage(size, size);
            PixelWriter pw = imagen.getPixelWriter();

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int argb = matrix.get(x, y) ? argbQR : argbFondo;
                    pw.setArgb(x, y, argb);
                }
            }

            return imagen;

        } catch (WriterException | IllegalArgumentException e) {
            // ZXing falla (texto vacío, error interno, etc.)
            return null;
        }
    }

    /**
     * Convierte un color hexadecimal (#RRGGBB o #AARRGGBB) a un int ARGB.
     * Si no tiene canal alfa, se añade FF (opaco).
     */
    private int hexToArgb(String hex) {
        String h = hex.replace("#", "");
        if (h.length() == 6) {
            h = "FF" + h; // sin alfa → completamente opaco
        }
        return (int) Long.parseLong(h, 16);
    }

    // ─── Getters y Setters ────────────────────────────────────────────────────

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
        invalidarCache();
    }

    public String getColumnaVinculada() {
        return columnaVinculada;
    }

    public void setColumnaVinculada(String columnaVinculada) {
        this.columnaVinculada = columnaVinculada;
    }

    public String getColorQR() {
        return colorQR;
    }

    public void setColorQR(String colorQR) {
        this.colorQR = colorQR;
        invalidarCache();
    }

    public String getColorFondo() {
        return colorFondo;
    }

    public void setColorFondo(String colorFondo) {
        this.colorFondo = colorFondo;
        invalidarCache();
    }

    public String getNivelCorreccion() {
        return nivelCorreccion;
    }

    public void setNivelCorreccion(String nivelCorreccion) {
        this.nivelCorreccion = nivelCorreccion;
        invalidarCache();
    }

    public int getMargen() {
        return margen;
    }

    public void setMargen(int margen) {
        this.margen = margen;
        invalidarCache();
    }

    /** @return {@code true} si el QR está vinculado a una columna de datos. */
    public boolean esDinamico() {
        return columnaVinculada != null && !columnaVinculada.isBlank();
    }
}
