# Feature Specification: Hardening del sistema y prevención de desinstalación

**Feature Branch**: `N/A (no branch hook configured)`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "Implementar la capa de protección del sistema (Hardening) y prevención de desinstalación en `:core:overlay` y `:app`.

### Componentes y Requisitos
1. **DeviceAdminReceiver (`BlockerDeviceAdminReceiver`)**:
   - Registrar un `DeviceAdminReceiver` en el `AndroidManifest.xml` con los meta-datos de políticas requeridos (`policies.xml` que incluya `force-lock` y `limit-password`).
   - Escuchar `onDisableRequested` y `onDisabled` para responder ante intentos de remover el permiso de administración del dispositivo.

2. **Detección de Pantallas de Ajustes del Sistema (`SettingsInterceptor`)**:
   - Extender la lógica de inspección del `BlockerAccessibilityService` para identificar cuando el usuario ingresa a paquetes o clases del sistema sensibles:
     * Ajustes de Accesibilidad (para apagar el servicio).
     * Configuración de la Aplicación en Android Settings (`com.android.settings` - para forzar cierre, limpiar datos o desinstalar).
     * Administradores de Dispositivo.
   - Al detectar cualquiera de estas vistas del SO, emitir inmediatamente una señal de bloqueo de alta prioridad al `OverlayCoordinator` para desplegar la `FrictionScreen` ANTES de que el usuario pueda interactuar con las opciones de desactivación.

3. **Reinicio Autónomo y Persistencia (`BootReceiver`)**:
   - Implementar un `BroadcastReceiver` que escuche `Intent.ACTION_BOOT_COMPLETED`, `ACTION_LOCKED_BOOT_COMPLETED` y `ACTION_MY_PACKAGE_REPLACED`.
   - Al recibir el evento, verificar si la protección estaba activa en `DataStore` e iniciar automáticamente el `MonitoringForegroundService` y recalibrar los servicios.

### Restricciones Técnicas
- El intento de apagado del servicio de accesibilidad o remoción de administración de dispositivo debe forzar el despliegue del overlay sin excepción.
- Manejar de forma adecuada los permisos en Android 14+ (API 34+) para el lanzamiento de vistas overlay desde background/servicios."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bloquear pantallas del sistema sensibles (Priority: P1) 🎯 MVP

La persona usuaria intenta desactivar la protección desde los ajustes del
sistema. Al entrar en una pantalla sensible, la aplicación muestra la pantalla
de fricción antes de que pueda operar las opciones de desactivación.

**Why this priority**: Es la defensa principal contra la desactivación accidental
o impulsiva del bloqueo. Sin ella, todo el sistema de control se evade en pocos
toques.

**Independent Test**: Abrir Ajustes de Accesibilidad, Información de la App o
Administradores de Dispositivo con la protección activa y comprobar que el
overlay aparece y cubre la pantalla; salir de la pantalla sensible y comprobar
que el overlay se retira.

**Acceptance Scenarios**:

1. **Given** la protección activa y el servicio de accesibilidad conectado, **When** la persona entra en Ajustes de Accesibilidad, **Then** el overlay de protección aparece en menos de un segundo y cubre la pantalla.
2. **Given** la protección activa, **When** la persona entra en la información de una aplicación con opciones de detener, borrar datos o desinstalar, **Then** el overlay de protección aparece y bloquea la interacción.
3. **Given** la protección activa, **When** la persona entra en Administradores de Dispositivo, **Then** el overlay de protección aparece.
4. **Given** el overlay de protección visible sobre una pantalla sensible, **When** la persona abandona esa pantalla, **Then** el overlay se retira.
5. **Given** una pantalla de ajustes no sensible, **When** la persona navega por ella, **Then** no se muestra el overlay de protección.

---

### User Story 2 - Responder a la desactivación del administrador (Priority: P2)

La persona usuaria intenta quitar el permiso de administración del dispositivo.
La aplicación muestra una advertencia y despliega la fricción, aunque el sistema
puede permitir la remoción definitiva.

**Why this priority**: El administrador de dispositivo es una capa de protección
adicional. Responder al intento disuade y deja trazabilidad, pero no puede
impedir por sí solo que el sistema operativo retire el permiso.

**Independent Test**: Solicitar la desactivación del administrador y comprobar
que aparece la advertencia y se intenta desplegar el overlay; confirmar la
desactivación y comprobar que se registra el estado y se notifica el cambio.

**Acceptance Scenarios**:

1. **Given** el permiso de administración activo, **When** el sistema solicita confirmación para desactivarlo, **Then** la aplicación muestra una advertencia y emite una señal de protección de alta prioridad.
2. **Given** el intento de desactivación en curso, **When** la señal llega al coordinador, **Then** se intenta mostrar la pantalla de fricción sin esperar a una regla de contenido.
3. **Given** que el administrador fue desactivado, **When** se recibe el evento de desactivación, **Then** se registra el nuevo estado de protección y se notifica o se intenta mostrar el overlay.
4. **Given** que el sistema completa la remoción, **When** la aplicación ya no es administradora, **Then** no se produce un bucle de errores ni un cierre inesperado.

---

### User Story 3 - Reiniciar la protección de forma autónoma (Priority: P3)

Después de reiniciar el dispositivo o de actualizar la aplicación, la protección
se reactiva automáticamente si estaba activa antes.

**Why this priority**: Sin reinicio autónomo, la protección desaparece tras el
primer reinicio o actualización y el usuario queda sin defensa.

**Independent Test**: Activar la protección, reiniciar el dispositivo y
comprobar que el servicio de monitoreo se inicia y los permisos/servicios se
recalibran. Repetir con una actualización de la aplicación.

**Acceptance Scenarios**:

1. **Given** la protección activa antes de un reinicio, **When** se recibe `BOOT_COMPLETED`, **Then** el servicio de monitoreo arranca automáticamente y se recalibran accesibilidad, overlay y administración.
2. **Given** la protección activa antes de un reinicio bloqueado, **When** se recibe `LOCKED_BOOT_COMPLETED`, **Then** se usa el estado seguro de arranque disponible y se inicia la monitorización o se difiere al desbloqueo.
3. **Given** una actualización de la aplicación con la protección activa, **When** se recibe `MY_PACKAGE_REPLACED`, **Then** la protección se rearma y el servicio vuelve a iniciarse.
4. **Given** la protección inactiva, **When** ocurre cualquiera de esos eventos, **Then** no se inicia el servicio de monitoreo.
5. **Given** que faltan permisos tras el reinicio, **When** se intenta arrancar la protección, **Then** se registra el diagnóstico y no se produce un cierre inesperado.

---

### Edge Cases

- Qué ocurre si el permiso de superposición fue revocado antes de entrar en una pantalla sensible?
- Qué ocurre si Android 14+ impide iniciar el servicio en primer plano desde background?
- Qué ocurre si el dispositivo está bloqueado y el almacenamiento de DataStore aún no está disponible?
- Qué ocurre si el servicio de accesibilidad ya está desactivado cuando se intenta detectar la pantalla sensible?
- Qué ocurre si el administrador de dispositivo ya fue removido?
- Qué ocurre si la detección de la pantalla sensible llega después de que la persona ya tocó una opción?
- Qué ocurre si la persona pulsa inicio o recientes con el overlay de protección visible?
- Qué ocurre si el paquete o la clase de Ajustes varía según el fabricante?
- Qué ocurre si el estado de protección en DataStore está corrupto o ausente?
- Qué ocurre si la aplicación es forzada a detenerse o sus datos son borrados?
- Qué ocurre si múltiples eventos de amenaza llegan en rápida sucesión?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST registrar un `DeviceAdminReceiver` con políticas de bloqueo forzado y limitación de contraseña.
- **FR-002**: El sistema MUST manejar la solicitud de desactivación del administrador mostrando una advertencia y emitiendo una señal de protección de alta prioridad.
- **FR-003**: El sistema MUST manejar la desactivación efectiva del administrador registrando el nuevo estado y notificando o intentando mostrar el overlay.
- **FR-004**: El sistema MUST identificar pantallas sensibles de accesibilidad, información de aplicación y administradores de dispositivo.
- **FR-005**: El sistema MUST emitir una señal de bloqueo de alta prioridad al detectar una pantalla sensible, sin depender de las reglas de contenido normales.
- **FR-006**: El coordinador MUST desplegar la pantalla de fricción al recibir la señal de alta prioridad.
- **FR-007**: La fricción de una pantalla sensible MUST impedir la interacción con las opciones de desactivación mientras esa pantalla permanezca en primer plano.
- **FR-008**: La fricción de una pantalla sensible MUST retirarse cuando la persona abandona la pantalla sensible.
- **FR-009**: El sistema MUST manejar de forma controlada la ausencia del permiso de superposición, sin cierres inesperados.
- **FR-010**: El sistema MUST escuchar `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` y `MY_PACKAGE_REPLACED`.
- **FR-011**: Ante esos eventos, el sistema MUST comprobar si la protección estaba activa antes de iniciar el servicio de monitoreo.
- **FR-012**: Si la protección estaba activa, el sistema MUST iniciar el servicio de monitoreo y recalibrar accesibilidad, superposición y administración.
- **FR-013**: Si la protección no estaba activa, el sistema MUST NOT iniciar el servicio de monitoreo.
- **FR-014**: El sistema MUST manejar las restricciones de Android 14+ para iniciar servicios en primer plano y mostrar overlays desde background.
- **FR-015**: El sistema MUST usar un estado de arranque seguro cuando el almacenamiento principal no esté disponible antes del desbloqueo.
- **FR-016**: La recepción repetida de señales de protección MUST ser idempotente y no duplicar overlays.
- **FR-017**: La detección de pantallas sensibles MUST permanecer desacoplada de la UI y comunicarse mediante eventos.
- **FR-018**: El sistema MUST registrar el resultado de los intentos de arranque y de bloqueo para diagnóstico.
- **FR-019**: La aplicación MUST NOT entrar en un bucle de reintentos o cierres si el sistema operativo impide una acción.
- **FR-020**: El sistema MUST dejar claro en su comportamiento que la remoción del administrador por el sistema operativo no puede impedirse de forma absoluta.

### Key Entities *(include if feature involves data)*

- **Pantalla sensible**: Vista del sistema cuya interacción puede desactivar la protección. Contiene categoría (accesibilidad, información de app, administradores), paquete y clase.
- **Señal de amenaza de protección**: Evento de alta prioridad que indica un intento de desactivación o una pantalla sensible. Contiene tipo, origen y marca de tiempo.
- **Estado de protección**: Indica si la protección estaba activa y debe rearmarse tras reinicio o actualización.
- **Evento de administrador**: Resultado de la solicitud o confirmación de desactivación del administrador.
- **Estado de arranque seguro**: Copia mínima del estado de protección disponible antes del desbloqueo del dispositivo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 95 % de las detecciones de pantallas sensibles muestra el overlay en menos de 1 segundo.
- **SC-002**: El 100 % de las señales de alta prioridad se procesa sin depender de reglas de contenido.
- **SC-003**: El 100 % de las salidas de una pantalla sensible retira el overlay correspondiente.
- **SC-004**: El 100 % de los reinicios con protección activa inicia el monitoreo en un máximo de 30 segundos tras `BOOT_COMPLETED`.
- **SC-005**: El 100 % de las actualizaciones de paquete con protección activa rearma el monitoreo.
- **SC-006**: El 100 % de los intentos de desactivación del administrador genera una advertencia o señal de protección, aunque el sistema complete la remoción.
- **SC-007**: El 0 % de los escenarios con permisos faltantes produce cierres inesperados o bucles de reintento.
- **SC-008**: El 0 % de las pantallas de ajustes no sensibles activa el overlay de protección.

## Assumptions

- La persona usuaria ya concedió accesibilidad, superposición y administración del dispositivo; la feature no inventa permisos que el sistema no otorga.
- Android no permite impedir de forma absoluta que el usuario o el sistema retiren el administrador de dispositivo. `onDisableRequested` es una advertencia y `onDisabled` una notificación posterior; la app responde, pero no puede garantizar el bloqueo de la remoción.
- Las pantallas sensibles se detectan por paquete y clase con heurísticas por fabricante; no se promete cobertura del 100 % de todas las variantes del sistema.
- La fricción de pantallas sensibles es una variante no desbloqueable mientras la pantalla permanezca en primer plano; al salir, se retira. No se ofrece una acción de cierre.
- La detección de accesibilidad ocurre después de que el sistema cambia de ventana; "antes de interactuar" se interpreta como el menor tiempo posible tras el cambio, no como una garantía atómica del sistema.
- El estado de protección se guarda en DataStore; para `LOCKED_BOOT_COMPLETED` se usa una copia mínima en almacenamiento protegido por dispositivo o se difiere el arranque hasta el desbloqueo.
- Android 14+ puede limitar el inicio de servicios en primer plano y la visualización de overlays desde background. La app usa el permiso de superposición, un servicio en primer plano ya activo cuando sea posible y manejo de errores; no se promete saltarse las restricciones del sistema.
- Los eventos de reinicio y actualización son idempotentes y no deben duplicar servicios ni overlays.
- No se implementa una ruta de escape oculta en esta iteración; la desactivación legítima queda sujeta a los mecanismos del sistema operativo.
- Restricciones técnicas impuestas por el solicitante: `DeviceAdminReceiver`, `policies.xml` con `force-lock` y `limit-password`, `SettingsInterceptor`, señal de alta prioridad al coordinador, `BootReceiver` con los tres intents y manejo de Android 14+.
