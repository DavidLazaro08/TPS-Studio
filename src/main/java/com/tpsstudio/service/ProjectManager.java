package com.tpsstudio.service;

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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/* ProjectManager (Service Layer)
 *
 * Actúa como punto central de la lógica relacionada con proyectos:
 * - Crear / abrir / guardar (y más adelante exportar)
 * - Cargar recientes
 * - Añadir elementos al proyecto actual (texto, imagen, fondo)
 *
 * Nota: la UI se mantiene informada mediante callbacks (onProjectChanged / onElementAdded). */

public class ProjectManager {

    // Lista observable de proyectos (la UI se refresca automáticamente)
    private final ObservableList<Proyecto> proyectos = FXCollections.observableArrayList();

    // Proyecto actualmente activo en la app
    private Proyecto proyectoActual;

    // Callbacks para avisar al Controller
    private Runnable onProjectChanged;
    private Runnable onElementAdded;

    // Sub-gestores especializados (IO y recientes)
    private final ProyectoFileManager fileManager;
    private final RecentProjectsManager recentManager;

    // =====================================================
    // Constructor
    // =====================================================

    public ProjectManager() {
        this.fileManager = new ProyectoFileManager();
        this.recentManager = new RecentProjectsManager();
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

    /* Crea un proyecto completo:
     * 1) Pide datos en un diálogo
     * 2) Genera estructura de carpetas
     * 3) Crea y guarda el archivo .tps
     * 4) Lo añade a recientes y a la lista */

    public Proyecto nuevoProyecto(Window owner) {

        NuevoProyectoDialog dialog = new NuevoProyectoDialog();
        Optional<ProyectoMetadata> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null; // cancelado
        }

        ProyectoMetadata metadata = result.get();

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

        recentManager.añadirReciente(metadata.getRutaTPS());
        ordenarProyectos();
        avisarProyectoCambiado();

        return nuevoProyecto;
    }

    /* Abre un proyecto existente (.tps). */

    public Proyecto abrirProyecto(Window owner) {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Abrir Proyecto");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos TPS", "*.tps")
        );

        File file = fileChooser.showOpenDialog(owner);
        if (file == null) {
            return null;
        }

        return cargarProyectoDesdeArchivo(file);
    }

    /* Lógica interna de carga del proyecto desde un archivo .tps. */

    private Proyecto cargarProyectoDesdeArchivo(File file) {

        Proyecto proyecto = fileManager.cargarProyecto(file);

        if (proyecto == null) {
            mostrarError("Error al leer el archivo de proyecto.");
            return null;
        }

        proyectos.add(proyecto);
        proyectoActual = proyecto;

        // Refrescar en recientes (por si cambió de ruta y ahora sabemos dónde está)
        if (proyecto.getMetadata() != null) {
            recentManager.añadirReciente(proyecto.getMetadata().getRutaTPS());
        }

        ordenarProyectos();
        avisarProyectoCambiado();
        mostrarInfo("Proyecto cargado correctamente.");

        return proyecto;
    }

    /* Carga los proyectos recientes desde el historial.
     * maxProyectos: 0 = ninguno, -1 = todos, N = N proyectos */

    public void cargarProyectosRecientes(int maxProyectos) {

        if (maxProyectos == 0) return;

        List<String> recientes = recentManager.getRecientes();
        int limite = (maxProyectos < 0) ? recientes.size() : Math.min(maxProyectos, recientes.size());

        for (int i = 0; i < limite; i++) {
            File file = new File(recientes.get(i));

            // Solo cargamos si sigue existiendo
            if (!file.exists()) continue;

            Proyecto proyecto = fileManager.cargarProyecto(file);
            if (proyecto != null) {
                proyectos.add(proyecto);
                // Nota: no lo seleccionamos como proyectoActual (para no abrir de golpe el último)
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

        if (proyecto == null || nuevaMetadata == null) return false;

        proyecto.setNombre(nuevaMetadata.getNombre());
        proyecto.setMetadata(nuevaMetadata);

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

    /* Cierra el proyecto en la interfaz (lo quita de la lista). No borra archivos del disco. */

    public void eliminarProyecto(Proyecto proyecto) {

        if (proyecto == null) return;

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
            mostrarError("Este proyecto no tiene ubicación en disco.\nCrea uno nuevo o usa 'Guardar como' (pendiente).");
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

        if (proyectoActual == null) return null;

        int num = proyectoActual.getElementosActuales().size() + 1;

        TextoElemento texto = new TextoElemento("Texto " + num, 50, 50);
        texto.setWidth(150);

        proyectoActual.getElementosActuales().add(texto);
        avisarElementoAñadido();

        return texto;
    }

    /* Importa una imagen:
     * - Si el proyecto tiene carpeta /Fotos, se copia allí para que sea portable
     * - Si no (modo legacy), se referencia desde la ruta original */

    public ImagenElemento añadirImagen(Window window) {

        if (proyectoActual == null) return null;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Imagen");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        File file = fileChooser.showOpenDialog(window);
        if (file == null) return null;

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

    /* Establece el fondo de la tarjeta:
     * - Se copia a /Fondos (si existe estructura)
     * - Se pregunta el modo (con sangre / sin sangre) salvo que el proyecto recuerde la preferencia
     * - Reemplaza el fondo anterior de la cara actual */

    public ImagenFondoElemento añadirFondo(Window window, FitModeProvider fitModeProvider) {

        if (proyectoActual == null) return null;

        ImagenFondoElemento fondoExistente = proyectoActual.getFondoActual();
        if (fondoExistente != null && !confirmarReemplazoFondo()) {
            return null;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Imagen de Fondo");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        File file = fileChooser.showOpenDialog(window);
        if (file == null) return null;

        try {
            // Modo de ajuste (preferencia guardada o diálogo)
            FondoFitMode fitMode;

            if (proyectoActual.isNoVolverAPreguntarFondo() && proyectoActual.getFondoFitModePreferido() != null) {
                fitMode = proyectoActual.getFondoFitModePreferido();
            } else {
                fitMode = fitModeProvider.getFitMode();
                if (fitMode == null) return null;
            }

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
                        fitMode
                );

                nuevoFondo.ajustarATamaño(
                        EditorCanvasManager.CARD_WIDTH,
                        EditorCanvasManager.CARD_HEIGHT,
                        EditorCanvasManager.BLEED_MARGIN
                );

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
                    fitMode
            );

            nuevoFondo.ajustarATamaño(
                    EditorCanvasManager.CARD_WIDTH,
                    EditorCanvasManager.CARD_HEIGHT,
                    EditorCanvasManager.BLEED_MARGIN
            );

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
        if (proyectoActual == null || elemento == null) return false;

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
}
