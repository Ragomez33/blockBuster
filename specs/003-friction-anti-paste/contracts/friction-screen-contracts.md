# Contracts: Pantalla de fricción y anti-portapapeles

**Feature**: `003-friction-anti-paste`  
**Scope**: `:core:overlay` y contratos compartidos de `:core:common`.

## 1. Evento de desbloqueo

```kotlin
data class OverlayUnlock(
    val target: String,
    val timestampEpochMillis: Long,
)
```

`OverlayController` se extiende con:

```kotlin
interface OverlayController {
    val isOverlayVisible: StateFlow<Boolean>
    val unlockEvents: SharedFlow<OverlayUnlock>

    fun showFriction(config: FrictionConfig)
    fun hideFriction()
}
```

Reglas:
- `unlockEvents` es un flujo caliente de eventos de desbloqueo completados.
- `OverlayManager` emite exactamente un evento por pulsación válida del botón.
- La pantalla no llama a `hideFriction`; solo emite `onUnlock`.
- `OverlayCoordinator` recolecta `unlockEvents`, oculta el overlay, limpia la
  activación y vuelve a `Idle`.
- Un desbloqueo no genera una nueva auditoría de bloqueo.

## 2. Contrato de `FrictionScreen`

```kotlin
@Composable
fun FrictionScreen(
    session: FrictionSession,
    onInputChanged: (String) -> Unit,
    onWarning: () -> Unit,
    onUnlock: () -> Unit,
)
```

Responsabilidades:
- Renderizar pantalla completa oscura, sin acción de cierre.
- Mostrar la cuenta regresiva de 60 s.
- Mostrar el texto objetivo y el campo de transcripción.
- Aplicar anti-paste y notificar el aviso.
- Habilitar el botón solo con `canSubmit`.
- Invocar `onUnlock` una sola vez al pulsar el botón habilitado.

`FrictionSession` sigue siendo la fuente de verdad; la UI no reinicia el
temporizador ni cambia `startedAt`.

## 3. Contrato de anti-paste

```kotlin
object FrictionInput {
    fun insertedCharacterCount(oldText: String, newText: String): Int
    fun isPaste(oldText: String, newText: String): Boolean =
        insertedCharacterCount(oldText, newText) > 3
}
```

Semántica:
- Se calcula el prefijo y sufijo comunes entre `oldText` y `newText`.
- `inserted = newText.length - commonPrefix - commonSuffix`.
- `inserted > 3` se considera pegado.
- Un cambio de hasta 3 caracteres se acepta como escritura manual.
- Al detectar pegado, el caller vacía `input`, preserva el temporizador y activa
  el aviso.

## 4. Contrato de sesión

```kotlin
data class FrictionSession(
    val target: String,
    val requiredText: String,
    val startedAtElapsedRealtime: Long,
    val input: String = "",
    val warningVisible: Boolean = false,
    val warningUntilElapsedRealtime: Long? = null,
)
```

Derivados:
- `remainingMillis(now)` = `max(0, 60_000 - (now - startedAt))`.
- `remainingSeconds(now)` = techo de `remainingMillis / 1000`.
- `canSubmit(now)` = `remainingMillis == 0 && input == requiredText`.

Reglas:
- Se reutiliza para el mismo `target` al recrear el host.
- Se crea para un `target` nuevo.
- Se descarta al ocultar/desbloquear.

## 5. Contrato del manager

`OverlayManager`:
- Mantiene la `FrictionSession` activa mientras el overlay está visible.
- Al mostrar el mismo `target` de nuevo, reutiliza la sesión.
- Al mostrar un `target` distinto, descarta la anterior y crea una nueva.
- Pasa la sesión y callbacks a `OverlayComposeHost`.
- Emite `OverlayUnlock` al recibir el callback de la pantalla.
- En `hideFriction` limpia la sesión, la vista y la composición.
- No cierra el overlay directamente por el botón de la pantalla.

## 6. Contrato de teclado, selección y portapapeles

- `LocalTextToolbar` se reemplaza por una implementación no-op.
- El campo usa `TextFieldValue` con selección colapsada al final tras cada
  cambio; no se conservan selecciones.
- `ClipboardManager` nativo limpia el portapapeles al mostrar la fricción y tras
  detectar un pegado.
- No se usan librerías externas para portapapeles o teclado.
- El botón atrás se consume con `BackHandler` y un
  `OnBackPressedDispatcherOwner` propio del overlay.

## 7. Contrato del coordinador

- Colecta `unlockEvents` además de `currentDetection`, reglas, temporizador y
  visibilidad.
- Al recibir `OverlayUnlock`:
  1. Anula la activación actual.
  2. Limpia `dismissedActivation` y `auditedActivation`.
  3. Llama `hideFriction()`.
  4. Publica `OverlayState.Idle`.
- No debe interpretar el ocultamiento por desbloqueo como `Dismissed`.
- Los eventos repetidos de desbloqueo no deben provocar múltiples cierres ni
  auditorías.

## 8. Contrato de estado y rotación

- `FrictionSession` se conserva en `OverlayManager` mientras el overlay existe.
- La rotación no reinicia `startedAt` ni borra `input`.
- La restauración tras muerte del proceso es best-effort; si no hay sesión, se
  crea una nueva y el temporizador vuelve a 60 s.
- `FrictionScreen` usa el estado de sesión para inicializar `TextFieldValue` y
  el contador; no usa un temporizador que reinicie en cada recomposición.
