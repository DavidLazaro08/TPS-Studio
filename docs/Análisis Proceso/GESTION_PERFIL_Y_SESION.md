# Documentación Técnica: Gestión de Perfil y Sesiones

Este documento detalla la implementación del sistema de perfiles de usuario y la funcionalidad de cierre de sesión en **TPS Studio**, mejorando la seguridad y la experiencia multi-usuario.

## 1. Interfaz de Usuario (UI)
Se ha integrado un panel de perfil persistente en la barra lateral izquierda del software:
-   **Ubicación**: Esquina inferior izquierda, fijado debajo de la lista de proyectos/capas.
-   **Indicador de Sesión**: Muestra dinámicamente el nombre del usuario activo (ej: `SESIÓN: DAVIDL`).
-   **Control de Cierre**: Incluye un enlace de "Cerrar Sesión" que permite cambiar de cuenta de forma rápida.

## 2. Lógica de Sesión
El sistema utiliza el `AuthService` (Singleton) para mantener el estado global de la aplicación:
-   **Inicio de Sesión**: Al validar las credenciales en el Login, el servicio establece el `currentUser`.
-   **Cierre de Sesión**: El método `logout()` limpia el usuario activo. Esto garantiza que ningún componente pueda acceder a datos privados (como proyectos recientes) después de cerrar la sesión.

## 3. Flujo de Logout (Cambio de Usuario)
Cuando el usuario pulsa "Cerrar Sesión":
1.  **Confirmación**: Se muestra un diálogo de confirmación para evitar cierres accidentales.
2.  **Limpieza**: Se llama a `AuthService.logout()`.
3.  **Transición**: El programa utiliza `FXMLLoader` para recargar la vista de Login en la ventana actual, asegurando una transición fluida sin abrir múltiples ventanas.

## 4. Detalles de Implementación (DAM)
-   **Estilos**: El panel de perfil utiliza CSS especializado (`.user-profile-panel`) para integrarse en el tema oscuro del programa con un diseño minimalista.
-   **Desacoplamiento**: El controlador principal (`MainViewController`) no gestiona los datos del usuario directamente, sino que consulta al servicio, siguiendo los principios de arquitectura limpia.
