# Research: Licencias Híbridas con Supabase y Pasarelas

**Feature**: `009-supabase-hybrid-licensing`
**Date**: 2026-11-06
**Status**: Complete — no `NEEDS CLARIFICATION` remaining

## Research Tasks

| # | Topic | Resolution |
|---|-------|------------|
| R1 | Versiones y artefactos de supabase-kt | BOM `3.9.0` + `auth-kt` + `postgrest-kt` |
| R2 | Motor HTTP requerido | `ktor-client-okhttp` `3.6.1` |
| R3 | Módulo donde alojar la integración | Nuevo `:core:network` |
| R4 | Identidad de licencia | UUID anónimo estable persistido en DataStore |
| R5 | Estrategia offline-first | DataStore como fuente inmediata; red actualiza en IO |
| R6 | Expiración y degradación a Free | Parseo ISO-8601; fecha vencida => Free |
| R7 | Coalescencia cold start/onResume | `Mutex` + throttle de 30 s |
| R8 | URLs de pasarelas y BuildConfig | `BINANCE_PAY_URL` y `PAYPAL_URL` configurables |
| R9 | Verificación manual sin dobles envíos | `isCheckingPayment` + llamada única |
| R10 | Estrategia de pruebas | Fake remote datasource; sin red real |

---

## R1 — supabase-kt

**Decision**: Usar el BOM `io.github.jan-tennert.supabase:bom:3.9.0` y los
artefactos `auth-kt` y `postgrest-kt`. El cliente se crea con
`createSupabaseClient(url, anonKey)` instalando `Auth` y `Postgrest`.

**Rationale**: `3.9.0` es la última versión estable publicada en Maven Central
(metadata consultada el 2026-11-06). El BOM alinea auth/postgrest. Auth se
instala porque es un requisito explícito y permite evolucionar a login anónimo
de Supabase en el futuro.

**Alternatives considered**:
- `gotrue-kt`: nombre de versiones anteriores; en 3.x el módulo es `auth-kt`.
- Versión beta `3.2.0-ksp-b1`: descartada por no ser estable.

---

## R2 — Motor HTTP

**Decision**: Añadir `io.ktor:ktor-client-okhttp:3.6.1` y el plugin de
serialización de Kotlin 2.4.21. El cliente Supabase usa OkHttp como engine
Android.

**Rationale**: Supabase-kt es multiplataforma y necesita un engine Ktor. OkHttp
es el engine Android estándar y no añade un cliente HTTP adicional. Ktor 3.6.1
es la última estable y es compatible con Kotlin 2.4.x.

**Alternatives considered**:
- `ktor-client-android`: válido, pero OkHttp es más robusto y común en Android.
- Sin engine explícito: el cliente falla en tiempo de ejecución.

---

## R3 — Módulo de red

**Decision**: Crear `:core:network` como Android library con Hilt, KSP,
serialización, Supabase y Ktor. `:app` y `:core:database` dependen de contratos
en `:core:common`, no del SDK.

**Rationale**: Evita que `:core:common` (dominio puro) o `:core:database`
(persistencia) dependan de Supabase/Ktor. Mantiene la dirección
`app -> core:network`, `core:database -> core:common`,
`core:network -> core:common`.

**Alternatives considered**:
- `:core:common`: contamina dominio con red y obliga a todos los consumidores a
  cargar el SDK.
- `:core:database`: mezcla persistencia con red y rompe responsabilidades.

---

## R4 — Identidad de licencia

**Decision**: `LicenseIdentityStore` genera un UUID con `UUID.randomUUID()` la
primera vez y lo persiste en DataStore bajo una clave privada. Ese UUID se usa
como `user_id` en la consulta a `subscriptions`.

**Rationale**: No requiere login ni backend de autenticación, cumple el requisito
de "ID de dispositivo/usuario anónimo registrado" y es estable entre sesiones.

**Alternatives considered**:
- `ANDROID_ID`: puede cambiar por dispositivo/fábrica y no es un UUID de
  suscripción.
- Supabase anonymous auth: requiere proyecto configurado y conexión en el primer
  arranque; se deja como evolución futura.
- Identidad en memoria: se pierde al reiniciar y desasocia la licencia.

---

## R5 — Offline-first

**Decision**: La UI sigue observando `observePremiumState()` de DataStore. En
cold start y `onResume` se lanza `checkSubscriptionStatus()` en
`Dispatchers.IO`. Si el remoto responde, se escribe la caché; si falla, no se
escribe y se conserva el valor local. La primera composición no espera la red.

**Rationale**: Garantiza continuidad operativa y cumple "cero bloqueos en cold
start". La red es una mejora, no un requisito para renderizar.

**Alternatives considered**:
- Bloquear la UI hasta la respuesta remota: viola el requisito.
- Solo remoto: deja la app inutilizable sin conexión.

---

## R6 — Expiración

**Decision**: `expires_at` se parsea como ISO-8601 con `Instant.parse` a epoch
millis. La licencia efectiva es Premium si `is_premium == true` y
(`expiresAt == null` o `expiresAt > now`). Si el parseo falla, se conserva la
caché y se registra el error.

**Rationale**: Evita licencias perpetuas por fechas inválidas y permite degradar
a Free sin conexión.

**Alternatives considered**:
- Ignorar la expiración: permitiría Premium indefinido.
- Tratar fecha inválida como Free: podría revocar licencias por un dato
  transitorio; se prefiere conservar la caché.

---

## R7 — Coalescencia de sincronizaciones

**Decision**: `PremiumRepositoryImpl` usa un `Mutex` para serializar
`checkSubscriptionStatus()` y un timestamp del último intento. Las llamadas de
`onResume` se omiten si la última sincronización fue hace menos de 30 s. La
verificación manual ("Verificar Pago") ignora el throttle y usa el mismo Mutex.

**Rationale**: Evita múltiples consultas al alternar entre apps o rotar la
pantalla, sin impedir una verificación manual explícita.

**Alternatives considered**:
- Sin control: varias llamadas simultáneas y respuestas fuera de orden.
- Bloquear siempre 30 s: impediría verificar el pago inmediatamente.

---

## R8 — Pasarelas y configuración

**Decision**: Añadir `BINANCE_PAY_URL` y `PAYPAL_URL` a `BuildConfig` desde
propiedades de Gradle con valores por defecto vacíos. La UI construye un
`Intent.ACTION_VIEW` con el URI y lo lanza con `runCatching`, validando
resolución. Si está vacío o no resuelve, muestra un Snackbar.

**Rationale**: Mantiene las URLs fuera del código y permite cambiarlas por
entorno sin tocar la app. No se añaden SDKs de pago.

**Alternatives considered**:
- Hardcodear URLs: difícil de cambiar y riesgo de filtrar entornos.
- SDK nativo de Binance/PayPal: dependencia externa innecesaria; el pago es web.

---

## R9 — Verificación manual

**Decision**: `SubscriptionViewModel.verifyPayment()` comprueba
`isCheckingPayment`; si ya hay una verificación en curso, ignora la pulsación.
Publica `isCheckingPayment`, resultado (`Confirmed`, `NotDetected`, `Offline`,
`Error`) y mensaje. La llamada remota es puntual, con timeout acotado.

**Rationale**: Cumple la acción "Verificar Pago" sin sondeo indefinido ni dobles
llamadas.

**Alternatives considered**:
- Polling automático cada N segundos: consumo y comportamiento impredecible.
- Sin bloqueo de doble pulsación: múltiples requests y mensajes duplicados.

---

## R10 — Pruebas

**Decision**:
- `PremiumRepositoryTest` recibe un `FakeSubscriptionRemoteDataSource` y un
  dispatcher de test. Cubre remoto exitoso, fallo offline, expiración,
  respuesta inválida, actualización de caché y `verifyPayment`.
- `SubscriptionViewModelTest` cubre apertura de pasarelas, verificación,
  indicador de carga, resultado confirmado/no detectado/error y no duplicados.
- No se instancia `SupabaseClient` real en tests.
- `RepositoryTestFixture` se actualiza con el fake remoto.

**Rationale**: Cumple "no romper tests existentes" y evita dependencia de red.

**Alternatives considered**:
- Mockear `SupabaseClient` con MockK: dependencia nueva no declarada; el fake del
  contrato es más simple.
- Tests de integración contra Supabase: lentos y no deterministas.
