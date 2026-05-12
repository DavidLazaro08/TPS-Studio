package com.tpsstudio.service;

import com.tpsstudio.dao.SettingsDAO;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * Gestiona la configuración local de TPS Studio.
 *
 * Actualmente guarda la ruta del editor externo de imágenes, como Photoshop,
 * GIMP u otro programa configurado por el usuario.
 */
public class SettingsManager implements SettingsDAO {

    private static final String PREF_EDITOR_PATH = "external_editor_path";

    private final Preferences prefs;

    // =====================================================
    // Constructor
    // =====================================================

    public SettingsManager() {
        this.prefs = Preferences.userNodeForPackage(SettingsManager.class);
    }

    // =====================================================
    // Editor externo
    // =====================================================

    public String getExternalEditorPath() {
        String path = prefs.get(PREF_EDITOR_PATH, null);

        if (path != null && !path.isEmpty()) {
            File file = new File(path);

            if (file.exists() && file.isFile()) {
                return path;
            }

            return null;
        }

        return null;
    }

    public void setExternalEditorPath(String path) {
        if (path == null) {
            prefs.remove(PREF_EDITOR_PATH);
        } else {
            prefs.put(PREF_EDITOR_PATH, path);
        }
    }

    public String getExternalEditorName() {
        String path = getExternalEditorPath();

        if (path != null) {
            return new File(path).getName();
        }

        return "Predeterminado";
    }

    public void clearExternalEditor() {
        prefs.remove(PREF_EDITOR_PATH);
    }
}