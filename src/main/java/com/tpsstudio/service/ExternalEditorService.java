package com.tpsstudio.service;

import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.util.AlertHelper;
import javafx.scene.control.Alert;
import java.io.File;

/**
 * Servicio para gestionar la apertura de imágenes en editores externos.
 * Centraliza la lógica de búsqueda de archivos y lanzamiento de procesos.
 */
public class ExternalEditorService {

    private final Proyecto proyectoActual;

    public ExternalEditorService(Proyecto proyectoActual) {
        this.proyectoActual = proyectoActual;
    }

    public void abrirEditor(ImagenFondoElemento fondo) {
        if (fondo == null || fondo.getRutaArchivo() == null) {
            AlertHelper.createAlert(Alert.AlertType.WARNING, 
                "El fondo no tiene una ruta de archivo asociada.").showAndWait();
            return;
        }

        File file = localizarArchivo(fondo);

        if (!file.exists()) {
            AlertHelper.createAlert(Alert.AlertType.ERROR,
                "El archivo " + file.getName() + " no existe en el disco.\n\n" +
                "Buscado en: " + file.getAbsolutePath()).showAndWait();
            return;
        }

        lanzarProceso(file);
    }

    private File localizarArchivo(ImagenFondoElemento fondo) {
        File file = new File(fondo.getRutaArchivo());

        // Si la ruta guardada no existe, intentamos localizarla dentro del proyecto (/Fondos)
        if (!file.exists() && proyectoActual != null && proyectoActual.getMetadata() != null) {
            String projectDir = proyectoActual.getMetadata().getCarpetaProyecto();
            if (projectDir != null) {
                File fondosDir = new File(projectDir, "Fondos");
                String originalName = file.getName();

                File optionA = new File(fondosDir, originalName);
                
                String nameNoExt = originalName;
                String ext = "";
                int dotIndex = originalName.lastIndexOf('.');
                if (dotIndex > 0) {
                    nameNoExt = originalName.substring(0, dotIndex);
                    ext = originalName.substring(dotIndex);
                }

                String suffix = (fondo == proyectoActual.getFondoFrente()) ? "_FRENTE" : "_DORSO";
                File optionB = new File(fondosDir, nameNoExt + suffix + ext);

                if (optionB.exists()) {
                    fondo.setRutaArchivo(optionB.getAbsolutePath());
                    return optionB;
                } else if (optionA.exists()) {
                    fondo.setRutaArchivo(optionA.getAbsolutePath());
                    return optionA;
                }
            }
        }
        return file;
    }

    private void lanzarProceso(File file) {
        try {
            SettingsManager settings = new SettingsManager();
            String customEditor = settings.getExternalEditorPath();
            boolean opened = false;

            if (customEditor != null && new File(customEditor).exists()) {
                mostrarAviso(settings.getExternalEditorName());
                String[] cmd = { "cmd", "/c", "start", "\"\"", customEditor, file.getAbsolutePath() };
                new ProcessBuilder(cmd).start();
                opened = true;
            }

            if (!opened) {
                mostrarAviso("editor predeterminado");
                String[] cmd = { "cmd", "/c", "start", "\"\"", file.getAbsolutePath() };
                new ProcessBuilder(cmd).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                java.awt.Desktop.getDesktop().open(file);
            } catch (Exception ex) {
                AlertHelper.createAlert(Alert.AlertType.ERROR, "No se pudo abrir el editor.").showAndWait();
            }
        }
    }

    private void mostrarAviso(String editorName) {
        Alert aviso = AlertHelper.createAlert(Alert.AlertType.INFORMATION);
        aviso.setTitle("Editando externamente");
        aviso.setHeaderText("Abriendo con " + editorName + "...");
        aviso.setContentText("Puedes editar la imagen mientras TPS Studio permanece abierto.\n" +
                           "Cuando guardes los cambios, pulsa 'Recargar' aquí para ver el resultado.");
        aviso.show();
    }
}
