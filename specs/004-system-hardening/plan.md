# Implementation Plan: Hardening del sistema y prevención de desinstalación

**Branch**: `004-system-hardening` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-system-hardening/spec.md`

## Summary

Añadir una capa de protección del sistema que detecte pantallas sensibles de
Ajustes, responda a intentos de desactivación del administrador y rearme la
monitorización tras reinicios o actualizaciones. Las amenazas de seguridad se
emiten por un bus de alta prioridad y el overlay se muestra en modo no
desbloqueable mientras la pantalla sensible permanezca en primer plano.

La app seguirá respetando las limitaciones de Android: no puede impedir de forma
absoluta la remoción del administrador ni el apagado del servicio de
accesibilidad. Se implementa la mejor respuesta posible, con manejo controlado de
restricciones Android 14+ y estado boot-safe en DataStore.

## Technical Context

**Language/Version**: Kotlin 2.4.21, JVM 17, AGP 9.5.0, Gradle 9.6.0

**Primary Dependencies**: Hilt 2.60.1, AndroidX AppCompat/Activity, Compose
(BOM 2026.09.00), Room 2.8.5, DataStore Preferences 1.3.0, Coroutines/Flow 1.11.0

**Storage**: DataStore principal para estado de protección + DataStore sobre
`createDeviceProtectedStorageContext()` para el estado boot-safe. Room no cambia.

**Testing**: JUnit 4.13.2, `kotlinx-coroutines-test` 1.11.0, Robolectric 4.17.
Pruebas del interceptor, bus, coordinador, estado DataStore y receptores; pruebas
manuales de device admin, accesibilidad y restricciones Android 14+.

**Target Platform**: Android API 26+ (`minSdk = 26`, `compileSdk`/`targetSdk = 37`)

**Project Type**: Aplicación móvil Android multi-módulo con Clean Architecture

**Performance Goals**: Overlay de protección en menos de 1 segundo desde la
detección; arranque de monitorización en menos de 30 segundos tras
`BOOT_COMPLETED`; sin ANR por DataStore en receptores.

**Constraints**: Detección por paquete/clase; amenazas de alta prioridad sin
evaluación de reglas; overlay no desbloqueable en pantallas sensibles; estado
boot-safe; `goAsync` en receptores; manejo de FGS/overlay restringido en Android
14+; ninguna garantía de bloqueo absoluto de desinstalación.

**Scale/Scope**: Un dispositivo por usuario; una pantalla sensible activa a la
vez; tres categorías de pantallas; tres acciones de arranque; estado booleano de
protección.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` sigue siendo la plantilla sin ratificar. No
existen principios, gates ni gobernanza ejecutables, por lo que no hay
violaciones que justificar.

Gates derivados de la arquitectura y la especificación:

| Gate | Estado | Evidencia |
|---|---|---|
| Detección desacoplada de UI | PASS | `SettingsInterceptor` + `ProtectionEventBus` |
| Prioridad de seguridad | PASS | Colector separado en `OverlayCoordinator` |
| Sin cambios de esquema Room | PASS | Solo DataStore |
| Estado boot-safe | PASS | DataStore en contexto protegido por dispositivo |
| Receptores sin ANR | PASS | `goAsync` + coroutines |
| Manejo Android 14+ | PASS | `runCatching`, diagnóstico, sin evasión |
| Limitaciones honestas | PASS | Device admin removal es best-effort |
| Testabilidad | PASS | Interceptor puro + estado/bus/receptores con Robolectric |

**Post-Phase 1 re-check**: PASS. Los contratos y el modelo de estado no
introducen dependencias circulares ni promesas imposibles.

## Project Structure

### Documentation (this feature)

```text
specs/004-system-hardening/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── hardening-contracts.md
└── tasks.md              # Generado por /speckit.tasks
```

### Source Code (repository root)

```text
core/common/src/main/kotlin/com/blockbuster/core/common/
├── model/
│   ├── SensitiveSystemScreen.kt
│   ├── ProtectionThreat.kt
│   └── ProtectionState.kt
├── event/
│   └── ProtectionEventBus.kt
└── contract/
    └── ProtectionStateRepository.kt

core/accessibility/src/main/kotlin/com/blockbuster/core/accessibility/
├── settings/
│   ├── SettingsInterceptor.kt
│   └── DefaultSettingsInterceptor.kt
└── BlockBusterAccessibilityService.kt   # Inspección y publicación

core/database/src/main/kotlin/com/blockbuster/core/database/
├── protection/
│   ├── ProtectionStateRepositoryImpl.kt
│   ├── ProtectionDataStore.kt
│   └── BootProtectionDataStore.kt
└── di/DatabaseModule.kt

core/overlay/src/main/kotlin/com/blockbuster/core/overlay/
├── OverlayCoordinator.kt      # Colector prioritario y estado
├── OverlayState.kt            # ProtectionBlocking
├── FrictionScreen.kt          # Modo PROTECTION sin desbloqueo
└── OverlayManager.kt          # FrictionConfig mode

core/common/src/main/kotlin/com/blockbuster/core/common/contract/
└── OverlayController.kt       # FrictionMode/PROTECTION

app/src/main/
├── AndroidManifest.xml        # BlockerDeviceAdminReceiver + BootReceiver
├── res/xml/device_admin_policies.xml
├── kotlin/com/blockbuster/app/
│   ├── admin/BlockerDeviceAdminReceiver.kt
│   ├── receiver/BootReceiver.kt
│   ├── protection/ProtectionHealthChecker.kt
│   └── service/MonitoringForegroundService.kt
└── src/test/...               # Pruebas de receptores/estado

core/accessibility/src/test/.../settings/DefaultSettingsInterceptorTest.kt
core/overlay/src/test/.../OverlayCoordinatorProtectionTest.kt
core/database/src/test/.../ProtectionStateRepositoryTest.kt
```

**Structure Decision**: La clasificación de pantallas vive en
`:core:accessibility`; el bus y modelos en `:core:common`; el estado en
`:core:database`; el overlay/coordinador en `:core:overlay`; los componentes de
sistema (DeviceAdmin, Boot, salud) en `:app`. No se toca Room.

## Implementation Phases

### Phase A — Contratos y estado

1. Crear `SensitiveSystemScreen`, `ProtectionThreat`, `ProtectionState`.
2. Crear `ProtectionEventBus` y su implementación.
3. Crear `ProtectionStateRepository` y la implementación con DataStore principal
   y boot-safe.
4. Añadir `FrictionMode`/`ProtectionBlocking` a los contratos de overlay.

### Phase B — Detección de pantallas sensibles

1. Implementar `SettingsInterceptor` con heurísticas AOSP/fabricantes.
2. Integrarlo en `BlockBusterAccessibilityService`; actualizar/limpiar
   `currentThreat` en cada cambio de ventana.
3. Pruebas del interceptor para accesibilidad, app info y device admin.

### Phase C — Overlay prioritario

1. Añadir `ProtectionBlocking` y `FrictionMode.PROTECTION`.
2. Implementar `FrictionScreen` en modo protección (sin desbloqueo ni timer).
3. Extender `OverlayCoordinator` con colector de amenazas y transición
   Idle/ProtectionBlocking.
4. Asegurar ocultamiento al salir de la pantalla sensible y no duplicar ventanas.

### Phase D — DeviceAdmin

1. Renombrar/crear `BlockerDeviceAdminReceiver`.
2. Crear `device_admin_policies.xml` con `force-lock` y `limit-password`.
3. Implementar `onEnabled`, `onDisableRequested`, `onDisabled` con EntryPoint
   Hilt, amenaza de alta prioridad y diagnóstico.
4. Actualizar manifiesto.

### Phase E — Boot, estado y salud

1. Extender `BootReceiver` a `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` y
   `MY_PACKAGE_REPLACED`; usar `goAsync`.
2. Consultar `ProtectionStateRepository` y arrancar FGS solo si está activo.
3. Implementar `ProtectionHealthChecker`.
4. Manejar restricciones Android 14+ y almacenamiento no disponible.

### Phase F — Validación

1. Pruebas JVM/Robolectric de interceptor, estado, coordinador y receptores.
2. `:core:accessibility:testDebugUnitTest`, `:core:overlay:testDebugUnitTest`,
   `:core:database:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`.
3. Validación manual en dispositivo de ajustes sensibles, admin, boot, locked
   boot, package replaced y permisos faltantes.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| El usuario/sistema puede quitar el admin | Protección evadible | Respuesta best-effort, advertencia y trazabilidad; no prometer bloqueo absoluto |
| Variantes de clases de Settings por fabricante | Detección incompleta | Heurísticas de paquete/clase + fallback sin bloqueo |
| Detección posterior al toque | Ventana de evasión | Overlay prioritario y re-emisión al permanecer en la pantalla |
| `LOCKED_BOOT_COMPLETED` sin almacenamiento | No se puede decidir el arranque | Copia boot-safe + diferir a `BOOT_COMPLETED` |
| Android 14+ bloquea FGS/overlay | Protección no visible | `runCatching`, diagnóstico, sin bucles |
| DataStore en receptor | ANR | `goAsync` + coroutines |
| Overlay de protección atrapa al usuario | Mala UX | Se retira al salir de la pantalla sensible; Home sigue disponible |
| Duplicación de overlays/servicios | Ventanas/consumo | Estado idempotente y comprobaciones de host |

## Out of Scope

- Garantizar la imposibilidad absoluta de desinstalar o desactivar la app.
- Evadir restricciones de Android 14+ o usar privilegios de sistema/root.
- Cobertura del 100 % de variantes OEM de Ajustes.
- Ruta de escape oculta o bypass de la protección.
- Cambios en el esquema de reglas/Room.
