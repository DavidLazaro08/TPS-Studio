package com.tpsstudio.view.dialogs;

import com.tpsstudio.model.project.ClienteInfo;
import com.tpsstudio.model.project.ProyectoMetadata;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

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

    // Para poder actualizar el label “Se creará la carpeta...”
    private Label lblUbicacionInfo;

    public NuevoProyectoDialog() {
        setTitle("Nuevo Proyecto TPS");
        setHeaderText("Crear nuevo proyecto de diseño de tarjetas");

        // Inicializar cliente info vacío (siempre no-null)
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

        lblUbicacionInfo = new Label("Se creará la carpeta: TPS_NombreDelProyecto/");
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
            if (!newVal) txtRutaBD.clear();
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

        // ===== VALIDACIÓN BÁSICA (habilitar/deshabilitar) =====
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

        // ===== CONVERTIR RESULTADO =====
        setResultConverter(buttonType -> {
            if (buttonType == btnCrear) {
                if (!validarFormulario()) return null;
                return crearMetadata();
            }
            return null;
        });

        // Estado inicial label cliente + carpeta preview
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

        File dir = dirChooser.showDialog(getDialogPane().getScene().getWindow());
        if (dir != null) {
            txtUbicacion.setText(dir.getAbsolutePath());
        }
    }

    /* Intenta encontrar un directorio inicial razonable (Windows ES suele ser "Documentos"). */
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

    /* Abre el FileChooser para seleccionar base de datos */
    private void seleccionarBD() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Base de Datos");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("Access", "*.accdb", "*.mdb"),
                new FileChooser.ExtensionFilter("Todos", "*.*")
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

    /* Abre el diálogo de datos del cliente */
    private void abrirDatosCliente() {
        if (clienteInfo == null) clienteInfo = new ClienteInfo();

        DatosClienteDialog dialog = new DatosClienteDialog(clienteInfo);
        Optional<ClienteInfo> result = dialog.showAndWait();

        if (result.isPresent()) {
            clienteInfo = result.get();
            if (clienteInfo == null) clienteInfo = new ClienteInfo();
            actualizarInfoCliente();
        }
    }

    /* Actualiza el label de información del cliente */
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
            lblClienteInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: green; -fx-font-style: normal;");
        } else {
            lblClienteInfo.setText("(Sin información del cliente)");
            lblClienteInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: gray; -fx-font-style: italic;");
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

        String nombreCarpeta;
        if (!nombre.isEmpty()) {
            // OJO: aquí queremos ver el comportamiento tipo TPS_____ si hay símbolos raros
            nombreCarpeta = "TPS_" + normalizarNombre(nombre);
        } else {
            nombreCarpeta = "TPS_NombreDelProyecto";
        }

        if (!ubicacion.isEmpty()) {
            lblUbicacionInfo.setText("Se creará la carpeta: "
                    + ubicacion + File.separator + nombreCarpeta + File.separator);
        } else {
            lblUbicacionInfo.setText("Se creará la carpeta: " + nombreCarpeta + "/");
        }
    }

    /*
     * Normaliza el nombre del proyecto para usarlo como nombre de carpeta.
     * Versión "clásica": si el nombre son símbolos, se convierte en ____ y listo.
     * (Sin fallback a "Proyecto")
     */
    private String normalizarNombre(String nombre) {
        if (nombre == null) return "";

        // Sustituye caracteres raros por "_" y espacios por "_"
        return nombre
                .replaceAll("[^a-zA-Z0-9_\\-\\s]", "_")
                .replaceAll("\\s+", "_")
                .trim();
    }

    /* Validación del formulario con mensajes claros. */
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

        // Validación BD si está activada
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

        // Guardamos el nombre tal cual para la UI
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
}
