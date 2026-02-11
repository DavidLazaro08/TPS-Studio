# TPS Studio

Aplicación de escritorio especializada en el diseño y preimpresión de tarjetas plásticas CR80 (85.60 × 53.98 mm) y acreditaciones profesionales.

## 🎯 Objetivo

Proporcionar una herramienta específica para técnicos de preimpresión y diseñadores que trabajan con tarjetas plásticas, automatizando el control de medidas precisas, márgenes de seguridad (3mm) y sangrado de corte (2mm).

## ✨ Características Implementadas

- **Gestión de Proyectos**: Crear, abrir y guardar proyectos con metadatos de cliente
- **Editor Visual CR80**: Lienzo con medidas exactas y zoom configurable
- **Elementos de Diseño**: 
  - Fondos con ajuste automático (sangrado/exacto)
  - Textos con fuentes, colores y estilos personalizables
  - Imágenes flotantes con control de opacidad
- **Sistema de Capas**: Visualización, selección y bloqueo de elementos
- **Panel de Propiedades**: Edición dinámica según elemento seleccionado
- **Guías Visuales**: Márgenes de seguridad y zona de sangrado
- **Integración Externa**: Edición de fondos en Photoshop u otros editores
- **Frente/Dorso**: Diseño independiente para ambas caras

## 🛠️ Tecnologías

- **Java 21** + **JavaFX 21.0.4**
- **Arquitectura MVVM** con separación de responsabilidades
- **Maven** para gestión de dependencias
- **Gson** para persistencia JSON
- **Gestión de imágenes** sin bloqueo de archivos (proxy pattern)

## 📁 Estructura del Proyecto

```
com.tpsstudio/
├── app/           # Punto de entrada (TPSStudio.java)
├── model/         # Entidades (Proyecto, Elemento, Cliente)
├── view/          # Controladores y managers (Canvas, Propiedades, Modos)
├── viewmodel/     # Estado de la vista
├── service/       # Lógica de negocio (ProjectManager, FileManager)
└── util/          # Utilidades (ImageUtils, caché)
```

## 🚀 Ejecución

```bash
mvn clean javafx:run
```

## 📚 Documentación

Para más detalles sobre requisitos, casos de uso y arquitectura, consulta la [documentación inicial](../DOCUMENTACIÓN%20PROYECTO/TPS_STUDIO-%20Documentación_InicialV2.pdf).

## 📌 Estado Actual

**Versión:** 0.1.0 (Primera Iteración)  
**Fase:** Desarrollo activo - Core funcional implementado

### Próximas Funcionalidades
- Exportación a PDF con especificaciones de impresión
- Campos variables conectados a datos externos
- Historial de cambios (undo/redo)
- Biblioteca de plantillas predefinidas

## 👤 Autor

**David Gutiérrez Ortiz**  
Proyecto Intermodular - DAM 2º Curso
