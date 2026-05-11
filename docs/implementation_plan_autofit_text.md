# Plan de Implementación: Texto Auto-Ajustable (Shrink-to-Fit)

Este plan describe la adición de una funcionalidad que permite que los elementos de texto mantengan un tamaño de caja fijo y reduzcan automáticamente el tamaño de la fuente para que el contenido (especialmente nombres largos) quepa sin desbordar ni forzar saltos de línea no deseados.

## User Review Required

> [!IMPORTANT]
> **Comportamiento de la Caja:** Cuando el modo "Auto-ajustar" esté activado, la caja de texto dejará de crecer automáticamente hacia la derecha o hacia abajo. El usuario definirá el tamaño del contenedor y TPS Studio calculará el tamaño de letra óptimo.
> ¿Es este el comportamiento deseado? (Caja fija, letra variable).

## Propuestas de Cambio

### 1. Modelo de Datos

#### [MODIFY] [TextoElemento.java](file:///c:/Users/Usuario/Documents/Grado%20S%20Programaci%C3%B3n%20-%20SEGUNDO/00%20PROYECTO%20FINAL/TPS_STUDIO_APP/TPS_Studio/tps-studio/src/main/java/com/tpsstudio/model/elements/TextoElemento.java)
- Añadir campo `private boolean autoAjustar` (default: `false`).
- Implementar getter y setter.

### 2. Motor de Renderizado (Editor)

#### [MODIFY] [EditorCanvasManager.java](file:///c:/Users/Usuario/Documents/Grado%20S%20Programaci%C3%B3n%20-%20SEGUNDO/00%20PROYECTO%20FINAL/TPS_STUDIO_APP/TPS_Studio/tps-studio/src/main/java/com/tpsstudio/view/managers/EditorCanvasManager.java)
- Modificar la sección de renderizado de texto para detectar el flag `autoAjustar`.
- **Lógica de cálculo:**
    - Si el texto excede el ancho (`ew`) o el alto (`eh`) con el tamaño de fuente actual:
        1. Calcular el factor de escala necesario: `factor = min(ew / anchoRequerido, eh / altoRequerido)`.
        2. Aplicar este factor al `fontSize` original para el dibujo actual.
- Desactivar el ajuste automático de `width`/`height` de la caja cuando este modo esté activo.

### 3. Interfaz de Usuario

#### [MODIFY] [PropertiesPanelController.java](file:///c:/Users/Usuario/Documents/Grado%20S%20Programaci%C3%B3n%20-%20SEGUNDO/00%20PROYECTO%20FINAL/TPS_STUDIO_APP/TPS_Studio/tps-studio/src/main/java/com/tpsstudio/view/managers/PropertiesPanelController.java)
- Añadir un `CheckBox` en el panel de propiedades de texto: **"Auto-ajustar (encoger)"**.
- Vincularlo bidireccionalmente con la propiedad del modelo.

### 4. Exportación PDF (Consistencia)

#### [MODIFY] [PDFExportService.java](file:///c:/Users/Usuario/Documents/Grado%20S%20Programaci%C3%B3n%20-%20SEGUNDO/00%20PROYECTO%20FINAL/TPS_STUDIO_APP/TPS_Studio/tps-studio/src/main/java/com/tpsstudio/service/PDFExportService.java)
- Replicar la lógica de cálculo de fuente dinámica para que el PDF generado coincida exactamente con lo que el usuario ve en el editor.

## Plan de Verificación

### Pruebas Manuales
1. Crear un elemento de texto con un nombre corto.
2. Activar "Auto-ajustar".
3. Escribir un nombre extremadamente largo (ej: "Felipe Juan Froilán de Todos los Santos de Marichalar y Borbón").
4. Verificar que la letra se hace pequeña automáticamente hasta caber en el recuadro sin salirse.
5. Cambiar el tamaño del recuadro (estirándolo) y verificar que la letra vuelve a crecer hasta su tamaño original (pero nunca superándolo).
6. Exportar a PDF y confirmar que el resultado es idéntico.
