package com.tpsstudio.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Gestor de proyectos recientes
 */
public class RecentProjectsManager {

    private static final String PREFS_KEY = "recent_projects";
    private static final int MAX_RECENT = 10;
    private static final String SEPARATOR = "|";

    private final Preferences prefs;
    private final List<String> recentProjects;
    private final String prefKey;

    public RecentProjectsManager(String username) {
        this.prefs = Preferences.userNodeForPackage(RecentProjectsManager.class);
        this.recentProjects = new ArrayList<>();
        
        // Clave única por usuario para aislar espacios de trabajo
        this.prefKey = PREFS_KEY + "_" + (username != null ? username : "Guest");
        
        cargarRecientes();

        // MIGRACIÓN LEGACY: Si el usuario es Admin y su lista está vacía, 
        // intentamos cargar la lista antigua (sin prefijo) para no perder datos.
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
                guardarRecientes(); // Consolidar en el nuevo nodo del usuario
            }
        }
    }

    /**
     * Añade un proyecto a la lista de recientes
     */
    public void añadirReciente(String rutaTPS) {
        if (rutaTPS == null || rutaTPS.isEmpty()) {
            return;
        }

        // Verificar que el archivo existe
        File archivo = new File(rutaTPS);
        if (!archivo.exists()) {
            return;
        }

        // Eliminar si ya existe (para moverlo al principio)
        recentProjects.remove(rutaTPS);

        // Añadir al principio
        recentProjects.add(0, rutaTPS);

        // Limitar a MAX_RECENT
        while (recentProjects.size() > MAX_RECENT) {
            recentProjects.remove(recentProjects.size() - 1);
        }

        guardarRecientes();
    }

    /**
     * Obtiene la lista de proyectos recientes
     */
    public List<String> getRecientes() {
        // Filtrar archivos que ya no existen
        recentProjects.removeIf(ruta -> !new File(ruta).exists());
        return new ArrayList<>(recentProjects);
    }

    /**
     * Obtiene el proyecto más reciente
     */
    public String getMasReciente() {
        List<String> recientes = getRecientes();
        return recientes.isEmpty() ? null : recientes.get(0);
    }

    /**
     * Limpia la lista de recientes
     */
    public void limpiar() {
        recentProjects.clear();
        guardarRecientes();
    }

    /**
     * Elimina un proyecto específico de la lista de recientes
     */
    public void eliminarReciente(String rutaTPS) {
        recentProjects.remove(rutaTPS);
        guardarRecientes();
    }

    /**
     * Carga los proyectos recientes desde las preferencias
     */
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

    /**
     * Guarda los proyectos recientes en las preferencias
     */
    private void guardarRecientes() {
        String datos = String.join(SEPARATOR, recentProjects);
        prefs.put(prefKey, datos);
    }
}
