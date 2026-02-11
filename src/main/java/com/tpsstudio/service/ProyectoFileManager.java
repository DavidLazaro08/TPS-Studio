package com.tpsstudio.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.tpsstudio.model.elements.*;
import com.tpsstudio.model.enums.*;
import com.tpsstudio.model.project.*;
import com.tpsstudio.util.ImageUtils;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/* Gestor de archivos y carpetas del proyecto */

public class ProyectoFileManager {

    // Adaptador personalizado para LocalDateTime
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString(), formatter);
        }
    }

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private static final DateTimeFormatter CLIENTE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /* Crea la estructura de carpetas del proyecto
     *
     * @param metadata Metadatos del proyecto
     * @return true si se creó correctamente */

    public boolean crearEstructuraCarpetas(ProyectoMetadata metadata) {
        try {
            // Normalizar nombre (quitar caracteres especiales)
            String nombreCarpeta = "TPS_" + normalizarNombre(metadata.getNombre());

            // Crear carpeta principal
            Path carpetaProyecto = Paths.get(metadata.getUbicacion(), nombreCarpeta);
            Files.createDirectories(carpetaProyecto);

            // Crear subcarpetas
            Path carpetaFotos = carpetaProyecto.resolve("Fotos");
            Path carpetaFondos = carpetaProyecto.resolve("Fondos");
            Path carpetaBBDD = carpetaProyecto.resolve("BBDD");

            Files.createDirectories(carpetaFotos);
            Files.createDirectories(carpetaFondos);
            Files.createDirectories(carpetaBBDD);

            // Actualizar metadata con rutas
            String nombreArchivo = normalizarNombre(metadata.getNombre()) + ".tps";
            metadata.setRutaTPS(carpetaProyecto.resolve(nombreArchivo).toString());
            metadata.setRutaFotos(carpetaFotos.toString());
            metadata.setRutaFondos(carpetaFondos.toString());

            // Inicializar fecha de creación si no existe
            if (metadata.getFechaCreacion() == null) {
                metadata.setFechaCreacion(LocalDateTime.now());
            }

            // Si hay BD, copiarla
            if (metadata.getRutaBBDD() != null && !metadata.getRutaBBDD().isEmpty()) {
                Path bdOrigen = Paths.get(metadata.getRutaBBDD());
                Path bdDestino = carpetaBBDD.resolve(bdOrigen.getFileName());
                Files.copy(bdOrigen, bdDestino, StandardCopyOption.REPLACE_EXISTING);
                metadata.setRutaBBDD(bdDestino.toString());
            }

            // Exportar datos del cliente si existen
            if (metadata.getClienteInfo() != null && metadata.getClienteInfo().tieneInformacion()) {
                exportarDatosCliente(carpetaProyecto, metadata.getClienteInfo());
            }

            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* Copia una imagen al proyecto y devuelve la ruta relativa
     *
     * @param imagenOrigen Archivo de imagen original
     * @param metadata     Metadatos del proyecto
     * @param esFondo      true si es un fondo, false si es una imagen normal
     * @param sufijo       Sufijo opcional para el nombre (ej: "FRENTE", "DORSO")
     * @return Ruta relativa (ej: "Fotos/logo.png") o null si falla */

    public String copiarImagenAProyecto(File imagenOrigen, ProyectoMetadata metadata, boolean esFondo, String sufijo) {
        try {
            String carpetaDestino = esFondo ? metadata.getRutaFondos() : metadata.getRutaFotos();

            // Construir nombre de archivo con sufijo si se proporciona
            String nombreOriginal = imagenOrigen.getName();
            String nombreFinal;

            if (sufijo != null && !sufijo.isEmpty()) {
                int puntoIndex = nombreOriginal.lastIndexOf('.');
                if (puntoIndex > 0) {
                    String nombre = nombreOriginal.substring(0, puntoIndex);
                    String extension = nombreOriginal.substring(puntoIndex);
                    nombreFinal = nombre + "_" + sufijo + extension;
                } else {
                    nombreFinal = nombreOriginal + "_" + sufijo;
                }
            } else {
                nombreFinal = nombreOriginal;
            }

            Path destino = Paths.get(carpetaDestino, nombreFinal);

            Files.copy(imagenOrigen.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);

            String subcarpeta = esFondo ? "Fondos" : "Fotos";
            return subcarpeta + "/" + nombreFinal;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /* Versión simplificada sin sufijo (para compatibilidad) */

    public String copiarImagenAProyecto(File imagenOrigen, ProyectoMetadata metadata, boolean esFondo) {
        return copiarImagenAProyecto(imagenOrigen, metadata, esFondo, null);
    }

    /* Guarda el proyecto en formato JSON
     *
     * @param proyecto Proyecto a guardar
     * @param metadata Metadatos del proyecto
     * @return true si se guardó correctamente */

    public boolean guardarProyecto(Proyecto proyecto, ProyectoMetadata metadata) {
        try {
            System.out.println("[DEBUG] Guardando proyecto: " + proyecto.getNombre());
            System.out.println("[DEBUG] Ruta TPS: " + metadata.getRutaTPS());

            metadata.setFechaModificacion(LocalDateTime.now());

            // Re-exportar datos del cliente (para asegurar que no se borre)
            if (metadata.getClienteInfo() != null && metadata.getClienteInfo().tieneInformacion()) {
                Path carpetaProyecto = Paths.get(metadata.getCarpetaProyecto());
                exportarDatosCliente(carpetaProyecto, metadata.getClienteInfo());
            }

            // Crear DTO para serialización
            ProyectoDTO dto = new ProyectoDTO();
            dto.setNombre(proyecto.getNombre());
            dto.setMetadata(metadata);
            dto.setMostrandoFrente(proyecto.isMostrandoFrente());
            dto.setFondoFitModePreferido(proyecto.getFondoFitModePreferido());
            dto.setNoVolverAPreguntarFondo(proyecto.isNoVolverAPreguntarFondo());

            dto.setElementosFrente(convertirElementosADTO(proyecto.getElementosFrente()));
            dto.setElementosDorso(convertirElementosADTO(proyecto.getElementosDorso()));

            if (proyecto.getFondoFrente() != null) {
                dto.setFondoFrente(convertirFondoADTO(proyecto.getFondoFrente()));
            }
            if (proyecto.getFondoDorso() != null) {
                dto.setFondoDorso(convertirFondoADTO(proyecto.getFondoDorso()));
            }

            String json = gson.toJson(dto);
            Path archivoTPS = Paths.get(metadata.getRutaTPS());
            Files.writeString(archivoTPS, json);

            System.out.println("[DEBUG] ✓ Proyecto guardado exitosamente en: " + archivoTPS);
            return true;

        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo guardar el proyecto:");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("[ERROR] Error inesperado al guardar:");
            e.printStackTrace();
            return false;
        }
    }

    /* Carga un proyecto desde archivo .tps
     *
     * @param archivoTPS Archivo .tps a cargar
     * @return Proyecto cargado o null si falla */

    public Proyecto cargarProyecto(File archivoTPS) {
        try {
            String json = Files.readString(archivoTPS.toPath());
            ProyectoDTO dto = gson.fromJson(json, ProyectoDTO.class);

            Proyecto proyecto = new Proyecto(dto.getNombre());
            proyecto.setMetadata(dto.getMetadata());
            proyecto.setMostrandoFrente(true); // Siempre abrir mostrando el frente
            proyecto.setFondoFitModePreferido(dto.getFondoFitModePreferido());
            proyecto.setNoVolverAPreguntarFondo(dto.isNoVolverAPreguntarFondo());

            Path carpetaProyecto = archivoTPS.toPath().getParent();

            // --- REHIDRATAR METADATA (por si el proyecto se movió de sitio) ---
            ProyectoMetadata metadata = proyecto.getMetadata();
            if (metadata != null && carpetaProyecto != null) {

                metadata.setRutaTPS(archivoTPS.getAbsolutePath());
                metadata.setRutaFotos(carpetaProyecto.resolve("Fotos").toString());
                metadata.setRutaFondos(carpetaProyecto.resolve("Fondos").toString());

                // Rehidratar ruta BBDD si existía (siempre dentro de /BBDD)
                if (metadata.getRutaBBDD() != null && !metadata.getRutaBBDD().isEmpty()) {
                    Path bbddNueva = carpetaProyecto.resolve("BBDD")
                            .resolve(Paths.get(metadata.getRutaBBDD()).getFileName());
                    if (Files.exists(bbddNueva)) {
                        metadata.setRutaBBDD(bbddNueva.toString());
                    }
                }
            }

            // Reconstruir elementos
            proyecto.getElementosFrente().addAll(
                    convertirDTOAElementos(dto.getElementosFrente(), carpetaProyecto));
            proyecto.getElementosDorso().addAll(
                    convertirDTOAElementos(dto.getElementosDorso(), carpetaProyecto));

            // Reconstruir fondos
            if (dto.getFondoFrente() != null) {
                proyecto.setFondoFrente(convertirDTOAFondo(dto.getFondoFrente(), carpetaProyecto));
            }
            if (dto.getFondoDorso() != null) {
                proyecto.setFondoDorso(convertirDTOAFondo(dto.getFondoDorso(), carpetaProyecto));
            }

            validarIntegridad(proyecto, proyecto.getMetadata());

            return proyecto;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ===== MÉTODOS PRIVADOS DE CONVERSIÓN =====

    private List<ElementoDTO> convertirElementosADTO(List<Elemento> elementos) {
        List<ElementoDTO> dtos = new ArrayList<>();
        for (Elemento elem : elementos) {
            ElementoDTO dto = new ElementoDTO();
            dto.setNombre(elem.getNombre());
            dto.setX(elem.getX());
            dto.setY(elem.getY());
            dto.setLocked(elem.isLocked());

            if (elem instanceof TextoElemento) {
                TextoElemento texto = (TextoElemento) elem;
                dto.setTipo("texto");
                dto.setContenido(texto.getContenido());
                dto.setFuente(texto.getFontFamily());
                dto.setTamaño((int) texto.getFontSize());
                dto.setColor(texto.getColor());
                dto.setWidth(texto.getWidth());

            } else if (elem instanceof ImagenElemento) {
                ImagenElemento imagen = (ImagenElemento) elem;
                dto.setTipo("imagen");
                dto.setRutaImagen(imagen.getRutaArchivo());
                dto.setWidth(imagen.getWidth());
                dto.setHeight(imagen.getHeight());
                dto.setMantenerProporcion(imagen.isMantenerProporcion());
            }

            dtos.add(dto);
        }
        return dtos;
    }

    private FondoDTO convertirFondoADTO(ImagenFondoElemento fondo) {
        FondoDTO dto = new FondoDTO();
        dto.setRutaImagen(fondo.getRutaArchivo());
        dto.setFitMode(fondo.getFitMode().name());
        return dto;
    }

    private List<Elemento> convertirDTOAElementos(List<ElementoDTO> dtos, Path carpetaProyecto) {
        List<Elemento> elementos = new ArrayList<>();
        if (dtos == null || carpetaProyecto == null) return elementos;

        for (ElementoDTO dto : dtos) {
            Elemento elem = null;

            if ("texto".equals(dto.getTipo())) {
                TextoElemento texto = new TextoElemento(
                        dto.getNombre(),
                        dto.getX(),
                        dto.getY());
                texto.setContenido(dto.getContenido());
                texto.setFontFamily(dto.getFuente());
                texto.setFontSize(dto.getTamaño());
                texto.setColor(dto.getColor());
                texto.setWidth(dto.getWidth());
                elem = texto;

            } else if ("imagen".equals(dto.getTipo())) {
                Path rutaAbsoluta = carpetaProyecto.resolve(dto.getRutaImagen());
                if (Files.exists(rutaAbsoluta)) {
                    javafx.scene.image.Image img = ImageUtils
                            .cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

                    ImagenElemento imagen = new ImagenElemento(
                            dto.getNombre(),
                            dto.getX(),
                            dto.getY(),
                            dto.getRutaImagen(),
                            img);

                    imagen.setWidth(dto.getWidth());
                    imagen.setHeight(dto.getHeight());
                    imagen.setMantenerProporcion(dto.isMantenerProporcion());
                    elem = imagen;
                }
            }

            if (elem != null) {
                elem.setLocked(dto.isLocked());
                elementos.add(elem);
            }
        }

        return elementos;
    }

    private ImagenFondoElemento convertirDTOAFondo(FondoDTO dto, Path carpetaProyecto) {
        if (dto == null || carpetaProyecto == null) return null;

        Path rutaAbsoluta = carpetaProyecto.resolve(dto.getRutaImagen());
        if (!Files.exists(rutaAbsoluta)) {
            return null;
        }

        javafx.scene.image.Image img = ImageUtils
                .cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

        FondoFitMode fitMode = FondoFitMode.valueOf(dto.getFitMode());
        ImagenFondoElemento fondo = new ImagenFondoElemento(
                dto.getRutaImagen(),
                img,
                EditorCanvasManager.CARD_WIDTH,
                EditorCanvasManager.CARD_HEIGHT,
                fitMode);

        fondo.ajustarATamaño(
                EditorCanvasManager.CARD_WIDTH,
                EditorCanvasManager.CARD_HEIGHT,
                EditorCanvasManager.BLEED_MARGIN);

        return fondo;
    }

    /* Valida que todas las imágenes existan */

    private void validarIntegridad(Proyecto proyecto, ProyectoMetadata metadata) {
        if (metadata == null) return;

        String carpeta = metadata.getCarpetaProyecto();
        if (carpeta == null || carpeta.isEmpty()) return;

        List<String> imagenesFaltantes = new ArrayList<>();
        Path carpetaProyecto = Paths.get(carpeta);

        verificarImagenesElementos(proyecto.getElementosFrente(), carpetaProyecto, imagenesFaltantes);
        verificarImagenesElementos(proyecto.getElementosDorso(), carpetaProyecto, imagenesFaltantes);

        if (proyecto.getFondoFrente() != null) {
            verificarImagen(proyecto.getFondoFrente().getRutaArchivo(), carpetaProyecto, imagenesFaltantes);
        }
        if (proyecto.getFondoDorso() != null) {
            verificarImagen(proyecto.getFondoDorso().getRutaArchivo(), carpetaProyecto, imagenesFaltantes);
        }

        if (!imagenesFaltantes.isEmpty()) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Imágenes faltantes");
                alert.setHeaderText("Algunas imágenes no se encontraron");
                alert.setContentText("Archivos faltantes:\n" + String.join("\n", imagenesFaltantes));
                alert.showAndWait();
            });
        }
    }

    private void verificarImagenesElementos(List<Elemento> elementos, Path carpetaProyecto, List<String> faltantes) {
        for (Elemento elem : elementos) {
            if (elem instanceof ImagenElemento) {
                ImagenElemento img = (ImagenElemento) elem;
                verificarImagen(img.getRutaArchivo(), carpetaProyecto, faltantes);
            }
        }
    }

    private void verificarImagen(String rutaRelativa, Path carpetaProyecto, List<String> faltantes) {
        if (rutaRelativa == null) return;
        Path rutaAbsoluta = carpetaProyecto.resolve(rutaRelativa);
        if (!Files.exists(rutaAbsoluta)) {
            faltantes.add(rutaRelativa);
        }
    }

    /* Normaliza el nombre del proyecto para usarlo como nombre de carpeta */

    private String normalizarNombre(String nombre) {
        return nombre.replaceAll("[^a-zA-Z0-9_\\-\\s]", "_").replaceAll("\\s+", "_");
    }

    /* Exporta los datos del cliente a un archivo de texto */

    private void exportarDatosCliente(Path carpetaProyecto, ClienteInfo cliente) {
        try {
            Path archivoCliente = carpetaProyecto.resolve("datos_cliente.txt");
            StringBuilder contenido = new StringBuilder();

            contenido.append("===========================================\n");
            contenido.append("       DATOS DEL CLIENTE\n");
            contenido.append("===========================================\n\n");

            if (cliente.getNombreEmpresa() != null && !cliente.getNombreEmpresa().isEmpty()) {
                contenido.append("Empresa: ").append(cliente.getNombreEmpresa()).append("\n");
            }

            if (cliente.getNombreContacto() != null && !cliente.getNombreContacto().isEmpty()) {
                contenido.append("Contacto: ").append(cliente.getNombreContacto()).append("\n");
            }

            if (cliente.getEmail() != null && !cliente.getEmail().isEmpty()) {
                contenido.append("Email: ").append(cliente.getEmail()).append("\n");
            }

            if (cliente.getTelefono() != null && !cliente.getTelefono().isEmpty()) {
                contenido.append("Teléfono: ").append(cliente.getTelefono()).append("\n");
            }

            if (cliente.getObservaciones() != null && !cliente.getObservaciones().isEmpty()) {
                contenido.append("\nObservaciones:\n");
                contenido.append(cliente.getObservaciones()).append("\n");
            }

            contenido.append("\n===========================================\n");
            contenido.append("Generado: ").append(LocalDateTime.now().format(CLIENTE_FORMATTER)).append("\n");

            Files.writeString(archivoCliente, contenido.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===== CLASES DTO PARA JSON =====

    public static class ProyectoDTO {
        private String nombre;
        private ProyectoMetadata metadata;
        private boolean mostrandoFrente;
        private FondoFitMode fondoFitModePreferido;
        private boolean noVolverAPreguntarFondo;
        private List<ElementoDTO> elementosFrente;
        private List<ElementoDTO> elementosDorso;
        private FondoDTO fondoFrente;
        private FondoDTO fondoDorso;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public ProyectoMetadata getMetadata() { return metadata; }
        public void setMetadata(ProyectoMetadata metadata) { this.metadata = metadata; }

        public boolean isMostrandoFrente() { return mostrandoFrente; }
        public void setMostrandoFrente(boolean mostrandoFrente) { this.mostrandoFrente = mostrandoFrente; }

        public FondoFitMode getFondoFitModePreferido() { return fondoFitModePreferido; }
        public void setFondoFitModePreferido(FondoFitMode fondoFitModePreferido) { this.fondoFitModePreferido = fondoFitModePreferido; }

        public boolean isNoVolverAPreguntarFondo() { return noVolverAPreguntarFondo; }
        public void setNoVolverAPreguntarFondo(boolean noVolverAPreguntarFondo) { this.noVolverAPreguntarFondo = noVolverAPreguntarFondo; }

        public List<ElementoDTO> getElementosFrente() { return elementosFrente; }
        public void setElementosFrente(List<ElementoDTO> elementosFrente) { this.elementosFrente = elementosFrente; }

        public List<ElementoDTO> getElementosDorso() { return elementosDorso; }
        public void setElementosDorso(List<ElementoDTO> elementosDorso) { this.elementosDorso = elementosDorso; }

        public FondoDTO getFondoFrente() { return fondoFrente; }
        public void setFondoFrente(FondoDTO fondoFrente) { this.fondoFrente = fondoFrente; }

        public FondoDTO getFondoDorso() { return fondoDorso; }
        public void setFondoDorso(FondoDTO fondoDorso) { this.fondoDorso = fondoDorso; }
    }

    public static class ElementoDTO {
        private String tipo; // "texto" o "imagen"
        private String nombre;
        private double x;
        private double y;
        private boolean locked;

        private String contenido;
        private String fuente;
        private int tamaño;
        private String color;

        private String rutaImagen;
        private double width;
        private double height;
        private boolean mantenerProporcion;

        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }

        public double getY() { return y; }
        public void setY(double y) { this.y = y; }

        public boolean isLocked() { return locked; }
        public void setLocked(boolean locked) { this.locked = locked; }

        public String getContenido() { return contenido; }
        public void setContenido(String contenido) { this.contenido = contenido; }

        public String getFuente() { return fuente; }
        public void setFuente(String fuente) { this.fuente = fuente; }

        public int getTamaño() { return tamaño; }
        public void setTamaño(int tamaño) { this.tamaño = tamaño; }

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }

        public String getRutaImagen() { return rutaImagen; }
        public void setRutaImagen(String rutaImagen) { this.rutaImagen = rutaImagen; }

        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }

        public double getHeight() { return height; }
        public void setHeight(double height) { this.height = height; }

        public boolean isMantenerProporcion() { return mantenerProporcion; }
        public void setMantenerProporcion(boolean mantenerProporcion) { this.mantenerProporcion = mantenerProporcion; }
    }

    public static class FondoDTO {
        private String rutaImagen;
        private String fitMode;

        public String getRutaImagen() { return rutaImagen; }
        public void setRutaImagen(String rutaImagen) { this.rutaImagen = rutaImagen; }

        public String getFitMode() { return fitMode; }
        public void setFitMode(String fitMode) { this.fitMode = fitMode; }
    }
}
