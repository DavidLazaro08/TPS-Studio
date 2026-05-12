package com.tpsstudio.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.tpsstudio.dao.ProyectoDAO;
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

/**
 * Gestiona la persistencia de los proyectos de TPS Studio.
 *
 * Se encarga de crear la estructura de carpetas, guardar y cargar el archivo .tps,
 * copiar recursos al proyecto y reconstruir los elementos al abrir un proyecto guardado.
 */
public class ProyectoFileManager implements ProyectoDAO {

    /**
     * Adaptador para guardar y leer fechas LocalDateTime con Gson.
     */
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

    /**
     * Crea la carpeta principal del proyecto y sus subcarpetas internas.
     */
    public boolean crearEstructuraCarpetas(ProyectoMetadata metadata) {
        try {
            String nombreCarpeta = "TPS_" + normalizarNombre(metadata.getNombre());

            Path carpetaProyecto = Paths.get(metadata.getUbicacion(), nombreCarpeta);
            Files.createDirectories(carpetaProyecto);

            Path carpetaFotos = carpetaProyecto.resolve("Fotos");
            Path carpetaFondos = carpetaProyecto.resolve("Fondos");
            Path carpetaBBDD = carpetaProyecto.resolve("Base de Datos (BBDD)");

            Files.createDirectories(carpetaFotos);
            Files.createDirectories(carpetaFondos);
            Files.createDirectories(carpetaBBDD);

            String nombreArchivo = normalizarNombre(metadata.getNombre()) + ".tps";
            metadata.setRutaTPS(carpetaProyecto.resolve(nombreArchivo).toString());
            metadata.setRutaFotos(carpetaFotos.toString());
            metadata.setRutaFondos(carpetaFondos.toString());

            if (metadata.getFechaCreacion() == null) {
                metadata.setFechaCreacion(LocalDateTime.now());
            }

            if (metadata.getRutaBBDD() != null && !metadata.getRutaBBDD().isEmpty()) {
                Path bdOrigen = Paths.get(metadata.getRutaBBDD());
                String ext = obtenerExtension(bdOrigen.getFileName().toString());
                String nombreBD = "BD_" + normalizarNombre(metadata.getNombre()) + ext;
                Path bdDestino = carpetaBBDD.resolve(nombreBD);

                Files.copy(bdOrigen, bdDestino, StandardCopyOption.REPLACE_EXISTING);
                metadata.setRutaBBDD(bdDestino.toString());
            }

            if (metadata.getClienteInfo() != null && metadata.getClienteInfo().tieneInformacion()) {
                exportarDatosCliente(carpetaProyecto, metadata.getClienteInfo());
            }

            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Copia una imagen dentro del proyecto y devuelve su ruta relativa.
     */
    public String copiarImagenAProyecto(File imagenOrigen, ProyectoMetadata metadata, boolean esFondo, String sufijo) {
        try {
            String carpetaDestino = esFondo ? metadata.getRutaFondos() : metadata.getRutaFotos();

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

    /**
     * Versión sin sufijo para imágenes normales.
     */
    public String copiarImagenAProyecto(File imagenOrigen, ProyectoMetadata metadata, boolean esFondo) {
        return copiarImagenAProyecto(imagenOrigen, metadata, esFondo, null);
    }

    /**
     * Guarda el proyecto en su archivo .tps.
     */
    public boolean guardarProyecto(Proyecto proyecto, ProyectoMetadata metadata) {
        try {
            metadata.setFechaModificacion(LocalDateTime.now());

            if (metadata.getClienteInfo() != null && metadata.getClienteInfo().tieneInformacion()) {
                Path carpetaProyecto = Paths.get(metadata.getCarpetaProyecto());
                exportarDatosCliente(carpetaProyecto, metadata.getClienteInfo());
            }

            ProyectoDTO dto = new ProyectoDTO();
            dto.setNombre(proyecto.getNombre());
            dto.setMetadata(metadata);
            dto.setMostrandoFrente(proyecto.isMostrandoFrente());
            dto.setFondoFitModePreferido(proyecto.getFondoFitModePreferido());
            dto.setNoVolverAPreguntarFondo(proyecto.isNoVolverAPreguntarFondo());
            dto.setOrientacion(proyecto.getOrientacion());

            dto.setElementosFrente(convertirElementosADTO(proyecto.getElementosFrente()));
            dto.setElementosDorso(convertirElementosADTO(proyecto.getElementosDorso()));

            if (proyecto.getFondoFrente() != null) {
                dto.setFondoFrente(convertirFondoADTO(proyecto.getFondoFrente()));
            }

            if (proyecto.getFondoDorso() != null) {
                dto.setFondoDorso(convertirFondoADTO(proyecto.getFondoDorso()));
            }

            dto.setEtiquetaIds(proyecto.getEtiquetaIds());

            String json = gson.toJson(dto);
            Path archivoTPS = Paths.get(metadata.getRutaTPS());
            Files.writeString(archivoTPS, json);

            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Carga un proyecto desde un archivo .tps.
     */
    public Proyecto cargarProyecto(File archivoTPS) {
        try {
            String json = Files.readString(archivoTPS.toPath());
            ProyectoDTO dto = gson.fromJson(json, ProyectoDTO.class);

            Proyecto proyecto = new Proyecto(dto.getNombre());
            proyecto.setMetadata(dto.getMetadata());
            proyecto.setMostrandoFrente(true);
            proyecto.setFondoFitModePreferido(dto.getFondoFitModePreferido());
            proyecto.setNoVolverAPreguntarFondo(dto.isNoVolverAPreguntarFondo());

            if (dto.getOrientacion() != null) {
                proyecto.setOrientacion(dto.getOrientacion());
            } else if (proyecto.getMetadata() != null) {
                proyecto.setOrientacion(proyecto.getMetadata().getOrientacion());
            }

            if (dto.getEtiquetaIds() != null) {
                proyecto.setEtiquetaIds(new ArrayList<>(dto.getEtiquetaIds()));
            }

            Path carpetaProyecto = archivoTPS.toPath().getParent();

            ProyectoMetadata metadata = proyecto.getMetadata();

            if (metadata != null && carpetaProyecto != null) {
                metadata.setRutaTPS(archivoTPS.getAbsolutePath());
                metadata.setRutaFotos(carpetaProyecto.resolve("Fotos").toString());
                metadata.setRutaFondos(carpetaProyecto.resolve("Fondos").toString());

                if (metadata.getRutaBBDD() != null && !metadata.getRutaBBDD().isEmpty()) {
                    String nombreArchivoGuardado = Paths.get(metadata.getRutaBBDD()).getFileName().toString();

                    Path bbddNueva = carpetaProyecto.resolve("Base de Datos (BBDD)").resolve(nombreArchivoGuardado);
                    Path bbddLegacy = carpetaProyecto.resolve("BBDD").resolve(nombreArchivoGuardado);

                    if (Files.exists(bbddNueva)) {
                        metadata.setRutaBBDD(bbddNueva.toString());
                    } else if (Files.exists(bbddLegacy)) {
                        metadata.setRutaBBDD(bbddLegacy.toString());
                    }
                }
            }

            proyecto.getElementosFrente().addAll(
                    convertirDTOAElementos(dto.getElementosFrente(), carpetaProyecto)
            );

            proyecto.getElementosDorso().addAll(
                    convertirDTOAElementos(dto.getElementosDorso(), carpetaProyecto)
            );

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

    // =====================================================
    // Conversión entre modelo y DTO
    // =====================================================

    private List<ElementoDTO> convertirElementosADTO(List<Elemento> elementos) {
        List<ElementoDTO> dtos = new ArrayList<>();

        for (Elemento elem : elementos) {
            ElementoDTO dto = new ElementoDTO();

            dto.setNombre(elem.getNombre());
            dto.setEtiqueta(elem.getEtiqueta());
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
                dto.setColumnaVinculada(texto.getColumnaVinculada());

            } else if (elem instanceof ImagenElemento) {
                ImagenElemento imagen = (ImagenElemento) elem;

                dto.setTipo("imagen");
                dto.setRutaImagen(imagen.getRutaArchivo());
                dto.setWidth(imagen.getWidth());
                dto.setHeight(imagen.getHeight());
                dto.setMantenerProporcion(imagen.isMantenerProporcion());
                dto.setColumnaVinculada(imagen.getColumnaVinculada());

            } else if (elem instanceof FormaElemento forma) {
                dto.setTipo("forma");
                dto.setWidth(forma.getWidth());
                dto.setHeight(forma.getHeight());
                dto.setTipoForma(forma.getTipoForma().name());
                dto.setColorRelleno(forma.getColorRelleno());
                dto.setColorBorde(forma.getColorBorde());
                dto.setGrosorBorde(forma.getGrosorBorde());
                dto.setConRelleno(forma.isConRelleno());
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

        if (dtos == null || carpetaProyecto == null) {
            return elementos;
        }

        for (ElementoDTO dto : dtos) {
            Elemento elem = null;

            if ("texto".equals(dto.getTipo())) {
                TextoElemento texto = new TextoElemento(
                        dto.getNombre(),
                        dto.getX(),
                        dto.getY()
                );

                texto.setContenido(dto.getContenido());
                texto.setFontFamily(dto.getFuente());
                texto.setFontSize(dto.getTamaño());
                texto.setColor(dto.getColor());
                texto.setWidth(dto.getWidth());
                texto.setColumnaVinculada(dto.getColumnaVinculada());

                elem = texto;

            } else if ("imagen".equals(dto.getTipo())) {
                ImagenElemento imagen;

                if (dto.getRutaImagen() == null || dto.getRutaImagen().isBlank()) {
                    imagen = new ImagenElemento(dto.getNombre(), dto.getX(), dto.getY(), null, null);

                } else {
                    Path rutaAbsoluta = carpetaProyecto.resolve(dto.getRutaImagen());

                    javafx.scene.image.Image img = Files.exists(rutaAbsoluta)
                            ? ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString())
                            : null;

                    imagen = new ImagenElemento(dto.getNombre(), dto.getX(), dto.getY(), dto.getRutaImagen(), img);
                }

                imagen.setWidth(dto.getWidth());
                imagen.setHeight(dto.getHeight());
                imagen.setMantenerProporcion(dto.isMantenerProporcion());
                imagen.setColumnaVinculada(dto.getColumnaVinculada());

                elem = imagen;

            } else if ("forma".equals(dto.getTipo())) {
                FormaElemento.TipoForma tipo = FormaElemento.TipoForma.valueOf(
                        dto.getTipoForma() != null ? dto.getTipoForma() : "RECTANGULO"
                );

                FormaElemento forma = new FormaElemento(
                        dto.getNombre(),
                        dto.getX(),
                        dto.getY(),
                        dto.getWidth(),
                        dto.getHeight(),
                        tipo
                );

                if (dto.getColorRelleno() != null) {
                    forma.setColorRelleno(dto.getColorRelleno());
                }

                if (dto.getColorBorde() != null) {
                    forma.setColorBorde(dto.getColorBorde());
                }

                forma.setGrosorBorde(dto.getGrosorBorde());
                forma.setConRelleno(dto.isConRelleno());

                elem = forma;
            }

            if (elem != null) {
                elem.setLocked(dto.isLocked());

                if (dto.getEtiqueta() != null && !dto.getEtiqueta().isEmpty()) {
                    elem.setEtiqueta(dto.getEtiqueta());
                }

                elementos.add(elem);
            }
        }

        return elementos;
    }

    private ImagenFondoElemento convertirDTOAFondo(FondoDTO dto, Path carpetaProyecto) {
        if (dto == null || carpetaProyecto == null) {
            return null;
        }

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
                fitMode
        );

        fondo.ajustarATamaño(
                EditorCanvasManager.CARD_WIDTH,
                EditorCanvasManager.CARD_HEIGHT,
                EditorCanvasManager.BLEED_MARGIN
        );

        return fondo;
    }

    // =====================================================
    // Validación de recursos
    // =====================================================

    private void validarIntegridad(Proyecto proyecto, ProyectoMetadata metadata) {
        if (metadata == null) {
            return;
        }

        String carpeta = metadata.getCarpetaProyecto();

        if (carpeta == null || carpeta.isEmpty()) {
            return;
        }

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
                Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.WARNING);
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
        if (rutaRelativa == null) {
            return;
        }

        Path rutaAbsoluta = carpetaProyecto.resolve(rutaRelativa);

        if (!Files.exists(rutaAbsoluta)) {
            faltantes.add(rutaRelativa);
        }
    }

    // =====================================================
    // Utilidades de archivo
    // =====================================================

    private String normalizarNombre(String nombre) {
        return nombre.replaceAll("[^a-zA-Z0-9_\\-\\s]", "_").replaceAll("\\s+", "_");
    }

    private String obtenerExtension(String nombreArchivo) {
        int idx = nombreArchivo.lastIndexOf('.');
        return (idx > 0) ? nombreArchivo.substring(idx) : "";
    }

    /**
     * Copia un archivo de base de datos a la carpeta interna del proyecto.
     */
    public String copiarBDAlProyecto(File bdOrigen, ProyectoMetadata metadata) {
        try {
            if (metadata == null || metadata.getCarpetaProyecto() == null) {
                return null;
            }

            Path carpetaBBDD = Paths.get(metadata.getCarpetaProyecto()).resolve("Base de Datos (BBDD)");
            Files.createDirectories(carpetaBBDD);

            String ext = obtenerExtension(bdOrigen.getName());
            String nombreBD = "BD_" + normalizarNombre(metadata.getNombre()) + ext;
            Path destino = carpetaBBDD.resolve(nombreBD);

            if (bdOrigen.toPath().equals(destino)) {
                return destino.toString();
            }

            Files.copy(bdOrigen.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
            return destino.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

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

    // =====================================================
    // DTO para guardar el proyecto en JSON
    // =====================================================

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
        private List<String> etiquetaIds;
        private com.tpsstudio.model.enums.Orientacion orientacion;

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

        public List<String> getEtiquetaIds() { return etiquetaIds; }
        public void setEtiquetaIds(List<String> etiquetaIds) { this.etiquetaIds = etiquetaIds; }

        public com.tpsstudio.model.enums.Orientacion getOrientacion() { return orientacion; }
        public void setOrientacion(com.tpsstudio.model.enums.Orientacion orientacion) { this.orientacion = orientacion; }
    }

    public static class ElementoDTO {
        private String tipo;
        private String nombre;
        private String etiqueta;
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
        private String columnaVinculada;

        private String tipoForma;
        private String colorRelleno;
        private String colorBorde;
        private double grosorBorde;
        private boolean conRelleno;

        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getEtiqueta() { return etiqueta; }
        public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }

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

        public String getColumnaVinculada() { return columnaVinculada; }
        public void setColumnaVinculada(String columnaVinculada) { this.columnaVinculada = columnaVinculada; }

        public String getTipoForma() { return tipoForma; }
        public void setTipoForma(String tipoForma) { this.tipoForma = tipoForma; }

        public String getColorRelleno() { return colorRelleno; }
        public void setColorRelleno(String colorRelleno) { this.colorRelleno = colorRelleno; }

        public String getColorBorde() { return colorBorde; }
        public void setColorBorde(String colorBorde) { this.colorBorde = colorBorde; }

        public double getGrosorBorde() { return grosorBorde; }
        public void setGrosorBorde(double grosorBorde) { this.grosorBorde = grosorBorde; }

        public boolean isConRelleno() { return conRelleno; }
        public void setConRelleno(boolean conRelleno) { this.conRelleno = conRelleno; }
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