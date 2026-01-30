package com.tpsstudio.service;

import com.tpsstudio.model.*;
import com.tpsstudio.view.EditorCanvasManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

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

    // ========== CONSTRUCTOR ==========

    public ProjectManager() {
        // Constructor vacío
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
     * Crea un nuevo proyecto CR80
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
     * Abre un proyecto existente (placeholder)
     */
    public void abrirProyecto() {
        System.out.println("Abrir proyecto (placeholder)");
    }

    /**
     * Guarda el proyecto actual (placeholder)
     */
    public void guardarProyecto() {
        System.out.println("Guardar proyecto (placeholder)");
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
                Image img = new Image(file.toURI().toString());
                int num = proyectoActual.getElementosActuales().size() + 1;
                ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50,
                        file.getAbsolutePath(), img);
                proyectoActual.getElementosActuales().add(imgElem);

                if (onElementAdded != null) {
                    onElementAdded.run();
                }

                return imgElem;
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
                Image img = new Image(file.toURI().toString());

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
