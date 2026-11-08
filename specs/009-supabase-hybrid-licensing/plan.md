# Implementation Plan: Licencias Híbridas con Supabase y Pasarelas

**Branch**: `009-supabase-hybrid-licensing` | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-supabase-hybrid-licensing/spec.md`

## Summary

Añadir un módulo `:core:network` con `supabase-kt` (Auth + Postgrest) y una
fuente remota de suscripciones. `PremiumRepositoryImpl` pasa a una estrategia
híbrida: la UI lee siempre la licencia local de DataStore de inmediato y, en
paralelo, se sincroniza con Supabase en cold start y `onResume`, actualizando la
caché si hay red y conservándola si falla. La pantalla de suscripción abre
Binance Pay y PayPal en flujos externos y ofrece "Verificar Pago" para forzar
una sincronización con indicador de carga.

## Technical Context

**Language/Version**: Kotlin 2.4.21, JVM 17, Android Gradle Plugin 9.5.0

**Primary Dependencies**: supabase-kt BOM `3.9.0` (`auth-kt`, `postgrest-kt`), Ktor `ktor-client-okhttp` `3.6.1`, Kotlin Serialization 2.4.21, Hilt 2.60.1, DataStore Preferences 1.3.0, Coroutines 1.11.0, Material 3/Compose existentes. No se añaden SDKs de pago nativos.

**Storage**: DataStore Preferences para licencia (`isPremium`, `expiresAt`, proveedor, identidad anónima y último sync). No hay cambios en Room.

**Testing**: JUnit 4.13.2 + Robolectric 4.17 + coroutines test. `PremiumRepositoryTest` se actualiza con un `FakeSubscriptionRemoteDataSource`; no se depende de Supabase real. `SubscriptionViewModelTest` se actualiza para pasarelas y verificación.

**Target Platform**: Android `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`.

**Project Type**: Aplicación Android multi-módulo; se añade `:core:network`.

**Performance Goals**: La licencia local se emite sin esperar red; la sincronización remota no bloquea la primera composición y tiene timeout acotado (10 s). El estado Premium/Free se actualiza en < 1 s tras una respuesta remota.

**Constraints**: Consultas en `Dispatchers.IO`; sin secretos de servicio en el APK; URLs de pasarela configurables; no romper tests existentes; modularidad `app -> core:network -> core:common`, `core:database -> core:common`; la UI nunca espera la red para renderizar.

**Scale/Scope**: Nuevo módulo de red, contrato remoto y modelo de licencia, evolución de `PremiumRepositoryImpl`, identidad anónima, cold start/onResume, pantalla de suscripción y tests. Aproximadamente 15–18 archivos.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` es plantilla sin ratificar. Se aplican las
convenciones del proyecto:

| Gate | Resultado |
|------|-----------|
| Dependencias externas justificadas | PASS — `supabase-kt` y Ktor son requisitos explícitos; no se añaden SDKs de pago. |
| Modularidad | PASS — nuevo `:core:network`; contratos en `:core:common`; persistencia en `:core:database`; UI en `:app`. |
| No romper Room/presets | PASS — no se tocan entidades, DAOs ni presets. |
| Offline-first | PASS — DataStore es la fuente inmediata; la red solo actualiza la caché. |
| Sin bloqueo del hilo principal | PASS — cold start/onResume lanzan sincronización en `Dispatchers.IO`; la UI lee el flujo local. |
| Seguridad | PASS — solo URL y anon key públicas en BuildConfig; no service role. |
| Tests existentes | PASS — se actualizan fakes y constructor de `PremiumRepositoryImpl`; no se depende de red real. |
| UI Material 3/tema oscuro | PASS — se conserva la pantalla y el tema actuales. |

Se justifica la adición del módulo `:core:network` y de las dependencias
Supabase/Ktor porque la feature las exige. No hay otras violaciones.

## Project Structure

### Documentation (this feature)

```text
specs/009-supabase-hybrid-licensing/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── hybrid-licensing-contract.md
└── tasks.md             # Fase 2 (/speckit.tasks, no creado aquí)
```

### Source Code (repository root)

```text
settings.gradle.kts                              # MODIFICAR: include(":core:network")
gradle/libs.versions.toml                        # MODIFICAR: BOM Supabase, auth/postgrest, Ktor, serialization
core/network/
├── build.gradle.kts                             # NUEVO: Android library, Hilt, KSP, serialization, Supabase/Ktor
├── src/main/AndroidManifest.xml                 # NUEVO
└── src/main/kotlin/com/blockbuster/core/network/
    ├── di/NetworkModule.kt                      # NUEVO: SupabaseClient singleton + datasource binding
    ├── supabase/SupabaseClientProvider.kt       # NUEVO: createSupabaseClient con Auth/Postgrest
    └── subscription/SupabaseSubscriptionRemoteDataSource.kt # NUEVO: consulta subscriptions

core/common/src/main/kotlin/com/blockbuster/core/common/
├── model/RemoteSubscription.kt                  # NUEVO
├── model/IsPremiumState.kt                      # MODIFICAR: expiresAtEpochMillis/paymentProvider
├── model/PaymentGateway.kt                      # NUEVO: BINANCE_PAY, PAYPAL
├── contract/SubscriptionRemoteDataSource.kt     # NUEVO
├── contract/PremiumRepository.kt                # MODIFICAR: sync/verify
└── result/AppResult.kt                          # REVISAR: errores de red/verificación

core/database/src/main/kotlin/com/blockbuster/core/database/
├── datastore/PremiumRepositoryImpl.kt           # MODIFICAR: híbrido + caché + expiración
├── datastore/LicenseIdentityStore.kt            # NUEVO: UUID anónimo persistido
├── datastore/BlockBusterPreferences.kt          # MODIFICAR: claves de licencia/identidad
└── di/DatabaseModule.kt                         # REVISAR: nuevas dependencias

app/
├── build.gradle.kts                             # MODIFICAR: core:network + BuildConfig URLs Supabase/pasarelas
├── src/main/kotlin/com/blockbuster/app/
│   ├── BlockBusterApplication.kt                # MODIFICAR: sync en cold start (IO)
│   ├── MainActivity.kt                          # MODIFICAR: sync en onResume con throttle
│   ├── ui/premium/SubscriptionViewModel.kt      # MODIFICAR: gateways, verify, loading
│   └── ui/premium/SubscriptionScreen.kt         # MODIFICAR: botones Binance/PayPal + Verificar Pago
└── src/test/kotlin/com/blockbuster/app/...       # ACTUALIZAR tests de SubscriptionViewModel

core/database/src/test/kotlin/...                  # ACTUALIZAR PremiumRepositoryTest/RepositoryTestFixture
```

**Structure Decision**: `:core:network` encapsula Supabase y Ktor, evitando que
`:core:database` o `:core:common` dependan del SDK. `SubscriptionRemoteDataSource`
y los modelos viven en `:core:common`; `PremiumRepositoryImpl` (en
`:core:database`) depende solo del contrato. `:app` ensambla ambos módulos.

## Design Decisions

1. **Supabase**: `createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY)` con
   `install(Auth)` e `install(Postgrest)`, provisto como `@Singleton` en
   `NetworkModule`. La anon key es pública; nunca se usa service role.
2. **Identidad**: `LicenseIdentityStore` genera un UUID una vez y lo persiste en
   DataStore. Se usa como `user_id` en la consulta remota; no requiere login.
3. **Contrato remoto**:
   ```kotlin
   interface SubscriptionRemoteDataSource {
       suspend fun fetchSubscription(userId: String): RemoteSubscription?
   }
   ```
   `RemoteSubscription` contiene `userId`, `isPremium`, `expiresAtEpochMillis`,
   `paymentProvider`. La implementación consulta `subscriptions` por `user_id`.
4. **Modelo local**: `IsPremiumState` se amplía con `expiresAtEpochMillis` y
   `paymentProvider`. `PremiumRepository` añade:
   - `suspend fun checkSubscriptionStatus(): LicenseSyncResult`
   - `suspend fun verifyPayment(): AppResult<IsPremiumState>`
   La UI sigue observando `observePremiumState()`.
5. **Estrategia híbrida**: `observePremiumState()` lee DataStore y calcula
   `isPremium = storedIsPremium && (expiresAt == null || expiresAt > now)`.
   `checkSubscriptionStatus()` ejecuta remoto en IO; si responde, escribe la
   caché; si falla, no escribe y devuelve un resultado `Offline` con la licencia
   local. La UI nunca espera esta llamada.
6. **Cold start/onResume**: `BlockBusterApplication.onCreate` lanza la
   sincronización en un scope de aplicación + `Dispatchers.IO`. `MainActivity`
   dispara en `onResume` a través de un método throttled/coalescido en el
   repositorio (una sincronización cada 30 s y `Mutex` para evitar duplicados).
7. **Expiración**: la fecha remota se parsea como ISO-8601 a epoch millis. Si
   está vencida, la licencia efectiva es Free. Si el parseo falla, se conserva la
   caché y se registra el fallo.
8. **Pasarelas**: `PaymentGateway` con URLs desde BuildConfig. La UI resuelve y
   abre `Intent.ACTION_VIEW` con `runCatching`; si falla, muestra Snackbar. No
   modifica el estado por sí sola.
9. **Verificación**: `SubscriptionViewModel.verifyPayment()` bloquea doble
   pulsación con `isCheckingPayment`, llama a `verifyPayment()` y publica
   `Success`, `NotDetected`, `Offline` o `Error` para Snackbar/estado.
10. **Legacy**: `setPremium()` se conserva para compatibilidad de datos/tests,
    pero la UI de compra deja de usarlo como camino principal.
11. **Tests**: el fixture de `:core:database` se actualiza con un fake remoto y
    un dispatcher de test. Los tests de `SubscriptionViewModel` se actualizan
    para las nuevas acciones. `SupabaseClient` no se instancia en tests.

## Phase 0: Research

Ver [research.md](./research.md). No quedan `NEEDS CLARIFICATION`.

## Phase 1: Design & Contracts

- Modelo de datos: [data-model.md](./data-model.md)
- Contrato de red/repositorio/UI: [contracts/hybrid-licensing-contract.md](./contracts/hybrid-licensing-contract.md)
- Guía de validación: [quickstart.md](./quickstart.md)

## Post-Design Constitution Check

Se mantienen los gates en PASS. La única complejidad añadida (módulo de red y
dependencias Supabase/Ktor) está justificada por el requisito explícito. No se
modifican Room, presets ni el motor de bloqueo, y la UI conserva su
comportamiento offline-first.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Nuevo módulo `:core:network` | Aislar Supabase/Ktor y respetar la modularidad | Incluir SDK en `:core:common` contaminaría dominio y obligaría a dependencias de red en todos los consumidores |
| Dependencias Supabase/Ktor | Integración real con la tabla de suscripciones | Cliente HTTP manual incrementaría código, serialización y manejo de errores sin ventaja |
| Ampliar `IsPremiumState` | Persistir expiración/proveedor y evaluar licencias vencidas | Mantener solo el booleano impediría el offline-first con expiración |
