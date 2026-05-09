package com.tpsstudio.view.controllers.sub;

import com.tpsstudio.model.print.SalidaImpresion;
import com.tpsstudio.model.print.SalidaImpresoraDirecta;
import com.tpsstudio.model.print.SalidaPDFSistema;
import com.tpsstudio.model.print.TrabajoImpresion;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.service.ImpresionService;
import com.tpsstudio.service.ProjectManager;
import com.tpsstudio.util.AlertHelper;
import com.tpsstudio.util.TPSToast;
import com.tpsstudio.view.dialogs.EditarProyectoDialog;
import com.tpsstudio.view.dialogs.ExportDialog;
import com.tpsstudio.view.dialogs.ImpresionDialog;
import com.tpsstudio.view.dialogs.NuevoProyectoDialog;
import com.tpsstudio.viewmodel.MainViewModel;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sub-controlador que centraliza todas las acciones de proyecto:
 * crear, abrir, guardar, exportar, imprimir y editar.
 *
 * <p>MainViewController delega en esta clase la lógica pesada de cada acción.
 * De esta forma, los métodos @FXML del controlador principal actúan como
 * simples puntos de entrada ("delegadores"), y la lógica real vive aquí.</p>
 *
 * <p>Esta clase no tiene acceso a los nodos @FXML directamente, solo necesita
 * el {@code canvas} para obtener el {@code Window} propietario de los diálogos.</p>
 */
public class ProjectActionsController {

    // Dependencias inyectadas desde MainViewController
    private final MainViewModel viewModel;
    private final ProjectManager projectManager;
    private final Canvas canvas;          // Solo para obtener getScene().getWindow()
    private final Runnable onRedraw;      // Callback para forzar redibujo del canvas

    /**
     * Crea el sub-controlador con las dependencias mínimas necesarias.
     *
     * @param viewModel      estado observable de la aplicación.
     * @param projectManager lógica de negocio de proyectos.
     * @param canvas         referencia al canvas central (solo para Window).
     * @param onRedraw       callback que ejecuta {@code dibujarCanvas()} en MainViewController.
     */
    public ProjectActionsController(MainViewModel viewModel,
                                    ProjectManager projectManager,
                                    Canvas canvas,
                                    Runnable onRedraw) {
        this.viewModel = viewModel;
        this.projectManager = projectManager;
        this.canvas = canvas;
        this.onRedraw = onRedraw;
    }

    // =========================================================
    // Acciones de proyecto
    // =========================================================

    /**
     * Abre el diálogo para crear un nuevo proyecto CR80 y, si se confirma,
     * lo crea y notifica al usuario con un alert informativo.
     */
    public void nuevoProyecto() {
        Window owner = canvas.getScene() != null ? canvas.getScene().getWindow() : null;
        NuevoProyectoDialog dialog = new NuevoProyectoDialog(owner);
        Optional<ProyectoMetadata> result = dialog.showAndWait();

        if (result.isPresent()) {
            ProyectoMetadata metadata = result.get();
            Proyecto nuevo = projectManager.crearProyectoDesdeMetadata(metadata);

            if (nuevo != null) {
                // Configurar propiedades físicas adicionales no incluidas en metadata
                nuevo.setTipoTroquel(dialog.getTipoTroquelSeleccionado());

                Platform.runLater(() -> {
                    Alert alert = AlertHelper.createAlert(Alert.AlertType.INFORMATION);
                    alert.initOwner(owner);
                    alert.setTitle("Proyecto Creado");
                    alert.setHeaderText("Proyecto creado y configurado con éxito");
                    alert.setContentText(
                            "Se ha generado la estructura completa para el proyecto:\n\n" +
                                    "Carpeta principal:\n" + metadata.getCarpetaProyecto() + "\n\n" +
                                    "Subcarpetas creadas automáticamente:\n" +
                                    "• Fotos\n" +
                                    "• Fondos\n" +
                                    "• Base de Datos (BBDD)");

                    String css = getClass().getResource("/css/dialogs.css").toExternalForm();
                    alert.getDialogPane().getStylesheets().add(css);

                    if (owner != null) {
                        alert.setOnShown(e -> {
                            javafx.stage.Stage stage = (javafx.stage.Stage) alert.getDialogPane().getScene().getWindow();
                            stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0);
                            stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0);
                        });
                    }
                    alert.showAndWait();
                });
            }
        }
    }

    /**
     * Muestra el FileChooser para abrir un proyecto .tps existente.
     */
    public void abrirProyecto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Abrir Proyecto");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos TPS", "*.tps"));

        File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (file != null) {
            projectManager.abrirProyectoDesdeArchivo(file);
        }
    }

    /**
     * Guarda el proyecto activo en su archivo .tps.
     */
    public void guardarProyecto() {
        projectManager.guardarProyecto();
    }

    /**
     * Abre el diálogo de configuración de exportación y, si el usuario confirma,
     * genera los PDFs correspondientes (Mail-Merge, prueba A4 y/o imprenta)
     * en un hilo de fondo para no bloquear la UI.
     */
    public void exportarProyecto() {
        if (viewModel.getProyectoActual() == null) {
            AlertHelper.createAlert(Alert.AlertType.WARNING, "Selecciona un proyecto antes de exportar.").showAndWait();
            return;
        }

        FuenteDatos fd = projectManager.getFuenteDatos();
        int totalRegistros = (fd != null) ? fd.getTotalRegistros() : 1;

        // 1. Diálogo de configuración de exportación
        ExportDialog exportDialog = new ExportDialog(
                canvas.getScene().getWindow(), totalRegistros, viewModel.getProyectoActual().getNombre());
        Optional<ExportDialog.ExportConfig> cfg = exportDialog.showAndWait();
        if (cfg.isEmpty() || cfg.get() == null) return;

        ExportDialog.ExportConfig config = cfg.get();

        // 2. Resolver filas a exportar (solo si exportarRegistros es true)
        List<Integer> filas = new ArrayList<>();
        if (config.exportarRegistros()) {
            try {
                filas = ExportDialog.parseRangoFilas(config.rangoFilas(), totalRegistros);
            } catch (IllegalArgumentException ex) {
                AlertHelper.createAlert(Alert.AlertType.ERROR, "El rango de registros no es válido:\n" + ex.getMessage())
                        .showAndWait();
                return;
            }
            if (filas.isEmpty()) {
                AlertHelper.createAlert(Alert.AlertType.WARNING, "Ningún registro válido seleccionado para Mail-Merge.")
                        .showAndWait();
                return;
            }
        }

        // 3. Elegir ubicación de destino
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar ubicación para la exportación");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName(viewModel.getProyectoActual().getNombre().replaceAll("[^a-zA-Z0-9._-]", "_") + ".pdf");
        File destino = fc.showSaveDialog(canvas.getScene().getWindow());
        if (destino == null) return;

        // 4. Generar PDFs en hilo de fondo
        com.tpsstudio.service.PDFExportService pdfService =
                new com.tpsstudio.service.PDFExportService(viewModel.getProyectoActual(), fd);

        final List<Integer> filasFinal = filas;
        final File basePath = destino;
        final Window ownerWindow = canvas.getScene().getWindow();

        new Thread(() -> {
            try {
                String baseUri = basePath.getAbsolutePath().replaceAll("(?i)\\.pdf$", "");
                int archivosGenerados = 0;

                // A) PDF Mail-Merge
                if (config.exportarRegistros()) {
                    File fMerge = new File(baseUri + "_registros.pdf");
                    pdfService.exportar(config, filasFinal, fMerge);
                    archivosGenerados++;
                }

                // B) Prueba A4
                if (config.configPrueba() != null) {
                    File fPrueba = new File(baseUri + "_prueba.pdf");
                    pdfService.generarPruebaA4(config.configPrueba(), fPrueba);
                    archivosGenerados++;
                }

                // C) PDF Imprenta
                if (config.exportarImprenta()) {
                    File fImprenta = new File(baseUri + "_imprenta.pdf");
                    pdfService.exportarImprenta(fImprenta);
                    archivosGenerados++;
                }

                final int total = archivosGenerados;
                Platform.runLater(() -> TPSToast.mostrar(
                        ownerWindow,
                        "Exportación completada (" + total + " archivos generados)",
                        null, TPSToast.Tipo.EXITO));

            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    Alert err = AlertHelper.createAlert(Alert.AlertType.ERROR);
                    err.setTitle("Error al exportar");
                    err.setHeaderText("No se pudo completar la exportación");
                    err.setContentText(ex.getMessage());
                    err.showAndWait();
                });
            }
        }, "pdf-export-thread").start();
    }

    /**
     * Abre el diálogo de impresión y, si el usuario confirma, genera un PDF temporal
     * y lo envía al sistema operativo (o a la impresora directa elegida).
     */
    public void imprimirProyecto() {
        if (viewModel.getProyectoActual() == null) {
            AlertHelper.createAlert(Alert.AlertType.WARNING, "Selecciona un proyecto antes de imprimir.").showAndWait();
            return;
        }

        if (!SalidaPDFSistema.isSupported()) {
            TPSToast.mostrar(canvas.getScene().getWindow(),
                    "La impresión mediante el sistema no está disponible en este equipo.",
                    null, TPSToast.Tipo.ERROR);
            return;
        }

        FuenteDatos fd = projectManager.getFuenteDatos();
        ImpresionDialog dialog = new ImpresionDialog(
                canvas.getScene().getWindow(),
                viewModel.getProyectoActual(),
                fd);

        Optional<TrabajoImpresion> resultado = dialog.showAndWait();
        if (resultado.isEmpty() || resultado.get() == null) return;

        ejecutarTrabajoImpresion(resultado.get(), viewModel.getProyectoActual(), fd);
    }

    /**
     * Abre el diálogo para editar o eliminar los datos de un proyecto existente.
     *
     * @param proyecto proyecto a editar.
     */
    public void editarProyecto(Proyecto proyecto) {
        Window owner = canvas.getScene().getWindow();
        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto, owner);
        Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            projectManager.eliminarProyecto(proyecto);
            return;
        }

        if (resultado.isPresent()) {
            ProyectoMetadata nuevaMetadata = resultado.get();
            projectManager.editarProyecto(proyecto, nuevaMetadata);

            // Si la BD vinculada cambió, recargar la fuente de datos
            projectManager.cargarFuenteDatos(nuevaMetadata.getRutaBBDD());
        }
    }

    // =========================================================
    // Helpers privados
    // =========================================================

    /**
     * Ejecuta el trabajo de impresión en un hilo de fondo.
     * Construye la estrategia de salida adecuada y delega en ImpresionService.
     */
    private void ejecutarTrabajoImpresion(TrabajoImpresion trabajo, Proyecto proyecto, FuenteDatos fd) {
        Window owner = canvas.getScene().getWindow();

        new Thread(() -> {
            try {
                SalidaImpresion salida;
                if (trabajo.nombreImpresora() != null) {
                    salida = new SalidaImpresoraDirecta(trabajo.nombreImpresora());
                } else {
                    salida = new SalidaPDFSistema();
                }
                new ImpresionService().ejecutar(trabajo, proyecto, fd, salida);

                Platform.runLater(() -> TPSToast.mostrar(
                        owner,
                        "Trabajo enviado a la cola de impresión",
                        null, TPSToast.Tipo.EXITO));

            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    Alert err = AlertHelper.createAlert(Alert.AlertType.ERROR);
                    err.setTitle("Error al imprimir");
                    err.setHeaderText("No se pudo completar la impresión");
                    err.setContentText(ex.getMessage());
                    err.showAndWait();
                });
            }
        }, "imprimir-thread").start();
    }
}
