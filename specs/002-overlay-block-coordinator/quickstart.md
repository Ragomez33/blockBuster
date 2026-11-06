# Quickstart: Coordinador de bloqueos y Overlay

**Feature**: `002-overlay-block-coordinator`  
**Prerequisites**: JDK 17+, Android SDK 37, dispositivo/emulador para validación
visual del overlay. `local.properties` con `sdk.dir`.

## Comandos

```bash
# Compilar módulos afectados
./gradlew :core:overlay:assembleDebug :app:assembleDebug

# Pruebas JVM del matcher, coordinador y overlay host
./gradlew :core:overlay:testDebugUnitTest :core:database:testDebugUnitTest

# Lint
./gradlew :app:lintDebug
```

## Escenario 1 — Coincidencia de dominio

1. Insertar una regla activa `DOMAIN` con patrón `example.com`.
2. Publicar una detección con `target = "https://www.example.com/watch?v=1"`.
3. Observar el coordinador y el controlador de overlay.

**Resultado esperado**:

- `BlockRuleMatcher` devuelve la regla.
- El coordinador solicita mostrar el overlay.
- `OverlayState.Blocking` refleja la regla y el objetivo normalizado.
- Se inserta exactamente un `BlockLog`.

## Escenario 2 — Sin coincidencia

1. No insertar reglas activas o usar un target distinto.
2. Publicar la detección.

**Resultado esperado**:

- No se muestra overlay.
- `OverlayState` permanece `Idle`.
- No se inserta auditoría.

## Escenario 3 — Precedencia determinista

1. Insertar dos reglas activas que coincidan con el mismo target, con IDs
   distintos.
2. Publicar la detección.

**Resultado esperado**:

- Se elige la regla de menor `id`.
- Se genera una sola activación y una sola auditoría.

## Escenario 4 — Deduplicación y auditoría

1. Activar un bloqueo.
2. Publicar diez detecciones repetidas del mismo target mientras el overlay
   sigue visible.

**Resultado esperado**:

- No se crean ventanas adicionales.
- No se insertan logs adicionales.
- El primer evento genera exactamente un log.

## Escenario 5 — Cierre manual

1. Mostrar el overlay para un target.
2. Ejecutar la acción de cierre del overlay.
3. Publicar otra detección del mismo target antes de cambiar de objetivo.

**Resultado esperado**:

- El overlay se oculta y la composición se destruye.
- La activación queda en `Dismissed` y no se reabre inmediatamente.
- Una nueva detección tras cambiar de objetivo puede volver a bloquear.

## Escenario 6 — Cambio de reglas con overlay visible

1. Mostrar el overlay para un target con una regla activa.
2. Deshabilitar o eliminar la regla.
3. Observar el coordinador.

**Resultado esperado**:

- El coordinador reevalúa y oculta el overlay.
- El estado vuelve a `Idle`.
- La auditoría histórica permanece.

## Escenario 7 — Permiso de superposición ausente

1. Revocar `SYSTEM_ALERT_WINDOW`.
2. Publicar una detección con coincidencia.

**Resultado esperado**:

- No se crea la ventana.
- No se produce un cierre inesperado.
- El diagnóstico refleja que el overlay no pudo mostrarse.

## Escenario 8 — Ciclo de vida y fugas

1. Mostrar y ocultar el overlay cincuenta veces con objetivos alternos.
2. Inspeccionar `OverlayManager`, el `ComposeView` y la memoria.

**Resultado esperado**:

- No quedan ventanas huérfanas.
- La composición se destruye en cada ocultamiento.
- No hay crecimiento sostenido de memoria ni referencias al lifecycle anterior.

## Escenario 9 — Validación manual en dispositivo

1. Conceder accesibilidad y superposición.
2. Crear una regla de dominio para un sitio de prueba.
3. Abrir el sitio en un navegador.
4. Verificar que el overlay cubre la pantalla y que los toques no llegan a la
   app de fondo.
5. Cerrar el overlay y comprobar que la app de fondo vuelve a responder.
6. Revisar el registro de auditoría local.

**Resultado esperado**:

- El overlay aparece en menos de un segundo desde la detección.
- La interacción de fondo queda bloqueada mientras el overlay está visible.
- Home/Recientes se comportan según la limitación documentada del sistema.
- El cierre libera la ventana y permite interactuar de nuevo.

## Referencias

- Modelo de datos: [`data-model.md`](data-model.md)
- Contratos: [`contracts/overlay-coordinator-contracts.md`](contracts/overlay-coordinator-contracts.md)
- Investigación: [`research.md`](research.md)

## Resultados de la implementación (2026-11-05)

Validación automatizada ejecutada:

| Comando | Resultado |
|---|---|
| `./gradlew :core:overlay:testDebugUnitTest` | PASS — 18 pruebas (matcher, coordinador, lifecycle y auditoría) |
| `./gradlew :core:database:testDebugUnitTest` | PASS — 20 pruebas |
| `./gradlew :app:assembleDebug` | PASS — APK generado |
| `./gradlew :app:lintDebug` | PASS — sin errores de lint |

Cobertura automatizada de los escenarios 1–6: coincidencia de dominio,
ausencia de coincidencia, precedencia, deduplicación de auditoría, cierre
manual y cambio de reglas/objetivo.

**Pendiente de validación manual** (requiere dispositivo/emulador): escenarios
7–9, especialmente touch sobre la app de fondo, comportamiento de
Home/Recientes, reapertura tras 50 ciclos y apagado de pantalla. No hay
dispositivo conectado en este entorno.

**Limitaciones conocidas**:

- La extracción de URL/keyword desde accesibilidad es best-effort; si no hay
  target, no se bloquea ni se audita.
- Home/Recientes no se suprimen de forma absoluta sin modo kiosco o device owner.
- El temporizador existente sigue consultándose; su semántica de paquete no se
  extiende a targets de contenido.
- La inmersión del sistema es best-effort y puede variar según el fabricante.
