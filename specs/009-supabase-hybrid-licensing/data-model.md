# Data Model: Licencias Híbridas con Supabase y Pasarelas

**Feature**: `009-supabase-hybrid-licensing`
**Date**: 2026-11-06

## SubscriptionResponse (DTO remoto)

Mapea una fila de la tabla `subscriptions` de Supabase.

| Campo | Tipo | Columna | Reglas |
|-------|------|---------|--------|
| `userId` | `String` | `user_id` | UUID o identificador anónimo no vacío. |
| `isPremium` | `Boolean` | `is_premium` | Indicador remoto de licencia. |
| `expiresAt` | `String?` | `expires_at` | ISO-8601 opcional. |
| `paymentProvider` | `String?` | `payment_provider` | `binance_pay`, `paypal` u otro valor libre. |

**Mapeo**

```text
SubscriptionResponse -> RemoteSubscription
  userId              -> userId
  isPremium           -> isPremium
  expiresAt (ISO)     -> expiresAtEpochMillis (Instant.parse)
  paymentProvider     -> paymentProvider
```

**Validación**

- `user_id` no vacío.
- `expires_at` inválido no debe romper la app: se mapea a `null` y se conserva
  la caché local.
- Una fila ausente (`null`) significa que no hay licencia remota conocida.

## RemoteSubscription (dominio)

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `userId` | `String` | Identidad usada en la consulta. |
| `isPremium` | `Boolean` | Estado remoto. |
| `expiresAtEpochMillis` | `Long?` | Expiración normalizada. |
| `paymentProvider` | `String?` | Proveedor que registró el pago. |

## IsPremiumState (local, ampliado)

| Campo | Tipo | Valor por defecto | Reglas |
|-------|------|-------------------|--------|
| `isPremium` | `Boolean` | `false` | Estado efectivo, considerando expiración. |
| `expiresAtEpochMillis` | `Long?` | `null` | `null` = sin expiración conocida. |
| `paymentProvider` | `String?` | `null` | Último proveedor conocido. |

**Cálculo efectivo**

```text
isPremium = storedIsPremium && (expiresAt == null || expiresAt > now)
```

## LicenseIdentity

| Campo | Tipo | Persistencia | Reglas |
|-------|------|--------------|--------|
| `userId` | `String` (UUID) | DataStore | Se genera una vez si no existe; estable entre sesiones. |

## LicenseSyncResult

| Valor | Significado | Efecto en UI |
|-------|-------------|--------------|
| `Updated(license)` | Remoto exitoso y caché actualizada | El flujo local emite el nuevo estado. |
| `Offline(cachedLicense)` | Fallo de red/timeout; se usa caché | La UI mantiene el estado local. |
| `Invalid(cachedLicense)` | Respuesta remota inválida | Se conserva la caché. |
| `NoIdentity` | No hay identidad disponible | Se trata como Free. |

## PaymentGateway

| Valor | URL de configuración | Etiqueta UI |
|-------|----------------------|-------------|
| `BINANCE_PAY` | `BINANCE_PAY_URL` | Binance Pay |
| `PAYPAL` | `PAYPAL_URL` | PayPal |

## PaymentVerificationStatus

| Valor | Significado |
|-------|-------------|
| `IDLE` | Sin verificación en curso. |
| `CHECKING` | Consulta remota en curso; botón deshabilitado. |
| `CONFIRMED` | La licencia Premium fue confirmada. |
| `NOT_DETECTED` | El remoto sigue sin reflejar el pago. |
| `OFFLINE` | No se pudo consultar por red. |
| `ERROR` | Fallo controlado inesperado. |

## SubscriptionUiState (UI)

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `isPremium` | `Boolean` | Estado efectivo. |
| `expiresAtEpochMillis` | `Long?` | Expiración mostrada. |
| `paymentProvider` | `String?` | Último proveedor. |
| `isCheckingPayment` | `Boolean` | Indicador de carga. |
| `verificationStatus` | `PaymentVerificationStatus` | Resultado de la última verificación. |
| `messageRes` | `Int?` | Mensaje para Snackbar/estado. |

## State Transitions

```text
[Cold start]
  ├─ leer DataStore (inmediato) ──▶ UI con licencia local
  └─ lanzar sync remoto en IO
       ├─ éxito ──▶ escribir DataStore ──▶ UI actualizada
       └─ fallo ──▶ conservar DataStore

[OnResume]
  └─ sync si han pasado ≥30 s (o no hay sync en curso)

[Verify Payment]
  ├─ CHECKING ──▶ CONFIRMED (remoto Premium) ──▶ UI desbloqueada
  │            ├─ NOT_DETECTED (remoto Free) ──▶ UI Free + reintento
  │            ├─ OFFLINE ──▶ UI mantiene estado local
  │            └─ ERROR ──▶ UI mantiene estado local

[Expiración]
  storedIsPremium && expiresAt <= now ──▶ Free efectivo
```

## Validation Rules

- La UI nunca espera la red para emitir la primera licencia.
- Toda escritura de caché ocurre tras una respuesta remota válida.
- Un fallo de red no borra la licencia local.
- La fecha de expiración vencida prevalece sobre `is_premium = true`.
- `verifyPayment` no se ejecuta dos veces en paralelo.
- Las URLs de pasarela vacías se consideran no disponibles.
- El UUID de identidad se genera una sola vez y no se expone fuera de la app.
