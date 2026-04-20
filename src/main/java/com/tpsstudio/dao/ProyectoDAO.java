package com.tpsstudio.dao;

import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;

import java.io.File;

/**
 * Interfaz DAO (Data Access Object) para la persistencia de proyectos.
 *
 * <p>Define el contrato de acceso a datos para las operaciones del proyecto,
 * abstrayendo el mecanismo de almacenamiento (archivos JSON, base de datos, etc.)
 * del resto de la aplicación.</p>
 *
 * <p>Esta abstracción permite que las capas superiores ({@code service},
 * {@code viewmodel}) operen con proyectos sin conocer los detalles de
 * cómo se persisten físicamente.</p>
 *
 * <p>Actualmente se implementa en {@link com.tpsstudio.service.ProyectoFileManager}
 * mediante serialización JSON a archivos {@code .tps}.</p>
 *
 * @see com.tpsstudio.service.ProyectoFileManager
 * @see com.tpsstudio.service.ProjectManager
 */
public interface ProyectoDAO {

    /**
     * Crea la estructura de carpetas del proyecto en disco.
     *
     * <p>Genera las subcarpetas necesarias (Fotos, Fondos, Base de Datos) y
     * actualiza la {@link ProyectoMetadata} con las rutas resultantes.</p>
     *
     * @param metadata Metadatos del proyecto con nombre y ubicación destino.
     * @return {@code true} si la estructura se creó correctamente; {@code false} en caso de error.
     */
    boolean crearEstructuraCarpetas(ProyectoMetadata metadata);

    /**
     * Guarda el proyecto en su archivo {@code .tps} (formato JSON).
     *
     * <p>Serializa tanto el modelo de dominio ({@link Proyecto}) como sus metadatos
     * y actualiza la fecha de modificación automáticamente.</p>
     *
     * @param proyecto Proyecto con todos sus elementos (frente y dorso).
     * @param metadata Metadatos que contienen la ruta del archivo destino.
     * @return {@code true} si el guardado fue exitoso; {@code false} en caso de error.
     */
    boolean guardarProyecto(Proyecto proyecto, ProyectoMetadata metadata);

    /**
     * Carga un proyecto desde un archivo {@code .tps}.
     *
     * <p>Deserializa el JSON y reconstruye el modelo completo, incluyendo
     * la rehidratación de rutas de recursos en caso de que el proyecto
     * haya sido movido de ubicación.</p>
     *
     * @param archivoTPS Archivo {@code .tps} a cargar.
     * @return El {@link Proyecto} cargado, o {@code null} si la carga falla.
     */
    Proyecto cargarProyecto(File archivoTPS);

    /**
     * Copia una imagen al directorio correspondiente del proyecto.
     *
     * <p>La imagen se copia a la subcarpeta Fotos o Fondos según {@code esFondo},
     * opcionalmente añadiendo un sufijo al nombre del archivo.</p>
     *
     * @param imagenOrigen Archivo de imagen de origen.
     * @param metadata     Metadatos del proyecto destino.
     * @param esFondo      {@code true} para copiar a Fondos; {@code false} a Fotos.
     * @param sufijo       Sufijo opcional para el nombre (p.ej. "FRENTE", "DORSO").
     * @return Ruta relativa resultante (p.ej. {@code "Fotos/logo.png"}), o {@code null} en error.
     */
    String copiarImagenAProyecto(File imagenOrigen, ProyectoMetadata metadata, boolean esFondo, String sufijo);

    /**
     * Versión simplificada de {@link #copiarImagenAProyecto(File, ProyectoMetadata, boolean, String)}
     * sin sufijo en el nombre de archivo.
     *
     * @param imagenOrigen Archivo de imagen de origen.
     * @param metadata     Metadatos del proyecto destino.
     * @param esFondo      {@code true} para copiar a Fondos; {@code false} a Fotos.
     * @return Ruta relativa resultante, o {@code null} en error.
     */
    String copiarImagenAProyecto(File imagenOrigen, ProyectoMetadata metadata, boolean esFondo);

    /**
     * Copia un archivo de base de datos (CSV/XLSX) a la subcarpeta
     * {@code "Base de Datos (BBDD)"} del proyecto.
     *
     * <p>Se invoca al crear o editar un proyecto cuando la base de datos vinculada
     * es un archivo externo que debe internalizarse dentro de la carpeta del proyecto.</p>
     *
     * @param bdOrigen  Archivo de base de datos de origen.
     * @param metadata  Metadatos del proyecto destino.
     * @return Ruta absoluta del archivo copiado, o {@code null} si falla o la BD
     *         ya pertenece al proyecto.
     */
    String copiarBDAlProyecto(File bdOrigen, ProyectoMetadata metadata);
}
