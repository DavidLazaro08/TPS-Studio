package com.tpsstudio.service;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.*;
import com.tpsstudio.view.managers.EditorCanvasManager;
import com.tpsstudio.view.dialogs.NuevoProyectoDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import com.tpsstudio.util.ImageUtils;

/**
 * ProjectManager - Service para gestión de proyectos y elementos
 * 
 * Responsabilidades:
 * - CRUD de proyectos (crear, abrir, guardar, exportar)
 * - CRUD de elementos (añadir texto, imagen, fondo, eliminar)
 * - Gestión de archivos (FileChooser, diálogos)
 * - Lógica de negocio (validaciones, fit modes, etc.)
 */
public class ProjectManager {

    // Estado de proyectos
    private final ObservableList<Proyecto> proyectos = FXCollections.observableArrayList();
    private Proyecto proyectoActual;

    // Callbacks para notificar cambios al Controller
    private Runnable onProjectChanged;
    private Runnable onElementAdded;

    // Gestor de archivos
    private final ProyectoFileManager fileManager;
    private final RecentProjectsManager recentManager;

    // ========== CONSTRUCTOR ==========

    public ProjectManager() {
        this.fileManager = new ProyectoFileManager();
        this.recentManager = new RecentProjectsManager();
    }

    // ========== SETTERS para callbacks ==========

    public void setOnProjectChanged(Runnable callback) {
        this.onProjectChanged = callback;
    }

    public void setOnElementAdded(Runnable callback) {
        this.onElementAdded = callback;
    }

    // ========== GETTERS ==========

    public ObservableList<Proyecto> getProyectos() {
        return proyectos;
    }

    public Proyecto getProyectoActual() {
        return proyectoActual;
    }

    public void setProyectoActual(Proyecto proyecto) {
        this.proyectoActual = proyecto;
        if (onProjectChanged != null) {
            onProjectChanged.run();
        }
    }

    // ========== CRUD PROYECTOS ==========

    /**
     * Crea un nuevo proyecto CR80 (versión simple, sin diálogo)
     */
    public Proyecto crearNuevoCR80() {
        int numero = proyectos.size() + 1;
        Proyecto nuevoProyecto = new Proyecto("Tarjeta CR80 #" + numero);
        proyectos.add(nuevoProyecto);
        proyectoActual = nuevoProyecto;

        if (onProjectChanged != null) {
            onProjectChanged.run();
        }

        return nuevoProyecto;
    }

    /**
     * Crea un nuevo proyecto con diálogo completo y estructura de carpetas
     * 
     * @param owner Ventana padre para el diálogo
     * @return Proyecto creado o null si se canceló
     */
    public Proyecto nuevoProyecto(Window owner) {
        // Mostrar diálogo
        NuevoProyectoDialog dialog = new NuevoProyectoDialog();
        Optional<ProyectoMetadata> result = dialog.showAndWait();

        if (result.isEmpty()) {
            return null; // Usuario canceló
        }

        ProyectoMetadata metadata = result.get();

        // Crear estructura de carpetas
        if (!fileManager.crearEstructuraCarpetas(metadata)) {
            mostrarError("No se pudo crear la estructura de carpetas");
            return null;
        }

        // Crear proyecto
        Proyecto nuevoProyecto = new Proyecto(metadata.getNombre());
        nuevoProyecto.setMetadata(metadata);

        // Guardar proyecto inicial
        if (!fileManager.guardarProyecto(nuevoProyecto, metadata)) {
            mostrarError("No se pudo guardar el proyecto");
            return null;
        }

        // Añadir a lista
        proyectos.add(nuevoProyecto);
        proyectoActual = nuevoProyecto;

        // Añadir a recientes (IMPORTANTE: para que aparezca al reiniciar)
        recentManager.añadirReciente(metadata.getRutaTPS());

        // Ordenar alfabéticamente
        ordenarProyectos();

        // Notificar
        if (onProjectChanged != null) {
            onProjectChanged.run();
        }

        return nuevoProyecto;
    }

    /**
     * Abre un proyecto desde archivo .tps
     * 
     * @param owner Ventana padre para el FileChooser
     * @return Proyecto cargado o null si se cancela/falla
     */
    public Proyecto abrirProyecto(Window owner) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Abrir Proyecto");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos TPS", "*.tps"));

        File file = fileChooser.showOpenDialog(owner);
        if (file != null) {
            return cargarProyectoDesdeArchivo(file);
        }
        return null;
    }

    /**
     * Carga un proyecto desde un archivo específico
     */
    private Proyecto cargarProyectoDesdeArchivo(File file) {
        Proyecto proyecto = fileManager.cargarProyecto(file);
        if (proyecto != null) {
            proyectos.add(proyecto);
            proyectoActual = proyecto;

            // Añadir a recientes
            if (proyecto.getMetadata() != null) {
                recentManager.añadirReciente(proyecto.getMetadata().getRutaTPS());
            }

            // Ordenar alfabéticamente
            ordenarProyectos();

            if (onProjectChanged != null) {
                onProjectChanged.run();
            }

            mostrarInfo("Proyecto cargado correctamente");
        } else {
            mostrarError("No se pudo cargar el proyecto");
        }
        return proyecto;
    }

    /**
     * Carga proyectos recientes al iniciar la aplicación
     * 
     * @param maxProyectos Número máximo de proyectos a cargar (0 = ninguno, -1 =
     *                     todos)
     */
    public void cargarProyectosRecientes(int maxProyectos) {
        if (maxProyectos == 0) {
            return;
        }

        List<String> recientes = recentManager.getRecientes();
        int limite = maxProyectos < 0 ? recientes.size() : Math.min(maxProyectos, recientes.size());

        for (int i = 0; i < limite; i++) {
            String rutaTPS = recientes.get(i);
            File file = new File(rutaTPS);
            if (file.exists()) {
                Proyecto proyecto = fileManager.cargarProyecto(file);
                if (proyecto != null) {
                    proyectos.add(proyecto);
                    // NO seleccionar automáticamente - dejar canvas vacío
                }
            }
        }

        // Ordenar alfabéticamente
        ordenarProyectos();

        // NO llamar onProjectChanged - no hay proyecto seleccionado al inicio
    }

    /**
     * Ordena los proyectos alfabéticamente por nombre
     */
    private void ordenarProyectos() {
        FXCollections.sort(proyectos, (p1, p2) -> p1.getNombre().compareToIgnoreCase(p2.getNombre()));
    }

    /**
     * Elimina un proyecto de la lista de recientes
     */
    public void eliminarDeRecientes(Proyecto proyecto) {
        if (proyecto != null && proyecto.getMetadata() != null) {
            recentManager.eliminarReciente(proyecto.getMetadata().getRutaTPS());
        }
    }

    /**
     * Edita un proyecto existente
     * 
     * @param proyecto      Proyecto a editar
     * @param nuevaMetadata Nueva metadata con cambios
     * @return true si se actualizó correctamente
     */
    public boolean editarProyecto(Proyecto proyecto, ProyectoMetadata nuevaMetadata) {
        if (proyecto == null || nuevaMetadata == null) {
            return false;
        }

        // Actualizar nombre del proyecto
        proyecto.setNombre(nuevaMetadata.getNombre());
        proyecto.setMetadata(nuevaMetadata);

        // Guardar cambios
        boolean guardado = fileManager.guardarProyecto(proyecto, nuevaMetadata);

        if (guardado) {
            // Reordenar por si cambió el nombre
            ordenarProyectos();

            if (onProjectChanged != null) {
                onProjectChanged.run();
            }

            mostrarInfo("Proyecto actualizado correctamente");
        } else {
            mostrarError("No se pudo actualizar el proyecto");
        }

        return guardado;
    }

    /**
     * Elimina un proyecto de la lista (no del disco)
     */
    public void eliminarProyecto(Proyecto proyecto) {
        if (proyecto == null) {
            return;
        }

        // Eliminar de recientes
        eliminarDeRecientes(proyecto);

        // Eliminar de lista
        proyectos.remove(proyecto);

        // Si era el proyecto actual, limpiar selección
        if (proyectoActual == proyecto) {
            proyectoActual = null;
        }

        if (onProjectChanged != null) {
            onProjectChanged.run();
        }

        mostrarInfo("Proyecto eliminado de la lista");
    }

    /**
     * Guarda el proyecto actual
     * 
     * @return true si se guardó correctamente
     */
    public boolean guardarProyecto() {
        if (proyectoActual == null) {
            mostrarError("No hay proyecto activo para guardar");
            return false;
        }

        ProyectoMetadata metadata = proyectoActual.getMetadata();
        if (metadata == null || metadata.getRutaTPS() == null) {
            mostrarError(
                    "El proyecto no tiene ubicación definida.\nUse 'Nuevo Proyecto' para crear un proyecto con ubicación.");
            return false;
        }

        if (fileManager.guardarProyecto(proyectoActual, metadata)) {
            mostrarInfo("Proyecto guardado correctamente");
            return true;
        } else {
            mostrarError("No se pudo guardar el proyecto");
            return false;
        }
    }

    /**
     * Exporta el proyecto actual (placeholder)
     */
    public void exportarProyecto() {
        System.out.println("Exportar proyecto (placeholder)");
    }

    // ========== CRUD ELEMENTOS ==========

    /**
     * Añade un nuevo elemento de texto al proyecto actual
     * 
     * @return El elemento creado o null si no hay proyecto
     */
    public TextoElemento añadirTexto() {
        if (proyectoActual == null) {
            return null;
        }

        int num = proyectoActual.getElementosActuales().size() + 1;
        TextoElemento texto = new TextoElemento("Texto " + num, 50, 50);
        texto.setWidth(150); // Ancho inicial amplio para facilitar edición
        proyectoActual.getElementosActuales().add(texto);

        if (onElementAdded != null) {
            onElementAdded.run();
        }

        return texto;
    }

    /**
     * Añade una imagen al proyecto actual
     * Si el proyecto tiene metadata, copia la imagen a la carpeta Fotos/
     * 
     * @param window Ventana padre para el FileChooser
     * @return El elemento creado o null si se cancela o falla
     */
    public ImagenElemento añadirImagen(Window window) {
        if (proyectoActual == null) {
            return null;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Imagen");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            try {
                String rutaImagen;
                ProyectoMetadata metadata = proyectoActual.getMetadata();

                // Si hay metadata, copiar imagen al proyecto
                if (metadata != null && metadata.getRutaFotos() != null) {
                    String rutaRelativa = fileManager.copiarImagenAProyecto(file, metadata, false);
                    if (rutaRelativa == null) {
                        mostrarErrorCargaImagen("No se pudo copiar la imagen al proyecto");
                        return null;
                    }
                    rutaImagen = rutaRelativa;

                    // Cargar imagen desde la nueva ubicación
                    Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaRelativa);
                    Image img = ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

                    int num = proyectoActual.getElementosActuales().size() + 1;
                    ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50, rutaImagen, img);
                    proyectoActual.getElementosActuales().add(imgElem);

                    // Auto-guardar
                    fileManager.guardarProyecto(proyectoActual, metadata);

                    if (onElementAdded != null) {
                        onElementAdded.run();
                    }

                    return imgElem;
                } else {
                    // Proyecto sin metadata (modo antiguo)
                    Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());
                    int num = proyectoActual.getElementosActuales().size() + 1;
                    ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50,
                            file.getAbsolutePath(), img);
                    proyectoActual.getElementosActuales().add(imgElem);

                    if (onElementAdded != null) {
                        onElementAdded.run();
                    }

                    return imgElem;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                mostrarErrorCargaImagen(ex.getMessage());
                return null;
            }
        }

        return null;
    }

    /**
     * Añade o reemplaza el fondo del proyecto actual
     * Si el proyecto tiene metadata, copia la imagen a la carpeta Fondos/
     * 
     * @param window          Ventana padre para diálogos
     * @param fitModeProvider Función que provee el FondoFitMode (diálogo)
     * @return El elemento de fondo creado o null si se cancela
     */
    public ImagenFondoElemento añadirFondo(Window window, FitModeProvider fitModeProvider) {
        if (proyectoActual == null) {
            return null;
        }

        // Verificar si ya existe fondo
        ImagenFondoElemento fondoExistente = proyectoActual.getFondoActual();
        if (fondoExistente != null) {
            if (!confirmarReemplazoFondo()) {
                return null;
            }
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Imagen de Fondo");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            try {
                // Determinar modo de ajuste
                FondoFitMode fitMode;
                if (proyectoActual.isNoVolverAPreguntarFondo() &&
                        proyectoActual.getFondoFitModePreferido() != null) {
                    // Usar preferencia guardada
                    fitMode = proyectoActual.getFondoFitModePreferido();
                } else {
                    // Pedir al provider (diálogo)
                    fitMode = fitModeProvider.getFitMode();
                    if (fitMode == null) {
                        return null; // Usuario canceló
                    }
                }

                String rutaFondo;
                ProyectoMetadata metadata = proyectoActual.getMetadata();

                // Si hay metadata, copiar fondo al proyecto
                if (metadata != null && metadata.getRutaFondos() != null) {
                    // Determinar sufijo según la cara actual
                    String sufijo = proyectoActual.isMostrandoFrente() ? "FRENTE" : "DORSO";
                    String rutaRelativa = fileManager.copiarImagenAProyecto(file, metadata, true, sufijo);
                    if (rutaRelativa == null) {
                        mostrarErrorCargaImagen("No se pudo copiar el fondo al proyecto");
                        return null;
                    }
                    rutaFondo = rutaRelativa;

                    // Cargar imagen desde la nueva ubicación
                    Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaRelativa);
                    Image img = ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

                    ImagenFondoElemento nuevoFondo = new ImagenFondoElemento(
                            rutaFondo, img,
                            EditorCanvasManager.CARD_WIDTH,
                            EditorCanvasManager.CARD_HEIGHT,
                            fitMode);
                    nuevoFondo.ajustarATamaño(
                            EditorCanvasManager.CARD_WIDTH,
                            EditorCanvasManager.CARD_HEIGHT,
                            EditorCanvasManager.BLEED_MARGIN);

                    proyectoActual.setFondoActual(nuevoFondo);

                    // Auto-guardar
                    fileManager.guardarProyecto(proyectoActual, metadata);

                    if (onElementAdded != null) {
                        onElementAdded.run();
                    }

                    return nuevoFondo;
                } else {
                    // Proyecto sin metadata (modo antiguo)
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

                    if (onElementAdded != null) {
                        onElementAdded.run();
                    }

                    return nuevoFondo;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                mostrarErrorCargaImagen(ex.getMessage());
                return null;
            }
        }

        return null;
    }

    /**
     * Elimina un elemento del proyecto actual
     * 
     * @param elemento Elemento a eliminar
     * @return true si se eliminó, false si no
     */
    public boolean eliminarElemento(Elemento elemento) {
        if (proyectoActual != null && elemento != null) {
            boolean removed = proyectoActual.getElementosActuales().remove(elemento);
            if (removed && onProjectChanged != null) {
                onProjectChanged.run();
            }
            return removed;
        }
        return false;
    }

    // ========== DIÁLOGOS Y VALIDACIONES ==========

    /**
     * Muestra diálogo de confirmación para reemplazar fondo
     */
    private boolean confirmarReemplazoFondo() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reemplazar Fondo");
        alert.setHeaderText("Ya existe un fondo en esta cara");
        alert.setContentText("¿Desea reemplazar el fondo actual?");

        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /**
     * Muestra diálogo de error al cargar imagen
     */
    private void mostrarErrorCargaImagen(String mensaje) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.setTitle("Error");
        errorAlert.setHeaderText("No se pudo cargar la imagen");
        errorAlert.setContentText(mensaje);
        errorAlert.showAndWait();
    }

    /**
     * Muestra diálogo de error genérico
     */
    private void mostrarError(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Error");
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    /**
     * Muestra diálogo de información
     */
    private void mostrarInfo(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Información");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    // ========== INTERFAZ FUNCIONAL ==========

    /**
     * Interfaz funcional para proveer FondoFitMode
     * Permite al Controller mostrar el diálogo sin que ProjectManager dependa de UI
     */
    @FunctionalInterface
    public interface FitModeProvider {
        FondoFitMode getFitMode();
    }
}
