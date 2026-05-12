package com.tpsstudio.service;

import com.tpsstudio.model.project.FuenteDatos;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Gestiona la lectura y escritura de fuentes de datos externas.
 *
 * Soporta Excel y CSV como formatos principales, y bases de datos Access/SQLite
 * mediante JDBC cuando los drivers estén disponibles.
 */
public class DatosVariablesManager {

    private static final Logger log = Logger.getLogger(DatosVariablesManager.class.getName());

    // =====================================================
    // Carga de fuentes de datos
    // =====================================================

    public Optional<FuenteDatos> cargar(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return Optional.empty();
        }

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

            } else if (nombre.endsWith(".mdb") || nombre.endsWith(".accdb")) {
                return leerDatabase(archivo, "jdbc:ucanaccess://");

            } else if (nombre.endsWith(".db") || nombre.endsWith(".sqlite")) {
                return leerDatabase(archivo, "jdbc:sqlite:");

            } else {
                log.warning("Formato no soportado: " + nombre);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.severe("Error al cargar fuente de datos '" + ruta + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    // =====================================================
    // Guardado de fuentes de datos
    // =====================================================

    public boolean guardar(FuenteDatos datos, String ruta) {
        if (datos == null || ruta == null || ruta.isBlank()) {
            return false;
        }

        File archivo = new File(ruta);
        String nombre = archivo.getName().toLowerCase();

        try {
            if (nombre.endsWith(".xlsx") || nombre.endsWith(".xls")) {
                return guardarExcel(datos, archivo);

            } else if (nombre.endsWith(".csv")) {
                return guardarCsv(datos, archivo);

            } else if (nombre.endsWith(".mdb") || nombre.endsWith(".accdb")) {
                return guardarDatabase(datos, archivo, "jdbc:ucanaccess://");

            } else if (nombre.endsWith(".db") || nombre.endsWith(".sqlite")) {
                return guardarDatabase(datos, archivo, "jdbc:sqlite:");

            } else {
                log.warning("Formato no soportado para guardado: " + nombre);
                return false;
            }

        } catch (Exception e) {
            log.severe("Error al guardar fuente de datos '" + ruta + "': " + e.getMessage());
            return false;
        }
    }

    // =====================================================
    // Excel
    // =====================================================

    private Optional<FuenteDatos> leerExcel(File archivo) throws IOException {
        try (Workbook wb = WorkbookFactory.create(archivo)) {
            Sheet hoja = wb.getSheetAt(0);

            if (hoja == null) {
                log.warning("El archivo Excel no tiene hojas: " + archivo.getName());
                return Optional.empty();
            }

            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            List<String> columnas = new ArrayList<>();
            List<Map<String, String>> filas = new ArrayList<>();

            boolean primeraFila = true;

            for (Row fila : hoja) {
                if (esFilaVacia(fila, fmt, evaluator)) {
                    continue;
                }

                if (primeraFila) {
                    for (Cell celda : fila) {
                        String nombre = fmt.formatCellValue(celda, evaluator).trim();
                        columnas.add(nombre.isEmpty() ? "Columna_" + (celda.getColumnIndex() + 1) : nombre);
                    }

                    primeraFila = false;

                } else {
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

            log.info("Excel cargado: " + archivo.getName() + " — " +
                    filas.size() + " registros, " + columnas.size() + " columnas");

            return Optional.of(new FuenteDatos(archivo.getName(), columnas, filas));
        }
    }

    private boolean guardarExcel(FuenteDatos datos, File archivo) throws IOException {
        Workbook wb;

        try (InputStream is = new FileInputStream(archivo)) {
            wb = WorkbookFactory.create(is);
        }

        try (wb) {
            Sheet hoja = wb.getSheetAt(0);
            if (hoja == null) return false;

            Map<String, Integer> colIndices = new HashMap<>();
            Row cabecera = hoja.getRow(hoja.getFirstRowNum());

            if (cabecera != null) {
                for (Cell c : cabecera) {
                    colIndices.put(c.getStringCellValue().trim(), c.getColumnIndex());
                }
            }

            int filaIndex = hoja.getFirstRowNum() + 1;

            for (Map<String, String> reg : datos.getFilas()) {
                Row row = hoja.getRow(filaIndex);

                if (row == null) {
                    row = hoja.createRow(filaIndex);
                }

                for (Map.Entry<String, String> entry : reg.entrySet()) {
                    Integer colIdx = colIndices.get(entry.getKey());

                    if (colIdx != null) {
                        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        cell.setCellValue(entry.getValue());
                    }
                }

                filaIndex++;
            }

            try (OutputStream os = new FileOutputStream(archivo)) {
                wb.write(os);
            }

            return true;
        }
    }

    private boolean esFilaVacia(Row fila, DataFormatter fmt, FormulaEvaluator evaluator) {
        if (fila == null) {
            return true;
        }

        for (Cell celda : fila) {
            if (!fmt.formatCellValue(celda, evaluator).trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    // =====================================================
    // CSV
    // =====================================================

    private Optional<FuenteDatos> leerCsv(File archivo) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8))) {

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
                if (linea.isBlank()) {
                    continue;
                }

                List<String> valores = splitCsv(linea, separador);
                Map<String, String> registro = new LinkedHashMap<>();

                for (int i = 0; i < columnas.size(); i++) {
                    registro.put(columnas.get(i), i < valores.size() ? valores.get(i).trim() : "");
                }

                filas.add(registro);
            }

            log.info("CSV cargado: " + archivo.getName() + " — " +
                    filas.size() + " registros, " + columnas.size() + " columnas");

            return Optional.of(new FuenteDatos(archivo.getName(), columnas, filas));
        }
    }

    private boolean guardarCsv(FuenteDatos datos, File archivo) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8))) {

            List<String> columnas = datos.getColumnas();
            char sep = ';';

            writer.write(String.join(String.valueOf(sep), columnas));
            writer.newLine();

            for (Map<String, String> reg : datos.getFilas()) {
                List<String> valores = new ArrayList<>();

                for (String col : columnas) {
                    String val = reg.getOrDefault(col, "");

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

    private char detectarSeparador(String primeraLinea) {
        long puntoYComa = primeraLinea.chars().filter(c -> c == ';').count();
        long coma = primeraLinea.chars().filter(c -> c == ',').count();

        return puntoYComa >= coma ? ';' : ',';
    }

    private List<String> splitCsv(String linea, char sep) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean enComillas = false;

        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);

            if (c == '"') {
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

    // =====================================================
    // Bases de datos JDBC
    // =====================================================

    private Optional<FuenteDatos> leerDatabase(File archivo, String urlPrefix) {
        String url = urlPrefix + archivo.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(url)) {
            DatabaseMetaData dbmd = conn.getMetaData();

            try (ResultSet tables = dbmd.getTables(null, null, "%", new String[]{"TABLE"})) {
                if (tables.next()) {
                    String nombreTabla = tables.getString("TABLE_NAME");
                    return leerTabla(conn, nombreTabla, archivo.getName());
                }
            }

            log.warning("No se encontraron tablas en la base de datos: " + archivo.getName());
            return Optional.empty();

        } catch (Exception e) {
            log.severe("Error al conectar con la base de datos '" + archivo.getName() + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<FuenteDatos> leerTabla(Connection conn, String nombreTabla, String nombreArchivo) throws SQLException {
        String query = "SELECT * FROM [" + nombreTabla + "]";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            int numCols = rsmd.getColumnCount();

            List<String> columnas = new ArrayList<>();

            for (int i = 1; i <= numCols; i++) {
                columnas.add(rsmd.getColumnName(i));
            }

            List<Map<String, String>> filas = new ArrayList<>();

            while (rs.next()) {
                Map<String, String> registro = new LinkedHashMap<>();

                for (int i = 1; i <= numCols; i++) {
                    Object val = rs.getObject(i);
                    registro.put(columnas.get(i - 1), val != null ? val.toString() : "");
                }

                filas.add(registro);
            }

            log.info("Base de datos cargada (" + nombreTabla + "): " +
                    nombreArchivo + " — " + filas.size() + " registros");

            return Optional.of(new FuenteDatos(nombreArchivo, columnas, filas));
        }
    }

    private boolean guardarDatabase(FuenteDatos datos, File archivo, String urlPrefix) {
        String url = urlPrefix + archivo.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(false);

            String nombreTabla = null;
            DatabaseMetaData dbmd = conn.getMetaData();

            try (ResultSet tables = dbmd.getTables(null, null, "%", new String[]{"TABLE"})) {
                if (tables.next()) {
                    nombreTabla = tables.getString("TABLE_NAME");
                }
            }

            if (nombreTabla == null) {
                return false;
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM [" + nombreTabla + "]");
            }

            List<String> columnas = datos.getColumnas();

            StringBuilder sql = new StringBuilder("INSERT INTO [")
                    .append(nombreTabla)
                    .append("] (");

            for (int i = 0; i < columnas.size(); i++) {
                sql.append("[")
                        .append(columnas.get(i))
                        .append("]")
                        .append(i < columnas.size() - 1 ? "," : "");
            }

            sql.append(") VALUES (");

            for (int i = 0; i < columnas.size(); i++) {
                sql.append("?").append(i < columnas.size() - 1 ? "," : "");
            }

            sql.append(")");

            try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                for (Map<String, String> reg : datos.getFilas()) {
                    for (int i = 0; i < columnas.size(); i++) {
                        String val = reg.getOrDefault(columnas.get(i), "");
                        pstmt.setString(i + 1, val);
                    }

                    pstmt.addBatch();
                }

                pstmt.executeBatch();
            }

            conn.commit();
            log.info("Base de datos actualizada (" + nombreTabla + "): " + archivo.getName());

            return true;

        } catch (Exception e) {
            log.severe("Error al guardar en base de datos '" + archivo.getName() + "': " + e.getMessage());
            return false;
        }
    }
}