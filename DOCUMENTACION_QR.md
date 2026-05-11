# Documentación del Sistema de Códigos (Universal)

Este módulo gestiona la generación y renderizado de códigos QR y de barras dentro de TPS Studio.

## 1. Arquitectura
El sistema se basa en la clase genérica `ElementoCodigo`, que centraliza la lógica para múltiples formatos.

### Clase `ElementoCodigo`
Hereda de `Elemento` y gestiona:
- **Tipos soportados:** QR, Code 128, Code 39, EAN-13, UPC-A.
- **Modos:** Estático (texto fijo) y Dinámico (vinculado a base de datos).
- **Estética:** Colores personalizables (código y fondo), márgenes (Quiet Zone) y visualización de texto human-readable.

## 2. Generación (ZXing)
Se utiliza la librería **Google ZXing (3.5.3)**.
- **Motor:** `MultiFormatWriter` para soportar tanto códigos 2D (QR) como 1D (Barras).
- **Caché:** Las imágenes se generan en una resolución interna de 400px y se cachean para optimizar el rendimiento. El caché se invalida automáticamente si cambia el contenido, el tipo o los colores.

## 3. Interfaz de Usuario
### Toolbox (Caja de Herramientas)
- **Código QR:** Botón independiente para acceso rápido.
- **Códigos de Barras:** Menú tipo acordeón que agrupa los formatos lineales.

### Panel de Propiedades
- **Selector de Tipo:** Permite cambiar el formato de un código ya existente.
- **Configuración QR:** Control de nivel de corrección de errores (L, M, Q, H).
- **Configuración Barras:** 
    - Checkbox para mostrar/ocultar el texto (número) inferior.
    - Spinner para ajustar el tamaño de fuente del texto.

## 4. Renderizado en Canvas
El `EditorCanvasManager` se encarga de dibujar la imagen generada. Para códigos de barras 1D, si la opción está activa, el canvas dibuja manualmente el texto centrado debajo del elemento utilizando las propiedades de estilo configuradas.

---
*Nota: Para códigos EAN-13 y UPC-A, el sistema valida automáticamente la longitud de los datos antes de intentar la generación.*
