package com.tpsstudio.view;

import com.tpsstudio.model.ClienteInfo;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;

/**
 * Diálogo para capturar información del cliente
 */
public class DatosClienteDialog extends Dialog<ClienteInfo> {

    private TextField txtNombreEmpresa;
    private TextField txtNombreContacto;
    private TextField txtEmail;
    private TextField txtTelefono;
    private TextArea txtObservaciones;

    private Label lblEmailError;
    private Label lblTelefonoError;

    public DatosClienteDialog() {
        this(null);
    }

    public DatosClienteDialog(ClienteInfo clienteExistente) {
        setTitle("Datos del Cliente");
        setHeaderText("Información del cliente que solicita el trabajo");

        // Crear grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        // Nombre de la empresa
        Label lblEmpresa = new Label("Nombre de la Empresa:");
        txtNombreEmpresa = new TextField();
        txtNombreEmpresa.setPromptText("Ej: Acme Corporation");
        txtNombreEmpresa.setPrefWidth(300);

        // Nombre del contacto
        Label lblContacto = new Label("Nombre del Contacto:");
        txtNombreContacto = new TextField();
        txtNombreContacto.setPromptText("Ej: Juan Pérez");

        // Email
        Label lblEmail = new Label("Email:");
        txtEmail = new TextField();
        txtEmail.setPromptText("Ej: contacto@empresa.com");
        lblEmailError = new Label();
        lblEmailError.setTextFill(Color.RED);
        lblEmailError.setStyle("-fx-font-size: 10px;");
        lblEmailError.setVisible(false);

        // Teléfono
        Label lblTelefono = new Label("Teléfono:");
        txtTelefono = new TextField();
        txtTelefono.setPromptText("Ej: +34 600 123 456");
        lblTelefonoError = new Label();
        lblTelefonoError.setTextFill(Color.RED);
        lblTelefonoError.setStyle("-fx-font-size: 10px;");
        lblTelefonoError.setVisible(false);

        // Observaciones
        Label lblObservaciones = new Label("Observaciones:");
        txtObservaciones = new TextArea();
        txtObservaciones.setPromptText("Notas adicionales sobre el proyecto...");
        txtObservaciones.setPrefRowCount(4);
        txtObservaciones.setWrapText(true);
        GridPane.setHgrow(txtObservaciones, Priority.ALWAYS);

        // Layout
        int row = 0;
        grid.add(lblEmpresa, 0, row);
        grid.add(txtNombreEmpresa, 1, row);

        row++;
        grid.add(lblContacto, 0, row);
        grid.add(txtNombreContacto, 1, row);

        row++;
        grid.add(lblEmail, 0, row);
        grid.add(txtEmail, 1, row);

        row++;
        grid.add(new Label(), 0, row); // Espacio vacío
        grid.add(lblEmailError, 1, row);

        row++;
        grid.add(lblTelefono, 0, row);
        grid.add(txtTelefono, 1, row);

        row++;
        grid.add(new Label(), 0, row); // Espacio vacío
        grid.add(lblTelefonoError, 1, row);

        row++;
        grid.add(lblObservaciones, 0, row);
        GridPane.setColumnSpan(lblObservaciones, 2);

        row++;
        grid.add(txtObservaciones, 0, row, 2, 1);

        getDialogPane().setContent(grid);

        // Botones
        ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(btnGuardar, btnCancelar);

        // Validación en tiempo real
        txtEmail.textProperty().addListener((obs, old, newVal) -> validarEmail());
        txtTelefono.textProperty().addListener((obs, old, newVal) -> validarTelefono());

        // Cargar datos existentes si los hay
        if (clienteExistente != null) {
            cargarDatos(clienteExistente);
        }

        // Convertir resultado
        setResultConverter(buttonType -> {
            if (buttonType == btnGuardar) {
                // Validar antes de guardar
                if (!validarEmail() || !validarTelefono()) {
                    return null;
                }
                return crearClienteInfo();
            }
            return null;
        });
    }

    /**
     * Valida el email y muestra error si es inválido
     */
    private boolean validarEmail() {
        String email = txtEmail.getText().trim();

        if (email.isEmpty()) {
            lblEmailError.setVisible(false);
            return true; // Email opcional
        }

        if (!ClienteInfo.validarEmail(email)) {
            lblEmailError.setText("⚠ Formato de email inválido");
            lblEmailError.setVisible(true);
            return false;
        }

        lblEmailError.setVisible(false);
        return true;
    }

    /**
     * Valida el teléfono y muestra error si es inválido
     */
    private boolean validarTelefono() {
        String telefono = txtTelefono.getText().trim();

        if (telefono.isEmpty()) {
            lblTelefonoError.setVisible(false);
            return true; // Teléfono opcional
        }

        if (!ClienteInfo.validarTelefono(telefono)) {
            lblTelefonoError.setText("⚠ Formato de teléfono inválido (9-20 dígitos)");
            lblTelefonoError.setVisible(true);
            return false;
        }

        lblTelefonoError.setVisible(false);
        return true;
    }

    /**
     * Carga datos de un ClienteInfo existente
     */
    private void cargarDatos(ClienteInfo cliente) {
        txtNombreEmpresa.setText(cliente.getNombreEmpresa());
        txtNombreContacto.setText(cliente.getNombreContacto());
        txtEmail.setText(cliente.getEmail());
        txtTelefono.setText(cliente.getTelefono());
        txtObservaciones.setText(cliente.getObservaciones());
    }

    /**
     * Crea un objeto ClienteInfo con los datos del formulario
     */
    private ClienteInfo crearClienteInfo() {
        ClienteInfo cliente = new ClienteInfo();
        cliente.setNombreEmpresa(txtNombreEmpresa.getText().trim());
        cliente.setNombreContacto(txtNombreContacto.getText().trim());
        cliente.setEmail(txtEmail.getText().trim());
        cliente.setTelefono(txtTelefono.getText().trim());
        cliente.setObservaciones(txtObservaciones.getText().trim());
        return cliente;
    }
}
