package com.tpsstudio.view.dialogs;

import com.tpsstudio.model.project.ClienteInfo;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.model.enums.TipoTroquel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.Optional;

/* Diálogo para crear un nuevo proyecto TPS */

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

    private Label lblUbicacionInfo;

    private ComboBox<TipoTroquel> cmbTroquel;

    // Ventana propietaria (para centrado y file choosers)
    private final Window ownerWindow;

    private static final String CSS = NuevoProyectoDialog.class
            .getResource("/css/dialogs.css").toExternalForm();

    public NuevoProyectoDialog(Window owner) {
        this.ownerWindow = owner;
        initOwner(owner); // centra el diálogo sobre la ventana principal

        setTitle("Nuevo Proyecto TPS");
        setHeaderText("Crear nuevo proyecto de diseño de tarjetas");

        // Aplicar hoja de estilos del diálogo
        getDialogPane().getStylesheets().add(CSS);

        // Inicializar cliente info vacío (siempre no-null)
        clienteInfo = new ClienteInfo();

        // Grid principal
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        // ===== NOMBRE DEL PROYECTO =====
        Label lblNombre = new Label("Nombre del proyecto:");
        lblNombre.getStyleClass().add("lbl-section");
        txtNombre = new TextField();
        txtNombre.setPromptText("Ej: Tarjetas Corporativas 2024");
        txtNombre.setPrefWidth(400);

        // ===== UBICACIÓN =====
        Label lblUbicacion = new Label("Ubicación:");
        lblUbicacion.getStyleClass().add("lbl-section");
        HBox hboxUbicacion = new HBox(5);

        txtUbicacion = new TextField();
        txtUbicacion.setEditable(false);
        txtUbicacion.setPromptText("Seleccione una carpeta...");
        txtUbicacion.setPrefWidth(300);

        btnSeleccionarUbicacion = new Button("Examinar...");
        btnSeleccionarUbicacion.setOnAction(e -> seleccionarUbicacion());

        hboxUbicacion.getChildren().addAll(txtUbicacion, btnSeleccionarUbicacion);

        lblUbicacionInfo = new Label("Se creará la carpeta: TPS_NombreDelProyecto/");
        lblUbicacionInfo.getStyleClass().add("lbl-hint");

        // ===== DATOS DEL CLIENTE =====
        Label lblCliente = new Label("Datos del Cliente:");
        lblCliente.getStyleClass().add("lbl-section");
        HBox hboxCliente = new HBox(10);

        btnDatosCliente = new Button("📋 Añadir/Editar Datos Cliente");
        btnDatosCliente.setOnAction(e -> abrirDatosCliente());

        lblClienteInfo = new Label("(Sin información del cliente)");
        lblClienteInfo.getStyleClass().add("lbl-hint-empty");
        hboxCliente.getChildren().addAll(btnDatosCliente, lblClienteInfo);

        // ===== TROQUELADO =====
        Label lblTroquel = new Label("Troquelado (Agujero Lanyard):");
        lblTroquel.getStyleClass().add("lbl-section");
        cmbTroquel = new ComboBox<>();
        cmbTroquel.getItems().addAll(TipoTroquel.values());
        cmbTroquel.getSelectionModel().selectFirst();
        cmbTroquel.setPrefWidth(300);

        // ===== VINCULAR BASE DE DATOS =====
        chkVincularBD = new CheckBox("Vincular base de datos (opcional)");
        chkVincularBD.getStyleClass().add("lbl-section");

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

        Label lblBDInfo = new Label(
                "Al crear el proyecto, Studio hará una copia de este archivo en la carpeta interna.");
        lblBDInfo.getStyleClass().add("lbl-hint");

        // Habilitar/deshabilitar BD según checkbox
        chkVincularBD.selectedProperty().addListener((obs, old, newVal) -> {
            txtRutaBD.setDisable(!newVal);
            btnSeleccionarBD.setDisable(!newVal);
            if (!newVal)
                txtRutaBD.clear();
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
        grid.add(lblTroquel, 0, row, 2, 1);
        row++;
        grid.add(cmbTroquel, 0, row, 2, 1);

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

        // ===== VALIDACIÓN BÁSICA =====
        Node btnCrearNode = getDialogPane().lookupButton(btnCrear);
        btnCrearNode.setDisable(true);

        txtNombre.textProperty().addListener((obs, old, newVal) -> {
            actualizarBotonCrear(btnCrearNode);
            actualizarInfoUbicacion();
        });

        txtUbicacion.textProperty().addListener((obs, old, newVal) -> {
            actualizarBotonCrear(btnCrearNode);
            actualizarInfoUbicacion();
        });

        // ===== RESULTADO =====
        setResultConverter(buttonType -> {
            if (buttonType == btnCrear) {
                if (!validarFormulario())
                    return null;
                return crearMetadata();
            }
            return null;
        });

        actualizarInfoCliente();
        actualizarInfoUbicacion();
    }

    /* Abre el DirectoryChooser para seleccionar ubicación */
    private void seleccionarUbicacion() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Seleccionar ubicación del proyecto");

        File inicial = buscarDirectorioInicial();
        if (inicial != null && inicial.exists() && inicial.isDirectory()) {
            dirChooser.setInitialDirectory(inicial);
        }

        // Usar ownerWindow para que el selector aparezca centrado en la app
        Window dialogWindow = (ownerWindow != null) ? ownerWindow : getDialogPane().getScene().getWindow();
        File dir = dirChooser.showDialog(dialogWindow);
        if (dir != null) {
            txtUbicacion.setText(dir.getAbsolutePath());
        }
    }

    /* Intenta encontrar un directorio inicial razonable. */
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

    /* Abre el FileChooser para seleccionar base de datos */
    private void seleccionarBD() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Base de Datos");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("Access", "*.accdb", "*.mdb"),
                new FileChooser.ExtensionFilter("Todos", "*.*"));

        File inicial = buscarDirectorioInicial();
        if (inicial != null && inicial.exists() && inicial.isDirectory()) {
            fileChooser.setInitialDirectory(inicial);
        }

        Window dialogWindow = (ownerWindow != null) ? ownerWindow : getDialogPane().getScene().getWindow();
        File file = fileChooser.showOpenDialog(dialogWindow);
        if (file != null) {
            txtRutaBD.setText(file.getAbsolutePath());
        }
    }

    /* Abre el diálogo de datos del cliente */
    private void abrirDatosCliente() {
        if (clienteInfo == null)
            clienteInfo = new ClienteInfo();

        DatosClienteDialog dialog = new DatosClienteDialog(clienteInfo);
        Optional<ClienteInfo> result = dialog.showAndWait();

        if (result.isPresent()) {
            clienteInfo = result.get();
            if (clienteInfo == null)
                clienteInfo = new ClienteInfo();
            actualizarInfoCliente();
        }
    }

    /* Actualiza el label de información del cliente (estado visual) */
    private void actualizarInfoCliente() {
        if (clienteInfo != null && clienteInfo.tieneInformacion()) {
            String nombreEmpresa = safe(clienteInfo.getNombreEmpresa());
            String nombreContacto = safe(clienteInfo.getNombreContacto());

            StringBuilder info = new StringBuilder("✓ ");
            if (!nombreEmpresa.isEmpty()) {
                info.append(nombreEmpresa);
            } else if (!nombreContacto.isEmpty()) {
                info.append(nombreContacto);
            } else {
                info.append("Datos del cliente añadidos");
            }

            lblClienteInfo.setText(info.toString());
            lblClienteInfo.getStyleClass().removeAll("lbl-hint-empty", "lbl-hint");
            lblClienteInfo.getStyleClass().add("lbl-hint-ok");
        } else {
            lblClienteInfo.setText("(Sin información del cliente)");
            lblClienteInfo.getStyleClass().removeAll("lbl-hint-ok", "lbl-hint");
            lblClienteInfo.getStyleClass().add("lbl-hint-empty");
        }
    }

    /* Actualiza el estado del botón Crear */
    private void actualizarBotonCrear(Node btnCrear) {
        boolean nombreValido = txtNombre.getText() != null && !txtNombre.getText().trim().isEmpty();
        boolean ubicacionValida = txtUbicacion.getText() != null && !txtUbicacion.getText().trim().isEmpty();
        btnCrear.setDisable(!(nombreValido && ubicacionValida));
    }

    /* Actualiza el label de información de ubicación */
    private void actualizarInfoUbicacion() {
        String nombre = txtNombre.getText() != null ? txtNombre.getText().trim() : "";
        String ubicacion = txtUbicacion.getText() != null ? txtUbicacion.getText().trim() : "";

        String nombreCarpeta = !nombre.isEmpty()
                ? "TPS_" + normalizarNombre(nombre)
                : "TPS_NombreDelProyecto";

        if (!ubicacion.isEmpty()) {
            lblUbicacionInfo.setText("Se creará la carpeta: "
                    + ubicacion + File.separator + nombreCarpeta + File.separator);
        } else {
            lblUbicacionInfo.setText("Se creará la carpeta: " + nombreCarpeta + "/");
        }
    }

    private String normalizarNombre(String nombre) {
        if (nombre == null)
            return "";
        return nombre
                .replaceAll("[^a-zA-Z0-9_\\-\\s]", "_")
                .replaceAll("\\s+", "_")
                .trim();
    }

    /* Validación del formulario con mensajes claros */
    private boolean validarFormulario() {
        String nombre = txtNombre.getText() != null ? txtNombre.getText().trim() : "";
        String ubicacion = txtUbicacion.getText() != null ? txtUbicacion.getText().trim() : "";

        if (nombre.isEmpty()) {
            mostrarError("Falta el nombre del proyecto", "Escribe un nombre para el proyecto.");
            return false;
        }

        if (ubicacion.isEmpty()) {
            mostrarError("Falta la ubicación", "Selecciona una carpeta donde se creará el proyecto.");
            return false;
        }

        File carpetaBase = new File(ubicacion);
        if (!carpetaBase.exists() || !carpetaBase.isDirectory()) {
            mostrarError("Ubicación inválida", "La ubicación seleccionada no es una carpeta válida.");
            return false;
        }

        if (chkVincularBD.isSelected()) {
            String rutaBD = txtRutaBD.getText() != null ? txtRutaBD.getText().trim() : "";
            if (!rutaBD.isEmpty()) {
                File f = new File(rutaBD);
                if (!f.exists() || !f.isFile()) {
                    mostrarError("Base de datos inválida", "El archivo seleccionado no existe o no es válido.");
                    return false;
                }
            }
        }

        return true;
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Nuevo Proyecto");
        alert.setHeaderText(titulo);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /* Crea el objeto ProyectoMetadata con los datos del formulario */
    private ProyectoMetadata crearMetadata() {
        ProyectoMetadata metadata = new ProyectoMetadata();

        metadata.setNombre(txtNombre.getText().trim());
        metadata.setUbicacion(txtUbicacion.getText().trim());
        metadata.setClienteInfo(clienteInfo != null ? clienteInfo : new ClienteInfo());

        if (chkVincularBD.isSelected()) {
            String ruta = txtRutaBD.getText() != null ? txtRutaBD.getText().trim() : "";
            if (!ruta.isEmpty()) {
                metadata.setRutaBBDD(ruta);
            }
        }

        return metadata;
    }

    public TipoTroquel getTipoTroquelSeleccionado() {
        return cmbTroquel.getValue() != null ? cmbTroquel.getValue() : TipoTroquel.NINGUNO;
    }
}
