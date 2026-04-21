package com.tpsstudio.service;

import com.tpsstudio.dao.ProyectoDAO;
import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.*;
import com.tpsstudio.util.ImageUtils;
import com.tpsstudio.view.dialogs.NuevoProyectoDialog;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.application.Platform;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Servicio principal de gestión de proyectos (capa Service).
 *
 * <p>Actúa como punto de entrada único para toda la lógica relacionada con
 * el ciclo de vida de los proyectos: crear, abrir, guardar, exportar, etc.
 * Delega las operaciones de persistencia al {@link ProyectoDAO} correspondiente.</p>
 *
 * <p><b>Patrón de comunicación con la Vista:</b><br/>
 * En lugar de depender de clases JavaFX directamente, utiliza callbacks
 * ({@link Runnable}, {@link java.util.function.BiConsumer}) para notificar
 * cambios a la capa superior, manteniendo el desacoplamiento Vista-Servicio.</p>
 *
 * @see ProyectoDAO
 * @see com.tpsstudio.service.ProyectoFileManager
 * @see com.tpsstudio.view.controllers.MainViewController
 */
public class ProjectManager {

    // Lista observable de proyectos (la UI se refresca automáticamente)
    private final javafx.collections.ObservableList<Proyecto> proyectos = javafx.collections.FXCollections.observableArrayList();

    // Proyecto actualmente activo en la app
    private Proyecto proyectoActual;

    // Callbacks para avisar al Controller
    private Runnable onProjectChanged;
    private Runnable onElementAdded;
    /** Callback para notificaciones (tipo: "info" | "error", mensaje). */
    private BiConsumer<String, String> onNotificacion;

    // Sub-gestores especializados (IO y recientes)
    /** Implementación DAO usada para persistir proyectos en disco. */
    private final ProyectoDAO fileManager;
    private final RecentProjectsManager recentManager;
    private final DatosVariablesManager datosVariablesManager;

    // Fuente de datos cargada del proyecto actual (null si no hay BD vinculada)
    private com.tpsstudio.model.project.FuenteDatos fuenteDatosActual;

    // =====================================================
    // Constructor
    // =====================================================

    public ProjectManager() {
        this.fileManager = new ProyectoFileManager();
        this.recentManager = new RecentProjectsManager();
        this.datosVariablesManager = new DatosVariablesManager();
    }

    // =====================================================
    // Conexiones (callbacks)
    // =====================================================

    public void setOnProjectChanged(Runnable callback) {
        this.onProjectChanged = callback;
    }

    public void setOnElementAdded(Runnable callback) {
        this.onElementAdded = callback;
    }

    /** Registrar callback de notificaciones (para mostrar toasts en la UI). */
    public void setOnNotificacion(BiConsumer<String, String> callback) {
        this.onNotificacion = callback;
    }

    // =====================================================
    // Getters / setters
    // =====================================================

    public ObservableList<Proyecto> getProyectos() {
        return proyectos;
    }

    public Proyecto getProyectoActual() {
        return proyectoActual;
    }

    public void setProyectoActual(Proyecto proyecto) {
        this.proyectoActual = proyecto;

        // Al seleccionar un proyecto de la lista, también asegurar que su BD (si tiene)
        // se carga en memoria
        String rutaBBDD = (proyecto != null && proyecto.getMetadata() != null) ? proyecto.getMetadata().getRutaBBDD()
                : null;
        cargarFuenteDatos(rutaBBDD);

        avisarProyectoCambiado();
    }

    // =====================================================
    // Operaciones de proyecto (crear / abrir / guardar / editar / cerrar)
    // =====================================================

    /* Crea un proyecto rápido en memoria (sin carpetas ni diálogo). */

    public Proyecto crearNuevoCR80() {
        int numero = proyectos.size() + 1;

        Proyecto nuevoProyecto = new Proyecto("Tarjeta CR80 #" + numero);
        proyectos.add(nuevoProyecto);

        proyectoActual = nuevoProyecto;
        avisarProyectoCambiado();

        return nuevoProyecto;
    }

    /*
     * Crea un proyecto completo desde los metadatos proporcionados por la UI:
     * 1) Genera estructura de carpetas
     * 2) Crea y guarda el archivo .tps
     * 3) Lo añade a recientes y a la lista
     */

    public Proyecto crearProyectoDesdeMetadata(ProyectoMetadata metadata) {
        if (metadata == null) return null;

        if (!fileManager.crearEstructuraCarpetas(metadata)) {
            mostrarError("No se pudo crear la estructura de carpetas.");
            return null;
        }

        Proyecto nuevoProyecto = new Proyecto(metadata.getNombre());
        nuevoProyecto.setMetadata(metadata);

        if (!fileManager.guardarProyecto(nuevoProyecto, metadata)) {
            mostrarError("No se pudo guardar el archivo de proyecto.");
            return null;
        }

        proyectos.add(nuevoProyecto);
        proyectoActual = nuevoProyecto;

        // Cargar fuente de datos si el proyecto ya traía una vinculada
        cargarFuenteDatos(metadata.getRutaBBDD());

        recentManager.añadirReciente(metadata.getRutaTPS());
        ordenarProyectos();
        avisarProyectoCambiado();

        return nuevoProyecto;
    }

    /* Abre un proyecto existente desde un archivo .tps ya seleccionado por la UI. */

    public Proyecto abrirProyectoDesdeArchivo(File file) {
        if (file == null) {
            return null;
        }

        Proyecto proyecto = fileManager.cargarProyecto(file);

        if (proyecto == null) {
            mostrarError("Error al leer el archivo de proyecto.");
            return null;
        }

        proyectos.add(proyecto);
        proyectoActual = proyecto;

        // Cargar fuente de datos si el proyecto tiene una BD vinculada
        String rutaBBDD = proyecto.getMetadata() != null ? proyecto.getMetadata().getRutaBBDD() : null;
        cargarFuenteDatos(rutaBBDD);

        // Refrescar en recientes (por si cambió de ruta y ahora sabemos dónde está)
        if (proyecto.getMetadata() != null) {
            recentManager.añadirReciente(proyecto.getMetadata().getRutaTPS());
        }

        ordenarProyectos();
        avisarProyectoCambiado();
        mostrarInfo("Proyecto cargado correctamente.");

        return proyecto;
    }

    /**
     * Carga la fuente de datos desde la ruta dada.
     * Si la ruta es null o vacía, descarga cualquier fuente anterior.
     * Llamar también desde EditarProyecto si se cambia la BD vinculada.
     */
    public void cargarFuenteDatos(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            fuenteDatosActual = null;
            return;
        }
        datosVariablesManager.cargar(ruta).ifPresentOrElse(
                datos -> fuenteDatosActual = datos,
                () -> fuenteDatosActual = null);
    }

    /** Devuelve la fuente de datos activa, o null si no hay ninguna. */
    public com.tpsstudio.model.project.FuenteDatos getFuenteDatos() {
        return fuenteDatosActual;
    }

    /*
     * Carga los proyectos recientes desde el historial.
     * maxProyectos: 0 = ninguno, -1 = todos, N = N proyectos
     */

    public void cargarProyectosRecientes(int maxProyectos) {

        if (maxProyectos == 0)
            return;

        List<String> recientes = recentManager.getRecientes();
        int limite = (maxProyectos < 0) ? recientes.size() : Math.min(maxProyectos, recientes.size());

        for (int i = 0; i < limite; i++) {
            File file = new File(recientes.get(i));

            // Solo cargamos si sigue existiendo
            if (!file.exists())
                continue;

            Proyecto proyecto = fileManager.cargarProyecto(file);
            if (proyecto != null) {
                proyectos.add(proyecto);
                // Nota: no lo seleccionamos como proyectoActual (para no abrir de golpe el
                // último)
            }
        }

        ordenarProyectos();
    }

    /* Mantiene la lista ordenada A-Z (para que no sea una tómbola visual). */

    private void ordenarProyectos() {
        FXCollections.sort(proyectos, (p1, p2) -> p1.getNombre().compareToIgnoreCase(p2.getNombre()));
    }

    /* Borra un proyecto del historial de recientes (pero NO del disco). */

    public void eliminarDeRecientes(Proyecto proyecto) {
        if (proyecto != null && proyecto.getMetadata() != null) {
            recentManager.eliminarReciente(proyecto.getMetadata().getRutaTPS());
        }
    }

    /* Actualiza datos del proyecto (nombre, cliente, etc.) y re-guarda el .tps. */

    public boolean editarProyecto(Proyecto proyecto, ProyectoMetadata nuevaMetadata) {

        if (proyecto == null || nuevaMetadata == null)
            return false;

        proyecto.setNombre(nuevaMetadata.getNombre());
        proyecto.setMetadata(nuevaMetadata);

        // Si la BD vinculada es un archivo externo al proyecto, copiarlo dentro
        String rutaBD = nuevaMetadata.getRutaBBDD();
        if (rutaBD != null && !rutaBD.isBlank()) {
            File bdFile = new File(rutaBD);
            if (bdFile.exists() && !esBDDentroDelProyecto(bdFile, nuevaMetadata)) {
                String rutaCopiada = fileManager.copiarBDAlProyecto(bdFile, nuevaMetadata);
                if (rutaCopiada != null) {
                    nuevaMetadata.setRutaBBDD(rutaCopiada);
                }
            }
        }

        boolean guardado = fileManager.guardarProyecto(proyecto, nuevaMetadata);

        if (guardado) {
            ordenarProyectos();
            avisarProyectoCambiado();
            mostrarInfo("Proyecto actualizado correctamente.");
            return true;
        }

        mostrarError("No se pudo actualizar el proyecto.");
        return false;
    }

    /*
     * Cierra el proyecto en la interfaz (lo quita de la lista). No borra archivos
     * del disco.
     */

    public void eliminarProyecto(Proyecto proyecto) {

        if (proyecto == null)
            return;

        eliminarDeRecientes(proyecto);
        proyectos.remove(proyecto);

        if (proyectoActual == proyecto) {
            proyectoActual = null;
        }

        avisarProyectoCambiado();
        mostrarInfo("Proyecto cerrado.");
    }

    /* Guarda el proyecto actual en su ruta .tps. */

    public boolean guardarProyecto() {

        if (proyectoActual == null) {
            mostrarError("No hay proyecto activo para guardar.");
            return false;
        }

        ProyectoMetadata metadata = proyectoActual.getMetadata();
        if (metadata == null || metadata.getRutaTPS() == null) {
            mostrarError(
                    "Este proyecto no tiene ubicación en disco.\nCrea uno nuevo o usa 'Guardar como' (pendiente).");
            return false;
        }

        if (fileManager.guardarProyecto(proyectoActual, metadata)) {
            mostrarInfo("Proyecto guardado correctamente.");
            return true;
        }

        mostrarError("Error al guardar el proyecto.");
        return false;
    }

    public void exportarProyecto() {
        System.out.println("Funcionalidad de exportación pendiente...");
    }

    // =====================================================
    // Gestión de elementos (texto / imagen / fondo)
    // =====================================================

    /* Crea un texto básico y lo añade al proyecto actual. */

    public TextoElemento añadirTexto() {

        if (proyectoActual == null)
            return null;

        int num = proyectoActual.getElementosActuales().size() + 1;

        TextoElemento texto = new TextoElemento("Texto " + num, 50, 50);
        texto.setWidth(150);

        proyectoActual.getElementosActuales().add(texto);
        avisarElementoAñadido();

        return texto;
    }

    /**
     * Añade una forma geométrica (Rectángulo, Elipse o Línea) al proyecto actual.
     */
    public FormaElemento añadirForma(FormaElemento.TipoForma tipo) {
        if (proyectoActual == null) return null;

        String nombreBase = switch (tipo) {
            case RECTANGULO -> "Rectángulo ";
            case ELIPSE -> "Elipse ";
            case LINEA -> "Línea ";
        };

        FormaElemento forma = new FormaElemento(
                nombreBase + (proyectoActual.getElementosActuales().size() + 1),
                50, 50, 100, 60, tipo);

        if (tipo == FormaElemento.TipoForma.LINEA) {
            forma.setHeight(4); // Pequeño alto inicial para facilitar selección
        }

        proyectoActual.getElementosActuales().add(forma);
        avisarElementoAñadido();
        return forma;
    }

    public ImagenElemento añadirImagenDesdeArchivo(File file) {

        if (proyectoActual == null || file == null)
            return null;

        try {
            ProyectoMetadata metadata = proyectoActual.getMetadata();

            // Proyecto con estructura (portable)
            if (metadata != null && metadata.getRutaFotos() != null) {

                String rutaRelativa = fileManager.copiarImagenAProyecto(file, metadata, false);
                if (rutaRelativa == null) {
                    mostrarErrorCargaImagen("Fallo al copiar imagen al repositorio del proyecto.");
                    return null;
                }

                Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaRelativa);
                Image img = ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

                int num = proyectoActual.getElementosActuales().size() + 1;
                ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50, rutaRelativa, img);

                proyectoActual.getElementosActuales().add(imgElem);

                // Auto-guardado (simple y útil)
                fileManager.guardarProyecto(proyectoActual, metadata);

                avisarElementoAñadido();
                return imgElem;
            }

            // Legacy (sin carpetas)
            Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());

            int num = proyectoActual.getElementosActuales().size() + 1;
            ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50, file.getAbsolutePath(), img);

            proyectoActual.getElementosActuales().add(imgElem);
            avisarElementoAñadido();

            return imgElem;

        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarErrorCargaImagen(ex.getMessage());
            return null;
        }
    }

    /*
     * Crea una ImagenElemento vac\u00eda (placeholder gris) sin abrir FileChooser.
     * Si hay FuenteDatos activa, detecta autom\u00e1ticamente columnas de foto.
     */
    public ImagenElemento a\u00f1adirImagenPlaceholder() {

        if (proyectoActual == null)
            return null;

        int num = proyectoActual.getElementosActuales().size() + 1;
        ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50, null, null);
        imgElem.setWidth(82); // proporción carnet ~35x45mm, tamaño cómodo sin ocupar demasiado
        imgElem.setHeight(106);

        // Auto-detecci\u00f3n de columna de foto si hay Excel vinculado
        if (fuenteDatosActual != null) {
            for (String columna : fuenteDatosActual.getColumnas()) {
                String upper = columna.toUpperCase();
                if (upper.equals("FOTO") || upper.equals("FOTOS")
                        || upper.equals("IMAGEN") || upper.equals("IMAGENES")) {
                    imgElem.setColumnaVinculada(columna);
                    break;
                }
            }
        }

        proyectoActual.getElementosActuales().add(imgElem);
        avisarElementoA\u00f1adido();

        return imgElem;
    }

    /*
     * Establece el fondo de la tarjeta desde un archivo.
     * Se espera que el controlador ya haya confirmado el reemplazo si hiciera falta.
     */

    public ImagenFondoElemento añadirFondoDesdeArchivo(File file, FondoFitMode fitMode) {

        if (proyectoActual == null || file == null || fitMode == null)
            return null;

        try {
            ProyectoMetadata metadata = proyectoActual.getMetadata();

            // Proyecto con estructura (portable)
            if (metadata != null && metadata.getRutaFondos() != null) {

                String sufijo = proyectoActual.isMostrandoFrente() ? "FRENTE" : "DORSO";
                String rutaRelativa = fileManager.copiarImagenAProyecto(file, metadata, true, sufijo);

                if (rutaRelativa == null) {
                    mostrarErrorCargaImagen("Error copiando fondo al proyecto.");
                    return null;
                }

                Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaRelativa);
                Image img = ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

                ImagenFondoElemento nuevoFondo = new ImagenFondoElemento(
                        rutaRelativa, img,
                        EditorCanvasManager.CARD_WIDTH,
                        EditorCanvasManager.CARD_HEIGHT,
                        fitMode);

                nuevoFondo.ajustarATamaño(
                        EditorCanvasManager.CARD_WIDTH,
                        EditorCanvasManager.CARD_HEIGHT,
                        EditorCanvasManager.BLEED_MARGIN);

                proyectoActual.setFondoActual(nuevoFondo);

                // Auto-guardado (igual que imágenes)
                fileManager.guardarProyecto(proyectoActual, metadata);

                avisarElementoAñadido();
                return nuevoFondo;
            }

            // Legacy
            Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());

            ImagenFondoElemento nuevoFondo = new ImagenFondoElemento(
                    file.getAbsolutePath(), img,
                    EditorCanvasManager.CARD_WIDTH,
                    EditorCanvasManager.CARD_HEIGHT,
                    fitMode);

            nuevoFondo.ajustarATamaño(
                    EditorCanvasManager.CARD_WIDTH,
                    EditorCanvasManager.CARD_HEIGHT,
                    EditorCanvasManager.BLEED_MARGIN);

            proyectoActual.setFondoActual(nuevoFondo);
            avisarElementoAñadido();

            return nuevoFondo;

        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarErrorCargaImagen(ex.getMessage());
            return null;
        }
    }

    /* Elimina un elemento del proyecto actual. */
    public boolean eliminarElemento(Elemento elemento) {
        if (proyectoActual == null || elemento == null)
            return false;

        boolean removed = proyectoActual.getElementosActuales().remove(elemento);

        if (removed) {
            avisarProyectoCambiado();
        }

        return removed;
    }

    // =====================================================
    // Utilidades / diálogos (Alerts)
    // =====================================================

    private boolean confirmarReemplazoFondo() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reemplazar Fondo");
        alert.setHeaderText("¡Ojo! Ya tienes un fondo puesto.");
        alert.setContentText("¿Seguro que quieres cambiarlo por uno nuevo?");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void mostrarErrorCargaImagen(String mensaje) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.setTitle("Ups...");
        errorAlert.setHeaderText("Problema con la imagen");
        errorAlert.setContentText(mensaje);
        errorAlert.showAndWait();
    }

    private void mostrarError(String mensaje) {
        if (onNotificacion != null) {
            onNotificacion.accept("error", mensaje);
        } else {
            // Fallback si no hay callback aún (durante inicialización)
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Algo salió mal");
            alert.setContentText(mensaje);
            alert.showAndWait();
        }
    }

    private void mostrarInfo(String mensaje) {
        if (onNotificacion != null) {
            onNotificacion.accept("info", mensaje);
        }
        // Si no hay callback registrado, silencioso (la UI lo gestiona)
    }

    // =====================================================
    // Helpers internos (para no repetir ifs por todos lados)
    // =====================================================

    private void avisarProyectoCambiado() {
        if (onProjectChanged != null) {
            onProjectChanged.run();
        }
    }

    private void avisarElementoAñadido() {
        if (onElementAdded != null) {
            onElementAdded.run();
        }
    }

    // =====================================================
    // Interfaces auxiliares
    // =====================================================

    @FunctionalInterface
    public interface FitModeProvider {
        FondoFitMode getFitMode();
    }

    /** Comprueba si el archivo de BD ya está dentro de la carpeta del proyecto. */
    private boolean esBDDentroDelProyecto(File bdFile, ProyectoMetadata metadata) {
        if (metadata.getCarpetaProyecto() == null)
            return false;
        return bdFile.getAbsolutePath().startsWith(metadata.getCarpetaProyecto());
    }
}
