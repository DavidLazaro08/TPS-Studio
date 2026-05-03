package com.tpsstudio.service;

import java.io.File;

/**
 * Estrategia de salida para un trabajo de impresión.
 *
 * <p>Define cómo se entrega el PDF generado a su destino final.
 * El renderizado y la generación del PDF son siempre los mismos;
 * solo cambia adónde va el resultado.</p>
 *
 * <p><b>Implementaciones:</b></p>
 * <ul>
 *   <li>{@link SalidaPDFSistema} — Fase 1: abre el PDF en el visor del sistema
 *       y muestra el diálogo de impresión nativo del SO.</li>
 *   <li>{@code SalidaPrinterJob} — Fase 2 (futura): envía directamente a una
 *       {@code PrintService} seleccionada por el usuario dentro de la app,
 *       sin necesitar un visor externo.</li>
 * </ul>
 */
public interface SalidaImpresion {

    /**
     * Envía el archivo PDF al destino correspondiente.
     *
     * @param archivoPdf archivo PDF generado por el pipeline de exportación.
     *                   Puede ser un archivo temporal; la implementación decide
     *                   si lo elimina o no.
     * @throws Exception si el envío falla por cualquier motivo.
     */
    void enviar(File archivoPdf) throws Exception;
}
