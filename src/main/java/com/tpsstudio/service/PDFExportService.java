package com.tpsstudio.service;

import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.project.ClienteInfo;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.view.dialogs.ExportDialog;
import com.tpsstudio.view.dialogs.PruebaConfigDialog;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio que genera PDFs de exportación Mail-Merge y pruebas de diseño A4.
 * Utiliza Canvas JavaFX off-screen + PDFBox para máxima calidad.
 */
public class PDFExportService {

    // Calidad de exportación: 400 DPI para uso en imprenta profesional
    private static final double EXPORT_DPI = 400.0;

    // Escala derivada automáticamente: píxeles necesarios por unidad de canvas
    // CARD_WIDTH (px en editor) = CR80_WIDTH_MM * 4.0 = 342.4px
    // EXPORT_SCALE = (CR80_WIDTH_MM/25.4 * EXPORT_DPI) / CARD_WIDTH
    private static final double EXPORT_SCALE =
            (EditorCanvasManager.CR80_WIDTH_MM / 25.4 * EXPORT_DPI) / EditorCanvasManager.CARD_WIDTH;

    // DPI del documento de prueba A4 (menor que exportación, suficiente para PDF de aprobación)
    private static final double PRUEBA_DPI = 200.0;

    // Imagen de silueta de placeholder (cargada una sola vez desde recursos)
    private static final Image SILUETA_IMG;
    static {
        Image tmp = null;
        try (var stream = PDFExportService.class.getResourceAsStream("/img/silueta.png")) {
            if (stream != null) tmp = new Image(stream);
        } catch (Exception ignored) {}
        SILUETA_IMG = tmp;
    }

    private final Proyecto proyecto;
    private final FuenteDatos fuenteDatos;

    public PDFExportService(Proyecto proyecto, FuenteDatos fuenteDatos) {
        this.proyecto = proyecto;
        this.fuenteDatos = fuenteDatos;
    }

    /**
     * Genera el PDF y lo guarda en la ruta indicada.
     *
     * @param config         Configuración elegida por el usuario en ExportDialog.
     * @param filaSeleccionadas Lista de índices 0-based de registros a exportar.
     * @param destino        Archivo .pdf de destino.
     * @throws Exception si algo falla en el proceso.
     */
    public void exportar(ExportDialog.ExportConfig config, List<Integer> filasSeleccionadas, File destino) throws Exception {
        // Construir la lista completa de "páginas" a exportar en orden
        List<EntradaPagina> paginas = new ArrayList<>();

        boolean tieneFrente = !proyecto.getElementosFrente().isEmpty() || proyecto.getFondoFrente() != null;
        boolean tieneDorso  = !proyecto.getElementosDorso().isEmpty() || proyecto.getFondoDorso() != null;

        for (int filaIdx : filasSeleccionadas) {
            // Siempre exportamos el frente
            paginas.add(new EntradaPagina(filaIdx, true));
            // Si es Anverso + Reverso Y el proyecto tiene dorso, añadir el dorso
            if (config.imprimirDorso() && tieneDorso) {
                paginas.add(new EntradaPagina(filaIdx, false));
            }
        }

        try (PDDocument pdf = new PDDocument()) {
            for (EntradaPagina entrada : paginas) {
                // Navegar la fuente de datos a la fila correspondiente
                if (fuenteDatos != null) {
                    fuenteDatos.irA(entrada.filaIdx);
                }

                // Renderizar canvas invisible en alta resolución
                BufferedImage imagen = renderizarTarjeta(entrada.esFrente, config.recortarSangre());

                // Crear página PDF con exactamente las dimensiones físicas CR80 (en puntos PDF 72pt/inch)
                float anchoPoints = (float) imagen.getWidth() * 72f / (float) EXPORT_DPI;
                float altoPoints  = (float) imagen.getHeight() * 72f / (float) EXPORT_DPI;
                PDRectangle mediaBox = new PDRectangle(anchoPoints, altoPoints);
                PDPage page = new PDPage(mediaBox);
                pdf.addPage(page);

                PDImageXObject pdImage = LosslessFactory.createFromImage(pdf, imagen);

                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    cs.drawImage(pdImage, 0, 0, anchoPoints, altoPoints);
                }
            }

            pdf.save(destino);
        }
    }

    /**
     * Genera un PDF para imprenta (2 páginas: Frente + Dorso) solo con fondos.
     * Sin textos, sin imágenes variables, sin guías. Máxima calidad (400dpi).
     */
    public void exportarImprenta(File destino) throws Exception {
        boolean hayDorso = proyecto.getFondoDorso() != null;

        try (PDDocument pdf = new PDDocument()) {
            // Página 1: Frente
            BufferedImage frente = renderizarSoloFondo(true);
            float anchoPoints = (float) frente.getWidth() * 72f / (float) EXPORT_DPI;
            float altoPoints  = (float) frente.getHeight() * 72f / (float) EXPORT_DPI;
            PDPage p1 = new PDPage(new PDRectangle(anchoPoints, altoPoints));
            pdf.addPage(p1);
            try (PDPageContentStream cs = new PDPageContentStream(pdf, p1)) {
                cs.drawImage(LosslessFactory.createFromImage(pdf, frente), 0, 0, anchoPoints, altoPoints);
            }

            // Página 2: Dorso (si existe)
            if (hayDorso) {
                BufferedImage dorso = renderizarSoloFondo(false);
                float a2 = (float) dorso.getWidth() * 72f / (float) EXPORT_DPI;
                float b2 = (float) dorso.getHeight() * 72f / (float) EXPORT_DPI;
                PDPage p2 = new PDPage(new PDRectangle(a2, b2));
                pdf.addPage(p2);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, p2)) {
                    cs.drawImage(LosslessFactory.createFromImage(pdf, dorso), 0, 0, a2, b2);
                }
            }

            pdf.save(destino);
        }
    }

    /**
     * Renderiza únicamente el fondo de una cara (sin ningún elemento de diseño).
     * Resultado: canvas limpio con fondo o blanco puro. Sin guías.
     */
    private BufferedImage renderizarSoloFondo(boolean esFrente) throws Exception {
        double cardW = EditorCanvasManager.CARD_WIDTH  * EXPORT_SCALE;
        double cardH = EditorCanvasManager.CARD_HEIGHT * EXPORT_SCALE;
        double bleed = EditorCanvasManager.BLEED_MARGIN * EXPORT_SCALE;
        double canvasW = cardW + bleed * 2;
        double canvasH = cardH + bleed * 2;

        ImagenFondoElemento fondo = esFrente ? proyecto.getFondoFrente() : proyecto.getFondoDorso();

        AtomicReference<BufferedImage> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        final double fCardX = bleed;
        final double fCardY = bleed;

        Platform.runLater(() -> {
            try {
                Canvas c = new Canvas(canvasW, canvasH);
                GraphicsContext gc = c.getGraphicsContext2D();
                gc.setFill(Color.WHITE);
                gc.fillRect(0, 0, canvasW, canvasH);

                if (fondo != null && fondo.getImagen() != null) {
                    gc.drawImage(fondo.getImagen(),
                            fCardX + fondo.getX() * EXPORT_SCALE,
                            fCardY + fondo.getY() * EXPORT_SCALE,
                            fondo.getWidth() * EXPORT_SCALE,
                            fondo.getHeight() * EXPORT_SCALE);
                }

                WritableImage snap = c.snapshot(null, null);
                resultRef.set(SwingFXUtils.fromFXImage(snap, null));
            } catch (Exception e) {
                errorRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        if (errorRef.get() != null) throw errorRef.get();
        return resultRef.get();
    }

    /**
     * Renderiza la cara indicada del proyecto en un BufferedImage de alta resolución.
     * Las guías/handles de selección NUNCA aparecen en el renderizado de exportación.
     * 
     * IMPORTANTE: Canvas.snapshot() solo puede ejecutarse en el hilo JavaFX.
     * Usamos CountDownLatch para sincronizar el hilo de fondo con el hilo FX.
     */
    private BufferedImage renderizarTarjeta(boolean esFrente, boolean recortarSangre) throws Exception {

        // Dimensiones del canvas virtual a 3× escala (para conseguir ~300dpi)
        double cardW = EditorCanvasManager.CARD_WIDTH  * EXPORT_SCALE;
        double cardH = EditorCanvasManager.CARD_HEIGHT * EXPORT_SCALE;
        double bleed = EditorCanvasManager.BLEED_MARGIN * EXPORT_SCALE;

        double canvasW = cardW + bleed * 2;
        double canvasH = cardH + bleed * 2;

        // Referencias compartidas entre hilos
        AtomicReference<BufferedImage> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        final double fCardX = bleed;
        final double fCardY = bleed;
        final double fCardW = cardW;
        final double fCardH = cardH;
        final List<Elemento> elementos = esFrente
                ? proyecto.getElementosFrente()
                : proyecto.getElementosDorso();
        final ImagenFondoElemento fondo = esFrente
                ? proyecto.getFondoFrente()
                : proyecto.getFondoDorso();

        // El snapshot DEBE ejecutarse en el hilo FX
        Platform.runLater(() -> {
            try {
                Canvas offCanvas = new Canvas(canvasW, canvasH);
                GraphicsContext gc = offCanvas.getGraphicsContext2D();
                gc.clearRect(0, 0, canvasW, canvasH);

                // Fondo
                if (fondo != null && fondo.getImagen() != null) {
                    double fx = fCardX + (fondo.getX() * EXPORT_SCALE);
                    double fy = fCardY + (fondo.getY() * EXPORT_SCALE);
                    double fw = fondo.getWidth()  * EXPORT_SCALE;
                    double fh = fondo.getHeight() * EXPORT_SCALE;
                    gc.drawImage(fondo.getImagen(), fx, fy, fw, fh);
                } else {
                    gc.setFill(Color.WHITE);
                    gc.fillRect(fCardX, fCardY, fCardW, fCardH);
                }

                // Elementos
                if (elementos != null) {
                    for (Elemento elem : elementos) {
                        if (!elem.isVisible()) continue;
                        dibujarElemento(gc, elem, fCardX, fCardY, EXPORT_SCALE);
                    }
                }

                // Snapshot
                WritableImage snapshot = offCanvas.snapshot(null, null);
                BufferedImage buffered = SwingFXUtils.fromFXImage(snapshot, null);

                // Recorte de sangre si aplica
                if (recortarSangre) {
                    int px = (int) Math.round(bleed);
                    int py = (int) Math.round(bleed);
                    int pw = (int) Math.round(fCardW);
                    int ph = (int) Math.round(fCardH);
                    buffered = buffered.getSubimage(px, py,
                            Math.min(pw, buffered.getWidth() - px),
                            Math.min(ph, buffered.getHeight() - py));
                }

                resultRef.set(buffered);
            } catch (Exception e) {
                errorRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        // Esperar a que el hilo FX haya terminado el snapshot
        latch.await();

        if (errorRef.get() != null) throw errorRef.get();
        return resultRef.get();
    }

    /**
     * Dibuja un único elemento — versión limpia para exportación:
     * sin handles, sin bordes de selección, sin guías.
     */
    private void dibujarElemento(GraphicsContext gc, Elemento elem, double cardX, double cardY, double scale) {
        double ex = cardX + (elem.getX() * scale);
        double ey = cardY + (elem.getY() * scale);
        double ew = elem.getWidth()  * scale;
        double eh = elem.getHeight() * scale;

        if (elem instanceof TextoElemento texto) {
            gc.setFill(Color.web(texto.getColor()));

            var weight = texto.isNegrita()
                    ? javafx.scene.text.FontWeight.BOLD
                    : javafx.scene.text.FontWeight.NORMAL;
            var posture = texto.isCursiva()
                    ? javafx.scene.text.FontPosture.ITALIC
                    : javafx.scene.text.FontPosture.REGULAR;

            gc.setFont(Font.font(texto.getFontFamily(), weight, posture, texto.getFontSize() * scale));

            // Resolver content (variable o fijo)
            String contenido = texto.getContenido();
            if (texto.getColumnaVinculada() != null && fuenteDatos != null) {
                String val = fuenteDatos.getValor(texto.getColumnaVinculada());
                if (val != null && !val.isEmpty()) contenido = val;
            }

            // Multi-línea con auto-wrap (mismo algoritmo que EditorCanvasManager)
            List<String> lines = computeLines(contenido, texto.isSaltoLinea(), gc.getFont(), ew);

            double lineH = texto.getFontSize() * scale * 1.2;
            double curY  = ey + (texto.getFontSize() * scale);

            for (String line : lines) {
                double textX = ex;
                javafx.scene.text.Text tmpText = new javafx.scene.text.Text(line);
                tmpText.setFont(gc.getFont());
                double tw = tmpText.getLayoutBounds().getWidth();

                if ("CENTER".equals(texto.getAlineacion())) {
                    textX = ex + (ew - tw) / 2;
                } else if ("RIGHT".equals(texto.getAlineacion())) {
                    textX = ex + ew - tw;
                }

                gc.fillText(line, textX, curY);
                curY += lineH;
            }

        } else if (elem instanceof ImagenElemento imgElem) {
            Image img = imgElem.getImagen();

            // Resolver imagen variable
            if (imgElem.getColumnaVinculada() != null && fuenteDatos != null) {
                String nombreArchivo = fuenteDatos.getValor(imgElem.getColumnaVinculada());
                Image imgVariable = resolverImagenVariable(nombreArchivo);
                if (imgVariable != null) img = imgVariable;
            }

            if (img != null) {
                gc.setGlobalAlpha(imgElem.getOpacity());
                gc.drawImage(img, ex, ey, ew, eh);
                gc.setGlobalAlpha(1.0);
            } else if (SILUETA_IMG != null) {
                // Sin imagen real: mostrar silueta de placeholder con opacidad completa
                gc.drawImage(SILUETA_IMG, ex, ey, ew, eh);
            } else {
                // Fallback gris si tampoco hay silueta en recursos
                gc.setFill(Color.web("#cccccc"));
                gc.fillRect(ex, ey, ew, eh);
            }
        } else if (elem instanceof FormaElemento forma) {
            double grosor = Math.max(1.0, forma.getGrosorBorde() * scale);
            gc.setLineWidth(grosor);

            switch (forma.getTipoForma()) {
                case RECTANGULO -> {
                    if (forma.isConRelleno()) {
                        gc.setFill(Color.web(forma.getColorRelleno()));
                        gc.fillRect(ex, ey, ew, eh);
                    }
                    gc.setStroke(Color.web(forma.getColorBorde()));
                    gc.strokeRect(ex, ey, ew, eh);
                }
                case ELIPSE -> {
                    if (forma.isConRelleno()) {
                        gc.setFill(Color.web(forma.getColorRelleno()));
                        gc.fillOval(ex, ey, ew, eh);
                    }
                    gc.setStroke(Color.web(forma.getColorBorde()));
                    gc.strokeOval(ex, ey, ew, eh);
                }
                case LINEA -> {
                    gc.setStroke(Color.web(forma.getColorBorde()));
                    // Centrada verticalmente en el área ocupada por el elemento
                    gc.strokeLine(ex, ey + eh / 2, ex + ew, ey + eh / 2);
                }
            }
        }
    }

    /** Misma lógica de word-wrap que EditorCanvasManager para coherencia visual */
    private List<String> computeLines(String contenido, boolean saltoLinea, Font font, double maxWidth) {
        List<String> rawLines = java.util.Arrays.asList(contenido.split("\n", -1));
        List<String> finalLines = new ArrayList<>();

        if (saltoLinea) {
            javafx.scene.text.Text helper = new javafx.scene.text.Text();
            helper.setFont(font);
            for (String raw : rawLines) {
                if (raw.isEmpty()) { finalLines.add(""); continue; }
                String[] words = raw.split(" ", -1);
                StringBuilder current = new StringBuilder();
                for (String word : words) {
                    String test = current.length() == 0 ? word : current + " " + word;
                    helper.setText(test);
                    if (helper.getLayoutBounds().getWidth() > maxWidth) {
                        if (current.length() > 0) { finalLines.add(current.toString()); current = new StringBuilder(); }
                        helper.setText(word);
                        if (helper.getLayoutBounds().getWidth() > maxWidth) {
                            StringBuilder partial = new StringBuilder();
                            for (char c : word.toCharArray()) {
                                helper.setText(partial.toString() + c);
                                if (helper.getLayoutBounds().getWidth() > maxWidth && partial.length() > 0) {
                                    finalLines.add(partial.toString()); partial = new StringBuilder().append(c);
                                } else { partial.append(c); }
                            }
                            current = partial;
                        } else { current = new StringBuilder(word); }
                    } else { current = new StringBuilder(test); }
                }
                if (current.length() > 0) finalLines.add(current.toString());
            }
        } else {
            finalLines.addAll(rawLines);
        }
        return finalLines;
    }

    /** Intenta cargar una imagen desde la columna de datos (nombre de archivo) */
    private Image resolverImagenVariable(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.isBlank()) return null;
        if (proyecto.getMetadata() == null) return null;

        String fotosDir = proyecto.getMetadata().getRutaFotos();
        if (fotosDir == null) return null;

        File imgFile = Paths.get(fotosDir, nombreArchivo).toFile();
        if (!imgFile.exists()) return null;

        try {
            return new Image(imgFile.toURI().toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Genera un PDF de prueba A4 para aprobación del cliente.
    /**
     * Genera el PDF de Muestra de Diseño A4 con la configuración elegida por el usuario.
     * La firma/aprobación siempre aparece al pie del documento.
     */
    public void generarPruebaA4(PruebaConfigDialog.PruebaConfig cfg, File destino) throws Exception {
        float A4_W_PT = (float) (210.0 / 25.4 * 72.0);
        float A4_H_PT = (float) (297.0 / 25.4 * 72.0);
        int A4_W_PX = (int) Math.round(210.0 / 25.4 * PRUEBA_DPI);
        int A4_H_PX = (int) Math.round(297.0 / 25.4 * PRUEBA_DPI);

        double CARD_SCALE_PRUEBA = (PRUEBA_DPI * EditorCanvasManager.CR80_WIDTH_MM / 25.4) / EditorCanvasManager.CARD_WIDTH;
        double cardW_px  = EditorCanvasManager.CARD_WIDTH  * CARD_SCALE_PRUEBA;
        double cardH_px  = EditorCanvasManager.CARD_HEIGHT * CARD_SCALE_PRUEBA;
        double bleed_px  = EditorCanvasManager.BLEED_MARGIN * CARD_SCALE_PRUEBA;

        final String nombreProyecto = proyecto.getNombre();
        final String nombreCliente  = obtenerNombreCliente();
        final List<String> varCols  = obtenerColumnasVariables();
        final String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        final String estudio = (cfg.nombreEstudio() == null || cfg.nombreEstudio().isBlank())
                ? "TPS Studio" : cfg.nombreEstudio();

        AtomicReference<BufferedImage> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                Canvas c = new Canvas(A4_W_PX, A4_H_PX);
                GraphicsContext gc = c.getGraphicsContext2D();
                gc.setFill(Color.WHITE);
                gc.fillRect(0, 0, A4_W_PX, A4_H_PX);

                double margin = A4_W_PX * 0.045;

                boolean hayDorso = !proyecto.getElementosDorso().isEmpty() || proyecto.getFondoDorso() != null;

                if (cfg.formatoA4Completo()) {
                    // ── CABECERA ────────────────────────────────────────────
                    double headerH = A4_H_PX * 0.075;
                    gc.setFill(Color.web("#1a1a2e"));
                    gc.fillRect(0, 0, A4_W_PX, headerH);

                    gc.setFill(Color.WHITE);
                    gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD,
                            javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.022));
                    gc.fillText(estudio + "  —  Muestra de Diseño", margin, headerH * 0.67);

                    gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.NORMAL,
                            javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.015));
                    String dateText = "Fecha: " + fecha;
                    javafx.scene.text.Text tmp = new javafx.scene.text.Text(dateText);
                    tmp.setFont(gc.getFont());
                    gc.fillText(dateText, A4_W_PX - margin - tmp.getLayoutBounds().getWidth() - 5, headerH * 0.67);

                    double yPos = headerH + margin * 0.8;

                    // Proyecto y cliente
                    gc.setFill(Color.web("#22223b"));
                    gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD,
                            javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.022));
                    gc.fillText("Proyecto:  " + nombreProyecto, margin, yPos);
                    yPos += A4_W_PX * 0.028;

                    if (!nombreCliente.isBlank()) {
                        gc.setFill(Color.web("#555555"));
                        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.NORMAL,
                                javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.017));
                        gc.fillText("Cliente:  " + nombreCliente, margin, yPos);
                        yPos += A4_W_PX * 0.028;
                    }

                    // Separador
                    gc.setStroke(Color.web("#cccccc"));
                    gc.setLineWidth(1.2);
                    gc.strokeLine(margin, yPos, A4_W_PX - margin, yPos);
                    yPos += margin * 0.7;

                    // ── ÁREA DE APROBACIÓN (reservada al final de la página) ──
                    // Calculamos cuánto espacio necesita el bloque inferior para fijarlo al pie
                    double pieH  = A4_H_PX * 0.005;  // tira de pie
                    double firmaH = cfg.incluirAprobacion() ? A4_H_PX * 0.115 : 0;
                    double fraseH = (!cfg.frasePersonalizada().isBlank()) ? A4_H_PX * 0.035 : 0;
                    double aprobYStart = A4_H_PX - pieH - firmaH - fraseH - margin * 0.5;

                    // ── DISEÑOS (zona central) ──────────────────────────────
                    double totalCardsW = hayDorso
                            ? (cardW_px + bleed_px * 2) * 2 + margin
                            : (cardW_px + bleed_px * 2);
                    double cardsStartX = (A4_W_PX - totalCardsW) / 2;

                    // Frente
                    renderTarjetaEnCanvas(gc, proyecto.getElementosFrente(), proyecto.getFondoFrente(),
                            cardsStartX, yPos, CARD_SCALE_PRUEBA, true);
                    double labelY = yPos + cardH_px + bleed_px * 2 + A4_W_PX * 0.015;

                    gc.setFill(Color.web("#333333"));
                    gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD,
                            javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.015));
                    double centroF = cardsStartX + (cardW_px + bleed_px * 2) / 2;
                    gc.fillText("ANVERSO", centroF - 25, labelY);

                    if (hayDorso) {
                        double dorsoX = cardsStartX + cardW_px + bleed_px * 2 + margin;
                        renderTarjetaEnCanvas(gc, proyecto.getElementosDorso(), proyecto.getFondoDorso(),
                                dorsoX, yPos, CARD_SCALE_PRUEBA, true);
                        double centroD = dorsoX + (cardW_px + bleed_px * 2) / 2;
                        gc.fillText("REVERSO", centroD - 25, labelY);
                    }
                    yPos = labelY + A4_W_PX * 0.030;

                    // ── CAMPOS VARIABLES (si los hay y están activados) ──────
                    if (cfg.incluirCamposVariables() && !varCols.isEmpty()) {
                        gc.setStroke(Color.web("#e0e0e0"));
                        gc.setLineWidth(1);
                        gc.strokeLine(margin, yPos, A4_W_PX - margin, yPos);
                        yPos += margin * 0.5;

                        gc.setFill(Color.web("#22223b"));
                        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD,
                                javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.016));
                        gc.fillText("Campos variables del diseño:", margin, yPos);
                        yPos += A4_W_PX * 0.020;

                        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.NORMAL,
                                javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.013));
                        int ncols = 2;
                        double colW = (A4_W_PX - margin * 2) / ncols;
                        for (int i = 0; i < varCols.size(); i++) {
                            double cx = margin + (i % ncols) * colW;
                            double cy = yPos + (i / ncols) * (A4_W_PX * 0.028);
                            gc.setFill(Color.web("#4a4a8a"));
                            gc.fillText("• " + varCols.get(i) + ":", cx, cy);
                            gc.setStroke(Color.web("#bbbbbb"));
                            gc.setLineWidth(0.7);
                            gc.strokeLine(cx + colW * 0.28, cy + 2, cx + colW * 0.85, cy + 2);
                        }
                    }

                    // ── FRASE PERSONALIZADA (antes de firma) ─────────────────
                    if (!cfg.frasePersonalizada().isBlank()) {
                        double fraseY = aprobYStart - fraseH + A4_W_PX * 0.020;
                        gc.setFill(Color.web("#888888"));
                        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.NORMAL,
                                javafx.scene.text.FontPosture.ITALIC, A4_W_PX * 0.013));
                        gc.fillText("* " + cfg.frasePersonalizada(), margin, fraseY);
                    }

                    // ── ÁREA DE APROBACIÓN (FIJA AL PIE) ────────────────────
                    if (cfg.incluirAprobacion()) {
                        gc.setStroke(Color.web("#cccccc"));
                        gc.setLineWidth(1.2);
                        gc.strokeLine(margin, aprobYStart, A4_W_PX - margin, aprobYStart);

                        double ay = aprobYStart + margin * 0.6;
                        gc.setFill(Color.web("#22223b"));
                        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD,
                                javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.015));
                        gc.fillText("Conformidad del cliente:", margin, ay);
                        ay += A4_W_PX * 0.022;

                        double boxSz = A4_W_PX * 0.022;
                        gc.setStroke(Color.web("#333333"));
                        gc.setLineWidth(1.2);
                        gc.strokeRect(margin, ay - boxSz * 0.8, boxSz, boxSz);
                        gc.setFill(Color.web("#2e7d32"));
                        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD,
                                javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.015));
                        gc.fillText("APROBADO", margin + boxSz + 10, ay);

                        double colDos = A4_W_PX * 0.38;
                        gc.setStroke(Color.web("#333333"));
                        gc.strokeRect(colDos, ay - boxSz * 0.8, boxSz, boxSz);
                        gc.setFill(Color.web("#c62828"));
                        gc.fillText("CON CORRECCIONES", colDos + boxSz + 10, ay);
                        ay += A4_W_PX * 0.042;

                        // Línea de firma
                        gc.setStroke(Color.web("#555555"));
                        gc.setLineWidth(0.9);
                        gc.strokeLine(margin, ay, margin + A4_W_PX * 0.32, ay);
                        gc.setFill(Color.web("#666666"));
                        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.NORMAL,
                                javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.012));
                        gc.fillText("Firma del cliente", margin, ay + A4_W_PX * 0.018);

                        double fechaCol = margin + A4_W_PX * 0.40;
                        gc.strokeLine(fechaCol, ay, fechaCol + A4_W_PX * 0.16, ay);
                        gc.fillText("Fecha", fechaCol, ay + A4_W_PX * 0.018);
                    }

                    // ── PIE DE PÁGINA ────────────────────────────────────────
                    gc.setFill(Color.web("#aaaaaa"));
                    gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.NORMAL,
                            javafx.scene.text.FontPosture.ITALIC, A4_W_PX * 0.011));
                    gc.fillText(estudio + "  ·  Muestra de diseño: " + nombreProyecto + "  ·  " + fecha,
                            margin, A4_H_PX - margin * 0.5);

                } else {
                    // ── MODO "Solo diseños" ──────────────────────────────────
                    boolean hd = !proyecto.getElementosDorso().isEmpty() || proyecto.getFondoDorso() != null;
                    double totalW = hd ? (cardW_px + bleed_px * 2) * 2 + margin : (cardW_px + bleed_px * 2);
                    double startX = (A4_W_PX - totalW) / 2;
                    double startY = (A4_H_PX - cardH_px - bleed_px * 2) / 2 - (hd ? 0 : 0);

                    renderTarjetaEnCanvas(gc, proyecto.getElementosFrente(), proyecto.getFondoFrente(),
                            startX, startY, CARD_SCALE_PRUEBA, true);
                    gc.setFill(Color.web("#444444"));
                    gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD,
                            javafx.scene.text.FontPosture.REGULAR, A4_W_PX * 0.016));
                    gc.fillText("ANVERSO",
                            startX + (cardW_px + bleed_px * 2) / 2 - 30,
                            startY + cardH_px + bleed_px * 2 + A4_W_PX * 0.02);

                    if (hd) {
                        double dx = startX + cardW_px + bleed_px * 2 + margin;
                        renderTarjetaEnCanvas(gc, proyecto.getElementosDorso(), proyecto.getFondoDorso(),
                                dx, startY, CARD_SCALE_PRUEBA, true);
                        gc.fillText("REVERSO",
                                dx + (cardW_px + bleed_px * 2) / 2 - 30,
                                startY + cardH_px + bleed_px * 2 + A4_W_PX * 0.02);
                    }
                }

                WritableImage snap = c.snapshot(null, null);
                resultRef.set(SwingFXUtils.fromFXImage(snap, null));
            } catch (Exception e) {
                errorRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        if (errorRef.get() != null) throw errorRef.get();

        BufferedImage a4img = resultRef.get();
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(A4_W_PT, A4_H_PT));
            pdf.addPage(page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(pdf, a4img);
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                cs.drawImage(pdImage, 0, 0, A4_W_PT, A4_H_PT);
            }
            pdf.save(destino);
        }
    }



    /** Renderiza los elementos de una cara en un GC ya existente, en modo prueba (sin datos variables). */
    private void renderTarjetaEnCanvas(GraphicsContext gc, List<? extends Elemento> elementos,
                                       ImagenFondoElemento fondo, double startX, double startY,
                                       double scale, boolean modoVista) {
        double cardW = EditorCanvasManager.CARD_WIDTH  * scale;
        double cardH = EditorCanvasManager.CARD_HEIGHT * scale;
        double bleed = EditorCanvasManager.BLEED_MARGIN * scale;
        double cardX = startX + bleed;
        double cardY = startY + bleed;

        // Fondo
        if (fondo != null && fondo.getImagen() != null) {
            gc.drawImage(fondo.getImagen(), cardX + fondo.getX() * scale, cardY + fondo.getY() * scale,
                    fondo.getWidth() * scale, fondo.getHeight() * scale);
        } else {
            gc.setFill(Color.WHITE);
            gc.fillRect(cardX, cardY, cardW, cardH);
        }

        // Guía de sangrado (rojo punteado exterior)
        gc.setStroke(Color.web("#d48a8a"));
        gc.setLineWidth(1.2);
        gc.setLineDashes(5, 5);
        gc.strokeRect(startX, startY, cardW + bleed * 2, cardH + bleed * 2);

        // Borde tarjeta
        gc.setStroke(Color.web("#888888"));
        gc.setLineWidth(1);
        gc.setLineDashes();
        gc.strokeRect(cardX, cardY, cardW, cardH);

        // Guía de seguridad (verde punteado interior)
        double safety = EditorCanvasManager.SAFETY_MARGIN * scale;
        gc.setStroke(Color.web("#4a9b7c"));
        gc.setLineWidth(0.8);
        gc.setLineDashes(3, 3);
        gc.strokeRect(cardX + safety, cardY + safety, cardW - safety * 2, cardH - safety * 2);
        gc.setLineDashes();

        // Elementos (modo prueba: variables → mostrar nombre de columna entre corchetes)
        if (elementos != null) {
            for (Elemento elem : elementos) {
                if (!elem.isVisible()) continue;
                if (elem instanceof TextoElemento texto) {
                    String contenido;
                    if (texto.getColumnaVinculada() != null) {
                        contenido = "[" + texto.getColumnaVinculada() + "]";
                    } else {
                        contenido = texto.getContenido();
                    }
                    gc.setFill(Color.web(texto.getColor()));
                    gc.setFont(Font.font(texto.getFontFamily(),
                            texto.isNegrita()  ? javafx.scene.text.FontWeight.BOLD  : javafx.scene.text.FontWeight.NORMAL,
                            texto.isCursiva()  ? javafx.scene.text.FontPosture.ITALIC : javafx.scene.text.FontPosture.REGULAR,
                            texto.getFontSize() * scale));
                    gc.fillText(contenido, cardX + elem.getX() * scale,
                            cardY + elem.getY() * scale + texto.getFontSize() * scale);
                } else if (elem instanceof ImagenElemento imgElem) {
                    Image img = imgElem.getImagen();
                    if (img == null && SILUETA_IMG != null) img = SILUETA_IMG;
                    if (img != null) {
                        gc.setGlobalAlpha(imgElem.getOpacity());
                        gc.drawImage(img, cardX + elem.getX() * scale, cardY + elem.getY() * scale,
                                elem.getWidth() * scale, elem.getHeight() * scale);
                        gc.setGlobalAlpha(1.0);
                    }
                }
            }
        }
    }

    /** Devuelve el nombre del cliente o empresa del proyecto */
    private String obtenerNombreCliente() {
        if (proyecto.getMetadata() == null) return "";
        ClienteInfo ci = proyecto.getMetadata().getClienteInfo();
        if (ci == null) return "";
        String empresa = ci.getNombreEmpresa() != null ? ci.getNombreEmpresa().trim() : "";
        String contacto = ci.getNombreContacto() != null ? ci.getNombreContacto().trim() : "";
        if (!empresa.isEmpty()) return empresa;
        return contacto;
    }

    /** Devuelve la lista de nombres de columnas vinculadas en el diseño */
    private List<String> obtenerColumnasVariables() {
        List<String> cols = new ArrayList<>();
        for (Elemento el : proyecto.getElementosFrente()) {
            if (el instanceof TextoElemento t && t.getColumnaVinculada() != null
                    && !cols.contains(t.getColumnaVinculada())) cols.add(t.getColumnaVinculada());
            if (el instanceof ImagenElemento i && i.getColumnaVinculada() != null
                    && !cols.contains(i.getColumnaVinculada())) cols.add(i.getColumnaVinculada());
        }
        for (Elemento el : proyecto.getElementosDorso()) {
            if (el instanceof TextoElemento t && t.getColumnaVinculada() != null
                    && !cols.contains(t.getColumnaVinculada())) cols.add(t.getColumnaVinculada());
            if (el instanceof ImagenElemento i && i.getColumnaVinculada() != null
                    && !cols.contains(i.getColumnaVinculada())) cols.add(i.getColumnaVinculada());
        }
        return cols;
    }

    /** Clase auxiliar para la lista ordenada de páginas a generar */
    private record EntradaPagina(int filaIdx, boolean esFrente) {}
}
