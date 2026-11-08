# Implementation Plan: Navegación por Pestañas, Focus Mode y Freemium

**Branch**: `007-tabs-focus-premium` | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-tabs-focus-premium/spec.md`

## Summary

Reorganizar la aplicación en una navegación inferior de tres pestañas
(Blocking, Blocklist, Settings) manteniendo intacta la lógica de presets
maestros. Añadir listas negras/blancas personalizadas con prioridad de lista
blanca, un Focus Mode Pomodoro global con sesión persistente y bloqueo extra
sobre apps de ocio, y un modelo freemium local con estado `IsPremiumState`,
opciones avanzadas bloqueadas y una pantalla de suscripción simulada. Todo en
Compose + Material 3 con el tema oscuro existente y sin dependencias nuevas.

## Technical Context

**Language/Version**: Kotlin 2.4.21, JVM 17, Android Gradle Plugin 9.5.0

**Primary Dependencies**: Jetpack Compose BOM 2026.09.00, Material 3, Navigation Compose 2.10.2, Lifecycle 2.11.0, Hilt 2.60.1, Room 2.8.5, DataStore Preferences 1.3.0, Coroutines 1.11.0. Sin dependencias nuevas.

**Storage**: Room para entradas de blocklist; DataStore Preferences para estado Premium, ajustes avanzados y sesión de Focus Mode. Se mantiene el almacenamiento actual de presets y reglas.

**Testing**: JUnit 4.13.2 + Robolectric 4.17 + `kotlinx-coroutines-test` en tests unitarios de `:app`, `:core:database` y `:core:overlay`; validación manual con `quickstart.md`.

**Target Platform**: Android `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`.

**Project Type**: Aplicación Android multi-módulo (`:app`, `:core:common`, `:core:database`, `:core:accessibility`, `:core:overlay`).

**Performance Goals**: Cambio de pestaña < 1 s; adición/eliminación de blocklist reflejada < 1 s; inicio de Focus Mode < 2 s; bloqueo extra aplicado < 1 s. Sin polling de base de datos.

**Constraints**: Conservar los switches maestros y el onboarding; tema oscuro corporativo; sin pasarela de pago real; sin nuevas dependencias; la lista blanca tiene prioridad sobre listas negras y Focus Mode; coordinación reactiva con StateFlow/Coroutines.

**Scale/Scope**: 1 scaffold de pestañas, 3 pantallas principales, 1 sheet de Focus Mode, 1 pantalla de suscripción, 3 repositorios nuevos, 1 entidad Room nueva, cambios en el coordinador de overlay y en la fricción, más tests. Aproximadamente 20–25 archivos.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` sigue siendo la plantilla sin ratificar. No hay
principios vinculantes; se aplican las convenciones del proyecto:

| Gate | Resultado |
|------|-----------|
| Sin dependencias externas nuevas | PASS — se usa Compose, Navigation, Hilt, Room y DataStore existentes. |
| Modularidad estricta | PASS — contratos/modelos en `:core:common`, persistencia en `:core:database`, evaluación/bloqueo en `:core:overlay`, UI en `:app`. |
| Preservar switches maestros | PASS — se reutiliza el ViewModel/repositorio de presets; solo se reorganiza la UI. |
| Preservar onboarding | PASS — el gating de `MainActivity` no cambia; las pestañas solo se montan con `isComplete == true`. |
| UI Compose + Material 3 + tema oscuro | PASS — `BlockBusterTheme` ya fuerza el tema oscuro corporativo. |
| Reactividad | PASS — Room/DataStore -> Flow -> ViewModel `StateFlow` -> Compose. |
| Estabilidad | PASS — operaciones de escritura con `runCatching`/`AppResult`, validación de duplicados y fallback a Free si el estado Premium no se puede leer. |
| Testing | PASS — Robolectric y coroutines test ya configurados. |
| Simplicidad | PASS — pago simulado local, sin backend ni facturación real. |

No hay violaciones que justifiquen Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/007-tabs-focus-premium/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── tabs-focus-premium-contracts.md
└── tasks.md             # Fase 2 (/speckit.tasks, no creado aquí)
```

### Source Code (repository root)

```text
app/src/main/kotlin/com/blockbuster/app/
├── MainActivity.kt                              # MODIFICAR: montar MainTabsScaffold
├── navigation/BlockBusterNavHost.kt             # MODIFICAR: grafo principal + suscripción
├── focus/FocusSessionCoordinator.kt             # NUEVO: ticker y transición de fases
├── service/MonitoringForegroundService.kt       # MODIFICAR: arrancar coordinador de Focus
├── ui/tabs/MainTabsScaffold.kt                  # NUEVO: NavigationBar + FAB global
├── ui/blocking/BlockingScreen.kt                # NUEVO: pestaña Blocking por secciones
├── ui/blocking/BlockingViewModel.kt             # NUEVO/REUTILIZAR lógica de presets
├── ui/blocklist/BlocklistScreen.kt              # NUEVO: listas negras/blancas
├── ui/blocklist/BlocklistViewModel.kt           # NUEVO
├── ui/settings/SettingsScreen.kt                # NUEVO
├── ui/settings/SettingsViewModel.kt             # NUEVO
├── ui/settings/PrivacyPolicyScreen.kt           # NUEVO
├── ui/focus/FocusModeSheet.kt                   # NUEVO: Pomodoro
├── ui/focus/FocusModeViewModel.kt               # NUEVO
├── ui/premium/PremiumGate.kt                    # NUEVO: candado/corona reutilizable
├── ui/premium/SubscriptionScreen.kt             # NUEVO: pasarela simulada
├── ui/premium/SubscriptionViewModel.kt          # NUEVO
├── ui/dashboard/MainDashboardScreen.kt          # ELIMINAR/REEMPLAZAR por BlockingScreen
└── ui/dashboard/MainDashboardViewModel.kt       # MIGRAR a BlockingViewModel

core/common/src/main/kotlin/com/blockbuster/core/common/
├── model/IsPremiumState.kt                      # NUEVO
├── model/BlocklistEntry.kt                      # NUEVO
├── model/FocusSession.kt                        # NUEVO
├── model/PremiumFeature.kt                      # NUEVO
├── model/LeisureAppCatalog.kt                   # NUEVO
├── contract/BlocklistRepository.kt              # NUEVO
├── contract/PremiumRepository.kt                # NUEVO
└── contract/FocusSessionRepository.kt           # NUEVO

core/database/src/main/kotlin/com/blockbuster/core/database/
├── entity/BlocklistEntryEntity.kt               # NUEVO
├── entity/BlocklistTypeConverter.kt             # NUEVO
├── dao/BlocklistEntryDao.kt                     # NUEVO
├── repository/BlocklistRepositoryImpl.kt        # NUEVO
├── datastore/PremiumRepositoryImpl.kt           # NUEVO
├── datastore/FocusSessionRepositoryImpl.kt      # NUEVO
├── datastore/BlockBusterPreferences.kt          # MODIFICAR: claves nuevas
├── AppDatabase.kt                               # MODIFICAR: versión 3 + entidad
└── di/DatabaseModule.kt                         # MODIFICAR: bindings

core/overlay/src/main/kotlin/com/blockbuster/core/overlay/
├── matching/BlocklistMatcher.kt                 # NUEVO: whitelist/blacklist
├── focus/FocusModeRuleProvider.kt               # NUEVO: reglas extra de ocio
├── OverlayCoordinator.kt                        # MODIFICAR: blocklist + focus + premium
├── FrictionSession.kt                           # MODIFICAR: ventana configurable
├── FrictionSessionStore.kt                      # MODIFICAR: ventana configurable
├── OverlayManager.kt                            # MODIFICAR: usar config de fricción
└── OverlayComposeHost.kt                        # MODIFICAR si cambia la firma de sesión

app/src/test/kotlin/com/blockbuster/app/...        # NUEVOS tests de ViewModels/UI contracts
core/database/src/test/kotlin/...                 # NUEVOS tests de blocklist y repositorios
core/overlay/src/test/kotlin/...                  # NUEVOS tests de matcher/focus/fricción
```

**Structure Decision**: Se mantiene la arquitectura modular existente. Los
modelos y contratos nuevos viven en `:core:common`; la persistencia en
`:core:database`; la evaluación de blocklist y Focus Mode que afecta al bloqueo
en `:core:overlay`; y toda la navegación/UI en `:app`. Esto evita dependencias
invertidas (`:core:overlay` no depende de `:core:database`) y conserva la lógica
de presets en `BlockingViewModel`.

## Design Decisions

1. **Navegación**: `BlockBusterNavHost` pasa a un grafo con rutas `tabs/blocking`,
   `tabs/blocklist`, `tabs/settings` y `subscription`. `MainTabsScaffold` usa
   `NavigationBar` y `navController.navigate(route) { popUpTo(startDestination)
   { saveState = true }; launchSingleTop = true; restoreState = true }`.
   El FAB de Focus Mode se dibuja en el `Scaffold` de pestañas para estar
   disponible en las tres pestañas.
2. **Blocking**: se reutiliza `MainDashboardViewModel` (renombrado a
   `BlockingViewModel`) y su combinación de presets, reglas activas, conexión y
   monitoreo. La UI se divide en `Content Blocking` (presets gratuitos),
   `Advanced Blocking` (features Premium) y `Customize Blocked screen`
   (personalización de fricción, Premium).
3. **Blocklist**: nueva entidad Room con `value`, `targetType`
   (`APP`, `WEBSITE`, `KEYWORD`), `listType` (`BLACKLIST`, `WHITELIST`) y
   `createdAt`, con índice único por `(value, targetType, listType)`. La UI usa
   pestañas secundarias Apps / Sitios y palabras y un selector negro/blanco.
4. **Precedencia**: antes de evaluar reglas o Focus Mode, el coordinador consulta
   las entradas whitelist. Si hay coincidencia, no se bloquea. Las entradas
   blacklist se transforman en reglas sintéticas (`PACKAGE`, `DOMAIN`,
   `KEYWORD`) y se añaden a las reglas activas.
5. **Focus Mode**: `FocusSession` se persiste en DataStore con fase, estado,
   duraciones y marcas de tiempo absolutas. Un `FocusSessionCoordinator`
   idempotente hace el tick y cambia de fase; se arranca desde
   `MonitoringForegroundService` y desde la raíz de la app. `OverlayCoordinator`
   observa la sesión y añade reglas de apps de ocio solo en fase `WORK` y estado
   `RUNNING`; pausa/descanso/stop relajan el bloqueo extra. La lista blanca
   siempre tiene prioridad.
6. **Premium**: `IsPremiumState` + `PremiumRepository` respaldados por DataStore.
   `PremiumFeature` enumúa las opciones avanzadas. `PremiumGate` muestra candado
   y abre `SubscriptionScreen`; la compra simulada es un botón local que
   actualiza el estado a Premium. Si la lectura falla, el estado efectivo es
   Free. Los ajustes avanzados (Shorts/Reels estrictos, ventana de fricción,
   partner) se persisten localmente y solo se aplican con Premium.
7. **Fricción configurable**: `FrictionConfig` incorpora `frictionWindowMillis`
   con valor por defecto 60 000; `FrictionSessionStore` y `FrictionSession`
   reciben la ventana y `OverlayManager` la propaga. Sin Premium se usa el valor
   por defecto.
8. **Datos de apps de ocio**: `LeisureAppCatalog` en `:core:common` contiene
   identificadores de paquetes conocidos (TikTok, Instagram, YouTube, Facebook,
   Snapchat, Reddit, X, Netflix, Twitch, etc.). `FocusModeRuleProvider` los
   convierte en reglas `PACKAGE` con IDs negativos estables.
9. **Tema**: se conserva `BlockBusterTheme` en modo oscuro; las nuevas pantallas
   usan `MaterialTheme.colorScheme` y no introducen colores fuera del tema salvo
   acentos semánticos de Premium.

## Phase 0: Research

Ver [research.md](./research.md). No quedan `NEEDS CLARIFICATION`.

## Phase 1: Design & Contracts

- Modelo de datos: [data-model.md](./data-model.md)
- Contratos de UI y repositorios: [contracts/tabs-focus-premium-contracts.md](./contracts/tabs-focus-premium-contracts.md)
- Guía de validación: [quickstart.md](./quickstart.md)

## Post-Design Constitution Check

Se mantienen los gates en PASS: no se añaden dependencias, no se invierte la
dirección de dependencias entre módulos, la lógica de presets se reutiliza, el
onboarding sigue gateando la app y todas las escrituras nuevas tienen manejo de
error. No hay complejidad que justificar.

## Complexity Tracking

No aplica: no existen violaciones de la constitución ni de las convenciones del
proyecto.
