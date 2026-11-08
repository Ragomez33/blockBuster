# Quickstart: Navegación por Pestañas, Focus Mode y Freemium

**Feature**: `007-tabs-focus-premium`
**Date**: 2026-11-06

Guía de validación de extremo a extremo. No contiene código de implementación.

## Prerequisites

- JDK 17 y Android SDK con `compileSdk = 37`.
- Emulador o dispositivo Android 8.0 (API 26) o superior; se recomienda API 34+.
- La app instalada como `com.blockbuster.app`.
- Onboarding completado para acceder a las pestañas.

## Build & Test Commands

```bash
# Linux / macOS
./gradlew :core:database:testDebugUnitTest \
  :core:overlay:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:assembleDebug

# Windows
gradlew.bat :core:database:testDebugUnitTest :core:overlay:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

Instalación:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Scenario 1 — Navegación y preservación de Blocking (P1)

1. Completar el onboarding (o iniciar con los tres permisos activos).
2. Abrir la app.
3. **Esperado**: barra inferior con Blocking, Blocklist y Settings; Blocking
   seleccionada.
4. Activar "Contenido adulto" en Blocking y comprobar el contador.
5. Ir a Blocklist y volver a Blocking.
6. **Esperado**: el interruptor y el contador conservan su estado.
7. Ir a Settings y volver.
8. **Esperado**: la pestaña seleccionada y los estados se conservan.

## Scenario 2 — Gating del onboarding (P1)

1. Revocar uno de los tres permisos críticos.
2. Volver a la app.
3. **Esperado**: se muestra el onboarding y no la navegación por pestañas.
4. Conceder el permiso de nuevo.
5. **Esperado**: se muestra el dashboard con Blocking seleccionada.

## Scenario 3 — Blocklist: añadir, eliminar y prioridad (P2)

1. Abrir Blocklist.
2. Alternar entre `Apps` y `Sites & Keywords`; comprobar los selectores
   `Blacklist`/`Whitelist`.
3. Pulsar el FAB, añadir una aplicación a la lista negra y confirmar.
4. **Esperado**: la entrada aparece de inmediato.
5. Repetir con el mismo valor en la lista negra.
6. **Esperado**: se rechaza como duplicado con un mensaje.
7. Añadir el mismo valor a la lista blanca.
8. **Esperado**: la lista blanca prevalece; la app no se bloquea.
9. Eliminar la entrada blanca y comprobar que la negra vuelve a aplicar.
10. Eliminar la entrada negra y comprobar que desaparece.

## Scenario 4 — Focus Mode (P3)

1. Pulsar el FAB global de Focus Mode desde cualquiera de las tres pestañas.
2. **Esperado**: se abre el temporizador sin perder la pestaña actual.
3. Iniciar una sesión sin cambiar la configuración.
4. **Esperado**: ciclo por defecto de 25 min de trabajo y 5 de descanso.
5. Configurar duraciones cortas válidas (por ejemplo 1/1) e iniciar.
6. **Esperado**: el temporizador avanza y respeta las duraciones.
7. Durante la fase de trabajo, abrir una app de ocio de la lista curada.
8. **Esperado**: el overlay bloquea la app aunque el preset correspondiente esté
   apagado.
9. Pausar la sesión.
10. **Esperado**: el bloqueo adicional se relaja y el tiempo restante se
    conserva.
11. Reanudar, cambiar de pestaña y volver.
12. **Esperado**: la sesión continúa.
13. Detener la sesión.
14. **Esperado**: se retira el bloqueo adicional y los switches maestros quedan
    como estaban.

## Scenario 5 — Persistencia de Focus (P3)

1. Iniciar una sesión de Focus de varios minutos.
2. Enviar la app a segundo plano o reiniciar el proceso.
3. Volver a abrir la app.
4. **Esperado**: la sesión se restaura con el tiempo restante correcto o se
   marca como finalizada si ya expiró.
5. Comprobar que el bloqueo adicional corresponde a la fase restaurada.

## Scenario 6 — Freemium y compra simulada (P4)

1. Abrir Settings con una cuenta nueva.
2. **Esperado**: estado `Free`.
3. Ir a Blocking y pulsar una opción de `Advanced Blocking` o `Customize Blocked
   screen`.
4. **Esperado**: se muestra el candado/corona y se abre la pantalla de
   suscripción.
5. Pulsar "Volver" sin comprar.
6. **Esperado**: se sigue en Free y la opción permanece bloqueada.
7. Volver a abrir la suscripción y completar la compra simulada.
8. **Esperado**: el estado pasa a Premium, la opción se desbloquea y el estado
   persiste tras reiniciar.
9. Intentar comprar de nuevo.
10. **Esperado**: no se duplica la suscripción.

## Scenario 7 — Ajustes y soporte (P4)

1. Abrir Settings.
2. Pulsar "Buscar actualizaciones".
3. **Esperado**: resultado comprensible sin cierre inesperado.
4. Abrir "Política de privacidad".
5. **Esperado**: se muestra el contenido dentro de la app y se puede volver.
6. Abrir "Soporte".
7. **Esperado**: se muestran FAQ/contacto sin salir de forma inesperada.

## Scenario 8 — Precedencia de lista blanca sobre Focus (P3/P2)

1. Iniciar Focus Mode en fase de trabajo.
2. Añadir una app de ocio a la lista blanca.
3. Abrir esa app.
4. **Esperado**: no se bloquea, porque la lista blanca tiene prioridad.
5. Eliminarla de la lista blanca.
6. **Esperado**: vuelve a bloquearse durante Focus.

## Automated Test Focus

`./gradlew :core:database:testDebugUnitTest :core:overlay:testDebugUnitTest
:app:testDebugUnitTest`

Debe cubrir:

- DAO/repositorio de blocklist: inserción, duplicado, eliminación y consultas.
- Migración Room 2→3.
- `BlocklistMatcher`: whitelist primero, app/dirección/keyword.
- `FocusModeRuleProvider`: solo genera reglas en fase `WORK`/`RUNNING`.
- `OverlayCoordinator`: whitelist exime; blacklist bloquea; Focus añade bloqueo;
  fricción configurable.
- `PremiumRepository`: Free por defecto, persistencia, fallback a Free.
- `FocusSessionRepository`: start/pause/resume/stop y validación de duraciones.
- ViewModels: estados Free/Premium, Focus y blocklist.

## Expected Outcomes

| Criterio | Verificación |
|----------|--------------|
| SC-001 | Tras onboarding se ven las 3 pestañas. |
| SC-002 | Cambio de pestaña < 1 s sin perder estado. |
| SC-003 | Los switches maestros conservan lógica y estado. |
| SC-004 | Añadir a blocklist < 30 s y reflejo < 1 s. |
| SC-005 | La whitelist prevalece sobre blacklist y Focus. |
| SC-006 | Focus inicia < 2 s y bloquea < 1 s. |
| SC-007 | La sesión conserva tiempo al cambiar de pestaña/segundo plano. |
| SC-008 | Free ve candados en funciones Premium. |
| SC-009 | Compra simulada desbloquea en < 1 s y persiste. |
| SC-010 | Cancelar compra no cambia el estado. |
| SC-011 | Onboarding pendiente impide las pestañas. |
| SC-012 | Fallos simulados se comunican sin cierre inesperado. |
