# Contract: Premium Locked Controls UI

**Feature**: `008-premium-locked-controls`
**Type**: Contrato interno de UI Compose
**Date**: 2026-11-06

## 1. PremiumControlState

```kotlin
enum class PremiumControlAction {
    TOGGLE,
    EDIT,
    OPEN_SUBSCRIPTION,
}

data class PremiumControlState(
    val isPremium: Boolean,
    val isLocked: Boolean,
    val enabled: Boolean,
    val alpha: Float,
    val showBadge: Boolean,
    val action: PremiumControlAction,
)
```

**Contrato**

- `isLocked = !isPremium`
- `enabled = isPremium`
- `alpha = if (isPremium) 1f else 0.55f`
- `showBadge = !isPremium`
- `action = if (isPremium) TOGGLE|EDIT else OPEN_SUBSCRIPTION`
- La función de mapeo es pura y no toca repositorios ni Compose.

## 2. PremiumSwitchRow

```kotlin
@Composable
fun PremiumSwitchRow(
    isPremium: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenSubscription: () -> Unit,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
)
```

**Contrato**

- Siempre muestra título, descripción y `Switch`.
- Free:
  - `Switch(enabled = false, onCheckedChange = null)`.
  - Fila con alpha 0.55 y colores deshabilitados.
  - `PremiumBadge` visible.
  - Tap en cualquier punto de la fila -> `onOpenSubscription()`.
  - `onCheckedChange` nunca se invoca.
- Premium:
  - `Switch(enabled = true)`.
  - Fila sin alpha reducido y sin badge.
  - Tap/checked change -> `onCheckedChange(nuevoValor)`.
- Semántica:
  - `stateDescription = "Bloqueado, requiere Premium"` en Free.
  - `stateDescription = "Desbloqueado"` en Premium.
  - El badge tiene descripción textual.

## 3. PremiumActionRow

```kotlin
@Composable
fun PremiumActionRow(
    isPremium: Boolean,
    title: String,
    description: String,
    valueLabel: String,
    onEdit: () -> Unit,
    onOpenSubscription: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Contrato**

- Siempre muestra título, descripción y valor actual.
- Free:
  - Fila con alpha 0.55.
  - `PremiumBadge` visible.
  - Tap -> `onOpenSubscription()`.
  - `onEdit` nunca se invoca.
- Premium:
  - Fila habilitada, sin alpha reducido ni badge.
  - Tap -> `onEdit()`.
- Misma semántica de estado que `PremiumSwitchRow`.

## 4. BlockingScreen

**Contrato de composición**

- Pasa `uiState.isPremium` a los componentes Premium.
- `Content Blocking` mantiene `PresetSwitchCard` sin cambios.
- `Advanced Blocking` usa:
  - `PremiumSwitchRow` para restricciones estrictas de Shorts/Reels.
  - `PremiumActionRow` para partner de responsabilidad.
- `Customize Blocked screen` usa `PremiumActionRow` para el tiempo de fricción.
- `onOpenSubscription` es el mismo callback que navega a la pantalla de
  suscripción existente.
- Los diálogos de fricción/partner solo se muestran si `uiState.isPremium` y la
  acción `onEdit` fue invocada.

## 5. Reglas de interacción

1. Free + tap en switch Premium -> no cambia `checked`, abre suscripción.
2. Free + tap en fila de fricción/partner -> no abre editor, abre suscripción.
3. Free + tap repetido -> no se encola más de una navegación de suscripción.
4. Premium + tap en switch -> cambia el valor y persiste.
5. Premium + tap en fila -> abre el editor y permite guardar.
6. Cambio Free -> Premium -> los controles se habilitan sin reinicio.
7. Cambio Premium -> Free -> los controles vuelven a bloquearse sin reinicio.
8. Fallo de lectura de suscripción -> se aplica el contrato Free.

## 6. Fuera de alcance

- No se modifica `PremiumRepository`, `PremiumSettings` ni `IsPremiumState`.
- No se modifica Room, presets, `BlockRulesRepository` ni el motor de bloqueo.
- No se crea una nueva pantalla de suscripción: se reutiliza la existente.
- No se añaden dependencias de UI testing.

## 7. Recursos requeridos

- `premium_locked` (badge, existente)
- `premium_upgrade` = "Upgrade to Premium" (nuevo)
- `premium_locked_state` = "Bloqueado, requiere Premium" (nuevo)
- `premium_unlocked_state` = "Desbloqueado" (nuevo)
- `premium_locked_hint` = "Suscríbete para cambiar esta opción" (nuevo)
