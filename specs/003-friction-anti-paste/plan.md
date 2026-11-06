# Implementation Plan: Pantalla de fricción y anti-portapapeles

**Branch**: `003-friction-anti-paste` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-friction-anti-paste/spec.md`

## Summary

Reemplazar la pantalla de fricción actual por una experiencia restrictiva de
60 segundos con transcripción exacta de un párrafo fijo de ~300 palabras,
anti-paste nativo y un evento de desbloqueo que el coordinador del overlay
procesa. La sesión de fricción se conserva fuera de la composición para
sobrevivir a rotaciones y recreaciones de la vista.

No se modifica la base de datos ni el esquema de reglas. El trabajo se concentra
en `:core:overlay` y en el contrato `OverlayController` de `:core:common`.

## Technical Context

**Language/Version**: Kotlin 2.4.21, JVM 17, AGP 9.5.0, Gradle 9.6.0

**Primary Dependencies**: Jetpack Compose (BOM 2026.09.00), Material 3,
`androidx.activity:activity-compose` (BackHandler), Hilt 2.60.1, Coroutines/Flow
1.11.0. Sin librerías externas de portapapeles/teclado.

**Storage**: Ninguno para la fricción; la sesión vive en memoria en
`OverlayManager`. Room/DataStore no se modifican.

**Testing**: JUnit 4.13.2, `kotlinx-coroutines-test` 1.11.0, Robolectric 4.17 y
pruebas existentes de `:core:overlay`/`:core:database`. Pruebas de lógica pura
para input/temporizador, pruebas del coordinador para unlock; validación manual
de IME, selección y rotación.

**Target Platform**: Android API 26+ (`minSdk = 26`, `compileSdk`/`targetSdk = 37`)

**Project Type**: Aplicación móvil Android multi-módulo con Clean Architecture

**Performance Goals**: Contador actualizado al menos cada segundo; feedback de
pegado en menos de 200 ms; UI fluida durante la transcripción; sin bloqueos del
hilo principal.

**Constraints**: Temporizador estricto de 60 000 ms; transcripción exacta
carácter por carácter; anti-paste con inserción >3 caracteres; sin cierre
inmediato; portapapeles y toolbar nativos; estado preservado ante rotación
cuando el sistema lo permita.

**Scale/Scope**: Una sesión de fricción activa a la vez; texto objetivo de ~300
palabras; entorno de un solo usuario/dispositivo.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` sigue siendo la plantilla sin ratificar. No
existen principios, gates ni gobernanza ejecutables, por lo que no hay
violaciones que justificar.

Gates derivados de la arquitectura y la especificación:

| Gate | Estado | Evidencia |
|---|---|---|
| La UI no acopla al coordinador | PASS | La pantalla emite `onUnlock`; el coordinador consume `OverlayController.unlockEvents` |
| Temporizador estricto y sin reinicio por recomposición | PASS | `startedAtElapsedRealtime` + tiempo restante calculado |
| Anti-paste nativo sin terceros | PASS | `LocalTextToolbar` no-op, `ClipboardManager`, detección por diff |
| Sin cierre inmediato | PASS | Se elimina el botón de cierre y se consume el back |
| Estado preservado ante rotación | PASS | `FrictionSession` retenida en `OverlayManager` |
| Sin cambios de base de datos | PASS | No se tocan Room/DataStore |
| Testabilidad | PASS | Lógica de input/sesión pura + coordinador con dobles |

**Post-Phase 1 re-check**: PASS. El modelo de sesión, los contratos de unlock y
la estrategia de estado no introducen dependencias circulares ni persistencia
innecesaria.

## Project Structure

### Documentation (this feature)

```text
specs/003-friction-anti-paste/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── friction-screen-contracts.md
└── tasks.md              # Generado por /speckit.tasks
```

### Source Code (repository root)

```text
core/common/src/main/kotlin/com/blockbuster/core/common/contract/
└── OverlayController.kt       # Añadir unlockEvents + OverlayUnlock

core/overlay/src/main/kotlin/com/blockbuster/core/overlay/
├── FrictionScreen.kt          # Pantalla de fricción completa
├── FrictionSession.kt         # Sesión, remainingMillis, canSubmit
├── FrictionInput.kt           # Detección de pegado por diferencia
├── OverlayComposeHost.kt      # Owners, BackHandler, clipboard
├── OverlayManager.kt          # Sesión retenida + emisión de unlock
├── OverlayCoordinator.kt      # Colecta unlockEvents y cierra
├── OverlayState.kt            # Existente
└── di/OverlayModule.kt        # Sin cambios de bindings nuevos

core/overlay/src/main/res/values/
├── strings.xml                # Textos de UI/aviso
└── friction_texts.xml         # Párrafo objetivo de ~300 palabras

core/overlay/src/test/kotlin/com/blockbuster/core/overlay/
├── FrictionInputTest.kt
├── FrictionSessionTest.kt
├── OverlayCoordinatorUnlockTest.kt
└── FrictionScreenTest.kt      # Si el entorno Compose lo permite

core/overlay/build.gradle.kts  # activity-compose/UI test deps si aplica
```

**Structure Decision**: La lógica de fricción permanece en `:core:overlay`; el
contrato de unlock se añade a `OverlayController` en `:core:common`; el
coordinador reacciona al evento sin conocer Compose. El contenido del texto
objetivo se versiona como recurso.

## Implementation Phases

### Phase A — Contratos y lógica pura

1. Añadir `OverlayUnlock` y `unlockEvents` a `OverlayController`.
2. Crear `FrictionSession` con `remainingMillis`, `remainingSeconds` y
   `canSubmit`.
3. Crear `FrictionInput.insertedCharacterCount`/`isPaste`.
4. Pruebas unitarias de sesión e input.

### Phase B — FrictionScreen

1. Reescribir `FrictionScreen` con fondo oscuro, contador, texto objetivo,
   `OutlinedTextField` y botón único.
2. Implementar `LocalTextToolbar` no-op, selección colapsada y aviso temporal.
3. Aplicar `imePadding`/scroll para el campo y el párrafo largo.
4. Añadir `BackHandler` para consumir atrás.

### Phase C — Host, manager y portapapeles

1. Extender `OverlayComposeHost` para recibir `FrictionSession` y callbacks.
2. Añadir `OnBackPressedDispatcherOwner` y limpieza de portapapeles nativa.
3. Retener la sesión en `OverlayManager`, reutilizarla al recrear y reiniciarla
   para un target nuevo.
4. Emitir `OverlayUnlock` desde el manager; no cerrar directamente desde la UI.

### Phase D — Coordinador

1. Colectar `unlockEvents` en `OverlayCoordinator`.
2. Limpiar activación/descarte/auditoría y ocultar el overlay al desbloquear.
3. Evitar que el desbloqueo se interprete como `Dismissed`.

### Phase E — Contenido y validación

1. Redactar y empaquetar el párrafo de ~300 palabras.
2. Pruebas del coordinador para unlock y no reauditoría.
3. `:core:overlay:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`.
4. Validación manual de teclado, portapapeles, rotación y back.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Compose no expone `textIsSelectable` | Selección parcialmente posible | Toolbar no-op, selección colapsada y anti-paste por diff; documentar límite |
| Limpiar el portapapeles es intrusivo | Pérdida de contenido del usuario | Aceptado como fricción explícita; solo mientras el overlay está activo |
| Autocompletado/dictado se detecta como paste | Falsos positivos | Comportamiento intencional: se exige escritura manual; documentado |
| Rotación no recrea la vista | Estado no se prueba | La sesión en manager sigue siendo fuente de verdad; prueba manual |
| Muerte del proceso | Pérdida de progreso | Best-effort documentado; sin persistencia en disco |
| Texto de 300 palabras dificulta la UI | Scroll/teclado incómodos | `imePadding`, contenedor scrollable y tipografía legible |
| Back navega a la app de fondo | Evasión de fricción | `BackHandler` + `OnBackPressedDispatcherOwner` propio |
| Deriva del contador | Temporizador impreciso | Cálculo desde `elapsedRealtime`, no suma de ticks |

## Out of Scope

- Persistencia del progreso tras muerte del proceso.
- Exenciones de accesibilidad o modos de "saltar fricción".
- Traducciones adicionales del texto objetivo.
- Librerías externas de portapapeles/teclado.
- Cambios en reglas, Room o DataStore.
