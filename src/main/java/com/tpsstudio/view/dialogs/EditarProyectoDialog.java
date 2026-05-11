package com.tpsstudio.view.dialogs;

import com.tpsstudio.model.project.ClienteInfo;
import com.tpsstudio.model.project.Etiqueta;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.service.EtiquetasManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Diálogo para editar un proyecto existente
 */
public class EditarProyectoDialog extends Dialog<ProyectoMetadata> {

    private final Proyecto proyecto;
    private final ProyectoMetadata metadata;

    private TextField txtNombre;
    private Label lblClienteInfo;
    private ClienteInfo clienteInfoActual;
    private CheckBox chkVincularBD;
    private TextField txtRutaBD;
    private String rutaAlmacenadaBD = null;
    private Label lblAvisoBD;
    private Button btnExaminarBD;

    // Categorías
    private final List<String> etiquetasSeleccionadas = new ArrayList<>();
    private HBox flowCategorias;
    private final EtiquetasManager etiquetasManager;

    private boolean eliminarProyecto = false;
    private final Window ownerWindow;

    private static final String CSS = EditarProyectoDialog.class
            .getResource("/css/dialogs.css").toExternalForm();

    public EditarProyectoDialog(Proyecto proyecto, Window owner, EtiquetasManager etiquetasManager) {
        this.proyecto = proyecto;
        this.ownerWindow = owner;
        this.etiquetasManager = etiquetasManager;
        // Pre-cargar las etiquetas ya asignadas al proyecto
        this.etiquetasSeleccionadas.addAll(proyecto.getEtiquetaIds());
        initOwner(owner);

        // Asegurar metadata no-null (por si acaso)
        ProyectoMetadata meta = proyecto.getMetadata();
        if (meta == null) {
            meta = new ProyectoMetadata();
            meta.setNombre(proyecto.getNombre());
        }
        this.metadata = meta;

        // Asegurar clienteInfoActual no-null
        this.clienteInfoActual = (metadata.getClienteInfo() != null)
                ? metadata.getClienteInfo()
                : new ClienteInfo();
        metadata.setClienteInfo(this.clienteInfoActual);

        setTitle("Editar Proyecto");
        setHeaderText("Modificar información del proyecto");

        // Aplicar CSS del diálogo
        getDialogPane().getStylesheets().add(CSS);

        // Crear contenido
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);

        // Nombre del proyecto
        Label lblNombre = new Label("Nombre del proyecto:");
        lblNombre.getStyleClass().add("lbl-section");
        txtNombre = new TextField(proyecto.getNombre());
        txtNombre.setPromptText("Ej: Tarjetas Corporativas 2024");

        // Datos del cliente
        Label lblCliente = new Label("Datos del cliente:");
        lblCliente.getStyleClass().add("lbl-section");
        HBox clienteBox = new HBox(10);
        clienteBox.setAlignment(Pos.CENTER_LEFT);

        lblClienteInfo = new Label(getTextoClienteInfo(clienteInfoActual));
        lblClienteInfo.getStyleClass().add("lbl-hint");

        Button btnEditarCliente = new Button("📋 Editar Datos Cliente");
        btnEditarCliente.getStyleClass().add("btn-dialog-action");
        btnEditarCliente.setOnAction(e -> abrirDialogoCliente());

        clienteBox.getChildren().addAll(lblClienteInfo, btnEditarCliente);

        // Categorías
        Label lblCats = new Label("Categorías:");
        lblCats.getStyleClass().add("lbl-section");

        flowCategorias = new HBox(6);
        flowCategorias.setAlignment(Pos.TOP_LEFT);
        flowCategorias.setPadding(new Insets(5, 0, 5, 0)); // Padding equilibrado

        ScrollPane scrollCats = new ScrollPane(flowCategorias);
        scrollCats.getStyleClass().add("scroll-invisible");
        scrollCats.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollCats.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollCats.setFitToHeight(true);
        scrollCats.setPrefHeight(42);
        scrollCats.setMinHeight(42);

        Button btnNuevaCat = new Button("+ Nueva");
        btnNuevaCat.getStyleClass().add("btn-dialog-action");
        btnNuevaCat.setMinWidth(Region.USE_PREF_SIZE);
        btnNuevaCat.setOnAction(e -> crearNuevaCategoria());

        HBox hboxCats = new HBox(8, btnNuevaCat, scrollCats);
        hboxCats.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(scrollCats, Priority.ALWAYS);

        Label lblCatsInfo = new Label("Organiza tus trabajos para filtrarlos rápidamente en la gestión principal.");
        lblCatsInfo.getStyleClass().add("lbl-hint");

        VBox vboxCats = new VBox(4, hboxCats, lblCatsInfo);
        rellenarChipsCategorias();

        // Base de datos
        Label lblBD = new Label("Base de datos:");
        lblBD.getStyleClass().add("lbl-section");
        chkVincularBD = new CheckBox("Vincular base de datos (Excel/Access)");

        HBox bdBox = new HBox(10);

        txtRutaBD = new TextField();
        txtRutaBD.setPromptText("Ruta del archivo de base de datos");
        txtRutaBD.setEditable(false);
        txtRutaBD.setDisable(true);
        HBox.setHgrow(txtRutaBD, Priority.ALWAYS);

        btnExaminarBD = new Button("Examinar...");
        btnExaminarBD.setDisable(true);
        btnExaminarBD.setOnAction(e -> seleccionarBaseDatos());

        bdBox.getChildren().addAll(txtRutaBD, btnExaminarBD);

        lblAvisoBD = new Label("");
        lblAvisoBD.getStyleClass().add("lbl-hint");
        lblAvisoBD.getStyleClass().add("aviso-bd-info");
        lblAvisoBD.setVisible(false);
        lblAvisoBD.setManaged(false);

        // Si ya tiene BD vinculada
        if (metadata.getRutaBBDD() != null && !metadata.getRutaBBDD().isEmpty()) {
            chkVincularBD.setSelected(true);
            rutaAlmacenadaBD = metadata.getRutaBBDD();
            actualizarVisualizacionRutaBD();
            txtRutaBD.setDisable(false);
            btnExaminarBD.setDisable(false);
        }

        chkVincularBD.selectedProperty().addListener((obs, old, val) -> {
            txtRutaBD.setDisable(!val);
            btnExaminarBD.setDisable(!val);
            if (!val) {
                txtRutaBD.clear();
                rutaAlmacenadaBD = null;
                lblAvisoBD.setVisible(false);
                lblAvisoBD.setManaged(false);
            } else if (rutaAlmacenadaBD != null) {
                actualizarVisualizacionRutaBD();
            }
        });

        // Información adicional
        Label lblInfo = new Label("ℹ Los cambios se aplicarán a la carpeta del proyecto");
        lblInfo.getStyleClass().add("lbl-hint");

        content.getChildren().addAll(
                lblNombre, txtNombre,
                new Separator(),
                lblCliente, clienteBox,
                new Separator(),
                lblCats, vboxCats,
                new Separator(),
                lblBD, chkVincularBD, bdBox, lblAvisoBD,
                new Separator(),
                lblInfo);

        getDialogPane().setContent(content);

        // Botones
        ButtonType btnGuardar = new ButtonType("Guardar Cambios", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnEliminarTipo = new ButtonType("Eliminar Proyecto", ButtonBar.ButtonData.LEFT);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        getDialogPane().getButtonTypes().addAll(btnEliminarTipo, btnCancelar, btnGuardar);

        // Estilo del botón eliminar (clase CSS, no inline)
        Button eliminarButton = (Button) getDialogPane().lookupButton(btnEliminarTipo);
        eliminarButton.getStyleClass().add("btn-danger");
        eliminarButton.setOnAction(e -> {
            Alert confirmacion = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
            confirmacion.setTitle("Confirmar eliminación");
            confirmacion.setHeaderText("¿Eliminar proyecto de la lista?");
            confirmacion.setContentText(
                    "El proyecto se eliminará de la lista de Trabajos, pero los archivos en disco NO se borrarán.");

            Optional<ButtonType> resultado = confirmacion.showAndWait();
            if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
                eliminarProyecto = true;
                setResult(null);
                close();
            }
        });

        // Validación y resultado
        Button guardarButton = (Button) getDialogPane().lookupButton(btnGuardar);
        guardarButton.setDisable(txtNombre.getText().trim().isEmpty());

        txtNombre.textProperty().addListener((obs, old, val) -> guardarButton.setDisable(val.trim().isEmpty()));

        setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                // Guardar etiquetas seleccionadas en el proyecto
                proyecto.setEtiquetaIds(new ArrayList<>(etiquetasSeleccionadas));

                metadata.setNombre(txtNombre.getText().trim());
                metadata.setClienteInfo(clienteInfoActual != null ? clienteInfoActual : new ClienteInfo());

                if (chkVincularBD.isSelected() && rutaAlmacenadaBD != null) {
                    metadata.setRutaBBDD(rutaAlmacenadaBD.isEmpty() ? null : rutaAlmacenadaBD);
                } else {
                    metadata.setRutaBBDD(null);
                }

                return metadata;
            }
            return null;
        });
    }

    // =========================================================
    // Chips de categorías
    // =========================================================

    private void rellenarChipsCategorias() {
        flowCategorias.getChildren().clear();
        if (etiquetasManager == null) return;
        for (Etiqueta e : etiquetasManager.getAll()) {
            flowCategorias.getChildren().add(crearChip(e));
        }
    }

    private ToggleButton crearChip(Etiqueta etiqueta) {
        Circle dot = new Circle(5);
        try { dot.setFill(Color.web(etiqueta.getColor())); } catch (Exception ex) { dot.setFill(Color.GRAY); }

        ToggleButton chip = new ToggleButton(etiqueta.getNombre());
        chip.setGraphic(dot);
        chip.setSelected(etiquetasSeleccionadas.contains(etiqueta.getId()));
        chip.getStyleClass().add("chip-categoria");
        chip.setOnAction(ev -> {
            if (chip.isSelected()) {
                etiquetasSeleccionadas.add(etiqueta.getId());
            } else {
                etiquetasSeleccionadas.remove(etiqueta.getId());
            }
        });
        return chip;
    }

    private void crearNuevaCategoria() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.initOwner(ownerWindow);
        dlg.setTitle("Nueva Categoría");
        dlg.setHeaderText(null);
        dlg.setContentText("Nombre de la categoría:");
        dlg.getDialogPane().getStylesheets().add(CSS);

        dlg.showAndWait().ifPresent(nombre -> {
            if (!nombre.isBlank() && etiquetasManager != null) {
                Etiqueta nueva = etiquetasManager.crear(nombre, null);
                etiquetasSeleccionadas.add(nueva.getId());
                rellenarChipsCategorias();
                flowCategorias.getChildren().stream()
                    .filter(n -> n instanceof ToggleButton)
                    .map(n -> (ToggleButton) n)
                    .filter(tb -> tb.getText().equals(nueva.getNombre()))
                    .findFirst()
                    .ifPresent(tb -> tb.setSelected(true));
            }
        });
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void abrirDialogoCliente() {
        if (clienteInfoActual == null)
            clienteInfoActual = new ClienteInfo();

        DatosClienteDialog dialog = new DatosClienteDialog(clienteInfoActual);
        Optional<ClienteInfo> resultado = dialog.showAndWait();

        if (resultado.isPresent()) {
            clienteInfoActual = resultado.get() != null ? resultado.get() : new ClienteInfo();
            lblClienteInfo.setText(getTextoClienteInfo(clienteInfoActual));
        }
    }

    private void seleccionarBaseDatos() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Base de Datos");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Archivos de Base de Datos", "*.xlsx", "*.xls", "*.accdb", "*.mdb"),
                new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("Access", "*.accdb", "*.mdb"));

        File inicial = buscarDirectorioInicial();
        if (inicial != null && inicial.exists() && inicial.isDirectory()) {
            fileChooser.setInitialDirectory(inicial);
        }

        Window dialogWindow = (ownerWindow != null) ? ownerWindow : getDialogPane().getScene().getWindow();
        File file = fileChooser.showOpenDialog(dialogWindow);
        if (file != null) {
            rutaAlmacenadaBD = file.getAbsolutePath();
            actualizarVisualizacionRutaBD();
        }
    }

    private void actualizarVisualizacionRutaBD() {
        if (rutaAlmacenadaBD == null || rutaAlmacenadaBD.isEmpty()) {
            txtRutaBD.clear();
            lblAvisoBD.setVisible(false);
            lblAvisoBD.setManaged(false);
            return;
        }

        File arch = new File(rutaAlmacenadaBD);
        String carpetaProy = metadata.getCarpetaProyecto();

        lblAvisoBD.setVisible(true);
        lblAvisoBD.setManaged(true);

        if (carpetaProy != null && rutaAlmacenadaBD.startsWith(carpetaProy)) {
            // Es la copia interna
            txtRutaBD.setText("📄 [COPIA INTERNA] " + arch.getName());
            lblAvisoBD.getStyleClass().removeAll("aviso-bd-info", "aviso-bd-ok");
            lblAvisoBD.getStyleClass().add("aviso-bd-ok");
            lblAvisoBD.setText("✓ Leyendo datos desde la protección remota interna del proyecto.\nDirectorio: "
                    + arch.getParent());
        } else {
            // Es un archivo externo nuevo que se va a vincular
            txtRutaBD.setText(rutaAlmacenadaBD);
            lblAvisoBD.getStyleClass().removeAll("aviso-bd-info", "aviso-bd-ok");
            lblAvisoBD.getStyleClass().add("aviso-bd-info");
            lblAvisoBD.setText(
                    "ℹ Al guardar cambios, Studio aislará y hará una copia interna del archivo para el proyecto.");
        }
    }

    private File buscarDirectorioInicial() {
        String userHome = System.getProperty("user.home");

        File docsES = new File(userHome, "Documentos");
        if (docsES.exists() && docsES.isDirectory())
            return docsES;

        File docsEN = new File(userHome, "Documents");
        if (docsEN.exists() && docsEN.isDirectory())
            return docsEN;

        File desktop = new File(userHome, "Desktop");
        if (desktop.exists() && desktop.isDirectory())
            return desktop;

        File home = new File(userHome);
        if (home.exists() && home.isDirectory())
            return home;

        return null;
    }

    private String getTextoClienteInfo(ClienteInfo info) {
        if (info == null || !info.tieneInformacion()) {
            return "Sin datos del cliente";
        }

        String empresa  = info.getNombreEmpresa()  != null ? info.getNombreEmpresa().trim()  : "";
        String contacto = info.getNombreContacto() != null ? info.getNombreContacto().trim() : "";

        if (!empresa.isEmpty())  return "✓ " + empresa;
        if (!contacto.isEmpty()) return "✓ " + contacto;

        return "✓ Datos del cliente añadidos";
    }

    public boolean isEliminarProyecto() {

        return eliminarProyecto;
    }
}
