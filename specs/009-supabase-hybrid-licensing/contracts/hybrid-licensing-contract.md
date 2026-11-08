# Contract: Hybrid Licensing, Supabase & Payment Gateways

**Feature**: `009-supabase-hybrid-licensing`
**Type**: Contratos de red, repositorio y UI
**Date**: 2026-11-06

## 1. Configuración de Supabase

**BuildConfig / Gradle**

| Clave | Uso | Obligatoria |
|-------|-----|-------------|
| `SUPABASE_URL` | URL de la instancia | Sí en producción |
| `SUPABASE_ANON_KEY` | Clave pública anónima | Sí en producción |
| `BINANCE_PAY_URL` | Enlace de pago Binance Pay | Opcional; vacío = no disponible |
| `PAYPAL_URL` | Enlace de pago PayPal | Opcional; vacío = no disponible |

**Contrato**

- Solo se incluyen claves públicas de cliente. Nunca `service_role`.
- Si URL/anon key están vacías, el cliente se marca como no configurado y la
  sincronización devuelve `Offline`/`NoIdentity` sin cerrar la app.
- El cliente se crea una vez como singleton Hilt.

## 2. Tabla `subscriptions`

```sql
create table subscriptions (
  user_id text primary key,
  is_premium boolean not null default false,
  expires_at timestamptz,
  payment_provider text
);
```

**Contrato de consulta**

- Entrada: `userId` estable.
- Salida: cero o una fila.
- Si hay varias filas para el mismo `user_id`, la implementación debe elegir una
  de forma determinista (por `updated_at` si existe; si no, la primera) y
  registrar la anomalía.
- El cliente no escribe en `subscriptions`; solo lee. El webhook/backend
  actualiza la tabla.

## 3. SubscriptionRemoteDataSource

```kotlin
interface SubscriptionRemoteDataSource {
    suspend fun fetchSubscription(userId: String): RemoteSubscription?
}
```

**Contrato**

- Se ejecuta en `Dispatchers.IO` desde el repositorio.
- `null` = no existe licencia remota conocida.
- Lanza excepciones controlables en fallos de red/parseo; el repositorio las
  captura con `runCatching`.
- No debe acceder a DataStore ni a la UI.

## 4. PremiumRepository (ampliado)

```kotlin
interface PremiumRepository {
    fun observePremiumState(): Flow<IsPremiumState>
    fun observeSettings(): Flow<PremiumSettings>

    suspend fun checkSubscriptionStatus(): LicenseSyncResult
    suspend fun verifyPayment(): AppResult<IsPremiumState>

    suspend fun setPremium(premium: Boolean): AppResult<Unit>
    suspend fun setStrictShortsReels(enabled: Boolean): AppResult<Unit>
    suspend fun setFrictionWindowMillis(value: Long): AppResult<Unit>
    suspend fun setAccountabilityPartner(value: String?): AppResult<Unit>
}
```

**Contrato de sincronización**

- `checkSubscriptionStatus()`:
  - Obtiene/crea la identidad.
  - Consulta el remoto dentro de `runCatching`.
  - Si hay respuesta válida, actualiza DataStore (`isPremium`, `expiresAt`,
    `provider`, timestamp de sync).
  - Si falla, no modifica la caché.
  - Coalesce llamadas concurrentes con `Mutex`.
  - Aplica throttle de 30 s para llamadas automáticas.
- `verifyPayment()`:
  - Ignora el throttle.
  - Si ya hay una verificación en curso, no lanza otra.
  - Devuelve el estado efectivo tras la consulta.
  - No deja la UI cargando indefinidamente; timeout acotado.
- `observePremiumState()`:
  - Emite la licencia local inmediatamente.
  - Aplica expiración: `isPremium = stored && (expiresAt == null || expiresAt > now)`.

## 5. LicenseIdentityStore

```kotlin
interface LicenseIdentityStore {
    suspend fun getOrCreateUserId(): String
    suspend fun peekUserId(): String?
}
```

**Contrato**

- Genera `UUID.randomUUID().toString()` si no existe.
- Persiste en DataStore.
- Es estable entre sesiones y reinicios.
- No se sincroniza entre dispositivos en esta iteración.

## 6. Cold start y onResume

**Contrato de arranque**

- `BlockBusterApplication.onCreate()`:
  - No bloquea.
  - Lanza `checkSubscriptionStatus()` en un scope de aplicación con
    `Dispatchers.IO`.
- `MainActivity.onResume()`:
  - Solicita `checkSubscriptionStatus()` (throttled).
  - No muestra loading ni bloquea la composición.
- La UI usa `observePremiumState()` desde el primer frame.

## 7. PaymentGateways

```kotlin
enum class PaymentGateway {
    BINANCE_PAY,
    PAYPAL,
}
```

**Contrato de UI**

- `SubscriptionScreen` muestra dos botones separados.
- Al pulsar:
  - Valida que la URL no esté vacía.
  - Construye `Intent.ACTION_VIEW` con `Uri.parse(url)`.
  - Valida resolución y lanza con `runCatching`.
  - Si falla, muestra Snackbar y no cambia el estado.
- Si `isPremium == true`, muestra el estado activo y no ofrece compra.
- No se abre una pasarela desde un estado Premium ya confirmado.

## 8. Verify Payment UI

```kotlin
data class SubscriptionUiState(
    val isPremium: Boolean,
    val expiresAtEpochMillis: Long?,
    val paymentProvider: String?,
    val isCheckingPayment: Boolean,
    val verificationStatus: PaymentVerificationStatus,
    val messageRes: Int?,
)
```

**Contrato**

- Acción "Verificar Pago" visible para Free.
- Pulsar:
  - Si `isCheckingPayment` es true, ignora.
  - Pone `isCheckingPayment = true`.
  - Llama `verifyPayment()`.
  - Publica `CONFIRMED`, `NOT_DETECTED`, `OFFLINE` o `ERROR`.
  - Vuelve a `isCheckingPayment = false` siempre.
- `CONFIRMED` desbloquea Premium por el flujo reactivo.
- `NOT_DETECTED` mantiene Free y sugiere reintentar.
- `OFFLINE` mantiene la licencia local y muestra mensaje de red.
- Sin doble verificación ni navegación duplicada.

## 9. Error handling

- Red/timeout: `LicenseSyncResult.Offline(cached)`.
- Parseo inválido: `LicenseSyncResult.Invalid(cached)`.
- Sin identidad: `NoIdentity`; efectivo Free.
- Pasarela sin URL/resolución: Snackbar, sin excepción.
- URL/anon key ausentes: cliente no configurado; sync returned Offline; la app
  sigue con la caché.
- Nunca se propaga una excepción de red a la UI.

## 10. Test contract

- `FakeSubscriptionRemoteDataSource` implementa el contrato con:
  - respuesta configurable,
  - excepción configurable,
  - contador de llamadas.
- `PremiumRepositoryTest` inyecta el fake y un dispatcher de test.
- No se instancia `SupabaseClient`.
- `SubscriptionViewModelTest` inyecta un `PremiumRepository` fake y verifica
  estados de verificación y no duplicados.
- `RepositoryTestFixture` actualizado con fake remoto.
