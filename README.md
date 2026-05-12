# TPS Studio — Card Design & Production Suite

TPS Studio es una solución de escritorio profesional diseñada específicamente para la gestión, diseño y preimpresión de tarjetas plásticas y acreditaciones en formato estándar CR80 (85.6 × 54 mm). 

La aplicación optimiza el flujo de trabajo de producción en masa, integrando herramientas de diseño visual con capacidades avanzadas de automatización de datos.

---

## Funcionalidades Principales

### Motor de Diseño y Preimpresión
*   **Editor CR80 Nativo**: Entorno de diseño escalable con guías de sangrado (2mm) y márgenes de seguridad (3mm) integrados para cumplimiento con estándares de imprenta.
*   **Gestión de Capas**: Control preciso sobre la profundidad, visibilidad y orden de los elementos del diseño.
*   **Diseño Bilateral**: Soporte independiente para Frente y Dorso de la tarjeta con sincronización de estado.

### Automatización y Datos Variables
*   **Integración Mail-Merge**: Vinculación dinámica con bases de datos externas en formato Microsoft Excel (.xlsx) y Microsoft Access (.accdb).
*   **Procesamiento de Imágenes Dinámicas**: Resolución automática de rutas de archivos para la inserción masiva de fotografías de identificación.
*   **Generación de Códigos**: Motor integrado para la creación de códigos QR y códigos de barras (Code 128, EAN) vinculados a campos de la base de datos.

### Salida y Producción
*   **Exportación Masiva (PDF)**: Generación de documentos individuales personalizados por registro.
*   **PDF para Artes Gráficas**: Salida de alta resolución con área de sangrado completa para producción en imprenta.
*   **Módulos de Impresión**: Interfaz directa con controladores de impresoras térmicas de tarjetas para impresión monocromo o color.

---

## Especificaciones Técnicas

El software ha sido desarrollado bajo una arquitectura MVVM (Model-View-ViewModel), garantizando la escalabilidad y mantenibilidad del código.

*   **Lenguaje**: Java 21 (LTS)
*   **Framework UI**: JavaFX 21.0.4
*   **Gestor de Proyecto**: Maven
*   **Bibliotecas Clave**: 
    *   Apache PDFBox (Generación de PDF)
    *   ZXing (Generación de códigos)
    *   Apache POI (Procesamiento de datos Excel)
    *   Gson (Persistencia de proyectos en formato JSON)

---

## Despliegue y Ejecución

### Requisitos de Desarrollo
*   Java Development Kit (JDK) 21
*   Apache Maven 3.9+

### Ejecución
```bash
mvn clean javafx:run
```

### Empaquetado
Para generar el ejecutable nativo de Windows (.exe), se recomienda el uso de la herramienta `jpackage` tras la compilación del proyecto.

---

## Documentación de Usuario

El manual de instrucciones detallado se encuentra disponible en formato HTML dentro del directorio `docs/`:
*   Parte 1: Configuración inicial y Gestión de Proyectos
*   Parte 2: Herramientas de Diseño y Flujos de Exportación

---

## Autoría

**David Gutiérrez Ortiz**  
Proyecto Final de Ciclo Formativo de Grado Superior  
Desarrollo de Aplicaciones Multiplataforma (DAM)
