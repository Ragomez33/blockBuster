# Contracts: Hardening del sistema y prevención de desinstalación

**Feature**: `004-system-hardening`  
**Scope**: `:core:common`, `:core:accessibility`, `:core:overlay`, `:core:database`, `:app`.

## 1. Bus de amenazas de protección

```kotlin
interface ProtectionEventBus {
    val events: SharedFlow<ProtectionThreat>
    val currentThreat: StateFlow<ProtectionThreat?>

    fun publish(threat: ProtectionThreat)
    fun clear()
}
```

Reglas:
- `currentThreat` se actualiza con la pantalla sensible actual.
- Al salir de la pantalla sensible, `clear()` deja `currentThreat = null`.
- `ADMIN_DISABLE_REQUESTED` y `ADMIN_DISABLED` se publican como eventos de alta
  prioridad; no se borran hasta que el coordinador los consuma o se resuelva el
  estado.
- Es un singleton Hilt de proceso.

## 2. Interceptor de ajustes

```kotlin
interface SettingsInterceptor {
    fun inspect(packageName: String?, className: String?): SensitiveSystemScreen?
}
```

Reglas:
- Devuelve `null` para pantallas no sensibles.
- Categorías: accesibilidad, información de app, administradores.
- La comparación no distingue mayúsculas en la clase.
- No accede a UI ni a base de datos.

## 3. Estado de protección

```kotlin
interface ProtectionStateRepository {
    fun observeProtectionEnabled(): Flow<Boolean>
    suspend fun isProtectionEnabled(): Boolean
    suspend fun setProtectionEnabled(enabled: Boolean, source: String)

    suspend fun isBootProtectionEnabled(): Boolean
    suspend fun setBootProtectionEnabled(enabled: Boolean)
}
```

Reglas:
- `setProtectionEnabled` escribe el DataStore principal y la copia boot-safe.
- `isBootProtectionEnabled` usa el DataStore de almacenamiento protegido por
  dispositivo y no requiere desbloqueo.
- Si la copia boot-safe no está disponible, `BootReceiver` difiere a
  `BOOT_COMPLETED`.
- El origen se registra para diagnóstico.

## 4. DeviceAdminReceiver

Componente: `BlockerDeviceAdminReceiver` en `:app`.

```text
onEnabled(context, intent)
  → marcar protección activa (origen device_admin)

onDisableRequested(context, intent): CharSequence
  → devolver advertencia visible
  → publicar ProtectionThreat(ADMIN_DISABLE_REQUESTED)
  → intentar iniciar/overlay de protección sin bloquear el callback

onDisabled(context, intent)
  → publicar ProtectionThreat(ADMIN_DISABLED)
  → actualizar estado de protección
  → registrar diagnóstico
```

Políticas declaradas en `res/xml/device_admin_policies.xml`:
- `force-lock`
- `limit-password`

Limitación contractual: Android puede completar la remoción; la app no puede
impedirla. `onDisableRequested` es una advertencia best-effort.

## 5. BootReceiver

Acciones:
- `android.intent.action.BOOT_COMPLETED`
- `android.intent.action.LOCKED_BOOT_COMPLETED`
- `android.intent.action.MY_PACKAGE_REPLACED`

Comportamiento:
1. Usar `goAsync()` + corrutina (`Dispatchers.IO`).
2. Leer el estado de protección boot-safe (o principal tras desbloqueo).
3. Si está activo:
   - iniciar `MonitoringForegroundService` con `runCatching`;
   - ejecutar `ProtectionHealthChecker`;
   - registrar el resultado.
4. Si está inactivo:
   - no iniciar servicio;
   - finalizar el `PendingResult`.
5. Capturar `ForegroundServiceStartNotAllowedException` y errores de permisos.

## 6. Recalibración de salud

```kotlin
interface ProtectionHealthChecker {
    suspend fun check(): ProtectionHealth
}

data class ProtectionHealth(
    val accessibilityEnabled: Boolean,
    val overlayGranted: Boolean,
    val deviceAdminActive: Boolean,
    val checkedAtEpochMillis: Long,
)
```

Se ejecuta tras boot/package-replaced. No bloquea el arranque; registra el estado
para diagnóstico.

## 7. Overlay y coordinador

`FrictionConfig` se extiende con:

```kotlin
enum class FrictionMode { STANDARD, PROTECTION }

data class FrictionConfig(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val message: String,
    val dismissLabel: String,
    val mode: FrictionMode = FrictionMode.STANDARD,
)
```

`OverlayState` añade `ProtectionBlocking(screen)`.

`OverlayCoordinator`:
- Colecta `ProtectionEventBus.currentThreat` en un colector prioritario.
- Si `currentThreat` es una pantalla sensible:
  - muestra `FrictionScreen` en modo `PROTECTION` (sin desbloqueo);
  - publica `ProtectionBlocking`;
  - ignora temporalmente la lógica normal de reglas para no ocultar la
    protección.
- Si `currentThreat` pasa a `null`:
  - oculta el overlay de protección;
  - reevalúa el bloqueo normal y publica `Idle` o `Blocking`.
- Para `ADMIN_DISABLE_REQUESTED`/`ADMIN_DISABLED`, intenta mostrar la fricción
  de protección y registra el evento; no promete impedir la remoción.
- Eventos repetidos no crean ventanas duplicadas.

## 8. Android 14+ y background

- La app requiere `SYSTEM_ALERT_WINDOW` para el overlay.
- El servicio de monitoreo es `specialUse` y permanece en primer plano.
- Los intentos de arranque/overlay desde background se envuelven en
  `runCatching`; los fallos se registran.
- No se intenta evadir restricciones del sistema ni iniciar actividades desde
  background sin permiso.
- Si el sistema bloquea el overlay, la app no entra en bucle de reintentos.

## 9. Configuración de manifiesto

- `BlockerDeviceAdminReceiver` con `android:permission="android.permission.BIND_DEVICE_ADMIN"`,
  `android:exported="true"` y metadata `@xml/device_admin_policies`.
- `BootReceiver` con `android:directBootAware="true"`, `exported="true"` y los
  tres intent-filters.
- Permiso `RECEIVE_BOOT_COMPLETED` ya existente.
- `BIND_DEVICE_ADMIN` es permiso de componente, no `uses-permission`.
