# Research: Persistencia de reglas de bloqueo

**Feature**: `001-block-rules-persistence`  
**Date**: 2026-11-05  
**Status**: Complete — no unresolved NEEDS CLARIFICATION

## Contexto técnico

El proyecto ya contiene una capa inicial en `:core:database` con un modelo
legacy basado en paquetes de aplicaciones (`package_name`, `app_label`,
`daily_limit_minutes`). La nueva especificación redefine el modelo canónico de
regla como `id`, `pattern`, `type`, `isEnabled` y `createdAt`, e incorpora una
tabla de auditoría `block_logs`.

También existen consumidores actuales en `:core:overlay` y `:app` que usan los
campos legacy. El plan debe resolver el reemplazo sin dejar el build roto.

## Decisiones

### D1. Reemplazar el esquema legacy, no coexistir con él

**Decision**: La nueva entidad `BlockRuleEntity` sustituye al modelo basado en
paquetes. Se elimina la tabla legacy y se actualizan los consumidores.

**Rationale**: La especificación declara el nuevo contrato como fuente única de
verdad. Mantener dos modelos de reglas duplicaría conceptos, DAOs y repositorios,
y haría ambiguo qué regla aplica el sistema.

**Alternatives considered**:
- Agregar una tabla nueva y conservar la antigua: rechazado por duplicación y
  deuda de mantenimiento.
- Mantener dos repositorios: rechazado por complejidad innecesaria en una app
  todavía no publicada.
- Adaptar el modelo antiguo con propiedades calculadas: rechazado porque oculta
  un cambio semántico profundo.

### D2. Migración destructiva controlada para la base pre-release

**Decision**: `AppDatabase` será versión 2 y usará
`fallbackToDestructiveMigration(dropAllTables = true)`.

**Rationale**: La tabla anterior almacenaba paquetes de aplicaciones; la nueva
almacena dominios y palabras clave. Convertir automáticamente un paquete en un
dominio produciría datos semánticamente incorrectos. La aplicación aún no tiene
usuarios de producción, por lo que el costo de descartar datos de desarrollo es
aceptable.

**Alternatives considered**:
- Migración 1→2 que copie `package_name` a `pattern`: rechazada por mezclar dos
  conceptos distintos.
- Nueva base de datos con otro nombre: rechazada por dejar una base huérfana.
- Migración explícita que solo borre y recree tablas: equivalente a la opción
  elegida, pero con más SQL y mantenimiento sin beneficio actual.

**Risk**: Pérdida de reglas locales al actualizar una instalación de desarrollo.
Se documentará como limitación pre-release y se reemplazará por una migración
explícita antes de publicar.

### D3. Estructura de paquetes solicitada

**Decision**: Ubicar las entidades en
`com.blockbuster.core.database.entity`, los DAOs en
`com.blockbuster.core.database.dao`, `AppDatabase` en la raíz del paquete de
base de datos y el repositorio en `com.blockbuster.core.database.repository`.
Las clases legacy de `local/` se mueven o se eliminan.

**Rationale**: Cumple la estructura requerida y separa claramente entidades,
acceso a datos y repositorio.

**Alternatives considered**: Mantener `local/` y `BlockBusterDatabase` por
compatibilidad; rechazado porque contradice los nombres explícitos del feature.

### D4. Persistir el enum de tipo como texto

**Decision**: `BlockRuleType` será un enum de dominio y se persistirá en la
columna `type` como `TEXT` mediante un `@TypeConverter` que usa `name`.
El DAO expondrá `getActiveRulesByType(type: String)` tal como se solicitó.

**Rationale**: El texto es legible, indexable y estable ante reordenamientos del
enum. Evita almacenar ordinales frágiles.

**Alternatives considered**:
- Entero ordinal: rechazado por fragilidad ante reordenamientos.
- Guardar `String` en la entidad: rechazado porque filtra detalles de
  almacenamiento al modelo de dominio.

### D5. Separar observación reactiva de resultados explícitos

**Decision**:
- Las lecturas observables devuelven `Flow<List<ModeloDominio>>`.
- Las lecturas puntuales y las escrituras devuelven `AppResult<T>`.
- Los flujos se mapean en el repositorio y se ejecutan con `flowOn(ioDispatcher)`.

**Rationale**: Envolver cada emisión de un `Flow` en `AppResult` complica a los
consumidores y no aporta valor para consultas Room que ya son frías y
reintentables. `AppResult` se reserva para operaciones que pueden fallar de
forma puntual (insertar, actualizar, eliminar, registrar auditoría).

**Alternatives considered**:
- `Flow<AppResult<List<...>>>`: rechazado por ruido y porque obliga a cada
  consumidor a desenvolver cada emisión.
- Lanzar excepciones desde escrituras: rechazado por el requisito de resultados
  controlados.

### D6. Dispatcher de E/S inyectado

**Decision**: Inyectar un `CoroutineDispatcher` calificado como `@IoDispatcher`
y proveerlo con `Dispatchers.IO`. Las operaciones suspend usan `withContext` y
los flujos usan `flowOn`.

**Rationale**: Garantiza estrictamente que el acceso a datos ocurra fuera del
hilo principal y permite sustituirlo por un `TestDispatcher` en pruebas.

**Alternatives considered**:
- Hardcodear `Dispatchers.IO`: rechazado por baja testabilidad.
- Confiar únicamente en el executor de Room: insuficiente para cumplir el
  requisito de forma explícita y verificable.

### D7. Auditoría con DAO propio y sin clave foránea

**Decision**: Agregar `BlockLogDao` con inserción y observación por regla. La
tabla `block_logs` no tendrá clave foránea hacia `block_rules`.

**Rationale**: El requisito exige conservar los registros aunque la regla se
elimine. Una clave foránea con cascada los borraría; una clave foránea sin
cascada impediría eliminar reglas con historial.

**Alternatives considered**:
- FK con `ON DELETE CASCADE`: viola la retención histórica.
- FK con `ON DELETE RESTRICT`: impide eliminar reglas auditadas.
- Solo entidad sin DAO: deja la auditoría sin forma de persistirse.

### D8. Adaptación mínima de consumidores

**Decision**: Actualizar `:core:overlay` y la UI de reglas para compilar contra
el nuevo contrato. La UI permitirá introducir un patrón y elegir el tipo. El
overlay usará un puente temporal: una regla `DOMAIN` cuya `pattern` coincida
exactamente con el paquete en primer plano se considera bloqueante. La
evaluación de palabras clave contra contenido de pantalla queda fuera de este
feature.

**Rationale**: El feature es de persistencia; la lógica de inspección de
contenido pertenece a una iteración posterior. El puente mantiene el
comportamiento actual para reglas que usan el nombre del paquete y evita
inventar semántica de coincidencias.

**Alternatives considered**:
- Dejar los consumidores sin actualizar: no compila.
- Reimplementar el motor de coincidencia en este feature: excede el alcance y
  requiere decisiones de producto no especificadas.

**Risk**: Las reglas de tipo keyword se persistirán pero aún no producirán
bloqueos. Se documentará como limitación.

### D9. Pruebas JVM con Room en memoria y Robolectric

**Decision**: Agregar Robolectric 4.17, `androidx.test:core` 1.7.0,
`room-testing` 2.8.5 y `kotlinx-coroutines-test` para probar DAOs y repositorio
en la JVM. Usar `Room.inMemoryDatabaseBuilder` con `allowMainThreadQueries`
solo en pruebas.

**Rationale**: Permite validar SQL real, conversores, índices y emisiones de
`Flow` sin requerir un emulador. Mantiene el pipeline de pruebas rápido.

**Alternatives considered**:
- Solo pruebas instrumentadas: requieren dispositivo/emulador y no son ideales
  para el ciclo rápido inicial.
- Solo mocks de DAO: no validan consultas SQL ni el esquema real.

### D10. Exportación de esquema

**Decision**: Mantener `exportSchema = true` y el argumento KSP
`room.schemaLocation = $projectDir/schemas`. El esquema de `AppDatabase`
versión 2 se versionará en `core/database/schemas/com.blockbuster.core.database.AppDatabase/2.json`.
El esquema legacy se elimina junto con la clase antigua.

**Rationale**: Permite auditoría de cambios y prepara migraciones futuras.

**Alternatives considered**: Desactivar la exportación; rechazado por el
requisito explícito y por higiene de evolución del esquema.
