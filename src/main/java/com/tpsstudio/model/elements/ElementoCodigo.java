package com.tpsstudio.model.elements;

import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.tpsstudio.model.enums.TipoCodigo;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.EnumMap;
import java.util.Map;

/**
 * Elemento gráfico que representa un código QR o código de barras.
 *
 * Utiliza ZXing para generar la imagen del código y permite vincular su
 * contenido a una columna de datos variables.
 */
public class ElementoCodigo extends Elemento {

    // =====================================================
    // Propiedades persistidas
    // =====================================================

    private TipoCodigo tipo;
    private String contenido;
    private String columnaVinculada;
    private String colorCodigo;
    private String colorFondo;
    private String nivelCorreccion;
    private int margen;
    private boolean mostrarTexto;
    private int fontSize;
    private boolean negrita;
    private boolean cursiva;

    // =====================================================
    // Caché en memoria
    // =====================================================

    private transient Image imagenCacheada;
    private transient String ultimoTextoGenerado;
    private transient TipoCodigo ultimoTipoGenerado;

    // =====================================================
    // Constructor
    // =====================================================

    public ElementoCodigo(String nombre, double x, double y, TipoCodigo tipo) {
        super(nombre, x, y, tipo.isEs2D() ? 60 : 80, tipo.isEs2D() ? 60 : 40);

        this.tipo = tipo;

        this.contenido = switch (tipo) {
            case QR -> "https://tpsstudio.com";
            case EAN13 -> "8412345678905";
            case UPCA -> "123456789012";
            default -> "12345678";
        };

        this.columnaVinculada = null;
        this.colorCodigo = "#000000";
        this.colorFondo = "#FFFFFF";
        this.nivelCorreccion = "M";
        this.margen = tipo.isEs2D() ? 1 : 10;
        this.mostrarTexto = true;
        this.fontSize = 10;
        this.negrita = true;
        this.cursiva = false;
    }

    // =====================================================
    // Generación de imagen
    // =====================================================

    public Image getImagen(String textoActual) {
        if (textoActual == null || textoActual.isBlank()) {
            return null;
        }

        if (imagenCacheada != null
                && textoActual.equals(ultimoTextoGenerado)
                && tipo == ultimoTipoGenerado) {
            return imagenCacheada;
        }

        imagenCacheada = generarImagen(textoActual);
        ultimoTextoGenerado = textoActual;
        ultimoTipoGenerado = tipo;

        return imagenCacheada;
    }

    public void invalidarCache() {
        imagenCacheada = null;
        ultimoTextoGenerado = null;
        ultimoTipoGenerado = null;
    }

    private Image generarImagen(String texto) {
        try {
            String textoProcesado = procesarTextoSegunTipo(texto, tipo);

            MultiFormatWriter writer = new MultiFormatWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);

            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, margen);

            if (tipo == TipoCodigo.QR) {
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.valueOf(nivelCorreccion));
            }

            int targetWidth = 400;
            int targetHeight = (int) (targetWidth * (height / width));

            if (tipo.isEs2D()) {
                targetHeight = targetWidth;
            }

            BitMatrix matrix = writer.encode(textoProcesado, tipo.getFormat(), targetWidth, targetHeight, hints);

            int argbColor = hexToArgb(colorCodigo);
            int argbFondo = hexToArgb(colorFondo);

            WritableImage imagen = new WritableImage(targetWidth, targetHeight);
            PixelWriter pw = imagen.getPixelWriter();

            for (int y = 0; y < targetHeight; y++) {
                for (int x = 0; x < targetWidth; x++) {
                    pw.setArgb(x, y, matrix.get(x, y) ? argbColor : argbFondo);
                }
            }

            return imagen;

        } catch (WriterException | IllegalArgumentException e) {
            return null;
        }
    }

    private int hexToArgb(String hex) {
        String h = (hex == null ? "#000000" : hex).replace("#", "");

        if (h.length() == 6) {
            h = "FF" + h;
        }

        try {
            return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException e) {
            return 0xFF000000;
        }
    }

    // =====================================================
    // Procesado de contenido
    // =====================================================

    public String getTextoProcesado(String textoRaw) {
        return procesarTextoSegunTipo(textoRaw, tipo);
    }

    private String procesarTextoSegunTipo(String texto, TipoCodigo tipo) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        if (tipo == TipoCodigo.EAN13) {
            String soloNumeros = texto.replaceAll("[^0-9]", "");

            if (soloNumeros.length() >= 12) {
                String base = soloNumeros.substring(0, 12);
                return base + calcularCheckDigitEAN(base);
            }

        } else if (tipo == TipoCodigo.UPCA) {
            String soloNumeros = texto.replaceAll("[^0-9]", "");

            if (soloNumeros.length() >= 11) {
                String base = soloNumeros.substring(0, 11);
                return base + calcularCheckDigitUPC(base);
            }
        }

        return texto;
    }

    private int calcularCheckDigitEAN(String base12) {
        int sum = 0;

        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(base12.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }

        int res = 10 - (sum % 10);
        return (res == 10) ? 0 : res;
    }

    private int calcularCheckDigitUPC(String base11) {
        int sum = 0;

        for (int i = 0; i < 11; i++) {
            int digit = Character.getNumericValue(base11.charAt(i));
            sum += (i % 2 == 0) ? digit * 3 : digit;
        }

        int res = 10 - (sum % 10);
        return (res == 10) ? 0 : res;
    }

    // =====================================================
    // Getters y setters
    // =====================================================

    public TipoCodigo getTipo() {
        return tipo;
    }

    public void setTipo(TipoCodigo tipo) {
        this.tipo = tipo;

        if (tipo.isEs2D() && width != height) {
            setWidth(Math.max(width, height));
            setHeight(getWidth());
        }

        invalidarCache();
    }

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

    public String getColorCodigo() {
        return colorCodigo;
    }

    public void setColorCodigo(String color) {
        this.colorCodigo = color;
        invalidarCache();
    }

    public String getColorFondo() {
        return colorFondo;
    }

    public void setColorFondo(String color) {
        this.colorFondo = color;
        invalidarCache();
    }

    public String getNivelCorreccion() {
        return nivelCorreccion;
    }

    public void setNivelCorreccion(String nivel) {
        this.nivelCorreccion = nivel;
        invalidarCache();
    }

    public int getMargen() {
        return margen;
    }

    public void setMargen(int margen) {
        this.margen = margen;
        invalidarCache();
    }

    public boolean isMostrarTexto() {
        return mostrarTexto;
    }

    public void setMostrarTexto(boolean mostrarTexto) {
        this.mostrarTexto = mostrarTexto;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public boolean isNegrita() {
        return negrita;
    }

    public void setNegrita(boolean negrita) {
        this.negrita = negrita;
    }

    public boolean isCursiva() {
        return cursiva;
    }

    public void setCursiva(boolean cursiva) {
        this.cursiva = cursiva;
    }

    public boolean esDinamico() {
        return columnaVinculada != null && !columnaVinculada.isBlank();
    }

    @Override
    public String toString() {
        return (tipo != null ? tipo.getNombre() : "Código")
                + (etiqueta != null && !etiqueta.isEmpty() ? " | " + etiqueta : "");
    }
}