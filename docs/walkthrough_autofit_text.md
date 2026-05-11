# Implementación de Auto-ajuste (Shrink-to-fit) en TPS Studio

Este documento detalla la implementación de la funcionalidad de **Auto-ajuste de texto** (también conocida como *shrink-to-fit*), diseñada para garantizar que el texto contenido en un elemento siempre quepa dentro de su caja delimitadora sin desbordar ni forzar saltos de línea.

## 🚀 Resumen de la Funcionalidad

La opción "Auto-ajustar al ancho" permite que, cuando un texto es más largo que el ancho disponible de su contenedor, el tamaño de la fuente se reduzca visualmente de forma proporcional hasta que el texto encaje perfectamente.

### Características Clave
- **Renderizado Visual Puro**: El tamaño de la fuente solo cambia durante el dibujado. No se modifica el `fontSize` real del modelo, lo que evita desconfiguraciones permanentes.
- **Alineación por la Base**: Aunque el texto se reduzca, se mantiene alineado por la línea de base (baseline) original, asegurando que varios elementos de texto sigan alineados verticalmente entre sí.
- **Exclusión Mutua**: Es incompatible con el "Salto de línea". Al activar una opción, la otra se desactiva automáticamente para evitar conflictos lógicos.
- **Consistencia en Exportación**: El PDF generado replica exactamente el escalado visual visto en el editor.

---

## 🛠️ Detalles de Implementación

### 1. Modelo (`TextoElemento.java`)
Se ha añadido una propiedad booleana `autoAjustar` con soporte para persistencia y compatibilidad retroactiva (manejo de nulos para proyectos antiguos).

```java
private Boolean autoAjustar; // null = false por defecto

public boolean isAutoAjustar() {
    return autoAjustar == null ? false : autoAjustar;
}
```

### 2. Interfaz de Usuario (`PropertiesPanelController.java`)
Se ha integrado un nuevo `CheckBox` en el panel de propiedades del texto.
- Incluye un **Tooltip** informativo.
- Implementa un sistema de **Notificación Toast** (`TPSToast`) para confirmar la activación al usuario.
- Gestiona la **exclusión mutua** con `chkSaltoLinea`.

### 3. Lógica de Renderizado (`EditorCanvasManager.java`)
El motor de renderizado calcula dinámicamente un `effectiveFontSize` local:
1. Mide el ancho del texto con el tamaño de fuente original.
2. Si el ancho supera el del elemento (`ew`), calcula un ratio: `ratio = ew / measuredWidth`.
3. Aplica el nuevo tamaño: `effectiveFontSize = originalFontSize * ratio` (con un mínimo de 4px).
4. **Alineación Vertical**: Se utiliza `ey + (originalFontSize * zoom)` para el posicionamiento, manteniendo la línea de base constante.

### 4. Exportación PDF (`PDFExportService.java`)
Se ha replicado exactamente la lógica de cálculo del `effectiveFontSize` en el motor de generación de PDF para garantizar la fidelidad del documento final respecto a lo diseñado en el lienzo.

---

## ⚠️ Consideraciones de Diseño
- **Prioridad de Salto de Línea**: El sistema de envoltorio de texto (wrap) se considera más importante para el diseño estructurado. El auto-ajuste es una herramienta para campos de longitud variable (como nombres o cargos) donde no se desea el salto de línea pero el espacio es limitado.
- **Precisión**: Se ha añadido una tolerancia del 2% en las validaciones de resolución de fondo relacionadas para evitar falsos positivos en el validador de diseño cuando los elementos están ajustados al límite.

---
*Documentación generada para el Proyecto Final de Grado - TPS Studio.*
