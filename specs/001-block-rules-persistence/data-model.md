# Data Model: Persistencia de reglas de bloqueo

**Feature**: `001-block-rules-persistence`  
**Database**: `AppDatabase` (Room, versión 2)  
**Schema export**: `core/database/schemas/com.blockbuster.core.database.AppDatabase/`

## Modelos de dominio (`:core:common`)

### `BlockRuleType`

Categoría cerrada del patrón:

| Valor | Significado |
|---|---|
| `DOMAIN` | Dominio o nombre de host |
| `KEYWORD_SHORTS` | Palabra clave asociada a Shorts |
| `KEYWORD_REELS` | Palabra clave asociada a Reels |

### `BlockRule`

| Campo | Tipo | Nulo | Descripción |
|---|---|---|---|
| `id` | `Long` | No | Identificador generado por la base de datos |
| `pattern` | `String` | No | Dominio o palabra clave |
| `type` | `BlockRuleType` | No | Categoría del patrón |
| `isEnabled` | `Boolean` | No | Indica si la regla participa en el bloqueo |
| `createdAt` | `Long` | No | Milisegundos desde la época |

### `BlockRuleDraft`

Modelo de creación sin identificador, para no simular IDs inexistentes:

| Campo | Tipo | Nulo | Default |
|---|---|---|---|
| `pattern` | `String` | No | — |
| `type` | `BlockRuleType` | No | — |
| `isEnabled` | `Boolean` | No | `true` |
| `createdAt` | `Long` | No | `System.currentTimeMillis()` en el llamador |

### `BlockLog`

| Campo | Tipo | Nulo | Descripción |
|---|---|---|---|
| `id` | `Long` | No | Identificador generado |
| `ruleId` | `Long` | No | Regla aplicada en el momento del bloqueo |
| `target` | `String` | No | Objetivo bloqueado (dominio, texto o paquete) |
| `timestamp` | `Long` | No | Milisegundos desde la época |

## Entidades Room (`:core:database/entity`)

### `BlockRuleEntity`

Tabla: `block_rules`

| Columna | Tipo SQL | Restricciones |
|---|---|---|
| `id` | `INTEGER` | `PRIMARY KEY AUTOINCREMENT` |
| `pattern` | `TEXT` | `NOT NULL` |
| `type` | `TEXT` | `NOT NULL`, valores del enum |
| `is_enabled` | `INTEGER` | `NOT NULL`, booleano |
| `created_at` | `INTEGER` | `NOT NULL` |

Índices:

- `index_block_rules_is_enabled_type` sobre `(is_enabled, type)` para las
  consultas de reglas activas por tipo.
- `index_block_rules_created_at` sobre `created_at` para ordenamiento.

No se declara índice único sobre `(pattern, type)`: el requisito no prohíbe
duplicados.

### `BlockLogEntity`

Tabla: `block_logs`

| Columna | Tipo SQL | Restricciones |
|---|---|---|
| `id` | `INTEGER` | `PRIMARY KEY AUTOINCREMENT` |
| `rule_id` | `INTEGER` | `NOT NULL` |
| `target` | `TEXT` | `NOT NULL` |
| `timestamp` | `INTEGER` | `NOT NULL` |

Índices:

- `index_block_logs_rule_id` sobre `rule_id`.
- `index_block_logs_timestamp` sobre `timestamp`.

Relación lógica: una regla tiene muchos registros de auditoría. **No se declara
clave foránea** para permitir eliminar la regla sin borrar el historial
(FR-010).

## Conversión enum ↔ texto

`BlockRuleTypeConverter`:

- `BlockRuleType` → `String` usando `type.name`.
- `String` → `BlockRuleType` usando `BlockRuleType.valueOf(valor)`.

El conversor se registra a nivel de `AppDatabase` con `@TypeConverters`.

## Mapeo entidad ↔ dominio

| Dominio | Entidad |
|---|---|
| `BlockRule.id` | `BlockRuleEntity.id` |
| `BlockRule.pattern` | `BlockRuleEntity.pattern` |
| `BlockRule.type` | `BlockRuleEntity.type` |
| `BlockRule.isEnabled` | `BlockRuleEntity.isEnabled` |
| `BlockRule.createdAt` | `BlockRuleEntity.createdAt` |
| `BlockLog.id` | `BlockLogEntity.id` |
| `BlockLog.ruleId` | `BlockLogEntity.ruleId` |
| `BlockLog.target` | `BlockLogEntity.target` |
| `BlockLog.timestamp` | `BlockLogEntity.timestamp` |

Los mapeos viven en el módulo de base de datos y no exponen anotaciones Room a
`:core:common`.

## Validación y reglas de negocio

| Regla | Dónde se aplica |
|---|---|
| El patrón no puede ser vacío ni solo espacios | UI de creación/edición; la capa de persistencia no lo altera |
| El tipo debe ser uno de los tres valores | Tipo enum; el conversor solo acepta valores guardados por la app |
| El patrón se guarda tal como llega, sin normalizar | Repositorio |
| Los duplicados `(pattern, type)` están permitidos | Sin índice único |
| Las marcas de tiempo son milisegundos desde la época | Llamador/repositorio |
| Los logs son inmutables | Solo se insertan y se leen |
| Los logs sobreviven a la eliminación de su regla | Sin FK |

## Transiciones de estado

### Regla

```text
Creada (habilitada por defecto)
   │
   ├── updateRule → actualiza pattern/type/isEnabled
   │        │
   │        └── isEnabled = false → Regla inactiva (no aparece en active rules)
   │
   └── deleteRule → Eliminada
            │
            └── block_logs históricos permanecen
```

No hay borrado lógico: la deshabilitación es el estado "inactiva" y la
eliminación es física.

### Registro de auditoría

```text
Bloqueo ocurrido → insertLog → Registro inmutable
```

## Migración y evolución

- Versión anterior: 1 (modelo legacy de paquetes de aplicación).
- Versión nueva: 2 (reglas por patrón/tipo + auditoría).
- Estrategia: migración destructiva de la base pre-release mediante
  `fallbackToDestructiveMigration(dropAllTables = true)`.
- Los datos legacy se descartan intencionalmente porque su semántica no es
  equivalente a `pattern`/`type`.
- Antes de producción se reemplazará por una migración explícita y se añadirá
  una prueba de migración con el esquema exportado.

## Volumen esperado

- Reglas: decenas a pocos cientos por dispositivo.
- Logs: miles de registros; las consultas se filtran por `rule_id` y se ordenan
  por `timestamp`.
- No se implementa purga automática en esta iteración; una política de retención
  queda para un feature posterior.
