package com.tpsstudio.util;

import javafx.scene.image.Image;
import java.io.File;
import java.io.IOException;

/**
 * Utilidades para manejo de imágenes
 */
public class ImageUtils {

    private static final java.io.File CACHE_DIR = new java.io.File(System.getProperty("java.io.tmpdir"),
            "tps_studio_cache");

    static {
        // Asegurar que existe el directorio de caché
        if (!CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
        }
    }

    /**
     * Limpia la caché de imágenes temporales.
     * Debería llamarse al iniciar o cerrar la aplicación.
     */
    public static void limpiarCache() {
        if (CACHE_DIR.exists() && CACHE_DIR.isDirectory()) {
            java.io.File[] files = CACHE_DIR.listFiles((dir, name) -> name.startsWith("img_proxy_"));
            if (files != null) {
                for (java.io.File f : files) {
                    try {
                        f.delete();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * Carga una imagen usando una estrategia de PROXY para evitar bloqueos de
     * archivo.
     * 1. Copia el archivo original a una ubicación temporal única.
     * 2. Carga la imagen desde esa copia temporal.
     * Esto garantiza que Java NUNCA mantenga un handle sobre el archivo original,
     * permitiendo que Photoshop u otros editores lo modifiquen libremente.
     * 
     * @param rutaArchivo Ruta absoluta del archivo
     * @return Objeto Image de JavaFX o null si hay error
     */
    public static Image cargarImagenSinBloqueo(String rutaArchivo) {
        if (rutaArchivo == null)
            return null;

        File archivoOriginal = new File(rutaArchivo);
        if (!archivoOriginal.exists())
            return null;

        try {
            // Crear nombre único para la copia temporal (timestamp permite versionado para
            // recargas)
            // Usamos System.nanoTime para mayor precisión y evitar colisiones rápidas
            String nombreTemp = "img_proxy_" + System.currentTimeMillis() + "_" + System.nanoTime() + ".png";
            File archivoTemp = new File(CACHE_DIR, nombreTemp);

            // Marcar para borrar al salir (best effort)
            archivoTemp.deleteOnExit();

            // Copiar contenido: Original -> Temp
            // Usamos REPLACE_EXISTING por si acaso
            java.nio.file.Files.copy(archivoOriginal.toPath(), archivoTemp.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Cargar imagen desde la COPIA TEMPORAL
            // Usamos el constructor URL de Image que es robusto y no bloquea el ORIGINAL
            // (Bloqueará el TEMP, pero eso no nos importa)
            return new Image(archivoTemp.toURI().toString());

        } catch (IOException e) {
            System.err.println("Error cargando imagen (proxy): " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
