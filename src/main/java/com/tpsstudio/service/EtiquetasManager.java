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
import java.util.stream.Collectors;

/**
 * Gestor de Categorías/Etiquetas por usuario.
 *
 * <p>Persiste la lista global de etiquetas del usuario y el estado activo del filtro
 * usando {@link java.util.prefs.Preferences} del sistema operativo (Registro en Windows).
 * Cada usuario tiene su propio espacio de etiquetas y filtro.</p>
 *
 * <p>Las etiquetas se serializan como JSON con Gson. La lista de IDs de filtro activo
 * también se serializa a JSON para soportar múltiples selecciones.</p>
 */
public class EtiquetasManager {

    private static final String KEY_ETIQUETAS   = "etiquetas_v1_";
    private static final String KEY_FILTRO_ACTIVO = "etiquetas_filtro_v1_";

    // Paleta de colores predefinidos armoniosos con el tema oscuro
    public static final List<String> PALETTE = List.of(
        "#6C63FF", // Lavanda (acento principal)
        "#3ABEFF", // Celeste
        "#2ECC71", // Verde esmeralda
        "#F39C12", // Dorado ámbar
        "#E74C6C", // Coral/Rosa
        "#1ABC9C", // Teal
        "#9B59B6", // Violeta
        "#E67E22", // Naranja
        "#95A5A6", // Gris plateado
        "#E91E8C"  // Fucsia
    );

    private final Preferences prefs;
    private final String username;
    private final Gson gson;
    private final List<Etiqueta> etiquetas;

    public EtiquetasManager(String username) {
        this.username = username != null ? username : "Guest";
        this.prefs = Preferences.userNodeForPackage(EtiquetasManager.class);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.etiquetas = new ArrayList<>();
        cargar();
    }

    // ============================================================
    // CRUD Etiquetas
    // ============================================================

    /** Devuelve la lista completa de etiquetas del usuario (inmutable para lectura). */
    public List<Etiqueta> getAll() {
        return Collections.unmodifiableList(etiquetas);
    }

    /** Busca una etiqueta por ID. */
    public Etiqueta findById(String id) {
        if (id == null) return null;
        return etiquetas.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
    }

    /**
     * Crea una nueva etiqueta con el nombre y color dados.
     * Elige el siguiente color de la paleta automáticamente si se pasa null.
     */
    public Etiqueta crear(String nombre, String color) {
        String c = (color != null && !color.isBlank()) ? color : siguienteColor();
        Etiqueta e = new Etiqueta(nombre.trim(), c);
        etiquetas.add(e);
        guardar();
        return e;
    }

    /** Renombra una etiqueta existente. */
    public boolean renombrar(String id, String nuevoNombre) {
        Etiqueta e = findById(id);
        if (e != null && nuevoNombre != null && !nuevoNombre.isBlank()) {
            e.setNombre(nuevoNombre.trim());
            guardar();
            return true;
        }
        return false;
    }

    /** Cambia el color de una etiqueta. */
    public boolean cambiarColor(String id, String color) {
        Etiqueta e = findById(id);
        if (e != null && color != null) {
            e.setColor(color);
            guardar();
            return true;
        }
        return false;
    }

    /** Elimina una etiqueta de la lista global. */
    public boolean eliminar(String id) {
        boolean removed = etiquetas.removeIf(e -> id != null && id.equals(e.getId()));
        if (removed) guardar();
        return removed;
    }

    // ============================================================
    // Filtro Activo
    // ============================================================

    /**
     * Devuelve los IDs de las categorías activas en el filtro.
     * Lista vacía = "Todos" (sin filtro activo).
     */
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

    /**
     * Guarda la selección activa del filtro.
     * Pasar lista vacía = "Todos".
     */
    public void setFiltroActivo(List<String> ids) {
        String json = gson.toJson(ids != null ? ids : new ArrayList<>());
        prefs.put(KEY_FILTRO_ACTIVO + username, json);
    }

    // ============================================================
    // Persistencia Interna
    // ============================================================

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

    /** Devuelve el siguiente color de la paleta basándose en cuántas etiquetas hay. */
    private String siguienteColor() {
        return PALETTE.get(etiquetas.size() % PALETTE.size());
    }
}
