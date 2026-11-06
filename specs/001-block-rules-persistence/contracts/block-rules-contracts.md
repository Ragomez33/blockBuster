# Contracts: Persistencia de reglas de bloqueo

**Feature**: `001-block-rules-persistence`  
**Scope**: Contratos internos entre `:core:common`, `:core:database`,
`:core:overlay` y `:app`.

## 1. Contrato público del repositorio

Ubicación: `:core:common`, `com.blockbuster.core.common.contract.BlockRulesRepository`.

```kotlin
interface BlockRulesRepository {
    fun observeActiveRules(): Flow<List<BlockRule>>
    fun observeAllRules(): Flow<List<BlockRule>>
    fun observeRule(id: Long): Flow<BlockRule?>

    suspend fun getActiveRulesByType(type: BlockRuleType): AppResult<List<BlockRule>>

    suspend fun insertRule(draft: BlockRuleDraft): AppResult<Long>
    suspend fun updateRule(rule: BlockRule): AppResult<Unit>
    suspend fun deleteRule(id: Long): AppResult<Unit>

    suspend fun recordBlock(
        ruleId: Long,
        target: String,
        timestamp: Long,
    ): AppResult<Long>

    fun observeBlockLogs(ruleId: Long): Flow<List<BlockLog>>

    // Temporizador existente; se conserva sin cambios funcionales.
    fun observeTimer(): Flow<BlockTimer?>
    suspend fun startTimer(timer: BlockTimer): AppResult<Unit>
    suspend fun stopTimer(): AppResult<Unit>
    suspend fun clearAll(): AppResult<Unit>
}
```

### Semántica

| Operación | Resultado esperado |
|---|---|
| `observeActiveRules` | Emite solo reglas con `isEnabled = true`, ordenadas por `createdAt` descendente |
| `observeAllRules` | Emite todas las reglas, habilitadas e inhabilitadas |
| `observeRule(id)` | Emite la regla o `null` si no existe |
| `getActiveRulesByType` | Devuelve reglas habilitadas del tipo solicitado; lista vacía si no hay coincidencias |
| `insertRule` | Devuelve el ID generado; el `id` del draft no existe |
| `updateRule` | Actualiza por `id`; `NotFound` si no existe |
| `deleteRule` | Elimina por `id`; `NotFound` si no existe |
| `recordBlock` | Inserta un registro inmutable y devuelve su ID |
| `observeBlockLogs` | Emite logs de la regla ordenados por `timestamp` descendente |
| `clearAll` | Borra reglas, logs y temporizador; usado por ajustes/reset |

Los flujos de lectura son fríos y no se envuelven en `AppResult`. Las escrituras
y lecturas puntuales sí devuelven `AppResult`.

### Errores

| Error | Uso |
|---|---|
| `AppError.NotFound` | Actualizar/eliminar una regla inexistente |
| `AppError.Storage` | Error de SQLite, disco o escritura |
| `AppError.Unexpected` | Cualquier otra excepción no clasificada |

## 2. Contrato de DAOs

Ubicación: `:core:database`, `com.blockbuster.core.database.dao`.

```kotlin
@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules WHERE is_enabled = 1 ORDER BY created_at DESC")
    fun observeActiveRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules ORDER BY created_at DESC")
    fun observeAllRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules WHERE id = :id LIMIT 1")
    fun observeRuleById(id: Long): Flow<BlockRuleEntity?>

    @Insert
    suspend fun insertRule(rule: BlockRuleEntity): Long

    @Update
    suspend fun updateRule(rule: BlockRuleEntity): Int

    @Delete
    suspend fun deleteRule(rule: BlockRuleEntity): Int

    @Query(
        "SELECT * FROM block_rules " +
            "WHERE is_enabled = 1 AND type = :type " +
            "ORDER BY created_at DESC",
    )
    suspend fun getActiveRulesByType(type: String): List<BlockRuleEntity>

    @Query("SELECT * FROM block_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Long): BlockRuleEntity?
}

@Dao
interface BlockLogDao {
    @Insert
    suspend fun insertLog(log: BlockLogEntity): Long

    @Query(
        "SELECT * FROM block_logs WHERE rule_id = :ruleId " +
            "ORDER BY timestamp DESC",
    )
    fun observeLogsForRule(ruleId: Long): Flow<List<BlockLogEntity>>
}
```

### Reglas de los DAOs

- `getActiveRulesByType` recibe `String` porque así lo exige el contrato; el
  repositorio pasa `BlockRuleType.name`.
- `updateRule` y `deleteRule` devuelven el número de filas afectadas; `0`
  significa `NotFound`.
- `deleteRule` recibe la entidad; el repositorio la obtiene previamente por ID.
- Los DAOs no realizan mapeos de dominio ni manejan `AppResult`; esa
  responsabilidad pertenece al repositorio.
- No se usa `LiveData`.

## 3. Contrato de hilos

- **Escrituras y lecturas puntuales**: el repositorio ejecuta cada operación
  dentro de `withContext(ioDispatcher)`.
- **Flujos**: el repositorio aplica `flowOn(ioDispatcher)` antes de exponerlos.
- **Dispatcher**: inyectado con el calificador `@IoDispatcher`; producción usa
  `Dispatchers.IO`, pruebas usan un `TestDispatcher`.
- Ningún método del repositorio debe ejecutar SQL en el hilo principal.

## 4. Contrato de esquema

- `AppDatabase` es `abstract`, `exportSchema = true`, versión 2.
- Entidades: `BlockRuleEntity` y `BlockLogEntity`.
- `@TypeConverters(BlockRuleTypeConverter::class)` a nivel de base de datos.
- `fallbackToDestructiveMigration(dropAllTables = true)` para la etapa
  pre-release.
- El esquema exportado se versiona en
  `core/database/schemas/com.blockbuster.core.database.AppDatabase/2.json`.

## 5. Contrato de consumidores

### UI (`:app`)

- Usa `observeAllRules()` para la pantalla de gestión.
- Crea reglas con `BlockRuleDraft`.
- Habilita/deshabilita con `updateRule(rule.copy(isEnabled = ...))`.
- Elimina con `deleteRule(rule.id)`.

### Overlay (`:core:overlay`)

- Observa `observeActiveRules()`.
- **Puente temporal**: considera bloqueante una regla `DOMAIN` cuyo `pattern`
  coincida exactamente con el paquete en primer plano.
- Las reglas `KEYWORD_SHORTS` y `KEYWORD_REELS` se persisten pero no se evalúan
  todavía; requieren el motor de inspección de contenido de una iteración
  posterior.
- El contrato `OverlayController` no cambia.
