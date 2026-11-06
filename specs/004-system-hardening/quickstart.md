# Quickstart: Hardening del sistema y prevención de desinstalación

**Feature**: `004-system-hardening`  
**Prerequisites**: dispositivo/emulador con Android 8+; permisos de
accesibilidad, superposición y administración; `adb` para eventos de boot.

## Comandos

```bash
# Compilar app y módulos afectados
./gradlew :app:assembleDebug :core:overlay:assembleDebug

# Pruebas JVM de interceptor, estado, coordinador y receptores (Robolectric)
./gradlew :core:accessibility:testDebugUnitTest :core:overlay:testDebugUnitTest :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug
```

## Escenario 1 — Ajustes de Accesibilidad

1. Activar la protección.
2. Abrir Ajustes → Accesibilidad.
3. Intentar tocar el servicio de BlockBuster.

**Resultado esperado**:

- El overlay de protección aparece antes de que se pueda operar la pantalla.
- No hay botón de desbloqueo.
- Al salir a una pantalla normal, el overlay se retira.

## Escenario 2 — Información de la aplicación

1. Abrir Ajustes → Aplicaciones → BlockBuster.
2. Intentar detener, borrar datos o desinstalar.

**Resultado esperado**:

- El overlay de protección cubre la pantalla y bloquea los toques.
- Al abandonar la información de la app, el overlay se retira.

## Escenario 3 — Administradores de dispositivo

1. Abrir Ajustes → Seguridad → Administradores de dispositivo.
2. Entrar en BlockBuster.

**Resultado esperado**:

- El overlay de protección aparece.
- No se permite interactuar con la opción de desactivar mientras la pantalla
  permanezca en primer plano.

## Escenario 4 — Intento de desactivar el administrador

1. Solicitar la desactivación del administrador.
2. Confirmar el diálogo del sistema.
3. Revisar logs/estado de protección.

**Resultado esperado**:

- `onDisableRequested` muestra la advertencia y publica una amenaza de alta
  prioridad.
- Se intenta desplegar el overlay.
- Si el sistema completa la desactivación, `onDisabled` registra el cambio y no
  se produce un bucle.
- La app no afirma haber impedido una acción que el sistema permite.

## Escenario 5 — Reinicio normal

1. Activar la protección.
2. Reiniciar el dispositivo con `adb reboot`.
3. Esperar el arranque.

**Resultado esperado**:

- `BOOT_COMPLETED` inicia `MonitoringForegroundService`.
- Se recalibran accesibilidad, superposición y administrador.
- El diagnóstico registra el resultado.

## Escenario 6 — Reinicio con almacenamiento bloqueado

1. Activar la protección.
2. Reiniciar y observar `LOCKED_BOOT_COMPLETED` antes del desbloqueo.

**Resultado esperado**:

- Se lee la copia boot-safe.
- Si está disponible y la protección está activa, se intenta arrancar el
  servicio; si no, se difiere a `BOOT_COMPLETED`.
- No se accede al DataStore principal antes del desbloqueo.

## Escenario 7 — Actualización de paquete

1. Activar la protección.
2. Reinstalar/actualizar la app con `adb install -r`.
3. Observar `MY_PACKAGE_REPLACED`.

**Resultado esperado**:

- La protección se rearma.
- El servicio se inicia si estaba activo.
- No se duplica el servicio ni el overlay.

## Escenario 8 — Protección inactiva

1. Desactivar la protección.
2. Reiniciar o actualizar la app.

**Resultado esperado**:

- El receptor comprueba el estado y **no** inicia el servicio.
- No se muestra overlay.

## Escenario 9 — Permisos faltantes o restricciones Android 14+

1. Revocar superposición o impedir el inicio del FGS desde background.
2. Provocar una pantalla sensible o un reinicio.

**Resultado esperado**:

- No hay cierre inesperado ni bucle de reintentos.
- El fallo queda registrado en diagnóstico.
- La app respeta las restricciones del sistema.

## Escenario 10 — Salida de la pantalla sensible

1. Mostrar el overlay de protección en una pantalla sensible.
2. Pulsar inicio o navegar a una pantalla no sensible.

**Resultado esperado**:

- El overlay se retira.
- La lógica normal de reglas vuelve a evaluarse.
- No queda una ventana huérfana.

## Referencias

- Modelo de datos: [`data-model.md`](data-model.md)
- Contratos: [`contracts/hardening-contracts.md`](contracts/hardening-contracts.md)
- Investigación: [`research.md`](research.md)

## Resultados de la implementación (2026-11-05)

Validación automatizada ejecutada:

| Comando | Resultado |
|---|---|
| `./gradlew :core:accessibility:testDebugUnitTest` | PASS — 5 pruebas del interceptor |
| `./gradlew :core:overlay:testDebugUnitTest` | PASS — 34 pruebas |
| `./gradlew :core:database:testDebugUnitTest` | PASS — 23 pruebas |
| `./gradlew :app:testDebugUnitTest` | PASS — 5 pruebas de receptores |
| `./gradlew :app:assembleDebug` | PASS — APK generado |
| `./gradlew :app:lintDebug` | PASS — sin errores |

Se verificó el manifiesto fusionado: `BootReceiver` con los tres intents y
`directBootAware`, `BlockerDeviceAdminReceiver` con `BIND_DEVICE_ADMIN` y
`device_admin_policies.xml` con `force-lock` y `limit-password`.

**Pendiente de validación manual** (requiere dispositivo/emulador): activar
administrador, abrir Ajustes reales, probar `LOCKED_BOOT_COMPLETED`, revocar
permisos y comprobar restricciones Android 14+ de FGS/overlay. No hay
dispositivo conectado en este entorno.

**Limitaciones conocidas**:

- Android puede remover el administrador y apagar la accesibilidad; la app
  responde con advertencia/overlay, pero no puede impedirlo de forma absoluta.
- Las clases de Ajustes varían por fabricante; la detección es heurística y
  puede no cubrir el 100 % de las variantes.
- El overlay de protección no es desbloqueable mientras la pantalla sensible
  esté en primer plano y se retira al salir.
- `LOCKED_BOOT_COMPLETED` usa la copia boot-safe o difiere a `BOOT_COMPLETED`.
- Android 14+ puede bloquear FGS/overlays desde background; se registra el fallo
  sin bucles.
