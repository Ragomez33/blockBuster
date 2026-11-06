# Implementation Plan: Coordinador de bloqueos y activación del Overlay

**Branch**: `002-overlay-block-coordinator` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-overlay-block-coordinator/spec.md`

## Summary

Implementar un coordinador reactivo que reciba detecciones de accesibilidad,
evalúe el objetivo (URL o keyword) contra las reglas activas persistidas y active
una pantalla de fricción a pantalla completa mediante `WindowManager`. Cada
activación se audita una sola vez en Room.

El trabajo se concentra en `:core:overlay`, `:core:common` y `:app`, con una
extensión mínima en `:core:accessibility` para transportar el objetivo detectado.
No se modifica el esquema de base de datos: se reutilizan `BlockRule`,
`BlockLog` y `BlockRulesRepository`.

## Technical Context

**Language/Version**: Kotlin 2.4.21, JVM 17, AGP 9.5.0, Gradle 9.6.0

**Primary Dependencies**: Hilt 2.60.1, Jetpack Compose (BOM 2026.09.00),
Kotlin Coroutines/Flow 1.11.0, Room 2.8.5, DataStore Preferences 1.3.0

**Storage**: Room existente (`block_rules`, `block_logs`) para reglas y
auditoría; DataStore para el temporizador. Sin migraciones nuevas.

**Testing**: JUnit 4.13.2, `kotlinx-coroutines-test` 1.11.0, Robolectric 4.17 y
pruebas existentes de `:core:database`. Pruebas del matcher y coordinador con
dobles; prueba de humo del host Compose si el entorno lo permite.

**Target Platform**: Android API 26+ (`minSdk = 26`, `compileSdk`/`targetSdk = 37`)

**Project Type**: Aplicación móvil Android multi-módulo con Clean Architecture

**Performance Goals**: Overlay visible en menos de 1 segundo desde la detección;
evaluación de reglas sin bloquear el hilo principal; sin ventanas duplicadas en
ráfagas de eventos.

**Constraints**: Evaluación asíncrona sobre IO/Default fuera del hilo principal;
`Flow` para reactividad; `WindowManager` con `TYPE_APPLICATION_OVERLAY`;
`ComposeView` con lifecycle/saved-state propios; auditoría mediante
`BlockLogEntity`; sin prometer supresión absoluta de Home/Recientes.

**Scale/Scope**: Decenas a cientos de reglas; eventos de accesibilidad en
ráfagas de 150 ms; una activación auditada por transición de bloqueo.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` sigue siendo la plantilla sin ratificar. No
existen principios, gates ni gobernanza ejecutables, por lo que no hay
violaciones que justificar.

Gates derivados de la arquitectura y la especificación:

| Gate | Estado | Evidencia |
|---|---|---|
| El coordinador no depende de Compose/WindowManager | PASS | Depende de `BlockEventBus`, `BlockRulesRepository`, `OverlayController` y matcher |
| Evaluación fuera del hilo principal | PASS | Reglas observadas por Flow Room; coordinador en `@OverlayScope` (Default) |
| Reactividad con Flow | PASS | `currentDetection`, `observeActiveRules`, `isOverlayVisible` |
| Auditoría local desacoplada | PASS | `repository.recordBlock(...)`; fallo no bloquea overlay |
| Ciclo de vida seguro de la vista | PASS | Owners propios + `disposeComposition` + `removeViewImmediate` |
| Sin cambios de esquema injustificados | PASS | No se crean tablas ni migraciones |
| Módulos y límites respetados | PASS | Contratos en common; implementación en overlay/accessibility |

**Post-Phase 1 re-check**: PASS. Los contratos y el modelo de estado no
introducen dependencias circulares ni acceso directo a Room desde la UI.

## Project Structure

### Documentation (this feature)

```text
specs/002-overlay-block-coordinator/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── overlay-coordinator-contracts.md
└── tasks.md              # Generado por /speckit.tasks
```

### Source Code (repository root)

```text
core/common/src/main/kotlin/com/blockbuster/core/common/
├── model/
│   ├── DetectionEvent.kt          # Añadir target opcional
│   └── DetectionSignal.kt         # Nuevo estado de detección normalizada
├── event/
│   └── BlockEventBus.kt           # currentDetection + publicación
└── contract/
    └── OverlayController.kt       # Semántica documentada; sin cambios de forma

core/accessibility/src/main/kotlin/com/blockbuster/core/accessibility/
├── BlockBusterAccessibilityService.kt  # Publicar target extraído
└── target/
    ├── TargetExtractor.kt
    └── DefaultTargetExtractor.kt

core/overlay/src/main/kotlin/com/blockbuster/core/overlay/
├── OverlayCoordinator.kt          # Coordinador reactivo y auditoría
├── OverlayManager.kt              # WindowManager, flags y touch
├── OverlayComposeHost.kt          # Lifecycle/dispose sin fugas
├── FrictionScreen.kt              # UI Compose existente
├── OverlayState.kt                # Idle/Blocking/Dismissed
├── matching/
│   ├── BlockRuleMatcher.kt
│   └── DefaultBlockRuleMatcher.kt
└── di/
    └── OverlayModule.kt           # Binding del matcher/coordinator

core/overlay/src/test/kotlin/com/blockbuster/core/overlay/
├── matching/
│   └── DefaultBlockRuleMatcherTest.kt
└── OverlayCoordinatorTest.kt

app/src/main/kotlin/com/blockbuster/app/
├── BlockBusterApplication.kt      # Arranque idempotente del coordinador
└── service/MonitoringForegroundService.kt

app/src/main/res/values/strings.xml  # Textos de fricción/estado si aplica
```

**Structure Decision**: La lógica de decisión vive en `:core:overlay`; el bus y
los modelos compartidos en `:core:common`; la extracción de objetivo en
`:core:accessibility`. `:app` solo ensambla y arranca el coordinador. No se toca
el esquema de `:core:database`.

## Implementation Phases

### Phase A — Contratos y detección

1. Añadir `target` a `DetectionEvent.ScreenDetected` con default `null`.
2. Crear `DetectionSignal` y `currentDetection` en `BlockEventBus`.
3. Crear `TargetExtractor` y `DefaultTargetExtractor` en `:core:accessibility`.
4. Publicar `target` desde `BlockBusterAccessibilityService` sin bloquear el
   hilo principal.

### Phase B — Matcher y estado

1. Crear `BlockRuleMatcher` y `DefaultBlockRuleMatcher`.
2. Definir `OverlayState` (`Idle`, `Blocking`, `Dismissed`) y `BlockMatch`.
3. Cubrir dominios, subdominios, keywords, objetivo nulo y precedencia con
   pruebas unitarias.

### Phase C — Coordinador

1. Reescribir `OverlayCoordinator` para combinar detección, reglas activas,
   visibilidad del overlay y estado de descarte.
2. Implementar deduplicación de activaciones y auditoría única.
3. Manejar cierre manual, cambios de reglas y desconexión del servicio.
4. Exponer `state: StateFlow<OverlayState>` para diagnóstico/pruebas.

### Phase D — Overlay robusto

1. Ajustar `OverlayManager`: flags de pantalla completa, touch, cutout y
   reemplazo idempotente.
2. Endurecer `OverlayComposeHost`: transiciones de lifecycle, `disposeComposition`
   y `removeViewImmediate`.
3. Añadir inmersión best-effort y documentar la limitación de Home/Recientes.

### Phase E — Integración y auditoría

1. Conectar `repository.recordBlock(...)` en la transición de activación.
2. Asegurar que el fallo de auditoría no afecte el overlay.
3. Verificar el arranque idempotente desde `Application` y
   `MonitoringForegroundService`.

### Phase F — Validación

1. Pruebas del matcher y coordinador con dobles.
2. Prueba de humo de lifecycle del overlay con Robolectric si es viable.
3. `:core:overlay:testDebugUnitTest`, `:core:database:testDebugUnitTest`,
   `:app:assembleDebug` y `:app:lintDebug`.
4. Validación manual en dispositivo de interacción táctil, cierre y
   Home/Recientes.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Extracción de URL no uniforme entre apps | No se bloquea si no hay target | Documentar; extraer de texto/root con límites; fallback a no bloquear |
| Home/Recientes no se pueden suprimir con overlay normal | Expectativa incumplida | Inmersión best-effort; documentar necesidad de kiosco/device owner |
| Fugas del ComposeView | Memoria y ventanas huérfanas | Owners por instancia, `disposeComposition`, `removeViewImmediate`, limpiar refs |
| Eventos repetidos generan auditorías duplicadas | Estadísticas infladas | Clave de activación y transición de estado |
| WindowManager falla al añadir vista | Crash o bloqueo fantasma | `runCatching`, limpieza de host y diagnóstico |
| Reglas cambian durante el overlay | Overlay obsoleto | Recolección reactiva de `observeActiveRules` |
| Coordinador bloquea el hilo principal | ANR/jank | Flow Room + `@OverlayScope`, mutaciones de ventana en main solo al mostrar/ocultar |

## Out of Scope

- Supresión absoluta de Home/Recientes sin device owner/kiosco.
- Extracción de URL desde WebView o integraciones por navegador.
- Historial visual de auditoría o estadísticas de UI.
- Nuevas tablas o migraciones Room.
- Cambios en las reglas de puntuación o límites diarios.
