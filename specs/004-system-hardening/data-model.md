# Data Model: Hardening del sistema y prevención de desinstalación

**Feature**: `004-system-hardening`  
**Persistence impact**: Nuevo estado booleano de protección en DataStore principal
y en DataStore de almacenamiento protegido por dispositivo. No hay cambios en
Room.

## Modelos de dominio (`:core:common`)

### `SensitiveScreenCategory`

| Valor | Significado |
|---|---|
| `ACCESSIBILITY_SETTINGS` | Ajustes del servicio de accesibilidad |
| `APP_INFO` | Información de app, forzar cierre, borrar datos o desinstalar |
| `DEVICE_ADMIN_SETTINGS` | Administradores de dispositivo |

### `SensitiveSystemScreen`

| Campo | Tipo | Nulo | Descripción |
|---|---|---|---|
| `category` | `SensitiveScreenCategory` | No | Tipo de pantalla sensible |
| `packageName` | `String` | No | Paquete de la pantalla del sistema |
| `className` | `String` | Sí | Clase/actividad si está disponible |
| `detectedAtEpochMillis` | `Long` | No | Momento de la detección |

### `ProtectionThreatType`

| Valor | Significado |
|---|---|
| `SENSITIVE_SCREEN` | Pantalla sensible en primer plano |
| `ADMIN_DISABLE_REQUESTED` | Intento de desactivar el administrador |
| `ADMIN_DISABLED` | Administrador ya desactivado |

### `ProtectionThreat`

| Campo | Tipo | Nulo | Descripción |
|---|---|---|---|
| `type` | `ProtectionThreatType` | No | Tipo de amenaza |
| `screen` | `SensitiveSystemScreen?` | Sí | Presente para `SENSITIVE_SCREEN` |
| `timestampEpochMillis` | `Long` | No | Momento de la señal |

### `ProtectionState`

| Campo | Tipo | Descripción |
|---|---|---|
| `enabled` | `Boolean` | La protección debe estar activa |
| `lastUpdatedEpochMillis` | `Long` | Última actualización |
| `source` | `String` | Origen del cambio (usuario, admin, boot) |

### `FrictionMode`

| Valor | Uso |
|---|---|
| `STANDARD` | Fricción normal de 60 s + transcripción |
| `PROTECTION` | Bloqueo de pantalla sensible, sin desbloqueo |

### `OverlayState` extendido

```kotlin
sealed interface OverlayState {
    data object Idle : OverlayState
    data class Blocking(val match: BlockMatch, val audited: Boolean) : OverlayState
    data class Dismissed(val match: BlockMatch) : OverlayState
    data class ProtectionBlocking(val screen: SensitiveSystemScreen) : OverlayState
}
```

## Estado persistido

### DataStore principal (`blockbuster_preferences`)

| Clave | Tipo | Descripción |
|---|---|---|
| `protection_enabled` | `Boolean` | Estado principal de protección |
| `protection_updated_at` | `Long` | Última actualización |
| `protection_source` | `String` | Origen del último cambio |

### DataStore de arranque seguro (`blockbuster_boot_preferences`)

Se crea sobre `context.createDeviceProtectedStorageContext()`:

| Clave | Tipo | Descripción |
|---|---|---|
| `protection_enabled` | `Boolean` | Copia mínima disponible antes del desbloqueo |
| `protection_updated_at` | `Long` | Última actualización |

Reglas:
- Cada cambio de protección escribe ambas copias.
- Si la copia segura no existe, `LOCKED_BOOT_COMPLETED` difiere a
  `BOOT_COMPLETED`.
- Si la copia segura y la principal difieren, gana la principal una vez
  desbloqueado; se registra el conflicto.

## Clasificación de pantallas sensibles

`SettingsInterceptor` evalúa `packageName` y `className`:

| Categoría | Señales |
|---|---|
| `ACCESSIBILITY_SETTINGS` | Paquete de Settings y clase contiene `Accessibility` |
| `APP_INFO` | Paquete de Settings y clase contiene `AppInfo`, `InstalledAppDetails`, `ApplicationInfo` o `Uninstall` |
| `DEVICE_ADMIN_SETTINGS` | Paquete de Settings y clase contiene `DeviceAdmin` |

Paquetes considerados Settings:
- `com.android.settings`
- `com.google.android.settings`
- Cualquier paquete cuyo nombre contenga `settings` y cuya clase coincida con
  las señales anteriores (heurística para fabricantes).

Un paquete/clase que no coincida produce `null` y no bloquea.

## Transiciones de estado

```text
Protección desactivada
        │ usuario activa / admin onEnabled
        ▼
Protección activa (DataStore principal + boot-safe)
        │
        ├── pantalla sensible detectada → ProtectionThreat(SENSITIVE_SCREEN)
        │        └── OverlayState.ProtectionBlocking / overlay PROTECTION
        │
        ├── usuario sale de la pantalla sensible → threat null
        │        └── overlay se retira, estado vuelve a Idle/Blocking normal
        │
        ├── onDisableRequested → ProtectionThreat(ADMIN_DISABLE_REQUESTED)
        │        └── advertencia + intento de overlay
        │
        ├── onDisabled → ProtectionThreat(ADMIN_DISABLED)
        │        └── protección marcada como desactivada / diagnóstico
        │
        ├── BOOT_COMPLETED / MY_PACKAGE_REPLACED con protección activa
        │        └── iniciar FGS + recalibrar salud
        │
        └── usuario desactiva protección → DataStore false en ambas copias
```

## Validaciones

| Regla | Aplicación |
|---|---|
| El estado de protección debe leerse antes de arrancar | `BootReceiver` |
| No iniciar el servicio si la protección está inactiva | `BootReceiver` |
| Las amenazas de alta prioridad no dependen de reglas | `OverlayCoordinator` |
| El overlay PROTECTION no ofrece desbloqueo | `FrictionScreen` |
| El overlay PROTECTION se retira al salir de la pantalla sensible | coordinador + threat null |
| La remoción del admin no se puede garantizar | `onDisableRequested`/`onDisabled` best-effort |
| No duplicar overlays por eventos repetidos | estado idempotente |
| No entrar en bucles si el SO rechaza FGS/overlay | `runCatching` + diagnóstico |

## Relaciones

```text
SettingsInterceptor ──► ProtectionEventBus ──► OverlayCoordinator
DeviceAdminReceiver ──► ProtectionEventBus
BootReceiver ──► ProtectionStateRepository ──► MonitoringForegroundService
ProtectionThreat ──► OverlayState.ProtectionBlocking
```
