# Implementation Plan: Controles Premium Bloqueados y Visibles

**Branch**: `008-premium-locked-controls` | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-premium-locked-controls/spec.md`

## Summary

Refinar la pestaña Blocking para que las funciones Premium sigan siempre
visibles, pero se presenten deshabilitadas cuando la cuenta es Free. El estado
`isPremium` ya se expone mediante `PremiumRepository` y `BlockingViewModel`; el
trabajo es de UI e interacción: switches atenuados con candado, filas Premium
bloqueadas y cualquier intento de interacción redirigido a la suscripción
simulada sin modificar el estado persistido. No se toca Room, presets ni el
motor de bloqueo.

## Technical Context

**Language/Version**: Kotlin 2.4.21, JVM 17

**Primary Dependencies**: Jetpack Compose BOM 2026.09.00, Material 3, Hilt 2.60.1, Lifecycle 2.11.0, Coroutines 1.11.0. Sin dependencias nuevas.

**Storage**: N/A para esta feature. Se reutilizan `IsPremiumState` y `PremiumSettings` ya persistidos en DataStore; no hay cambios de esquema ni de persistencia.

**Testing**: JUnit 4.13.2 + Robolectric 4.17 + coroutines test para el mapeo puro de estado Premium y los ViewModels existentes; validación manual con `quickstart.md` para la interacción táctil y el flujo de suscripción.

**Target Platform**: Android `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`.

**Project Type**: Aplicación Android con UI Compose en `:app`.

**Performance Goals**: El cambio visual entre Free y Premium debe reflejarse en menos de 1 segundo tras la compra, sin reinicio ni recarga manual.

**Constraints**: Solo cambios en `:app`; no modificar Room, presets, accesibilidad ni overlay; mantener el tema oscuro corporativo; Material 3 puro; el estado debe fluir desde los `StateFlow` existentes.

**Scale/Scope**: `BlockingScreen`, `PremiumGate` y nuevos componentes reutilizables de control bloqueado; tests de mapeo de estado y validación manual. Aproximadamente 6–8 archivos.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` es una plantilla sin ratificar. Se aplican las
convenciones del proyecto:

| Gate | Resultado |
|------|-----------|
| Sin dependencias nuevas | PASS — solo Compose y Material 3 ya presentes. |
| Modularidad estricta | PASS — todos los cambios viven en `:app`; consume contratos existentes de `:core:common`. |
| No alterar Room/presets | PASS — no se tocan entidades, DAOs, repositorios de presets ni `BlockRulesRepository`. |
| No alterar motor de bloqueo | PASS — `:core:overlay` y `:core:accessibility` no se modifican. |
| Reactividad existente | PASS — `BlockingViewModel.uiState.isPremium` ya deriva de `PremiumRepository`. |
| Presets gratuitos intactos | PASS — los switches de Content Blocking conservan su lógica actual. |
| UI Compose + Material 3 + tema oscuro | PASS — se reutilizan componentes y paleta existentes. |
| Accesibilidad | PASS — se añaden descripciones de estado para el bloqueo Premium. |
| Testing | PASS — tests de lógica de estado y validación manual del flujo táctil. |

No hay violaciones que justifiquen Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/008-premium-locked-controls/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── premium-locked-ui-contract.md
└── tasks.md             # Fase 2 (/speckit.tasks, no creado aquí)
```

### Source Code (repository root)

```text
app/src/main/kotlin/com/blockbuster/app/
├── ui/blocking/BlockingScreen.kt              # MODIFICAR: usar componentes bloqueados
├── ui/blocking/BlockingViewModel.kt           # REVISAR: ya expone isPremium/settings
└── ui/premium/PremiumGate.kt                  # MODIFICAR/AMPLIAR: badge, estado y semántica

app/src/test/kotlin/com/blockbuster/app/
└── ui/premium/PremiumControlStateTest.kt      # NUEVO: mapeo Free/Premium y acción
```

Se valorará añadir un archivo pequeño de modelo de presentación puro, por
ejemplo `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumControlState.kt`,
para que el comportamiento enabled/locked sea testeable sin Compose UI testing.

**Structure Decision**: La feature es exclusivamente de presentación. Se
mantiene la pantalla y el ViewModel existentes y se extrae un contrato de
componente reutilizable (`PremiumGate`, `PremiumSwitchRow`, `PremiumActionRow`)
para que las tres opciones Premium compartan el mismo tratamiento visual y de
interacción. No se crea ningún módulo nuevo.

## Design Decisions

1. **Fuente de verdad**: `BlockingViewModel.uiState.isPremium` sigue siendo la
   única fuente para habilitar/deshabilitar. No se duplica estado local ni se
   lee DataStore desde la UI.
2. **Switches Premium**: se encapsulan en `PremiumSwitchRow` con:
   - `Switch(checked, enabled = isPremium, onCheckedChange = ...)`.
   - `Modifier.alpha(if (isPremium) 1f else DISABLED_ALPHA)` sobre la fila.
   - Colores deshabilitados de `SwitchDefaults`.
   - `PremiumBadge` visible solo en Free.
   - Fila clicable cuando Free; la acción abre la suscripción y NO invoca el
     cambio de estado.
3. **Filas de acción Premium** (fricción y partner): `PremiumActionRow` muestra
   siempre título, descripción y valor actual; en Free aplica alpha y badge y
   redirige cualquier clic a la suscripción; en Premium ejecuta el callback del
   editor.
4. **Intercepción táctil**: la fila completa es el área interactiva. El switch
   deshabilitado no consume el clic; si en pruebas se detecta consumo, se
   añade una capa transparente de intercepción sobre el control. El estado
   persistido nunca se modifica desde el callback de UI en Free.
5. **Semántica**: `Modifier.semantics` expone `stateDescription` ("Bloqueado,
   requiere Premium" / "Desbloqueado") y el badge tiene descripción accesible.
6. **Navegación**: se reutiliza `onOpenSubscription` de `BlockingScreen`; no se
   añade una nueva ruta. Los taps repetidos no se traducen en pilas duplicadas
   porque la navegación existente usa una sola pantalla de suscripción.
7. **Sin cambios de persistencia**: los valores Premium guardados se conservan
   al volver a Free; solo se impide su edición.
8. **Sin cambios en presets gratuitos**: `PresetSwitchCard` y sus callbacks
   permanecen intactos.

## Phase 0: Research

Ver [research.md](./research.md). No quedan `NEEDS CLARIFICATION`.

## Phase 1: Design & Contracts

- Modelo de estado de presentación: [data-model.md](./data-model.md)
- Contrato de componentes/UI: [contracts/premium-locked-ui-contract.md](./contracts/premium-locked-ui-contract.md)
- Guía de validación: [quickstart.md](./quickstart.md)

## Post-Design Constitution Check

Se mantienen los gates en PASS: la feature es UI-only, no añade dependencias, no
modifica persistencia ni bloqueo, conserva los presets gratuitos y usa el flujo
reactivo existente. No hay complejidad adicional que justificar.

## Complexity Tracking

No aplica: no existen violaciones de la constitución ni de las convenciones del
proyecto.
