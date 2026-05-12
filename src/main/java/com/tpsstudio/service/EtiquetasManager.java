package com.tpsstudio.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tpsstudio.model.project.Etiqueta;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Gestiona las etiquetas/categorías de proyectos por usuario.
 *
 * Guarda tanto la lista de etiquetas como el filtro activo usando Preferences.
 * Cada usuario tiene su propio conjunto de etiquetas.
 */
public class EtiquetasManager {

    private static final String KEY_ETIQUETAS = "etiquetas_v1_";
    private static final String KEY_FILTRO_ACTIVO = "etiquetas_filtro_v1_";

    public static final List<String> PALETTE = List.of(
            "#6C63FF",
            "#3ABEFF",
            "#2ECC71",
            "#F39C12",
            "#E74C6C",
            "#1ABC9C",
            "#9B59B6",
            "#E67E22",
            "#95A5A6",
            "#E91E8C"
    );

    private final Preferences prefs;
    private final String username;
    private final Gson gson;
    private final List<Etiqueta> etiquetas;

    // =====================================================
    // Constructor
    // =====================================================

    public EtiquetasManager(String username) {
        this.username = username != null ? username : "Guest";
        this.prefs = Preferences.userNodeForPackage(EtiquetasManager.class);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.etiquetas = new ArrayList<>();

        cargar();
    }

    // =====================================================
    // CRUD de etiquetas
    // =====================================================

    public List<Etiqueta> getAll() {
        return Collections.unmodifiableList(etiquetas);
    }

    public Etiqueta findById(String id) {
        if (id == null) {
            return null;
        }

        return etiquetas.stream()
                .filter(e -> id.equals(e.getId()))
                .findFirst()
                .orElse(null);
    }

    public Etiqueta crear(String nombre, String color) {
        String c = (color != null && !color.isBlank()) ? color : siguienteColor();

        Etiqueta etiqueta = new Etiqueta(nombre.trim(), c);
        etiquetas.add(etiqueta);
        guardar();

        return etiqueta;
    }

    public boolean renombrar(String id, String nuevoNombre) {
        Etiqueta etiqueta = findById(id);

        if (etiqueta != null && nuevoNombre != null && !nuevoNombre.isBlank()) {
            etiqueta.setNombre(nuevoNombre.trim());
            guardar();
            return true;
        }

        return false;
    }

    public boolean cambiarColor(String id, String color) {
        Etiqueta etiqueta = findById(id);

        if (etiqueta != null && color != null) {
            etiqueta.setColor(color);
            guardar();
            return true;
        }

        return false;
    }

    public boolean eliminar(String id) {
        boolean removed = etiquetas.removeIf(e -> id != null && id.equals(e.getId()));

        if (removed) {
            guardar();
        }

        return removed;
    }

    // =====================================================
    // Filtro activo
    // =====================================================

    public List<String> getFiltroActivo() {
        String json = prefs.get(KEY_FILTRO_ACTIVO + username, "[]");

        try {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> ids = gson.fromJson(json, listType);

            return ids != null ? ids : new ArrayList<>();

        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    public void setFiltroActivo(List<String> ids) {
        String json = gson.toJson(ids != null ? ids : new ArrayList<>());
        prefs.put(KEY_FILTRO_ACTIVO + username, json);
    }

    // =====================================================
    // Persistencia interna
    // =====================================================

    private void cargar() {
        etiquetas.clear();

        String json = prefs.get(KEY_ETIQUETAS + username, "[]");

        try {
            Type listType = new TypeToken<List<Etiqueta>>() {}.getType();
            List<Etiqueta> loaded = gson.fromJson(json, listType);

            if (loaded != null) {
                etiquetas.addAll(loaded);
            }

        } catch (Exception ex) {
            System.err.println("[EtiquetasManager] Error al cargar etiquetas: " + ex.getMessage());
        }
    }

    private void guardar() {
        String json = gson.toJson(etiquetas);
        prefs.put(KEY_ETIQUETAS + username, json);
    }

    private String siguienteColor() {
        return PALETTE.get(etiquetas.size() % PALETTE.size());
    }
}