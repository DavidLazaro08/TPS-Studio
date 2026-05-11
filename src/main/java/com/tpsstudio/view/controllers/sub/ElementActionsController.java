package com.tpsstudio.view.controllers.sub;

import com.tpsstudio.model.elements.ElementoCodigo;
import com.tpsstudio.model.enums.TipoCodigo;
import com.tpsstudio.model.elements.FormaElemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.service.ProjectManager;
import com.tpsstudio.util.AlertHelper;
import com.tpsstudio.util.TPSToast;
import com.tpsstudio.view.managers.EditorCanvasManager;
import com.tpsstudio.viewmodel.MainViewModel;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.util.function.Supplier;

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
    private final Supplier<FondoFitMode> onFitModeDialog; // Callback → mostrarDialogoFitMode()

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
                                    Runnable onEnsureProps,
                                    Supplier<FondoFitMode> onFitModeDialog) {
        this.viewModel = viewModel;
        this.projectManager = projectManager;
        this.canvasManager = canvasManager;
        this.canvas = canvas;
        this.onRedraw = onRedraw;
        this.onEnsureProps = onEnsureProps;
        this.onFitModeDialog = onFitModeDialog;
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

        FondoFitMode fitMode;
        if (viewModel.getProyectoActual().isNoVolverAPreguntarFondo()
                && viewModel.getProyectoActual().getFondoFitModePreferido() != null) {
            fitMode = viewModel.getProyectoActual().getFondoFitModePreferido();
        } else {
            fitMode = onFitModeDialog.get();
            if (fitMode == null) return;
        }

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
        }
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
