package com.tpsstudio.model.project;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.enums.TipoTroquel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Entidad de dominio central de TPS Studio.
 *
 * <p>Representa un trabajo de diseño de tarjeta CR80 (tarjeta de crédito estándar).
 * Encapsula todos los datos necesarios para diseñar, editar y exportar una tarjeta:
 * los elementos gráficos de cada cara, los fondos y los metadatos del proyecto.</p>
 *
 * <p><b>Modelo de dos caras:</b><br/>
 * Cada proyecto contiene dos listas independientes de {@link com.tpsstudio.model.elements.Elemento}
 * (frente y dorso), así como un fondo opcional por cara ({@link com.tpsstudio.model.elements.ImagenFondoElemento}).
 * El atributo {@code mostrandoFrente} determina qué cara se está editando actualmente
 * en el canvas, y los métodos {@link #getElementosActuales()} y {@link #getFondoActual()}
 * abstraen esta dualidad para no duplicar lógica en los controladores.</p>
 *
 * <p><b>Persistencia:</b><br/>
 * Esta clase es serializable mediante Gson a archivos {@code .tps} a través del
 * {@link com.tpsstudio.dao.ProyectoDAO} y su implementación
 * {@link com.tpsstudio.service.ProyectoFileManager}.</p>
 *
 * @see com.tpsstudio.model.project.ProyectoMetadata
 * @see com.tpsstudio.dao.ProyectoDAO
 */
public class Proyecto {

    private final int id;
    private String nombre;
    private final String tipo; // "CR80"
    private boolean mostrandoFrente; // true = frente, false = dorso

    // Lista de elementos gráficos en la tarjeta
    private final ObservableList<Elemento> elementosFrente;
    private final ObservableList<Elemento> elementosDorso;

    // Fondos (uno por cara)
    private ImagenFondoElemento fondoFrente;
    private ImagenFondoElemento fondoDorso;

    // Preferencia de modo de ajuste para fondos
    private FondoFitMode fondoFitModePreferido;
    private boolean noVolverAPreguntarFondo;

    // Tipo de troquel físico
    private TipoTroquel tipoTroquel;

    // Metadatos del proyecto (ubicación, cliente, etc.)
    private ProyectoMetadata metadata;

    private static int contadorId = 1;

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
    }

    // Getters y setters
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

    public boolean isMostrandoFrente() {
        return mostrandoFrente;
    }

    public void setMostrandoFrente(boolean mostrandoFrente) {
        this.mostrandoFrente = mostrandoFrente;
    }

    /**
     * Obtiene la lista de elementos de la cara actual
     */
    public ObservableList<Elemento> getElementosActuales() {
        return mostrandoFrente ? elementosFrente : elementosDorso;
    }

    public ObservableList<Elemento> getElementosFrente() {
        return elementosFrente;
    }

    public ObservableList<Elemento> getElementosDorso() {
        return elementosDorso;
    }

    /**
     * Obtiene el fondo de la cara actual
     */
    public ImagenFondoElemento getFondoActual() {
        return mostrandoFrente ? fondoFrente : fondoDorso;
    }

    /**
     * Establece el fondo de la cara actual
     */
    public void setFondoActual(ImagenFondoElemento fondo) {
        if (mostrandoFrente) {
            fondoFrente = fondo;
        } else {
            fondoDorso = fondo;
        }
    }

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

    public ProyectoMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ProyectoMetadata metadata) {
        this.metadata = metadata;
    }

    public TipoTroquel getTipoTroquel() {
        // Por retrocompatibilidad con proyectos guardados antes del Enum
        return tipoTroquel != null ? tipoTroquel : TipoTroquel.NINGUNO;
    }

    public void setTipoTroquel(TipoTroquel tipoTroquel) {
        this.tipoTroquel = tipoTroquel;
    }

    @Override
    public String toString() {
        return nombre + " (" + tipo + ")";
    }
}
