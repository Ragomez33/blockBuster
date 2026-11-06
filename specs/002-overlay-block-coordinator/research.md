# Research: Coordinador de bloqueos y activación del Overlay

**Feature**: `002-overlay-block-coordinator`  
**Date**: 2026-11-05  
**Status**: Complete — no unresolved NEEDS CLARIFICATION

## Contexto técnico

El proyecto ya tiene:
- Un bus reactivo `BlockEventBus` con `SharedFlow` y `currentForegroundPackage`.
- `OverlayCoordinator` que combina paquete en primer plano, reglas activas y temporizador.
- `OverlayManager` con `WindowManager` y `TYPE_APPLICATION_OVERLAY`.
- `OverlayComposeHost` con owners de lifecycle, saved state y ViewModel.
- `BlockRulesRepository.recordBlock(...)` para auditoría en Room.

La nueva feature exige un coordinador que evalúe objetivos (URL o keyword),
active el overlay con bloqueo de interacción, audite una sola vez por activación
y garantice un ciclo de vida seguro de la vista Compose. El evento actual de
accesibilidad no transporta una URL ni un keyword, por lo que se requiere una
extensión mínima del contrato de detección.

## Decisiones

### D1. Extender la detección con un objetivo opcional

**Decision**: Agregar `target: String?` a `DetectionEvent.ScreenDetected` y
exponer `currentDetection: StateFlow<DetectionSignal?>` en `BlockEventBus`.
Agregar un `TargetExtractor` en `:core:accessibility` que obtiene el objetivo de
`AccessibilityEvent.text`, `contentDescription` y, cuando sea viable, del nodo
raíz activo.

**Rationale**: El coordinador necesita un objetivo distinto del paquete para
evaluar reglas de dominio o keyword. Mantener el paquete permite conservar la
trazabilidad y el fallback de diagnóstico.

**Alternatives considered**:
- Usar solo `packageName` como objetivo: rechazado porque no representa una URL
  ni una keyword.
- Inyectar URLs desde un navegador o WebView: rechazado por acoplar la feature a
  una app concreta y por no cubrir contenido nativo.
- Bloquear con base en el título de la ventana: rechazado por ser demasiado
  ambiguo para dominios.

**Risk**: La extracción de URL desde accesibilidad no es uniforme entre apps.
Si no hay objetivo, el coordinador no bloquea y no audita; se documenta como
limitación.

### D2. Coincidencia determinista de reglas

**Decision**: Implementar un `BlockRuleMatcher` puro:
- `DOMAIN`: normalizar el objetivo (quitar esquema, `www.`, ruta, query y
  fragmento), comparar host igual o subdominio del patrón, sin distinguir
  mayúsculas.
- `KEYWORD_SHORTS` / `KEYWORD_REELS`: comparar por contención sin distinguir
  mayúsculas.
- Si varias reglas coinciden, elegir la de menor `id` y auditar solo esa.
- Un objetivo nulo o vacío nunca coincide.

**Rationale**: Centraliza la semántica en una función testeable y evita
decisiones dispersas entre coordinador, UI y repositorio.

**Alternatives considered**:
- Coincidencia por regex definida por el usuario: fuera de alcance y riesgosa.
- Coincidencia por igualdad exacta de texto: demasiado restrictiva para URLs con
  rutas y keywords.
- Bloquear con todas las reglas coincidentes: generaría múltiples auditorías y
  mensajes ambiguos.

### D3. Evaluación reactiva con reglas cacheadas

**Decision**: El coordinador combina `currentDetection` con
`repository.observeActiveRules()` y evalúa con el matcher en memoria. No consulta
la base de datos por cada evento individual.

**Rationale**: `observeActiveRules()` ya es un flujo Room ejecutado fuera del
hilo principal. Cachear la lista evita consultas repetidas por ráfagas de
eventos y reevalúa automáticamente cuando cambian las reglas.

**Alternatives considered**:
- Llamar `getActiveRulesByType()` por cada evento: produce consultas redundantes
  y mayor latencia.
- Consultar desde el hilo principal: viola la restricción explícita.

### D4. Una auditoría por activación, no por evento

**Decision**: Auditar con `repository.recordBlock(...)` solo en la transición de
"sin bloqueo" a "bloqueo" para una combinación regla + objetivo. Mantener una
clave de activación y no volver a auditar mientras el overlay permanezca visible
para esa clave. Al cerrar el overlay, la activación termina y una nueva
detección puede generar otra auditoría.

**Rationale**: La accesibilidad emite eventos repetidos; auditar cada uno
inflaría las estadísticas y contradice el criterio de una entrada por activación.

**Alternatives considered**:
- Auditar cada evento: rechazado por duplicados.
- Auditar solo al cerrar el overlay: pierde bloqueos que nunca se cierran y
  complica la asociación temporal.

### D5. Detección de cierre del overlay mediante estado observable

**Decision**: El coordinador observa `OverlayController.isOverlayVisible` para
detectar cuándo la persona usuaria cierra el overlay. Si el objetivo sigue
coincidiendo, se marca la activación como descartada hasta que cambie la
detección (nuevo timestamp/objetivo) o cambien las reglas.

**Rationale**: Evita que un cierre manual se revierta inmediatamente por el
siguiente evento de accesibilidad y no requiere exponer callbacks de UI en el
contrato común.

**Alternatives considered**:
- Callback `onDismiss` en `FrictionConfig`: acopla el contrato a una función y
  complica el testing del coordinador.
- Re-mostrar inmediatamente: contradice la acción explícita del usuario.

### D6. Overlay a pantalla completa y bloqueo de touch

**Decision**: Mantener `MATCH_PARENT`, `TYPE_APPLICATION_OVERLAY`, gravedad
`TOP|START`, `FLAG_LAYOUT_IN_SCREEN` y `FLAG_LAYOUT_NO_LIMITS`. No establecer
`FLAG_NOT_TOUCH_MODAL` mientras el overlay esté bloqueando, de modo que la
ventana sea táctil y consuma los eventos dentro de su área completa. La `Surface`
de Compose ocupa toda la pantalla.

**Rationale**: El overlay debe impedir la interacción con la app de fondo. Al
cubrir todo y ser táctil, no queda área exterior por la que pasar eventos.

**Alternatives considered**:
- `FLAG_NOT_TOUCH_MODAL`: permite que toques fuera de la ventana lleguen al
  fondo; con pantalla completa no aporta beneficio y abre una vía de escape.
- `FLAG_NOT_FOCUSABLE`: reduce la capacidad de capturar eventos de teclado.

### D7. Inicio/recientes: mejor esfuerzo, no promesa absoluta

**Decision**: Aplicar flags de inmersión temporal y mantener el overlay por
encima; documentar que la supresión total de Home/Recientes requiere modo kiosco,
device owner o administración del dispositivo, fuera del alcance de un overlay
normal.

**Rationale**: Android no permite que una app común intercepte permanentemente
los botones de navegación del sistema. Prometerlo produciría una falsa sensación
de seguridad.

**Alternatives considered**:
- Bloquear Home/Recientes con `AccessibilityService`: el sistema no lo garantiza
  y puede violar políticas.
- Requerir device admin en esta feature: cambia el alcance y la experiencia de
  permisos.

### D8. Ciclo de vida del ComposeView sin fugas

**Decision**: Mantener un `OverlayComposeHost` con `LifecycleOwner`,
`SavedStateRegistryOwner` y `ViewModelStoreOwner` propios. Al ocultar:
`CREATED → STARTED → RESUMED` durante la vida visible, luego
`RESUMED → DESTROYED`, `disposeComposition()`, `removeViewImmediate(...)` y
limpieza de referencias (`host = null`, `currentConfig = null`). No reutilizar
owners ni la composición después de destruirlos.

**Rationale**: Compose exige owners válidos y la composición debe liberarse para
evitar leaks y recomposiciones sobre una vista desmontada.

**Alternatives considered**:
- Reutilizar el mismo `ComposeView` para cada bloqueo: riesgo de estado obsoleto
  y de retener el contexto/actividad.
- No llamar `disposeComposition`: deja observadores y corrutinas de composición
  activos.

### D9. Sin cambios de esquema Room

**Decision**: Reutilizar `BlockRuleEntity`, `BlockLogEntity` y
`BlockRulesRepository.recordBlock(...)`. No hay migración ni tabla nueva.

**Rationale**: La feature solo añade comportamiento de coordinación y auditoría;
el esquema existente ya cubre las necesidades.

### D10. Pruebas del coordinador con dobles y prueba de vida del overlay

**Decision**:
- Pruebas unitarias del matcher para dominios, subdominios, keywords, objetivo
  nulo y precedencia.
- Pruebas del coordinador con `BlockEventBus`, repositorio y `OverlayController`
  falsos: coincidencia, no coincidencia, auditoría única, cambio de reglas,
  cierre y fallo de auditoría.
- Prueba de humo con Robolectric para `OverlayComposeHost`/`OverlayManager` si el
  entorno lo permite; validación manual de interacción y sistema en dispositivo.

**Rationale**: La lógica del coordinador es determinista y testeable sin
depender de un emulador. El comportamiento de WindowManager y botones del
sistema requiere validación manual.
