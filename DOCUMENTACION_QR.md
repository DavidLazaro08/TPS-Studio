# Documentación Técnica: Sistema de Códigos QR en TPS Studio

Esta documentación detalla la implementación, arquitectura y uso del sistema de generación de códigos QR integrado en **TPS Studio**.

## 1. Arquitectura del Sistema

El sistema sigue el patrón **MVVM (Model-View-ViewModel)** del proyecto, integrándose en las capas existentes de la siguiente manera:

### Modelo (`ElementoQR.java`)
- **Herencia:** Extiende de la clase base `Elemento`.
- **Motor de Generación:** Utiliza la librería **Google ZXing** (v3.5.3).
- **Propiedades Clave:**
  - `contenido`: Texto estático (URL, texto, etc.).
  - `columnaVinculada`: Nombre de la columna de la base de datos para modo dinámico.
  - `colorQR` y `colorFondo`: Personalización cromática en formato Hexadecimal.
  - `nivelCorreccion`: Niveles L, M, Q, H para redundancia de datos.
  - `margen`: Tamaño de la "quiet zone" alrededor del código.

### Vista y Controladores
- **`EditorCanvasManager.java`**: Gestiona el renderizado en tiempo real. Utiliza un sistema de **caché transitorio** (`transient Image`) para evitar regenerar el QR en cada frame del canvas, invalidándolo solo cuando cambian las propiedades.
- **`PropertiesPanelController.java`**: Proporciona la interfaz de edición en el panel derecho, incluyendo `ColorPickers` y lógica de vinculación de datos.
- **`ModeManager.java`**: Registra el elemento en la `Toolbox` (icono `⦀`) y gestiona su visualización en la lista de capas.

## 2. Integración de Datos (Modo Dinámico)

El `ElementoQR` soporta vinculación automática con fuentes de datos (Excel/Access):
1. Si `columnaVinculada` no es nula, el motor ignora el campo `contenido` estático.
2. Durante la navegación de registros o exportación, el sistema extrae el valor de la celda correspondiente y regenera el QR al vuelo.
3. Se incluye un placeholder visual ("QR DATOS") cuando no hay una base de datos cargada para facilitar el diseño.

## 3. Dependencias (Maven)

Se han añadido las siguientes dependencias en el `pom.xml`:
```xml
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.3</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.3</version>
</dependency>
```

## 4. Guía de Uso para el Usuario

1. **Añadir:** Pulsar el botón "Código QR" en la barra de herramientas izquierda.
2. **Personalizar:** Desde el panel de la derecha, se pueden cambiar los colores para que coincidan con la identidad corporativa.
3. **Vincular:** Seleccionar una columna del Excel en la sección "Datos Variables" para generar QRs únicos por cada tarjeta (ej. para carnets de socio con URL de perfil).
4. **Optimizar:** Si el QR va a ser impreso en un tamaño muy pequeño, se recomienda subir el **Nivel de Corrección** a "H (30%)" para asegurar la legibilidad.

---
*Documentación generada para el Proyecto Final de Grado - TPS Studio.*
