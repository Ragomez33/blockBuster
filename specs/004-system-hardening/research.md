# Research: Hardening del sistema y prevención de desinstalación

**Feature**: `004-system-hardening`  
**Date**: 2026-11-05  
**Status**: Complete — no unresolved NEEDS CLARIFICATION

## Contexto técnico

El proyecto ya cuenta con:
- `BlockBusterAccessibilityService` que publica detecciones y targets.
- `OverlayCoordinator` que evalúa reglas y muestra `FrictionScreen`.
- `MonitoringForegroundService` con tipo `specialUse`.
- `BootReceiver` que inicia el servicio en `BOOT_COMPLETED`.
- `BlockBusterDeviceAdminReceiver` con política `force-lock`.
- DataStore para el estado de monitoreo.

La nueva feature añade detección de pantallas sensibles, una vía de amenaza de
alta prioridad, políticas de administración y un arranque condicionado al estado
de protección.

## Decisiones

### D1. La remoción del administrador no puede bloquearse de forma absoluta

**Decision**: Tratar `onDisableRequested` como advertencia y `onDisabled` como
notificación posterior. En ambos casos se emite una amenaza de protección y se
intenta mostrar el overlay; no se promete impedir la remoción.

**Rationale**: Android no permite que una app impida al usuario o al sistema
retirar el administrador. Simular una garantía produciría una falsa sensación de
seguridad y posiblemente bucles.

**Alternatives considered**:
- Bloquear la pantalla de confirmación del sistema: no es posible de forma
  fiable y puede violar políticas de plataforma.
- No responder: pierde la oportunidad de disuadir y auditar.

### D2. `SettingsInterceptor` por heurística de paquete y clase

**Decision**: Crear un `SettingsInterceptor` en `:core:accessibility` que clasifica
pantallas por paquete/clase:
- Accesibilidad: paquetes de Settings y clases que contengan
  `Accessibility`/`AccessibilitySettings`.
- Información de app/desinstalación: clases con `InstalledAppDetails`,
  `AppInfo`, `ApplicationInfo` o `Uninstall`.
- Administradores: clases con `DeviceAdmin` o `DeviceAdministration`.

Se incluyen paquetes de Settings de AOSP y heuristicas para fabricantes
(`com.android.settings`, `com.google.android.settings`, paquetes que contengan
`settings`).

**Rationale**: No existe una API pública que enumere estas pantallas. La
heurística es testeable y extensible, y falla de forma segura (sin bloqueo).

**Alternatives considered**:
- Lista cerrada de clases AOSP: frágil en fabricantes.
- Detección por texto visible: costosa y dependiente del idioma.
- Usar `UsageStats`/`ActivityManager`: no ofrece clase de actividad fiable en
  tiempo real.

### D3. Bus de amenazas separado del bus de detección normal

**Decision**: Añadir `ProtectionEventBus` con `events: SharedFlow<ProtectionThreat>`
y `currentThreat: StateFlow<ProtectionThreat?>`.

**Rationale**: Las amenazas deben saltarse la evaluación de reglas de contenido y
llegar al coordinador de inmediato. Mezclarlas con `DetectionEvent` obligaría a
que el matcher y el repositorio participaran en una decisión de seguridad.

**Alternatives considered**:
- Reutilizar `BlockEventBus`: acopla seguridad a reglas y complica la prioridad.
- Callback directo UI→coordinador: rompe el desacoplamiento.

### D4. Overlay de protección no desbloqueable

**Decision**: Extender `FrictionConfig` con `mode: FrictionMode` (`STANDARD`,
`PROTECTION`). En modo `PROTECTION`, `FrictionScreen` muestra una pantalla oscura
de bloqueo sin temporizador, campo ni botón de desbloqueo. El coordinador la
mantiene mientras `currentThreat` no sea nulo y la retira cuando la persona
abandona la pantalla sensible.

**Rationale**: El objetivo es impedir la interacción con opciones de
desactivación. Permitir el flujo normal de 60 s + transcripción abriría una vía
directa para desactivar la protección.

**Alternatives considered**:
- Reutilizar el flujo estándar: permite desbloquear y desactivar.
- Re-mostrar el overlay tras cada desbloqueo: parpadeo y ventana de interacción.

### D5. Estado de protección principal + estado de arranque seguro

**Decision**:
- Mantener el estado principal en DataStore de credenciales.
- Escribir una copia mínima `protection_enabled` en un DataStore sobre
  `createDeviceProtectedStorageContext()` para `LOCKED_BOOT_COMPLETED`.
- Si la copia segura no está disponible, diferir el arranque a `BOOT_COMPLETED`.

**Rationale**: El almacenamiento principal no está disponible antes del
desbloqueo. Una copia mínima permite decidir si arrancar la monitorización sin
exponer datos sensibles.

**Alternatives considered**:
- Solo DataStore principal: `LOCKED_BOOT_COMPLETED` no puede leerlo.
- SharedPreferences: prohibido por las restricciones del proyecto.
- Room en almacenamiento directo: sobredimensionado para un booleano.

### D6. `BootReceiver` con `goAsync` y arranque condicionado

**Decision**: El receiver usará `goAsync()` + corrutina para leer el estado,
iniciar `MonitoringForegroundService` solo si la protección estaba activa y
recalibrar permisos/servicios. Capturará `ForegroundServiceStartNotAllowedException`
y otras excepciones de plataforma.

**Rationale**: Las APIs de DataStore son suspend y el receptor tiene un tiempo
limitado. El arranque debe ser condicional para no reactivar la protección si el
usuario la desactivó.

**Alternatives considered**:
- `runBlocking` en `onReceive`: riesgo de ANR.
- Iniciar siempre el servicio: viola el estado del usuario y gasta batería.

### D7. Recalibración de salud de protección

**Decision**: Crear `ProtectionHealthChecker` en `:app` que verifique:
accesibilidad habilitada, permiso de superposición y administrador activo.
`BootReceiver` lo ejecuta tras el arranque y registra el diagnóstico.

**Rationale**: Un reinicio no garantiza que los permisos sigan concedidos. La
recalibración permite decidir si la protección es efectiva sin bloquear el
arranque.

### D8. DeviceAdminReceiver resuelto con EntryPoint de Hilt

**Decision**: `BlockerDeviceAdminReceiver` obtendrá sus dependencias mediante
`EntryPointAccessors` en lugar de depender de inyección generada para
`BroadcastReceiver`.

**Rationale**: Evita fragilidad en la generación de componentes Hilt para
receptores y permite emitir eventos/estado desde callbacks del sistema.

**Alternatives considered**:
- `@AndroidEntryPoint` en el receiver: posible, pero más acoplado al soporte de
  Hilt para BroadcastReceiver y a la generación de clases.
- Singleton global mutable: difícil de testear.

### D9. Android 14+: mejor esfuerzo, sin evadir restricciones

**Decision**: Mantener el permiso `SYSTEM_ALERT_WINDOW`, el FGS `specialUse` y
capturar fallos de arranque/overlay. No se intenta evadir las restricciones de
background del sistema.

**Rationale**: Android 14+ puede impedir inicios de FGS desde background y ciertos
overlays. La app debe degradar de forma controlada y registrar el diagnóstico.

### D10. Pruebas de interceptor, estado y receptores

**Decision**:
- Pruebas del `SettingsInterceptor` con paquetes/clases AOSP y variantes.
- Pruebas de `ProtectionEventBus`/coordinador para prioridad y cierre al salir.
- Pruebas del estado de protección (principal y boot-safe) con DataStore.
- Pruebas de `BootReceiver` con `Robolectric` para eventos y servicio
  condicionado.
- Validación manual de device admin, accesibilidad real y restricciones Android
  14+ en dispositivo.
