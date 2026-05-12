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
 * Sub-controlador que centraliza todas las acciones sobre elementos del diseño:
 * añadir texto, imagen, forma o fondo, y eliminar el elemento seleccionado.
 *
 * <p>MainViewController delega en esta clase la lógica de cada acción de edición.
 * Los métodos son llamados directamente desde los callbacks de ModeManager
 * (que son lambdas registradas en setupCanvas).</p>
 *
 * <p>No tiene acceso a nodos @FXML directamente — solo usa {@code canvas}
 * para obtener el {@code Window} propietario de los diálogos FileChooser.</p>
 */
public class ElementActionsController {

    // Dependencias inyectadas desde MainViewController
    private final MainViewModel viewModel;
    private final ProjectManager projectManager;
    private final EditorCanvasManager canvasManager;
    private final Canvas canvas;              // Solo para obtener getScene().getWindow()
    private final Runnable onRedraw;          // Callback → dibujarCanvas()
    private final Runnable onEnsureProps;     // Callback → ensurePropertiesPanelVisible()

    /**
     * Crea el sub-controlador con las dependencias mínimas necesarias.
     *
     * @param viewModel       estado observable de la aplicación.
     * @param projectManager  lógica de negocio de proyectos y elementos.
     * @param canvasManager   gestor del canvas (para actualizar elemento seleccionado).
     * @param canvas          referencia al canvas central (solo para Window).
     * @param onRedraw        callback que ejecuta dibujarCanvas() en MainViewController.
     * @param onEnsureProps   callback que ejecuta ensurePropertiesPanelVisible() en MVC.
     * @param onFitModeDialog callback que muestra el diálogo de FitMode y devuelve la elección.
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

    /**
     * Añade un nuevo elemento de texto al proyecto activo y lo selecciona.
     */
    public void añadirTexto() {
        TextoElemento texto = projectManager.añadirTexto();
        if (texto != null) {
            viewModel.setElementoSeleccionado(texto);
            canvasManager.setElementoSeleccionado(texto);
            onEnsureProps.run();
        }
    }

    /**
     * Añade un nuevo placeholder de imagen al proyecto activo y lo selecciona.
     * Si la auto-detección vincula una columna de foto, muestra una notificación toast.
     */
    public void añadirImagen() {
        ImagenElemento imagen = projectManager.añadirImagenPlaceholder();
        if (imagen != null) {
            viewModel.setElementoSeleccionado(imagen);
            canvasManager.setElementoSeleccionado(imagen);
            onEnsureProps.run();

            // Avisar si se detectó y vinculó columna de foto automáticamente
            if (imagen.getColumnaVinculada() != null) {
                notificarColumnaAutoVinculada(imagen.getColumnaVinculada());
            }
        }
    }

    /**
     * Añade una nueva forma geométrica del tipo indicado y la selecciona.
     *
     * @param tipo tipo de forma (rectángulo, elipse, línea).
     */
    public void añadirForma(FormaElemento.TipoForma tipo) {
        FormaElemento forma = projectManager.añadirForma(tipo);
        if (forma != null) {
            viewModel.setElementoSeleccionado(forma);
            canvasManager.setElementoSeleccionado(forma);
            onEnsureProps.run();
        }
    }

    /**
     * Añade un nuevo código (QR o Barras) al proyecto activo y lo selecciona.
     */
    public void añadirCodigo(TipoCodigo tipo) {
        ElementoCodigo codigo = projectManager.añadirCodigo(tipo);
        if (codigo != null) {
            viewModel.setElementoSeleccionado(codigo);
            canvasManager.setElementoSeleccionado(codigo);
            onEnsureProps.run();
        }
    }

    /**
     * Muestra el FileChooser para seleccionar una imagen de fondo.
     * Si ya hay un fondo, pide confirmación antes de reemplazarlo.
     * Aplica el FitMode elegido por el usuario (o el preferido guardado).
     */
    public void añadirFondo() {
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

        ImagenFondoElemento fondo = projectManager.añadirFondoDesdeArchivo(file, fitMode);
        if (fondo != null) {
            viewModel.setElementoSeleccionado(fondo);
            canvasManager.setElementoSeleccionado(fondo);
        }
    }

    // =========================================================
    // Eliminar elemento
    // =========================================================

    /**
     * Elimina el elemento actualmente seleccionado del proyecto.
     */
    public void eliminarElemento() {
        if (projectManager.eliminarElemento(viewModel.getElementoSeleccionado())) {
            canvasManager.setElementoSeleccionado(null);
            onRedraw.run();
        }
    }

    /**
     * Abre el archivo del fondo en el editor externo configurado (si existe),
     * o en el editor predeterminado del sistema.
     */
    public void abrirEditorExterno(ImagenFondoElemento fondo) {
        new ExternalEditorService(viewModel.getProyectoActual()).abrirEditor(fondo);
    }

    /**
     * Recarga la imagen del fondo desde el disco.
     */
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

        // Resolver la ruta del archivo: puede ser relativa (proyectos con estructura)
        // o absoluta (proyectos legacy). En el caso relativo, se resuelve contra
        // la carpeta raíz del proyecto para obtener la ruta física real.
        final File file;
        String rutaGuardada = fondo.getRutaArchivo();
        var metadata = viewModel.getProyectoActual() != null
                ? viewModel.getProyectoActual().getMetadata() : null;

        if (!new File(rutaGuardada).isAbsolute() && metadata != null
                && metadata.getCarpetaProyecto() != null) {
            // Ruta relativa → resolverla contra la carpeta del proyecto
            Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaGuardada);
            file = rutaAbsoluta.toFile();
        } else {
            // Ruta absoluta (proyectos legacy) o sin metadata
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

            // Respetar preferencia guardada — igual que en añadirFondo()
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


    // =========================================================
    // Helpers privados
    // =========================================================

    /**
     * Muestra un diálogo de confirmación antes de reemplazar el fondo actual.
     */
    private boolean confirmarReemplazoFondo() {
        Alert alert = AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reemplazar Fondo");
        alert.setHeaderText("¡Ojo! Ya tienes un fondo puesto.");
        alert.setContentText("¿Seguro que quieres cambiarlo por uno nuevo?");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /**
     * Muestra un toast con retraso cuando la auto-detección vincula columna de foto.
     */
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
