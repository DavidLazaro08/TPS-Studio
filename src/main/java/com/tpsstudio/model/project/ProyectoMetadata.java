package com.tpsstudio.model.project;

import java.time.LocalDateTime;

/**
 * Metadatos asociados a un proyecto de TPS Studio.
 *
 * Guarda información complementaria al diseño: nombre, rutas en disco,
 * datos del cliente, fechas y orientación de la tarjeta.
 */
public class ProyectoMetadata {

    private String nombre;
    private String ubicacion;

    private String rutaTPS;
    private String rutaFotos;
    private String rutaFondos;
    private String rutaBBDD;

    private ClienteInfo clienteInfo;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    private com.tpsstudio.model.enums.Orientacion orientacion;

    // =====================================================
    // Constructor
    // =====================================================

    public ProyectoMetadata() {
        this.clienteInfo = new ClienteInfo();
        this.fechaCreacion = LocalDateTime.now();
        this.fechaModificacion = LocalDateTime.now();
        this.orientacion = com.tpsstudio.model.enums.Orientacion.HORIZONTAL;
    }

    // =====================================================
    // Datos básicos
    // =====================================================

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    // =====================================================
    // Rutas del proyecto
    // =====================================================

    public String getRutaTPS() {
        return rutaTPS;
    }

    public void setRutaTPS(String rutaTPS) {
        this.rutaTPS = rutaTPS;
    }

    public String getRutaFotos() {
        return rutaFotos;
    }

    public void setRutaFotos(String rutaFotos) {
        this.rutaFotos = rutaFotos;
    }

    public String getRutaFondos() {
        return rutaFondos;
    }

    public void setRutaFondos(String rutaFondos) {
        this.rutaFondos = rutaFondos;
    }

    public String getRutaBBDD() {
        return rutaBBDD;
    }

    public void setRutaBBDD(String rutaBBDD) {
        this.rutaBBDD = rutaBBDD;
    }

    public String getCarpetaProyecto() {
        if (rutaTPS == null) {
            return null;
        }

        return new java.io.File(rutaTPS).getParent();
    }

    // =====================================================
    // Cliente
    // =====================================================

    public ClienteInfo getClienteInfo() {
        return clienteInfo;
    }

    public void setClienteInfo(ClienteInfo clienteInfo) {
        this.clienteInfo = clienteInfo != null ? clienteInfo : new ClienteInfo();
    }

    // =====================================================
    // Fechas
    // =====================================================

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }

    public void setFechaModificacion(LocalDateTime fechaModificacion) {
        this.fechaModificacion = fechaModificacion;
    }

    // =====================================================
    // Orientación
    // =====================================================

    public com.tpsstudio.model.enums.Orientacion getOrientacion() {
        return orientacion != null
                ? orientacion
                : com.tpsstudio.model.enums.Orientacion.HORIZONTAL;
    }

    public void setOrientacion(com.tpsstudio.model.enums.Orientacion orientacion) {
        this.orientacion = orientacion;
    }
}