package com.tpsstudio.service;

/**
 * DTO inmutable que describe un trabajo de impresión.
 *
 * <p>Contiene todas las opciones que el usuario elige en {@code ImpresionDialog}.
 * Es independiente de JavaFX y de cualquier lógica de salida, por lo que puede
 * pasarse sin problemas entre hilos.</p>
 *
 * <p><b>Nota sobre índices:</b> {@code registroActualIdx} es 0-based internamente,
 * igual que {@code FuenteDatos}. La interfaz siempre muestra posición 1-based al
 * usuario (registro 1, 2, 3…). La conversión la hace {@code ImpresionDialog}.</p>
 *
 * @param imprimirFrente      true si se debe incluir la cara delantera.
 * @param imprimirDorso       true si se debe incluir la cara trasera.
 *                            Solo aplica cuando {@code imprimirFrente} también es true;
 *                            "solo dorso" queda para una fase posterior.
 * @param soloRegistroActual  true para imprimir únicamente el registro visible en pantalla.
 * @param rangoFilas          texto del rango cuando {@code soloRegistroActual} es false.
 *                            Formatos válidos: "TODOS", "1-5", "2,4,7", "1-3,5".
 * @param recortarSangre      true para generar el PDF sin área de sangrado (tamaño CR80 final).
 * @param registroActualIdx   índice 0-based del registro activo; usado cuando
 *                            {@code soloRegistroActual} es true.
 * @param nombreImpresora     nombre de la impresora elegida, o nulo si se usa el visor de PDF por defecto.
 */
public record TrabajoImpresion(
        boolean imprimirFrente,
        boolean imprimirDorso,
        boolean soloRegistroActual,
        String  rangoFilas,
        boolean recortarSangre,
        int     registroActualIdx,
        String  nombreImpresora
) {}
