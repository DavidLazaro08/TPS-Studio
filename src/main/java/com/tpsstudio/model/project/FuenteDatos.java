package com.tpsstudio.model.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Representa una fuente de datos cargada en memoria (Excel o CSV).
 * Guarda las columnas (cabecera), todos los registros y el índice de navegación actual.
 * Es un modelo puro: sin lógica de lectura ni de UI.
 */
public class FuenteDatos {

    private final List<String> columnas;
    private final List<Map<String, String>> filas;
    private int indiceActual;

    // Nombre del archivo de origen (solo informativo, para mostrar en UI)
    private final String nombreArchivo;

    public FuenteDatos(String nombreArchivo, List<String> columnas, List<Map<String, String>> filas) {
        this.nombreArchivo = nombreArchivo != null ? nombreArchivo : "";
        this.columnas = Collections.unmodifiableList(new ArrayList<>(columnas));
        this.filas = new ArrayList<>(filas);
        this.indiceActual = filas.isEmpty() ? -1 : 0;
    }

    // ── Acceso al registro actual ──────────────────────────────────────────────

    /** Devuelve el registro en la posición actual, o null si no hay filas. */
    public Map<String, String> getRegistroActual() {
        if (indiceActual < 0 || indiceActual >= filas.size())
            return null;
        return Collections.unmodifiableMap(filas.get(indiceActual));
    }

    /**
     * Devuelve el valor del campo indicado del registro actual.
     * Devuelve "" si el campo no existe o no hay registro.
     */
    public String getValor(String campo) {
        Map<String, String> registro = getRegistroActual();
        if (registro == null)
            return "";
        String val = registro.get(campo);
        return val != null ? val : "";
    }

    // ── Navegación ──────────────────────────────────────────────────────────────

    /** Avanza al registro siguiente. Devuelve true si hubo movimiento. */
    public boolean siguiente() {
        if (indiceActual < filas.size() - 1) {
            indiceActual++;
            return true;
        }
        return false;
    }

    /** Retrocede al registro anterior. Devuelve true si hubo movimiento. */
    public boolean anterior() {
        if (indiceActual > 0) {
            indiceActual--;
            return true;
        }
        return false;
    }

    /**
     * Salta directamente a la posición n (0-based). No hace nada si n es inválido.
     */
    public void irA(int n) {
        if (n >= 0 && n < filas.size()) {
            indiceActual = n;
        }
    }

    // ── Info general ────────────────────────────────────────────────────────────

    public boolean tieneRegistros() {
        return !filas.isEmpty();
    }

    public int getTotalRegistros() {
        return filas.size();
    }

    /** Índice actual (0-based). -1 si no hay registros. */
    public int getIndiceActual() {
        return indiceActual;
    }

    /** Posición para mostrar en UI (1-based). 0 si no hay registros. */
    public int getPosicionActual() {
        return indiceActual + 1;
    }

    public List<String> getColumnas() {
        return columnas;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    @Override
    public String toString() {
        return nombreArchivo + " [" + filas.size() + " registros, " + columnas.size() + " columnas]";
    }
}
