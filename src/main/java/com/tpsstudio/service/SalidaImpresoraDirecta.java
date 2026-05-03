package com.tpsstudio.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;

import javax.print.PrintService;
import java.awt.print.PrinterJob;
import java.io.File;

/**
 * Estrategia de impresión que envía el documento directamente a una impresora física.
 *
 * <p>Utiliza Java Print Service (JPS) y Apache PDFBox para mandar el documento PDF
 * directamente al spooler de la impresora seleccionada, sin intervención de visores
 * externos (ideal para colas de producción en impresoras Evolis, Matica, etc.).</p>
 */
public class SalidaImpresoraDirecta implements SalidaImpresion {

    private final String nombreImpresora;

    public SalidaImpresoraDirecta(String nombreImpresora) {
        if (nombreImpresora == null || nombreImpresora.isBlank()) {
            throw new IllegalArgumentException("El nombre de la impresora no puede estar vacío");
        }
        this.nombreImpresora = nombreImpresora;
    }

    @Override
    public void enviar(File archivoPdf) throws Exception {
        PrintService targetService = null;
        
        // Buscar el PrintService que coincida con el nombre
        PrintService[] services = PrinterJob.lookupPrintServices();
        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(nombreImpresora)) {
                targetService = service;
                break;
            }
        }

        if (targetService == null) {
            throw new Exception("La impresora '" + nombreImpresora + "' ya no está disponible o no se encuentra instalada.");
        }

        // Enviar trabajo sincrónicamente usando PDFBox
        try (PDDocument documento = PDDocument.load(archivoPdf)) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(targetService);
            job.setPageable(new PDFPageable(documento));
            
            // Enviamos a la cola de Windows sin mostrar diálogo (impresión silenciosa)
            job.print();
        } finally {
            // Como el trabajo ya se ha enviado sincrónicamente a la cola (spooler),
            // el PDF temporal ya no es necesario y se puede borrar inmediatamente.
            if (archivoPdf.exists()) {
                archivoPdf.delete();
            }
        }
    }
}
