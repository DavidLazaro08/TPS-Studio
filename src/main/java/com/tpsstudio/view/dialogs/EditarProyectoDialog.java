package com.tpsstudio.view.dialogs;

import com.tpsstudio.model.project.ClienteInfo;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
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
    private Button btnExaminarBD;

    private boolean eliminarProyecto = false;

    public EditarProyectoDialog(Proyecto proyecto) {
        this.proyecto = proyecto;

        // Asegurar metadata no-null (por si acaso)
        ProyectoMetadata meta = proyecto.getMetadata();
        if (meta == null) {
            meta = new ProyectoMetadata();
            meta.setNombre(proyecto.getNombre());
        }
        this.metadata = meta;

        // Asegurar clienteInfoActual no-null
        this.clienteInfoActual = (metadata.getClienteInfo() != null) ? metadata.getClienteInfo() : new ClienteInfo();
        metadata.setClienteInfo(this.clienteInfoActual);

        setTitle("Editar Proyecto");
        setHeaderText("Modificar información del proyecto");

        // Crear contenido
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);

        // Nombre del proyecto
        Label lblNombre = new Label("Nombre del proyecto:");
        txtNombre = new TextField(proyecto.getNombre());
        txtNombre.setPromptText("Ej: Tarjetas Corporativas 2024");

        // Datos del cliente
        Label lblCliente = new Label("Datos del cliente:");
        HBox clienteBox = new HBox(10);
        clienteBox.setAlignment(Pos.CENTER_LEFT);

        lblClienteInfo = new Label(getTextoClienteInfo(clienteInfoActual));
        lblClienteInfo.setStyle("-fx-text-fill: #666;");

        Button btnEditarCliente = new Button("📋 Editar Datos Cliente");
        btnEditarCliente.setOnAction(e -> abrirDialogoCliente());

        clienteBox.getChildren().addAll(lblClienteInfo, btnEditarCliente);

        // Base de datos
        Label lblBD = new Label("Base de datos:");
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

        // Si ya tiene BD vinculada
        if (metadata.getRutaBBDD() != null && !metadata.getRutaBBDD().isEmpty()) {
            chkVincularBD.setSelected(true);
            txtRutaBD.setText(metadata.getRutaBBDD());
            txtRutaBD.setDisable(false);
            btnExaminarBD.setDisable(false);
        }

        chkVincularBD.selectedProperty().addListener((obs, old, val) -> {
            txtRutaBD.setDisable(!val);
            btnExaminarBD.setDisable(!val);
            if (!val) {
                txtRutaBD.clear();
            }
        });

        // Información adicional
        Label lblInfo = new Label("ℹ️ Los cambios se aplicarán a la carpeta del proyecto");
        lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        content.getChildren().addAll(
                lblNombre, txtNombre,
                new Separator(),
                lblCliente, clienteBox,
                new Separator(),
                lblBD, chkVincularBD, bdBox,
                new Separator(),
                lblInfo
        );

        getDialogPane().setContent(content);

        // Botones
        ButtonType btnGuardar = new ButtonType("Guardar Cambios", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnEliminarTipo = new ButtonType("Eliminar Proyecto", ButtonBar.ButtonData.LEFT);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        getDialogPane().getButtonTypes().addAll(btnEliminarTipo, btnCancelar, btnGuardar);

        // Estilo del botón eliminar
        Button eliminarButton = (Button) getDialogPane().lookupButton(btnEliminarTipo);
        eliminarButton.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white;");
        eliminarButton.setOnAction(e -> {
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
            confirmacion.setTitle("Confirmar eliminación");
            confirmacion.setHeaderText("¿Eliminar proyecto de la lista?");
            confirmacion.setContentText(
                    "El proyecto se eliminará de la lista de Trabajos, pero los archivos en disco NO se borrarán."
            );

            Optional<ButtonType> resultado = confirmacion.showAndWait();
            if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
                eliminarProyecto = true;
                setResult(null); // Señal para eliminar
                close();
            }
        });

        // Validación y resultado
        Button guardarButton = (Button) getDialogPane().lookupButton(btnGuardar);
        guardarButton.setDisable(txtNombre.getText().trim().isEmpty());

        txtNombre.textProperty().addListener((obs, old, val) -> {
            guardarButton.setDisable(val.trim().isEmpty());
        });

        setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                // Actualizar metadata
                metadata.setNombre(txtNombre.getText().trim());
                metadata.setClienteInfo(clienteInfoActual != null ? clienteInfoActual : new ClienteInfo());

                if (chkVincularBD.isSelected()) {
                    String ruta = txtRutaBD.getText() != null ? txtRutaBD.getText().trim() : "";
                    metadata.setRutaBBDD(ruta.isEmpty() ? null : ruta);
                } else {
                    metadata.setRutaBBDD(null);
                }

                return metadata;
            }
            return null;
        });
    }

    private void abrirDialogoCliente() {
        if (clienteInfoActual == null) clienteInfoActual = new ClienteInfo();

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
                new FileChooser.ExtensionFilter("Access", "*.accdb", "*.mdb")
        );

        File inicial = buscarDirectorioInicial();
        if (inicial != null && inicial.exists() && inicial.isDirectory()) {
            fileChooser.setInitialDirectory(inicial);
        }

        File file = fileChooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (file != null) {
            txtRutaBD.setText(file.getAbsolutePath());
        }
    }

    private File buscarDirectorioInicial() {
        String userHome = System.getProperty("user.home");

        File docsES = new File(userHome, "Documentos");
        if (docsES.exists() && docsES.isDirectory()) return docsES;

        File docsEN = new File(userHome, "Documents");
        if (docsEN.exists() && docsEN.isDirectory()) return docsEN;

        File desktop = new File(userHome, "Desktop");
        if (desktop.exists() && desktop.isDirectory()) return desktop;

        File home = new File(userHome);
        if (home.exists() && home.isDirectory()) return home;

        return null;
    }

    private String getTextoClienteInfo(ClienteInfo info) {
        if (info == null || !info.tieneInformacion()) {
            return "Sin datos del cliente";
        }

        String empresa = info.getNombreEmpresa() != null ? info.getNombreEmpresa().trim() : "";
        String contacto = info.getNombreContacto() != null ? info.getNombreContacto().trim() : "";

        if (!empresa.isEmpty()) return "✓ " + empresa;
        if (!contacto.isEmpty()) return "✓ " + contacto;

        return "✓ Datos del cliente añadidos";
    }

    public boolean isEliminarProyecto() {
        return eliminarProyecto;
    }
}
