# Contract: Onboarding UI & System Actions

**Feature**: `005-onboarding-setup-wizard`
**Type**: Contrato interno de UI/aplicación (app Android)
**Date**: 2026-11-05

Este contrato fija las interfaces observables entre el estado de onboarding, la
pantalla Compose y los intents del sistema. No expone APIs públicas fuera de la
aplicación.

## 1. OnboardingRepository

```kotlin
@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    val state: StateFlow<OnboardingState>
    fun refresh()
}
```

**Contrato**

- `state.value` es siempre un estado evaluado; la primera emisión se calcula de
  forma síncrona en la construcción.
- `refresh()` reevalúa los tres permisos y emite un nuevo `OnboardingState`
  aunque los valores no cambien (o aplica `distinctUntilChanged` si no hay
  consumidores afectados).
- `state` no completa nunca mientras viva el proceso.
- No realiza I/O de disco ni trabajo en segundo plano prolongado.

## 2. OnboardingViewModel

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    repository: OnboardingRepository,
) : ViewModel() {
    val uiState: StateFlow<OnboardingUiState>
    fun refresh()
    fun openOverlaySettings(context: Context)
    fun openAccessibilitySettings(context: Context)
    fun openDeviceAdminSettings(context: Context)
    fun consumeError()
}
```

**Contrato**

- `uiState` refleja `repository.state` y añade `actionError`.
- Las funciones `open*` no lanzan excepciones; devuelven `Unit` y, si falla, se
  publica un `actionError` con un recurso de mensaje.
- `refresh()` es idempotente y seguro de llamar múltiples veces.

## 3. OnboardingScreen

```kotlin
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenDeviceAdminSettings: () -> Unit,
    onConsumeError: () -> Unit,
)
```

**Contrato de presentación**

- Muestra un título/introducción y exactamente tres tarjetas, en orden:
  1. Superposición
  2. Accesibilidad
  3. Administrador del dispositivo
- Cada tarjeta muestra:
  - título y descripción breve;
  - estado `"✔ Activo"` cuando el permiso está concedido (color primario);
  - estado pendiente y botón de acción cuando no lo está.
- Las tarjetas concedidas pueden seguir mostrando su estado activo y ocultar el
  botón (o mostrarlo deshabilitado); no se exige acción sobre ellas.
- No hay botón para saltar el asistente ni para acceder al dashboard.
- Cuando `actionError != null`, se muestra un `Snackbar`; al descartarlo o
  transcurrir su duración se invoca `onConsumeError`.
- La pantalla es desplazable verticalmente y usable con fuentes grandes.

## 4. Intents del sistema

`OnboardingIntents` (helper sin estado) implementa:

| Paso | Intent principal | Datos | Fallbacks |
|------|------------------|-------|-----------|
| Overlay | `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` | `Uri.parse("package:<packageName>")` | mismo action sin URI → `ACTION_MANAGE_APPLICATIONS_SETTINGS` → `ACTION_SETTINGS` |
| Accesibilidad | `Settings.ACTION_ACCESSIBILITY_SETTINGS` | — | `ACTION_SETTINGS` |
| Device Admin | `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN` | `EXTRA_DEVICE_ADMIN = ComponentName(context, BlockerDeviceAdminReceiver::class.java)` + `EXTRA_ADD_EXPLANATION` opcional | `ACTION_SECURITY_SETTINGS` → `ACTION_SETTINGS` |

**Contrato de seguridad**

- Antes de lanzar: `intent.resolveActivity(context.packageManager) != null`.
- Lanzamiento envuelto en `runCatching`.
- Si el intent principal falla, se prueba el siguiente fallback.
- Si todos fallan, se devuelve `false` y el ViewModel publica `actionError`.
- Nunca se propaga una excepción a la UI ni se cierra la actividad.

## 5. Routing raíz

```kotlin
@Composable
fun BlockBusterRoot(
    onboardingState: OnboardingState,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenDeviceAdminSettings: () -> Unit,
)
```

**Contrato**

- `onboardingState.isComplete == false` → renderiza únicamente
  `OnboardingScreen`; `BlockBusterNavHost` no se compone.
- `onboardingState.isComplete == true` → renderiza `BlockBusterNavHost`.
- `MainActivity.onResume()` invoca `OnboardingViewModel.refresh()`.
- El estado inicial se obtiene de forma síncrona; no se muestra el dashboard
  antes de la primera evaluación.

## 6. Textos requeridos

Recursos en `app/src/main/res/values/strings.xml` (valores propuestos):

- `onboarding_title`
- `onboarding_subtitle`
- `onboarding_step_overlay_title` / `_description` / `_action`
- `onboarding_step_accessibility_title` / `_description` / `_action`
- `onboarding_step_admin_title` / `_description` / `_action`
- `onboarding_status_active` = `"✔ Activo"`
- `onboarding_status_pending` = `"Pendiente"`
- `onboarding_error_settings_unavailable`
- `onboarding_error_try_again` (opcional para Snackbar)
