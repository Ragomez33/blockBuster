# Implementation Plan: Persistencia de reglas de bloqueo

**Branch**: `001-block-rules-persistence` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-block-rules-persistence/spec.md`

## Summary

Implementar en `:core:database` la persistencia local de reglas de bloqueo y su
auditoría con Room, exponiendo modelos de dominio limpios a través de un
repositorio Hilt. El modelo canónico pasa a ser `BlockRule` (`id`, `pattern`,
`type`, `isEnabled`, `createdAt`) más `BlockLog`, y reemplaza el esquema legacy
basado en paquetes de aplicación.

La capa de repositorio ejecutará todo acceso a datos en un dispatcher de E/S
inyectado, expondrá observación exclusivamente con `Flow` y devolverá
`AppResult<T>` en lecturas puntuales y escrituras. La base será `AppDatabase`
versión 2 con `exportSchema = true` y migración destructiva documentada para la
etapa pre-release.

## Technical Context

**Language/Version**: Kotlin 2.4.21, JVM 17, AGP 9.5.0, Gradle 9.6.0

**Primary Dependencies**: Room 2.8.5, KSP 2.3.13, Hilt 2.60.1,
Kotlin Coroutines/Flow 1.11.0, DataStore Preferences 1.3.0

**Storage**: SQLite local mediante Room (`blockbuster.db`); DataStore para el
temporizador existente. Esquema exportado en `core/database/schemas`.

**Testing**: JUnit 4.13.2, `kotlinx-coroutines-test` 1.11.0, Robolectric 4.17,
`androidx.test:core` 1.7.0 y `room-testing` 2.8.5. Pruebas JVM con
`Room.inMemoryDatabaseBuilder`.

**Target Platform**: Android API 26+ (`minSdk = 26`, `compileSdk`/`targetSdk = 37`)

**Project Type**: Aplicación móvil Android multi-módulo con Clean Architecture

**Performance Goals**: Cambios de reglas visibles en suscriptores en menos de
1 segundo; operaciones de almacenamiento sin bloquear el hilo principal;
consultas filtradas por índices sobre `is_enabled`, `type` y `created_at`.

**Constraints**: Acceso a datos estrictamente sobre `Dispatchers.IO` (inyectado
como `@IoDispatcher`); sin `LiveData`; observación solo con `Flow`; sin
`SharedPreferences`; `exportSchema = true`; resultados de escritura envueltos en
`AppResult`; migración destructiva pre-release.

**Scale/Scope**: Decenas a cientos de reglas y miles de logs por dispositivo.
Esta iteración no implementa sincronización remota, purga de logs ni motor de
coincidencia de palabras clave sobre contenido de pantalla.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` no contiene una constitución ratificada: sigue
siendo la plantilla con marcadores. No existen principios, gates ni reglas de
gobernanza ejecutables, por lo que no hay violaciones constitucionales que
justificar.

Gates derivados de la arquitectura existente y de la especificación:

| Gate | Estado | Evidencia |
|---|---|---|
| El módulo de datos no depende de UI/Compose | PASS | `:core:database` solo usa Room, Hilt, DataStore y coroutines |
| Observación exclusivamente reactiva | PASS | DAOs y repositorio exponen `Flow`; no se usa LiveData |
| Persistencia local permitida | PASS | Room + DataStore; sin SharedPreferences |
| Esquema exportado | PASS | `exportSchema = true` y `room.schemaLocation` ya configurado |
| Acceso a datos fuera del hilo principal | PASS | Dispatcher `@IoDispatcher` + `withContext`/`flowOn` |
| Contratos desacoplados en `:core:common` | PASS | Modelos y `BlockRulesRepository` viven en el módulo común |
| Build y pruebas reproducibles | PASS | Pruebas JVM con Room en memoria y Robolectric |

**Post-Phase 1 re-check**: PASS. El diseño de entidades, DAOs, repositorio y
pruebas mantiene los gates anteriores sin excepciones.

## Project Structure

### Documentation (this feature)

```text
specs/001-block-rules-persistence/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── block-rules-contracts.md
└── tasks.md              # Generado por /speckit.tasks
```

### Source Code (repository root)

```text
core/common/src/main/kotlin/com/blockbuster/core/common/
├── model/
│   ├── BlockRule.kt              # Nuevo modelo: id, pattern, type, isEnabled, createdAt
│   ├── BlockRuleType.kt          # DOMAIN, KEYWORD_SHORTS, KEYWORD_REELS
│   ├── BlockLog.kt               # Modelo de auditoría
│   └── BlockTimer.kt             # Existente, sin cambios funcionales
└── contract/
    └── BlockRulesRepository.kt   # Contrato actualizado

core/database/src/main/kotlin/com/blockbuster/core/database/
├── AppDatabase.kt                # RoomDatabase versión 2, exportSchema
├── entity/
│   ├── BlockRuleEntity.kt
│   ├── BlockLogEntity.kt
│   └── BlockRuleTypeConverter.kt
├── dao/
│   ├── BlockRuleDao.kt
│   └── BlockLogDao.kt
├── repository/
│   └── BlockRulesRepositoryImpl.kt
├── datastore/                    # Existente: temporizador
│   └── ...
└── di/
    ├── DatabaseModule.kt         # AppDatabase, DAOs, DataStore
    ├── RepositoryModule.kt       # @Binds del repositorio
    └── IoDispatcher.kt           # Calificador + provider

core/database/src/test/kotlin/com/blockbuster/core/database/
├── dao/
│   ├── BlockRuleDaoTest.kt
│   └── BlockLogDaoTest.kt
└── repository/
    └── BlockRulesRepositoryImplTest.kt

core/database/schemas/com.blockbuster.core.database.AppDatabase/
└── 2.json                        # Esquema exportado

core/overlay/src/main/kotlin/com/blockbuster/core/overlay/
└── OverlayCoordinator.kt         # Adaptado al nuevo modelo

app/src/main/kotlin/com/blockbuster/app/ui/rules/
├── RulesViewModel.kt             # Patrón + tipo; CRUD por ID
└── RulesScreen.kt                # Formulario de patrón/tipo; lista y switch

app/src/main/res/values/strings.xml
```

**Structure Decision**: Se conserva la arquitectura multi-módulo existente. Los
contratos y modelos de dominio permanecen en `:core:common`; la implementación
Room, DAOs, mapeos y Hilt viven en `:core:database`; los consumidores se adaptan
sin invertir la dirección de dependencias. Se eliminan
`core/database/.../local/BlockBusterDatabase.kt` y el modelo legacy de paquetes.

## Implementation Phases

### Phase A — Dominio y contrato

1. Crear `BlockRuleType` y `BlockLog` en `:core:common`.
2. Reemplazar `BlockRule` por el modelo `id/pattern/type/isEnabled/createdAt`.
3. Agregar `BlockRuleDraft` para creación sin ID.
4. Actualizar `BlockRulesRepository` con observación, CRUD, filtro por tipo,
   auditoría y temporizador.

### Phase B — Room

1. Mover entidades a `entity/` y crear `BlockRuleEntity` y `BlockLogEntity` con
   índices.
2. Implementar `BlockRuleTypeConverter`.
3. Implementar `BlockRuleDao` y `BlockLogDao`.
4. Crear `AppDatabase` versión 2 con `exportSchema = true`.
5. Eliminar la base legacy y configurar migración destructiva pre-release.

### Phase C — Repositorio y Hilt

1. Implementar `BlockRulesRepositoryImpl` con mapeos entidad/dominio.
2. Ejecutar lecturas y escrituras con `@IoDispatcher`.
3. Mapear errores a `AppResult`/`AppError`.
4. Actualizar módulos Hilt (`AppDatabase`, DAOs, dispatcher, binding).

### Phase D — Consumidores

1. Adaptar `OverlayCoordinator` al nuevo modelo y puente temporal `DOMAIN`.
2. Adaptar `RulesViewModel` a `BlockRuleDraft`, tipo y operaciones por ID.
3. Adaptar `RulesScreen` a un formulario de patrón + tipo, con validación de
   patrón no vacío.
4. Actualizar strings y eliminar el uso de `PackageManager` para reglas.

### Phase E — Pruebas y esquema

1. Agregar dependencias de Robolectric, Room testing y AndroidX Test.
2. Probar DAOs con Room en memoria.
3. Probar repositorio: mapeos, `AppResult`, filtro por tipo, auditoría y
   dispatcher.
4. Generar y versionar `2.json`; verificar exportación.
5. Ejecutar `assembleDebug`, `testDebugUnitTest` y `lintDebug`.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Pérdida de reglas locales por migración destructiva | Datos de desarrollo descartados | Documentado como pre-release; añadir migración explícita antes de producción |
| Las reglas keyword aún no bloquean | Funcionalidad parcial | Persistencia completa; motor de contenido en iteración posterior |
| El puente `DOMAIN == package` es temporal | Semántica limitada | Documentado en contratos; reemplazar por matcher real |
| Robolectric no soporta compileSdk 37 | Pruebas no ejecutan | Usar `@Config(sdk = [36])` si es necesario; validar en la primera ejecución |
| Cambios de esquema no auditados | Regresiones futuras | `exportSchema = true` y esquema versionado |

## Out of Scope

- Motor de coincidencia de dominios/palabras clave contra contenido de pantalla.
- Purga o retención automática de logs.
- Sincronización remota o cuentas de usuario.
- Migración explícita 1→2 para producción.
- Pantallas de historial de auditoría.
