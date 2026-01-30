# Backup de Archivos del Refactoring

Este directorio contiene los archivos originales antes del refactoring de MainViewController.

## Archivos

### `MainViewController_BACKUP.java` (1594 líneas)
**Fecha**: Enero 2026  
**Descripción**: Versión original del MainViewController antes de dividirlo en 5 clases especializadas.

**Contenía**:
- Renderizado del canvas
- Eventos de mouse (drag & drop, resize)
- Panel de propiedades
- Gestión de modos (DESIGN/PRODUCTION)
- CRUD de proyectos y elementos
- Diálogos y helpers

**Refactorizado en**:
- `MainViewController.java` (551 líneas) - Coordinación y UI
- `EditorCanvasManager.java` (567 líneas) - Renderizado y mouse
- `PropertiesPanelController.java` (485 líneas) - Panel de propiedades
- `ModeManager.java` (379 líneas) - Gestión de modos
- `ProjectManager.java` (294 líneas) - Lógica de negocio

---

### `EditorViewController.java.backup`
**Descripción**: Backup de un controlador anterior (posiblemente obsoleto).

---

## ⚠️ Importante

**NO USAR ESTOS ARCHIVOS EN PRODUCCIÓN**

Estos archivos se mantienen únicamente como referencia histórica. El código activo está en:
- `../MainViewController.java`
- `../EditorCanvasManager.java`
- `../PropertiesPanelController.java`
- `../ModeManager.java`
- `../../service/ProjectManager.java`

---

## Documentación del Refactoring

Para entender el refactoring completo, consulta:
- `docs/mainviewcontroller_audit.md.resolved` - Plan de refactoring
- Artifacts en `.gemini/antigravity/brain/.../refactoring_audit_for_dummies.md`
