# Tasks: Coordinador de bloqueos y activación del Overlay

**Input**: Design documents from `specs/002-overlay-block-coordinator/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/overlay-coordinator-contracts.md`, `quickstart.md`

**Tests**: Incluidos porque `plan.md` (Phase F) y `quickstart.md` los requieren
para validar matcher, coordinador, auditoría y lifecycle del overlay.

**Organization**: Tareas agrupadas por historia de usuario para permitir
implementación y validación independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario (`US1`, `US2`, `US3`)
- Todas las tareas incluyen rutas de archivo exactas

## Path Conventions

- Contratos/modelos: `core/common/src/main/kotlin/com/blockbuster/core/common/`
- Accesibilidad: `core/accessibility/src/main/kotlin/com/blockbuster/core/accessibility/`
- Overlay: `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/`
- Pruebas overlay: `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/`
- App: `app/src/main/kotlin/com/blockbuster/app/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencias de prueba y configuración necesarias para validar el coordinador y el host Compose.

- [X] T001 [P] Agregar `testImplementation` de Coroutines Test, Robolectric y AndroidX Test Core en `core/overlay/build.gradle.kts`, reutilizando las versiones de `gradle/libs.versions.toml`
- [X] T002 [P] Configurar `testOptions { unitTests.isIncludeAndroidResources = true }` y los `jvmArgs --add-opens` para Robolectric/JDK 25 en `core/overlay/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Contratos de detección, extracción de objetivo y modelos de estado que todas las historias necesitan.

**⚠️ CRITICAL**: No iniciar US1, US2 ni US3 hasta completar esta fase.

- [X] T003 [P] Agregar `target: String? = null` al `ScreenDetected` en `core/common/src/main/kotlin/com/blockbuster/core/common/model/DetectionEvent.kt`
- [X] T004 [P] Crear `DetectionSignal` con `packageName`, `target`, `className` y `timestampEpochMillis` en `core/common/src/main/kotlin/com/blockbuster/core/common/model/DetectionSignal.kt`
- [X] T005 [P] Extender `BlockEventBus` con `currentDetection: StateFlow<DetectionSignal?>`; actualizar `publish(ScreenDetected)` y limpiar la detección en `ServiceConnectionChanged(false)` en `core/common/src/main/kotlin/com/blockbuster/core/common/event/BlockEventBus.kt`
- [X] T006 [P] Crear la interfaz `TargetExtractor` en `core/accessibility/src/main/kotlin/com/blockbuster/core/accessibility/target/TargetExtractor.kt`
- [X] T007 [P] Implementar `DefaultTargetExtractor` usando `event.text`, `contentDescription` y el nodo raíz activo con recorrido acotado en `core/accessibility/src/main/kotlin/com/blockbuster/core/accessibility/target/DefaultTargetExtractor.kt`
- [X] T008 [P] Publicar `target` desde `BlockBusterAccessibilityService` mediante `TargetExtractor` en `core/accessibility/src/main/kotlin/com/blockbuster/core/accessibility/BlockBusterAccessibilityService.kt`
- [X] T009 [P] Crear `OverlayState` (`Idle`, `Blocking`, `Dismissed`), `BlockMatch` y `BlockActivationKey` en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayState.kt`
- [X] T010 [P] Crear la interfaz `BlockRuleMatcher` en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/matching/BlockRuleMatcher.kt`

**Checkpoint**: Contratos y modelos compilando; las historias pueden comenzar.

---

## Phase 3: User Story 1 - Bloquear objetivos que coinciden con una regla (Priority: P1) 🎯 MVP

**Goal**: Evaluar el objetivo detectado contra las reglas activas y activar el
overlay a pantalla completa cuando hay coincidencia.

**Independent Test**: Con una regla `DOMAIN` activa, publicar una detección con
target coincidente y verificar que `OverlayCoordinator` solicita mostrar el
overlay; con un target distinto, verificar que no lo solicita.

### Tests for User Story 1

- [X] T011 [P] [US1] Crear pruebas del matcher para dominio, subdominio, keywords, target nulo y precedencia por menor `id` en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/matching/DefaultBlockRuleMatcherTest.kt`
- [X] T012 [P] [US1] Crear pruebas del coordinador para coincidencia, no coincidencia y cambio de objetivo usando `BlockEventBus`, repositorio y `OverlayController` falsos en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/OverlayCoordinatorTest.kt`

### Implementation for User Story 1

- [X] T013 [US1] Implementar `DefaultBlockRuleMatcher` con normalización de dominio, contención de keywords y precedencia determinista en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/matching/DefaultBlockRuleMatcher.kt`
- [X] T014 [US1] Reescribir `OverlayCoordinator` para combinar `currentDetection`, `observeActiveRules` y `isOverlayVisible`, evaluar con el matcher y solicitar `showFriction`/`hideFriction` en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`
- [X] T015 [US1] Exponer `state: StateFlow<OverlayState>` y hacer `start()` idempotente en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`
- [X] T016 [US1] Registrar los bindings Hilt de `BlockRuleMatcher` y del coordinador en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/di/OverlayModule.kt`
- [X] T017 [US1] Verificar el arranque idempotente del coordinador desde `BlockBusterApplication` y `MonitoringForegroundService` en `app/src/main/kotlin/com/blockbuster/app/BlockBusterApplication.kt` y `app/src/main/kotlin/com/blockbuster/app/service/MonitoringForegroundService.kt`
- [X] T018 [US1] Ejecutar `./gradlew :core:overlay:testDebugUnitTest :app:assembleDebug` y corregir los fallos de US1 en `core/overlay/src/` y `app/src/main/kotlin/com/blockbuster/app/`

**Checkpoint**: US1 funcional y testeable de forma independiente. Es el MVP.

---

## Phase 4: User Story 2 - Gestionar el ciclo de vida del overlay sin fugas (Priority: P2)

**Goal**: Garantizar pantalla completa, bloqueo táctil, cierre limpio, ausencia
de ventanas duplicadas y liberación de la composición al ocultar o reabrir.

**Independent Test**: Mostrar/ocultar el overlay cincuenta veces y verificar que
no quedan vistas retenidas, que el touch no llega al fondo y que cada instancia
se destruye correctamente.

### Tests for User Story 2

- [X] T019 [P] [US2] Crear prueba Robolectric del ciclo de vida de `OverlayComposeHost` (creación, show, hide, `disposeComposition`, owners destruidos) en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/OverlayComposeHostTest.kt`
- [X] T020 [P] [US2] Crear pruebas del coordinador para cierre manual (`Dismissed`), cambio de objetivo y ausencia de reapertura inmediata en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/OverlayCoordinatorLifecycleTest.kt`

### Implementation for User Story 2

- [X] T021 [US2] Ajustar `OverlayManager` con `MATCH_PARENT`, `TYPE_APPLICATION_OVERLAY`, flags de pantalla completa/cutout, ausencia de `FLAG_NOT_TOUCH_MODAL` durante bloqueo, reemplazo idempotente y `removeViewImmediate` en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayManager.kt`
- [X] T022 [US2] Endurecer `OverlayComposeHost` para transitar lifecycle `CREATED → RESUMED → DESTROYED`, destruir saved state/ViewModelStore y no reutilizar owners en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayComposeHost.kt`
- [X] T023 [US2] Implementar en `OverlayCoordinator` el estado `Dismissed`, la observación de `isOverlayVisible` y la no reapertura inmediata del mismo target en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`
- [X] T024 [US2] Ejecutar `./gradlew :core:overlay:testDebugUnitTest :app:assembleDebug` y corregir los fallos de lifecycle/touch en `core/overlay/src/`

**Checkpoint**: US1 y US2 funcionan; el overlay es seguro de abrir y cerrar.

---

## Phase 5: User Story 3 - Auditar cada activación de bloqueo (Priority: P3)

**Goal**: Insertar exactamente un registro de auditoría por activación, tolerar
fallos de escritura y conservar el historial ante eliminación de reglas.

**Independent Test**: Provocar una activación, verificar un único `BlockLog`;
repetir detecciones del mismo target y comprobar que no se duplica; simular fallo
de auditoría y verificar que el overlay sigue funcionando.

### Tests for User Story 3

- [X] T025 [P] [US3] Crear pruebas del coordinador para auditoría única, deduplicación por activación, cierre/rearme y fallo de `recordBlock` en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/OverlayCoordinatorAuditTest.kt`
- [X] T026 [P] [US3] Verificar con una prueba de integración que el log persiste tras eliminar la regla en `core/database/src/test/kotlin/com/blockbuster/core/database/repository/BlockRulesRepositoryLogTest.kt`

### Implementation for User Story 3

- [X] T027 [US3] Invocar `repository.recordBlock(rule.id, target, timestamp)` solo en la transición a bloqueo y mantener la clave de activación en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`
- [X] T028 [US3] Tolerar `AppResult.Failure` de auditoría sin ocultar el overlay y registrar el diagnóstico en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`
- [X] T029 [US3] Ejecutar `./gradlew :core:overlay:testDebugUnitTest :core:database:testDebugUnitTest` y corregir los fallos de auditoría en `core/overlay/src/` y `core/database/src/test/`

**Checkpoint**: Las tres historias funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Recursos, documentación, validación integral y limitaciones del sistema.

- [X] T030 [P] Revisar y actualizar los textos de fricción/estado en `core/overlay/src/main/res/values/strings.xml` y `app/src/main/res/values/strings.xml`
- [X] T031 [P] Actualizar `README.md` y `specs/002-overlay-block-coordinator/quickstart.md` con el flujo final, comandos y limitaciones (Home/Recientes, target no disponible)
- [X] T032 Ejecutar `./gradlew :core:overlay:testDebugUnitTest :core:database:testDebugUnitTest :app:assembleDebug :app:lintDebug` y corregir fallos restantes en `core/` y `app/`
- [X] T033 [P] Ejecutar los escenarios automatizables de `specs/002-overlay-block-coordinator/quickstart.md` y registrar resultados/limitaciones en ese archivo
- [X] T034 [P] Documentar en `specs/002-overlay-block-coordinator/quickstart.md` que la validación manual de touch, cierre, reapertura y Home/Recientes queda pendiente por no haber dispositivo/emulador conectado, junto con las limitaciones conocidas

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; puede iniciar de inmediato.
- **Foundational (Phase 2)**: depende de Phase 1 y bloquea todas las historias.
- **US1 (Phase 3)**: depende de Phase 2. Es el MVP.
- **US2 (Phase 4)**: depende de Phase 2 y de la coordinación base de US1 para probar el cierre.
- **US3 (Phase 5)**: depende de Phase 2 y de la transición de activación de US1.
- **Polish (Phase 6)**: depende de las historias deseadas.

### User Story Dependencies

- **US1 (P1)**: sin dependencia de otras historias; usa detección, matcher y overlay base.
- **US2 (P2)**: puede implementar el host/manager en paralelo con US1, pero la prueba de cierre requiere el coordinador de US1.
- **US3 (P3)**: requiere la transición de activación de US1 para insertar la auditoría; los tests de persistencia pueden ejecutarse en paralelo.

### Within Each User Story

- Contratos/modelos foundationales ya completos.
- Tests antes de la implementación correspondiente cuando se trabaje en TDD.
- Matcher antes del coordinador.
- Coordinador antes de la auditoría.
- Overlay manager/host antes de las pruebas de lifecycle.
- Validación de build al final de cada historia.

---

## Parallel Execution Examples

### User Story 1

```text
T011 DefaultBlockRuleMatcherTest.kt
T012 OverlayCoordinatorTest.kt
```

### User Story 2

```text
T019 OverlayComposeHostTest.kt
T020 OverlayCoordinatorLifecycleTest.kt
```

### User Story 3

```text
T025 OverlayCoordinatorAuditTest.kt
T026 BlockRulesRepositoryLogTest.kt
```

### Foundational

```text
T003 DetectionEvent.kt
T004 DetectionSignal.kt
T005 BlockEventBus.kt
T006 TargetExtractor.kt
T007 DefaultTargetExtractor.kt
T009 OverlayState.kt
T010 BlockRuleMatcher.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 (Setup).
2. Completar Phase 2 (Foundational).
3. Completar Phase 3 (US1): matcher + coordinador + activación del overlay.
4. **STOP and VALIDATE**: pruebas de US1 y `:app:assembleDebug`.
5. Entregar/demostrar el MVP.

### Incremental Delivery

1. Setup + Foundational → contratos y detección listos.
2. US1 → bloqueo por coincidencia y overlay (MVP).
3. US2 → touch, pantalla completa y ciclo de vida sin fugas.
4. US3 → auditoría local.
5. Polish → documentación, lint y validación manual.

### Parallel Team Strategy

1. Equipo completa Setup + Foundational.
2. Luego:
   - Developer A: US1 (matcher + coordinador).
   - Developer B: US2 (OverlayManager + OverlayComposeHost).
   - Developer C: US3 (auditoría + pruebas de persistencia), tras la transición de US1.
3. Integrar al final y ejecutar Phase 6.

---

## Notes

- `[P]` indica archivos distintos y ausencia de dependencias pendientes.
- Los tests se incluyen porque el plan y el quickstart los exigen; no se asume TDD estricto salvo que el equipo lo decida.
- La extracción de URL desde accesibilidad es best-effort; sin target no hay bloqueo ni auditoría.
- Home/Recientes no se suprimen de forma absoluta con un overlay normal; se documenta la limitación.
- No se modifican esquemas Room ni migraciones en esta feature.
- Commit después de cada tarea o grupo lógico.
