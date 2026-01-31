package com.tpsstudio.model.elements;

/**
 * Elemento de texto en la tarjeta CR80
 */
public class TextoElemento extends Elemento {

    private String contenido;
    private double fontSize;
    private String fontFamily;
    private String color; // Formato hex: #RRGGBB
    private String alineacion; // "LEFT", "CENTER", "RIGHT"
    private boolean negrita;
    private boolean cursiva;

    public TextoElemento(String nombre, double x, double y) {
        super(nombre, x, y, 100, 30); // Tamaño por defecto
        this.contenido = "Texto";
        this.fontSize = 14;
        this.fontFamily = "Arial";
        this.color = "#000000";
        this.alineacion = "LEFT";
        this.negrita = false;
        this.cursiva = false;
    }

    // Getters y setters
    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = fontSize;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getAlineacion() {
        return alineacion;
    }

    public void setAlineacion(String alineacion) {
        this.alineacion = alineacion;
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
}
