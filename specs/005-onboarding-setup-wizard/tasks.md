---
description: "Task list for Onboarding Setup Wizard implementation"
---

# Tasks: Asistente de Configuración Inicial (Onboarding Setup Wizard)

**Input**: Design documents from `/specs/005-onboarding-setup-wizard/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Incluidos según la estrategia de verificación definida en `plan.md` y `research.md` (Robolectric + JUnit, sin dependencias nuevas). No se exige TDD estricto, pero cada test debe ejecutarse y fallar antes de la implementación que valida cuando sea posible.

**Organization**: Tareas agrupadas por historia de usuario para permitir implementación y validación independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece la tarea (US1, US2, US3)
- Todas las rutas son relativas a la raíz del repositorio

## Path Conventions

- Aplicación Android multi-módulo: código en `app/src/main/kotlin/com/blockbuster/app/`, recursos en `app/src/main/res/`, tests en `app/src/test/kotlin/com/blockbuster/app/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Recursos compartidos y verificación del estado base del proyecto.

- [X] T001 Add onboarding string resources (`onboarding_title`, `onboarding_subtitle`, `onboarding_step_overlay_title/_description/_action`, `onboarding_step_accessibility_title/_description/_action`, `onboarding_step_admin_title/_description/_action`, `onboarding_status_active` = `"✔ Activo"`, `onboarding_status_pending` = `"Pendiente"`, `onboarding_error_settings_unavailable`) to `app/src/main/res/values/strings.xml`
- [X] T002 Run `gradlew.bat :app:testDebugUnitTest` (or `./gradlew :app:testDebugUnitTest`) from the repository root and confirm the existing suite is green before modifying `app/build.gradle.kts` or any source file

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Modelo de estado, comprobaciones del sistema, repositorio, intents y ViewModel compartidos por las tres historias.

**⚠️ CRITICAL**: Ninguna historia puede completarse sin esta fase.

- [X] T003 [P] Create `OnboardingState` data class with fields `overlayGranted: Boolean`, `accessibilityEnabled: Boolean`, `deviceAdminActive: Boolean`, and derived `isComplete = overlayGranted && accessibilityEnabled && deviceAdminActive` in `app/src/main/kotlin/com/blockbuster/app/onboarding/OnboardingState.kt`
- [X] T004 [P] Expose public `isOverlayGranted()`, `isAccessibilityEnabled()` and `isDeviceAdminActive()` checks from `app/src/main/kotlin/com/blockbuster/app/protection/ProtectionHealthChecker.kt`; accessibility MUST match the full flattened `ComponentName` of `com.blockbuster.core.accessibility.BlockBusterAccessibilityService` against `AccessibilityManager.getEnabledAccessibilityServiceList(...)` and `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, not just the package name
- [X] T005 [P] Create `OnboardingIntents` helper in `app/src/main/kotlin/com/blockbuster/app/onboarding/OnboardingIntents.kt` that builds the primary intents: overlay with `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` + `Uri.parse("package:${context.packageName}")`, accessibility with `Settings.ACTION_ACCESSIBILITY_SETTINGS`, and device admin with `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN` + `EXTRA_DEVICE_ADMIN = ComponentName(context, BlockerDeviceAdminReceiver::class.java)`; validate `resolveActivity(context.packageManager) != null` and launch inside `runCatching`
- [X] T006 Create `OnboardingRepository` (`@Singleton`, `@Inject constructor(@ApplicationContext context: Context)`) in `app/src/main/kotlin/com/blockbuster/app/onboarding/OnboardingRepository.kt` exposing `StateFlow<OnboardingState>` and `refresh()`; the initial value MUST be computed synchronously using `ProtectionHealthChecker` so no dashboard frame is shown before the first evaluation
- [X] T007 Create `OnboardingViewModel` (`@HiltViewModel`) in `app/src/main/kotlin/com/blockbuster/app/onboarding/OnboardingViewModel.kt` exposing `uiState: StateFlow<OnboardingUiState>` with `permissions`, `isComplete`, and nullable `actionError`; add `refresh()`, `openOverlaySettings(context)`, `openAccessibilitySettings(context)`, `openDeviceAdminSettings(context)` and `consumeError()`, and ensure no open action throws
- [X] T008 [P] Add Robolectric tests in `app/src/test/kotlin/com/blockbuster/app/onboarding/OnboardingRepositoryTest.kt` covering: `isComplete` is false with any missing permission, `isComplete` is true only with all three, `refresh()` emits a re-evaluated state, and accessibility uses the full component name (use `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [36])`)

**Checkpoint**: Foundation ready — model, checks, repository, intents and ViewModel exist; user stories can proceed.

---

## Phase 3: User Story 1 - Configuración guiada antes de usar la aplicación (Priority: P1) 🎯 MVP

**Goal**: Mostrar un asistente con tres tarjetas secuenciales que solicite superposición, accesibilidad y administración del dispositivo, bloqueando el panel principal hasta completar los tres permisos.

**Independent Test**: Instalar en limpio sin permisos, abrir la app y comprobar que aparece el asistente en vez del panel; pulsar cada acción y comprobar que abre la pantalla del sistema correcta; conceder los tres permisos y comprobar que aparece el dashboard.

### Implementation for User Story 1

- [X] T009 [US1] Create `OnboardingScreen` composable in `app/src/main/kotlin/com/blockbuster/app/ui/onboarding/OnboardingScreen.kt` with a title/subtitle, a vertical scroll container and exactly three `Card`s in order: Overlay, Accessibility, Device Admin; each card MUST show title, description, a pending status when not granted, and an action button when pending
- [X] T010 [US1] Add a root composable (for example `BlockBusterRoot`) in `app/src/main/kotlin/com/blockbuster/app/MainActivity.kt` that renders only `OnboardingScreen` when `onboardingState.isComplete == false` and only `BlockBusterNavHost` when `isComplete == true`; the dashboard MUST NOT be composed while permissions are pending
- [X] T011 [US1] Wire `MainActivity` to obtain `OnboardingViewModel` with `by viewModels()`, collect `uiState` with `collectAsStateWithLifecycle()`, and pass the three action callbacks to `OnboardingScreen` so each card opens its corresponding system settings screen via `OnboardingIntents`
- [X] T012 [US1] Ensure the overlay card launches `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with `package:${context.packageName}`, the accessibility card launches `Settings.ACTION_ACCESSIBILITY_SETTINGS`, and the device admin card launches `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN` with the `BlockerDeviceAdminReceiver` `ComponentName` in `app/src/main/kotlin/com/blockbuster/app/ui/onboarding/OnboardingScreen.kt` and `app/src/main/kotlin/com/blockbuster/app/onboarding/OnboardingIntents.kt`
- [X] T013 [US1] Validate quickstart Scenarios 1 and 2 in `specs/005-onboarding-setup-wizard/quickstart.md` on an emulator/device: dashboard blocked from a clean install, each action opens the expected settings screen, and the dashboard appears only after granting all three permissions

**Checkpoint**: US1 is independently demonstrable as the MVP: a gated three-step setup flow.

---

## Phase 4: User Story 2 - Confirmación inmediata al volver del menú del sistema (Priority: P2)

**Goal**: Reevaluar automáticamente el estado al volver del menú del sistema y reflejar `"✔ Activo"` en cada tarjeta sin reiniciar la app, incluyendo cambios en caliente de accesibilidad.

**Independent Test**: Con dos permisos concedidos y uno pendiente, conceder el pendiente en el sistema y volver a la app; la tarjeta cambia a `"✔ Activo"` en menos de 1 segundo y el dashboard aparece si era el último permiso.

### Implementation for User Story 2

- [X] T014 [US2] Override `MainActivity.onResume()` in `app/src/main/kotlin/com/blockbuster/app/MainActivity.kt` to call `onboardingViewModel.refresh()` so the three permissions are re-evaluated every time the user returns from a system settings screen
- [X] T015 [US2] Combine the repository refresh trigger with the `AccessibilityEngine.connectionState` flow in `app/src/main/kotlin/com/blockbuster/app/onboarding/OnboardingRepository.kt` so accessibility changes while the app is visible update the emitted `OnboardingState` without polling
- [X] T016 [US2] Render the `"✔ Activo"` badge (`onboarding_status_active`) in primary color for granted cards and the pending status otherwise in `app/src/main/kotlin/com/blockbuster/app/ui/onboarding/OnboardingScreen.kt`, driven only by `OnboardingUiState` so the UI updates as soon as a new state is collected
- [X] T017 [P] [US2] Extend `app/src/test/kotlin/com/blockbuster/app/onboarding/OnboardingRepositoryTest.kt` with tests for state transitions after `refresh()` (pending → active and active → pending) and for accessibility connection changes updating the state
- [X] T018 [US2] Validate quickstart Scenarios 3 and 4 in `specs/005-onboarding-setup-wizard/quickstart.md`: immediate `"✔ Activo"` on return, and re-blocking of the dashboard after revoking a permission with the app in background

**Checkpoint**: US1 and US2 work together: the setup flow is gated and updates immediately on return.

---

## Phase 5: User Story 3 - Continuidad ante pantallas del sistema no disponibles (Priority: P3)

**Goal**: Evitar cierres inesperados cuando una pantalla de ajustes no existe o el lanzamiento falla, mostrando un mensaje comprensible y manteniendo el asistente utilizable.

**Independent Test**: Provocar la indisponibilidad de un intent de ajustes y comprobar que la app sigue abierta, muestra un mensaje con la alternativa manual y permite reintentar.

### Implementation for User Story 3

- [X] T019 [US3] Add ordered fallbacks in `app/src/main/kotlin/com/blockbuster/app/onboarding/OnboardingIntents.kt`: overlay primary → same `ACTION_MANAGE_OVERLAY_PERMISSION` without URI → `ACTION_MANAGE_APPLICATIONS_SETTINGS` → `ACTION_SETTINGS`; accessibility primary → `ACTION_SETTINGS`; device admin primary → `ACTION_SECURITY_SETTINGS` → `ACTION_SETTINGS`; return a success/failure result without propagating exceptions
- [X] T020 [US3] Publish `onboarding_error_settings_unavailable` into `actionError` from `app/src/main/kotlin/com/blockbuster/app/onboarding/OnboardingViewModel.kt` when all fallbacks fail, and show it in a `Snackbar` in `app/src/main/kotlin/com/blockbuster/app/ui/onboarding/OnboardingScreen.kt`, invoking `consumeError()` when dismissed
- [X] T021 [P] [US3] Add Robolectric tests in `app/src/test/kotlin/com/blockbuster/app/onboarding/OnboardingIntentsTest.kt` covering the action and data of each primary intent, the presence of `DevicePolicyManager.EXTRA_DEVICE_ADMIN` as the correct `ComponentName`, the fallback order, and that an unresolvable intent returns failure without throwing
- [X] T022 [P] [US3] Add tests in `app/src/test/kotlin/com/blockbuster/app/onboarding/OnboardingViewModelTest.kt` verifying `actionError` is set when an open action fails and cleared by `consumeError()`, using a fake or test repository and `kotlinx-coroutines-test`
- [ ] T023 [US3] Validate quickstart Scenarios 5 and 6 in `specs/005-onboarding-setup-wizard/quickstart.md`: unavailable settings screen shows a message without crashing, and repeated actions leave the app stable

**Checkpoint**: All three stories work: gated setup, reactive confirmation, and graceful failure handling.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verificación final, accesibilidad de la pantalla y limpieza.

- [X] T024 [P] Review `app/src/main/kotlin/com/blockbuster/app/ui/onboarding/OnboardingScreen.kt` for large-font support, vertical scrolling, content descriptions and Material 3 theming consistency with `app/src/main/kotlin/com/blockbuster/app/ui/dashboard/MainDashboardScreen.kt`
- [X] T025 [P] Remove unused imports and run `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` to confirm the full unit suite passes and the debug APK builds
- [ ] T026 Run all scenarios in `specs/005-onboarding-setup-wizard/quickstart.md` end-to-end on API 26 and API 34+ and record any deviations
- [X] T027 [P] Confirm no new dependencies were added to `app/build.gradle.kts` or `gradle/libs.versions.toml` and that all new code lives under `app/src/main/kotlin/com/blockbuster/app/onboarding/` and `app/src/main/kotlin/com/blockbuster/app/ui/onboarding/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T001) — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational completion.
- **User Story 2 (Phase 4)**: Depends on US1 completion because it refines the same screen and activity wiring.
- **User Story 3 (Phase 5)**: Depends on Foundational completion; it can start in parallel with US2 once US1's screen exists.
- **Polish (Phase 6)**: Depends on all desired stories being complete.

### User Story Dependencies

- **US1 (P1)**: Requires T003–T007; T009–T013 deliver the MVP.
- **US2 (P2)**: Requires US1 (T009–T011); T014–T018 add lifecycle refresh, live accessibility updates and the active badge.
- **US3 (P3)**: Requires Foundational (T005, T007); T019–T023 add fallbacks, error surfacing and resilience tests. Integrates with US1's screen.

### Within Each User Story

- Tests are written before the implementation they validate where practical and must fail first.
- Model/state before repository; repository before ViewModel; ViewModel before screen wiring.
- Core implementation before manual quickstart validation.

### Parallel Opportunities

- T003, T004, T005 touch different files and can run in parallel after T002.
- T008 and T017 are test-only files and can run in parallel with unrelated UI work.
- T021 and T022 touch different test files and can run in parallel.
- T024, T025 and T027 in Polish are independent.

---

## Parallel Example: User Story 1

```bash
# After Foundational phase, US1 implementation tasks touch different concerns:
Task: "Create OnboardingScreen composable in app/src/main/kotlin/com/blockbuster/app/ui/onboarding/OnboardingScreen.kt"
Task: "Wire OnboardingViewModel and root routing in app/src/main/kotlin/com/blockbuster/app/MainActivity.kt"
Task: "Implement primary intents in app/src/main/kotlin/com/blockbuster/app/onboarding/OnboardingIntents.kt"
```

## Parallel Example: User Story 2

```bash
# Repository reactivity and tests can proceed together:
Task: "Combine refresh trigger with AccessibilityEngine.connectionState in OnboardingRepository.kt"
Task: "Extend OnboardingRepositoryTest.kt with refresh/state-transition tests"
```

## Parallel Example: User Story 3

```bash
# Independent test files:
Task: "Add OnboardingIntentsTest.kt for intent actions, extras and fallbacks"
Task: "Add OnboardingViewModelTest.kt for actionError handling"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational.
3. Complete Phase 3: US1 (T009–T013).
4. **STOP and VALIDATE**: run quickstart Scenarios 1–2 and confirm the dashboard is blocked until all three permissions are granted.
5. Deploy/demo the MVP.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 → gated three-step wizard (MVP).
3. US2 → immediate reactive confirmation on return.
4. US3 → graceful handling of unavailable system screens.
5. Polish → final verification and cleanup.

### Parallel Team Strategy

1. One developer completes Setup + Foundational.
2. After Foundational:
   - Developer A: US1 screen + routing (T009–T013).
   - Developer B: prepares US3 tests (T021) against the agreed `OnboardingIntents` contract.
3. After US1: Developer A completes US2 (T014–T018); Developer B completes US3 (T019–T023).
4. Merge and run Polish.

---

## Notes

- [P] tasks = different files, no incomplete dependencies.
- [Story] labels map tasks to user stories for traceability.
- Each story must be independently completable and testable at its checkpoint.
- Manual quickstart validation is required for permission flows because real system screens cannot be fully simulated by Robolectric.
- Avoid adding dependencies or moving onboarding code into `:core:*`; the feature is app-specific per `plan.md`.
