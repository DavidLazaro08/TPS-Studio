package com.tpsstudio.view.managers.props;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.FormaElemento;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class ShapePropertyHandler extends BasePropertyHandler {

    public ShapePropertyHandler(Canvas canvas, EditorCanvasManager canvasManager, 
                                Runnable onCanvasRedraw, Runnable onPropertyChanged) {
        super(canvas, canvasManager, onCanvasRedraw, onPropertyChanged);
    }

    @Override
    public void buildPanel(VBox container, Elemento elemento) {
        if (!(elemento instanceof FormaElemento forma)) return;

        // 1. Etiqueta
        addEtiquetaControl(container, forma, "Ej: RECUADRO, MARCO...");

        // 2. Posición y Tamaño
        addPositionSizeControls(container, forma);
        container.getChildren().add(new Separator());

        // 3. Estilo de Forma (Borde)
        Label lblEstilo = new Label("Estilo de Forma");
        lblEstilo.getStyleClass().add("prop-label");

        CheckBox chkBorde = new CheckBox("Borde activo");
        chkBorde.setSelected(forma.isConBorde());
        chkBorde.selectedProperty().addListener((obs, o, n) -> {
            forma.setConBorde(n); if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        addColorControlWithEyedropper(container, "Color del Borde:", forma.getColorBorde(), chkBorde.selectedProperty(), hex -> {
            forma.setColorBorde(hex); if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        Label lblGrosor = new Label("Grosor del Borde:");
        Spinner<Double> spnGrosor = new Spinner<>(0.5, 20.0, forma.getGrosorBorde(), 0.5);
        spnGrosor.setEditable(true);
        spnGrosor.setMaxWidth(100);
        spnGrosor.disableProperty().bind(chkBorde.selectedProperty().not());
        spnGrosor.valueProperty().addListener((obs, o, n) -> {
            forma.setGrosorBorde(n); if (onCanvasRedraw != null) onCanvasRedraw.run();
        });

        container.getChildren().addAll(lblEstilo, chkBorde, lblGrosor, spnGrosor, new Separator());

        // 4. Relleno (Excepto para líneas)
        if (forma.getTipoForma() != FormaElemento.TipoForma.LINEA) {
            CheckBox chkRelleno = new CheckBox("Relleno activo");
            chkRelleno.setSelected(forma.isConRelleno());
            chkRelleno.selectedProperty().addListener((obs, o, n) -> {
                forma.setConRelleno(n); if (onCanvasRedraw != null) onCanvasRedraw.run();
            });

            addColorControlWithEyedropper(container, "Color de Relleno:", forma.getColorRelleno(), chkRelleno.selectedProperty(), hex -> {
                forma.setColorRelleno(hex); if (onCanvasRedraw != null) onCanvasRedraw.run();
            });
            container.getChildren().addAll(chkRelleno, new Separator());
        }

        // 5. Opacidad y Radio
        Label lblOp = new Label("Opacidad:");
        Slider sldOp = new Slider(0, 1, forma.getOpacidad());
        sldOp.valueProperty().addListener((obs, o, n) -> {
            forma.setOpacidad(n.doubleValue()); if (onCanvasRedraw != null) onCanvasRedraw.run();
        });
        container.getChildren().addAll(lblOp, sldOp);

        if (forma.getTipoForma() == FormaElemento.TipoForma.RECTANGULO) {
            Label lblRadio = new Label("Redondeado de Esquinas:");
            Slider sldRadio = new Slider(0, 100, forma.getRadioCurvatura());
            sldRadio.valueProperty().addListener((obs, o, n) -> {
                forma.setRadioCurvatura(n.doubleValue()); if (onCanvasRedraw != null) onCanvasRedraw.run();
            });
            container.getChildren().addAll(new Separator(), lblRadio, sldRadio);
        }
    }
}
