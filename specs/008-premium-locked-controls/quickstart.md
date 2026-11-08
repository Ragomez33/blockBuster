# Quickstart: Controles Premium Bloqueados y Visibles

**Feature**: `008-premium-locked-controls`
**Date**: 2026-11-06

Guía de validación de UI e interacción. No contiene código de implementación.

## Prerequisites

- JDK 17 y Android SDK con `compileSdk = 37`.
- Emulador o dispositivo API 26+ (se recomienda API 34+).
- App instalada con onboarding completado.
- Una cuenta en estado Free para los escenarios 2–5.
- Una cuenta con posibilidad de completar la suscripción simulada para los
  escenarios 6–7.

## Build & Test Commands

```bash
# Linux / macOS
./gradlew :app:testDebugUnitTest :app:assembleDebug

# Windows
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Instalación:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Scenario 1 — Funciones Premium visibles en Free (P1)

1. Abrir la pestaña Blocking con una cuenta Free.
2. Revisar Content Blocking, Advanced Blocking y Customize Blocked screen.
3. **Esperado**:
   - Los presets gratuitos siguen visibles y habilitados.
   - Restricciones estrictas de Shorts/Reels está visible, atenuada y con badge.
   - Personalización del tiempo de fricción está visible, atenuada y con badge.
   - Partner de responsabilidad está visible, atenuado y con badge.
   - Ninguna opción Premium está oculta.

## Scenario 2 — Interruptor Premium bloqueado (P1)

1. Con cuenta Free, pulsar el interruptor de restricciones de Shorts/Reels.
2. **Esperado**:
   - El interruptor no cambia de estado.
   - Se abre la pantalla "Upgrade to Premium".
   - Al volver sin comprar, el interruptor sigue en su valor anterior.
3. Pulsar directamente sobre el área del switch deshabilitado.
4. **Esperado**: mismo resultado; la suscripción se abre y el valor no cambia.

## Scenario 3 — Ajuste de fricción bloqueado (P1)

1. Con cuenta Free, pulsar la fila de personalización del tiempo de fricción.
2. **Esperado**:
   - No se abre el diálogo de edición.
   - Se abre la pantalla de suscripción.
   - El valor mostrado (por ejemplo, 60 segundos) no cambia.

## Scenario 4 — Partner bloqueado (P1)

1. Con cuenta Free, pulsar la fila del partner de responsabilidad.
2. **Esperado**:
   - No se abre el diálogo de partner.
   - Se abre la pantalla de suscripción.
   - El valor previo, si existía, sigue visible sin cambios.

## Scenario 5 — Cancelar suscripción (P1)

1. Abrir la suscripción desde una opción bloqueada.
2. Pulsar "Volver" sin comprar.
3. **Esperado**:
   - La cuenta sigue en Free.
   - Las opciones Premium siguen atenuadas y con badge.
   - Ningún valor persistido ha cambiado.

## Scenario 6 — Desbloqueo tras la compra (P2)

1. Abrir la suscripción desde una opción Premium.
2. Completar la compra simulada.
3. Volver a Blocking.
4. **Esperado**:
   - La cuenta aparece como Premium.
   - Los tres controles Premium se habilitan sin reiniciar.
   - El alpha reducido y los badges desaparecen.
   - El interruptor se puede alternar y los editores se abren.

## Scenario 7 — Persistencia del desbloqueo y de los valores (P2)

1. Con cuenta Premium, cambiar el interruptor de restricciones.
2. Editar el tiempo de fricción y guardar un valor válido.
3. Editar el partner y guardar.
4. Reiniciar la aplicación.
5. **Esperado**:
   - La cuenta sigue Premium.
   - Los tres controles siguen habilitados.
   - Los valores guardados se conservan.

## Scenario 8 — Presets gratuitos intactos (P3)

1. Con cuenta Free, activar y desactivar los tres presets de Content Blocking.
2. **Esperado**:
   - Los presets se activan/desactivan con normalidad.
   - No se abre la suscripción.
   - El contador de protecciones se actualiza y persiste.
   - No hay regresiones en el bloqueo.

## Scenario 9 — Fallback de suscripción no disponible (Edge)

1. Simular un fallo de lectura del estado de suscripción (datos corruptos o
   repositorio no disponible).
2. Abrir Blocking.
3. **Esperado**:
   - La cuenta se trata como Free.
   - Las opciones Premium siguen visibles y bloqueadas.
   - La app no se cierra y los presets gratuitos siguen funcionando.

## Scenario 10 — Taps repetidos (Edge)

1. Con cuenta Free, pulsar varias veces seguidas un control Premium.
2. **Esperado**:
   - No se encolan varias pantallas de suscripción.
   - El estado del control no cambia.
   - Al volver, la pantalla sigue utilizable.

## Automated Test Focus

`./gradlew :app:testDebugUnitTest` debe cubrir:

- `PremiumControlState` Free: locked, disabled, alpha reducido, badge visible,
  acción `OPEN_SUBSCRIPTION`.
- `PremiumControlState` Premium: unlocked, enabled, alpha completo, sin badge,
  acción `TOGGLE`/`EDIT`.
- `BlockingViewModel` sigue exponiendo `isPremium` reactivo.
- Los métodos de escritura Premium no se invocan desde Free.
- Los presets gratuitos conservan su estado y lógica.

## Expected Outcomes

| Criterio | Verificación |
|----------|--------------|
| SC-001 | Las 3 opciones Premium están visibles en Free. |
| SC-002 | Las 3 muestran badge de candado/corona. |
| SC-003 | Todo tap Free abre suscripción y no cambia estado. |
| SC-004 | Cero valores Premium persistidos desde Free. |
| SC-005 | Tras comprar, los controles se habilitan en < 1 s. |
| SC-006 | Los presets gratuitos mantienen su comportamiento. |
| SC-007 | Los valores Premium se conservan al volver a Premium. |
| SC-008 | Fallo de estado Premium se trata como Free sin cierre. |
| SC-009 | Ninguna modificación Premium es posible en Free. |
| SC-010 | Tema oscuro/Material 3 se mantiene. |
