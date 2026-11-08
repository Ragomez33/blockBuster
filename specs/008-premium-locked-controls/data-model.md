# Data Model: Controles Premium Bloqueados y Visibles

**Feature**: `008-premium-locked-controls`
**Date**: 2026-11-06

Esta feature no introduce persistencia nueva. Modela el estado de presentación
que transforma `IsPremiumState` en habilitación/bloqueo visual y en la acción
efectiva de cada control.

## PremiumOption

Enumera las funciones Premium de la pestaña Blocking.

| Valor | Categoría | Control actual |
|-------|-----------|----------------|
| `STRICT_SHORTS_REELS` | Advanced Blocking | Interruptor |
| `FRICTION_TIME_CUSTOMIZATION` | Customize Blocked screen | Fila que abre editor |
| `ACCOUNTABILITY_PARTNER` | Advanced Blocking | Fila que abre editor |

**Reglas**

- Toda opción Premium MUST permanecer visible para cuentas Free.
- Los presets gratuitos de Content Blocking no forman parte de este enum.

## PremiumControlState

Estado de presentación derivado de la suscripción y de la opción.

| Campo | Tipo | Regla |
|-------|------|-------|
| `isPremium` | `Boolean` | Copia del estado reactivo de la cuenta. |
| `isLocked` | `Boolean` | `isLocked = !isPremium`. |
| `enabled` | `Boolean` | `enabled = isPremium`. |
| `alpha` | `Float` | `1f` si Premium; `0.55f` si Free. |
| `showBadge` | `Boolean` | `true` si Free. |
| `action` | `PremiumControlAction` | Ver abajo. |

**Invariantes**

- `isLocked == true` implica `enabled == false`, `alpha < 1f` y `showBadge == true`.
- `enabled == true` implica `isLocked == false`, `alpha == 1f` y `showBadge == false`.
- El estado persistido de la opción no se modifica al cambiar entre Free y Premium.

## PremiumControlAction

Acción efectiva de la interfaz.

| Valor | Cuándo | Efecto |
|-------|--------|--------|
| `TOGGLE` | Premium + interruptor | Cambia el valor persistido. |
| `EDIT` | Premium + fila de ajuste | Abre el editor correspondiente. |
| `OPEN_SUBSCRIPTION` | Free | Abre la suscripción; no cambia el valor. |

**Reglas**

- Cualquier interacción Free produce `OPEN_SUBSCRIPTION`.
- Ninguna acción Free puede derivar en escritura de `PremiumRepository`.
- Los taps repetidos producen como máximo una navegación visible a suscripción.

## PremiumToggleRowState

Extensión de presentación para interruptores.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `checked` | `Boolean` | Valor persistido actual. |
| `control` | `PremiumControlState` | Estado de habilitación/acción. |
| `title` | recurso de texto | Título del interruptor. |
| `description` | recurso de texto | Explicación del beneficio. |

## PremiumActionRowState

Extensión de presentación para filas que abren un editor.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `valueLabel` | `String` o recurso | Valor actual mostrado (segundos, partner). |
| `control` | `PremiumControlState` | Estado de habilitación/acción. |
| `title` | recurso de texto | Título de la fila. |
| `description` | recurso de texto | Explicación. |

## State Transitions

```text
[Free]
  ├─ opciones Premium visibles, atenuadas y con badge
  ├─ tap interruptor ──▶ OPEN_SUBSCRIPTION (valor sin cambios)
  ├─ tap fila fricción ──▶ OPEN_SUBSCRIPTION (editor no se abre)
  └─ tap fila partner ──▶ OPEN_SUBSCRIPTION (editor no se abre)

[Free] ──compra simulada completada──▶ [Premium]
[Premium]
  ├─ opciones habilitadas al 100%
  ├─ tap interruptor ──▶ TOGGLE (persiste)
  └─ tap fila ──▶ EDIT (abre editor)

[Premium] ──estado leído como no disponible/revocado──▶ [Free]
```

## Validation Rules

- Un fallo de lectura de suscripción se representa como `isPremium = false`.
- El badge y el estado deshabilitado se muestran siempre que `isPremium = false`.
- El switch Free mantiene el último valor persistido visible, aunque no se pueda
  cambiar.
- El editor Free nunca se abre.
- El estado visual se recalcula por el flujo reactivo; no se cachea localmente.
- Los presets gratuitos no se ven afectados por `PremiumControlState`.
