package com.tpsstudio.view.managers.design;

import com.tpsstudio.model.enums.TipoCodigo;
import com.tpsstudio.model.elements.FormaElemento;
import com.tpsstudio.util.AnimationHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;

/**
 * Gestor especializado para el Panel de Herramientas (Toolbox).
 */
public class ToolboxManager {

    private boolean barcodesExpanded = false;
    private boolean shapesExpanded = false;
    private Button btnValidar;

    // Callbacks
    private final Runnable onAddText;
    private final Runnable onAddImage;
    private final Runnable onAddBackground;
    private final Consumer<TipoCodigo> onAddCode;
    private final Consumer<FormaElemento.TipoForma> onAddShape;
    private final Runnable onValidateDesign;

    public ToolboxManager(Runnable onAddText, 
                          Runnable onAddImage, 
                          Runnable onAddBackground,
                          Consumer<TipoCodigo> onAddCode,
                          Consumer<FormaElemento.TipoForma> onAddShape,
                          Runnable onValidateDesign) {
        this.onAddText = onAddText;
        this.onAddImage = onAddImage;
        this.onAddBackground = onAddBackground;
        this.onAddCode = onAddCode;
        this.onAddShape = onAddShape;
        this.onValidateDesign = onValidateDesign;
    }

    public Button getBtnValidar() {
        return btnValidar;
    }

    public VBox buildToolboxPanel() {
        VBox toolbox = new VBox(4);
        toolbox.setPadding(new Insets(14, 12, 14, 12));
        toolbox.getStyleClass().add("tools-panel");

        VBox header = new VBox(2);
        header.setPadding(new Insets(0, 0, 8, 0));
        Label lblToolbox = new Label("Herramientas");
        lblToolbox.getStyleClass().add("panel-title");
        Label lblSubtitulo = new Label("Seleccione un elemento para añadir al lienzo");
        lblSubtitulo.getStyleClass().add("panel-placeholder");
        header.getChildren().addAll(lblToolbox, lblSubtitulo);

        // ---- Texto ----
        Button btnTexto = makeToolButton("T", "tool-icon", "Texto", "tool-label", "tool-button");
        btnTexto.setOnAction(e -> { if (onAddText != null) onAddText.run(); });

        // ---- Imagen ----
        Button btnImagen = makeToolButton("▣", "tool-icon", "Imagen", "tool-label", "tool-button");
        btnImagen.setOnAction(e -> { if (onAddImage != null) onAddImage.run(); });

        // ---- Fondo ----
        Button btnFondo = makeToolButton("⬚", "tool-icon", "Fondo", "tool-label", "tool-button");
        btnFondo.setOnAction(e -> { if (onAddBackground != null) onAddBackground.run(); });

        // ---- Acordeón de Códigos (QR + Barras) ----
        VBox codesContainer = new VBox(0);
        VBox codesSubMenu = new VBox(1);
        codesSubMenu.getStyleClass().add("tool-subtools");
        codesSubMenu.setVisible(barcodesExpanded);
        codesSubMenu.setManaged(barcodesExpanded);

        Label iconExpanderC = new Label(barcodesExpanded ? "\u25BE" : "\u25B8");
        iconExpanderC.getStyleClass().add("tool-icon");
        iconExpanderC.setMinWidth(16);

        Label iconCodes = new Label("⦀");
        iconCodes.getStyleClass().add("tool-icon");
        Label textCodes = new Label("Códigos");
        textCodes.getStyleClass().add("tool-label");

        Region spacerC = new Region();
        HBox.setHgrow(spacerC, Priority.ALWAYS);

        HBox codesGraphic = new HBox(iconCodes, textCodes, spacerC, iconExpanderC);
        codesGraphic.setAlignment(Pos.CENTER_LEFT);
        codesGraphic.setMaxWidth(Double.MAX_VALUE);
        codesGraphic.setSpacing(0);

        Button btnToggleCodes = new Button();
        btnToggleCodes.setGraphic(codesGraphic);
        btnToggleCodes.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        btnToggleCodes.setMaxWidth(Double.MAX_VALUE);
        btnToggleCodes.getStyleClass().add("tool-button");
        
        btnToggleCodes.setOnAction(e -> {
            barcodesExpanded = !barcodesExpanded;
            iconExpanderC.setText(barcodesExpanded ? "\u25BE" : "\u25B8");
            AnimationHelper.animateAccordion(codesSubMenu, barcodesExpanded);
        });

        Button btnQR = makeSubToolButton("⦀", "Código QR");
        btnQR.setOnAction(e -> { if (onAddCode != null) onAddCode.accept(TipoCodigo.QR); });
        codesSubMenu.getChildren().add(btnQR);

        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setMinHeight(1);
        separator.setMaxHeight(1);
        separator.setStyle("-fx-background-color: #ffffff22;");
        VBox.setMargin(separator, new Insets(4, 15, 4, 15));
        codesSubMenu.getChildren().add(separator);

        for (TipoCodigo tipo : TipoCodigo.values()) {
            if (tipo == TipoCodigo.QR) continue; 
            Button btnSub = makeSubToolButton("‖", tipo.getNombre());
            btnSub.setOnAction(e -> { if (onAddCode != null) onAddCode.accept(tipo); });
            codesSubMenu.getChildren().add(btnSub);
        }

        codesContainer.getChildren().addAll(btnToggleCodes, codesSubMenu);

        // ---- Subherramientas de forma ----
        VBox shapesSubMenu = new VBox(1);
        shapesSubMenu.getStyleClass().add("tool-subtools");
        shapesSubMenu.setVisible(shapesExpanded);
        shapesSubMenu.setManaged(shapesExpanded);

        Button btnRectangulo = makeSubToolButton("▭", "Rectángulo");
        btnRectangulo.setOnAction(e -> { if (onAddShape != null) onAddShape.accept(FormaElemento.TipoForma.RECTANGULO); });
        Button btnElipse = makeSubToolButton("◯", "Elipse");
        btnElipse.setOnAction(e -> { if (onAddShape != null) onAddShape.accept(FormaElemento.TipoForma.ELIPSE); });
        Button btnLinea = makeSubToolButton("―", "Línea");
        btnLinea.setOnAction(e -> { if (onAddShape != null) onAddShape.accept(FormaElemento.TipoForma.LINEA); });

        shapesSubMenu.getChildren().addAll(btnRectangulo, btnElipse, btnLinea);

        // ---- Dibujar Forma (acordeón) ----
        Label iconExpander = new Label(shapesExpanded ? "\u25BE" : "\u25B8");
        iconExpander.getStyleClass().add("tool-icon");
        iconExpander.setMinWidth(16);

        Label iconFormas = new Label("⬒");
        iconFormas.getStyleClass().add("tool-icon");
        Label textFormas = new Label("Dibujar Forma");
        textFormas.getStyleClass().add("tool-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox formasGraphic = new HBox(iconFormas, textFormas, spacer, iconExpander);
        formasGraphic.setAlignment(Pos.CENTER_LEFT);
        formasGraphic.setMaxWidth(Double.MAX_VALUE);

        Button btnToggleFormas = new Button();
        btnToggleFormas.setGraphic(formasGraphic);
        btnToggleFormas.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        btnToggleFormas.setMaxWidth(Double.MAX_VALUE);
        btnToggleFormas.getStyleClass().add("tool-button");
        btnToggleFormas.setOnAction(e -> {
            shapesExpanded = !shapesExpanded;
            iconExpander.setText(shapesExpanded ? "\u25BE" : "\u25B8");
            AnimationHelper.animateAccordion(shapesSubMenu, shapesExpanded);
        });

        // ---- Validar Diseño ----
        btnValidar = makeToolButton("✓", "tool-icon", "Validar Diseño", "tool-label", "validate-button");
        btnValidar.setOnAction(e -> { if (onValidateDesign != null) onValidateDesign.run(); });

        toolbox.getChildren().addAll(
                header, btnTexto, btnImagen, btnFondo,
                codesContainer, btnToggleFormas, shapesSubMenu,
                new Separator(), btnValidar
        );
        return toolbox;
    }

    private Button makeToolButton(String iconText, String iconStyle,
                                   String labelText, String labelStyle,
                                   String buttonStyle) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add(iconStyle);
        Label label = new Label(labelText);
        label.getStyleClass().add(labelStyle);

        HBox graphic = new HBox(icon, label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setMaxWidth(Double.MAX_VALUE);

        Button btn = new Button();
        btn.setGraphic(graphic);
        btn.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add(buttonStyle);
        return btn;
    }

    private Button makeSubToolButton(String iconText, String labelText) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add("tool-icon");
        Label label = new Label(labelText);
        label.getStyleClass().add("tool-label");

        HBox graphic = new HBox(icon, label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setMaxWidth(Double.MAX_VALUE);

        Button btn = new Button();
        btn.setGraphic(graphic);
        btn.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("tool-subbutton");
        return btn;
    }
}
