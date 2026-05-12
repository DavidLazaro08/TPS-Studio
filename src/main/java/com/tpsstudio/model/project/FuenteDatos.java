package com.tpsstudio.model.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Modelo en memoria para datos variables.
 *
 * Representa una tabla cargada desde Excel, CSV u otra fuente compatible:
 * columnas, filas y posición actual de navegación.
 */
public class FuenteDatos {

    private final List<String> columnas;
    private final List<Map<String, String>> filas;
    private final String nombreArchivo;

    private int indiceActual;

    // =====================================================
    // Constructor
    // =====================================================

    public FuenteDatos(String nombreArchivo, List<String> columnas, List<Map<String, String>> filas) {
        this.nombreArchivo = nombreArchivo != null ? nombreArchivo : "";
        this.columnas = Collections.unmodifiableList(new ArrayList<>(columnas));
        this.filas = new ArrayList<>(filas);
        this.indiceActual = filas.isEmpty() ? -1 : 0;
    }

    // =====================================================
    // Registro actual
    // =====================================================

    public Map<String, String> getRegistroActual() {
        if (indiceActual < 0 || indiceActual >= filas.size()) {
            return null;
        }

        return Collections.unmodifiableMap(filas.get(indiceActual));
    }

    public String getValor(String campo) {
        Map<String, String> registro = getRegistroActual();

        if (registro == null) {
            return "";
        }

        String val = registro.get(campo);
        return val != null ? val : "";
    }

    public void actualizarValorActual(String campo, String nuevoValor) {
        if (indiceActual < 0 || indiceActual >= filas.size()) {
            return;
        }

        filas.get(indiceActual).put(campo, nuevoValor);
    }

    public List<Map<String, String>> getFilas() {
        return Collections.unmodifiableList(filas);
    }

    // =====================================================
    // Navegación
    // =====================================================

    public boolean siguiente() {
        if (indiceActual < filas.size() - 1) {
            indiceActual++;
            return true;
        }

        return false;
    }

    public boolean anterior() {
        if (indiceActual > 0) {
            indiceActual--;
            return true;
        }

        return false;
    }

    public void irA(int n) {
        if (n >= 0 && n < filas.size()) {
            indiceActual = n;
        }
    }

    // =====================================================
    // Información general
    // =====================================================

    public boolean tieneRegistros() {
        return !filas.isEmpty();
    }

    public int getTotalRegistros() {
        return filas.size();
    }

    public int getIndiceActual() {
        return indiceActual;
    }

    public int getPosicionActual() {
        return indiceActual + 1;
    }

    public List<String> getColumnas() {
        return columnas;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    // =====================================================
    // Representación
    // =====================================================

    @Override
    public String toString() {
        return nombreArchivo + " [" + filas.size() + " registros, " + columnas.size() + " columnas]";
    }
}