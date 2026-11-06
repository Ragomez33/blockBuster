# Data Model: Coordinador de bloqueos y Overlay

**Feature**: `002-overlay-block-coordinator`  
**Persistence impact**: Ninguno. Se reutilizan `BlockRuleEntity`, `BlockLogEntity`
y el repositorio existente.

## Modelos de dominio y estado (`:core:common` / `:core:overlay`)

### `DetectionSignal`

Representa la detección normalizada que consume el coordinador.

| Campo | Tipo | Nulo | Descripción |
|---|---|---|---|
| `packageName` | `String` | No | Paquete de la app en primer plano |
| `target` | `String` | Sí | URL o texto detectado |
| `className` | `String` | Sí | Clase de la ventana, para diagnóstico |
| `timestampEpochMillis` | `Long` | No | Momento de la detección |

Se publica como `BlockEventBus.currentDetection: StateFlow<DetectionSignal?>`.

### `DetectionEvent.ScreenDetected` extendido

| Campo | Tipo | Nulo | Descripción |
|---|---|---|---|
| `packageName` | `String` | No | Se conserva del contrato existente |
| `className` | `String` | Sí | Se conserva |
| `timestampEpochMillis` | `Long` | No | Se conserva |
| `target` | `String` | Sí | Nuevo; URL/texto si el extractor lo obtiene |

### `BlockMatch`

Resultado de evaluar un objetivo contra las reglas activas.

| Campo | Tipo | Nulo | Descripción |
|---|---|---|---|
| `rule` | `BlockRule` | No | Regla seleccionada |
| `target` | `String` | No | Objetivo original no vacío |
| `normalizedTarget` | `String` | No | Host o texto normalizado usado en la comparación |
| `detectedAtEpochMillis` | `Long` | No | Timestamp de la detección que originó el match |

### `BlockActivationKey`

Clave lógica para evitar auditorías duplicadas:

```text
(ruleId, normalizedTarget)
```

Se reinicia cuando el overlay se cierra o el objetivo cambia.

### `OverlayState`

Estado observable que expone el coordinador para pruebas y diagnóstico:

| Estado | Campos | Significado |
|---|---|---|
| `Idle` | — | Sin bloqueo activo |
| `Blocking` | `match: BlockMatch`, `audited: Boolean` | Overlay visible o solicitado |
| `Dismissed` | `match: BlockMatch` | La persona usuaria cerró el overlay para ese objetivo |

Transiciones:

```text
Idle ── match ──► Blocking(audited=false)
Blocking(audited=false) ── audit success/failure ──► Blocking(audited=true)
Blocking ── isOverlayVisible=false ──► Dismissed
Dismissed ── nueva detección con otro target/timestamp ──► Idle
Blocking/Dismissed ── reglas cambian y ya no hay match ──► Idle
Idle/Blocking/Dismissed ── servicio desconectado ──► Idle
```

### `FrictionConfig` (existente)

Se mantiene sin cambios estructurales. El coordinador lo construye con:

- `packageName = match.target` (clave de identidad de la ventana).
- `appLabel = match.rule.pattern`.
- Textos desde recursos de `:core:overlay`.

## Modelos persistidos existentes

### `BlockRule` / `BlockRuleEntity`

Sin cambios. Se usan `id`, `pattern`, `type`, `isEnabled`, `createdAt` para
evaluar coincidencias.

### `BlockLog` / `BlockLogEntity`

Sin cambios. Cada activación auditada inserta:

| Campo | Valor |
|---|---|
| `ruleId` | `match.rule.id` |
| `target` | objetivo original |
| `timestamp` | timestamp de la detección |
| `id` | Autogenerado |

## Reglas de comparación

| Tipo | Normalización | Coincidencia |
|---|---|---|
| `DOMAIN` | Minúsculas, sin esquema, `www.`, ruta, query ni fragmento | Host igual al patrón o subdominio (`host.endsWith(".$pattern")`) |
| `KEYWORD_SHORTS` | Minúsculas | `target.contains(pattern)` |
| `KEYWORD_REELS` | Minúsculas | `target.contains(pattern)` |

Precedencia: menor `id` entre las reglas que coinciden.

## Validaciones

- `target` nulo, vacío o solo espacios: no hay match y no se audita.
- El patrón no se normaliza en la base de datos; la normalización es de
  comparación en memoria.
- Un fallo de auditoría no cambia el estado del overlay; se registra para
  diagnóstico.
- No se crean ventanas si el permiso de superposición no está concedido.

## Relaciones

```text
DetectionSignal ──► BlockRuleMatcher ──► BlockMatch
BlockMatch ──► OverlayState
BlockMatch ──► FrictionConfig ──► OverlayController
BlockMatch ──► BlockLog (una vez por activación)
```

No hay nuevas tablas, claves foráneas ni migraciones.
