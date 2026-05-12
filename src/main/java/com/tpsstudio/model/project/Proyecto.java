package com.tpsstudio.model.project;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.enums.TipoTroquel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidad principal de TPS Studio.
 *
 * Representa un proyecto de diseño de tarjeta CR80, incluyendo sus dos caras,
 * los elementos gráficos, los fondos, la orientación, el troquel y los metadatos
 * asociados al trabajo.
 */
public class Proyecto {

    private final int id;
    private String nombre;
    private final String tipo;
    private boolean mostrandoFrente;

    // Elementos gráficos por cara
    private final ObservableList<Elemento> elementosFrente;
    private final ObservableList<Elemento> elementosDorso;

    // Fondo independiente para frente y dorso
    private ImagenFondoElemento fondoFrente;
    private ImagenFondoElemento fondoDorso;

    // Preferencias de ajuste de fondo
    private FondoFitMode fondoFitModePreferido;
    private boolean noVolverAPreguntarFondo;

    // Opciones físicas del soporte
    private TipoTroquel tipoTroquel;
    private com.tpsstudio.model.enums.Orientacion orientacion;

    // Etiquetas/categorías asignadas al proyecto
    private List<String> etiquetaIds;

    // Metadatos: cliente, rutas, ubicación, etc.
    private ProyectoMetadata metadata;

    private static int contadorId = 1;

    // =====================================================
    // Constructor
    // =====================================================

    public Proyecto(String nombre) {
        this.id = contadorId++;
        this.nombre = nombre;
        this.tipo = "CR80";
        this.mostrandoFrente = true;

        this.elementosFrente = FXCollections.observableArrayList();
        this.elementosDorso = FXCollections.observableArrayList();

        this.fondoFrente = null;
        this.fondoDorso = null;

        this.fondoFitModePreferido = null;
        this.noVolverAPreguntarFondo = false;

        this.tipoTroquel = TipoTroquel.NINGUNO;
        this.orientacion = com.tpsstudio.model.enums.Orientacion.HORIZONTAL;
    }

    // =====================================================
    // Datos básicos
    // =====================================================

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public ProyectoMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ProyectoMetadata metadata) {
        this.metadata = metadata;
    }

    // =====================================================
    // Cara activa
    // =====================================================

    public boolean isMostrandoFrente() {
        return mostrandoFrente;
    }

    public void setMostrandoFrente(boolean mostrandoFrente) {
        this.mostrandoFrente = mostrandoFrente;
    }

    public ObservableList<Elemento> getElementosActuales() {
        return mostrandoFrente ? elementosFrente : elementosDorso;
    }

    public ImagenFondoElemento getFondoActual() {
        return mostrandoFrente ? fondoFrente : fondoDorso;
    }

    public void setFondoActual(ImagenFondoElemento fondo) {
        if (mostrandoFrente) {
            fondoFrente = fondo;
        } else {
            fondoDorso = fondo;
        }
    }

    // =====================================================
    // Elementos por cara
    // =====================================================

    public ObservableList<Elemento> getElementosFrente() {
        return elementosFrente;
    }

    public ObservableList<Elemento> getElementosDorso() {
        return elementosDorso;
    }

    // =====================================================
    // Fondos por cara
    // =====================================================

    public ImagenFondoElemento getFondoFrente() {
        return fondoFrente;
    }

    public void setFondoFrente(ImagenFondoElemento fondoFrente) {
        this.fondoFrente = fondoFrente;
    }

    public ImagenFondoElemento getFondoDorso() {
        return fondoDorso;
    }

    public void setFondoDorso(ImagenFondoElemento fondoDorso) {
        this.fondoDorso = fondoDorso;
    }

    // =====================================================
    // Preferencias de fondo
    // =====================================================

    public FondoFitMode getFondoFitModePreferido() {
        return fondoFitModePreferido;
    }

    public void setFondoFitModePreferido(FondoFitMode fondoFitModePreferido) {
        this.fondoFitModePreferido = fondoFitModePreferido;
    }

    public boolean isNoVolverAPreguntarFondo() {
        return noVolverAPreguntarFondo;
    }

    public void setNoVolverAPreguntarFondo(boolean noVolverAPreguntarFondo) {
        this.noVolverAPreguntarFondo = noVolverAPreguntarFondo;
    }

    // =====================================================
    // Troquel y orientación
    // =====================================================

    public TipoTroquel getTipoTroquel() {
        return tipoTroquel != null ? tipoTroquel : TipoTroquel.NINGUNO;
    }

    public void setTipoTroquel(TipoTroquel tipoTroquel) {
        this.tipoTroquel = tipoTroquel;
    }

    public com.tpsstudio.model.enums.Orientacion getOrientacion() {
        return orientacion != null
                ? orientacion
                : com.tpsstudio.model.enums.Orientacion.HORIZONTAL;
    }

    public void setOrientacion(com.tpsstudio.model.enums.Orientacion orientacion) {
        this.orientacion = orientacion;
    }

    // =====================================================
    // Etiquetas
    // =====================================================

    public List<String> getEtiquetaIds() {
        if (etiquetaIds == null) {
            etiquetaIds = new ArrayList<>();
        }

        return etiquetaIds;
    }

    public void setEtiquetaIds(List<String> etiquetaIds) {
        this.etiquetaIds = etiquetaIds != null ? etiquetaIds : new ArrayList<>();
    }

    public void addEtiqueta(String etiquetaId) {
        if (etiquetaId != null && !getEtiquetaIds().contains(etiquetaId)) {
            etiquetaIds.add(etiquetaId);
        }
    }

    public void removeEtiqueta(String etiquetaId) {
        getEtiquetaIds().remove(etiquetaId);
    }

    // =====================================================
    // Representación
    // =====================================================

    @Override
    public String toString() {
        return nombre + " (" + tipo + ")";
    }
}