package com.tpsstudio.service;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * Gestor de configuración de la aplicación
 * Permite guardar preferencias como el editor externo y otras opciones
 * globales.
 */
public class SettingsManager {

    private static final String PREF_EDITOR_PATH = "external_editor_path";

    // Instancia única (Singleton simplificado) o gestionada por el controller
    // Por simplicidad usaremos métodos estáticos o una instancia nueva ya que
    // Preferences es thread-safe
    private final Preferences prefs;

    public SettingsManager() {
        this.prefs = Preferences.userNodeForPackage(SettingsManager.class);
    }

    /**
     * Obtiene la ruta del editor externo configurado
     * 
     * @return Ruta al ejecutable o null si no está configurado/no existe
     */
    public String getExternalEditorPath() {
        String path = prefs.get(PREF_EDITOR_PATH, null);
        if (path != null && !path.isEmpty()) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                return path;
            } else {
                // Si el archivo ya no existe, limpiar la preferencia para evitar errores
                // O mejor, devolver null pero dejar la pref por si fue un cambio temporal de
                // disco
                return null;
            }
        }
        return null;
    }

    /**
     * Establece la ruta del editor externo
     * 
     * @param path Ruta absoluta al ejecutable
     */
    public void setExternalEditorPath(String path) {
        if (path == null) {
            prefs.remove(PREF_EDITOR_PATH);
        } else {
            prefs.put(PREF_EDITOR_PATH, path);
        }
    }

    /**
     * Obtiene el nombre amigable del editor (ej: "Photoshop.exe")
     */
    public String getExternalEditorName() {
        String path = getExternalEditorPath();
        if (path != null) {
            return new File(path).getName();
        }
        return "Predeterminado";
    }

    /**
     * Borra la configuración del editor externo (volver a predeterminado)
     */
    public void clearExternalEditor() {
        prefs.remove(PREF_EDITOR_PATH);
    }
}
