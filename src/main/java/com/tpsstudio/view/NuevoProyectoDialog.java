package com.tpsstudio.view;

import com.tpsstudio.model.ClienteInfo;
import com.tpsstudio.model.ProyectoMetadata;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Optional;

/**
 * Diálogo para crear un nuevo proyecto TPS
 */
public class NuevoProyectoDialog extends Dialog<ProyectoMetadata> {

    private TextField txtNombre;
    private TextField txtUbicacion;
    private Button btnSeleccionarUbicacion;
    private CheckBox chkVincularBD;
    private TextField txtRutaBD;
    private Button btnSeleccionarBD;
    private Button btnDatosCliente;
    private Label lblClienteInfo;

    private ClienteInfo clienteInfo;

    public NuevoProyectoDialog() {
        setTitle("Nuevo Proyecto TPS");
        setHeaderText("Crear nuevo proyecto de diseño de tarjetas");

        // Inicializar cliente info vacío
        clienteInfo = new ClienteInfo();

        // Crear grid principal
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        // ===== NOMBRE DEL PROYECTO =====
        Label lblNombre = new Label("Nombre del proyecto:");
        lblNombre.setStyle("-fx-font-weight: bold;");
        txtNombre = new TextField();
        txtNombre.setPromptText("Ej: Tarjetas Corporativas 2024");
        txtNombre.setPrefWidth(400);

        // ===== UBICACIÓN =====
        Label lblUbicacion = new Label("Ubicación:");
        lblUbicacion.setStyle("-fx-font-weight: bold;");
        HBox hboxUbicacion = new HBox(5);
        txtUbicacion = new TextField();
        txtUbicacion.setEditable(false);
        txtUbicacion.setPromptText("Seleccione una carpeta...");
        txtUbicacion.setPrefWidth(300);
        btnSeleccionarUbicacion = new Button("Examinar...");
        btnSeleccionarUbicacion.setOnAction(e -> seleccionarUbicacion());
        hboxUbicacion.getChildren().addAll(txtUbicacion, btnSeleccionarUbicacion);

        Label lblUbicacionInfo = new Label("Se creará la carpeta: TPS_NombreDelProyecto/");
        lblUbicacionInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        // ===== DATOS DEL CLIENTE =====
        Label lblCliente = new Label("Datos del Cliente:");
        lblCliente.setStyle("-fx-font-weight: bold;");
        HBox hboxCliente = new HBox(10);
        btnDatosCliente = new Button("📋 Añadir/Editar Datos Cliente");
        btnDatosCliente.setOnAction(e -> abrirDatosCliente());
        lblClienteInfo = new Label("(Sin información del cliente)");
        lblClienteInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: gray; -fx-font-style: italic;");
        hboxCliente.getChildren().addAll(btnDatosCliente, lblClienteInfo);

        // ===== VINCULAR BASE DE DATOS =====
        chkVincularBD = new CheckBox("Vincular base de datos (opcional)");
        chkVincularBD.setStyle("-fx-font-weight: bold;");

        HBox hboxBD = new HBox(5);
        txtRutaBD = new TextField();
        txtRutaBD.setEditable(false);
        txtRutaBD.setDisable(true);
        txtRutaBD.setPromptText("Seleccione archivo Excel o Access...");
        txtRutaBD.setPrefWidth(300);
        btnSeleccionarBD = new Button("Examinar...");
        btnSeleccionarBD.setDisable(true);
        btnSeleccionarBD.setOnAction(e -> seleccionarBD());
        hboxBD.getChildren().addAll(txtRutaBD, btnSeleccionarBD);

        Label lblBDInfo = new Label("Para uso futuro con campos variables");
        lblBDInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        // Habilitar/deshabilitar BD según checkbox
        chkVincularBD.selectedProperty().addListener((obs, old, newVal) -> {
            txtRutaBD.setDisable(!newVal);
            btnSeleccionarBD.setDisable(!newVal);
            if (!newVal) {
                txtRutaBD.clear();
            }
        });

        // ===== LAYOUT =====
        int row = 0;
        grid.add(lblNombre, 0, row, 2, 1);
        row++;
        grid.add(txtNombre, 0, row, 2, 1);

        row++;
        grid.add(new Separator(), 0, row, 2, 1);

        row++;
        grid.add(lblUbicacion, 0, row, 2, 1);
        row++;
        grid.add(hboxUbicacion, 0, row, 2, 1);
        row++;
        grid.add(lblUbicacionInfo, 0, row, 2, 1);

        row++;
        grid.add(new Separator(), 0, row, 2, 1);

        row++;
        grid.add(lblCliente, 0, row, 2, 1);
        row++;
        grid.add(hboxCliente, 0, row, 2, 1);

        row++;
        grid.add(new Separator(), 0, row, 2, 1);

        row++;
        grid.add(chkVincularBD, 0, row, 2, 1);
        row++;
        grid.add(hboxBD, 0, row, 2, 1);
        row++;
        grid.add(lblBDInfo, 0, row, 2, 1);

        getDialogPane().setContent(grid);

        // ===== BOTONES =====
        ButtonType btnCrear = new ButtonType("Crear Proyecto", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(btnCrear, btnCancelar);

        // ===== VALIDACIÓN =====
        Node btnCrearNode = getDialogPane().lookupButton(btnCrear);
        btnCrearNode.setDisable(true);

        // Habilitar botón Crear solo si nombre y ubicación están completos
        txtNombre.textProperty().addListener((obs, old, newVal) -> {
            actualizarBotonCrear(btnCrearNode);
            actualizarInfoUbicacion(lblUbicacionInfo);
        });

        txtUbicacion.textProperty().addListener((obs, old, newVal) -> {
            actualizarBotonCrear(btnCrearNode);
        });

        // ===== CONVERTIR RESULTADO =====
        setResultConverter(buttonType -> {
            if (buttonType == btnCrear) {
                return crearMetadata();
            }
            return null;
        });
    }

    /**
     * Abre el DirectoryChooser para seleccionar ubicación
     */
    private void seleccionarUbicacion() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Seleccionar ubicación del proyecto");

        // Intentar usar ubicación de Documentos como inicial
        String userHome = System.getProperty("user.home");
        File documentsDir = new File(userHome, "Documents");
        if (documentsDir.exists()) {
            dirChooser.setInitialDirectory(documentsDir);
        }

        File dir = dirChooser.showDialog(getDialogPane().getScene().getWindow());
        if (dir != null) {
            txtUbicacion.setText(dir.getAbsolutePath());
        }
    }

    /**
     * Abre el FileChooser para seleccionar base de datos
     */
    private void seleccionarBD() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Base de Datos");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("Access", "*.accdb", "*.mdb"),
                new FileChooser.ExtensionFilter("Todos", "*.*"));

        File file = fileChooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (file != null) {
            txtRutaBD.setText(file.getAbsolutePath());
        }
    }

    /**
     * Abre el diálogo de datos del cliente
     */
    private void abrirDatosCliente() {
        DatosClienteDialog dialog = new DatosClienteDialog(clienteInfo);
        Optional<ClienteInfo> result = dialog.showAndWait();

        if (result.isPresent()) {
            clienteInfo = result.get();
            actualizarInfoCliente();
        }
    }

    /**
     * Actualiza el label de información del cliente
     */
    private void actualizarInfoCliente() {
        if (clienteInfo.tieneInformacion()) {
            StringBuilder info = new StringBuilder("✓ ");
            if (!clienteInfo.getNombreEmpresa().isEmpty()) {
                info.append(clienteInfo.getNombreEmpresa());
            } else if (!clienteInfo.getNombreContacto().isEmpty()) {
                info.append(clienteInfo.getNombreContacto());
            } else {
                info.append("Datos del cliente añadidos");
            }
            lblClienteInfo.setText(info.toString());
            lblClienteInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: green; -fx-font-style: normal;");
        } else {
            lblClienteInfo.setText("(Sin información del cliente)");
            lblClienteInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: gray; -fx-font-style: italic;");
        }
    }

    /**
     * Actualiza el estado del botón Crear
     */
    private void actualizarBotonCrear(Node btnCrear) {
        boolean nombreValido = !txtNombre.getText().trim().isEmpty();
        boolean ubicacionValida = !txtUbicacion.getText().isEmpty();
        btnCrear.setDisable(!(nombreValido && ubicacionValida));
    }

    /**
     * Actualiza el label de información de ubicación
     */
    private void actualizarInfoUbicacion(Label lblInfo) {
        String nombre = txtNombre.getText().trim();
        if (!nombre.isEmpty()) {
            String nombreCarpeta = "TPS_" + normalizarNombre(nombre);
            lblInfo.setText("Se creará la carpeta: " + nombreCarpeta + "/");
        } else {
            lblInfo.setText("Se creará la carpeta: TPS_NombreDelProyecto/");
        }
    }

    /**
     * Normaliza el nombre del proyecto para usarlo como nombre de carpeta
     */
    private String normalizarNombre(String nombre) {
        return nombre.replaceAll("[^a-zA-Z0-9_\\-\\s]", "_").replaceAll("\\s+", "_");
    }

    /**
     * Crea el objeto ProyectoMetadata con los datos del formulario
     */
    private ProyectoMetadata crearMetadata() {
        ProyectoMetadata metadata = new ProyectoMetadata();
        metadata.setNombre(txtNombre.getText().trim());
        metadata.setUbicacion(txtUbicacion.getText());
        metadata.setClienteInfo(clienteInfo);

        if (chkVincularBD.isSelected() && !txtRutaBD.getText().isEmpty()) {
            metadata.setRutaBBDD(txtRutaBD.getText());
        }

        return metadata;
    }
}
