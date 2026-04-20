package com.tpsstudio.dao;

/**
 * Interfaz DAO (Data Access Object) para la persistencia de la configuración
 * de la aplicación.
 *
 * <p>Define el contrato de acceso a las preferencias del usuario (editor externo,
 * opciones globales, etc.), abstrayendo el mecanismo de almacenamiento de la
 * lógica de negocio.</p>
 *
 * <p>La implementación actual, {@link com.tpsstudio.service.SettingsManager},
 * utiliza las {@link java.util.prefs.Preferences} del sistema operativo para
 * persistir la configuración entre sesiones.</p>
 *
 * @see com.tpsstudio.service.SettingsManager
 */
public interface SettingsDAO {

    /**
     * Obtiene la ruta absoluta del editor externo de imágenes configurado.
     *
     * <p>Si el archivo referenciado ya no existe en disco, devuelve {@code null}
     * para evitar errores silenciosos.</p>
     *
     * @return Ruta al ejecutable del editor, o {@code null} si no está configurado
     *         o el archivo no existe.
     */
    String getExternalEditorPath();

    /**
     * Establece la ruta del editor externo de imágenes.
     *
     * <p>Pasar {@code null} equivale a llamar a {@link #clearExternalEditor()}.</p>
     *
     * @param path Ruta absoluta al ejecutable del editor externo.
     */
    void setExternalEditorPath(String path);

    /**
     * Devuelve el nombre amigable del editor configurado (p.ej. {@code "Photoshop.exe"}).
     *
     * @return Nombre del ejecutable, o {@code "Predeterminado"} si no hay editor configurado.
     */
    String getExternalEditorName();

    /**
     * Elimina la configuración del editor externo, restaurando el comportamiento predeterminado.
     */
    void clearExternalEditor();
}
