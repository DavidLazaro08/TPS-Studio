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

    public FormaElemento(String nombre, double x, double y, double width, double height, TipoForma tipo) {
        super(nombre, x, y, width, height);
        this.tipoForma    = tipo;
        this.colorBorde   = "#000000";
        this.colorRelleno = "#4a6b7c";
        this.grosorBorde  = 2.0;
        this.conRelleno   = (tipo != TipoForma.LINEA); // Las líneas nunca tienen relleno
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
}
