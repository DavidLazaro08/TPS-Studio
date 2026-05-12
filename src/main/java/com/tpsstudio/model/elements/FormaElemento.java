package com.tpsstudio.model.elements;

/**
 * Elemento gráfico de forma geométrica.
 *
 * Permite representar rectángulos, elipses y líneas dentro de la tarjeta,
 * con configuración de borde, relleno, opacidad y redondeado.
 */
public class FormaElemento extends Elemento {

    public enum TipoForma {
        RECTANGULO,
        ELIPSE,
        LINEA
    }

    private TipoForma tipoForma;

    private String colorRelleno;
    private String colorBorde;

    private double grosorBorde;
    private boolean conRelleno;
    private boolean conBorde;

    private double radioCurvatura;
    private double opacidad;

    // =====================================================
    // Constructor
    // =====================================================

    public FormaElemento(String nombre,
                         double x,
                         double y,
                         double width,
                         double height,
                         TipoForma tipo) {

        super(nombre, x, y, width, height);

        this.tipoForma = tipo;
        this.colorBorde = "#000000";
        this.colorRelleno = "#4a6b7c";
        this.grosorBorde = 2.0;
        this.conRelleno = tipo != TipoForma.LINEA;
        this.conBorde = true;
        this.radioCurvatura = 0.0;
        this.opacidad = 1.0;
    }

    // =====================================================
    // Tipo de forma
    // =====================================================

    public TipoForma getTipoForma() {
        return tipoForma;
    }

    public void setTipoForma(TipoForma tipoForma) {
        this.tipoForma = tipoForma;
    }

    // =====================================================
    // Colores
    // =====================================================

    public String getColorRelleno() {
        return colorRelleno;
    }

    public void setColorRelleno(String colorRelleno) {
        this.colorRelleno = colorRelleno;
    }

    public String getColorBorde() {
        return colorBorde;
    }

    public void setColorBorde(String colorBorde) {
        this.colorBorde = colorBorde;
    }

    // =====================================================
    // Borde y relleno
    // =====================================================

    public double getGrosorBorde() {
        return grosorBorde;
    }

    public void setGrosorBorde(double grosorBorde) {
        this.grosorBorde = grosorBorde;
    }

    public boolean isConRelleno() {
        return conRelleno;
    }

    public void setConRelleno(boolean conRelleno) {
        this.conRelleno = conRelleno;
    }

    public boolean isConBorde() {
        return conBorde;
    }

    public void setConBorde(boolean conBorde) {
        this.conBorde = conBorde;
    }

    // =====================================================
    // Apariencia
    // =====================================================

    public double getRadioCurvatura() {
        return radioCurvatura;
    }

    public void setRadioCurvatura(double radioCurvatura) {
        this.radioCurvatura = radioCurvatura;
    }

    public double getOpacidad() {
        return opacidad;
    }

    public void setOpacidad(double opacidad) {
        this.opacidad = opacidad;
    }
}