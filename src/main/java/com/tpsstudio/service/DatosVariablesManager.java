package com.tpsstudio.service;

import com.tpsstudio.model.project.FuenteDatos;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/*
 * Lee fuentes de datos externas (Excel o CSV) y las convierte en un FuenteDatos.
 * Excel es el formato principal. CSV se soporta como alternativa ligera.
 * Access queda aplazado para una fase posterior.
 */
public class DatosVariablesManager {

    private static final Logger log = Logger.getLogger(DatosVariablesManager.class.getName());

    /**
     * Intenta cargar el archivo indicado por la ruta.
     * Detecta el formato por la extensión del archivo.
     * Devuelve Optional.empty() si la ruta es inválida, el archivo no existe o
     * falla la lectura.
     */
    public Optional<FuenteDatos> cargar(String ruta) {
        if (ruta == null || ruta.isBlank())
            return Optional.empty();

        File archivo = new File(ruta);
        if (!archivo.exists() || !archivo.isFile()) {
            log.warning("Archivo de fuente de datos no encontrado: " + ruta);
            return Optional.empty();
        }

        String nombre = archivo.getName().toLowerCase();

        try {
            if (nombre.endsWith(".xlsx") || nombre.endsWith(".xls")) {
                return leerExcel(archivo);
            } else if (nombre.endsWith(".csv")) {
                return leerCsv(archivo);
            } else {
                log.warning("Formato no soportado en esta fase: " + nombre);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.severe("Error al cargar fuente de datos '" + ruta + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Guarda los datos de vuelta al archivo original (sobrescribe).
     * Soporta Excel (.xlsx, .xls) y CSV.
     */
    public boolean guardar(FuenteDatos datos, String ruta) {
        if (ruta == null || ruta.isBlank()) return false;
        
        File archivo = new File(ruta);
        String nombre = archivo.getName().toLowerCase();
        
        try {
            if (nombre.endsWith(".xlsx") || nombre.endsWith(".xls")) {
                return guardarExcel(datos, archivo);
            } else if (nombre.endsWith(".csv")) {
                return guardarCsv(datos, archivo);
            } else {
                log.warning("Formato no soportado para guardado: " + nombre);
                return false;
            }
        } catch (Exception e) {
            log.severe("Error al guardar fuente de datos '" + ruta + "': " + e.getMessage());
            return false;
        }
    }

    // ── Escritura Excel ────────────────────────────────────────────────────────
    
    private boolean guardarExcel(FuenteDatos datos, File archivo) throws IOException {
        // Para no perder formatos complejos, intentamos abrir el original y modificarlo
        try (InputStream is = new FileInputStream(archivo);
             Workbook wb = WorkbookFactory.create(is)) {
            
            Sheet hoja = wb.getSheetAt(0);
            if (hoja == null) return false;
            
            // Mapeo de nombres de columna a índices
            Map<String, Integer> colIndices = new HashMap<>();
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());
            if (cabecera != null) {
                for (Cell c : cabecera) {
                    colIndices.put(c.getStringCellValue().trim(), c.getColumnIndex());
                }
            }
            
            // Actualizar filas (empezando después de la cabecera)
            int filaIndex = hoja.getFirstRowNum() + 1;
            List<Map<String, String>> filas = datos.getFilas();
            
            for (Map<String, String> reg : filas) {
                Row row = hoja.getRow(filaIndex);
                if (row == null) row = hoja.createRow(filaIndex);
                
                for (Map.Entry<String, String> entry : reg.entrySet()) {
                    Integer colIdx = colIndices.get(entry.getKey());
                    if (colIdx != null) {
                        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        cell.setCellValue(entry.getValue());
                    }
                }
                filaIndex++;
            }
            
            // Escribir cambios
            try (OutputStream os = new FileOutputStream(archivo)) {
                wb.write(os);
            }
            return true;
        }
    }

    // ── Escritura CSV ──────────────────────────────────────────────────────────

    private boolean guardarCsv(FuenteDatos datos, File archivo) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8))) {
            
            List<String> columnas = datos.getColumnas();
            char sep = ';'; // Usamos punto y coma por defecto para Excel compatibility en España
            
            // Cabecera
            writer.write(String.join(String.valueOf(sep), columnas));
            writer.newLine();
            
            // Filas
            for (Map<String, String> reg : datos.getFilas()) {
                List<String> valores = new ArrayList<>();
                for (String col : columnas) {
                    String val = reg.getOrDefault(col, "");
                    // Escapar comillas si fuera necesario (simplificado aquí)
                    if (val.contains(String.valueOf(sep)) || val.contains("\"")) {
                        val = "\"" + val.replace("\"", "\"\"") + "\"";
                    }
                    valores.add(val);
                }
                writer.write(String.join(String.valueOf(sep), valores));
                writer.newLine();
            }
            return true;
        }
    }

    // ── Lectura Excel (.xlsx / .xls) ───────────────────────────────────────────

    private Optional<FuenteDatos> leerExcel(File archivo) throws IOException {
        try (Workbook wb = WorkbookFactory.create(archivo)) {
            // Siempre tomamos la primera hoja
            Sheet hoja = wb.getSheetAt(0);
            if (hoja == null) {
                log.warning("El archivo Excel no tiene hojas: " + archivo.getName());
                return Optional.empty();
            }

            // DataFormatter convierte cualquier celda a String igual que la vería el
            // usuario en Excel
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            List<String> columnas = new ArrayList<>();
            List<Map<String, String>> filas = new ArrayList<>();

            boolean primeraFila = true;
            for (Row fila : hoja) {
                // Ignorar filas completamente vacías
                if (esFilaVacia(fila, fmt, evaluator))
                    continue;

                if (primeraFila) {
                    // Primera fila no vacía = cabecera
                    for (Cell celda : fila) {
                        String nombre = fmt.formatCellValue(celda, evaluator).trim();
                        columnas.add(nombre.isEmpty() ? "Columna_" + (celda.getColumnIndex() + 1) : nombre);
                    }
                    primeraFila = false;
                } else {
                    // Resto = registros
                    Map<String, String> registro = new LinkedHashMap<>();
                    for (int i = 0; i < columnas.size(); i++) {
                        Cell celda = fila.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        registro.put(columnas.get(i), fmt.formatCellValue(celda, evaluator).trim());
                    }
                    filas.add(registro);
                }
            }

            if (columnas.isEmpty()) {
                log.warning("El archivo Excel no tiene cabecera legible: " + archivo.getName());
                return Optional.empty();
            }

            log.info("Excel cargado: " + archivo.getName() + " — " + filas.size() + " registros, " + columnas.size()
                    + " columnas");
            return Optional.of(new FuenteDatos(archivo.getName(), columnas, filas));
        }
    }

    /** Devuelve true si todas las celdas de la fila están vacías. */
    private boolean esFilaVacia(Row fila, DataFormatter fmt, FormulaEvaluator evaluator) {
        if (fila == null)
            return true;
        for (Cell celda : fila) {
            if (!fmt.formatCellValue(celda, evaluator).trim().isEmpty())
                return false;
        }
        return true;
    }

    // ── Lectura CSV ────────────────────────────────────────────────────────────

    private Optional<FuenteDatos> leerCsv(File archivo) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8))) {

            // Primera línea: detectar separador y leer cabecera
            String lineaCabecera = reader.readLine();
            if (lineaCabecera == null || lineaCabecera.isBlank()) {
                log.warning("CSV vacío o sin cabecera: " + archivo.getName());
                return Optional.empty();
            }

            char separador = detectarSeparador(lineaCabecera);
            List<String> columnas = splitCsv(lineaCabecera, separador);

            List<Map<String, String>> filas = new ArrayList<>();
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (linea.isBlank())
                    continue;
                List<String> valores = splitCsv(linea, separador);
                Map<String, String> registro = new LinkedHashMap<>();
                for (int i = 0; i < columnas.size(); i++) {
                    registro.put(columnas.get(i), i < valores.size() ? valores.get(i).trim() : "");
                }
                filas.add(registro);
            }

            log.info("CSV cargado: " + archivo.getName() + " — " + filas.size() + " registros, " + columnas.size()
                    + " columnas");
            return Optional.of(new FuenteDatos(archivo.getName(), columnas, filas));
        }
    }

    /** Autodetecta si el CSV usa ',' o ';' como separador. */
    private char detectarSeparador(String primeraLinea) {
        long puntoYComa = primeraLinea.chars().filter(c -> c == ';').count();
        long coma = primeraLinea.chars().filter(c -> c == ',').count();
        return puntoYComa >= coma ? ';' : ',';
    }

    /** Divide una línea CSV respetando comillas dobles. */
    private List<String> splitCsv(String linea, char sep) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean enComillas = false;

        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                // Comilla doble escapada ("") dentro de un campo entre comillas
                if (enComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    enComillas = !enComillas;
                }
            } else if (c == sep && !enComillas) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }
}
