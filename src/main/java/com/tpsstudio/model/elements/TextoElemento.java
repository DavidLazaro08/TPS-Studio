package com.tpsstudio.model.elements;

/**
 * Elemento de texto colocado sobre la tarjeta.
 *
 * Permite configurar contenido, fuente, color, alineación y comportamiento
 * de ajuste dentro de su caja delimitadora. También puede vincularse a una
 * columna de datos variables.
 */
public class TextoElemento extends Elemento {

    private String contenido;
    private double fontSize;
    private String fontFamily;
    private String color;
    private String alineacion;

    private boolean negrita;
    private boolean cursiva;

    /*
     * Se usan Boolean en lugar de boolean para mantener compatibilidad
     * con proyectos antiguos guardados sin estas propiedades.
     */
    private Boolean saltoLinea;
    private Boolean autoAjustar;

    // null = texto fijo; valor = nombre de columna vinculada
    private String columnaVinculada;

    // =====================================================
    // Constructor
    // =====================================================

    public TextoElemento(String nombre, double x, double y) {
        super(nombre, x, y, 100, 30);

        this.contenido = "Texto";
        this.fontSize = 14;
        this.fontFamily = "Arial";
        this.color = "#000000";
        this.alineacion = "LEFT";

        this.negrita = false;
        this.cursiva = false;

        this.saltoLinea = true;
        this.autoAjustar = false;
    }

    // =====================================================
    // Contenido
    // =====================================================

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public String getColumnaVinculada() {
        return columnaVinculada;
    }

    public void setColumnaVinculada(String columnaVinculada) {
        this.columnaVinculada = columnaVinculada;
    }

    // =====================================================
    // Fuente y estilo
    // =====================================================

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

    // =====================================================
    // Ajuste de texto
    // =====================================================

    public boolean isSaltoLinea() {
        return saltoLinea == null ? true : saltoLinea;
    }

    public void setSaltoLinea(boolean saltoLinea) {
        this.saltoLinea = saltoLinea;
    }

    public boolean isAutoAjustar() {
        return autoAjustar == null ? false : autoAjustar;
    }

    public void setAutoAjustar(boolean autoAjustar) {
        this.autoAjustar = autoAjustar;
    }
}