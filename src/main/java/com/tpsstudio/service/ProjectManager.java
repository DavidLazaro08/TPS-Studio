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
 * ProjectManager - Director de orquesta del Service Layer.
 * 
 * Se encarga de:
 * 1. Lo gordo: Crear, Guardar, Abrir y Exportar proyectos.
 * 2. Lo fino: Añadir textos, imágenes y fondos al proyecto actual.
 * 3. Lo burocrático: Gestionar diálogos de archivo y estructuras de carpetas.
 */
public class ProjectManager {

    // Lista observable de proyectos (para que la UI se entere si añadimos uno)
    private final ObservableList<Proyecto> proyectos = FXCollections.observableArrayList();
    private Proyecto proyectoActual;

    // Teléfonos rojos (Callbacks) para avisar al Controller
    private Runnable onProjectChanged;
    private Runnable onElementAdded;

    // Sub-gestores especializados
    private final ProyectoFileManager fileManager;
    private final RecentProjectsManager recentManager;

    // ========== CONSTRUCTOR ==========

    public ProjectManager() {
        this.fileManager = new ProyectoFileManager();
        this.recentManager = new RecentProjectsManager();
    }

    // ========== SETTERS (Conexiones) ==========

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

    // ========== OPERACIONES DE PROYECTO (CRUD) ==========

    /**
     * Crea un proyecto rápido en memoria (sin carpetas ni diálogo).
     * Útil para pruebas rápidas.
     */
    public Proyecto crearNuevoCR80() {
        int numero = proyectos.size() + 1;
        Proyecto nuevoProyecto = new Proyecto("Tarjeta CR80 #" + numero);
        proyectos.add(nuevoProyecto);
        proyectoActual = nuevoProyecto;

        // Avisamos que hay proyecto nuevo
        if (onProjectChanged != null) {
            onProjectChanged.run();
        }

        return nuevoProyecto;
    }

    /**
     * El "Big Bang": Crea un proyecto completo.
     * 1. Muestra formulario de datos.
     * 2. Crea carpetas físicas en disco.
     * 3. Crea el archivo .tps.
     * 4. Lo añade a recientes.
     */
    public Proyecto nuevoProyecto(Window owner) {
        // 1. Pedir datos al usuario
        NuevoProyectoDialog dialog = new NuevoProyectoDialog();
        Optional<ProyectoMetadata> result = dialog.showAndWait();

        if (result.isEmpty()) {
            return null; // Se echó atrás
        }

        ProyectoMetadata metadata = result.get();

        // 2. Crear estructura física (Carpetas, assets...)
        if (!fileManager.crearEstructuraCarpetas(metadata)) {
            mostrarError("No se pudo crear la estructura de carpetas.");
            return null;
        }

        // 3. Instanciar objeto Proyecto
        Proyecto nuevoProyecto = new Proyecto(metadata.getNombre());
        nuevoProyecto.setMetadata(metadata);

        // 4. Guardar archivo maestro .tps
        if (!fileManager.guardarProyecto(nuevoProyecto, metadata)) {
            mostrarError("No se pudo guardar el archivo de proyecto.");
            return null;
        }

        // 5. Añadir a la sesión actual
        proyectos.add(nuevoProyecto);
        proyectoActual = nuevoProyecto;

        // 6. Recordar para la próxima (Recientes)
        recentManager.añadirReciente(metadata.getRutaTPS());

        ordenarProyectos();

        if (onProjectChanged != null) {
            onProjectChanged.run();
        }

        return nuevoProyecto;
    }

    /**
     * Abre un proyecto existente (.tps) buscando en disco.
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
     * Lógica interna de carga de proyecto.
     */
    private Proyecto cargarProyectoDesdeArchivo(File file) {
        Proyecto proyecto = fileManager.cargarProyecto(file);
        if (proyecto != null) {
            proyectos.add(proyecto);
            proyectoActual = proyecto;

            // Refrescar en recientes (por si lo movimos, ahora sabemos que existe aquí)
            if (proyecto.getMetadata() != null) {
                recentManager.añadirReciente(proyecto.getMetadata().getRutaTPS());
            }

            ordenarProyectos();

            if (onProjectChanged != null) {
                onProjectChanged.run();
            }

            mostrarInfo("Proyecto cargado correctamente.");
        } else {
            mostrarError("Error al leer el archivo de proyecto.");
        }
        return proyecto;
    }

    /**
     * Resucita los proyectos de la sesión anterior.
     * 
     * @param maxProyectos Cuantos cargar (-1 para todos)
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
            // Solo cargamos si el archivo sigue existiendo
            if (file.exists()) {
                Proyecto proyecto = fileManager.cargarProyecto(file);
                if (proyecto != null) {
                    proyectos.add(proyecto);
                    // NOTA: No lo seleccionamos (proyectoActual) para no abrir de golpe el último
                }
            }
        }

        ordenarProyectos();
    }

    /**
     * Mantiene la lista de proyectos ordenada A-Z
     */
    private void ordenarProyectos() {
        FXCollections.sort(proyectos, (p1, p2) -> p1.getNombre().compareToIgnoreCase(p2.getNombre()));
    }

    /**
     * Borra un proyecto del historial, pero NO del disco.
     */
    public void eliminarDeRecientes(Proyecto proyecto) {
        if (proyecto != null && proyecto.getMetadata() != null) {
            recentManager.eliminarReciente(proyecto.getMetadata().getRutaTPS());
        }
    }

    /**
     * Actualiza datos del proyecto (Nombre, Cliente, etc).
     */
    public boolean editarProyecto(Proyecto proyecto, ProyectoMetadata nuevaMetadata) {
        if (proyecto == null || nuevaMetadata == null) {
            return false;
        }

        proyecto.setNombre(nuevaMetadata.getNombre());
        proyecto.setMetadata(nuevaMetadata);

        boolean guardado = fileManager.guardarProyecto(proyecto, nuevaMetadata);

        if (guardado) {
            ordenarProyectos();

            if (onProjectChanged != null) {
                onProjectChanged.run();
            }

            mostrarInfo("Proyecto actualizado correctamente.");
        } else {
            mostrarError("No se pudo actualizar el proyecto.");
        }

        return guardado;
    }

    /**
     * Cierra el proyecto de la interfaz (Lista de la izquierda).
     */
    public void eliminarProyecto(Proyecto proyecto) {
        if (proyecto == null) {
            return;
        }

        eliminarDeRecientes(proyecto);
        proyectos.remove(proyecto);

        if (proyectoActual == proyecto) {
            proyectoActual = null;
        }

        if (onProjectChanged != null) {
            onProjectChanged.run();
        }

        mostrarInfo("Proyecto cerrado.");
    }

    /**
     * Guarda cambios en el proyecto actual.
     */
    public boolean guardarProyecto() {
        if (proyectoActual == null) {
            mostrarError("No hay proyecto activo para guardar.");
            return false;
        }

        ProyectoMetadata metadata = proyectoActual.getMetadata();
        if (metadata == null || metadata.getRutaTPS() == null) {
            mostrarError(
                    "Este proyecto no tiene ubicación en disco (es volátil).\nUse 'Guardar Como' o cree uno nuevo.");
            return false;
        }

        if (fileManager.guardarProyecto(proyectoActual, metadata)) {
            mostrarInfo("Proyecto guardado correctamente.");
            return true;
        } else {
            mostrarError("Error crítico al guardar proyecto.");
            return false;
        }
    }

    public void exportarProyecto() {
        System.out.println("Funcionalidad de exportación pendiente...");
    }

    // ========== GESTIÓN DE ELEMENTOS (TEXTO/IMAGEN) ==========

    /**
     * Crea un elemento de texto y lo planta en el canvas.
     */
    public TextoElemento añadirTexto() {
        if (proyectoActual == null) {
            return null;
        }

        int num = proyectoActual.getElementosActuales().size() + 1;
        TextoElemento texto = new TextoElemento("Texto " + num, 50, 50);
        texto.setWidth(150); // Ancho generoso para empezar
        proyectoActual.getElementosActuales().add(texto);

        if (onElementAdded != null) {
            onElementAdded.run();
        }

        return texto;
    }

    /**
     * Importa una imagen externa:
     * 1. La copia a la carpeta 'Fotos' del proyecto (para portabilidad).
     * 2. La carga desde esa nueva ubicación.
     * 3. Crea el elemento en el canvas.
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

                // Lógica de importación segura
                if (metadata != null && metadata.getRutaFotos() != null) {
                    // Copiar físico
                    String rutaRelativa = fileManager.copiarImagenAProyecto(file, metadata, false);
                    if (rutaRelativa == null) {
                        mostrarErrorCargaImagen("Fallo al copiar imagen al repositorio del proyecto.");
                        return null;
                    }
                    rutaImagen = rutaRelativa;

                    // Cargar memoria (usando Proxy para no bloquear fichero)
                    Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaRelativa);
                    Image img = ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

                    int num = proyectoActual.getElementosActuales().size() + 1;
                    ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50, rutaImagen, img);
                    proyectoActual.getElementosActuales().add(imgElem);

                    // Auto-guardado de seguridad
                    fileManager.guardarProyecto(proyectoActual, metadata);

                    if (onElementAdded != null) {
                        onElementAdded.run();
                    }

                    return imgElem;
                } else {
                    // Fallback para proyectos legacy sin carpetas
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
     * Establece el FONDO de la tarjeta.
     * Similar a añadirImagen, pero:
     * 1. Copia a carpeta 'Fondos'.
     * 2. Pregunta si quieres Sangrado (Bleed) o ajuste exacto.
     * 3. Reemplaza cualquier fondo previo.
     */
    public ImagenFondoElemento añadirFondo(Window window, FitModeProvider fitModeProvider) {
        if (proyectoActual == null) {
            return null;
        }

        // Si ya hay fondo, preguntar antes de machacar
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
                // Decidir modo de ajuste (Bleed vs Final)
                FondoFitMode fitMode;
                if (proyectoActual.isNoVolverAPreguntarFondo() &&
                        proyectoActual.getFondoFitModePreferido() != null) {
                    fitMode = proyectoActual.getFondoFitModePreferido();
                } else {
                    // Preguntar al usuario mediante diálogo externo (inyectado)
                    fitMode = fitModeProvider.getFitMode();
                    if (fitMode == null) {
                        return null; // Cancelado
                    }
                }

                String rutaFondo;
                ProyectoMetadata metadata = proyectoActual.getMetadata();

                if (metadata != null && metadata.getRutaFondos() != null) {
                    // Copiar con sufijo FRENTE o DORSO para organización
                    String sufijo = proyectoActual.isMostrandoFrente() ? "FRENTE" : "DORSO";
                    String rutaRelativa = fileManager.copiarImagenAProyecto(file, metadata, true, sufijo);
                    if (rutaRelativa == null) {
                        mostrarErrorCargaImagen("Error copiando fondo al proyecto.");
                        return null;
                    }
                    rutaFondo = rutaRelativa;

                    Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaRelativa);
                    Image img = ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

                    ImagenFondoElemento nuevoFondo = new ImagenFondoElemento(
                            rutaFondo, img,
                            EditorCanvasManager.CARD_WIDTH,
                            EditorCanvasManager.CARD_HEIGHT,
                            fitMode);

                    // Ajustar automáticamente al tamaño del canvas
                    nuevoFondo.ajustarATamaño(
                            EditorCanvasManager.CARD_WIDTH,
                            EditorCanvasManager.CARD_HEIGHT,
                            EditorCanvasManager.BLEED_MARGIN);

                    proyectoActual.setFondoActual(nuevoFondo);

                    fileManager.guardarProyecto(proyectoActual, metadata);

                    if (onElementAdded != null) {
                        onElementAdded.run();
                    }

                    return nuevoFondo;
                } else {
                    // Legacy mode
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
     * Borra un elemento del canvas y avisa para refrescar.
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

    // ========== UTILIDADES Y DIÁLOGOS ==========

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
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Algo salió mal");
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void mostrarInfo(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Información");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    // ========== INTERFACES AUXILIARES ==========

    /**
     * Interfaz para pedir el modo de ajuste (Bleed vs Final) sin ensuciar
     * este servicio con lógica de JavaFX Dialogs complejos.
     */
    @FunctionalInterface
    public interface FitModeProvider {
        FondoFitMode getFitMode();
    }
}
