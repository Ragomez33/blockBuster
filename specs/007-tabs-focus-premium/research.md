# Research: Navegación por Pestañas, Focus Mode y Freemium

**Feature**: `007-tabs-focus-premium`
**Date**: 2026-11-06
**Status**: Complete — no `NEEDS CLARIFICATION` remaining

## Research Tasks

| # | Topic | Resolution |
|---|-------|------------|
| R1 | Navegación por pestañas con estado preservado | Navigation Compose con rutas y `saveState`/`restoreState` |
| R2 | Precedencia y evaluación de listas negras/blancas | Whitelist primero; blacklist convertida en reglas sintéticas |
| R3 | Persistencia de Focus Mode y transición de fases | DataStore con marcas de tiempo absolutas + coordinador idempotente |
| R4 | Clasificación de apps de ocio sin nueva taxonomía | Catálogo curado en `:core:common` |
| R5 | Freemium local y compra simulada | `IsPremiumState` en DataStore; sin proveedor de facturación |
| R6 | Personalización del tiempo de fricción | `frictionWindowMillis` en `FrictionConfig` |
| R7 | Migración de Room al añadir blocklist | Versión 3 con migración explícita y tests |
| R8 | Estrategia de pruebas | Robolectric + coroutines test + quickstart manual |

---

## R1 — Navegación por pestañas

**Decision**: Usar `NavHost` con rutas `tabs/blocking`, `tabs/blocklist` y
`tabs/settings`. `MainTabsScaffold` dibuja `NavigationBar`, el FAB global de
Focus Mode y el contenido. La navegación entre pestañas usa
`launchSingleTop = true`, `restoreState = true` y `saveState = true`.

**Rationale**: Navigation Compose ya está declarado. Permite conservar el
estado de cada pestaña, manejar el botón atrás y añadir la ruta de suscripción
sin introducir dependencias. El FAB en el scaffold garantiza acceso global.

**Alternatives considered**:
- `rememberSaveable` con un índice de pestaña: más simple, pero pierde el
  estado interno de listas y no ofrece un grafo para suscripción/privacidad.
- Una sola pantalla con `when` y composición condicional: no preserva estado
  ni ofrece navegación de retorno consistente.
- Bottom sheets para todo: no replica la navegación por pestañas solicitada.

---

## R2 — Blocklist y precedencia

**Decision**: Nueva entidad Room `BlocklistEntryEntity` con
`(value, targetType, listType)` único. `BlocklistMatcher` comprueba primero la
lista blanca: una coincidencia de app/sitio/keyword exime de cualquier bloqueo.
Si no hay exención, las entradas de lista negra se convierten en
`BlockRule` sintéticas (PACKAGE/DOMAIN/KEYWORD) y se suman a las reglas activas
antes de evaluar.

**Rationale**: La prioridad de whitelist es un requisito explícito y debe
evaluarse antes de reglas, presets y Focus Mode. Reutilizar `BlockRule` y
`DefaultBlockRuleMatcher` evita duplicar la lógica de coincidencia.

**Alternatives considered**:
- Guardar listas en DataStore como conjuntos: no permite metadatos, ids ni
  consultas por tipo; Room es más adecuado.
- Evaluar blacklist con un matcher separado: duplica reglas de dominio/keyword.
- Tratar whitelist como reglas con prioridad negativa: complica el orden y la
  auditoría; un chequeo explícito es más claro.

---

## R3 — Focus Mode y transición de fases

**Decision**: `FocusSession` se persiste en DataStore con fase (`WORK`/`BREAK`),
estado (`IDLE`/`RUNNING`/`PAUSED`), duraciones, `phaseEndsAtEpochMillis` y
`pausedRemainingMillis`. Un `FocusSessionCoordinator` singleton arranca desde
`MonitoringForegroundService` y desde la raíz de la app, hace tick cada segundo
y actualiza las transiciones. `OverlayCoordinator` observa la sesión y aplica
reglas extra solo cuando la fase es `WORK` y el estado es `RUNNING`.

**Rationale**: Las marcas de tiempo absolutas permiten restaurar el tiempo
restante tras un reinicio. El coordinador separa la lógica de tiempo de la UI y
del overlay. El servicio de monitoreo mantiene la sesión viva aunque la app esté
en segundo plano.

**Alternatives considered**:
- `CountDownTimer` solo en la UI: se detiene al salir de la pantalla y no
  mantiene el bloqueo en segundo plano.
- Guardar solo segundos restantes: se desfasa con el sueño del dispositivo y no
  se puede recuperar tras un reinicio.
- Un `WorkManager` periódico: latencia mínima de 15 minutos, inaceptable para
  un Pomodoro.

---

## R4 — Apps de ocio

**Decision**: `LeisureAppCatalog` en `:core:common` contiene una lista curada de
paquetes de ocio (TikTok, Instagram, YouTube, Facebook, Snapchat, Reddit, X,
Netflix, Twitch, etc.). `FocusModeRuleProvider` en `:core:overlay` los convierte
en reglas `PACKAGE` sintéticas cuando Focus está en fase de trabajo.

**Rationale**: `:core:overlay` no puede depender de `:core:database`; un catálogo
en `:core:common` mantiene la dirección de dependencias y permite que la UI
muestre qué apps cubre Focus Mode. Es coherente con la clasificación por presets
existente.

**Alternatives considered**:
- Depender del catálogo de presets en `:core:database`: rompe la modularidad.
- Clasificar apps por categoría de `PackageManager`: requiere permisos y no es
  fiable entre fabricantes.
- Bloquear todas las apps no esenciales: demasiado agresivo y con falsos
  positivos.

---

## R5 — Freemium y compra simulada

**Decision**: `IsPremiumState` y `PremiumRepository` viven en `:core:common`;
la implementación usa DataStore. `PremiumFeature` enumúa las funciones
avanzadas. El estado por defecto es Free; la pantalla de suscripción simula la
compra con un botón local que persiste Premium. Cualquier fallo de lectura se
trata como Free.

**Rationale**: Cumple el requisito sin integrar facturación real ni añadir SDKs.
La simulación permite validar toda la experiencia de gating.

**Alternatives considered**:
- Google Play Billing: dependencia externa y configuración de consola; fuera de
  alcance de esta iteración.
- Estado Premium en memoria: no sobrevive al reinicio y contradice el requisito.
- Estado en Room: DataStore es más adecuado para una preferencia booleana y
  ajustes pequeños.

---

## R6 — Tiempo de fricción configurable

**Decision**: Añadir `frictionWindowMillis` a `FrictionConfig` (por defecto
60 000). `FrictionSession` y `FrictionSessionStore` reciben la ventana;
`OverlayManager` la propaga desde la configuración Premium. Sin Premium o sin
configuración se usa 60 s.

**Rationale**: El tiempo actual está fijado en `FrictionSession.WINDOW_MILLIS`;
la personalización Premium exige parametrizarlo sin romper el flujo estándar ni
la reutilización de sesiones.

**Alternatives considered**:
- Mantener 60 s fijos y mostrar solo una UI "Premium": incumple el requisito de
  personalización.
- Duplicar `FrictionSession` para Premium: código duplicado y riesgo de
  divergencia.

---

## R7 — Migración de Room

**Decision**: Subir `AppDatabase` a versión 3 y añadir
`MIGRATION_2_3` para crear `blocklist_entries` con sus índices. Eliminar el
`fallbackToDestructiveMigration(dropAllTables = true)` para no borrar datos de
usuario al actualizar. Añadir un test de migración.

**Rationale**: La app ya tiene datos de presets, reglas y auditoría; una
migración destructiva perdería configuración. Room 2.8.5 soporta migraciones
explícitas y tests con `MigrationTestHelper`.

**Alternatives considered**:
- Fallback destructivo: simple pero inaceptable para datos de usuario.
- Recrear la base de datos: pierde reglas y logs.

---

## R8 — Testing

**Decision**:
- `:core:database`: tests de DAO/repositorio de blocklist, persistencia Premium y
  Focus, migración 2→3.
- `:core:overlay`: tests de `BlocklistMatcher` (whitelist precedence, app/site/
  keyword), `FocusModeRuleProvider`, `OverlayCoordinator` con Focus activo y
  fricción configurable.
- `:app`: tests de ViewModels con fakes (tabs, Focus, Premium) y verificación
  manual de navegación y paywall con `quickstart.md`.
- Comando: `./gradlew :core:database:testDebugUnitTest
  :core:overlay:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`.

**Rationale**: Mantiene el patrón existente de Robolectric y coroutines test.
Las interacciones con la UI del sistema y la compra simulada se validan manualmente.

**Alternatives considered**:
- Tests instrumentados de Compose: requerirían `androidTest` y dependencias no
  declaradas.
- Solo pruebas manuales: no protege regresiones de matcher, persistencia ni
  precedencia de listas.
