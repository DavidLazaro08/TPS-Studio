package com.tpsstudio.view.dialogs;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Diálogo principal de exportación.
 * Permite elegir uno o varios modos: Mail-Merge, Muestra de Diseño A4 o PDF
 * para Imprenta.
 */
public class ExportDialog extends Dialog<ExportDialog.ExportConfig> {

    public record ExportConfig(
            // Opciones Mail-Merge
            boolean exportarRegistros,
            String rangoFilas,
            boolean imprimirDorso,
            boolean recortarSangre,
            // Muestra de diseño A4
            PruebaConfigDialog.PruebaConfig configPrueba, // null = no generar
            // PDF para imprenta
            boolean exportarImprenta) {
    }

    private static final String CSS = ExportDialog.class
            .getResource("/css/dialogs.css").toExternalForm();

    // ──────────────────────────────────────────────────────────────────────────
    // Estado interno de la prueba (configurada mediante PruebaConfigDialog)
    private PruebaConfigDialog.PruebaConfig configPrueba = null;

    public ExportDialog(Window owner, int totalRegistrosBD, String nombreProyecto) {
        initOwner(owner);
        setTitle("Exportar");
        setHeaderText("¿Qué deseas generar?");

        getDialogPane().getStylesheets().add(CSS);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        ((Button) getDialogPane().lookupButton(ButtonType.OK)).setText("Generar");

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setPrefWidth(470);

        // ── MODO 1: Mail-Merge ────────────────────────────────────────────────
        CheckBox chkMailMerge = new CheckBox("Exportar registros (PDF Mail-Merge)");
        chkMailMerge.getStyleClass().add("lbl-section");
        chkMailMerge.setSelected(true);

        Label lblBDInfo = new Label("BD: " + totalRegistrosBD + " registros cargados");
        lblBDInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        Button btnConfigurarMailMerge = new Button("⚙ Configurar...");

        final String[] rangoFilasVal = { "TODOS" };
        final boolean[] imprimirDorsoVal = { true };
        final boolean[] sinSangreVal = { false };

        btnConfigurarMailMerge.setOnAction(e -> {
            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.initOwner(owner);
            dlg.setTitle("Configurar PDF Mail-Merge");
            dlg.setHeaderText("Opciones para la exportación de registros");
            dlg.getDialogPane().getStylesheets().add(CSS);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
            ((Button) dlg.getDialogPane().lookupButton(ButtonType.OK)).setText("Aceptar");

            TextField txtMMRango = new TextField("TODOS".equalsIgnoreCase(rangoFilasVal[0]) ? "" : rangoFilasVal[0]);
            txtMMRango.setPromptText("TODOS");
            txtMMRango.setPrefWidth(300);

            Label lblRangoHint = new Label("Ej: TODOS. O por comas: 1, 3. O por guiones: 1-5");
            lblRangoHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");

            VBox boxRango = new VBox(4, new Label("Registros a exportar:"), txtMMRango, lblRangoHint);

            ToggleGroup tgComp = new ToggleGroup();
            RadioButton rbMMAnverso = new RadioButton("Solo Anverso");
            RadioButton rbMMAnversoReverso = new RadioButton("Anverso + Reverso");
            rbMMAnverso.setToggleGroup(tgComp);
            rbMMAnversoReverso.setToggleGroup(tgComp);
            if (imprimirDorsoVal[0])
                rbMMAnversoReverso.setSelected(true);
            else
                rbMMAnverso.setSelected(true);

            ToggleGroup tgSang = new ToggleGroup();
            RadioButton rbMMConSangre = new RadioButton("Con sangre (imprenta)");
            RadioButton rbMMSinSangre = new RadioButton("Sin sangre (CR80 final)");
            rbMMConSangre.setToggleGroup(tgSang);
            rbMMSinSangre.setToggleGroup(tgSang);
            if (sinSangreVal[0])
                rbMMSinSangre.setSelected(true);
            else
                rbMMConSangre.setSelected(true);

            HBox hbComp = new HBox(20, rbMMAnverso, rbMMAnversoReverso);
            HBox hbSang = new HBox(20, rbMMConSangre, rbMMSinSangre);

            VBox dlgBox = new VBox(20,
                    boxRango,
                    new VBox(8, new Label("Composición:"), hbComp),
                    new VBox(8, new Label("Tolerancia (sangre):"), hbSang));
            dlgBox.setPadding(new Insets(20));
            dlg.getDialogPane().setContent(dlgBox);

            dlg.showAndWait().ifPresent(res -> {
                if (res == ButtonType.OK) {
                    rangoFilasVal[0] = txtMMRango.getText().trim().isEmpty() ? "TODOS" : txtMMRango.getText().trim();
                    imprimirDorsoVal[0] = rbMMAnversoReverso.isSelected();
                    sinSangreVal[0] = rbMMSinSangre.isSelected();
                    btnConfigurarMailMerge.setText("⚙ Configurado ✔");
                }
            });
        });

        VBox boxMailMerge = new VBox(6, lblBDInfo, btnConfigurarMailMerge);
        boxMailMerge.setPadding(new Insets(0, 0, 0, 18));

        // Habilitar/deshabilitar sub-opciones
        chkMailMerge.selectedProperty().addListener((o, old, val) -> btnConfigurarMailMerge.setDisable(!val));

        // ── MODO 2: Muestra de Diseño A4 ────────────────────────────────────
        CheckBox chkPrueba = new CheckBox("Muestra de Diseño A4 (para aprobación del cliente)");
        chkPrueba.getStyleClass().add("lbl-section");

        Label lblPruebaHint = new Label("Genera un PDF A4 listo para enviar al cliente con el diseño y área de firma.");
        lblPruebaHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");
        lblPruebaHint.setWrapText(true);

        Button btnConfigurarPrueba = new Button("⚙ Configurar...");
        btnConfigurarPrueba.setDisable(true);
        btnConfigurarPrueba.setOnAction(e -> {
            PruebaConfigDialog cfd = new PruebaConfigDialog(owner, nombreProyecto);
            cfd.showAndWait().ifPresent(c -> {
                configPrueba = c;
                btnConfigurarPrueba.setText("⚙ Configurado ✔");
            });
        });

        chkPrueba.selectedProperty().addListener((o, old, val) -> {
            btnConfigurarPrueba.setDisable(!val);
            if (!val) {
                configPrueba = null;
                btnConfigurarPrueba.setText("⚙ Configurar...");
            }
        });

        VBox boxPrueba = new VBox(6, lblPruebaHint, btnConfigurarPrueba);
        boxPrueba.setPadding(new Insets(0, 0, 0, 18));

        // ── MODO 3: PDF para Imprenta (solo fondos) ──────────────────────────
        CheckBox chkImprenta = new CheckBox("PDF para Imprenta (solo fondos, máxima calidad)");
        chkImprenta.getStyleClass().add("lbl-section");

        Label lblImprentaHint = new Label(
                "Genera un PDF de 2 páginas (Frente/Dorso) con solo el fondo, sin textos ni imágenes variables. "
                        + "Ideal para enviar a imprenta antes de tener los datos.");
        lblImprentaHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");
        lblImprentaHint.setWrapText(true);

        VBox boxImprenta = new VBox(6, lblImprentaHint);
        boxImprenta.setPadding(new Insets(0, 0, 0, 18));

        // Requerir al menos un modo seleccionado
        Button btnGenerar = (Button) getDialogPane().lookupButton(ButtonType.OK);
        Runnable checkAtLeastOne = () -> btnGenerar
                .setDisable(!chkMailMerge.isSelected() && !chkPrueba.isSelected() && !chkImprenta.isSelected());
        chkMailMerge.selectedProperty().addListener((o, old, val) -> checkAtLeastOne.run());
        chkPrueba.selectedProperty().addListener((o, old, val) -> checkAtLeastOne.run());
        chkImprenta.selectedProperty().addListener((o, old, val) -> checkAtLeastOne.run());

        root.getChildren().addAll(
                chkMailMerge, boxMailMerge,
                new Separator(),
                chkPrueba, boxPrueba,
                new Separator(),
                chkImprenta, boxImprenta);

        getDialogPane().setContent(root);

        setResultConverter(btn -> {
            if (btn != ButtonType.OK)
                return null;

            // Si activaron prueba pero no configuraron, usar configuración por defecto
            PruebaConfigDialog.PruebaConfig cfgPrueba = null;
            if (chkPrueba.isSelected()) {
                cfgPrueba = (configPrueba != null) ? configPrueba
                        : new PruebaConfigDialog.PruebaConfig("TPS Studio", true, true, true, "");
            }

            return new ExportConfig(
                    chkMailMerge.isSelected(),
                    rangoFilasVal[0].isEmpty() ? "TODOS" : rangoFilasVal[0],
                    imprimirDorsoVal[0],
                    sinSangreVal[0],
                    cfgPrueba,
                    chkImprenta.isSelected());
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper para parsear rango de filas

    public static List<Integer> parseRangoFilas(String rangoStr, int totalRegistros) {
        List<Integer> filas = new ArrayList<>();
        rangoStr = rangoStr.toUpperCase().replaceAll("\\s+", "");

        if (rangoStr.isEmpty() || rangoStr.equals("TODOS") || rangoStr.equals("ALL")) {
            for (int i = 0; i < totalRegistros; i++)
                filas.add(i);
            return filas;
        }

        for (String part : rangoStr.split(",")) {
            if (part.contains("-")) {
                String[] bounds = part.split("-");
                if (bounds.length != 2)
                    throw new IllegalArgumentException("Rango inválido: " + part);
                int start = Integer.parseInt(bounds[0]);
                int end = Integer.parseInt(bounds[1]);
                if (start > end) {
                    int t = start;
                    start = end;
                    end = t;
                }
                for (int i = start; i <= end; i++) {
                    int z = i - 1;
                    if (z >= 0 && z < totalRegistros && !filas.contains(z))
                        filas.add(z);
                }
            } else {
                try {
                    int z = Integer.parseInt(part) - 1;
                    if (z >= 0 && z < totalRegistros && !filas.contains(z))
                        filas.add(z);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Número inválido: " + part);
                }
            }
        }
        return filas;
    }
}
