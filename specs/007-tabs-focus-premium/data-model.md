# Data Model: Navegación por Pestañas, Focus Mode y Freemium

**Feature**: `007-tabs-focus-premium`
**Date**: 2026-11-06

La feature combina datos persistidos (Room/DataStore) con estado derivado en
memoria para navegación, temporizador y bloqueo adicional.

## IsPremiumState

Estado efectivo de la suscripción.

| Campo | Tipo | Origen | Reglas |
|-------|------|--------|--------|
| `isPremium` | `Boolean` | DataStore (`is_premium`) | Por defecto `false`; si la lectura falla, se trata como `false`. |

**Invariantes**

- Una cuenta nueva es Free.
- El cambio a Premium solo ocurre tras confirmar la compra simulada.
- Cancelar o fallar la compra no modifica el valor.
- El valor persiste entre sesiones y reinicios.

## PremiumFeature

Enumeración de funciones avanzadas.

| Valor | Descripción | Estado Free |
|-------|-------------|-------------|
| `SHORTS_REELS_RESTRICTIONS` | Reglas estrictas específicas para Shorts/Reels | Bloqueada |
| `FRICTION_TIME_CUSTOMIZATION` | Personalización de la ventana de fricción | Bloqueada |
| `ACCOUNTABILITY_PARTNER` | Configuración de partner de responsabilidad | Bloqueada |

## PremiumSettings

Ajustes avanzados persistidos en DataStore. Solo se aplican cuando
`IsPremiumState.isPremium == true`.

| Campo | Tipo | Valor por defecto | Validación |
|-------|------|-------------------|------------|
| `strictShortsReels` | `Boolean` | `false` | Solo aplicable con Premium. |
| `frictionWindowMillis` | `Long` | `60_000` | Entre `15_000` y `180_000`; se ignora si no hay Premium. |
| `accountabilityPartner` | `String?` | `null` | Texto libre recortado, máximo 120 caracteres. |

## BlocklistEntry

Entrada de lista negra o blanca.

| Campo | Tipo | Origen | Reglas |
|-------|------|--------|--------|
| `id` | `Long` | Room autogenerado | `> 0`. |
| `value` | `String` | Room | No vacío; se recorta; para apps es un package id. |
| `targetType` | `BlocklistTargetType` | Room | `APP`, `WEBSITE`, `KEYWORD`. |
| `listType` | `BlocklistType` | Room | `BLACKLIST`, `WHITELIST`. |
| `createdAt` | `Long` | Room | Milisegundos desde epoch. |

**Enums**

```text
BlocklistTargetType: APP, WEBSITE, KEYWORD
BlocklistType: BLACKLIST, WHITELIST
```

**Índices y restricciones**

- Único: `(value, target_type, list_type)`.
- Índice de consulta: `(list_type, target_type)`.
- Duplicado exacto en la misma lista y tipo: se rechaza con un error controlado.
- El mismo valor puede existir en lista negra y blanca simultáneamente; la
  whitelist gana en la evaluación.

**Reglas de coincidencia**

- `APP`: igualdad exacta con el `packageName` de la detección.
- `WEBSITE`: host exacto o subdominio del valor, sin distinguir mayúsculas.
- `KEYWORD`: contención sin distinguir mayúsculas en el objetivo textual o en
  el `className`.

## FocusSession

Sesión de productividad persistida en DataStore.

| Campo | Tipo | Valor por defecto | Reglas |
|-------|------|-------------------|--------|
| `runState` | `FocusRunState` | `IDLE` | `IDLE`, `RUNNING`, `PAUSED`. |
| `phase` | `FocusPhase` | `WORK` | `WORK`, `BREAK`. |
| `workMinutes` | `Int` | `25` | Entre 1 y 120. |
| `breakMinutes` | `Int` | `5` | Entre 1 y 60. |
| `phaseStartedAtEpochMillis` | `Long?` | `null` | Requerido cuando `RUNNING`. |
| `phaseEndsAtEpochMillis` | `Long?` | `null` | Requerido cuando `RUNNING`. |
| `pausedRemainingMillis` | `Long?` | `null` | Requerido cuando `PAUSED`. |
| `completedWorkCycles` | `Int` | `0` | No negativo. |

**Invariantes**

- `RUNNING` implica `phaseEndsAtEpochMillis > phaseStartedAtEpochMillis`.
- `PAUSED` implica `pausedRemainingMillis >= 0`.
- `IDLE` no aplica bloqueo adicional.
- Solo la fase `WORK` con `RUNNING` aplica bloqueo extra.
- Al expirar `WORK`, se pasa a `BREAK`; al expirar `BREAK`, se vuelve a `WORK`.
- Detener limpia los campos temporales y retira el bloqueo extra.

## FocusModeRule

Regla sintética generada en memoria para apps de ocio durante Focus Mode.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `packageName` | `String` | Identificador de la app de ocio. |
| `rule` | `BlockRule` | Regla `PACKAGE` con ID negativo estable. |

No se persiste. `LeisureAppCatalog` es la fuente de paquetes.

## MainTab

| Valor | Etiqueta | Contenido |
|-------|----------|-----------|
| `BLOCKING` | Blocking | Presets, opciones avanzadas y personalización de fricción |
| `BLOCKLIST` | Blocklist | Listas negras/blancas de apps, sitios y keywords |
| `SETTINGS` | Settings | Cuenta, actualizaciones, privacidad y soporte |

## State Transitions

### Suscripción

```text
[Free] ──(compra simulada confirmada)──▶ [Premium]
[Premium] ──(no hay flujo de cancelación en esta iteración)──▶ [Premium]
[Free/Premium] ──(lectura fallida)──▶ [Free efectivo]
```

### Focus Session

```text
[IDLE]
  │ start(work, break)
  ▼
[WORK · RUNNING] ──(expira work)──▶ [BREAK · RUNNING] ──(expira break)──▶ [WORK · RUNNING]
  │ pause()                              │ pause()
  ▼                                      ▼
[WORK · PAUSED] ──resume()──▶ [WORK · RUNNING]
  │ stop()                               │ stop()
  ▼                                      ▼
[IDLE] ◀─────────────────────────────────┘
```

### Blocklist

```text
[Entrada válida] ──(insert)──▶ [Persistida]
[Persistida] ──(delete)──▶ [Eliminada]
[Persistida] ──(duplicado mismo tipo/lista)──▶ [Rechazada]
```

## Validation Rules

- `value` no puede estar vacío ni contener solo espacios.
- Para `APP`, el valor debe tener formato de package id (`a.b.c`).
- Para `WEBSITE`, se normaliza a minúsculas y se elimina el esquema.
- Para `KEYWORD`, se conserva el texto recortado.
- Las duraciones de Focus se validan antes de persistir; valores inválidos no
  inician sesión.
- El estado Premium se evalúa antes de aplicar cualquier ajuste avanzado.
- La ventana de fricción personalizada se ignora si la cuenta es Free.
