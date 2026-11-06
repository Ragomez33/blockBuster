# Contracts: Coordinador de bloqueos y Overlay

**Feature**: `002-overlay-block-coordinator`  
**Scope**: `:core:common`, `:core:accessibility`, `:core:overlay`, `:app`.

## 1. Evento y bus reactivo

### `DetectionEvent.ScreenDetected`

```kotlin
data class ScreenDetected(
    val packageName: String,
    val className: String?,
    override val timestampEpochMillis: Long,
    val target: String? = null,
) : DetectionEvent
```

`target` es opcional para conservar compatibilidad con los emisores actuales.
Un `target` nulo o vacío no produce bloqueo.

### `BlockEventBus`

```kotlin
interface BlockEventBus {
    val events: SharedFlow<DetectionEvent>
    val currentForegroundPackage: StateFlow<String?>
    val currentDetection: StateFlow<DetectionSignal?>

    fun publish(event: DetectionEvent)
}

data class DetectionSignal(
    val packageName: String,
    val target: String?,
    val className: String?,
    val timestampEpochMillis: Long,
)
```

Reglas:
- `publish(ScreenDetected)` actualiza `currentForegroundPackage` y
  `currentDetection`.
- `ServiceConnectionChanged(connected = false)` limpia `currentDetection`.
- `events` se conserva como `SharedFlow` para auditoría/consumidores puntuales.

## 2. Extracción de objetivo

```kotlin
interface TargetExtractor {
    fun extract(event: AccessibilityEvent): String?
}
```

Ubicación: `:core:accessibility`.

Prioridad de extracción:
1. `event.text` con contenido no vacío.
2. `event.contentDescription`.
3. Texto del nodo raíz activo, con recorrido acotado y sin bloquear el hilo
   principal.

Si no hay texto útil, devuelve `null`. El extractor no interpreta reglas ni
decide bloqueos.

## 3. Matcher de reglas

```kotlin
interface BlockRuleMatcher {
    fun findMatch(
        target: String?,
        rules: List<BlockRule>,
    ): BlockMatch?
}

data class BlockMatch(
    val rule: BlockRule,
    val target: String,
    val normalizedTarget: String,
    val detectedAtEpochMillis: Long,
)
```

Ubicación: `:core:overlay`, `com.blockbuster.core.overlay.matching`.

Semántica:
- `DOMAIN`: host igual o subdominio; normalización sin esquema, `www.`, ruta,
  query ni fragmento; sin distinguir mayúsculas.
- `KEYWORD_SHORTS` / `KEYWORD_REELS`: contención sin distinguir mayúsculas.
- Precedencia: menor `id`.
- Objetivo nulo/vacío: `null`.

## 4. Coordinador

```kotlin
@Singleton
class OverlayCoordinator @Inject constructor(...) {
    val state: StateFlow<OverlayState>
    fun start()
}
```

Contrato:
- `start()` es idempotente.
- Colecta `currentDetection`, `observeActiveRules()` y
  `overlayController.isOverlayVisible`.
- Evalúa fuera del hilo principal; la consulta de reglas usa el flujo Room ya
  ejecutado sobre IO.
- Solicita `showFriction(config)` cuando hay match y no está descartado.
- Solicita `hideFriction()` cuando no hay match, el servicio se desconecta o el
  objetivo cambia.
- Audita una sola vez por activación mediante
  `repository.recordBlock(...)`; un fallo no impide el overlay.
- No conoce Compose ni WindowManager; solo depende de contratos.

### Estado

```kotlin
sealed interface OverlayState {
    data object Idle : OverlayState
    data class Blocking(
        val match: BlockMatch,
        val audited: Boolean,
    ) : OverlayState
    data class Dismissed(val match: BlockMatch) : OverlayState
}
```

## 5. Controlador del overlay

```kotlin
interface OverlayController {
    val isOverlayVisible: StateFlow<Boolean>
    fun showFriction(config: FrictionConfig)
    fun hideFriction()
}
```

Garantías:
- `showFriction` es idempotente para el mismo `packageName`/objetivo.
- `showFriction` reemplaza la ventana si cambia el objetivo.
- `hideFriction` es idempotente y siempre deja `isOverlayVisible = false`.
- La ventana usa `MATCH_PARENT`, `TYPE_APPLICATION_OVERLAY` y es táctil.
- No se establece `FLAG_NOT_TOUCH_MODAL` mientras bloquea.
- Si falta `SYSTEM_ALERT_WINDOW`, no se crea la ventana y no se lanza una
  excepción al consumidor.

### Ciclo de vida

- `ComposeView` con `LifecycleOwner`, `SavedStateRegistryOwner` y
  `ViewModelStoreOwner` propios.
- `hideFriction` ejecuta `disposeComposition()`, destruye el lifecycle y llama
  `removeViewImmediate`.
- `OverlayManager` no retiene `host` ni `config` después de ocultar.
- Mostrar/ocultar 50 veces no debe dejar vistas retenidas.

### Limitación del sistema

Un overlay normal no puede impedir de forma absoluta Home/Recientes. Se aplica
inmersión temporal y se documenta que la supresión total requiere modo kiosco,
device owner o administración del dispositivo, fuera de alcance.

## 6. Auditoría

```kotlin
suspend fun recordBlock(
    ruleId: Long,
    target: String,
    timestamp: Long,
): AppResult<Long>
```

Reglas:
- Se invoca una vez por transición a bloqueo.
- No se invoca en cada evento repetido mientras el overlay sigue visible.
- Un `AppResult.Failure` se registra para diagnóstico y no cancela el overlay.
- Los registros sobreviven a la eliminación de la regla (sin FK, ya existente).
