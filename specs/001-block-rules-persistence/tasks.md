# Tasks: Persistencia de reglas de bloqueo

**Input**: Design documents from `specs/001-block-rules-persistence/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/block-rules-contracts.md`, `quickstart.md`

**Tests**: Incluidos porque `plan.md` (Phase E) y `quickstart.md` los requieren
para validar DAOs, repositorio y persistencia real.

**Organization**: Tareas agrupadas por historia de usuario para permitir
implementación y validación independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario (`US1`, `US2`, `US3`)
- Todas las tareas incluyen rutas de archivo exactas

## Path Conventions

- Módulo común: `core/common/src/main/kotlin/com/blockbuster/core/common/`
- Módulo de datos: `core/database/src/main/kotlin/com/blockbuster/core/database/`
- Pruebas JVM: `core/database/src/test/kotlin/com/blockbuster/core/database/`
- Consumidores: `core/overlay/src/main/kotlin/`, `app/src/main/kotlin/`
- Recursos: `app/src/main/res/values/strings.xml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencias de prueba y dispatcher compartido.

- [X] T001 [P] Agregar `robolectric = "4.17"`, `androidx-test-core = "1.7.0"` y las librerías `room-testing`, `robolectric` y `androidx-test-core` en `gradle/libs.versions.toml`
- [X] T002 [P] Configurar `testOptions { unitTests.isIncludeAndroidResources = true }` y las dependencias `testImplementation` de Robolectric, AndroidX Test Core, Room Testing y Coroutines Test en `core/database/build.gradle.kts`
- [X] T003 [P] Crear el calificador `@IoDispatcher` y el provider Hilt de `Dispatchers.IO` en `core/database/src/main/kotlin/com/blockbuster/core/database/di/IoDispatcher.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Modelos canónicos, esquema Room, DAOs, repositorio y DI. Ninguna historia puede implementarse hasta completar esta fase.

**⚠️ CRITICAL**: No iniciar US1, US2 ni US3 hasta que esta fase compile.

- [X] T004 [P] Crear el enum `BlockRuleType` con los valores `DOMAIN`, `KEYWORD_SHORTS` y `KEYWORD_REELS` en `core/common/src/main/kotlin/com/blockbuster/core/common/model/BlockRuleType.kt`
- [X] T005 [P] Reemplazar `BlockRule` por `id: Long`, `pattern: String`, `type: BlockRuleType`, `isEnabled: Boolean` y `createdAt: Long`; agregar `BlockRuleDraft` sin ID en `core/common/src/main/kotlin/com/blockbuster/core/common/model/BlockRule.kt` y `core/common/src/main/kotlin/com/blockbuster/core/common/model/BlockRuleDraft.kt`
- [X] T006 [P] Crear el modelo `BlockLog` con `id: Long`, `ruleId: Long`, `target: String` y `timestamp: Long` en `core/common/src/main/kotlin/com/blockbuster/core/common/model/BlockLog.kt`
- [X] T007 Actualizar el contrato `BlockRulesRepository` con `observeActiveRules`, `observeAllRules`, `observeRule`, `getActiveRulesByType`, `insertRule`, `updateRule`, `deleteRule`, `recordBlock`, `observeBlockLogs` y los métodos existentes del temporizador en `core/common/src/main/kotlin/com/blockbuster/core/common/contract/BlockRulesRepository.kt`
- [X] T008 [P] Crear `BlockRuleEntity` en `core/database/src/main/kotlin/com/blockbuster/core/database/entity/BlockRuleEntity.kt` con `id: Long` `PRIMARY KEY AUTOINCREMENT`, `pattern: String` no nulo, `type: BlockRuleType` no nulo (`DOMAIN`, `KEYWORD_SHORTS`, `KEYWORD_REELS`), `isEnabled: Boolean` no nulo y `createdAt: Long` no nulo; índices `(is_enabled, type)` y `created_at`
- [X] T009 [P] Crear `BlockLogEntity` en `core/database/src/main/kotlin/com/blockbuster/core/database/entity/BlockLogEntity.kt` con `id`, `ruleId`, `target` y `timestamp` no nulos; índices `rule_id` y `timestamp`; sin clave foránea para conservar el historial
- [X] T010 [P] Implementar `BlockRuleTypeConverter` (`BlockRuleType` ↔ `String` usando `name`/`valueOf`) en `core/database/src/main/kotlin/com/blockbuster/core/database/entity/BlockRuleTypeConverter.kt`
- [X] T011 Crear `AppDatabase` abstracta, versión 2, `exportSchema = true`, entidades `BlockRuleEntity` y `BlockLogEntity` y `@TypeConverters(BlockRuleTypeConverter::class)` en `core/database/src/main/kotlin/com/blockbuster/core/database/AppDatabase.kt`
- [X] T012 [P] Crear `BlockRuleDao` con `observeActiveRules(): Flow<List<BlockRuleEntity>>`, `observeAllRules(): Flow<List<BlockRuleEntity>>`, `observeRuleById(id: Long)`, `insertRule(rule): Long`, `updateRule(rule): Int`, `deleteRule(rule): Int`, `getActiveRulesByType(type: String): List<BlockRuleEntity>` y `getRuleById(id: Long)` en `core/database/src/main/kotlin/com/blockbuster/core/database/dao/BlockRuleDao.kt`
- [X] T013 [P] Crear `BlockLogDao` con `insertLog(log): Long` y `observeLogsForRule(ruleId): Flow<List<BlockLogEntity>>`, ordenado por `timestamp DESC`, en `core/database/src/main/kotlin/com/blockbuster/core/database/dao/BlockLogDao.kt`
- [X] T014 Implementar `BlockRulesRepositoryImpl` en `core/database/src/main/kotlin/com/blockbuster/core/database/repository/BlockRulesRepositoryImpl.kt` con mapeos entidad/dominio, `withContext(ioDispatcher)` en suspend, `flowOn(ioDispatcher)` en flujos y errores mapeados a `AppResult`/`AppError`
- [X] T015 Actualizar los módulos Hilt en `core/database/src/main/kotlin/com/blockbuster/core/database/di/DatabaseModule.kt` para proveer `AppDatabase` con `Room.databaseBuilder(...).fallbackToDestructiveMigration(dropAllTables = true)`, `BlockRuleDao`, `BlockLogDao`, `DataStore` y el binding de `BlockRulesRepositoryImpl`
- [X] T016 Eliminar `core/database/src/main/kotlin/com/blockbuster/core/database/local/BlockBusterDatabase.kt`, `BlockRuleEntity.kt`, `BlockRuleDao.kt` y el esquema legacy `core/database/schemas/com.blockbuster.core.database.local.BlockBusterDatabase/1.json`; actualizar imports y adaptar `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt` para compilar con el nuevo modelo usando `observeAllRules()` y filtro local de `isEnabled`
- [X] T017 Ejecutar `./gradlew :core:database:assembleDebug` y corregir cualquier error de compilación en `core/database/src/main/kotlin/` antes de iniciar historias

**Checkpoint**: Esquema, DAOs, repositorio y DI compilando. Las historias pueden comenzar.

---

## Phase 3: User Story 1 - Gestionar reglas de bloqueo persistentes (Priority: P1) 🎯 MVP

**Goal**: Crear, actualizar, habilitar/deshabilitar y eliminar reglas con IDs
autogenerados, persistiéndolas en Room y devolviendo `AppResult`.

**Independent Test**: Insertar una regla, actualizarla, deshabilitarla y
eliminarla mediante el repositorio; verificar IDs, valores y errores
controlados. Reiniciar la app y comprobar persistencia.

### Tests for User Story 1

- [X] T018 [P] [US1] Crear pruebas de CRUD de DAO (insert devuelve ID > 0, update/delete devuelven 1 y 0 para ID inexistente) en `core/database/src/test/kotlin/com/blockbuster/core/database/dao/BlockRuleDaoCrudTest.kt`
- [X] T019 [P] [US1] Crear pruebas del repositorio para `insertRule`, `updateRule`, `deleteRule`, `observeAllRules` y mapeos `AppResult.NotFound`/`AppError.Storage` en `core/database/src/test/kotlin/com/blockbuster/core/database/repository/BlockRulesRepositoryCrudTest.kt`

### Implementation for User Story 1

- [X] T020 [US1] Adaptar `RulesViewModel` a `BlockRuleDraft`, `observeAllRules`, `updateRule` y `deleteRule(id)` en `app/src/main/kotlin/com/blockbuster/app/ui/rules/RulesViewModel.kt`
- [X] T021 [US1] Adaptar `RulesScreen` para crear reglas con patrón y tipo, mostrar el patrón/tipo, alternar `isEnabled` y eliminar por ID en `app/src/main/kotlin/com/blockbuster/app/ui/rules/RulesScreen.kt`
- [X] T022 [US1] Agregar textos de tipos (`DOMAIN`, `KEYWORD_SHORTS`, `KEYWORD_REELS`), validación de patrón no vacío y etiquetas del formulario en `app/src/main/res/values/strings.xml`
- [X] T023 [US1] Ejecutar `./gradlew :core:database:testDebugUnitTest :app:assembleDebug` y corregir los fallos de US1 en `core/database/src/` y `app/src/main/kotlin/com/blockbuster/app/ui/rules/`

**Checkpoint**: US1 funcional y testeable de forma independiente. Es el MVP.

---

## Phase 4: User Story 2 - Observar reglas activas en tiempo real (Priority: P2)

**Goal**: Emitir reglas activas por `Flow`, filtrar por tipo y reaccionar a
cambios sin reiniciar; adaptar el overlay al nuevo modelo.

**Independent Test**: Suscribirse a `observeActiveRules`, insertar/actualizar/
deshabilitar reglas y comprobar emisiones en menos de 1 segundo; ejecutar
`getActiveRulesByType` y verificar solo reglas habilitadas del tipo.

### Tests for User Story 2

- [X] T024 [P] [US2] Crear pruebas de flujos de DAO (emisión inicial vacía, actualización tras insert/update/delete, filtro `is_enabled = 1` y orden `created_at DESC`) en `core/database/src/test/kotlin/com/blockbuster/core/database/dao/BlockRuleDaoFlowTest.kt`
- [X] T025 [P] [US2] Crear pruebas de repositorio para `observeActiveRules`, `observeRule`, `getActiveRulesByType` y `flowOn`/mapeo de dominio en `core/database/src/test/kotlin/com/blockbuster/core/database/repository/BlockRulesRepositoryFlowTest.kt`

### Implementation for User Story 2

- [X] T026 [US2] Cambiar `OverlayCoordinator` de `observeAllRules()` a `observeActiveRules()` y aplicar el puente temporal `DOMAIN` (coincidencia exacta `pattern == packageName`) en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`
- [X] T027 [US2] Ejecutar `./gradlew :core:database:testDebugUnitTest :app:assembleDebug` y corregir los fallos de flujos y overlay en `core/database/src/` y `core/overlay/src/main/kotlin/`

**Checkpoint**: US1 y US2 funcionan; las reglas activas se reflejan en la UI y
el overlay.

---

## Phase 5: User Story 3 - Auditar localmente los bloqueos (Priority: P3)

**Goal**: Persistir registros de auditoría inmutables y conservarlos aunque se
elimine la regla asociada.

**Independent Test**: Insertar un log con `ruleId`, `target` y `timestamp`,
observarlo, eliminar la regla y comprobar que el log sigue disponible.

### Tests for User Story 3

- [X] T028 [P] [US3] Crear pruebas de `BlockLogDao` (inserción devuelve ID, observación por regla ordenada por `timestamp DESC`) en `core/database/src/test/kotlin/com/blockbuster/core/database/dao/BlockLogDaoTest.kt`
- [X] T029 [P] [US3] Crear pruebas del repositorio para `recordBlock`, `observeBlockLogs`, retención tras `deleteRule` y mapeo de errores en `core/database/src/test/kotlin/com/blockbuster/core/database/repository/BlockRulesRepositoryLogTest.kt`

### Implementation for User Story 3

- [X] T030 [US3] Ejecutar `./gradlew :core:database:testDebugUnitTest` y corregir los fallos de auditoría en `core/database/src/main/kotlin/com/blockbuster/core/database/dao/BlockLogDao.kt` y `core/database/src/main/kotlin/com/blockbuster/core/database/repository/BlockRulesRepositoryImpl.kt`

**Checkpoint**: Las tres historias funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Esquema, limpieza, documentación y validación integral.

- [X] T031 [P] Verificar y versionar el esquema `core/database/schemas/com.blockbuster.core.database.AppDatabase/2.json`; eliminar cualquier esquema legacy restante en `core/database/schemas/`
- [X] T032 [P] Eliminar el bloque `<queries>` y el código de `PackageManager` que ya no se usa en `app/src/main/AndroidManifest.xml` y `app/src/main/kotlin/com/blockbuster/app/ui/rules/RulesViewModel.kt`
- [X] T033 [P] Actualizar `README.md` y `specs/001-block-rules-persistence/quickstart.md` con el modelo final, comandos y limitaciones conocidas
- [X] T034 Ejecutar `./gradlew :core:database:testDebugUnitTest :app:assembleDebug :app:lintDebug` y corregir cualquier fallo restante en `core/`, `app/` y `gradle/`
- [X] T035 [P] Ejecutar los escenarios automatizables de `specs/001-block-rules-persistence/quickstart.md`, registrar resultados y limitaciones en ese mismo archivo, y dejar constancia de que la validación manual en dispositivo queda pendiente por falta de emulador/dispositivo conectado

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; puede iniciar de inmediato.
- **Foundational (Phase 2)**: depende de Phase 1 y bloquea todas las historias.
- **US1 (Phase 3)**: depende de Phase 2. Es el MVP.
- **US2 (Phase 4)**: depende de Phase 2. Puede ejecutarse en paralelo con US1 si se separan archivos; el overlay depende de que el repositorio compile.
- **US3 (Phase 5)**: depende de Phase 2. Puede ejecutarse en paralelo con US1/US2.
- **Polish (Phase 6)**: depende de las historias deseadas.

### User Story Dependencies

- **US1 (P1)**: sin dependencia de otras historias; usa el repositorio foundational.
- **US2 (P2)**: sin dependencia funcional de US1; comparte repositorio y añade consumo reactivo/overlay.
- **US3 (P3)**: sin dependencia funcional de US1/US2; usa entidad y DAO foundationales.

### Within Each User Story

- Tests antes de la implementación correspondiente (si se ejecutan en modo TDD).
- Modelos/DAOs foundationales ya están completos.
- Repositorio foundational ya compila antes de US1.
- Adaptación de UI/overlay después de que el contrato y repositorio estén verdes.
- Validación de build al final de cada historia.

---

## Parallel Execution Examples

### User Story 1

```text
T018 BlockRuleDaoCrudTest.kt
T019 BlockRulesRepositoryCrudTest.kt
```

### User Story 2

```text
T024 BlockRuleDaoFlowTest.kt
T025 BlockRulesRepositoryFlowTest.kt
```

### User Story 3

```text
T028 BlockLogDaoTest.kt
T029 BlockRulesRepositoryLogTest.kt
```

### Foundational

```text
T004 BlockRuleType.kt
T005 BlockRule.kt + BlockRuleDraft.kt
T006 BlockLog.kt
T008 BlockRuleEntity.kt
T009 BlockLogEntity.kt
T010 BlockRuleTypeConverter.kt
T012 BlockRuleDao.kt
T013 BlockLogDao.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 (Setup).
2. Completar Phase 2 (Foundational) y validar `:core:database:assembleDebug`.
3. Completar Phase 3 (US1): CRUD persistente + UI de gestión.
4. **STOP and VALIDATE**: ejecutar pruebas de US1 y `:app:assembleDebug`.
5. Entregar/demostrar el MVP.

### Incremental Delivery

1. Setup + Foundational → base de datos y repositorio listos.
2. US1 → CRUD persistente y UI (MVP).
3. US2 → observación reactiva y overlay.
4. US3 → auditoría local.
5. Polish → esquema, limpieza, lint y quickstart.

### Parallel Team Strategy

1. Equipo completa Setup + Foundational.
2. Luego:
   - Developer A: US1 (CRUD + UI).
   - Developer B: US2 (flujos + overlay).
   - Developer C: US3 (auditoría + pruebas).
3. Integrar al final y ejecutar Phase 6.

---

## Notes

- `[P]` indica archivos distintos y ausencia de dependencias pendientes.
- Los tests se incluyen porque el plan y el quickstart los exigen; no se asume TDD estricto salvo que el equipo lo decida.
- La migración destructiva es intencional en pre-release; no se debe publicar sin una migración explícita.
- Las reglas keyword se persisten pero su evaluación de contenido queda fuera de alcance.
- Commit después de cada tarea o grupo lógico.
