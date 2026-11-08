# Research: Controles Premium Bloqueados y Visibles

**Feature**: `008-premium-locked-controls`
**Date**: 2026-11-06
**Status**: Complete — no `NEEDS CLARIFICATION` remaining

## Research Tasks

| # | Topic | Resolution |
|---|-------|------------|
| R1 | Cómo deshabilitar visualmente sin ocultar | Fila visible con alpha reducido, colores disabled y badge |
| R2 | Cómo interceptar el tap en un switch deshabilitado | Fila contenedora clicable; el switch disabled no consume el gesto |
| R3 | Cómo evitar cambios de estado desde Free | El callback de cambio solo se pasa cuando `isPremium == true` |
| R4 | Cómo reflejar el desbloqueo tras la compra | `StateFlow` existente de `PremiumRepository` → `BlockingViewModel` → Compose |
| R5 | Cómo mantener accesibilidad | `stateDescription` y descripción del badge |
| R6 | Cómo probar sin dependencias nuevas | Modelo de presentación puro testeable + validación manual |

---

## R1 — Tratamiento visual del estado bloqueado

**Decision**: Mantener la fila y el control visibles. En Free, aplicar
`Modifier.alpha(0.55f)` a la fila, usar `SwitchDefaults.colors` con colores
deshabilitados y mostrar `PremiumBadge` (candado/corona). El texto y el valor
actual siguen legibles.

**Rationale**: Cumple el requisito de mostrar el valor del producto y comunicar
el estado sin ocultar la función. El alpha y el tintado grisáceo son patrones
Material 3 reconocibles.

**Alternatives considered**:
- Ocultar las opciones: rechazado por el requisito explícito.
- Solo mostrar el candado sin atenuar: menos claro sobre la imposibilidad de
  interacción.
- Deshabilitar toda la tarjeta sin contenido visible: reduce descubribilidad.

---

## R2 — Intercepción del tap

**Decision**: La fila completa (`Card`/`Row`) es clicable cuando Free. El
`Switch` usa `enabled = false` y `onCheckedChange = null`. Los toques sobre el
switch deshabilitado no consumen el gesto y llegan al contenedor. Si una versión
de Compose los consumiera, se superpone una capa transparente de intercepción
sobre la zona del switch.

**Rationale**: Es el patrón más simple y accesible: toda la superficie bloqueada
abre la suscripción, sin depender de que el usuario acierte exactamente en el
switch.

**Alternatives considered**:
- Interceptar solo el switch: deja la fila sin respuesta y es menos usable.
- Usar `pointerInput` global: más complejo y difícil de testear.
- Un botón "Upgrade" separado: no cumple "al intentar interactuar sobre él".

---

## R3 — Evitar cambios de estado desde Free

**Decision**: La UI no pasa el callback de cambio al control cuando
`isPremium == false`; el único callback activo en esa rama es
`onOpenSubscription`. El ViewModel no recibe ninguna llamada de escritura desde
la interacción bloqueada.

**Rationale**: La protección es doble: la UI no ofrece la acción de cambio y el
callbacks de persistencia no se invoca. Esto garantiza cero persistencia desde
Free.

**Alternatives considered**:
- Validar en el ViewModel: buena defensa en profundidad, pero el requisito es
  de UI; además el ViewModel ya devuelve error de permiso si se llama. Se puede
  mantener como red de seguridad sin cambios.
- Deshabilitar solo visualmente: insuficiente si el callback sigue activo.

---

## R4 — Reactividad tras la suscripción

**Decision**: Reutilizar `BlockingViewModel.uiState.isPremium`, que ya combina
`PremiumRepository.observePremiumState()` con el resto del estado. Al completar
la compra simulada, la pantalla se recompone y los controles pasan a habilitados.

**Rationale**: No requiere estado local ni recarga manual. La compra ya persiste
`IsPremiumState` y la navegación de regreso mantiene la pestaña.

**Alternatives considered**:
- `remember` local con el estado de compra: se desincroniza.
- Reconsultar al volver: innecesario y menos reactivo.

---

## R5 — Accesibilidad

**Decision**: Añadir `stateDescription` a la fila/semántica del control:
"Bloqueado, requiere Premium" cuando Free y "Desbloqueado" cuando Premium. El
badge incluye descripción textual y no depende solo del color/alpha.

**Rationale**: El estado no debe comunicarse únicamente con opacidad. Los
lectores de pantalla necesitan una descripción explícita.

**Alternatives considered**:
- Solo candado visual: insuficiente para TalkBack.
- `contentDescription` en el switch deshabilitado: puede ser ignorado; mejor en
  la fila contenedora.

---

## R6 — Estrategia de pruebas

**Decision**:
- Extraer `PremiumControlState` puro (`isPremium`, `isLocked`, `enabled`,
  `alpha`, `action`) y tests JUnit para Free/Premium.
- Tests de `BlockingViewModel` ya existentes para comprobar que `isPremium`
  refleja la suscripción.
- Validación manual con `quickstart.md` para taps sobre switch/fila, navegación
  a suscripción, cancelación y desbloqueo.
- No añadir dependencias de Compose UI testing.

**Rationale**: Evita nuevas dependencias y cubre la lógica crítica de decisión.
La interacción táctil se valida en dispositivo/emulador.

**Alternatives considered**:
- Añadir `ui-test-junit4`: dependencia no declarada y no necesaria.
- Solo manual: no protege la regresión de habilitación/bloqueo.
