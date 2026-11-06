# Tasks: Pantalla de fricción y anti-portapapeles

**Input**: Design documents from `specs/003-friction-anti-paste/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/friction-screen-contracts.md`, `quickstart.md`

**Tests**: Incluidos porque `plan.md` (Phase E) y `quickstart.md` los requieren
para validar temporizador, coincidencia, anti-paste, unlock y estado.

**Organization**: Tareas agrupadas por historia de usuario para permitir
implementación y validación independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario (`US1`, `US2`, `US3`)
- Todas las tareas incluyen rutas de archivo exactas

## Path Conventions

- Contratos: `core/common/src/main/kotlin/com/blockbuster/core/common/contract/`
- Overlay: `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/`
- Recursos: `core/overlay/src/main/res/values/`
- Pruebas: `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/`
- Build: `core/overlay/build.gradle.kts`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencias y configuración necesarias para la nueva pantalla.

- [X] T001 [P] Agregar `implementation(libs.androidx.activity.compose)` en `core/overlay/build.gradle.kts` para habilitar `BackHandler` y `OnBackPressedDispatcherOwner`
- [X] T002 [P] Verificar que `core/overlay/build.gradle.kts` mantiene `testOptions { unitTests.isIncludeAndroidResources = true }` y las dependencias de Coroutines Test/Robolectric para las pruebas de la pantalla

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Contratos, sesión, detección de pegado y contenido objetivo compartidos.

**⚠️ CRITICAL**: No iniciar US1, US2 ni US3 hasta completar esta fase.

- [X] T003 [P] Añadir `OverlayUnlock(target, timestampEpochMillis)` y `unlockEvents: SharedFlow<OverlayUnlock>` a `core/common/src/main/kotlin/com/blockbuster/core/common/contract/OverlayController.kt`; actualizar `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/support/FakeOverlayController.kt` para implementar el flujo
- [X] T004 [P] Crear `FrictionSession` con `target`, `requiredText`, `startedAtElapsedRealtime`, `input`, `warningVisible`, `warningUntilElapsedRealtime`, además de `remainingMillis`, `remainingSeconds` y `canSubmit` en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/FrictionSession.kt`
- [X] T005 [P] Crear `FrictionInput` con `insertedCharacterCount(oldText, newText)` y `isPaste` (inserción > 3) en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/FrictionInput.kt`
- [X] T006 [P] Crear el recurso `friction_discipline_text` con un párrafo fijo en español de aproximadamente 300 palabras sobre control de impulsos y enfoque en `core/overlay/src/main/res/values/friction_texts.xml`
- [X] T007 [P] Añadir textos de botón/aviso/contador en `core/overlay/src/main/res/values/strings.xml` (por ejemplo `friction_unlock`, `friction_paste_warning`, `friction_timer_remaining`)

**Checkpoint**: Sesión, input y contenido listos; las historias pueden comenzar.

---

## Phase 3: User Story 1 - Completar la fricción de 60 segundos y la transcripción (Priority: P1) 🎯 MVP

**Goal**: Pantalla restrictiva con contador de 60 s, transcripción exacta y botón
habilitado solo al cumplir ambas condiciones; el desbloqueo llega al coordinador.

**Independent Test**: Abrir la fricción, comprobar el botón deshabilitado durante
60 s, verificar que sigue deshabilitado con texto incorrecto y que se habilita
solo con coincidencia exacta a los 0 s; pulsar y comprobar el cierre.

### Tests for User Story 1

- [X] T008 [P] [US1] Crear pruebas de `FrictionSession` para `remainingMillis`, `remainingSeconds`, `canSubmit` y el umbral de 60 000 ms en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/FrictionSessionTest.kt`
- [X] T009 [P] [US1] Crear pruebas del coordinador para `unlockEvents`: ocultar overlay, volver a `Idle`, limpiar activación y no generar auditoría nueva en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/OverlayCoordinatorUnlockTest.kt`

### Implementation for User Story 1

- [X] T010 [US1] Reescribir `FrictionScreen` con fondo oscuro a pantalla completa, contador visible de 60 s, texto objetivo, `OutlinedTextField`, botón único deshabilitado hasta tiempo 0 + coincidencia exacta, sin acción de cierre y con `BackHandler` en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/FrictionScreen.kt`
- [X] T011 [US1] Extender `OverlayComposeHost` para recibir `FrictionSession`, `onInputChanged` y `onUnlock`, proveer `OnBackPressedDispatcherOwner` y pasar el texto objetivo desde recursos en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayComposeHost.kt`
- [X] T012 [US1] Extender `OverlayManager` para crear la sesión al mostrar, pasarla al host, emitir `OverlayUnlock` al completar y limpiar la sesión al ocultar en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayManager.kt`
- [X] T013 [US1] Colectar `unlockEvents` en `OverlayCoordinator`, limpiar activación/descarte/auditoría, ocultar el overlay y publicar `OverlayState.Idle` en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`
- [X] T014 [US1] Ejecutar `./gradlew :core:overlay:testDebugUnitTest :app:assembleDebug` y corregir los fallos de US1 en `core/overlay/src/` y `core/common/src/`

**Checkpoint**: US1 funcional y testeable de forma independiente. Es el MVP.

---

## Phase 4: User Story 2 - Impedir el pegado de texto (Priority: P2)

**Goal**: Deshabilitar acciones contextuales, detectar inserciones de más de 3
caracteres, vaciar el campo y mostrar un aviso temporal sin detener el contador.

**Independent Test**: Pegar un texto de más de tres caracteres y comprobar que el
campo queda vacío, aparece el aviso y el contador sigue; teclear manualmente y
comprobar que no se activa el aviso.

### Tests for User Story 2

- [X] T015 [P] [US2] Crear pruebas de `FrictionInput` para inserción al final, en medio, reemplazo de selección, exactamente 3 caracteres y 4+ caracteres en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/FrictionInputTest.kt`

### Implementation for User Story 2

- [X] T016 [US2] Implementar en `FrictionScreen` el `LocalTextToolbar` no-op, `TextFieldValue` con selección colapsada y el manejo de `onValueChange` que vacía el campo y activa el aviso cuando `FrictionInput.isPaste` en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/FrictionScreen.kt`
- [X] T017 [US2] Añadir el aviso temporal de 3 s y limpiar el portapapeles nativo con `ClipboardManager` al mostrar la fricción y tras un pegado en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/FrictionScreen.kt` y `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayComposeHost.kt`
- [X] T018 [US2] Ejecutar `./gradlew :core:overlay:testDebugUnitTest :app:assembleDebug` y corregir los fallos de anti-paste en `core/overlay/src/`

**Checkpoint**: US1 y US2 funcionan; el pegado no permite evadir la transcripción.

---

## Phase 5: User Story 3 - Conservar el progreso ante rotaciones (Priority: P3)

**Goal**: Mantener tiempo restante y texto al recrear la vista, y reiniciar solo
para una activación de otro objetivo.

**Independent Test**: Iniciar la fricción, escribir parte del texto, rotar y
comprobar que el tiempo y el texto se conservan; abrir otro objetivo y verificar
el reinicio a 60 s.

### Tests for User Story 3

- [X] T019 [P] [US3] Crear pruebas de reutilización de sesión y reinicio por nuevo target en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/FrictionSessionReuseTest.kt`

### Implementation for User Story 3

- [X] T020 [US3] Reutilizar la `FrictionSession` existente cuando `OverlayManager` recibe el mismo target y crear una nueva (startedAt actual, input vacío) para un target distinto en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayManager.kt`
- [X] T021 [US3] Inicializar el estado de `FrictionScreen` desde la sesión retenida y usar `rememberSaveable` solo para el aviso/estado transitorio, sin reiniciar el contador en recomposiciones en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/FrictionScreen.kt` y `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayComposeHost.kt`
- [X] T022 [US3] Ejecutar `./gradlew :core:overlay:testDebugUnitTest :app:assembleDebug` y corregir los fallos de rotación/recreación en `core/overlay/src/`

**Checkpoint**: Las tres historias funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Contenido, documentación, validación integral y limitaciones.

- [X] T023 [P] Verificar que `friction_discipline_text` tiene aproximadamente 300 palabras, es estable y está referenciado por `FrictionScreen` en `core/overlay/src/main/res/values/friction_texts.xml`
- [X] T024 [P] Actualizar `README.md` y `specs/003-friction-anti-paste/quickstart.md` con el flujo final, comandos y limitaciones (selección parcial, portapapeles, rotación, IME)
- [X] T025 Ejecutar `./gradlew :core:overlay:testDebugUnitTest :core:database:testDebugUnitTest :app:assembleDebug :app:lintDebug` y corregir fallos restantes en `core/overlay/` y `app/`
- [X] T026 [P] Ejecutar los escenarios automatizables de `specs/003-friction-anti-paste/quickstart.md` y registrar resultados/limitaciones en ese archivo
- [X] T027 [P] Documentar en `specs/003-friction-anti-paste/quickstart.md` que la validación manual de teclado, portapapeles, selección, rotación y botón atrás queda pendiente por no haber dispositivo/emulador conectado, junto con las limitaciones conocidas

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; puede iniciar de inmediato.
- **Foundational (Phase 2)**: depende de Phase 1 y bloquea todas las historias.
- **US1 (Phase 3)**: depende de Phase 2. Es el MVP.
- **US2 (Phase 4)**: depende de Phase 2 y de la pantalla base de US1.
- **US3 (Phase 5)**: depende de Phase 2 y de la sesión/manager de US1.
- **Polish (Phase 6)**: depende de las historias deseadas.

### User Story Dependencies

- **US1 (P1)**: sin dependencia de otras historias; usa sesión, pantalla, manager y coordinador.
- **US2 (P2)**: requiere la pantalla de US1 para integrar anti-paste y avisos.
- **US3 (P3)**: requiere la sesión/manager de US1 para reutilizar estado al recrear.

### Within Each User Story

- Tests antes de la implementación correspondiente cuando se trabaje en TDD.
- Contratos/modelos foundationales ya completos.
- Pantalla base antes de anti-paste.
- Sesión/manager antes de la reutilización por rotación.
- Validación de build al final de cada historia.

---

## Parallel Execution Examples

### User Story 1

```text
T008 FrictionSessionTest.kt
T009 OverlayCoordinatorUnlockTest.kt
```

### User Story 2

```text
T015 FrictionInputTest.kt
```

### User Story 3

```text
T019 FrictionSessionReuseTest.kt
```

### Foundational

```text
T003 OverlayController.kt
T004 FrictionSession.kt
T005 FrictionInput.kt
T006 friction_texts.xml
T007 strings.xml
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 (Setup).
2. Completar Phase 2 (Foundational).
3. Completar Phase 3 (US1): pantalla, temporizador, coincidencia y unlock.
4. **STOP and VALIDATE**: pruebas de US1 y `:app:assembleDebug`.
5. Entregar/demostrar el MVP.

### Incremental Delivery

1. Setup + Foundational → contratos, sesión y contenido listos.
2. US1 → fricción base y desbloqueo (MVP).
3. US2 → anti-paste y aviso.
4. US3 → preservación ante rotación.
5. Polish → documentación, lint y validación manual.

### Parallel Team Strategy

1. Equipo completa Setup + Foundational.
2. Luego:
   - Developer A: US1 (pantalla + sesión + unlock).
   - Developer B: US2 (anti-paste) tras la pantalla base.
   - Developer C: US3 (rotación) tras la sesión/manager.
3. Integrar al final y ejecutar Phase 6.

---

## Notes

- `[P]` indica archivos distintos y ausencia de dependencias pendientes.
- Los tests se incluyen porque el plan y el quickstart los exigen; no se asume TDD estricto salvo que el equipo lo decida.
- Compose no expone `textIsSelectable`; se usa toolbar no-op + selección colapsada + anti-paste por diff.
- Limpiar el portapapeles es intencional y se documenta como parte de la fricción.
- El texto objetivo debe ser estable; cambiarlo invalida sesiones y comparaciones.
- No se modifican Room, DataStore ni el esquema de reglas.
- Commit después de cada tarea o grupo lógico.
