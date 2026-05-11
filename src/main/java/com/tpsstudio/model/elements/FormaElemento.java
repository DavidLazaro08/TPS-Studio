package com.tpsstudio.model.elements;

/**
 * Elemento gráfico de forma geométrica: rectángulo, elipse o línea.
 * Hereda posición y dimensiones de Elemento.
 */
public class FormaElemento extends Elemento {

    public enum TipoForma { RECTANGULO, ELIPSE, LINEA }

    private TipoForma tipoForma;
    private String colorRelleno;   // null → sin relleno (transparente)
    private String colorBorde;
    private double grosorBorde;
    private boolean conRelleno;
    private boolean conBorde;
    private double radioCurvatura; // Para rectángulos redondeados
    private double opacidad;       // 0.0 a 1.0

    public FormaElemento(String nombre, double x, double y, double width, double height, TipoForma tipo) {
        super(nombre, x, y, width, height);
        this.tipoForma      = tipo;
        this.colorBorde     = "#000000";
        this.colorRelleno   = "#4a6b7c";
        this.grosorBorde    = 2.0;
        this.conRelleno     = (tipo != TipoForma.LINEA);
        this.conBorde       = true;
        this.radioCurvatura = 0.0;
        this.opacidad       = 1.0;
    }

    public TipoForma getTipoForma() { return tipoForma; }
    public void setTipoForma(TipoForma tipoForma) { this.tipoForma = tipoForma; }

    public String getColorRelleno() { return colorRelleno; }
    public void setColorRelleno(String colorRelleno) { this.colorRelleno = colorRelleno; }

    public String getColorBorde() { return colorBorde; }
    public void setColorBorde(String colorBorde) { this.colorBorde = colorBorde; }

    public double getGrosorBorde() { return grosorBorde; }
    public void setGrosorBorde(double grosorBorde) { this.grosorBorde = grosorBorde; }

    public boolean isConRelleno() { return conRelleno; }
    public void setConRelleno(boolean conRelleno) { this.conRelleno = conRelleno; }

    public boolean isConBorde() { return conBorde; }
    public void setConBorde(boolean conBorde) { this.conBorde = conBorde; }

    public double getRadioCurvatura() { return radioCurvatura; }
    public void setRadioCurvatura(double radioCurvatura) { this.radioCurvatura = radioCurvatura; }

    public double getOpacidad() { return opacidad; }
    public void setOpacidad(double opacidad) { this.opacidad = opacidad; }
}
