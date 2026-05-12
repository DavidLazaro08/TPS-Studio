package com.tpsstudio.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Gestiona el historial de proyectos recientes.
 *
 * Los proyectos se guardan en preferencias locales del sistema y se separan
 * por usuario para que cada sesión tenga su propio espacio de trabajo.
 */
public class RecentProjectsManager {

    private static final String PREFS_KEY = "recent_projects";
    private static final int MAX_RECENT = 10;
    private static final String SEPARATOR = "|";

    private final Preferences prefs;
    private final List<String> recentProjects;
    private final String prefKey;

    // =====================================================
    // Constructor
    // =====================================================

    public RecentProjectsManager(String username) {
        this.prefs = Preferences.userNodeForPackage(RecentProjectsManager.class);
        this.recentProjects = new ArrayList<>();

        this.prefKey = PREFS_KEY + "_" + (username != null ? username : "Guest");

        cargarRecientes();

        /*
         * Migración sencilla para proyectos antiguos:
         * si Admin no tiene lista propia todavía, intenta recuperar la lista
         * guardada antes de separar los recientes por usuario.
         */
        if (recentProjects.isEmpty() && "Admin".equalsIgnoreCase(username)) {
            String legacyData = prefs.get(PREFS_KEY, "");

            if (!legacyData.isEmpty()) {
                System.out.println("Migrando proyectos recientes antiguos para Admin...");

                String[] rutas = legacyData.split("\\" + SEPARATOR);

                for (String ruta : rutas) {
                    if (!ruta.isEmpty() && new File(ruta).exists()) {
                        recentProjects.add(ruta);
                    }
                }

                guardarRecientes();
            }
        }
    }

    // =====================================================
    // Operaciones públicas
    // =====================================================

    public void anadirReciente(String rutaTPS) {
        if (rutaTPS == null || rutaTPS.isEmpty()) {
            return;
        }

        File archivo = new File(rutaTPS);

        if (!archivo.exists()) {
            return;
        }

        recentProjects.remove(rutaTPS);
        recentProjects.add(0, rutaTPS);

        while (recentProjects.size() > MAX_RECENT) {
            recentProjects.remove(recentProjects.size() - 1);
        }

        guardarRecientes();
    }

    public List<String> getRecientes() {
        recentProjects.removeIf(ruta -> !new File(ruta).exists());
        return new ArrayList<>(recentProjects);
    }

    public String getMasReciente() {
        List<String> recientes = getRecientes();
        return recientes.isEmpty() ? null : recientes.get(0);
    }

    public void limpiar() {
        recentProjects.clear();
        guardarRecientes();
    }

    public void eliminarReciente(String rutaTPS) {
        recentProjects.remove(rutaTPS);
        guardarRecientes();
    }

    // =====================================================
    // Persistencia interna
    // =====================================================

    private void cargarRecientes() {
        String datos = prefs.get(prefKey, "");

        if (!datos.isEmpty()) {
            String[] rutas = datos.split("\\" + SEPARATOR);

            for (String ruta : rutas) {
                if (!ruta.isEmpty() && new File(ruta).exists()) {
                    recentProjects.add(ruta);
                }
            }
        }
    }

    private void guardarRecientes() {
        String datos = String.join(SEPARATOR, recentProjects);
        prefs.put(prefKey, datos);
    }
}