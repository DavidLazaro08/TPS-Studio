package com.tpsstudio.service;

import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.view.dialogs.ExportDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestador del proceso de impresión.
 *
 * <p>Recibe un {@link TrabajoImpresion} con las opciones del usuario, construye
 * los parámetros que necesita {@link PDFExportService}, genera el PDF y lo entrega
 * a la estrategia {@link SalidaImpresion} indicada.</p>
 *
 * <p>Este servicio no tiene estado propio y puede instanciarse por trabajo.</p>
 *
 * <p><b>Limitación Fase 1:</b> {@link PDFExportService#exportar} siempre incluye
 * la cara delantera y opcionalmente la trasera. La opción "solo dorso" queda
 * pendiente para cuando PDFExportService la soporte de forma nativa.</p>
 */
public class ImpresionService {

    /**
     * Ejecuta el trabajo de impresión completo.
     *
     * @param trabajo     opciones del trabajo (caras, registros, sangre…).
     * @param proyecto    proyecto activo del que se renderiza el diseño.
     * @param fuenteDatos fuente de datos variables; puede ser {@code null}.
     * @param salida      estrategia de salida (PDF sistema, PrinterJob futuro…).
     * @throws Exception  si falla el renderizado, la generación del PDF o el envío.
     */
    public void ejecutar(TrabajoImpresion trabajo, Proyecto proyecto,
                         FuenteDatos fuenteDatos, SalidaImpresion salida) throws Exception {

        // 1. Resolver lista de filas (índices 0-based)
        int totalRegistros = (fuenteDatos != null) ? fuenteDatos.getTotalRegistros() : 1;
        List<Integer> filas = resolverFilas(trabajo, totalRegistros);

        if (filas.isEmpty()) {
            throw new IllegalStateException("No hay registros válidos para imprimir.");
        }

        // 2. Construir ExportConfig compatible con PDFExportService.
        //    imprimirDorso solo aplica si imprimirFrente también está activo;
        //    "solo dorso" no está soportado en Fase 1 (limitación del método exportar).
        boolean dorso = trabajo.imprimirFrente() && trabajo.imprimirDorso();

        ExportDialog.ExportConfig config = new ExportDialog.ExportConfig(
                true,                       // exportarRegistros
                resolverRangoTexto(trabajo), // rangoFilas (informativo; las filas ya están resueltas)
                dorso,                       // imprimirDorso
                trabajo.recortarSangre(),    // recortarSangre
                null,                        // configPrueba — no aplica en impresión
                false                        // exportarImprenta — no aplica
        );

        // 3. Generar PDF en archivo temporal
        File archivoPdf = crearArchivoTemporal(proyecto.getNombre());
        try {
            PDFExportService pdfService = new PDFExportService(proyecto, fuenteDatos);
            pdfService.exportar(config, filas, archivoPdf);
        } catch (Exception ex) {
            // Si el PDF falla, limpiamos el temporal para no dejar basura
            archivoPdf.delete();
            throw ex;
        }

        // 4. Enviar al destino (Desktop.print en Fase 1; PrinterJob directo en Fase 2)
        salida.enviar(archivoPdf);
    }

    // ──────────────────────────── helpers ────────────────────────────

    /**
     * Construye la lista de filas (índices 0-based) a partir de las opciones del trabajo.
     * Si no hay fuente de datos, devuelve siempre [0] (el diseño estático).
     */
    private List<Integer> resolverFilas(TrabajoImpresion trabajo, int totalRegistros) {
        if (trabajo.soloRegistroActual()) {
            // Registro actual: lista de un único índice
            List<Integer> filas = new ArrayList<>(1);
            int idx = trabajo.registroActualIdx();
            if (idx >= 0 && idx < totalRegistros) {
                filas.add(idx);
            } else {
                filas.add(0); // Fallback seguro
            }
            return filas;
        }

        // Rango de registros: delegar en el parser existente de ExportDialog
        String rango = (trabajo.rangoFilas() == null || trabajo.rangoFilas().isBlank())
                ? "TODOS"
                : trabajo.rangoFilas();

        try {
            return ExportDialog.parseRangoFilas(rango, totalRegistros);
        } catch (IllegalArgumentException ex) {
            // Si el rango es inválido, imprimir todos como fallback seguro
            List<Integer> todas = new ArrayList<>(totalRegistros);
            for (int i = 0; i < totalRegistros; i++) todas.add(i);
            return todas;
        }
    }

    /**
     * Devuelve el texto de rango adecuado para {@code ExportConfig}.
     * Cuando se imprime el registro actual usamos su posición 1-based como texto.
     */
    private String resolverRangoTexto(TrabajoImpresion trabajo) {
        if (trabajo.soloRegistroActual()) {
            // Posición humana = índice 0-based + 1
            return String.valueOf(trabajo.registroActualIdx() + 1);
        }
        return (trabajo.rangoFilas() == null || trabajo.rangoFilas().isBlank())
                ? "TODOS"
                : trabajo.rangoFilas();
    }

    /**
     * Crea un archivo PDF temporal con nombre basado en el proyecto.
     * Se ubica en el directorio temporal del sistema operativo.
     */
    private File crearArchivoTemporal(String nombreProyecto) throws IOException {
        String prefijo = "TPS_Impr_"
                + nombreProyecto.replaceAll("[^a-zA-Z0-9]", "_").substring(
                        0, Math.min(nombreProyecto.length(), 20))
                + "_";
        return File.createTempFile(prefijo, ".pdf");
    }
}
