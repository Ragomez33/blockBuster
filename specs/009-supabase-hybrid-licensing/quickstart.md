# Quickstart: Licencias Híbridas con Supabase y Pasarelas

**Feature**: `009-supabase-hybrid-licensing`
**Date**: 2026-11-06

Guía de validación de extremo a extremo. No contiene código de implementación.

## Prerequisites

- JDK 17 y Android SDK con `compileSdk = 37`.
- Emulador/dispositivo API 26+.
- Proyecto Supabase con tabla `subscriptions`.
- Valores de configuración en `local.properties` o variables de entorno:
  - `SUPABASE_URL`
  - `SUPABASE_ANON_KEY`
  - `BINANCE_PAY_URL` (opcional)
  - `PAYPAL_URL` (opcional)
- No commitear claves reales ni usar `service_role` en la app.

Ejemplo de configuración en `local.properties` (ver también
`specs/009-supabase-hybrid-licensing/config.sample.properties`):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anon-key
BINANCE_PAY_URL=https://your-binance-pay-checkout.example
PAYPAL_URL=https://your-paypal-checkout.example
```

## Build & Test Commands

```bash
# Linux / macOS
./gradlew :core:database:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug

# Windows
gradlew.bat :core:database:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

Instalación:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Scenario 1 — Cold start con licencia cacheada (P1)

1. Con red, iniciar la app y confirmar una licencia Premium en Supabase.
2. Esperar a que la sincronización termine.
3. Activar modo avión y cerrar la app por completo.
4. Abrir la app.
5. **Esperado**:
   - El estado Premium aparece sin esperar a la red.
   - Las funciones Premium siguen desbloqueadas.
   - No hay pantalla de carga bloqueante ni cierre inesperado.

## Scenario 2 — Cold start sin licencia local y sin red (P1)

1. Borrar los datos de la app (o usar una instalación limpia).
2. Activar modo avión justo antes del primer arranque.
3. Abrir la app.
4. **Esperado**:
   - La cuenta se trata como Free.
   - La app es usable.
   - No se produce un error bloqueante.

## Scenario 3 — Sincronización remota a Premium (P1)

1. Tener la app en Free con conexión.
2. Insertar/actualizar en Supabase una fila Premium para la identidad de la app:
   ```sql
   insert into subscriptions (user_id, is_premium, expires_at, payment_provider)
   values ('<uuid>', true, now() + interval '30 days', 'paypal')
   on conflict (user_id) do update set
     is_premium = excluded.is_premium,
     expires_at = excluded.expires_at,
     payment_provider = excluded.payment_provider;
   ```
3. Reiniciar la app o volver a primer plano.
4. **Esperado**:
   - La sincronización actualiza el estado local.
   - Premium se refleja en menos de 1 segundo tras la respuesta.
   - La fecha de expiración y el proveedor se conservan localmente.

## Scenario 4 — Expiración remota (P1)

1. Con Premium local, actualizar la fila remota con `is_premium = true` y una
   `expires_at` en el pasado.
2. Volver a primer plano o reiniciar.
3. **Esperado**: la cuenta pasa a Free y las funciones avanzadas se bloquean.

## Scenario 5 — Fallo de red durante la sincronización (P1)

1. Tener una licencia Premium local.
2. Desactivar la red.
3. Volver a primer plano.
4. **Esperado**:
   - La app conserva Premium local.
   - No se muestra un error bloqueante.
   - El intento de sincronización se registra como offline.

## Scenario 6 — Binance Pay (P2)

1. Abrir Settings → suscripción con cuenta Free.
2. Configurar `BINANCE_PAY_URL`.
3. Pulsar "Binance Pay".
4. **Esperado**: se abre el flujo externo configurado.
5. Vaciar la URL o usar un enlace inválido y repetir.
6. **Esperado**: Snackbar de error, app abierta y estado sin cambios.

## Scenario 7 — PayPal (P2)

1. Con `PAYPAL_URL` configurada, pulsar "PayPal".
2. **Esperado**: se abre el flujo externo de PayPal.
3. Cancelar/abandonar el flujo.
4. Volver a la app.
5. **Esperado**: la cuenta sigue Free y no se ha modificado ningún valor.

## Scenario 8 — Verificar Pago confirmado (P3)

1. Completar un pago y actualizar manualmente la fila remota a Premium.
2. En la app Free, pulsar "Verificar Pago".
3. **Esperado**:
   - Aparece un indicador de carga breve.
   - Se ejecuta una consulta remota.
   - La cuenta pasa a Premium y las funciones se desbloquean.
   - Se muestra un mensaje de éxito.
   - El indicador desaparece.

## Scenario 9 — Verificar Pago no detectado (P3)

1. Pulsar "Verificar Pago" sin que la fila remota esté actualizada.
2. **Esperado**:
   - La cuenta sigue Free.
   - Se muestra un mensaje para reintentar cuando el pago se procese.
   - La app sigue usable.

## Scenario 10 — Verificar Pago offline (P3)

1. Activar modo avión.
2. Pulsar "Verificar Pago".
3. **Esperado**:
   - Se muestra un mensaje de error de red.
   - Se conserva el estado local.
   - El indicador de carga termina.

## Scenario 11 — Doble pulsación (P3)

1. Pulsar "Verificar Pago" repetidamente.
2. **Esperado**:
   - Solo se ejecuta una verificación.
   - No se duplican mensajes ni navegaciones.

## Scenario 12 — Seguridad de configuración (Restricción)

1. Inspeccionar el APK/BuildConfig.
2. **Esperado**:
   - Solo aparecen URL y anon key públicas.
   - No aparece ninguna service role key ni secreto de backend.
3. Sin URL/anon key configuradas:
   - La app arranca, se mantiene la caché local y no se cierra.

## Automated Test Focus

`./gradlew :core:database:testDebugUnitTest :app:testDebugUnitTest`

Debe cubrir:

- `PremiumRepositoryImpl` con `FakeSubscriptionRemoteDataSource`:
  - remoto exitoso actualiza caché;
  - fallo de red conserva caché;
  - expiración vencida => Free;
  - respuesta inválida conserva caché;
  - `verifyPayment` confirma/no detecta/offline;
  - identidad estable;
  - no dobles sincronizaciones.
- `SubscriptionViewModel`:
  - abrir Binance/PayPal;
  - verificación con loading;
  - éxito/no detectado/error;
  - doble pulsación ignorada;
  - no compra si ya Premium.
- Tests existentes actualizados, sin red real ni SupabaseClient.

## Expected Outcomes

| Criterio | Verificación |
|----------|--------------|
| SC-001 | Cold start muestra licencia local sin esperar red. |
| SC-002 | Ninguna consulta remota bloquea la UI. |
| SC-003 | Fallo de red conserva el estado local. |
| SC-004 | Expiración remota degrada a Free. |
| SC-005 | Cambio remoto se refleja en < 1 s tras sync. |
| SC-006 | Botones de pasarela abren su flujo o muestran error. |
| SC-007 | Verificar Pago muestra resultado en < 10 s. |
| SC-008 | Verificación exitosa desbloquea sin reiniciar. |
| SC-009 | Cero verificaciones duplicadas. |
| SC-010 | Sin secretos de servicio en el APK. |
| SC-011 | Tests existentes y nuevos pasan. |
