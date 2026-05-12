package com.tpsstudio.view.controllers.sub;

import com.tpsstudio.model.print.SalidaImpresion;
import com.tpsstudio.model.print.SalidaImpresoraDirecta;
import com.tpsstudio.model.print.SalidaPDFSistema;
import com.tpsstudio.model.print.TrabajoImpresion;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.service.EtiquetasManager;
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
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sub-controlador que centraliza todas las acciones de proyecto.
 */
public class ProjectActionsController {

    private final MainViewModel viewModel;
    private final ProjectManager projectManager;
    private final Canvas canvas;          
    private final Runnable onRedraw;      
    private final EtiquetasManager etiquetasManager;

    public ProjectActionsController(MainViewModel viewModel,
                                    ProjectManager projectManager,
                                    Canvas canvas,
                                    Runnable onRedraw,
                                    EtiquetasManager etiquetasManager) {
        this.viewModel = viewModel;
        this.projectManager = projectManager;
        this.canvas = canvas;
        this.onRedraw = onRedraw;
        this.etiquetasManager = etiquetasManager;
    }

    public void nuevoProyecto() {
        Window owner = canvas.getScene() != null ? canvas.getScene().getWindow() : null;
        NuevoProyectoDialog dialog = new NuevoProyectoDialog(owner, etiquetasManager);
        Optional<ProyectoMetadata> result = dialog.showAndWait();

        if (result.isPresent()) {
            ProyectoMetadata metadata = result.get();
            Proyecto nuevo = projectManager.crearProyectoDesdeMetadata(metadata);

            if (nuevo != null) {
                nuevo.setEtiquetaIds(dialog.getEtiquetasSeleccionadas());
                projectManager.guardarProyecto();

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
                    alert.showAndWait();
                });
            }
        }
    }

    public void abrirProyecto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Abrir Proyecto");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos TPS", "*.tps"));

        File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (file != null) {
            projectManager.abrirProyectoDesdeArchivo(file);
        }
    }

    public void guardarProyecto() {
        projectManager.guardarProyecto();
    }

    public void exportarProyecto() {
        if (viewModel.getProyectoActual() == null) {
            AlertHelper.createAlert(Alert.AlertType.WARNING, "Selecciona un proyecto antes de exportar.").showAndWait();
            return;
        }

        FuenteDatos fd = projectManager.getFuenteDatos();
        int totalRegistros = (fd != null) ? fd.getTotalRegistros() : 1;

        ExportDialog exportDialog = new ExportDialog(
                canvas.getScene().getWindow(), totalRegistros, viewModel.getProyectoActual().getNombre());
        Optional<ExportDialog.ExportConfig> cfg = exportDialog.showAndWait();
        if (cfg.isEmpty() || cfg.get() == null) return;

        ExportDialog.ExportConfig config = cfg.get();
        List<Integer> filas = new ArrayList<>();
        if (config.exportarRegistros()) {
            try {
                filas = ExportDialog.parseRangoFilas(config.rangoFilas(), totalRegistros);
            } catch (IllegalArgumentException ex) {
                AlertHelper.createAlert(Alert.AlertType.ERROR, "El rango de registros no es válido:\n" + ex.getMessage()).showAndWait();
                return;
            }
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar ubicación para la exportación");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName(viewModel.getProyectoActual().getNombre().replaceAll("[^a-zA-Z0-9._-]", "_") + ".pdf");
        File destino = fc.showSaveDialog(canvas.getScene().getWindow());
        if (destino == null) return;

        com.tpsstudio.service.PDFExportService pdfService = new com.tpsstudio.service.PDFExportService(viewModel.getProyectoActual(), fd);
        final List<Integer> filasFinal = filas;
        final File basePath = destino;
        final Window ownerWindow = canvas.getScene().getWindow();

        new Thread(() -> {
            try {
                String baseUri = basePath.getAbsolutePath().replaceAll("(?i)\\.pdf$", "");
                int archivosGenerados = 0;
                if (config.exportarRegistros()) {
                    pdfService.exportar(config, filasFinal, new File(baseUri + "_registros.pdf"));
                    archivosGenerados++;
                }
                if (config.configPrueba() != null) {
                    pdfService.generarPruebaA4(config.configPrueba(), new File(baseUri + "_prueba.pdf"));
                    archivosGenerados++;
                }
                if (config.exportarImprenta()) {
                    pdfService.exportarImprenta(new File(baseUri + "_imprenta.pdf"));
                    archivosGenerados++;
                }
                final int total = archivosGenerados;
                Platform.runLater(() -> TPSToast.mostrarRelativo(canvas, "Exportación completada (" + total + " archivos)", TPSToast.Tipo.EXITO));
            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> AlertHelper.createAlert(Alert.AlertType.ERROR, "No se pudo completar la exportación: " + ex.getMessage()).showAndWait());
            }
        }, "pdf-export-thread").start();
    }

    public void imprimirProyecto() {
        if (viewModel.getProyectoActual() == null) {
            AlertHelper.createAlert(Alert.AlertType.WARNING, "Selecciona un proyecto antes de imprimir.").showAndWait();
            return;
        }

        FuenteDatos fd = projectManager.getFuenteDatos();
        ImpresionDialog dialog = new ImpresionDialog(canvas.getScene().getWindow(), viewModel.getProyectoActual(), fd);
        Optional<TrabajoImpresion> resultado = dialog.showAndWait();
        if (resultado.isPresent()) {
            ejecutarTrabajoImpresion(resultado.get(), viewModel.getProyectoActual(), fd);
        }
    }

    public boolean editarProyecto(Proyecto proyecto) {
        Window owner = canvas.getScene().getWindow();
        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto, owner, etiquetasManager);
        Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            projectManager.eliminarProyecto(proyecto);
            return true;
        }

        if (resultado.isPresent()) {
            ProyectoMetadata nuevaMetadata = resultado.get();
            projectManager.editarProyecto(proyecto, nuevaMetadata);
            projectManager.cargarFuenteDatos(nuevaMetadata.getRutaBBDD());
            return true;
        }
        return false;
    }

    public void validarDiseno() {
        if (viewModel.getProyectoActual() == null) return;
        com.tpsstudio.service.DesignValidatorService validator = new com.tpsstudio.service.DesignValidatorService();
        java.util.List<String> avisos = validator.validarDiseno(viewModel.getProyectoActual());

        Alert alert = AlertHelper.createAlert(Alert.AlertType.INFORMATION);
        alert.initOwner(canvas.getScene().getWindow());
        alert.setTitle("Validación de Diseño");
        alert.setHeaderText(avisos.isEmpty() ? "¡Diseño correcto!" : "Avisos de diseño encontrados:");

        if (avisos.isEmpty()) {
            alert.setContentText("No se han detectado problemas de resolución ni elementos fuera de las zonas seguras.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String aviso : avisos) sb.append("• ").append(aviso).append("\n\n");
            
            TextArea textArea = new TextArea(sb.toString().trim());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefHeight(250);
            textArea.setPrefWidth(460);
            
            VBox content = new VBox(textArea);
            VBox.setVgrow(textArea, javafx.scene.layout.Priority.ALWAYS);
            content.setPadding(new javafx.geometry.Insets(10, 0, 0, 0));
            alert.getDialogPane().setContent(content);
        }
        alert.showAndWait();
    }

    public void descargarPlantilla() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Descargar Plantilla CR80");
        fileChooser.setInitialFileName("Plantilla_CR80_TPS.pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));

        File file = fileChooser.showSaveDialog(canvas.getScene().getWindow());
        if (file == null) return;

        try (java.io.InputStream in = getClass().getResourceAsStream("/pdf/Plantilla_CR80_TPS.pdf")) {
            if (in != null) {
                java.nio.file.Files.copy(in, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                TPSToast.mostrarRelativo(canvas, "Plantilla descargada con éxito", TPSToast.Tipo.EXITO);
            } else {
                TPSToast.mostrar(canvas.getScene().getWindow(), "No se encontró el recurso interno /pdf/Plantilla_CR80_TPS.pdf", null, TPSToast.Tipo.ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            TPSToast.mostrar(canvas.getScene().getWindow(), "Error al guardar la plantilla.", null, TPSToast.Tipo.ERROR);
        }
    }

    private void ejecutarTrabajoImpresion(TrabajoImpresion trabajo, Proyecto proyecto, FuenteDatos fd) {
        Window owner = canvas.getScene().getWindow();
        new Thread(() -> {
            try {
                SalidaImpresion salida = (trabajo.nombreImpresora() != null) ? new SalidaImpresoraDirecta(trabajo.nombreImpresora()) : new SalidaPDFSistema();
                new ImpresionService().ejecutar(trabajo, proyecto, fd, salida);
                Platform.runLater(() -> TPSToast.mostrarRelativo(canvas, "Trabajo enviado a la cola de impresión", TPSToast.Tipo.EXITO));
            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> AlertHelper.createAlert(Alert.AlertType.ERROR, "No se pudo completar la impresión: " + ex.getMessage()).showAndWait());
            }
        }, "imprimir-thread").start();
    }
}
