# Data Model: Pantalla de fricción y anti-portapapeles

**Feature**: `003-friction-anti-paste`  
**Persistence impact**: Ninguno. El estado de fricción es efímero y vive en
memoria mientras el overlay está visible.

## Modelos de dominio/UI (`:core:overlay`)

### `FrictionSession`

Estado fuente de verdad de una sesión de fricción.

| Campo | Tipo | Nulo | Descripción |
|---|---|---|---|
| `target` | `String` | No | Objetivo bloqueado que identifica la sesión |
| `requiredText` | `String` | No | Párrafo fijo de ~300 palabras |
| `startedAtElapsedRealtime` | `Long` | No | Marca de `elapsedRealtime` al iniciar los 60 s |
| `input` | `String` | No | Texto transcrito actualmente |
| `warningVisible` | `Boolean` | No | Aviso de pegado activo |
| `warningUntilElapsedRealtime` | `Long?` | Sí | Fin programado del aviso |

Reglas:
- `remainingMillis = max(0, 60_000 - (elapsedRealtime - startedAt))`.
- `canSubmit = remainingMillis == 0L && input == requiredText`.
- `warningVisible` es temporal (3 s) y no altera el temporizador.
- Una sesión se crea al mostrar el overlay para un `target`.
- Se reutiliza si el host se recrea para el mismo `target`.
- Se descarta al desbloquear u ocultar el overlay.
- Un `target` distinto crea una sesión nueva con `startedAt` actual e `input` vacío.

### `FrictionUiState`

Estado derivado que consume `FrictionScreen`.

| Campo | Tipo | Descripción |
|---|---|---|
| `target` | `String` | Objetivo bloqueado |
| `requiredText` | `String` | Texto de referencia |
| `input` | `String` | Texto actual |
| `remainingSeconds` | `Int` | Segundos restantes para mostrar (0–60) |
| `canSubmit` | `Boolean` | `remainingSeconds == 0 && input == requiredText` |
| `warningVisible` | `Boolean` | Aviso de pegado visible |

### `OverlayUnlock`

Evento emitido al completar la fricción.

| Campo | Tipo | Descripción |
|---|---|---|
| `target` | `String` | Objetivo desbloqueado |
| `timestampEpochMillis` | `Long` | Momento del desbloqueo |

Se publica en `OverlayController.unlockEvents: SharedFlow<OverlayUnlock>`.

### `PasteInspection`

Resultado de inspeccionar un cambio de texto.

| Campo | Tipo | Descripción |
|---|---|---|
| `insertedCharacters` | `Int` | Caracteres insertados en el cambio |
| `isPaste` | `Boolean` | `insertedCharacters > 3` |

## Modelos existentes que se reutilizan

- `FrictionConfig` (`packageName`, `appLabel`, `title`, `message`,
  `dismissLabel`): se mantiene para no romper el contrato del overlay. El
  `dismissLabel` deja de mostrarse como acción de cierre.
- `OverlayState` del coordinador: `Idle`, `Blocking`, `Dismissed`.
- `BlockLog`: no se modifica; la fricción no crea nuevas tablas.

## Transiciones de estado

```text
Overlay mostrado para target T
        │
        ▼
Sesión creada: startedAt = ahora, input = "", remaining = 60 s
        │
        ├── input cambia (manual, <=3 caracteres) → input actualizado
        │
        ├── input cambia con inserción >3 → input = "", warning = true
        │        └── 3 s después → warning = false
        │
        ├── rotación/recreación del host → misma sesión reutilizada
        │
        ├── timer llega a 0 + input == requiredText → canSubmit = true
        │
        └── usuario pulsa Desbloquear → OverlayUnlock emitido
                 │
                 ▼
        OverlayCoordinator oculta overlay y vuelve a Idle
```

## Reglas de validación

| Regla | Aplicación |
|---|---|
| Temporizador estricto de 60 000 ms | `remainingMillis` desde `startedAt` |
| El botón no se habilita antes de 0 | `canSubmit` requiere `remainingMillis == 0` |
| Coincidencia exacta | `input == requiredText`, incluyendo espacios y puntuación |
| Pegado | inserción > 3 caracteres en un solo cambio |
| Inserción permitida | hasta 3 caracteres en un cambio |
| Aviso | 3 segundos, no pausa el temporizador |
| Rotación | misma sesión; no reinicia `startedAt` |
| Nueva activación | nuevo `target` reinicia `startedAt` e `input` |
| Sin cierre inmediato | no hay botón de cierre; back consumido |
| Sin selección persistente | toolbar no-op y selección colapsada |

## Persistencia y ciclo de vida

- La sesión se retiene en `OverlayManager` (singleton) mientras el overlay está
  visible.
- `OverlayComposeHost` recibe la sesión y sincroniza el input.
- Si la ventana se recrea por rotación, se reconstruye la UI desde la sesión.
- La muerte del proceso elimina la sesión; no hay persistencia en disco.
- Al ocultar o desbloquear se descarta la sesión para evitar reutilización
  accidental.
