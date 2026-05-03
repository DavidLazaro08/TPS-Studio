package com.tpsstudio.view.dialogs;

import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.service.TrabajoImpresion;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Diálogo de configuración de impresión — Fase 1.
 *
 * <p>Independiente de {@link ExportDialog}: se centra en las opciones relevantes
 * para imprimir físicamente tarjetas, sin las secciones de prueba A4 ni PDF
 * de imprenta que no aplican aquí.</p>
 *
 * <p>Devuelve un {@link TrabajoImpresion} listo para pasar a {@code ImpresionService}.
 * Los índices de registro son siempre 0-based internamente; la interfaz muestra
 * posiciones 1-based al usuario.</p>
 */
public class ImpresionDialog extends Dialog<TrabajoImpresion> {

    private static final String CSS = ImpresionDialog.class
            .getResource("/css/dialogs.css").toExternalForm();

    public ImpresionDialog(Window owner, Proyecto proyecto, FuenteDatos fuenteDatos) {
        initOwner(owner);
        setTitle("Imprimir tarjeta");
        setHeaderText("Configura las opciones de impresión");

        getDialogPane().getStylesheets().add(CSS);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        ((Button) getDialogPane().lookupButton(ButtonType.OK)).setText("Imprimir");

        boolean tieneDorso = proyecto != null
                && (!proyecto.getElementosDorso().isEmpty() || proyecto.getFondoDorso() != null);

        int registroActual = (fuenteDatos != null) ? fuenteDatos.getIndiceActual() : 0;
        int totalRegistros  = (fuenteDatos != null) ? fuenteDatos.getTotalRegistros() : 1;

        // ── CARA ───────────────────────────────────────────────────────────────
        Label lblCara = new Label("Cara a imprimir");
        lblCara.getStyleClass().add("lbl-section");

        ToggleGroup tgCara = new ToggleGroup();
        RadioButton rbFrente = new RadioButton("Solo Frente");
        RadioButton rbAmbas  = new RadioButton("Frente y Dorso");
        rbFrente.setToggleGroup(tgCara);
        rbAmbas.setToggleGroup(tgCara);
        rbFrente.setSelected(true);

        if (!tieneDorso) {
            rbAmbas.setDisable(true);
            Tooltip.install(rbAmbas, new Tooltip("Este proyecto no tiene diseño de dorso."));
        }

        HBox hbCara = new HBox(20, rbFrente, rbAmbas);
        hbCara.setPadding(new Insets(0, 0, 0, 12));

        // ── REGISTROS ──────────────────────────────────────────────────────────
        Label lblRegistros = new Label("Registros");
        lblRegistros.getStyleClass().add("lbl-section");

        ToggleGroup tgReg = new ToggleGroup();

        String textoActual = (fuenteDatos != null)
                ? String.format("Registro actual  (%d / %d)", registroActual + 1, totalRegistros)
                : "Registro actual (diseño estático)";

        RadioButton rbActual = new RadioButton(textoActual);
        RadioButton rbRango  = new RadioButton("Rango:  ");
        rbActual.setToggleGroup(tgReg);
        rbRango.setToggleGroup(tgReg);
        rbActual.setSelected(true);

        TextField txtRango = new TextField();
        txtRango.setPromptText("Ej: TODOS  |  1-5  |  2,4,7");
        txtRango.setPrefWidth(200);
        txtRango.setDisable(true);

        Label lblRangoHint = new Label("TODOS, rangos (1-5) o valores separados por coma (2,4,7)");
        lblRangoHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

        HBox hbRango = new HBox(4, rbRango, txtRango);
        hbRango.setAlignment(Pos.CENTER_LEFT);
        hbRango.setPadding(new Insets(0, 0, 0, 12));

        rbActual.setPadding(new Insets(0, 0, 0, 12));
        rbRango.selectedProperty().addListener((o, old, val) -> txtRango.setDisable(!val));

        if (fuenteDatos == null || !fuenteDatos.tieneRegistros()) {
            rbRango.setDisable(true);
            Tooltip.install(rbRango, new Tooltip("Vincula una base de datos al proyecto para usar rangos."));
        }

        VBox boxRegistros = new VBox(6, rbActual, hbRango, lblRangoHint);

        // ── SANGRE ─────────────────────────────────────────────────────────────
        Label lblSangre = new Label("Tolerancia de sangre");
        lblSangre.getStyleClass().add("lbl-section");

        ToggleGroup tgSangre = new ToggleGroup();
        RadioButton rbSinSangre = new RadioButton("Sin sangre — tamaño final CR80  (85,60 × 53,98 mm)");
        RadioButton rbConSangre = new RadioButton("Con sangre — incluye 2 mm por lado");
        rbSinSangre.setToggleGroup(tgSangre);
        rbConSangre.setToggleGroup(tgSangre);
        rbSinSangre.setSelected(true); // Predeterminado para impresión física directa

        VBox boxSangre = new VBox(6, rbSinSangre, rbConSangre);
        boxSangre.setPadding(new Insets(0, 0, 0, 12));

        // ── MODO DE SALIDA (informativo) ───────────────────────────────────────
        Label lblModo = new Label(
                "Modo: Vista previa PDF + Imprimir  —  el visor del sistema mostrará el diálogo de impresión.");
        lblModo.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");
        lblModo.setWrapText(true);

        // ── ENSAMBLADO ─────────────────────────────────────────────────────────
        VBox root = new VBox(14,
                lblCara, hbCara,
                new Separator(),
                lblRegistros, boxRegistros,
                new Separator(),
                lblSangre, boxSangre,
                new Separator(),
                lblModo
        );
        root.setPadding(new Insets(20));
        root.setPrefWidth(480);
        getDialogPane().setContent(root);

        // ── CONVERSIÓN ─────────────────────────────────────────────────────────
        setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;

            boolean frente = true; // Fase 1: siempre frente
            boolean dorso  = rbAmbas.isSelected() && tieneDorso;
            boolean soloActual = rbActual.isSelected();
            String rango = txtRango.getText().trim().isEmpty() ? "TODOS" : txtRango.getText().trim();
            boolean sinSangre = rbSinSangre.isSelected();

            return new TrabajoImpresion(frente, dorso, soloActual, rango, sinSangre, registroActual);
        });
    }
}
