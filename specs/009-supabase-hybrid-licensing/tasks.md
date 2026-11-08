---
description: "Task list for Supabase hybrid licensing"
---

# Tasks: Licencias Híbridas con Supabase y Pasarelas

**Input**: Design documents from `/specs/009-supabase-hybrid-licensing/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Incluidos según la estrategia del plan: tests de repositorio con fuente remota simulada, tests de ViewModel y validación manual de red/pasarelas con `quickstart.md`.

**Organization**: Tareas agrupadas por historia de usuario para permitir implementación y validación independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario a la que pertenece la tarea (US1–US4)
- Todas las rutas son relativas a la raíz del repositorio

## Path Conventions

- Módulo de red nuevo: `core/network/src/main/kotlin/com/blockbuster/core/network/`.
- Contratos/modelos: `core/common/src/main/kotlin/com/blockbuster/core/common/`.
- Persistencia: `core/database/src/main/kotlin/com/blockbuster/core/database/`.
- UI/arranque: `app/src/main/kotlin/com/blockbuster/app/`.
- Tests: `core/database/src/test/kotlin/` y `app/src/test/kotlin/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencias, módulo de red y configuración de BuildConfig.

- [X] T001 Run `gradlew.bat :core:database:testDebugUnitTest :app:testDebugUnitTest` from the repository root and confirm the baseline suite is green before modifying `app/build.gradle.kts` or any source file
- [X] T002 Add version-catalog entries to `gradle/libs.versions.toml`: `supabase = "3.9.0"` (BOM), `ktor = "3.6.1"`, libraries `supabase-bom`, `supabase-auth`, `supabase-postgrest`, `ktor-client-okhttp`, `kotlinx-serialization-json`, and plugin `kotlin-serialization = "2.4.21"`
- [X] T003 Create the `:core:network` Android library in `core/network/build.gradle.kts` and `core/network/src/main/AndroidManifest.xml`, applying `android.library`, `ksp`, `hilt` and `kotlin.serialization`, with dependencies on `:core:common`, Supabase BOM/auth/postgrest, Ktor OkHttp, Hilt and coroutines; add `include(":core:network")` to `settings.gradle.kts`
- [X] T004 Wire `implementation(project(":core:network"))` into `app/build.gradle.kts`, enable `buildFeatures.buildConfig = true`, and define `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `BINANCE_PAY_URL` and `PAYPAL_URL` via `buildConfigField` sourced from Gradle properties/environment with empty-string defaults; do not commit real values

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Contratos, modelos, identidad, cliente Supabase y DTO remoto compartidos por las historias.

**⚠️ CRITICAL**: Ninguna historia puede completarse sin esta fase.

- [X] T005 [P] Create `RemoteSubscription` (`userId`, `isPremium`, `expiresAtEpochMillis`, `paymentProvider`) in `core/common/src/main/kotlin/com/blockbuster/core/common/model/RemoteSubscription.kt` and the `SubscriptionRemoteDataSource` contract with `suspend fun fetchSubscription(userId: String): RemoteSubscription?` in `core/common/src/main/kotlin/com/blockbuster/core/common/contract/SubscriptionRemoteDataSource.kt`
- [X] T006 [P] Extend `IsPremiumState` with `expiresAtEpochMillis: Long? = null` and `paymentProvider: String? = null` in `core/common/src/main/kotlin/com/blockbuster/core/common/model/IsPremiumState.kt`; add `PaymentGateway` (`BINANCE_PAY`, `PAYPAL`) in `core/common/src/main/kotlin/com/blockbuster/core/common/model/PaymentGateway.kt` and `LicenseSyncResult` (`Updated`, `Offline`, `Invalid`, `NoIdentity`) in `core/common/src/main/kotlin/com/blockbuster/core/common/model/LicenseSyncResult.kt`
- [X] T007 [P] Extend `PremiumRepository` with `suspend fun checkSubscriptionStatus(): LicenseSyncResult` and `suspend fun verifyPayment(): AppResult<IsPremiumState>` in `core/common/src/main/kotlin/com/blockbuster/core/common/contract/PremiumRepository.kt`, preserving all existing methods
- [X] T008 [P] Create `LicenseIdentityStore` with `getOrCreateUserId()`/`peekUserId()` in `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/LicenseIdentityStore.kt`; generate a UUID once and persist it in DataStore; add the required keys to `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/BlockBusterPreferences.kt`
- [X] T009 [P] Create `SupabaseClientProvider` in `core/network/src/main/kotlin/com/blockbuster/core/network/supabase/SupabaseClientProvider.kt` using `createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY)` with `install(Auth)` and `install(Postgrest)`; return a safely non-configured marker or fail controlled when URL/key are empty
- [X] T010 [P] Create `SubscriptionResponse` `@Serializable` DTO with `user_id`, `is_premium`, `expires_at: String?`, `payment_provider: String?` and `SupabaseSubscriptionRemoteDataSource` in `core/network/src/main/kotlin/com/blockbuster/core/network/subscription/`; query `subscriptions` by `user_id`, parse ISO-8601 `expires_at` to epoch millis with `runCatching`, and return `RemoteSubscription?`
- [X] T011 [P] Create `NetworkModule` in `core/network/src/main/kotlin/com/blockbuster/core/network/di/NetworkModule.kt` providing the singleton `SupabaseClient` and binding `SubscriptionRemoteDataSource` to the Supabase implementation
- [X] T012 Update `core/database/src/test/kotlin/com/blockbuster/core/database/RepositoryTestFixture.kt` with a `FakeSubscriptionRemoteDataSource` and the new `PremiumRepositoryImpl` dependencies; create the fake in `core/database/src/test/kotlin/com/blockbuster/core/database/support/FakeSubscriptionRemoteDataSource.kt`
- [X] T013 Compile `:core:network`, `:core:database` and `:app` after the foundational changes and fix Hilt/serialization/version conflicts before continuing

**Checkpoint**: Foundation ready — Supabase client, remote contract, identity and DataStore keys exist; user stories can proceed.

---

## Phase 3: User Story 1 - Mantener la licencia sin conexión (Priority: P1) 🎯 MVP

**Goal**: La licencia local se emite inmediatamente y se conserva cuando la red falla.

**Independent Test**: Guardar Premium local, desactivar la red, reiniciar y comprobar que Premium y las funciones siguen disponibles; sin licencia local y sin red, comprobar Free sin errores bloqueantes.

### Tests for User Story 1

- [X] T014 [P] [US1] Extend `core/database/src/test/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryTest.kt` with a fake remote covering: remote Premium updates cache, remote failure preserves cache, expired remote becomes Free, invalid/missing response preserves cache, and no local license + offline becomes Free

### Implementation for User Story 1

- [X] T015 [US1] Evolve `PremiumRepositoryImpl` in `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryImpl.kt` to inject `SubscriptionRemoteDataSource`, `LicenseIdentityStore`, `DataStore` and an IO dispatcher; keep the immediate `observePremiumState()` read from DataStore and compute effective Premium as `storedIsPremium && (expiresAt == null || expiresAt > now)`
- [X] T016 [US1] Implement `checkSubscriptionStatus()` using `runCatching` around the remote fetch in `Dispatchers.IO`; on success write `isPremium`, `expiresAtEpochMillis`, `paymentProvider` and sync timestamp to DataStore; on failure return `Offline`/`Invalid`/`NoIdentity` without deleting the cached license in `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryImpl.kt`
- [X] T017 [US1] Keep `PremiumSettings` methods and `setPremium()` working; ensure `setPremium()` remains a compatibility/test path and does not depend on the remote in `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryImpl.kt`
- [X] T018 [US1] Validate quickstart Scenarios 1, 2 and 5 in `specs/009-supabase-hybrid-licensing/quickstart.md`: cached Premium offline, Free with no cache/offline, and network failure preserving the cache

**Checkpoint**: US1 delivers offline-first license continuity.

---

## Phase 4: User Story 2 - Sincronizar al iniciar y volver a la app (Priority: P1)

**Goal**: Sincronizar licencia en cold start y `onResume` sin bloquear la UI ni duplicar consultas.

**Independent Test**: Cambiar la licencia remota, reiniciar o volver a primer plano y comprobar la actualización en menos de 1 segundo; con red caída, comprobar que la UI mantiene la caché.

### Tests for User Story 2

- [X] T019 [P] [US2] Add synchronization tests to `core/database/src/test/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryTest.kt`: concurrent calls do not produce duplicate remote requests, automatic calls honor the 30-second throttle, `verifyPayment` bypasses the throttle, and expired remote rows downgrade to Free

### Implementation for User Story 2

- [X] T020 [US2] Add `Mutex`-based coalescing and a 30-second automatic-sync throttle to `checkSubscriptionStatus()` in `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryImpl.kt`; `verifyPayment()` uses the same mutex but ignores the throttle
- [X] T021 [US2] Inject `PremiumRepository` into `app/src/main/kotlin/com/blockbuster/app/BlockBusterApplication.kt` and launch `checkSubscriptionStatus()` on an application-scoped coroutine with `Dispatchers.IO` from `onCreate()`, without blocking startup
- [X] T022 [US2] Trigger the throttled `checkSubscriptionStatus()` from `app/src/main/kotlin/com/blockbuster/app/MainActivity.kt` in `onResume()` without showing loading or awaiting the result
- [X] T023 [US2] Validate quickstart Scenarios 3 and 4 in `specs/009-supabase-hybrid-licensing/quickstart.md`: remote Premium sync, expiration downgrade and onResume behavior

**Checkpoint**: US1 and US2 deliver offline-first plus remote refresh.

---

## Phase 5: User Story 3 - Pagar con Binance Pay o PayPal (Priority: P2)

**Goal**: Abrir los flujos externos de ambas pasarelas de forma segura y sin alterar el estado.

**Independent Test**: Pulsar Binance Pay y PayPal desde SubscriptionScreen y comprobar que se abre el flujo configurado; con URL vacía o inválida, comprobar Snackbar y app estable.

### Tests for User Story 3

- [X] T024 [P] [US3] Add `PaymentGatewayLinksTest` in `app/src/test/kotlin/com/blockbuster/app/ui/premium/PaymentGatewayLinksTest.kt` verifying URL selection for BINANCE_PAY/PAYPAL, empty URLs, and invalid/blank values

### Implementation for User Story 3

- [X] T025 [US3] Create `PaymentGatewayLinks` in `app/src/main/kotlin/com/blockbuster/app/ui/premium/PaymentGatewayLinks.kt` mapping `PaymentGateway` to `BuildConfig.BINANCE_PAY_URL`/`BuildConfig.PAYPAL_URL` and exposing a null/blank result when unavailable
- [X] T026 [US3] Update `app/src/main/kotlin/com/blockbuster/app/ui/premium/SubscriptionScreen.kt` to show separate Binance Pay and PayPal buttons; build `Intent.ACTION_VIEW` with `Uri.parse(url)`, validate resolution and launch with `runCatching`; show a Snackbar on failure and never mutate Premium state from the gateway action
- [X] T027 [US3] Update `app/src/main/kotlin/com/blockbuster/app/ui/premium/SubscriptionViewModel.kt` only as needed to expose gateway availability and user-facing errors; keep `isPremium` from the repository flow
- [X] T028 [US3] Validate quickstart Scenarios 6 and 7 in `specs/009-supabase-hybrid-licensing/quickstart.md`: Binance Pay, PayPal, cancel/return and unavailable URL handling

**Checkpoint**: US3 connects the subscription screen to both payment flows.

---

## Phase 6: User Story 4 - Verificar el pago manualmente (Priority: P3)

**Goal**: Forzar una sincronización inmediata desde la UI, con loading y resultados claros, sin duplicados.

**Independent Test**: Con una fila remota actualizada a Premium, pulsar "Verificar Pago" y comprobar loading, desbloqueo y éxito; sin actualización remota, comprobar reintento; offline, comprobar error y caché conservada.

### Tests for User Story 4

- [X] T029 [P] [US4] Add repository tests for `verifyPayment()` in `core/database/src/test/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryTest.kt`: confirmed, not detected, offline, invalid response and bypass of automatic throttle
- [X] T030 [P] [US4] Extend `app/src/test/kotlin/com/blockbuster/app/ui/premium/SubscriptionViewModelTest.kt` for the loading indicator, confirmed/not-detected/offline statuses, duplicate-tap suppression and preserving Free on failure

### Implementation for User Story 4

- [X] T031 [US4] Implement `verifyPayment()` in `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryImpl.kt`: force a remote fetch, update the cache on success, return the effective `IsPremiumState` and preserve the cache on failure with a bounded timeout
- [X] T032 [US4] Extend `SubscriptionViewModel` in `app/src/main/kotlin/com/blockbuster/app/ui/premium/SubscriptionViewModel.kt` with `verificationStatus`, `isCheckingPayment`, message resources and `verifyPayment()`; ignore repeated calls while checking and always reset the loading flag
- [X] T033 [US4] Add the "Verificar Pago" action and loading indicator to `app/src/main/kotlin/com/blockbuster/app/ui/premium/SubscriptionScreen.kt`, showing confirmed/not-detected/offline/error messages without blocking the screen
- [X] T034 [US4] Validate quickstart Scenarios 8, 9, 10 and 11 in `specs/009-supabase-hybrid-licensing/quickstart.md`: confirmed payment, not detected, offline and repeated taps

**Checkpoint**: All four stories are functional and independently testable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Seguridad, configuración, regresión y validación final.

- [X] T035 [P] Inspect `app/build.gradle.kts` and generated BuildConfig to confirm only public Supabase URL/anon key and gateway URLs are included; ensure no service-role key or backend secret is committed
- [X] T036 [P] Document required `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `BINANCE_PAY_URL` and `PAYPAL_URL` configuration in `specs/009-supabase-hybrid-licensing/quickstart.md` and a sample Gradle properties snippet, without real credentials
- [X] T037 Run `gradlew.bat :core:network:testDebugUnitTest :core:database:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` and fix any regression or dependency conflict
- [ ] T038 Run all Scenarios 1–12 in `specs/009-supabase-hybrid-licensing/quickstart.md` on API 26 and API 34+ and record deviations
- [X] T039 [P] Confirm module dependency directions remain `app -> core:network`, `app -> core:database`, `core:database -> core:common`, `core:network -> core:common` and that no core module depends on `:app`
- [X] T040 [P] Review `core/database/src/main/kotlin/com/blockbuster/core/database/datastore/PremiumRepositoryImpl.kt` for cancellation handling, timeout bound, no main-thread work and no swallowed `CancellationException`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories.
- **US1 (Phase 3)**: Depends on Foundational; delivers the offline-first MVP.
- **US2 (Phase 4)**: Depends on US1 repository behavior.
- **US3 (Phase 5)**: Depends on Setup (BuildConfig) and can proceed in parallel with US1/US2 once the screen exists.
- **US4 (Phase 6)**: Depends on US1/US2 repository sync and US3 screen.
- **Polish (Phase 7)**: Depends on all desired stories.

### User Story Dependencies

- **US1 (P1)**: Requires T005–T018; T014–T018 deliver cached license continuity.
- **US2 (P1)**: Requires US1; T019–T023 deliver cold start/onResume sync and throttle.
- **US3 (P2)**: Requires T002–T004 and the existing SubscriptionScreen/ViewModel; T024–T028 deliver gateways.
- **US4 (P3)**: Requires US1/US2 sync and US3 screen; T029–T034 deliver verify payment.

### Within Each Story

- Tests before implementation where practical.
- Models/contracts before repositories, repositories before ViewModels, ViewModels before screens.
- Core sync before UI integration.
- Manual quickstart validation after implementation.

### Parallel Opportunities

- T005–T007 and T008–T011 touch different files and can run in parallel after T002–T004.
- T014, T019 and T029 are separate test additions to PremiumRepositoryTest and can be prepared together.
- T024, T030 and T035–T040 are independent files/reviews.
- US3 can be developed in parallel with US1/US2 once the module compiles.

---

## Parallel Example: Foundational

```bash
Task: "Create RemoteSubscription and SubscriptionRemoteDataSource in core/common"
Task: "Create LicenseIdentityStore in core/database"
Task: "Create SupabaseClientProvider in core/network"
Task: "Create SubscriptionResponse and SupabaseSubscriptionRemoteDataSource in core/network"
```

## Parallel Example: User Story 3

```bash
Task: "Add PaymentGatewayLinksTest in app/src/test"
Task: "Update SubscriptionScreen with Binance Pay/PayPal intents"
Task: "Update SubscriptionViewModel gateway availability"
```

---

## Implementation Strategy

### MVP First (US1)

1. Complete Setup + Foundational.
2. Complete US1.
3. **STOP and VALIDATE**: quickstart Scenarios 1, 2 and 5.
4. The app must remain fully usable offline with the cached license.

### Incremental Delivery

1. Foundation ready.
2. US1 → offline-first license (MVP).
3. US2 → cold start/onResume sync and expiration.
4. US3 → Binance Pay/PayPal flows.
5. US4 → Verify Payment and loading states.
6. Polish → security, config documentation and full quickstart.

### Parallel Team Strategy

1. One developer completes Setup + Foundational.
2. Developer A: US1/US2 repository and startup sync.
3. Developer B: US3 payment gateways in the existing subscription screen.
4. Developer C: test updates and security/config review.
5. After US1: Developer A completes US4 verify flow with Developer B.

---

## Notes

- [P] tasks touch different files and can run without incomplete dependencies.
- [Story] labels map tasks to their user story.
- Do not commit real Supabase keys, gateway URLs or service-role credentials.
- Do not instantiate `SupabaseClient` in unit tests; use the fake remote datasource.
- The UI must never block on network; DataStore is the immediate source of truth.
