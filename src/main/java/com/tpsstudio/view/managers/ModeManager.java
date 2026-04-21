package com.tpsstudio.view.managers;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.elements.ImagenFondoElemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.enums.FondoFitMode;
import com.tpsstudio.model.project.FuenteDatos;
import com.tpsstudio.model.project.Proyecto;
import com.tpsstudio.model.project.ProyectoMetadata;
import com.tpsstudio.view.dialogs.EditarProyectoDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import com.tpsstudio.util.AnimationHelper;

/**
 * Gestor del modo de la interfaz de usuario (Diseño / Producción).
 *
 * <p>Controla qué contenido se muestra en los paneles laterales izquierdo y derecho
 * en función del modo {@link com.tpsstudio.model.enums.AppMode} activo:</p>
 * <ul>
 *   <li><b>DESIGN</b>: herramientas + capas en el panel izquierdo; propiedades
 *       del elemento seleccionado o datos variables en el panel derecho.</li>
 *   <li><b>PRODUCTION</b>: lista de proyectos recientes en el panel izquierdo;
 *       opciones de exportación en el panel derecho.</li>
 * </ul>
 *
 * <p><b>Patrón de comunicación:</b><br/>
 * Para mantener el desacoplamiento con {@link com.tpsstudio.view.controllers.MainViewController},
 * usa callbacks ({@link Runnable}, {@link java.util.function.Consumer}) que el
 * controlador principal registra mediante los métodos {@code setOn...()}.
 * Esto evita referencias circulares y facilita la trazabilidad del flujo de eventos.</p>
 *
 * <p><b>Animaciones:</b><br/>
 * Al cambiar de modo, aplica {@link javafx.animation.FadeTransition} sobre los paneles
 * para una transición visual fluida, mejorando la experiencia de usuario.</p>
 *
 * @see com.tpsstudio.view.controllers.MainViewController
 * @see com.tpsstudio.model.enums.AppMode
 * @see PropertiesPanelController
 */
public class ModeManager {

    // Estado actual (Diseño / Producción)
    private AppMode currentMode;

    // Contenedores físicos de UI (se rellenan dinámicamente)
    private final VBox leftPanel;
    private final VBox rightPanel;

    // Controlador del panel de propiedades (solo aplica en modo Diseño)
    private final PropertiesPanelController propertiesPanelController;

    // Referencia opcional al ProjectManager (solo si se usa el helper interno)
    private com.tpsstudio.service.ProjectManager projectManager;

    // Callbacks hacia el controlador principal (MainViewController)
    private Runnable onAddText;
    private Runnable onAddImage;
    private Runnable onAddBackground;
    private Runnable onNewCR80;
    private Runnable onExport;

    private Consumer<Elemento> onElementSelected;
    private Consumer<Proyecto> onProjectSelected;
    private Consumer<Proyecto> onEditProject;

    private Consumer<ImagenFondoElemento> onEditExternal;
    private Consumer<ImagenFondoElemento> onReload;
    private Consumer<Elemento> onToggleLock;

    private Runnable onValidateDesign;
    private Runnable onCanvasRedraw;

    // Nuevo callback para añadir formas
    private java.util.function.Consumer<com.tpsstudio.model.elements.FormaElemento.TipoForma> onAddShape;

    // Se guarda para poder refrescar solo la parte de "Capas" sin rehacer todo el
    // panel izquierdo
    private VBox layersPanel;

    // Panel de datos variables (null si no hay fuente de datos activa)
    private VBox datosPanel;

    // Indicador de qué "pestaña" del panel derecho está activa
    private boolean isPropertiesActive = true;
    private javafx.scene.Node propertiesNode;
    private javafx.scene.Node datosNode;

    // Estado de expansión del submenú de formas
    private boolean shapesExpanded = false;

    public ModeManager(VBox leftPanel, VBox rightPanel, PropertiesPanelController propertiesPanelController) {
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.propertiesPanelController = propertiesPanelController;
        this.currentMode = AppMode.DESIGN;
    }

    // ===================== SETTERS DE CALLBACKS =====================

    public void setOnAddText(Runnable callback) {
        this.onAddText = callback;
    }

    public void setOnAddImage(Runnable callback) {
        this.onAddImage = callback;
    }

    public void setOnAddBackground(Runnable callback) {
        this.onAddBackground = callback;
    }

    public void setOnNewCR80(Runnable callback) {
        this.onNewCR80 = callback;
    }

    public void setOnExport(Runnable callback) {
        this.onExport = callback;
    }

    public void setOnElementSelected(Consumer<Elemento> callback) {
        this.onElementSelected = callback;
    }

    public void setOnProjectSelected(Consumer<Proyecto> callback) {
        this.onProjectSelected = callback;
    }

    public void setOnEditProject(Consumer<Proyecto> callback) {
        this.onEditProject = callback;
    }

    public void setOnEditExternal(Consumer<ImagenFondoElemento> callback) {
        this.onEditExternal = callback;
    }

    public void setOnReload(Consumer<ImagenFondoElemento> callback) {
        this.onReload = callback;
    }

    public void setOnToggleLock(Consumer<Elemento> callback) {
        this.onToggleLock = callback;
    }

    public void setOnValidateDesign(Runnable callback) {
        this.onValidateDesign = callback;
    }

    public void setOnAddShape(java.util.function.Consumer<com.tpsstudio.model.elements.FormaElemento.TipoForma> callback) {
        this.onAddShape = callback;
    }

    public void setOnCanvasRedraw(Runnable callback) {
        this.onCanvasRedraw = callback;
    }

    public void setProjectManager(com.tpsstudio.service.ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    // ===================== MODO ACTUAL =====================

    public AppMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Cambia el modo de la interfaz y reconstruye los paneles laterales.
     * Nota: el canvas se repinta al final para evitar inconsistencias visuales.
     */
    public void switchMode(AppMode newMode, Proyecto proyecto, Elemento selectedElement,
            ObservableList<Proyecto> projects) {
        this.currentMode = newMode;

        // Limpieza completa de paneles
        leftPanel.getChildren().clear();
        rightPanel.getChildren().clear();

        // Reconstrucción según modo
        if (newMode == AppMode.DESIGN) {
            buildDesignModePanels(proyecto, selectedElement);
        } else {
            buildProductionModePanels(projects, proyecto);
        }

        // Animación suave de transición
        javafx.animation.FadeTransition fadeLeft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(550), leftPanel);
        fadeLeft.setFromValue(0.3);
        fadeLeft.setToValue(1.0);
        fadeLeft.play();

        javafx.animation.FadeTransition fadeRight = new javafx.animation.FadeTransition(javafx.util.Duration.millis(550), rightPanel);
        fadeRight.setFromValue(0.3);
        fadeRight.setToValue(1.0);
        fadeRight.play();

        // Repintado final (por seguridad)
        if (onCanvasRedraw != null) {
            onCanvasRedraw.run();
        }
    }

    // ===================== MODO DISEÑO =====================

    public void setRightPanelTabActiva(boolean isProperties) {
        if (this.isPropertiesActive == isProperties) return; // Evitar disparar animaciones si no cambia
        
        this.isPropertiesActive = isProperties;
        if (currentMode == AppMode.DESIGN) {
            javafx.scene.Node targetNode = isPropertiesActive ? propertiesNode : datosNode;
            
            if (targetNode != null) {
                // Animación suave de cambio de pestaña (Cross-fade)
                javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(350), rightPanel);
                fade.setFromValue(0.4);
                fade.setToValue(1.0);
                
                rightPanel.getChildren().setAll(targetNode);
                fade.play();
            }
        }
    }

    private void buildDesignModePanels(Proyecto proyecto, Elemento selectedElement) {
        // Panel izquierdo: herramientas + capas
        VBox toolbox = buildToolboxPanel();
        layersPanel = buildLayersPanel(proyecto, selectedElement);
        leftPanel.getChildren().addAll(toolbox, new Separator(), layersPanel);

        // Panel derecho: Nodos para "Propiedades" y "Datos Variables"

        // 1. Nodo de Propiedades
        VBox properties = propertiesPanelController.buildPanel(selectedElement, proyecto);
        ScrollPane scrollProps = new ScrollPane(properties);
        scrollProps.setFitToWidth(true);
        scrollProps.getStyleClass().add("panel-scroll-view");
        this.propertiesNode = scrollProps;

        // 2. Nodo de Datos Variables
        if (projectManager != null && projectManager.getFuenteDatos() != null) {
            datosPanel = buildDatosVariablesPanel(projectManager.getFuenteDatos(), proyecto);
            // Sin ScrollPane extra: el VBox tiene vgrow=ALWAYS y la TextArea lleva scroll
            // interno
            VBox.setVgrow(datosPanel, Priority.ALWAYS);
            this.datosNode = datosPanel;
        } else {
            datosPanel = buildEmptyDatosVariablesPanel(proyecto);
            this.datosNode = datosPanel;
        }

        // Mostrar el nodo activo según el botón clickeado
        if (isPropertiesActive) {
            rightPanel.getChildren().setAll(propertiesNode);
        } else {
            rightPanel.getChildren().setAll(datosNode);
        }
    }

    /**
     * Refresca solo el panel de capas. Útil cuando se añaden/eliminan elementos o
     * cambia el lado (frente/dorso).
     */
    public void refreshLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        if (layersPanel == null)
            return;

        VBox newLayers = buildLayersPanel(proyecto, selectedElement);

        int index = leftPanel.getChildren().indexOf(layersPanel);
        if (index != -1) {
            leftPanel.getChildren().set(index, newLayers);
            layersPanel = newLayers;
        }
    }

    private VBox buildToolboxPanel() {
        VBox toolbox = new VBox(8);
        toolbox.setPadding(new Insets(12));

        Label lblToolbox = new Label("Herramientas");
        lblToolbox.getStyleClass().add("panel-title");

        Button btnTexto = new Button("T Texto");
        btnTexto.setMaxWidth(Double.MAX_VALUE);
        btnTexto.getStyleClass().add("toolbox-btn");
        btnTexto.setOnAction(e -> {
            if (onAddText != null)
                onAddText.run();
        });

        Button btnImagen = new Button("🖼 Imagen");
        btnImagen.setMaxWidth(Double.MAX_VALUE);
        btnImagen.getStyleClass().add("toolbox-btn");
        btnImagen.setOnAction(e -> {
            if (onAddImage != null)
                onAddImage.run();
        });

        Button btnFondo = new Button("🎨 Fondo");
        btnFondo.setMaxWidth(Double.MAX_VALUE);
        btnFondo.getStyleClass().add("toolbox-btn");
        btnFondo.setOnAction(e -> {
            if (onAddBackground != null)
                onAddBackground.run();
        });

        // Contenedor para el submenú de formas (tipo acordeón)
        VBox shapesSubMenu = new VBox(4);
        shapesSubMenu.setPadding(new Insets(0, 0, 0, 15)); // Sangría para parecer submenú
        shapesSubMenu.setVisible(shapesExpanded);
        shapesSubMenu.setManaged(shapesExpanded);

        Button btnRectangulo = new Button("▭ Rectángulo");
        btnRectangulo.setMaxWidth(Double.MAX_VALUE);
        btnRectangulo.getStyleClass().add("toolbox-btn");
        btnRectangulo.setOnAction(e -> {
            if (onAddShape != null) onAddShape.accept(com.tpsstudio.model.elements.FormaElemento.TipoForma.RECTANGULO);
        });

        Button btnElipse = new Button("○ Elipse");
        btnElipse.setMaxWidth(Double.MAX_VALUE);
        btnElipse.getStyleClass().add("toolbox-btn");
        btnElipse.setOnAction(e -> {
            if (onAddShape != null) onAddShape.accept(com.tpsstudio.model.elements.FormaElemento.TipoForma.ELIPSE);
        });

        Button btnLinea = new Button("― Línea");
        btnLinea.setMaxWidth(Double.MAX_VALUE);
        btnLinea.getStyleClass().add("toolbox-btn");
        btnLinea.setOnAction(e -> {
            if (onAddShape != null) onAddShape.accept(com.tpsstudio.model.elements.FormaElemento.TipoForma.LINEA);
        });

        shapesSubMenu.getChildren().addAll(btnRectangulo, btnElipse, btnLinea);

        // Botón principal que despliega el submenú
        Button btnToggleFormas = new Button(shapesExpanded ? "▼ Dibujar Forma" : "▶ Dibujar Forma");
        btnToggleFormas.setMaxWidth(Double.MAX_VALUE);
        btnToggleFormas.getStyleClass().add("toolbox-btn");
        btnToggleFormas.setOnAction(e -> {
            shapesExpanded = !shapesExpanded;
            btnToggleFormas.setText(shapesExpanded ? "▼ Dibujar Forma" : "▶ Dibujar Forma");
            
            // Usar el helper de animación para una transición suave
            AnimationHelper.animateAccordion(shapesSubMenu, shapesExpanded);
        });

        Button btnValidar = new Button("✓ Validar Diseño");
        btnValidar.setMaxWidth(Double.MAX_VALUE);
        btnValidar.getStyleClass().add("primary-btn");
        btnValidar.setOnAction(e -> {
            if (onValidateDesign != null)
                onValidateDesign.run();
        });

        // Organizar: El submenú aparece justo debajo de su botón
        toolbox.getChildren().addAll(
                lblToolbox,
                btnTexto,
                btnImagen,
                btnFondo,
                btnToggleFormas,
                shapesSubMenu,
                new Separator(),
                btnValidar
        );
        return toolbox;
    }

    /**
     * Construye la lista de capas (fondo + elementos). El fondo se muestra arriba
     * si existe.
     */
    private VBox buildLayersPanel(Proyecto proyecto, Elemento selectedElement) {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12));
        VBox.setVgrow(panel, Priority.ALWAYS); // Hacer que el panel de capas ocupe el resto del VBox

        Label lblCapas = new Label("Capas");
        lblCapas.getStyleClass().add("panel-title");

        ListView<Elemento> listCapas = new ListView<>();
        listCapas.getStyleClass().add("project-list");
        listCapas.setPrefHeight(120); // Altura mínima preferida más pequeña
        VBox.setVgrow(listCapas, Priority.ALWAYS); // Que la lista sea lo que se estire y use scroll

        if (proyecto != null) {
            ObservableList<Elemento> allElements = FXCollections.observableArrayList();

            // Fondo (si hay) + elementos del lado actual
            ImagenFondoElemento fondo = proyecto.getFondoActual();
            if (fondo != null)
                allElements.add(fondo);
            allElements.addAll(proyecto.getElementosActuales());

            listCapas.setItems(allElements);

            // Si nos pasan el elemento seleccionado, intentamos reflejarlo en la lista
            if (selectedElement != null) {
                listCapas.getSelectionModel().select(selectedElement);
            }

            listCapas.setCellFactory(lv -> new ListCell<Elemento>() {
                @Override
                protected void updateItem(Elemento item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        setText(null);
                        setContextMenu(null);
                        return;
                    }

                    String lockIcon = item.isLocked() ? "🔒 " : "";
                    setText(lockIcon + item.toString());

                    // Menú contextual según tipo
                    ContextMenu contextMenu = new ContextMenu();

                    if (item instanceof ImagenFondoElemento) {
                        MenuItem menuEditar = new MenuItem("Editar imagen externa...");
                        menuEditar.setOnAction(e -> {
                            if (onEditExternal != null)
                                onEditExternal.accept((ImagenFondoElemento) item);
                        });

                        MenuItem menuRecargar = new MenuItem("Recargar desde disco");
                        menuRecargar.setOnAction(e -> {
                            if (onReload != null)
                                onReload.accept((ImagenFondoElemento) item);
                        });

                        MenuItem menuLock = new MenuItem(item.isLocked() ? "Desbloquear" : "Bloquear");
                        menuLock.setOnAction(e -> {
                            if (onToggleLock != null)
                                onToggleLock.accept(item);
                            // Esto ayuda a que el icono 🔒 se vea actualizado sin tener que reconstruir
                            // paneles
                            listCapas.refresh();
                        });

                        contextMenu.getItems().addAll(menuEditar, menuRecargar, new SeparatorMenuItem(), menuLock);

                    } else {
                        MenuItem menuEliminar = new MenuItem("Eliminar");
                        menuEliminar.setOnAction(e -> {
                            proyecto.getElementosActuales().remove(item);

                            // Si borramos lo seleccionado, limpiamos selección en UI
                            if (onElementSelected != null)
                                onElementSelected.accept(null);

                            // Refrescamos la lista (y normalmente el canvas se repinta vía controller)
                            listCapas.refresh();
                        });

                        contextMenu.getItems().add(menuEliminar);
                    }

                    setContextMenu(contextMenu);
                }
            });

            // Selección de capas -> se lo comunicamos al controlador
            listCapas.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
                if (onElementSelected != null)
                    onElementSelected.accept(newVal);
            });
        }

        panel.getChildren().addAll(lblCapas, listCapas);
        return panel;
    }

    /* Construye el panel de navegación y vista del registro activo. */
    private VBox buildDatosVariablesPanel(FuenteDatos datos, Proyecto proyecto) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        VBox.setVgrow(panel, Priority.ALWAYS);

        // Cabecera
        Label lblTitulo = new Label(datos.getNombreArchivo());
        lblTitulo.getStyleClass().add("panel-title");
        lblTitulo.setMaxWidth(Double.MAX_VALUE);
        lblTitulo.setWrapText(false);

        Button btnCambiarBD = new Button("⚙ Cambiar BD...");
        btnCambiarBD.getStyleClass().add("toolbox-btn");
        btnCambiarBD.setMaxWidth(Double.MAX_VALUE);
        btnCambiarBD.setOnAction(e -> {
            if (onEditProject != null && proyecto != null)
                onEditProject.accept(proyecto);
        });

        // Contador
        Label lblContador = new Label(calcularContador(datos));
        lblContador.getStyleClass().add("toolbar-label");

        // Navegación
        Button btnAnterior = new Button("◄ Anterior");
        btnAnterior.getStyleClass().add("toolbox-btn");
        btnAnterior.setMaxWidth(Double.MAX_VALUE);
        btnAnterior.setDisable(!datos.tieneRegistros() || datos.getIndiceActual() <= 0);

        Button btnSiguiente = new Button("Siguiente ►");
        btnSiguiente.getStyleClass().add("toolbox-btn");
        btnSiguiente.setMaxWidth(Double.MAX_VALUE);
        btnSiguiente.setDisable(!datos.tieneRegistros() || datos.getIndiceActual() >= datos.getTotalRegistros() - 1);

        HBox navBox = new HBox(8, btnAnterior, btnSiguiente);
        navBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(btnAnterior, Priority.ALWAYS);
        HBox.setHgrow(btnSiguiente, Priority.ALWAYS);

        // Vista de registro: pares COLUMNA → VALOR dentro de un ScrollPane
        VBox vistaRegistro = construirVistaRegistro(datos);

        ScrollPane scrollRegistro = new ScrollPane(vistaRegistro);
        scrollRegistro.setFitToWidth(true);
        scrollRegistro.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollRegistro.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollRegistro.getStyleClass().add("panel-scroll-view");
        VBox.setVgrow(scrollRegistro, Priority.ALWAYS);

        btnAnterior.setOnAction(e -> {
            datos.anterior();
            lblContador.setText(calcularContador(datos));
            btnAnterior.setDisable(datos.getIndiceActual() <= 0);
            btnSiguiente.setDisable(datos.getIndiceActual() >= datos.getTotalRegistros() - 1);
            actualizarVistaRegistro(vistaRegistro, datos);
            if (onCanvasRedraw != null)
                onCanvasRedraw.run();
        });

        btnSiguiente.setOnAction(e -> {
            datos.siguiente();
            lblContador.setText(calcularContador(datos));
            btnAnterior.setDisable(datos.getIndiceActual() <= 0);
            btnSiguiente.setDisable(datos.getIndiceActual() >= datos.getTotalRegistros() - 1);
            actualizarVistaRegistro(vistaRegistro, datos);
            if (onCanvasRedraw != null)
                onCanvasRedraw.run();
        });

        panel.getChildren().addAll(
                lblTitulo,
                btnCambiarBD,
                new Separator(),
                lblContador,
                navBox,
                new Separator(),
                scrollRegistro);

        return panel;
    }

    /* Crea el contenedor de pares COLUMNA/VALOR para el registro inicial. */
    private VBox construirVistaRegistro(FuenteDatos datos) {
        VBox contenedor = new VBox(6);
        contenedor.setPadding(new Insets(4, 0, 4, 0));
        rellenarVistaRegistro(contenedor, datos);
        return contenedor;
    }

    /* Limpia y vuelve a dibujar los pares tras un cambio de registro. */
    private void actualizarVistaRegistro(VBox contenedor, FuenteDatos datos) {
        contenedor.getChildren().clear();
        rellenarVistaRegistro(contenedor, datos);
    }

    /*
     * Añade al contenedor un bloque por cada columna con su valor en el registro
     * actual.
     */
    private void rellenarVistaRegistro(VBox contenedor, FuenteDatos datos) {
        Map<String, String> registro = datos.getRegistroActual();

        if (registro == null) {
            Label lblVacio = new Label("(sin registros)");
            lblVacio.getStyleClass().add("panel-placeholder");
            contenedor.getChildren().add(lblVacio);
            return;
        }

        for (String columna : datos.getColumnas()) {
            String valor = registro.getOrDefault(columna, "");

            Label lblColumna = new Label(columna);
            lblColumna.getStyleClass().add("dato-columna");
            lblColumna.setMaxWidth(Double.MAX_VALUE);

            Label lblValor = new Label(valor.isEmpty() ? "—" : valor);
            lblValor.getStyleClass().add("dato-valor");
            lblValor.setMaxWidth(Double.MAX_VALUE);
            lblValor.setWrapText(true);

            VBox campo = new VBox(2, lblColumna, lblValor);
            campo.setPadding(new Insets(6, 10, 6, 10));
            campo.getStyleClass().add("dato-campo");
            campo.setMaxWidth(Double.MAX_VALUE);

            contenedor.getChildren().add(campo);
        }
    }

    private VBox buildEmptyDatosVariablesPanel(Proyecto proyecto) {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(30));
        panel.setAlignment(Pos.CENTER);

        Label lblMensaje = new Label("No hay base de datos vinculada");
        lblMensaje.getStyleClass().add("panel-placeholder");

        Button btnVincular = new Button("+ Vincular Base de Datos");
        btnVincular.getStyleClass().add("primary-btn");
        btnVincular.setOnAction(e -> {
            if (onEditProject != null && proyecto != null) {
                onEditProject.accept(proyecto);
            }
        });

        panel.getChildren().addAll(lblMensaje, btnVincular);
        return panel;
    }

    private String calcularContador(FuenteDatos datos) {
        if (!datos.tieneRegistros())
            return "(sin registros)";
        return "Registro " + datos.getPosicionActual() + " / " + datos.getTotalRegistros();
    }

    // ===================== MODO PRODUCCIÓN =====================

    private void buildProductionModePanels(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = buildProjectListPanel(projects, currentProject);
        leftPanel.getChildren().add(projectPanel);

        VBox exportPanel = buildExportPanel();
        exportPanel.getStyleClass().add("panel-dark-bg");

        ScrollPane scrollPane = new ScrollPane(exportPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("panel-scroll-view");
        scrollPane.setPadding(Insets.EMPTY);

        rightPanel.getChildren().add(scrollPane);
    }

    private VBox buildProjectListPanel(ObservableList<Proyecto> projects, Proyecto currentProject) {
        VBox projectPanel = new VBox(8);
        projectPanel.setPadding(new Insets(12));

        Label lblTrabajos = new Label("Trabajos");
        lblTrabajos.getStyleClass().add("panel-title");

        ListView<Proyecto> listProyectos = new ListView<>();
        listProyectos.setItems(projects);
        listProyectos.getStyleClass().add("project-list");
        listProyectos.setPrefHeight(400);

        // Si hay proyecto actual, lo seleccionamos al abrir el modo producción
        if (currentProject != null) {
            listProyectos.getSelectionModel().select(currentProject);
        }

        listProyectos.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && onProjectSelected != null)
                onProjectSelected.accept(newVal);
        });

        // Doble click para editar
        listProyectos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Proyecto seleccionado = listProyectos.getSelectionModel().getSelectedItem();
                if (seleccionado != null && seleccionado.getMetadata() != null) {
                    if (onEditProject != null) {
                        // Lo normal: que el MainViewController gestione el flujo
                        onEditProject.accept(seleccionado);
                    } else {
                        // Alternativa: si no hay callback, tiramos del helper interno (si hay
                        // projectManager)
                        abrirDialogoEditarProyecto(seleccionado);
                    }
                }
            }
        });

        Button btnNuevoCR80 = new Button("+ Nuevo CR80");
        btnNuevoCR80.getStyleClass().add("primary-btn");
        btnNuevoCR80.setMaxWidth(Double.MAX_VALUE);
        btnNuevoCR80.setOnAction(e -> {
            if (onNewCR80 != null)
                onNewCR80.run();
        });

        projectPanel.getChildren().addAll(lblTrabajos, listProyectos, btnNuevoCR80);
        return projectPanel;
    }

    /**
     * Helper interno por si se quiere que ModeManager resuelva el diálogo.
     * Si usas callbacks (lo ideal), este método puede quedarse sin usar.
     */
    private void abrirDialogoEditarProyecto(Proyecto proyecto) {
        if (projectManager == null)
            return;

        EditarProyectoDialog dialog = new EditarProyectoDialog(proyecto, null);
        Optional<ProyectoMetadata> resultado = dialog.showAndWait();

        if (dialog.isEliminarProyecto()) {
            projectManager.eliminarProyecto(proyecto);
            return;
        }

        if (resultado.isPresent()) {
            projectManager.editarProyecto(proyecto, resultado.get());
        }
    }

    /**
     * Panel de exportación. De momento son placeholders, pero deja el hueco
     * preparado.
     */
    private VBox buildExportPanel() {
        VBox exportPanel = new VBox(15);
        exportPanel.setPadding(new Insets(30));
        exportPanel.setFillWidth(true);

        Label lblExport = new Label("Exportación");
        lblExport.getStyleClass().add("panel-title");

        Label lblInfoExp = new Label("Formato: PNG/PDF (pendiente)");
        lblInfoExp.getStyleClass().add("panel-placeholder");

        Label lblDpi = new Label("DPI: 300 (pendiente)");
        lblDpi.getStyleClass().add("panel-placeholder");

        Label lblGuias = new Label("Incluir guías: No (pendiente)");
        lblGuias.getStyleClass().add("panel-placeholder");

        Label lblSide = new Label("Exportar: Frente (pendiente)");
        lblSide.getStyleClass().add("panel-placeholder");

        Button btnDoExport = new Button("Exportar");
        btnDoExport.getStyleClass().add("success-btn");
        btnDoExport.setMaxWidth(200.0);
        btnDoExport.setOnAction(e -> {
            if (onExport != null)
                onExport.run();
        });

        exportPanel.getChildren().addAll(
                lblExport,
                lblInfoExp,
                lblDpi,
                lblGuias,
                lblSide,
                new Separator(),
                btnDoExport);

        return exportPanel;
    }
}
