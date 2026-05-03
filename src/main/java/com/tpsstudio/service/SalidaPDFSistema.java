package com.tpsstudio.service;

import java.awt.Desktop;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Salida de impresión mediante el sistema operativo — Fase 1.
 *
 * <p>Entrega el PDF al visor predeterminado del sistema (Microsoft Edge PDF,
 * Adobe Reader, Foxit…), que muestra el diálogo de impresión nativo del SO.
 * El usuario selecciona la impresora desde ese diálogo, como en cualquier
 * aplicación profesional de impresión de tarjetas.</p>
 *
 * <p>El archivo temporal se elimina automáticamente pasados
 * {@value #SEGUNDOS_ESPERA_BORRADO} segundos — margen suficiente para que
 * el proceso del visor lea el PDF antes de que desaparezca.</p>
 *
 * <p><b>Para Fase 2:</b> crear {@code SalidaPrinterJob implements SalidaImpresion}
 * que use {@code PrintServiceLookup} + {@code PrinterJob} para envío directo
 * a una impresora elegida dentro de TPS Studio. No hay que tocar nada más.</p>
 */
public class SalidaPDFSistema implements SalidaImpresion {

    /** Segundos antes de borrar el PDF temporal tras enviarlo al SO. */
    private static final int SEGUNDOS_ESPERA_BORRADO = 30;

    /**
     * Indica si la impresión mediante {@code Desktop} está disponible en este sistema.
     * En Windows 10 / 11 devuelve true prácticamente siempre.
     */
    public static boolean isSupported() {
        return Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }

    /**
     * Abre el PDF con el visor predeterminado del sistema y lanza el diálogo
     * de impresión nativo. El archivo temporal se programará para eliminarse
     * automáticamente.
     *
     * @param archivoPdf PDF a entregar. Normalmente es un archivo temporal.
     * @throws Exception si {@code Desktop.print()} falla.
     */
    @Override
    public void enviar(File archivoPdf) throws Exception {
        Desktop.getDesktop().open(archivoPdf);
        programarEliminacion(archivoPdf);
    }

    /**
     * Registra el archivo para eliminación al cerrar la JVM y también lanza
     * un hilo daemon que lo borra pasado el tiempo de espera configurado.
     */
    private void programarEliminacion(File archivo) {
        // Seguridad: se borra aunque la JVM cierre antes del plazo
        archivo.deleteOnExit();

        ScheduledExecutorService limpieza = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "imprimir-limpieza");
            t.setDaemon(true); // No impide que la JVM termine
            return t;
        });

        limpieza.schedule(() -> {
            archivo.delete();
            limpieza.shutdown();
        }, SEGUNDOS_ESPERA_BORRADO, TimeUnit.SECONDS);
    }
}
