package com.tpsstudio.view.controllers.sub;

import com.tpsstudio.model.elements.ElementoCodigo;
import com.tpsstudio.model.enums.TipoCodigo;
import com.tpsstudio.model.elements.FormaElemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.service.ExternalEditorService;
import com.tpsstudio.service.ProjectManager;
import com.tpsstudio.util.AlertHelper;
import com.tpsstudio.util.ImageUtils;
import com.tpsstudio.util.TPSToast;
import com.tpsstudio.view.managers.EditorCanvasManager;
import com.tpsstudio.viewmodel.MainViewModel;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Sub-controlador encargado de las acciones de edición sobre elementos:
 * añadir texto, imagen, forma, código, fondo y eliminar el elemento seleccionado.
 *
 * MainViewController delega aquí estas operaciones para mantener el controlador
 * principal más limpio.
 */
public class ElementActionsController {

    // =========================================================
    // Dependencias
    // =========================================================

    private final MainViewModel viewModel;
    private final ProjectManager projectManager;
    private final EditorCanvasManager canvasManager;
    private final Canvas canvas;
    private final Runnable onRedraw;
    private final Runnable onEnsureProps;

    /**
     * Crea el sub-controlador con las dependencias necesarias.
     *
     * @param viewModel      estado observable de la aplicación.
     * @param projectManager lógica de negocio de proyectos y elementos.
     * @param canvasManager  gestor del canvas.
     * @param canvas         canvas central, usado también para obtener la ventana.
     * @param onRedraw       callback para redibujar el canvas.
     * @param onEnsureProps  callback para mostrar el panel de propiedades.
     */
    public ElementActionsController(MainViewModel viewModel,
                                    ProjectManager projectManager,
                                    EditorCanvasManager canvasManager,
                                    Canvas canvas,
                                    Runnable onRedraw,
                                    Runnable onEnsureProps) {
        this.viewModel = viewModel;
        this.projectManager = projectManager;
        this.canvasManager = canvasManager;
        this.canvas = canvas;
        this.onRedraw = onRedraw;
        this.onEnsureProps = onEnsureProps;
    }

    // =========================================================
    // Añadir elementos
    // =========================================================

    public void anadirTexto() {
        TextoElemento texto = projectManager.anadirTexto();
        if (texto != null) {
            viewModel.setElementoSeleccionado(texto);
            canvasManager.setElementoSeleccionado(texto);
            onEnsureProps.run();
        }
    }

    public void anadirImagen() {
        ImagenElemento imagen = projectManager.anadirImagenPlaceholder();
        if (imagen != null) {
            viewModel.setElementoSeleccionado(imagen);
            canvasManager.setElementoSeleccionado(imagen);
            onEnsureProps.run();

            if (imagen.getColumnaVinculada() != null) {
                notificarColumnaAutoVinculada(imagen.getColumnaVinculada());
            }
        }
    }

    public void anadirForma(FormaElemento.TipoForma tipo) {
        FormaElemento forma = projectManager.anadirForma(tipo);
        if (forma != null) {
            viewModel.setElementoSeleccionado(forma);
            canvasManager.setElementoSeleccionado(forma);
            onEnsureProps.run();
        }
    }

    public void anadirCodigo(TipoCodigo tipo) {
        ElementoCodigo codigo = projectManager.anadirCodigo(tipo);
        if (codigo != null) {
            viewModel.setElementoSeleccionado(codigo);
            canvasManager.setElementoSeleccionado(codigo);
            onEnsureProps.run();
        }
    }

    public void anadirFondo() {
        if (viewModel.getProyectoActual() == null) return;

        ImagenFondoElemento fondoExistente = viewModel.getProyectoActual().getFondoActual();
        if (fondoExistente != null && !confirmarReemplazoFondo()) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Imagen de Fondo");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = fileChooser.showOpenDialog(canvas.getScene().getWindow());
        if (file == null) return;

        FondoFitMode fitMode = mostrarDialogoFitMode();
        if (fitMode == null) return;

        ImagenFondoElemento fondo = projectManager.anadirFondoDesdeArchivo(file, fitMode);
        if (fondo != null) {
            viewModel.setElementoSeleccionado(fondo);
            canvasManager.setElementoSeleccionado(fondo);
        }
    }

    // =========================================================
    // Eliminar elemento
    // =========================================================

    public void eliminarElemento() {
        if (projectManager.eliminarElemento(viewModel.getElementoSeleccionado())) {
            canvasManager.setElementoSeleccionado(null);
            onRedraw.run();
        }
    }

    // =========================================================
    // Editor externo y recarga de fondo
    // =========================================================

    public void abrirEditorExterno(ImagenFondoElemento fondo) {
        new ExternalEditorService(viewModel.getProyectoActual()).abrirEditor(fondo);
    }

    public void recargarFondo(ImagenFondoElemento fondo) {
        if (fondo == null || fondo.getRutaArchivo() == null) {
            Alert alert = AlertHelper.createAlert(Alert.AlertType.WARNING);
            alert.setTitle("Advertencia");
            alert.setHeaderText("No se puede recargar");
            alert.setContentText("El fondo no tiene una ruta de archivo asociada.\n" +
                    "Añade el fondo desde un archivo para poder recargarlo.");
            alert.showAndWait();
            return;
        }

        final File file;
        String rutaGuardada = fondo.getRutaArchivo();
        var metadata = viewModel.getProyectoActual() != null
                ? viewModel.getProyectoActual().getMetadata() : null;

        if (!new File(rutaGuardada).isAbsolute() && metadata != null
                && metadata.getCarpetaProyecto() != null) {
            Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaGuardada);
            file = rutaAbsoluta.toFile();
        } else {
            file = new File(rutaGuardada);
        }

        if (!file.exists()) {
            Alert alert = AlertHelper.createAlert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Archivo no encontrado");
            alert.setContentText("El archivo no existe en:\n" + file.getAbsolutePath() +
                    "\n\nSe mantendrá la versión anterior en memoria.");
            alert.showAndWait();
            return;
        }

        try {
            javafx.scene.image.Image nuevaImagen = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());
            if (nuevaImagen == null) throw new Exception("No se pudo cargar la imagen (resultado null)");

            fondo.setImagen(nuevaImagen);

            FondoFitMode nuevoModo;
            if (viewModel.getProyectoActual() != null
                    && viewModel.getProyectoActual().isNoVolverAPreguntarFondo()
                    && viewModel.getProyectoActual().getFondoFitModePreferido() != null) {
                nuevoModo = viewModel.getProyectoActual().getFondoFitModePreferido();
            } else {
                nuevoModo = mostrarDialogoFitMode();
            }

            if (nuevoModo != null) fondo.setFitMode(nuevoModo);

            fondo.ajustarATamaño(
                    EditorCanvasManager.CARD_WIDTH,
                    EditorCanvasManager.CARD_HEIGHT,
                    EditorCanvasManager.BLEED_MARGIN);

            onRedraw.run();
            TPSToast.mostrar(canvas.getScene().getWindow(), "Fondo recargado correctamente", null, TPSToast.Tipo.EXITO);

        } catch (Exception ex) {
            Alert alert = AlertHelper.createAlert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No se pudo recargar la imagen");
            alert.setContentText("Error al cargar el archivo:\n" + ex.getMessage() +
                    "\n\nSe mantendrá la versión anterior en memoria.");
            alert.showAndWait();
        }
    }

    // =========================================================
    // Diálogos
    // =========================================================

    private FondoFitMode mostrarDialogoFitMode() {
        Dialog<FondoFitMode> dialog = new Dialog<>();
        dialog.setTitle("Modo de Ajuste del Fondo");
        dialog.setHeaderText("¿Cómo desea ajustar el fondo a la tarjeta?");
        dialog.initOwner(canvas.getScene().getWindow());
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/dialogs.css").toExternalForm());

        ButtonType btnBleed = new ButtonType("Con sangre", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnFinal = new ButtonType("Sin sangre", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnBleed, btnFinal, btnCancelar);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label lblExplicacion = new Label("El fondo puede ajustarse de dos formas:");
        lblExplicacion.getStyleClass().add("lbl-section");

        VBox opcionBleed = new VBox(5);
        Label lblBleedTitulo = new Label("✓ Con sangre (CR80 + 2mm por lado)");
        lblBleedTitulo.getStyleClass().add("lbl-section");
        Label lblBleedDesc = new Label("Cubre el área completa incluyendo 2mm de sangrado (89.60 × 57.98 mm)");
        lblBleedDesc.getStyleClass().add("lbl-hint");
        Label lblBleedUso = new Label("Recomendado para fondos que se extienden hasta el borde");
        lblBleedUso.getStyleClass().add("lbl-hint");
        opcionBleed.getChildren().addAll(lblBleedTitulo, lblBleedDesc, lblBleedUso);

        VBox opcionFinal = new VBox(5);
        Label lblFinalTitulo = new Label("✓ Sin sangre (CR80 final)");
        lblFinalTitulo.getStyleClass().add("lbl-section");
        Label lblFinalDesc = new Label("Cubre solo el área final de la tarjeta (85.60 × 53.98 mm)");
        lblFinalDesc.getStyleClass().add("lbl-hint");
        Label lblFinalUso = new Label("Útil para fondos que no deben llegar al borde");
        lblFinalUso.getStyleClass().add("lbl-hint");
        opcionFinal.getChildren().addAll(lblFinalTitulo, lblFinalDesc, lblFinalUso);

        content.getChildren().addAll(
                lblExplicacion, new Separator(),
                opcionBleed, new Separator(),
                opcionFinal);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == btnBleed) return FondoFitMode.BLEED;
            if (buttonType == btnFinal) return FondoFitMode.FINAL;
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    private boolean confirmarReemplazoFondo() {
        Alert alert = AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reemplazar Fondo");
        alert.setHeaderText("¡Ojo! Ya tienes un fondo puesto.");
        alert.setContentText("¿Seguro que quieres cambiarlo por uno nuevo?");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    // =========================================================
    // Helpers privados
    // =========================================================

    private void notificarColumnaAutoVinculada(String columna) {
        PauseTransition delay = new PauseTransition(Duration.seconds(1.2));
        delay.setOnFinished(e -> TPSToast.mostrar(
                canvas.getScene().getWindow(),
                "✔ Columna \"" + columna + "\" vinculada automáticamente",
                "La imagen cambiará al navegar por los registros. Puedes cambiarla en Propiedades.",
                TPSToast.Tipo.EXITO,
                5.5));
        delay.play();
    }
}