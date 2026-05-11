# Documentación de Herramientas de Diseño Avanzadas — TPS Studio

Esta documentación detalla el funcionamiento y la implementación de las herramientas de precisión añadidas recientemente al editor de TPS Studio.

## 1. Herramienta de Cuentagotas (Eyedropper)

El Cuentagotas permite capturar colores exactos directamente desde cualquier punto del lienzo (canvas) para aplicarlos a rellenos o bordes de elementos.

### Funcionamiento Técnico
- **Clase Principal:** `EditorCanvasManager.java` gestiona el estado `eyedropperActive`.
- **Captura de Píxel:** Utiliza el método `canvas.snapshot()` para obtener una imagen en tiempo real de lo que se ve en pantalla. Al hacer clic, el `PixelReader` extrae el color exacto en las coordenadas `(x, y)` del ratón.
- **Flujo de Usuario:**
    1. El usuario pulsa el botón ⌖ en el panel de propiedades.
    2. El botón entra en estado de **Pulso (Heartbeat)** y cambia a azul profundo.
    3. Aparece un **Toast (Aviso)** verde en pantalla: "Modo Cuentagotas Activado".
    4. El cursor cambia a una **Mira (Crosshair)**.
    5. Al hacer clic en el lienzo, el color se aplica al selector y el modo se desactiva automáticamente.

### Feedback Visual
- **Animación de Pulso:** Implementada mediante `ScaleTransition` en `PropertiesPanelController.java`, creando un efecto rítmico mientras la herramienta espera el clic.
- **Notificaciones (Toast):** Integradas con `TPSToast.java` para mantener la coherencia con el sistema de guardado de la aplicación.

---

## 2. Redondeo de Bordes (Radio de Curvatura)

El redondeo de bordes permite transformar rectángulos rígidos en formas suavizadas o botones circulares.

### Implementación en el Modelo
- **Propiedad:** `radioCurvatura` (tipo `double`) en la clase `FormaElemento`.
- **Rango:** De 0 a 200 píxeles.

### Renderizado en el Canvas
En `EditorCanvasManager.dibujarCanvas()`, se utiliza el método nativo de JavaFX `gc.fillRoundRect()` y `gc.strokeRoundRect()`.
```java
double arc = forma.getRadioCurvatura() * zoomLevel;
gc.fillRoundRect(ex, ey, ew, eh, arc, arc);
```
*Nota: El valor del radio se escala automáticamente con el nivel de zoom para que la previsualización sea siempre fiel al tamaño real de la tarjeta.*

### Interfaz de Usuario
- **Control:** Un `Spinner<Double>` en el panel de propiedades permite ajustar el radio con precisión de 1px.
- **Interactividad:** El cambio se refleja instantáneamente en el lienzo mediante el sistema de invalidación de caché de elementos.

---

## 3. Control de Opacidad (Alpha)

Todos los elementos de forma soportan transparencia ajustable.

### Funcionamiento
- **Propiedad:** `opacidad` (0.0 a 1.0) en `Elemento.java`.
- **Renderizado:** Se aplica mediante `gc.setGlobalAlpha(forma.getOpacidad())` antes de dibujar el elemento y se restaura inmediatamente después.
- **Utilidad:** Permite crear efectos de superposición y marcas de agua sutiles sin ocultar los elementos del fondo.

---

## 4. Notas de Estilo y Consistencia
- **Paleta de Colores:** Todas las herramientas utilizan los colores definidos en `_theme.css` (ej. `tps-accent-deep` para estados activos).
- **Consistencia Visual:** Los botones de herramientas de diseño comparten el mismo padding, radio de borde y efectos de hover que el resto de la suite TPS Studio.
