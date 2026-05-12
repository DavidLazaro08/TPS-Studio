# TPS Studio — Guía de Arquitectura y Clases

> **Generado:** 2026-05-12 | **Contexto:** Post-refactorización MVVM  
> Este documento es la referencia viva de la estructura del proyecto. Actualizar tras cada refactorización significativa.

---

## Estructura de Paquetes

```
com.tpsstudio
├── app/                        → Punto de entrada (Main, App)
├── dao/                        → Interfaces DAO de persistencia
├── model/
│   ├── auth/                   → Modelos de autenticación
│   ├── elements/               → Elementos del diseño (Texto, Imagen, Forma, Código, Fondo)
│   ├── enums/                  → Enumeraciones globales
│   ├── print/                  → Modelos de impresión/exportación
│   └── project/                → Proyecto, Metadata, FuenteDatos, ClienteInfo
├── service/                    → Lógica de negocio (capa Service)
├── util/                       → Utilidades transversales
├── view/
│   ├── controllers/            → Controladores FXML principales
│   │   └── sub/                → Sub-controladores delegados
│   ├── dialogs/                → Diálogos reutilizables
│   ├── managers/               → Managers de UI (canvas, capas, toolbox...)
│   │   ├── design/             → Managers del área de diseño/canvas
│   │   └── props/              → Handlers del panel de propiedades
│   └── backup/                 → Clases antiguas / en desuso
└── viewmodel/                  → ViewModels (patrón MVVM)
```

---

## Capa: View — Controllers

| Clase | Archivo | Descripción |
|---|---|---|
| `MainViewController` | `controllers/MainViewController.java` | Controlador FXML raíz; inicializa y coordina todos los sub-controladores y managers. Punto de entrada de la vista principal. |
| `LoginViewController` | `controllers/LoginViewController.java` | Pantalla de login independiente; gestiona autenticación de usuario. |
| `ActivationViewController` | `controllers/ActivationViewController.java` | Pantalla de activación de licencia. |
| `ElementActionsController` | `controllers/sub/ElementActionsController.java` | Sub-controlador: añadir/eliminar elementos y gestión completa de fondos (FileChooser, reload, diálogo FitMode). |
| `ProjectActionsController` | `controllers/sub/ProjectActionsController.java` | Sub-controlador: crear, abrir, guardar, editar y cerrar proyectos. |
| `SessionController` | `controllers/sub/SessionController.java` | Sub-controlador: login, logout y activación de licencia desde la vista principal. |

---

## Capa: View — Managers (Design)

| Clase | Archivo | Descripción |
|---|---|---|
| `EditorCanvasManager` | `managers/EditorCanvasManager.java` | Orquestador del canvas: conecta `CanvasRenderer`, `CanvasInteractionHandler` y `CanvasAnimationManager`. |
| `CanvasRenderer` | `managers/design/CanvasRenderer.java` | Dibuja todos los elementos (fondo, formas, imágenes, texto, QR) sobre el `Canvas` JavaFX. |
| `CanvasInteractionHandler` | `managers/design/CanvasInteractionHandler.java` | Gestiona drag, resize y selección con ratón sobre el canvas. |
| `CanvasAnimationManager` | `managers/design/CanvasAnimationManager.java` | Animaciones de flip frente/dorso y transiciones del canvas. |
| `LayersPanelManager` | `managers/design/LayersPanelManager.java` | Lógica del panel lateral de capas: orden Z, visibilidad, bloqueo, arrastrar para reordenar. |
| `ProductionViewManager` | `managers/design/ProductionViewManager.java` | Orquestador de la vista de producción. Gestiona estado de filtrado y acciones CRUD. |
| `ProjectListCellFactory` | `managers/design/ProjectListCellFactory.java` | Fábrica de celdas: construye el layout visual, animaciones y botones de cada proyecto en la lista. |
| `FiltroPopupManager` | `managers/design/FiltroPopupManager.java` | Gestiona el botón de categorías y el popup desplegable de filtros. |
| `VariableDataManager` | `managers/design/VariableDataManager.java` | Gestiona la barra de navegación de registros y la vinculación de columnas a elementos en la vista de producción. |
| `ToolboxManager` | `managers/ToolboxManager.java` | Panel de herramientas lateral: botones para añadir texto, imagen, forma y código. |
| `ModeManager` | `managers/ModeManager.java` | Controla el modo activo (diseño/previsualización) y actualiza el panel de capas (ListView). |
| `ShortcutManager` | `managers/ShortcutManager.java` | Registra y gestiona los atajos de teclado globales de la aplicación. |
| `PropertiesPanelController` | `managers/PropertiesPanelController.java` | Enruta la selección de elemento al handler de propiedades correcto según el tipo. |

---

## Capa: View — Managers (Props / Propiedades)

| Clase | Archivo | Descripción |
|---|---|---|
| `BasePropertyHandler` | `managers/props/BasePropertyHandler.java` | Handler base con propiedades comunes a todos los elementos (nombre, posición, bloqueo). |
| `TextPropertyHandler` | `managers/props/TextPropertyHandler.java` | Panel de propiedades específico para `TextoElemento` (fuente, tamaño, color, columna vinculada). |
| `ImagePropertyHandler` | `managers/props/ImagePropertyHandler.java` | Panel de propiedades específico para `ImagenElemento` (ruta, proporción, columna vinculada). |
| `ShapePropertyHandler` | `managers/props/ShapePropertyHandler.java` | Panel de propiedades específico para `FormaElemento` (color relleno, borde, grosor). |
| `CodePropertyHandler` | `managers/props/CodePropertyHandler.java` | Panel de propiedades específico para `ElementoCodigo` (tipo QR/barras, contenido, columna). |

---

## Capa: ViewModel

| Clase | Archivo | Descripción |
|---|---|---|
| `MainViewModel` | `viewmodel/MainViewModel.java` | Estado observable central de la aplicación: proyecto actual, elemento seleccionado, modo activo, zoom. |

---

## Capa: Service (Lógica de Negocio)

| Clase | Archivo | Descripción |
|---|---|---|
| `ProjectManager` | `service/ProjectManager.java` | Servicio central del ciclo de vida de proyectos: crear, abrir, guardar, gestión de elementos y fondos. |
| `ProyectoFileManager` | `service/ProyectoFileManager.java` | DAO de persistencia: serialización JSON (.tps), copia de recursos al proyecto, rehidratación de rutas al cargar. |
| `RecentProjectsManager` | `service/RecentProjectsManager.java` | Gestiona el historial de proyectos recientes por usuario (lectura/escritura a preferencias). |
| `DatosVariablesManager` | `service/DatosVariablesManager.java` | Carga y guarda fuentes de datos (Excel/Access) para la vista de producción variable. |
| `EtiquetasManager` | `service/EtiquetasManager.java` | Gestiona el sistema de etiquetas de colores para organizar proyectos. |
| `PDFExportService` | `service/PDFExportService.java` | Exportación a PDF de alta calidad con soporte de datos variables por registro. |
| `ImpresionService` | `service/ImpresionService.java` | Impresión directa de tarjetas desde la aplicación. |
| `DesignValidatorService` | `service/DesignValidatorService.java` | Valida el diseño antes de exportar (márgenes, fuentes, elementos fuera de área, etc.). |
| `ExternalEditorService` | `service/ExternalEditorService.java` | Abre archivos de recursos (fondos, imágenes) en el editor externo configurado. |
| `AuthService` | `service/AuthService.java` | Singleton de autenticación y gestión de sesión de usuario. |
| `SettingsManager` | `service/SettingsManager.java` | Persiste preferencias globales de la aplicación (editor externo, preferencias UI). |

---

## Capa: Model — Elements

| Clase | Descripción |
|---|---|
| `Elemento` | Clase base abstracta para todos los elementos del diseño (posición, tamaño, nombre, bloqueo, etiqueta). |
| `TextoElemento` | Elemento de texto con fuente, tamaño, color y columna de datos vinculada. |
| `ImagenElemento` | Placeholder de imagen con ruta relativa, proporciones y columna vinculada. |
| `FormaElemento` | Forma geométrica (rectángulo, elipse, línea) con color de relleno y borde. |
| `ElementoCodigo` | Código QR o de barras con contenido estático o columna vinculada. |
| `ImagenFondoElemento` | Fondo de tarjeta: guarda la **ruta relativa** (`"Fondos/img.png"`), la imagen cargada y el `FondoFitMode`. Siempre bloqueado. |

---

## Capa: Model — Project

| Clase | Descripción |
|---|---|
| `Proyecto` | Modelo principal: listas de elementos frente/dorso, fondos, orientación, metadata y estado de flip. |
| `ProyectoMetadata` | Metadatos: nombre, cliente, rutas de carpetas (`carpetaProyecto`, `rutaFotos`, `rutaFondos`, `rutaTPS`, `rutaBBDD`). |
| `FuenteDatos` | Datos cargados de una fuente Excel/Access: columnas y filas de registros. |
| `ClienteInfo` | Información del cliente asociado al proyecto (nombre, email, teléfono, observaciones). |

---

## Capa: Model — Enums

| Enum | Descripción |
|---|---|
| `FondoFitMode` | `BLEED` (con sangrado 2mm) / `FINAL` (solo área CR80). |
| `Orientacion` | `HORIZONTAL` / `VERTICAL`. |
| `TipoCodigo` | `QR` / `BARRAS`. |

---

## Convenciones Arquitectónicas

### Rutas de Archivos
> [!IMPORTANT]
> Los elementos (`ImagenFondoElemento`, `ImagenElemento`) almacenan siempre **rutas relativas** a la carpeta raíz del proyecto (ej: `"Fondos/fondo_DORSO.png"`). Para obtener la ruta física real hay que resolverlas:
> ```java
> Path rutaAbsoluta = Paths.get(metadata.getCarpetaProyecto()).resolve(elemento.getRutaArchivo());
> ```
> Al cargar desde disco (`ProyectoFileManager.cargarProyecto`) la rehidratación se hace automáticamente. En cualquier otro acceso manual (recarga, editor externo, etc.) se debe resolver siempre explícitamente.

### Comunicación Vista ↔ Servicio
- Los servicios (`ProjectManager`, etc.) **no conocen JavaFX UI**. Se comunican con la vista mediante **callbacks** (`Runnable`, `BiConsumer<String,String>` para notificaciones).
- El estado compartido fluye a través de **`MainViewModel`** (propiedades observables de JavaFX).

### Delegación en `MainViewController`
- `MainViewController` solo coordina; **no contiene lógica de negocio propia**.
- Toda acción de usuario se delega a uno de los sub-controladores (`ElementActionsController`, `ProjectActionsController`, `SessionController`) o managers.

---

## Prioridad de Revisión de Código (formato, comentarios y limpieza)

> Orden en que revisar clase a clase para mejorar legibilidad, Javadoc, nombres y estilo — **sin cambiar lógica**.

| # | Clase | Razón de prioridad | Qué revisar |
|---|---|---|---|
| 1 | `ProjectManager` | Es la clase de negocio más consultada; debe ser referencia de estilo | Comentarios de bloque, nombres de métodos en español consistentes, eliminar `System.out` de debug (`[DEBUG]`) |
| 2 | `ProyectoFileManager` | Compleja y crítica; los DTOs internos merecen Javadoc propio | Documentar las clases `ProyectoDTO`, `ElementoDTO`, `FondoDTO`; aclarar el contrato de rutas relativas vs absolutas |
| 3 | `CanvasRenderer` | Muchos métodos de dibujo sin documentar; muy visitado al depurar | Añadir comentarios por sección (fondo, elementos, selección, guías); documentar unidades (px canvas vs mm reales) |
| 4 | `MainViewController` | Punto de entrada; debe quedar impecable como "cara" del código | Revisar que todos los métodos `@FXML` tengan una línea de Javadoc; eliminar comentarios obsoletos de antes de la refactorización |
| 5 | `ElementActionsController` | Recién revisado; buen estado pero sin Javadoc en helpers privados | Añadir `@param` / `@return` a `mostrarDialogoFitMode()` y `confirmarReemplazoFondo()` |
| 6 | `ModeManager` | Lógica de cell factory comentada pero compleja | Documentar el por qué del reset explícito en `updateItem()` (anti ghost-layers) |
| 7 | `CanvasInteractionHandler` | Lógica de hit-testing y resize sin comentarios de intención | Explicar los cálculos de umbral de selección y los offsets de redimensionado |
| 8 | `MainViewModel` | Pocas líneas pero muy visitado; debe ser autoexplicativo | Verificar que todas las propiedades observables tienen Javadoc de una línea |
| 9 | `*PropertyHandler` (x4) | Patrón repetitivo; revisar los 4 juntos en una sesión | Unificar estilo de comentarios entre `Text`, `Image`, `Shape` y `Code` handlers |
| 10 | `LayersPanelManager` | Funcional, pero con algunos bloques largos sin seccionar | Añadir separadores de sección (`// === Reordenado ===`, etc.) y limpiar lógica duplicada |

---

## Prioridad de Revisión / Refactorización

| # | Clase | Tamaño | Razón |
|---|---|---|---|
| 1 | `ProductionViewManager` | ~240 líneas | ✅ Refactorizado (2026-05-12). Dividido en 3 clases. |
| 2 | `CanvasRenderer` | ~700 líneas | Core del renderizado; documentar bien |
| 3 | `ProyectoFileManager` | ~740 líneas | Persistencia crítica; separar DTOs a su propio archivo |
| 4 | `ProjectManager` | ~771 líneas | Orquestador; separar lógica de alertas/UI |
| 5 | `MainViewController` | ~1100 líneas | Limpio tras refactorización, mantener |
| 6 | `ElementActionsController` | ~350 líneas | Revisado 2026-05-12; bug reload fondo corregido |
| 7 | `CanvasInteractionHandler` | ~500 líneas | Lógica compleja de UX; documentar |
| 8 | `LayersPanelManager` | ~700 líneas | Bug ghost-layers corregido; revisar comentarios |
| 9 | `*PropertyHandler` (x4) | ~200-400c/u | Patrón uniforme; revisión conjunta |

---

## Bugs Corregidos (histórico)

| Fecha | Bug | Archivo | Solución |
|---|---|---|---|
| 2026-05-12 | "Archivo no encontrado" al recargar fondo inmediatamente tras añadirlo | `ElementActionsController.recargarFondo()` | `getRutaArchivo()` devuelve ruta relativa; se resuelve ahora contra `metadata.getCarpetaProyecto()` antes de `file.exists()` |
| 2026-05-11 | "Ghost layers" — múltiples items aparecen seleccionados en el panel de capas | `ModeManager` (ListView cell factory) | Reset explícito de estado visual en `updateItem()` al reciclar células |
