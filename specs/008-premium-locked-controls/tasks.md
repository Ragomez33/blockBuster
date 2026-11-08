---
description: "Task list for visible locked Premium controls"
---

# Tasks: Controles Premium Bloqueados y Visibles

**Input**: Design documents from `/specs/008-premium-locked-controls/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Incluidos según la estrategia del plan: tests JUnit del mapeo puro de estado Premium y de los ViewModels existentes, más validación manual de la interacción táctil mediante `quickstart.md`.

**Organization**: Tareas agrupadas por historia de usuario para permitir implementación y validación independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece la tarea (US1–US4)
- Todas las rutas son relativas a la raíz del repositorio

## Path Conventions

- UI en `app/src/main/kotlin/com/blockbuster/app/ui/`.
- Tests en `app/src/test/kotlin/com/blockbuster/app/`.
- Recursos en `app/src/main/res/values/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Recursos y verificación del estado base.

- [X] T001 Run `gradlew.bat :app:testDebugUnitTest` from the repository root and confirm the existing suite is green before modifying `app/build.gradle.kts` or any source file
- [X] T002 Add strings `premium_upgrade`, `premium_locked_state`, `premium_unlocked_state` and `premium_locked_hint` to `app/src/main/res/values/strings.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Modelo de presentación puro que determina bloqueo, habilitación y acción efectiva.

- [X] T003 Create `PremiumOption` (`STRICT_SHORTS_REELS`, `FRICTION_TIME_CUSTOMIZATION`, `ACCOUNTABILITY_PARTNER`), `PremiumControlAction` (`TOGGLE`, `EDIT`, `OPEN_SUBSCRIPTION`) and `PremiumControlState` with the exact mapping `isLocked = !isPremium`, `enabled = isPremium`, `alpha = if (isPremium) 1f else 0.55f`, `showBadge = !isPremium` and `action = if (isPremium) TOGGLE|EDIT else OPEN_SUBSCRIPTION` in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumControlState.kt`
- [X] T004 [P] Add `PremiumControlStateTest` in `app/src/test/kotlin/com/blockbuster/app/ui/premium/PremiumControlStateTest.kt` covering Free (locked, disabled, alpha 0.55, badge visible, `OPEN_SUBSCRIPTION`) and Premium (unlocked, enabled, alpha 1f, no badge, `TOGGLE`/`EDIT`) for all three Premium options

---

## Phase 3: User Story 1 - Ver todas las funciones aunque estén bloqueadas (Priority: P1) 🎯 MVP

**Goal**: Mantener visibles las tres opciones Premium en sus categorías con badge y apariencia deshabilitada cuando la cuenta es Free.

**Independent Test**: Abrir Blocking con una cuenta Free y comprobar que restricciones de Shorts/Reels, tiempo de fricción y partner son visibles, atenuados y con badge; los presets gratuitos siguen habilitados.

### Implementation for User Story 1

- [X] T005 [US1] Extend `PremiumBadge` with an accessible content description and add `PremiumSwitchRow` (title, description, checked switch, badge) in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt`
- [X] T006 [US1] Add `PremiumActionRow` (title, description, current value, badge) for Premium rows that open editors in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt`
- [X] T007 [US1] Wire `BlockingScreen` `Advanced Blocking` to `PremiumSwitchRow` for strict Shorts/Reels and `PremiumActionRow` for accountability partner in `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt`
- [X] T008 [US1] Wire `BlockingScreen` `Customize Blocked screen` to `PremiumActionRow` for friction time in `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt`
- [X] T009 [US1] Ensure `Content Blocking` keeps `PresetSwitchCard` unchanged and that all three Premium options remain composed and visible for Free in `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt`
- [X] T010 [US1] Apply the Free visual treatment from `PremiumControlState`: row alpha 0.55, disabled switch colors, visible badge and legible text in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt`
- [X] T011 [US1] Validate quickstart Scenario 1 in `specs/008-premium-locked-controls/quickstart.md`: all Premium options visible and locked for Free, free presets preserved

**Checkpoint**: US1 delivers the visible locked-state UI.

---

## Phase 4: User Story 2 - Intentar usar una función Premium sin suscripción (Priority: P1)

**Goal**: Interceptar cualquier interacción con una opción Premium en Free, no cambiar el estado y abrir la suscripción simulada.

**Independent Test**: Pulsar el switch Premium y las filas de fricción/partner con cuenta Free; comprobar que no se modifica ningún valor, no se abren editores y aparece "Upgrade to Premium".

### Tests for User Story 2

- [X] T012 [P] [US2] Extend `app/src/test/kotlin/com/blockbuster/app/ui/blocking/BlockingViewModelTest.kt` verifying that Premium write actions are rejected/not persisted when the account is Free and that preset toggles remain unaffected

### Implementation for User Story 2

- [X] T013 [US2] In `PremiumSwitchRow`, set `Switch(enabled = false, onCheckedChange = null)` and route every row tap to `onOpenSubscription()` when `isPremium == false`, never invoking `onCheckedChange`, in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt`
- [X] T014 [US2] In `PremiumActionRow`, route every row tap to `onOpenSubscription()` and never invoke `onEdit` when `isPremium == false` in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt`
- [X] T015 [US2] Guard the friction and partner dialogs in `BlockingScreen` so they only open when `uiState.isPremium` is true and the callback executes at most one navigation per tap in `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt`
- [X] T016 [US2] Add `stateDescription` semantics ("Bloqueado, requiere Premium" / "Desbloqueado") and the Premium hint to the locked rows in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt`
- [X] T017 [US2] Validate quickstart Scenarios 2–5 in `specs/008-premium-locked-controls/quickstart.md`: switch tap, friction tap, partner tap, no state change and cancel purchase

**Checkpoint**: US1 and US2 deliver the complete Free experience.

---

## Phase 5: User Story 3 - Desbloqueo inmediato tras suscribirse (Priority: P2)

**Goal**: Habilitar todos los controles Premium de forma reactiva tras la compra simulada, conservando sus valores.

**Independent Test**: Completar la compra desde una opción bloqueada y comprobar que los tres controles quedan habilitados sin reiniciar, que se pueden editar y que los valores persisten.

### Tests for User Story 3

- [X] T018 [P] [US3] Extend `app/src/test/kotlin/com/blockbuster/app/ui/blocking/BlockingViewModelTest.kt` verifying `isPremium` flips reactively and the three Premium settings can be changed only after Premium

### Implementation for User Story 3

- [X] T019 [US3] Ensure the Premium branch removes alpha 0.55 and badge, enables the switch, and invokes `onCheckedChange`/`onEdit` normally in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt`
- [X] T020 [US3] Verify `BlockingScreen` reads `uiState.isPremium` directly from the existing `StateFlow` without local cached state in `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt`
- [X] T021 [US3] Validate quickstart Scenarios 6 and 7 in `specs/008-premium-locked-controls/quickstart.md`: immediate unlock, editing and persistence after restart

**Checkpoint**: US3 completes the Premium transition.

---

## Phase 6: User Story 4 - Preservar la protección gratuita (Priority: P3)

**Goal**: Garantizar que los presets gratuitos y la persistencia existente no se alteran.

**Independent Test**: Con una cuenta Free, activar y desactivar los presets de Content Blocking y comprobar que no abren la suscripción y que persisten con normalidad.

### Implementation for User Story 4

- [X] T022 [US4] Confirm `PresetSwitchCard` and its callbacks remain untouched in `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt`, and that `PremiumGate` is used only for Premium options
- [X] T023 [P] [US4] Add or extend regression coverage for free preset toggling in `app/src/test/kotlin/com/blockbuster/app/ui/blocking/BlockingViewModelTest.kt`
- [X] T024 [US4] Validate quickstart Scenario 8 in `specs/008-premium-locked-controls/quickstart.md`: free presets activate/deactivate without subscription navigation and preserve behavior

**Checkpoint**: All four stories work without regressions.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Accesibilidad, limpieza y validación final.

- [X] T025 [P] Review `app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt` and `app/src/main/kotlin/com/blockbuster/app/ui/blocking/BlockingScreen.kt` for large fonts, TalkBack semantics, dark theme consistency and Material 3 colors
- [X] T026 [P] Confirm no changes were made to Room entities/DAOs, `BlockRulesRepository`, preset persistence, `:core:overlay` or `:core:accessibility`
- [X] T027 Run `gradlew.bat :app:testDebugUnitTest :app:assembleDebug` and fix any regression
- [ ] T028 Run all Scenarios 1–10 in `specs/008-premium-locked-controls/quickstart.md` on API 26 and API 34+ where available and record deviations
- [X] T029 [P] Confirm no new dependencies were added to `app/build.gradle.kts` or `gradle/libs.versions.toml` and that all changes stay under `app/src/main/kotlin/com/blockbuster/app/ui/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup (T002) — BLOCKS all user stories.
- **US1 (Phase 3)**: Depends on Foundational.
- **US2 (Phase 4)**: Depends on US1 components (`PremiumSwitchRow`/`PremiumActionRow`).
- **US3 (Phase 5)**: Depends on US1 and US2.
- **US4 (Phase 6)**: Depends on Foundational and US1; independent of US2/US3 behavior.
- **Polish (Phase 7)**: Depends on all desired stories.

### User Story Dependencies

- **US1 (P1)**: Requires T003/T004; T005–T011 deliver the visible locked UI.
- **US2 (P1)**: Requires US1 components; T012–T017 deliver interception and no-persistence behavior.
- **US3 (P2)**: Requires US1/US2; T018–T021 deliver the Premium branch and reactive unlock.
- **US4 (P3)**: Requires T003 and US1; T022–T024 guarantee free-preset regression safety.

### Within Each Story

- Tests and pure state mapping before UI behavior.
- Component primitives before screen wiring.
- Free branch before Premium branch.
- Manual E2E after implementation.

### Parallel Opportunities

- T003 and T002 touch different files.
- T004 can run once T003 exists.
- T012, T018 and T023 are independent test-file extensions.
- T025, T026 and T029 in Polish are independent reviews.

---

## Parallel Example: User Story 1

```bash
Task: "Extend PremiumBadge and PremiumSwitchRow in app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt"
Task: "Add PremiumActionRow in app/src/main/kotlin/com/blockbuster/app/ui/premium/PremiumGate.kt"
Task: "Wire Advanced Blocking and Customize Blocked screen in BlockingScreen.kt"
```

## Parallel Example: Tests

```bash
Task: "Extend BlockingViewModelTest for Free Premium rejection"
Task: "Extend BlockingViewModelTest for Premium unlock"
Task: "Add regression coverage for free preset toggling"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Complete Setup + Foundational.
2. Complete US1: visible locked controls.
3. Complete US2: interaction interception and subscription routing.
4. **STOP and VALIDATE**: quickstart Scenarios 1–5.
5. This is the core requested behavior.

### Incremental Delivery

1. Foundation + US1 → visible locked UI.
2. US2 → interactive gating.
3. US3 → reactive Premium unlock.
4. US4 → regression safety for free presets.
5. Polish → accessibility and full quickstart.

### Parallel Team Strategy

1. One developer completes Setup + Foundational.
2. Developer A: US1 components and wiring.
3. Developer B: test coverage for Free/Premium mapping.
4. After US1: Developer A completes US2; Developer B completes US4 regression checks.
5. Merge and run full quickstart.

---

## Notes

- [P] tasks touch different files and can run without incomplete dependencies.
- [Story] labels map each task to its user story.
- No Room, preset persistence or blocking-engine changes are allowed.
- Manual quickstart validation is required for the touch interception and simulated subscription flow.
- The existing `PremiumRepository` and `BlockingViewModel.isPremium` are the only subscription sources.
