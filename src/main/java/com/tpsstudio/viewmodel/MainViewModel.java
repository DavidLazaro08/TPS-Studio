package com.tpsstudio.viewmodel;

import com.tpsstudio.model.elements.Elemento;
import com.tpsstudio.model.enums.AppMode;
import com.tpsstudio.model.project.Proyecto;
import javafx.beans.property.*;

/* ViewModel principal de la aplicación.
 * Centraliza el estado observable de la interfaz, permitiendo data binding real.
 * Los controllers leen y escriben aquí; los cambios se propagan automáticamente. */

public class MainViewModel {

    // Texto de barra de estado
    private final StringProperty statusText = new SimpleStringProperty("TPS Studio listo");

    // Modo activo (Diseño / Producción)
    private final ObjectProperty<AppMode> currentMode = new SimpleObjectProperty<>(AppMode.PRODUCTION);

    // Proyecto cargado actualmente
    private final ObjectProperty<Proyecto> proyectoActual = new SimpleObjectProperty<>(null);

    // Elemento seleccionado en el canvas
    private final ObjectProperty<Elemento> elementoSeleccionado = new SimpleObjectProperty<>(null);

    // Nivel de zoom actual
    private final DoubleProperty zoomLevel = new SimpleDoubleProperty(1.5);

    // Visibilidad de guías de troquel
    private final BooleanProperty mostrarGuias = new SimpleBooleanProperty(true);

    // Estado del chip de proyecto (Colapsado/Expandido)
    private final BooleanProperty projectChipCollapsed = new SimpleBooleanProperty(true);

    // =====================================================
    // statusText
    // =====================================================

    public StringProperty statusTextProperty()     { return statusText; }
    public String getStatusText()                  { return statusText.get(); }
    public void   setStatusText(String text)       { statusText.set(text); }

    // =====================================================
    // currentMode
    // =====================================================

    public ObjectProperty<AppMode> currentModeProperty()    { return currentMode; }
    public AppMode getCurrentMode()                          { return currentMode.get(); }
    public void    setCurrentMode(AppMode mode)              { currentMode.set(mode); }

    // =====================================================
    // proyectoActual
    // =====================================================

    public ObjectProperty<Proyecto> proyectoActualProperty() { return proyectoActual; }
    public Proyecto getProyectoActual()                       { return proyectoActual.get(); }
    public void     setProyectoActual(Proyecto proyecto)      { proyectoActual.set(proyecto); }

    // =====================================================
    // elementoSeleccionado
    // =====================================================

    public ObjectProperty<Elemento> elementoSeleccionadoProperty() { return elementoSeleccionado; }
    public Elemento getElementoSeleccionado()                       { return elementoSeleccionado.get(); }
    public void     setElementoSeleccionado(Elemento elemento)      { elementoSeleccionado.set(elemento); }

    // =====================================================
    // zoomLevel
    // =====================================================

    public DoubleProperty zoomLevelProperty()   { return zoomLevel; }
    public double getZoomLevel()                { return zoomLevel.get(); }
    public void   setZoomLevel(double zoom)     { zoomLevel.set(zoom); }

    // =====================================================
    // mostrarGuias
    // =====================================================

    public BooleanProperty mostrarGuiasProperty() { return mostrarGuias; }
    public boolean isMostrarGuias()              { return mostrarGuias.get(); }
    public void    setMostrarGuias(boolean show) { mostrarGuias.set(show); }

    // =====================================================
    // projectChipCollapsed
    // =====================================================

    public BooleanProperty projectChipCollapsedProperty() { return projectChipCollapsed; }
    public boolean isProjectChipCollapsed()               { return projectChipCollapsed.get(); }
    public void    setProjectChipCollapsed(boolean b)      { projectChipCollapsed.set(b); }
}
