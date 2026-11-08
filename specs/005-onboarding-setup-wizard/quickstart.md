# Quickstart: Onboarding Setup Wizard

**Feature**: `005-onboarding-setup-wizard`
**Date**: 2026-11-05

Guía de validación de extremo a extremo. No contiene código de implementación.

## Prerequisites

- Android Studio / Android SDK con `compileSdk = 37`.
- JDK 17.
- Un emulador o dispositivo con Android 8.0 (API 26) o superior; se recomienda
  API 34+ para validar los flujos modernos de ajustes.
- `adb` disponible en el `PATH`.
- La app instalada con el paquete `com.blockbuster.app`.

## Build & Test Commands

```bash
# Linux / macOS
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug

# Windows
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
```

Instalación:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Baseline: revocar los tres permisos

Antes de validar el onboarding conviene partir de un estado limpio. Los
siguientes comandos son orientativos y requieren un emulador con permisos de
root o un dispositivo de pruebas; en un dispositivo real también se puede hacer
manualmente desde Ajustes.

```bash
# Overlay
adb shell appops set com.blockbuster.app SYSTEM_ALERT_WINDOW deny

# Accesibilidad: quitar el servicio de la lista habilitada
adb shell settings put secure enabled_accessibility_services ""

# Device admin: desactivar el administrador
adb shell dpm remove-active-admin com.blockbuster.app/.admin.BlockerDeviceAdminReceiver
```

> Si el comando de `dpm` falla porque el administrador no está activo, el estado
> ya es el deseado.

## Scenario 1 — Bloqueo inicial del dashboard (P1)

1. Revocar los tres permisos (ver baseline).
2. Abrir la app desde el launcher.
3. **Esperado**: se muestra el asistente con tres tarjetas y estado pendiente;
   el dashboard de interruptores maestros (presets y monitoreo) no es accesible.
4. Pulsar la acción del paso 1.
5. **Esperado**: se abre la pantalla del sistema para conceder superposición a
   `com.blockbuster.app` (o una pantalla de ajustes equivalente si el intent no
   resuelve).
6. Volver atrás a la app.
7. **Esperado**: la tarjeta del paso 1 muestra `✔ Activo`; el dashboard sigue
   bloqueado porque faltan permisos.

## Scenario 2 — Concesión secuencial completa (P1)

1. Partir de Scenario 1 con overlay concedido.
2. Pulsar la acción del paso 2 y conceder el servicio de accesibilidad
   `BlockBuster` en los ajustes del sistema.
3. Volver a la app.
4. **Esperado**: la tarjeta del paso 2 muestra `✔ Activo`; el dashboard sigue
   bloqueado porque falta el administrador.
5. Pulsar la acción del paso 3 y activar el administrador del dispositivo.
6. Volver a la app.
7. **Esperado**: la tarjeta del paso 3 muestra `✔ Activo` y la app pasa
   automáticamente al dashboard de interruptores maestros (`BlockBusterNavHost`),
   donde se ven los presets Contenido adulto, Videos cortos y Búsquedas visuales.

## Scenario 3 — Confirmación inmediata al volver (P2)

1. Partir de un estado con dos permisos concedidos y uno pendiente.
2. Conceder el permiso pendiente desde la pantalla del sistema.
3. Volver a la app sin reiniciarla.
4. **Esperado**: la tarjeta correspondiente cambia a `✔ Activo` en menos de
   1 segundo y, si era el último, aparece el dashboard.

## Scenario 4 — Revocación con la app en segundo plano (P2/P3)

1. Completar los tres permisos y llegar al dashboard.
2. Enviar la app a segundo plano.
3. Revocar el permiso de superposición desde Ajustes del sistema
   (`adb shell appops set com.blockbuster.app SYSTEM_ALERT_WINDOW deny`).
4. Volver a la app.
5. **Esperado**: el asistente vuelve a mostrarse, la tarjeta de superposición
   aparece pendiente y el dashboard queda bloqueado.

## Scenario 5 — Pantalla de ajustes no disponible (P3)

1. Usar un dispositivo/emulador donde el intent específico de un paso no
   resuelva (por ejemplo, un OEM sin esa pantalla).
2. Pulsar la acción del paso afectado.
3. **Esperado**: la app no se cierra; intenta un fallback o muestra un mensaje
   comprensible con la alternativa manual.
4. Volver a la app y comprobar que el asistente sigue siendo utilizable y que
   el estado se reevalúa.

## Scenario 6 — Acciones repetidas (P3)

1. Pulsar repetidamente el botón de un paso mientras se abre y se cierra la
   pantalla del sistema.
2. **Esperado**: no hay cierres inesperados ni estados inconsistentes.

## Automated Test Focus

`./gradlew :app:testDebugUnitTest` debe cubrir:

- `OnboardingState` calcula `isComplete` solo con los tres permisos.
- El repositorio emite el estado reevaluado tras `refresh()`.
- La detección de accesibilidad usa el componente completo y no un simple
  `contains(packageName)`.
- Los intents de cada paso llevan la action, el URI y el
  `EXTRA_DEVICE_ADMIN` correctos.
- Las rutas de fallback y el resultado de error no lanzan excepciones.

## Expected Outcomes

| Criterio | Verificación |
|----------|--------------|
| SC-001 | Instalación limpia siempre muestra el asistente. |
| SC-002 | Los tres pasos se completan en menos de 3 minutos. |
| SC-003 | Cambio a `✔ Activo` en < 1 s tras volver. |
| SC-004 | Dashboard inaccesible con cualquier permiso pendiente. |
| SC-005 | Sin cierres con pantallas no disponibles. |
