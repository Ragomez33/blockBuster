---
description: "Task list for tabs, Focus Mode and freemium implementation"
---

# Tasks: Navegación por Pestañas, Focus Mode y Freemium

**Input**: Design documents from `/specs/007-tabs-focus-premium/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Incluidos porque `plan.md` y `research.md` definen una estrategia de pruebas explícita. Se recomienda escribirlos antes de la implementación correspondiente y verificar que fallen primero cuando sea viable.

**Organization**: Tareas agrupadas por historia de usuario para permitir implementación y validación independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece la tarea (US1–US4)
- Todas las rutas son relativas a la raíz del repositorio

## Path Conventions

- App Android multi-módulo: código en `app/src/main/kotlin/com/blockbuster/app/`, recursos en `app/src/main/res/`, tests en `app/src/test/kotlin/com/blockbuster/app/`.
- Núcleo: `core/common/`, `core/database/` y `core/overlay/` con sus respectivos `src/main` y `src/test`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Recursos compartidos y verificación del estado base.

- [X] T001 Run `gradlew.bat :core:database:testDebugUnitTest :core:overlay:testDebugUnitTest :app:testDebugUnitTest` from the repository root and confirm the baseline suite is green before modifying `app/build.gradle.kts` or any source file
- [X] T002 Add strings for tabs, Blocking sections, Blocklist, Focus Mode, Settings and Premium to `app/src/main/res/values/strings.xml` (`tab_blocking`, `tab_blocklist`, `tab_settings`, `blocking_section_content`, `blocking_section_advanced`, `blocking_section_customize`, `premium_locked`, `premium_crown`, `premium_subscribe`, `premium_price`, `blocklist_tab_apps`, `blocklist_tab_sites_keywords`, `blocklist_blacklist`, `blocklist_whitelist`, `blocklist_add`, `blocklist_empty`, `blocklist_duplicate`, `blocklist_invalid_app`, `focus_mode_title`, `focus_work`, `focus_break`, `focus_start`, `focus_pause`, `focus_resume`, `focus_stop`, `focus_cycles`, `focus_error_duration`, `settings_account`, `settings_free`, `settings_premium`, `settings_check_updates`, `settings_privacy`, `settings_support`, `settings_privacy_title`, `settings_support_title`, `settings_update_result`, `subscription_title`, `subscription_description`, `subscription_success`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Modelos, contratos, persistencia y migración compartidos por las cuatro historias.

**⚠️ CRITICAL**: Ninguna historia puede completarse sin esta fase.

### Models and contracts

- [X] T003 [P] Create `IsPremiumState` (default `isPremium = false`), `PremiumFeature` (`SHORTS_REELS_RESTRICTIONS`, `FRICTION_TIME_CUSTOMIZATION`, `ACCOUNTABILITY_PARTNER`) and `PremiumSettings` (defaults `strictShortsReels = false`, `frictionWindowMillis = 60_000` between 15_000 and 180_000, `accountabilityPartner = null` trimmed to 120 chars) in `core/common/src/main/kotlin/com/blockbuster/core/common/model/IsPremiumState.kt` and `core/common/src/main/kotlin/com/blockbuster/core/common/model/PremiumFeature.kt`
- [X] T004 [P] Create `BlocklistEntry`, `BlocklistEntryDraft`, `BlocklistType` (`BLACKLIST`, `WHITELIST`) and `BlocklistTargetType` (`APP`, `WEBSITE`, `KEYWORD`) with unique key `(value, targetType, listType)`, non-blank value and normalized website values in `core/common/src/main/kotlin/com/blockbuster/core/common/model/BlocklistEntry.kt`
- [X] T005 [P] Create `FocusSession` with `runState` (`IDLE`, `RUNNING`, `PAUSED`), `phase` (`WORK`, `BREAK`), `workMinutes` 1–120 (default 25), `breakMinutes` 1–60 (default 5), absolute phase timestamps and pause remaining milliseconds in `core/common/src/main/kotlin/com/blockbuster/core/common/model/FocusSession.kt`
- [X] T006 [P] Create the curated leisure package list (TikTok, Instagram, YouTube, Facebook, Snapchat, Reddit, X, Netflix, Twitch and equivalents) in `core/common/src/main/kotlin/com/blockbuster/core/common/model/LeisureAppCatalog.kt`
- [X] T007 [P] Define `BlocklistRepository` (`observeEntries`, `observeEntries(listType, targetType)`, `addEntry`, `deleteEntry`) with `AppResult` writes in `core/common/src/main/kotlin/com/blockbuster/core/common/contract/BlocklistRepository.kt`
- [X] T008 [P] Define `PremiumRepository` (`observePremiumState`, `observeSettings`, `setPremium`, `setStrictShortsReels`, `setFrictionWindowMillis`, `setAccountabilityPartner`) in `core/common/src/main/kotlin/com/blockbuster/core/common/contract/PremiumRepository.kt`
- [X] T009 [P] Define `FocusSessionRepository` (`observeSession`, `start`, `pause`, `resume`, `stop`, `saveDurations`) in `core/common/src/main/kotlin/com/blockbuster/core/common/contract/FocusSessionRepository.kt`

### Persistence

- [X] T010 Create `BlocklistEntryEntity` with columns `id`, `value`, `target_type`, `list_type`, `created_at`, unique index on `(value, target_type, list_type)` and query index on `(list_type, target_type)` in `core/database/src/main/kotlin/com/blockbuster/core/database/entity/BlocklistEntryEntity.kt`, plus enum converters in `core/database/src/main/kotlin/com/blockbuster/core/database/entity/BlocklistTypeConverter.kt`
- [X] T011 Create `BlocklistEntryDao` in `core/database/src/main/kotlin/com/blockbuster/core/database/dao/BlocklistEntryDao.kt` with reactive queries by list/target type, `insert` and `delete` by id
- [X] T012 Add `BlocklistEntryEntity` to `AppDatabase`, bump to version 3 and provide `MIGRATION_2_3` creating `blocklist_entries` and its indices; remove destructive fallback in `core/database/src/main/kotlin/com/blockbuster/core/database/AppDatabase.kt` and `core/database/src/main/kotlin/com/blockbuster/core/database/di/DatabaseModule.kt`
- [X] T013 Implement `BlocklistRepositoryImpl` in `core/database/src/main/kotlin/com/blockbuster/core/database/repository/BlocklistRepositoryImpl.kt` mapping entities/domain, trimming values, normalizing websites, rejecting duplicates with `AppError.Conflict` and missing deletes with `AppError.NotFound`; bind it in `core/database/src/main/kotlin/com/blockbuster/core/database/di/DatabaseModule.kt`
- [X] T014 Implement `PremiumRepositoryImpl` using the existing DataStore with keys for premium state and settings, defaulting to Free and returning controlled failures in `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryImpl.kt`; bind it in `core/database/src/main/kotlin/com/blockbuster/core/database/di/DatabaseModule.kt`
- [X] T015 Implement `FocusSessionRepositoryImpl` using DataStore with absolute timestamps, validation 1–120 work minutes and 1–60 break minutes, and state-aware pause/resume/stop in `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/FocusSessionRepositoryImpl.kt`; bind it in `core/database/src/main/kotlin/com/blockbuster/core/database/di/DatabaseModule.kt`

### Foundational tests

- [X] T016 [P] Add DAO/repository tests for blocklist insert/duplicate/delete/query in `core/database/src/test/kotlin/com/blockbuster/core/database/dao/BlocklistEntryDaoTest.kt` and `core/database/src/test/kotlin/com/blockbuster/core/database/repository/BlocklistRepositoryTest.kt`
- [X] T017 [P] Add DataStore tests for Premium defaults, persistence, fallback to Free and Focus session transitions in `core/database/src/test/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryTest.kt` and `core/database/src/test/kotlin/com/blockbuster/core/database/datastore/FocusSessionRepositoryTest.kt`
- [X] T018 [P] Add Room migration 2→3 test preserving existing rules and creating blocklist tables in `core/database/src/test/kotlin/com/blockbuster/core/database/migration/AppDatabaseMigrationTest.kt`

**Checkpoint**: Foundation ready — models, contracts, persistence and migration exist.

---

## Phase 3: User Story 1 - Navegación por pestañas con Blocking como inicio (Priority: P1) 🎯 MVP

**Goal**: Añadir la barra inferior de tres pestañas, montar Blocking con los presets existentes y conservar el gating del onboarding.

**Independent Test**: Completar el onboarding, comprobar las tres pestañas, alternar entre ellas, activar un preset y verificar que el estado se conserva al volver a Blocking.

### Tests for User Story 1

- [X] T019 [P] [US1] Add `BlockingViewModelTest` in `app/src/test/kotlin/com/blockbuster/app/ui/blocking/BlockingViewModelTest.kt` verifying preset enabled states, active protection count and monitoring status are exposed reactively without changing existing preset behavior

### Implementation for User Story 1

- [X] T020 [US1] Migrate `MainDashboardViewModel` to `BlockingViewModel` in `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingViewModel.kt`, reusing `ProtectionPresetRepository`, `BlockRulesRepository`, `AccessibilityEngine` and `MonitoringForegroundService.isRunning` exactly as today
- [X] T021 [US1] Create `BlockingScreen` in `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt` with sections `Content Blocking`, `Advanced Blocking` and `Customize Blocked screen`; `Content Blocking` MUST keep the three existing preset switches and counters
- [X] T022 [US1] Create reusable `PremiumGate` composable with lock/crown indicator and click callback in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt`
- [X] T023 [US1] Create `MainTabsScaffold` in `app/src/main/kotlin/com/blockbuster/app/ui/tabs/MainTabsScaffold.kt` with Material 3 `NavigationBar` for Blocking, Blocklist and Settings, selected-tab state preservation and a placeholder global Focus FAB
- [X] T024 [US1] Update `BlockBusterNavHost` in `app/src/main/kotlin/com/blockbuster/app/navigation/BlockBusterNavHost.kt` with routes `tabs/blocking`, `tabs/blocklist`, `tabs/settings`, `subscription` and `settings/privacy`; make Blocking the start destination and use `saveState`/`restoreState`/`launchSingleTop`
- [X] T025 [US1] Update `MainActivity` in `app/src/main/kotlin/com/blockbuster/app/MainActivity.kt` so the onboarding gate still renders only `OnboardingScreen` when incomplete and mounts `MainTabsScaffold`/`BlockBusterNavHost` only when complete
- [X] T026 [US1] Delete the obsolete `MainDashboardScreen.kt`/`MainDashboardViewModel.kt` references after migrating to Blocking and keep `app/src/main/kotlin/com/blockbuster/app/ui/dashboard/` free of unused UI
- [X] T027 [US1] Validate quickstart Scenarios 1 and 2 in `specs/007-tabs-focus-premium/quickstart.md`: three tabs, Blocking default, preset state preservation and onboarding re-gating

**Checkpoint**: US1 is the MVP — tabbed navigation with existing protection intact.

---

## Phase 4: User Story 2 - Gestionar listas negras y blancas (Priority: P2)

**Goal**: Añadir la pestaña Blocklist con segmentación Apps/Sitios y palabras, listas negra/blanca, FAB de adición y prioridad de whitelist.

**Independent Test**: Añadir una app a la lista negra, comprobar bloqueo; añadirla a la blanca, comprobar exención; eliminarla y comprobar que vuelve a aplicar la negra.

### Tests for User Story 2

- [X] T028 [P] [US2] Add `BlocklistMatcherTest` in `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/matching/BlocklistMatcherTest.kt` covering whitelist precedence, app/site/keyword matching and blacklist rule conversion
- [X] T029 [P] [US2] Add `BlocklistViewModelTest` in `app/src/test/kotlin/com/blockbuster/app/ui/blocklist/BlocklistViewModelTest.kt` covering add success, duplicate error, delete and filter changes

### Implementation for User Story 2

- [X] T030 [US2] Implement `BlocklistMatcher` in `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/matching/BlocklistMatcher.kt`: whitelist checked first; APP matches exact package, WEBSITE matches host/subdomain ignoring case, KEYWORD matches target or className; blacklist maps to `PACKAGE`/`DOMAIN`/`KEYWORD` rules with stable negative ids
- [X] T031 [US2] Create `BlocklistScreen` in `app/src/main/kotlin/com/blockbuster/app/ui/blocklist/BlocklistScreen.kt` with secondary tabs Apps / Sites & Keywords, Blacklist / Whitelist selector, empty states and reactive list content
- [X] T032 [US2] Create `AddBlocklistEntrySheet` in `app/src/main/kotlin/com/blockbuster/app/ui/blocklist/AddBlocklistEntrySheet.kt` with list type, target type, value validation (`APP` package format, non-blank values) and duplicate handling
- [X] T033 [US2] Create `BlocklistViewModel` in `app/src/main/kotlin/com/blockbuster/app/ui/blocklist/BlocklistViewModel.kt` combining repository flows, selected filters, add/delete actions and user-facing error state
- [X] T034 [US2] Add the Blocklist FAB using `BlocklistScreen`/`AddBlocklistEntrySheet` in `app/src/main/kotlin/com/blockbuster/app/ui/blocklist/BlocklistScreen.kt` and wire it to `BlocklistViewModel`
- [X] T035 [US2] Integrate blocklist into `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`: combine blocklist entries, check whitelist before rules/presets/Focus, append blacklist rules to the effective rule set and audit only non-exempt activations
- [X] T036 [P] [US2] Add `OverlayCoordinatorBlocklistTest` in `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/OverlayCoordinatorBlocklistTest.kt` verifying whitelist exemption and blacklist blocking
- [X] T037 [US2] Validate quickstart Scenario 3 in `specs/007-tabs-focus-premium/quickstart.md`: add/delete entries, duplicate rejection and whitelist precedence

**Checkpoint**: US1 and US2 work together — tabs and customizable lists.

---

## Phase 5: User Story 3 - Focus Mode con temporizador Pomodoro (Priority: P3)

**Goal**: Añadir el FAB global de Focus Mode, sesión Pomodoro persistente y bloqueo extra de apps de ocio durante el trabajo.

**Independent Test**: Abrir Focus desde cualquier pestaña, iniciar una sesión corta, comprobar bloqueo extra en trabajo, relajación en descanso, pausa/stop y restauración tras reinicio.

### Tests for User Story 3

- [X] T038 [P] [US3] Add `FocusModeRuleProviderTest` in `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/focus/FocusModeRuleProviderTest.kt` verifying rules exist only for `WORK` + `RUNNING` and use the curated leisure packages
- [X] T039 [P] [US3] Add `FocusSessionCoordinatorTest` in `app/src/test/kotlin/com/blockbuster/app/focus/FocusSessionCoordinatorTest.kt` covering work→break→work transitions, pause, stop and restart catch-up
- [X] T040 [P] [US3] Add `FocusModeViewModelTest` in `app/src/test/kotlin/com/blockbuster/app/ui/focus/FocusModeViewModelTest.kt` covering duration validation, start/pause/resume/stop and remaining-time exposure

### Implementation for User Story 3

- [X] T041 [US3] Implement `FocusModeRuleProvider` in `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/focus/FocusModeRuleProvider.kt` generating synthetic `PACKAGE` rules with stable negative ids from `LeisureAppCatalog`
- [X] T042 [US3] Implement `FocusSessionCoordinator` in `app/src/main/kotlin/com/blockbuster/app/focus/FocusSessionCoordinator.kt` with an idempotent `start()`, one-second tick, phase transitions and restart catch-up from absolute timestamps
- [X] T043 [US3] Start `FocusSessionCoordinator` from `app/src/main/kotlin/com/blockbuster/app/MainActivity.kt` and `app/src/main/kotlin/com/blockbuster/app/service/MonitoringForegroundService.kt` without duplicating loops
- [X] T044 [US3] Create `FocusModeViewModel` in `app/src/main/kotlin/com/blockbuster/app/ui/focus/FocusModeViewModel.kt` observing `FocusSession`, ticking remaining time and exposing start/pause/resume/stop plus duration editing
- [X] T045 [US3] Create `FocusModeSheet` in `app/src/main/kotlin/com/blockbuster/app/ui/focus/FocusModeSheet.kt` with phase, remaining time, cycles, duration fields and start/pause/resume/stop controls; use it from the global FAB in `app/src/main/kotlin/com/blockbuster/app/ui/tabs/MainTabsScaffold.kt`
- [X] T046 [US3] Integrate `FocusSession` into `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`: append focus rules only for `WORK` + `RUNNING`, keep whitelist precedence and relax extra blocking on pause/break/stop
- [X] T047 [P] [US3] Add `OverlayCoordinatorFocusTest` in `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/OverlayCoordinatorFocusTest.kt` verifying extra blocking during work, relaxation during break/pause and whitelist precedence
- [X] T048 [US3] Validate quickstart Scenarios 4, 5 and 8 in `specs/007-tabs-focus-premium/quickstart.md`: Focus lifecycle, persistence across restart and whitelist-over-Focus precedence

**Checkpoint**: US1–US3 work together — protected, customizable and focused.

---

## Phase 6: User Story 4 - Ajustes, cuenta y desbloqueo Premium (Priority: P4)

**Goal**: Añadir Settings, estado Free/Premium persistente, pantalla de suscripción simulada y desbloqueo de funciones avanzadas.

**Independent Test**: Abrir Settings en Free, comprobar candados; cancelar una compra; completar la compra simulada; comprobar desbloqueo y persistencia tras reiniciar.

### Tests for User Story 4

- [X] T049 [P] [US4] Add `SubscriptionViewModelTest` in `app/src/test/kotlin/com/blockbuster/app/ui/premium/SubscriptionViewModelTest.kt` covering Free→Premium, cancelled purchase and no duplicate purchase
- [X] T050 [P] [US4] Add `SettingsViewModelTest` in `app/src/test/kotlin/com/blockbuster/app/ui/settings/SettingsViewModelTest.kt` covering account state, update result and privacy/support entry points
- [X] T051 [P] [US4] Extend `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/FrictionSessionTest.kt` with configurable `frictionWindowMillis` and default 60-second behavior

### Implementation for User Story 4

- [X] T052 [US4] Create `SubscriptionViewModel` in `app/src/main/kotlin/com/blockbuster/app/ui/premium/SubscriptionViewModel.kt` exposing premium state and a simulated purchase action with error handling and no duplication
- [X] T053 [US4] Create `SubscriptionScreen` in `app/src/main/kotlin/com/blockbuster/app/ui/premium/SubscriptionScreen.kt` with benefit, simulated price, current state, subscribe and back actions
- [X] T054 [US4] Create `SettingsViewModel` in `app/src/main/kotlin/com/blockbuster/app/ui/settings/SettingsViewModel.kt` observing premium state, exposing account label, update check result and navigation actions
- [X] T055 [US4] Create `SettingsScreen` in `app/src/main/kotlin/com/blockbuster/app/ui/settings/SettingsScreen.kt` with account card, update check, privacy policy, support options and subscription entry
- [X] T056 [US4] Create `PrivacyPolicyScreen` in `app/src/main/kotlin/com/blockbuster/app/ui/settings/PrivacyPolicyScreen.kt` with static readable content and back navigation
- [X] T057 [US4] Add Premium gating and advanced controls in `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt` for Shorts/Reels restrictions, friction time customization and accountability partner; Free taps MUST open `SubscriptionScreen`, Premium controls persist through `PremiumRepository`
- [X] T058 [US4] Add `frictionWindowMillis` (default 60_000, range 15_000–180_000) to `FrictionConfig` in `core/common/src/main/kotlin/com/blockbuster/core/common/contract/OverlayController.kt` and propagate it through `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/FrictionSession.kt`, `FrictionSessionStore.kt`, `OverlayManager.kt` and `OverlayComposeHost.kt`
- [X] T059 [US4] Integrate premium settings into `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`: apply strict Shorts/Reels rules only for Premium, use the configured friction window, and fall back to defaults for Free or failed reads
- [X] T060 [US4] Wire `SettingsScreen`, `SubscriptionScreen` and `PrivacyPolicyScreen` into `app/src/main/kotlin/com/blockbuster/app/navigation/BlockBusterNavHost.kt` and ensure returning from subscription restores the previous tab
- [X] T061 [US4] Validate quickstart Scenarios 6 and 7 in `specs/007-tabs-focus-premium/quickstart.md`: Free locks, cancelled purchase, simulated purchase, persistence, update check, privacy and support

**Checkpoint**: All four user stories are functional and independently testable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Limpieza, accesibilidad, regresión y validación final.

- [X] T062 [P] Review the new screens in `app/src/main/kotlin/com/blockbuster/app/ui/tabs/MainTabsScaffold.kt`, `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt`, `app/src/main/kotlin/com/blockbuster/app/ui/blocklist/BlocklistScreen.kt`, `app/src/main/kotlin/com/blockbuster/app/ui/settings/SettingsScreen.kt`, `app/src/main/kotlin/com/blockbuster/app/ui/focus/FocusModeSheet.kt` and `app/src/main/kotlin/com/blockbuster/app/ui/premium/SubscriptionScreen.kt` for large fonts, scrolling, content descriptions and dark Material 3 consistency
- [X] T063 [P] Remove unused dashboard/home/rules references and confirm the tab navigation is the only main entry in `app/src/main/kotlin/com/blockbuster/app/navigation/BlockBusterNavHost.kt`
- [X] T064 Run `gradlew.bat :core:database:testDebugUnitTest :core:overlay:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` and fix any regression
- [ ] T065 Run all Scenarios 1–8 in `specs/007-tabs-focus-premium/quickstart.md` on API 26 and API 34+ and record deviations
- [X] T066 Confirm no new dependencies were added to `app/build.gradle.kts`, `core/*/build.gradle.kts` or `gradle/libs.versions.toml`, and that module dependency directions remain `app -> core`, `overlay -> common`, `database -> common`
- [X] T067 [P] Verify the Premium fallback path and focus/tab state restoration after process death in `specs/007-tabs-focus-premium/quickstart.md` and document any platform-specific limitations

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup (T002) — BLOCKS all user stories.
- **US1 (Phase 3)**: Depends on Foundational; delivers the navigation MVP.
- **US2 (Phase 4)**: Depends on Foundational and the US1 tab scaffold; can run in parallel with US3 after US1.
- **US3 (Phase 5)**: Depends on Foundational and US1; can run in parallel with US2 after US1.
- **US4 (Phase 6)**: Depends on Foundational and US1; can run in parallel with US2/US3 once the tab shell and PremiumGate exist.
- **Polish (Phase 7)**: Depends on all desired stories.

### User Story Dependencies

- **US1 (P1)**: Requires T003–T018; T019–T027 deliver the MVP.
- **US2 (P2)**: Requires T004, T007, T010–T013, T018; integrates with US1 via `BlocklistScreen`.
- **US3 (P3)**: Requires T005, T006, T009, T015, T018; integrates with the global FAB from US1 and the overlay.
- **US4 (P4)**: Requires T003, T008, T014, T018 and `PremiumGate` from US1; independent of US2/US3 except shared navigation.

### Within Each User Story

- Tests are written first where practical and must fail before implementation.
- Models before repositories, repositories before ViewModels, ViewModels before screens.
- Core blocking changes before manual end-to-end validation.

### Parallel Opportunities

- T003–T009 (common models/contracts) touch different files.
- T010–T012 (entity/DAO/database) can be prepared alongside model tasks after T004.
- T016–T018 are separate test files.
- T028, T029 and T036 are independent test files.
- T038–T040 and T047 are separate Focus test files.
- T049–T051 are separate Premium/friction test files.
- T062, T063 and T067 in Polish are independent.

---

## Parallel Example: Foundational

```bash
Task: "Create IsPremiumState and PremiumFeature in core/common"
Task: "Create BlocklistEntry models in core/common"
Task: "Create FocusSession model in core/common"
Task: "Define BlocklistRepository in core/common"
```

## Parallel Example: User Story 2

```bash
Task: "Add BlocklistMatcherTest in core/overlay"
Task: "Add BlocklistViewModelTest in app"
Task: "Add OverlayCoordinatorBlocklistTest in core/overlay"
```

## Parallel Example: User Story 3

```bash
Task: "Add FocusModeRuleProviderTest in core/overlay"
Task: "Add FocusSessionCoordinatorTest in app"
Task: "Add FocusModeViewModelTest in app"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Setup + Foundational.
2. Complete US1 (T019–T027).
3. **STOP and VALIDATE**: quickstart Scenarios 1–2; confirm three tabs, Blocking default, preset state preservation and onboarding gating.
4. Deploy/demo the navigation MVP.

### Incremental Delivery

1. Foundation ready.
2. US1 → tab navigation and Blocking (MVP).
3. US2 → blocklists and whitelist precedence.
4. US3 → Focus Mode and extra blocking.
5. US4 → Settings, Premium and simulated subscription.
6. Polish → regression, accessibility and full quickstart.

### Parallel Team Strategy

1. One developer completes Setup + Foundational.
2. After US1 shell exists:
   - Developer A: US2 Blocklist.
   - Developer B: US3 Focus Mode.
   - Developer C: US4 Settings/Premium.
3. Merge and run the full quickstart and test suite.

---

## Notes

- [P] tasks touch different files and can run without incomplete dependencies.
- [Story] labels map each task to its user story.
- Manual quickstart validation is required for system permissions, overlay behavior and the simulated purchase flow.
- Do not add new dependencies or move blocking logic out of `:core:overlay`.
- Preserve the existing preset switch behavior and onboarding gate in every change.
