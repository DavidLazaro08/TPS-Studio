package com.tpsstudio.util;

import javafx.scene.text.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades para procesamiento de texto.
 */
public class TextUtils {

    /**
     * Calcula las líneas de texto tras aplicar word-wrap (salto de línea) si corresponde.
     * Mantiene consistencia visual entre EditorCanvasManager y PDFExportService.
     *
     * @param contenido Texto a procesar
     * @param saltoLinea Si debe aplicar word-wrap o solo respetar saltos manuales
     * @param font Fuente a aplicar para medir dimensiones
     * @param maxWidth Ancho máximo permitido por línea (usado si saltoLinea es true)
     * @return Lista de líneas resultantes
     */
    public static List<String> computeLines(String contenido, boolean saltoLinea, Font font, double maxWidth) {
        List<String> rawLines = java.util.Arrays.asList(contenido.split("\n", -1));
        List<String> finalLines = new ArrayList<>();

        if (saltoLinea) {
            javafx.scene.text.Text helper = new javafx.scene.text.Text();
            helper.setFont(font);
            for (String raw : rawLines) {
                if (raw.isEmpty()) { 
                    finalLines.add(""); 
                    continue; 
                }
                String[] words = raw.split(" ", -1);
                StringBuilder currentLine = new StringBuilder();
                
                for (String word : words) {
                    String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
                    helper.setText(testLine);
                    
                    if (helper.getLayoutBounds().getWidth() > maxWidth) {
                        // Si ya había algo en la línea, lo guardamos y bajamos
                        if (currentLine.length() > 0) { 
                            finalLines.add(currentLine.toString()); 
                            currentLine = new StringBuilder(); 
                        }
                        
                        // Evaluamos si la palabra por sí sola supera el ancho
                        helper.setText(word);
                        if (helper.getLayoutBounds().getWidth() > maxWidth) {
                            // Palabra mega-larga (ej: textooooooooooooo)
                            // La partimos letra a letra forzosamente
                            StringBuilder partialWord = new StringBuilder();
                            for (char c : word.toCharArray()) {
                                helper.setText(partialWord.toString() + c);
                                if (helper.getLayoutBounds().getWidth() > maxWidth && partialWord.length() > 0) {
                                    finalLines.add(partialWord.toString()); 
                                    partialWord = new StringBuilder().append(c);
                                } else { 
                                    partialWord.append(c); 
                                }
                            }
                            currentLine = partialWord;
                        } else { 
                            currentLine = new StringBuilder(word); 
                        }
                    } else { 
                        currentLine = new StringBuilder(testLine); 
                    }
                }
                if (currentLine.length() > 0) finalLines.add(currentLine.toString());
            }
        } else {
            finalLines.addAll(rawLines);
        }
        return finalLines;
    }
}
