# 🪪 TPS Studio — Diseño y Producción de Tarjetas

¡Bienvenido a **TPS Studio**! Esta es una herramienta profesional diseñada para simplificar la creación de tarjetas de identificación, carnets y acreditaciones. Olvídate de pelearte con medidas milimétricas en programas de diseño genéricos; aquí todo está pensado para el formato estándar **CR80** (85.6 × 54 mm).

---

## 🚀 ¿Qué puedes hacer con TPS Studio?

Este programa no solo sirve para diseñar, sino para **producir en masa**. Si tienes un diseño y un listado de 500 personas en un Excel, TPS Studio hace el trabajo sucio por ti.

### ✨ Características Estrella:
*   **🎨 Editor Visual Inteligente**: Diseña con guías de sangrado y seguridad para que la imprenta no te devuelva los archivos.
*   **📊 Datos Variables (Mail-Merge)**: Vincula tus diseños a archivos **Excel** o **Access**. ¡Cambia de registro y mira cómo se actualiza la tarjeta al instante!
*   **🖼️ Gestión de Fotos**: Vincula una carpeta de imágenes y asócialas a tus registros automáticamente.
*   **🔳 Códigos QR y de Barras**: Genera códigos dinámicos basados en la información de tu base de datos.
*   **📄 Exportación Profesional**:
    *   **PDF Individuales**: Un archivo por persona listo para enviar.
    *   **Muestra A4**: Para que el cliente te firme el visto bueno.
    *   **PDF para Imprenta**: Con sangrado y alta resolución.
*   **🖨️ Impresión Directa**: Imprime directamente en tu impresora de tarjetas (Frente o Frente+Dorso).

---

## 🛠️ Tecnologías utilizadas

Este proyecto es una aplicación de escritorio robusta construida con:
*   **Java 21 LTS** (El corazón del programa).
*   **JavaFX 21** (Para una interfaz moderna y fluida).
*   **Maven** (Gestión de dependencias).
*   **PDFBox** (Para la generación de PDFs de alta calidad).
*   **ZXing** (Para los códigos QR).
*   **Apache POI & Ucanaccess** (Para leer tus Excels y bases de datos).

---

## 📂 Estructura del Proyecto

Si eres programador y quieres echar un ojo al código, así es como nos organizamos:
*   `app/`: El arranque del programa.
*   `model/`: La definición de los objetos (Tarjetas, Elementos, Clientes...).
*   `view/`: Toda la magia visual y los controladores de la interfaz.
*   `viewmodel/`: El estado de la aplicación (siguiendo el patrón **MVVM**).
*   `service/`: Los "músculos" (gestión de archivos, lógica de proyectos, exportación).
*   `util/`: Herramientas de apoyo (carga de imágenes, textos, etc.).

---

## 🏃 Cómo ponerlo en marcha

Para ejecutar el proyecto en modo desarrollo, asegúrate de tener **Maven** y **JDK 21** instalados, y lanza:

```bash
mvn clean javafx:run
```

Para generar un instalador **.exe** para Windows, utiliza `jpackage` apuntando al JAR generado en la carpeta `target/`.

---

## 📝 Documentación adicional

¿Necesitas ayuda? Hemos preparado un **Manual de Usuario** detallado en la carpeta `docs/`:
*   [Manual Parte 1 - Inicio y Proyectos](docs/manual_tps_studio_parte1.html)
*   [Manual Parte 2 - Diseño y Exportación](docs/manual_tps_studio_parte2.html)

---

## 👤 Autor

**David Gutiérrez Ortiz**  
*Proyecto Final - Grado Superior de Programación (DAM)*

---
> *Diseñado con ❤️ para que hacer carnets deje de ser una pesadilla.*
