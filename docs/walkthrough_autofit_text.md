# Walkthrough: Texto Auto-Ajustable (Shrink-to-Fit)

Se ha implementado con éxito la capacidad de escalado automático de fuente para elementos de texto, una función crítica para manejar datos variables con longitudes impredecibles (como nombres y apellidos complejos).

## Cambios Realizados

### 1. Modelo y Persistencia
- Se ha añadido la propiedad `autoAjustar` a la clase `TextoElemento`.
- Esta propiedad se guarda y carga automáticamente con el proyecto (formato JSON).

### 2. Panel de Propiedades (UI)
- Se ha añadido el CheckBox **"Auto-ajustar (encoger texto)"** en la sección de texto del panel lateral.
- Incluye un **Tooltip explicativo** para guiar al usuario sobre su funcionamiento.

### 3. Motor de Renderizado (WYSIWYG)
- **EditorCanvasManager:** Ahora calcula un `effectiveFontSize` en tiempo real. Si el texto medido supera el ancho de la caja, se aplica una reducción proporcional inmediata.
- Se ha bloqueado el auto-crecimiento de la caja cuando este modo está activo, permitiendo que el usuario defina límites estrictos.

### 4. Exportación Profesional
- **PDFExportService:** Se ha sincronizado la lógica de renderizado para que el PDF de producción y las muestras de diseño A4 reflejen exactamente el mismo escalado de fuente que se ve en pantalla.

## Cómo Utilizarlo
1. Selecciona un elemento de texto vinculado a una base de datos (ej: el nombre del cliente).
2. Ajusta el ancho de la caja al máximo espacio disponible en tu diseño.
3. Activa la casilla **"Auto-ajustar (encoger texto)"**.
4. ¡Listo! Cualquier nombre, por largo que sea, se encogerá automáticamente para no salirse de la caja.

---
*Implementado por Antigravity para TPS Studio.*
