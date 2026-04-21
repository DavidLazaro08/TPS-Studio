# Documentación Técnica: Sistema de Autenticación y Activación Local

Este documento describe la arquitectura y el funcionamiento del sistema de seguridad implementado en **TPS Studio**, diseñado para gestionar licencias de software y acceso multi-usuario de forma local y persistente.

## 1. Arquitectura del Sistema
El sistema sigue un patrón de diseño desacoplado utilizando servicios singleton y persistencia en formato JSON.

### Componentes Principales:
- **`AuthService.java`**: El motor central. Gestiona la carga/guardado de datos, la validación de licencias contra la "lista oficial" y el estado de la sesión actual (`currentUser`).
- **`RecentProjectsManager.java`**: Gestiona el historial de proyectos. Se ha adaptado para que cada usuario tenga su propia lista aislada de archivos recientes.
- **`licencias.txt`**: Archivo de recursos que actúa como "Whitelist" de claves permitidas.

## 2. Flujo de Activación
Para que el software sea funcional, debe pasar por un proceso de activación:
1.  El programa verifica en el arranque si existe una licencia activa en el archivo `auth.json`.
2.  Si no existe, el `LoginViewController` muestra un aviso de **"SISTEMA NO ACTIVADO"**.
3.  El usuario accede a la pantalla de activación, introduce sus datos y una clave.
4.  El sistema valida la clave contra `src/main/resources/auth/licencias.txt`.
5.  Si la clave es válida, se genera el registro del usuario y la licencia en el sistema local.

## 3. Persistencia de Datos (Formato JSON)
La información de seguridad no se guarda en el directorio del programa (donde podría borrarse al actualizar), sino en la carpeta de perfil del usuario de Windows:

**Ruta**: `C:\Users\<TuUsuario>\.tpsstudio\auth.json`

### Ejemplo de estructura:
```json
{
  "license": {
    "key": "TPS-PRO-2026-GOLD-01",
    "type": "PRO",
    "active": true
  },
  "users": [
    {
      "username": "David",
      "email": "ejemplo@correo.com",
      "password": "****"
    }
  ]
}
```

## 4. Aislamiento de Espacios de Trabajo (Multi-usuario)
Una característica premium del sistema es el aislamiento de proyectos por usuario:
- Cada usuario que inicia sesión tiene su propia clave en el registro de preferencias de Windows: `recent_projects_{username}`.
- **Retrocompatibilidad**: El usuario `Admin` mantiene su historial previo mediante una lógica de migración automática implementada en el `RecentProjectsManager`.

## 5. Resumen para la Defensa (Puntos Clave)
- **Seguridad "Offline"**: No requiere conexión a internet, validando licencias contra un diccionario interno.
- **Escalabilidad**: Es muy fácil añadir nuevas licencias oficiales simplemente editando un archivo de texto en recursos.
- **Experiencia de Usuario**: Interfaz diseñada con JavaFX bajo estándares modernos (Dark Mode, feedback visual mediante Toasts y alertas personalizadas).
