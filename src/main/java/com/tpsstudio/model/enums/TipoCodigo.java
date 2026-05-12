package com.tpsstudio.model.enums;

import com.google.zxing.BarcodeFormat;

/**
 * Tipos de códigos soportados por TPS Studio.
 *
 * Relaciona el nombre mostrado en la interfaz con el formato técnico
 * utilizado por ZXing para generar la imagen del código.
 */
public enum TipoCodigo {

    QR("Código QR", BarcodeFormat.QR_CODE, true),
    CODE128("Código 128", BarcodeFormat.CODE_128, false),
    CODE39("Código 39", BarcodeFormat.CODE_39, false),
    EAN13("EAN-13", BarcodeFormat.EAN_13, false),
    UPCA("UPC-A", BarcodeFormat.UPC_A, false);

    private final String nombre;
    private final BarcodeFormat format;
    private final boolean es2D;

    // =====================================================
    // Constructor
    // =====================================================

    TipoCodigo(String nombre, BarcodeFormat format, boolean es2D) {
        this.nombre = nombre;
        this.format = format;
        this.es2D = es2D;
    }

    // =====================================================
    // Getters
    // =====================================================

    public String getNombre() {
        return nombre;
    }

    public BarcodeFormat getFormat() {
        return format;
    }

    public boolean isEs2D() {
        return es2D;
    }

    // =====================================================
    // Representación
    // =====================================================

    @Override
    public String toString() {
        return nombre;
    }
}