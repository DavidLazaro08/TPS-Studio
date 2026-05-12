package com.tpsstudio.service;

import com.tpsstudio.model.print.SalidaImpresion;
import com.tpsstudio.model.print.TrabajoImpresion;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.view.dialogs.ExportDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio encargado de preparar y ejecutar trabajos de impresión.
 *
 * Genera un PDF temporal a partir del proyecto activo y lo entrega
 * a la estrategia de salida indicada.
 */
public class ImpresionService {

    // =====================================================
    // Ejecución principal
    // =====================================================

    public void ejecutar(TrabajoImpresion trabajo,
                         Proyecto proyecto,
                         FuenteDatos fuenteDatos,
                         SalidaImpresion salida) throws Exception {

        int totalRegistros = (fuenteDatos != null) ? fuenteDatos.getTotalRegistros() : 1;
        List<Integer> filas = resolverFilas(trabajo, totalRegistros);

        if (filas.isEmpty()) {
            throw new IllegalStateException("No hay registros válidos para imprimir.");
        }

        /*
         * En esta versión, PDFExportService exporta siempre el frente
         * y añade el dorso solo si se solicita anverso + reverso.
         */
        boolean dorso = trabajo.imprimirFrente() && trabajo.imprimirDorso();

        ExportDialog.ExportConfig config = new ExportDialog.ExportConfig(
                true,
                resolverRangoTexto(trabajo),
                dorso,
                trabajo.recortarSangre(),
                null,
                false
        );

        File archivoPdf = crearArchivoTemporal(proyecto.getNombre());

        try {
            PDFExportService pdfService = new PDFExportService(proyecto, fuenteDatos);
            pdfService.exportar(config, filas, archivoPdf);

        } catch (Exception ex) {
            archivoPdf.delete();
            throw ex;
        }

        salida.enviar(archivoPdf);
    }

    // =====================================================
    // Resolución de registros
    // =====================================================

    private List<Integer> resolverFilas(TrabajoImpresion trabajo, int totalRegistros) {
        if (trabajo.soloRegistroActual()) {
            List<Integer> filas = new ArrayList<>(1);
            int idx = trabajo.registroActualIdx();

            if (idx >= 0 && idx < totalRegistros) {
                filas.add(idx);
            } else {
                filas.add(0);
            }

            return filas;
        }

        String rango = (trabajo.rangoFilas() == null || trabajo.rangoFilas().isBlank())
                ? "TODOS"
                : trabajo.rangoFilas();

        try {
            return ExportDialog.parseRangoFilas(rango, totalRegistros);

        } catch (IllegalArgumentException ex) {
            List<Integer> todas = new ArrayList<>(totalRegistros);

            for (int i = 0; i < totalRegistros; i++) {
                todas.add(i);
            }

            return todas;
        }
    }

    private String resolverRangoTexto(TrabajoImpresion trabajo) {
        if (trabajo.soloRegistroActual()) {
            return String.valueOf(trabajo.registroActualIdx() + 1);
        }

        return (trabajo.rangoFilas() == null || trabajo.rangoFilas().isBlank())
                ? "TODOS"
                : trabajo.rangoFilas();
    }

    // =====================================================
    // Archivo temporal
    // =====================================================

    private File crearArchivoTemporal(String nombreProyecto) throws IOException {
        String nombreSeguro = nombreProyecto.replaceAll("[^a-zA-Z0-9]", "_");

        if (nombreSeguro.length() > 20) {
            nombreSeguro = nombreSeguro.substring(0, 20);
        }

        String prefijo = "TPS_Impr_" + nombreSeguro + "_";
        return File.createTempFile(prefijo, ".pdf");
    }
}