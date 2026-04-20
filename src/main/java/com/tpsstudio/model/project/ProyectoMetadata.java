package com.tpsstudio.model.project;

import java.time.LocalDateTime;

/**
 * Metadatos asociados a un {@link Proyecto}.
 *
 * <p>Separa la información descriptiva y de persistencia del modelo de dominio puro.
 * Contiene rutas de recursos en disco, información del cliente y marcas de tiempo,
 * que no forman parte del diseño gráfico en sí pero son necesarias para gestionar
 * el ciclo de vida del proyecto.</p>
 *
 * <p><b>Rutas gestionadas:</b></p>
 * <ul>
 *   <li>{@code rutaTPS} — Archivo principal del proyecto ({@code .tps}).</li>
 *   <li>{@code rutaFotos} — Subcarpeta de fotografías de personas/elementos variables.</li>
 *   <li>{@code rutaFondos} — Subcarpeta de imágenes de fondo de tarjeta.</li>
 *   <li>{@code rutaBBDD} — Archivo de base de datos CSV/XLSX para mail-merge (opcional).</li>
 * </ul>
 *
 * <p>Las rutas son rehidratadas al cargar el proyecto para soportar proyectos
 * que hayan sido movidos de ubicación en disco.</p>
 *
 * @see Proyecto
 * @see ClienteInfo
 * @see com.tpsstudio.service.ProyectoFileManager
 */
public class ProyectoMetadata {

    private String nombre;
    private String ubicacion; // Carpeta padre donde se creará TPS_NombreProyecto
    private String rutaTPS;    // Ruta completa al archivo proyecto.tps
    private String rutaFotos;  // Ruta a carpeta Fotos/
    private String rutaFondos; // Ruta a carpeta Fondos/
    private String rutaBBDD;   // Ruta a base de datos (opcional)
    private ClienteInfo clienteInfo; // Información del cliente
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    public ProyectoMetadata() {
        this.clienteInfo = new ClienteInfo();
        this.fechaCreacion = LocalDateTime.now();
        this.fechaModificacion = LocalDateTime.now();
    }

    // ================== GETTERS Y SETTERS ==================

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

    public ClienteInfo getClienteInfo() {
        return clienteInfo;
    }

    public void setClienteInfo(ClienteInfo clienteInfo) {
        this.clienteInfo = clienteInfo != null ? clienteInfo : new ClienteInfo();
    }

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

    /* Obtiene la carpeta raíz del proyecto (TPS_NombreProyecto) */

    public String getCarpetaProyecto() {
        if (rutaTPS == null) {
            return null;
        }
        return new java.io.File(rutaTPS).getParent();
    }
}
