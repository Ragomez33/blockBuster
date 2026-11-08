# Data Model: Onboarding Setup Wizard

**Feature**: `005-onboarding-setup-wizard`
**Date**: 2026-11-05

La funcionalidad no persiste datos. Las entidades son modelos inmutables en
memoria derivados del estado real del sistema operativo.

## OnboardingState

Estado completo del asistente, emitido por `OnboardingRepository` y consumido
por `OnboardingViewModel`/`OnboardingScreen`.

| Campo | Tipo | Origen | Reglas |
|-------|------|--------|--------|
| `overlayGranted` | `Boolean` | `Settings.canDrawOverlays(context)` | `true` solo si el permiso de superposición está concedido. |
| `accessibilityEnabled` | `Boolean` | `AccessibilityManager` + `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` para `BlockBusterAccessibilityService` | `true` solo si el componente completo está habilitado. |
| `deviceAdminActive` | `Boolean` | `DevicePolicyManager.isAdminActive(ComponentName(context, BlockerDeviceAdminReceiver::class.java))` | `true` solo si el receiver es administrador activo. |
| `isComplete` | `Boolean` (derivado) | `overlayGranted && accessibilityEnabled && deviceAdminActive` | Nunca se almacena por separado; se calcula como propiedad. |

**Invariantes**

- `isComplete == true` implica los tres booleanos en `true`.
- `isComplete == false` implica al menos un booleano en `false`.
- El estado se recalcula desde el sistema en cada `refresh()`; no se cachea
  entre sesiones.
- El valor inicial se calcula síncronamente al construir el repositorio.

## PermissionStep (modelo de presentación)

Describe cada tarjeta del asistente. Puede derivarse en la UI a partir de
`OnboardingState`; no se persiste.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `step` | `OnboardingStep` (`OVERLAY`, `ACCESSIBILITY`, `DEVICE_ADMIN`) | Identificador y orden recomendado. |
| `granted` | `Boolean` | Derivado del campo correspondiente de `OnboardingState`. |
| `title` | recurso de texto | Título del paso. |
| `description` | recurso de texto | Explicación del propósito del permiso. |
| `actionLabel` | recurso de texto | Etiqueta del botón ("Configurar" / "Activar"). |

**Orden**: `OVERLAY` → `ACCESSIBILITY` → `DEVICE_ADMIN`.

## OnboardingUiState

Estado de presentación expuesto por `OnboardingViewModel`.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `permissions` | `OnboardingState` | Estado de los tres permisos. |
| `isComplete` | `Boolean` | Atajo de `permissions.isComplete` para el routing raíz. |
| `actionError` | `Int?` (res id) | Mensaje a mostrar en `Snackbar` cuando un intent no puede abrirse. Se limpia tras consumirse. |

## OnboardingAction (eventos de UI)

| Acción | Entrada | Efecto |
|--------|---------|--------|
| `Refresh` | — | Recalcula los tres permisos desde el sistema. |
| `OpenOverlaySettings` | `Context` | Lanza ajustes de superposición con fallback. |
| `OpenAccessibilitySettings` | `Context` | Lanza ajustes de accesibilidad con fallback. |
| `OpenDeviceAdminSettings` | `Context` | Lanza alta de administrador con fallback. |
| `ConsumeError` | — | Limpia `actionError`. |

## State Transitions

```text
[Sin evaluar]
   │ constructor del repositorio / refresh()
   ▼
[Evaluado: isComplete = false] ──(conceder todos los permisos + ON_RESUME)──▶ [Evaluado: isComplete = true]
   ▲                                                                                    │
   └──────────────────(revocar cualquier permiso + ON_RESUME)───────────────────────────┘

[Evaluado: isComplete = false] ──(acción de ajustes falla)──▶ [mismo estado + actionError]
[Evaluado: isComplete = ...]  ──(servicio de accesibilidad conecta/desconecta)──▶ [recalculado]
```

## Validation Rules

- Ningún permiso se infiere de preferencias guardadas; siempre se consulta al
  sistema.
- La accesibilidad se valida por componente completo, no por nombre de paquete.
- Un intent que no resuelve no cambia el estado de permisos; solo genera
  `actionError`.
- `actionError` no bloquea el asistente y puede reintentarse.
- El dashboard solo se habilita cuando `isComplete` es `true` en el último
  estado emitido.
