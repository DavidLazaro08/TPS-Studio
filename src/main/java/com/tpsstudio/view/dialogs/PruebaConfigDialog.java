package com.tpsstudio.view.dialogs;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

/**
 * Diálogo de configuración para el PDF de Muestra de Diseño (A4).
 */
public class PruebaConfigDialog extends Dialog<PruebaConfigDialog.PruebaConfig> {

    public record PruebaConfig(
            String nombreEstudio,
            boolean formatoA4Completo,      // true = A4 con cabecera + firma; false = solo diseños
            boolean incluirAprobacion,       // área "Aprobado / Con correcciones"
            boolean incluirCamposVariables,  // tabla de campos del diseño
            String frasePersonalizada        // "" = no incluir
    ) {}

    private static final String CSS = PruebaConfigDialog.class
            .getResource("/css/dialogs.css").toExternalForm();

    public PruebaConfigDialog(Window owner, String nombreProyecto) {
        initOwner(owner);
        setTitle("Configurar Muestra de Diseño");
        setHeaderText("Personaliza el PDF de muestra para el cliente");

        getDialogPane().getStylesheets().add(CSS);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        ((Button) getDialogPane().lookupButton(ButtonType.OK)).setText("Aceptar");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(14);
        grid.setPadding(new Insets(20));
        grid.setPrefWidth(440);

        int row = 0;

        // 1. Nombre del estudio/empresa
        Label lblEstudio = new Label("Nombre del estudio / empresa:");
        lblEstudio.getStyleClass().add("lbl-section");
        TextField txtEstudio = new TextField("TPS Studio");
        txtEstudio.setPromptText("Nombre visible en la cabecera del PDF");
        txtEstudio.setPrefWidth(300);
        Label lblEstudioHint = new Label("Aparece como remitente en el documento.");
        lblEstudioHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");

        grid.add(lblEstudio, 0, row++);
        grid.add(txtEstudio, 0, row++);
        grid.add(lblEstudioHint, 0, row++);
        grid.add(new Separator(), 0, row++);

        // 2. Formato del documento
        Label lblFormato = new Label("Formato del documento:");
        lblFormato.getStyleClass().add("lbl-section");

        ToggleGroup tgFormato = new ToggleGroup();
        RadioButton rbA4Completo = new RadioButton("Documento A4 completo (cabecera + datos de proyecto + firma)");
        RadioButton rbSoloDiseños = new RadioButton("Solo los diseños (sin cabecera ni texto adicional)");
        rbA4Completo.setToggleGroup(tgFormato);
        rbSoloDiseños.setToggleGroup(tgFormato);
        rbA4Completo.setSelected(true);

        VBox boxFormato = new VBox(6, rbA4Completo, rbSoloDiseños);

        grid.add(lblFormato, 0, row++);
        grid.add(boxFormato, 0, row++);
        grid.add(new Separator(), 0, row++);

        // 3. Opciones adicionales (solo visibles en A4 completo)
        Label lblOpciones = new Label("Contenido del documento:");
        lblOpciones.getStyleClass().add("lbl-section");

        CheckBox chkAprobacion = new CheckBox("Incluir área de aprobación con firma del cliente");
        chkAprobacion.setSelected(true);

        CheckBox chkCampos = new CheckBox("Incluir tabla de campos variables del diseño");
        chkCampos.setSelected(true);

        CheckBox chkFrase = new CheckBox("Añadir frase personalizada:");
        TextField txtFrase = new TextField("Diseño sujeto a revisión de impresión.");
        txtFrase.setDisable(true);
        txtFrase.setPrefWidth(360);

        chkFrase.selectedProperty().addListener((obs, old, val) -> txtFrase.setDisable(!val));

        VBox boxOpciones = new VBox(7, chkAprobacion, chkCampos, chkFrase, txtFrase);

        // Desactivas las opciones de contenido si se elige "Solo diseños"
        rbSoloDiseños.selectedProperty().addListener((obs, old, val) -> {
            chkAprobacion.setDisable(val);
            chkCampos.setDisable(val);
            chkFrase.setDisable(val);
            txtFrase.setDisable(val || !chkFrase.isSelected());
        });

        grid.add(lblOpciones, 0, row++);
        grid.add(boxOpciones, 0, row++);

        getDialogPane().setContent(grid);

        setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String frase = (chkFrase.isSelected() && !txtFrase.getText().isBlank())
                    ? txtFrase.getText().trim() : "";
            return new PruebaConfig(
                    txtEstudio.getText().isBlank() ? "TPS Studio" : txtEstudio.getText().trim(),
                    rbA4Completo.isSelected(),
                    chkAprobacion.isSelected() && rbA4Completo.isSelected(),
                    chkCampos.isSelected() && rbA4Completo.isSelected(),
                    frase
            );
        });
    }
}
