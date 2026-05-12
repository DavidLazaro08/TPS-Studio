package com.tpsstudio.service;

import com.tpsstudio.dao.ProyectoDAO;
import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ElementoCodigo;
import com.tpsstudio.model.elements.FormaElemento;
import com.tpsstudio.model.elements.ImagenElemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.elements.TextoElemento;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.enums.Orientacion;
import com.tpsstudio.model.enums.TipoCodigo;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.util.ImageUtils;
import com.tpsstudio.view.managers.EditorCanvasManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Servicio principal para la gestión de proyectos de TPS Studio.
 *
 * <p>Centraliza las operaciones relacionadas con el ciclo de vida de un proyecto:
 * creación, apertura, guardado, duplicado, cierre y gestión básica de elementos.</p>
 *
 * <p>La persistencia se delega en una implementación de {@link ProyectoDAO}, de forma
 * que esta clase no se encarga directamente de serializar ni leer archivos.</p>
 *
 * <p>La comunicación con la interfaz se realiza mediante callbacks. Así se evita que
 * el servicio dependa directamente de los controladores de JavaFX.</p>
 */
public class ProjectManager {

    private final ObservableList<Proyecto> proyectos = FXCollections.observableArrayList();

    private Proyecto proyectoActual;
    private FuenteDatos fuenteDatosActual;

    private Runnable onProjectChanged;
    private Runnable onElementAdded;
    private BiConsumer<String, String> onNotificacion;

    private final ProyectoDAO fileManager;
    private final RecentProjectsManager recentManager;
    private final DatosVariablesManager datosVariablesManager;

    // =====================================================
    // Constructor
    // =====================================================

    public ProjectManager() {
        this.fileManager = new ProyectoFileManager();

        String currentUser = AuthService.getInstance().getCurrentUser();
        this.recentManager = new RecentProjectsManager(currentUser);

        this.datosVariablesManager = new DatosVariablesManager();
    }

    // =====================================================
    // Callbacks de comunicación con la interfaz
    // =====================================================

    public void setOnProjectChanged(Runnable callback) {
        this.onProjectChanged = callback;
    }

    public void setOnElementAdded(Runnable callback) {
        this.onElementAdded = callback;
    }

    /**
     * Registra el callback usado para mostrar notificaciones en la interfaz.
     *
     * @param callback recibe el tipo de aviso ("info" o "error") y el mensaje.
     */
    public void setOnNotificacion(BiConsumer<String, String> callback) {
        this.onNotificacion = callback;
    }

    // =====================================================
    // Getters / setters
    // =====================================================

    public ObservableList<Proyecto> getProyectos() {
        return proyectos;
    }

    public Proyecto getProyectoActual() {
        return proyectoActual;
    }

    public void setProyectoActual(Proyecto proyecto) {
        this.proyectoActual = proyecto;

        String rutaBBDD = (proyecto != null && proyecto.getMetadata() != null)
                ? proyecto.getMetadata().getRutaBBDD()
                : null;

        cargarFuenteDatos(rutaBBDD);
        avisarProyectoCambiado();
    }

    // =====================================================
    // Operaciones de proyecto
    // =====================================================

    /**
     * Crea un proyecto CR80 básico en memoria.
     * Se utiliza como creación rápida, sin diálogo ni estructura de carpetas.
     */
    public Proyecto crearNuevoCR80() {
        int numero = proyectos.size() + 1;

        Proyecto nuevoProyecto = new Proyecto("Tarjeta CR80 #" + numero);
        proyectos.add(nuevoProyecto);

        proyectoActual = nuevoProyecto;
        avisarProyectoCambiado();

        return nuevoProyecto;
    }

    /**
     * Crea un proyecto completo a partir de los metadatos indicados por la interfaz.
     *
     * <p>Este método crea la estructura de carpetas, genera el archivo .tps inicial,
     * añade el proyecto a la lista y lo registra como proyecto reciente.</p>
     */
    public Proyecto crearProyectoDesdeMetadata(ProyectoMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        if (!fileManager.crearEstructuraCarpetas(metadata)) {
            mostrarError("No se pudo crear la estructura de carpetas.");
            return null;
        }

        Proyecto nuevoProyecto = new Proyecto(metadata.getNombre());
        nuevoProyecto.setMetadata(metadata);
        nuevoProyecto.setOrientacion(metadata.getOrientacion());

        if (!fileManager.guardarProyecto(nuevoProyecto, metadata)) {
            mostrarError("No se pudo guardar el archivo de proyecto.");
            return null;
        }

        proyectos.add(nuevoProyecto);
        proyectoActual = nuevoProyecto;

        cargarFuenteDatos(metadata.getRutaBBDD());

        recentManager.anadirReciente(metadata.getRutaTPS());
        ordenarProyectos();
        avisarProyectoCambiado();

        return nuevoProyecto;
    }

    /**
     * Abre un proyecto existente desde un archivo .tps.
     */
    public Proyecto abrirProyectoDesdeArchivo(File file) {
        if (file == null) {
            return null;
        }

        Proyecto proyecto = fileManager.cargarProyecto(file);

        if (proyecto == null) {
            mostrarError("Error al leer el archivo de proyecto.");
            return null;
        }

        proyectos.add(proyecto);
        proyectoActual = proyecto;

        String rutaBBDD = proyecto.getMetadata() != null
                ? proyecto.getMetadata().getRutaBBDD()
                : null;

        cargarFuenteDatos(rutaBBDD);

        if (proyecto.getMetadata() != null) {
            recentManager.anadirReciente(proyecto.getMetadata().getRutaTPS());
        }

        ordenarProyectos();
        avisarProyectoCambiado();
        mostrarInfo("Proyecto cargado correctamente.");

        return proyecto;
    }

    /**
     * Carga una fuente de datos vinculada al proyecto actual.
     *
     * <p>Si la ruta llega vacía o nula, se descarga cualquier fuente anterior.</p>
     */
    public void cargarFuenteDatos(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            fuenteDatosActual = null;
            return;
        }

        datosVariablesManager.cargar(ruta).ifPresentOrElse(
                datos -> fuenteDatosActual = datos,
                () -> fuenteDatosActual = null
        );
    }

    /**
     * Devuelve la fuente de datos activa.
     *
     * @return fuente de datos cargada, o null si no hay ninguna vinculada.
     */
    public FuenteDatos getFuenteDatos() {
        return fuenteDatosActual;
    }

    /**
     * Guarda los cambios realizados sobre la fuente de datos activa.
     */
    public void guardarFuenteDatosActual() {
        if (proyectoActual != null
                && fuenteDatosActual != null
                && proyectoActual.getMetadata().getRutaBBDD() != null) {

            boolean exito = datosVariablesManager.guardar(
                    fuenteDatosActual,
                    proyectoActual.getMetadata().getRutaBBDD()
            );

            if (exito) {
                if (onNotificacion != null) {
                    onNotificacion.accept("info", "Base de datos actualizada correctamente.");
                }
            } else {
                if (onNotificacion != null) {
                    onNotificacion.accept("error", "Error al guardar los cambios en la base de datos.");
                }
            }
        }
    }

    /**
     * Carga proyectos desde el historial de recientes.
     *
     * @param maxProyectos 0 = ninguno, -1 = todos, N = número máximo de proyectos.
     */
    public void cargarProyectosRecientes(int maxProyectos) {
        if (maxProyectos == 0) {
            return;
        }

        List<String> recientes = recentManager.getRecientes();
        int limite = (maxProyectos < 0)
                ? recientes.size()
                : Math.min(maxProyectos, recientes.size());

        for (int i = 0; i < limite; i++) {
            File file = new File(recientes.get(i));

            if (!file.exists()) {
                continue;
            }

            Proyecto proyecto = fileManager.cargarProyecto(file);

            if (proyecto != null) {
                proyectos.add(proyecto);
            }
        }

        ordenarProyectos();
    }

    private void ordenarProyectos() {
        FXCollections.sort(proyectos, (p1, p2) ->
                p1.getNombre().compareToIgnoreCase(p2.getNombre())
        );
    }

    /**
     * Elimina un proyecto del historial de recientes, pero no borra sus archivos.
     */
    public void eliminarDeRecientes(Proyecto proyecto) {
        if (proyecto != null && proyecto.getMetadata() != null) {
            recentManager.eliminarReciente(proyecto.getMetadata().getRutaTPS());
        }
    }

    /**
     * Actualiza los datos principales de un proyecto y vuelve a guardarlo.
     */
    public boolean editarProyecto(Proyecto proyecto, ProyectoMetadata nuevaMetadata) {
        if (proyecto == null || nuevaMetadata == null) {
            return false;
        }

        proyecto.setNombre(nuevaMetadata.getNombre());
        proyecto.setMetadata(nuevaMetadata);
        proyecto.setOrientacion(nuevaMetadata.getOrientacion());

        String rutaBD = nuevaMetadata.getRutaBBDD();

        if (rutaBD != null && !rutaBD.isBlank()) {
            File bdFile = new File(rutaBD);

            if (bdFile.exists() && !esBDDentroDelProyecto(bdFile, nuevaMetadata)) {
                String rutaCopiada = fileManager.copiarBDAlProyecto(bdFile, nuevaMetadata);

                if (rutaCopiada != null) {
                    nuevaMetadata.setRutaBBDD(rutaCopiada);
                }
            }
        }

        boolean guardado = fileManager.guardarProyecto(proyecto, nuevaMetadata);

        if (guardado) {
            ordenarProyectos();
            avisarProyectoCambiado();
            mostrarInfo("Proyecto actualizado correctamente.");
            return true;
        }

        mostrarError("No se pudo actualizar el proyecto.");
        return false;
    }

    /**
     * Cierra un proyecto en la interfaz.
     *
     * <p>No elimina la carpeta ni el archivo .tps del disco.</p>
     */
    public void eliminarProyecto(Proyecto proyecto) {
        if (proyecto == null) {
            return;
        }

        eliminarDeRecientes(proyecto);
        proyectos.remove(proyecto);

        if (proyectoActual == proyecto) {
            proyectoActual = null;
        }

        avisarProyectoCambiado();
        mostrarInfo("Proyecto cerrado.");
    }

    /**
     * Duplica un proyecto creando una nueva estructura física y copiando sus recursos.
     */
    public Proyecto duplicarProyecto(Proyecto original) {
        if (original == null || original.getMetadata() == null) {
            return null;
        }

        ProyectoMetadata metaOriginal = original.getMetadata();
        File folderOriginal = new File(metaOriginal.getCarpetaProyecto());

        if (!folderOriginal.exists()) {
            mostrarError("La carpeta del proyecto original no existe.");
            return null;
        }

        try {
            ProyectoMetadata nuevaMeta = new ProyectoMetadata();
            nuevaMeta.setNombre(original.getNombre() + " (Copia)");
            nuevaMeta.setClienteInfo(metaOriginal.getClienteInfo());
            nuevaMeta.setUbicacion(metaOriginal.getUbicacion());

            String parent = folderOriginal.getParent();
            String nombreLimpio = original.getNombre().replaceAll("[^a-zA-Z0-9_\\-\\s]", "_");
            String nuevoNombreCarpeta = "TPS_" + nombreLimpio + "_Copia";
            File folderCopia = new File(parent, nuevoNombreCarpeta);

            int i = 1;
            while (folderCopia.exists()) {
                folderCopia = new File(parent, nuevoNombreCarpeta + "_" + i++);
            }

            nuevaMeta.setUbicacion(parent);

            if (!fileManager.crearEstructuraCarpetas(nuevaMeta)) {
                mostrarError("No se pudo crear la estructura para el duplicado.");
                return null;
            }

            copyDirectory(Paths.get(metaOriginal.getRutaFotos()), Paths.get(nuevaMeta.getRutaFotos()));
            copyDirectory(Paths.get(metaOriginal.getRutaFondos()), Paths.get(nuevaMeta.getRutaFondos()));

            if (metaOriginal.getRutaBBDD() != null
                    && metaOriginal.getRutaBBDD().contains(metaOriginal.getCarpetaProyecto())) {

                File bdOrig = new File(metaOriginal.getRutaBBDD());

                if (bdOrig.exists()) {
                    String bdCopiada = fileManager.copiarBDAlProyecto(bdOrig, nuevaMeta);
                    nuevaMeta.setRutaBBDD(bdCopiada);
                }
            } else {
                nuevaMeta.setRutaBBDD(metaOriginal.getRutaBBDD());
            }

            Proyecto copia = fileManager.cargarProyecto(new File(metaOriginal.getRutaTPS()));

            if (copia != null) {
                copia.setNombre(nuevaMeta.getNombre());
                copia.setMetadata(nuevaMeta);

                fileManager.guardarProyecto(copia, nuevaMeta);

                proyectos.add(copia);
                recentManager.anadirReciente(nuevaMeta.getRutaTPS());
                ordenarProyectos();

                mostrarInfo("Proyecto duplicado correctamente.");
                return copia;
            }

        } catch (Exception e) {
            e.printStackTrace();
            mostrarError("Error al duplicar: " + e.getMessage());
        }

        return null;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }

        Files.walk(source).forEach(s -> {
            try {
                Path d = target.resolve(source.relativize(s));

                if (Files.isDirectory(s)) {
                    if (!Files.exists(d)) {
                        Files.createDirectory(d);
                    }
                } else {
                    Files.copy(s, d, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }

            } catch (Exception e) {
                // Se ignoran errores aislados para no detener toda la copia.
                // El flujo principal ya informa si el duplicado completo falla.
            }
        });
    }

    /**
     * Guarda el proyecto activo en su archivo .tps.
     */
    public boolean guardarProyecto() {
        if (proyectoActual == null) {
            mostrarError("No hay proyecto activo para guardar.");
            return false;
        }

        ProyectoMetadata metadata = proyectoActual.getMetadata();

        if (metadata == null || metadata.getRutaTPS() == null) {
            mostrarError("Este proyecto no tiene ubicación en disco.\nCrea uno nuevo o usa 'Guardar como' (pendiente).");
            return false;
        }

        if (fileManager.guardarProyecto(proyectoActual, metadata)) {
            mostrarInfo("Proyecto guardado correctamente.");
            return true;
        }

        mostrarError("Error al guardar el proyecto.");
        return false;
    }

    public void exportarProyecto() {
        System.out.println("Funcionalidad de exportación pendiente...");
    }

    // =====================================================
    // Gestión de elementos
    // =====================================================

    /**
     * Añade un texto básico al proyecto actual.
     */
    public TextoElemento anadirTexto() {
        if (proyectoActual == null) {
            return null;
        }

        int num = proyectoActual.getElementosActuales().size() + 1;

        TextoElemento texto = new TextoElemento("Texto " + num, 50, 50);
        texto.setWidth(150);

        proyectoActual.getElementosActuales().add(texto);
        avisarElementoAnadido();

        return texto;
    }

    /**
     * Añade un código QR o código de barras al proyecto actual.
     */
    public ElementoCodigo anadirCodigo(TipoCodigo tipo) {
        if (proyectoActual == null) {
            return null;
        }

        int num = proyectoActual.getElementosActuales().size() + 1;
        String nombre = tipo.getNombre() + " " + num;

        ElementoCodigo codigo = new ElementoCodigo(nombre, 131, 88, tipo);

        proyectoActual.getElementosActuales().add(codigo);
        avisarElementoAnadido();

        return codigo;
    }

    /**
     * Añade una forma geométrica al proyecto actual.
     */
    public FormaElemento anadirForma(FormaElemento.TipoForma tipo) {
        if (proyectoActual == null) {
            return null;
        }

        String nombreBase = switch (tipo) {
            case RECTANGULO -> "Rectángulo ";
            case ELIPSE -> "Elipse ";
            case LINEA -> "Línea ";
        };

        FormaElemento forma = new FormaElemento(
                nombreBase + (proyectoActual.getElementosActuales().size() + 1),
                50,
                50,
                100,
                60,
                tipo
        );

        if (tipo == FormaElemento.TipoForma.LINEA) {
            forma.setHeight(4);
        }

        proyectoActual.getElementosActuales().add(forma);
        avisarElementoAnadido();

        return forma;
    }

    /**
     * Añade una imagen desde un archivo local.
     *
     * <p>Si el proyecto tiene estructura de carpetas, la imagen se copia dentro
     * del proyecto para mantenerlo portable.</p>
     */
    public ImagenElemento anadirImagenDesdeArchivo(File file) {
        if (proyectoActual == null || file == null) {
            return null;
        }

        try {
            ProyectoMetadata metadata = proyectoActual.getMetadata();

            if (metadata != null && metadata.getRutaFotos() != null) {
                String rutaRelativa = fileManager.copiarImagenAProyecto(file, metadata, false);

                if (rutaRelativa == null) {
                    mostrarErrorCargaImagen("Fallo al copiar imagen al repositorio del proyecto.");
                    return null;
                }

                Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaRelativa);
                Image img = ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

                int num = proyectoActual.getElementosActuales().size() + 1;
                ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50, rutaRelativa, img);

                proyectoActual.getElementosActuales().add(imgElem);
                fileManager.guardarProyecto(proyectoActual, metadata);

                avisarElementoAnadido();
                return imgElem;
            }

            Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());

            int num = proyectoActual.getElementosActuales().size() + 1;
            ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50, file.getAbsolutePath(), img);

            proyectoActual.getElementosActuales().add(imgElem);
            avisarElementoAnadido();

            return imgElem;

        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarErrorCargaImagen(ex.getMessage());
            return null;
        }
    }

    /**
     * Añade un placeholder de imagen.
     *
     * <p>Se usa especialmente para campos de foto vinculados a una fuente de datos.</p>
     */
    public ImagenElemento anadirImagenPlaceholder() {
        if (proyectoActual == null) {
            return null;
        }

        int num = proyectoActual.getElementosActuales().size() + 1;
        ImagenElemento imgElem = new ImagenElemento("Imagen " + num, 50, 50, null, null);

        imgElem.setWidth(82);
        imgElem.setHeight(106);

        if (fuenteDatosActual != null) {
            for (String columna : fuenteDatosActual.getColumnas()) {
                String upper = columna.toUpperCase();

                if (upper.equals("FOTO")
                        || upper.equals("FOTOS")
                        || upper.equals("IMAGEN")
                        || upper.equals("IMAGENES")) {

                    imgElem.setColumnaVinculada(columna);
                    break;
                }
            }
        }

        proyectoActual.getElementosActuales().add(imgElem);
        avisarElementoAnadido();

        return imgElem;
    }

    /**
     * Establece una imagen como fondo del frente o dorso actual.
     */
    public ImagenFondoElemento anadirFondoDesdeArchivo(File file, FondoFitMode fitMode) {
        if (proyectoActual == null || file == null || fitMode == null) {
            return null;
        }

        try {
            ProyectoMetadata metadata = proyectoActual.getMetadata();

            if (metadata != null && metadata.getRutaFondos() != null) {
                String sufijo = proyectoActual.isMostrandoFrente() ? "FRENTE" : "DORSO";
                String rutaRelativa = fileManager.copiarImagenAProyecto(file, metadata, true, sufijo);

                if (rutaRelativa == null) {
                    mostrarErrorCargaImagen("Error copiando fondo al proyecto.");
                    return null;
                }

                Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(rutaRelativa);
                Image img = ImageUtils.cargarImagenSinBloqueo(rutaAbsoluta.toAbsolutePath().toString());

                double cardW = (proyectoActual.getOrientacion() == Orientacion.VERTICAL)
                        ? EditorCanvasManager.CARD_HEIGHT
                        : EditorCanvasManager.CARD_WIDTH;

                double cardH = (proyectoActual.getOrientacion() == Orientacion.VERTICAL)
                        ? EditorCanvasManager.CARD_WIDTH
                        : EditorCanvasManager.CARD_HEIGHT;

                ImagenFondoElemento nuevoFondo = new ImagenFondoElemento(
                        rutaRelativa,
                        img,
                        cardW,
                        cardH,
                        fitMode
                );

                nuevoFondo.ajustarATamaño(
                        cardW,
                        cardH,
                        EditorCanvasManager.BLEED_MARGIN
                );

                proyectoActual.setFondoActual(nuevoFondo);
                fileManager.guardarProyecto(proyectoActual, metadata);

                avisarElementoAnadido();
                return nuevoFondo;
            }

            Image img = ImageUtils.cargarImagenSinBloqueo(file.getAbsolutePath());

            ImagenFondoElemento nuevoFondo = new ImagenFondoElemento(
                    file.getAbsolutePath(),
                    img,
                    EditorCanvasManager.CARD_WIDTH,
                    EditorCanvasManager.CARD_HEIGHT,
                    fitMode
            );

            nuevoFondo.ajustarATamaño(
                    EditorCanvasManager.CARD_WIDTH,
                    EditorCanvasManager.CARD_HEIGHT,
                    EditorCanvasManager.BLEED_MARGIN
            );

            proyectoActual.setFondoActual(nuevoFondo);
            avisarElementoAnadido();

            return nuevoFondo;

        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarErrorCargaImagen(ex.getMessage());
            return null;
        }
    }

    /**
     * Elimina un elemento del proyecto actual.
     */
    public boolean eliminarElemento(Elemento elemento) {
        if (proyectoActual == null || elemento == null) {
            return false;
        }

        boolean removed = proyectoActual.getElementosActuales().remove(elemento);

        if (removed) {
            avisarProyectoCambiado();
        }

        return removed;
    }

    // =====================================================
    // Diálogos y avisos
    // =====================================================

    private boolean confirmarReemplazoFondo() {
        Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reemplazar fondo");
        alert.setHeaderText("Ya existe un fondo en esta cara.");
        alert.setContentText("¿Quieres reemplazarlo por uno nuevo?");

        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void mostrarErrorCargaImagen(String mensaje) {
        Alert errorAlert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.ERROR);
        errorAlert.setTitle("Problema con la imagen");
        errorAlert.setHeaderText("No se pudo cargar la imagen");
        errorAlert.setContentText(mensaje);
        errorAlert.showAndWait();
    }

    private void mostrarError(String mensaje) {
        if (onNotificacion != null) {
            onNotificacion.accept("error", mensaje);
            return;
        }

        Alert alert = com.tpsstudio.util.AlertHelper.createAlert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Algo salió mal");
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void mostrarInfo(String mensaje) {
        if (onNotificacion != null) {
            onNotificacion.accept("info", mensaje);
        }
    }

    // =====================================================
    // Helpers internos
    // =====================================================

    private void avisarProyectoCambiado() {
        if (onProjectChanged != null) {
            onProjectChanged.run();
        }
    }

    private void avisarElementoAnadido() {
        if (onElementAdded != null) {
            onElementAdded.run();
        }
    }

    private boolean esBDDentroDelProyecto(File bdFile, ProyectoMetadata metadata) {
        if (metadata.getCarpetaProyecto() == null) {
            return false;
        }

        return bdFile.getAbsolutePath().startsWith(metadata.getCarpetaProyecto());
    }

    // =====================================================
    // Interfaces auxiliares
    // =====================================================

    @FunctionalInterface
    public interface FitModeProvider {
        FondoFitMode getFitMode();
    }
}