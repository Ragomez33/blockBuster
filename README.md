# BlockBuster

Aplicación Android nativa para bloquear dominios y palabras clave mediante un
`AccessibilityService`, una pantalla de fricción superpuesta y reglas
persistidas localmente.

## Stack

- Kotlin 2.4.21, AGP 9.5, Gradle 9.6
- Jetpack Compose + Material 3
- Hilt, Room, DataStore Preferences
- Kotlin Coroutines + Flow
- Clean Architecture multi-módulo

## Módulos

| Módulo | Responsabilidad |
|---|---|
| `:app` | Entry point, navegación Compose, Hilt raíz, FGS y receivers |
| `:core:common` | Contratos, modelos, `AppResult`, bus de eventos |
| `:core:accessibility` | `AccessibilityService` reactivo y desacoplado |
| `:core:overlay` | `WindowManager` overlay + pantalla de fricción Compose |
| `:core:database` | Room, DataStore y repositorio de reglas |

## Modelo de reglas

- `BlockRule`: `id`, `pattern`, `type` (`DOMAIN`, `KEYWORD_SHORTS`,
  `KEYWORD_REELS`), `isEnabled`, `createdAt`.
- `BlockLog`: `id`, `ruleId`, `target`, `timestamp`; se conserva aunque se
  elimine la regla.
- Room `AppDatabase` versión 2, esquema exportado en `core/database/schemas`.
- La migración es destructiva en esta etapa pre-release; debe sustituirse por
  una migración explícita antes de publicar.
- La evaluación de reglas `KEYWORD_*` sobre contenido de pantalla aún no está
  implementada; solo el puente `DOMAIN` se aplica en el overlay.

## Coordinador de bloqueos

- `OverlayCoordinator` combina `DetectionSignal`, reglas activas, temporizador y
  visibilidad del overlay para decidir el bloqueo.
- `DefaultBlockRuleMatcher`: dominio por host/subdominio y keywords por
  contención, sin distinguir mayúsculas; precedencia por menor `id`.
- El overlay usa `TYPE_APPLICATION_OVERLAY`, pantalla completa, consumo táctil y
  owners de lifecycle/saved-state propios.
- Cada activación genera un único registro de auditoría; los eventos repetidos
  no duplican logs.
- La extracción de URL/texto desde accesibilidad es best-effort.
- Home/Recientes no se pueden suprimir de forma absoluta con un overlay normal;
  se requiere kiosco o device owner.

## Pantalla de fricción

- Espera estricta de 60 segundos y transcripción exacta de un párrafo fijo de
  300 palabras.
- El botón de desbloqueo solo se habilita con tiempo 0 y coincidencia exacta.
- Anti-paste nativo: toolbar no-op, selección colapsada, limpieza de
  portapapeles y detección de inserciones de más de 3 caracteres.
- Sin cierre inmediato; el botón atrás se consume.
- `FrictionSession` se retiene en `OverlayManager` para conservar tiempo y texto
  ante rotaciones/recreación del host.
- El desbloqueo se emite a `OverlayCoordinator` mediante `unlockEvents`.

## Hardening del sistema

- `BlockerDeviceAdminReceiver` con políticas `force-lock` y `limit-password`.
- `onDisableRequested` advierte y emite una amenaza de alta prioridad;
  `onDisabled` registra el cambio. Android puede completar la remoción: no se
  promete bloquearla de forma absoluta.
- `SettingsInterceptor` detecta Ajustes de Accesibilidad, información de app y
  administradores de dispositivo.
- `ProtectionEventBus` alimenta un overlay de protección no desbloqueable que se
  retira al salir de la pantalla sensible.
- `BootReceiver` escucha `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` y
  `MY_PACKAGE_REPLACED`; arranca el monitoreo solo si la protección estaba
  activa.
- Estado principal en DataStore + copia boot-safe en almacenamiento protegido
  por dispositivo.
- Android 14+ se maneja con diagnóstico y sin evadir restricciones de FGS/overlay.

## Documentación

- Especificación de reglas: [`specs/001-block-rules-persistence/spec.md`](specs/001-block-rules-persistence/spec.md)
- Plan de reglas: [`specs/001-block-rules-persistence/plan.md`](specs/001-block-rules-persistence/plan.md)
- Especificación del coordinador: [`specs/002-overlay-block-coordinator/spec.md`](specs/002-overlay-block-coordinator/spec.md)
- Plan del coordinador: [`specs/002-overlay-block-coordinator/plan.md`](specs/002-overlay-block-coordinator/plan.md)
- Especificación de fricción: [`specs/003-friction-anti-paste/spec.md`](specs/003-friction-anti-paste/spec.md)
- Plan de fricción: [`specs/003-friction-anti-paste/plan.md`](specs/003-friction-anti-paste/plan.md)
- Especificación de hardening: [`specs/004-system-hardening/spec.md`](specs/004-system-hardening/spec.md)
- Plan de hardening: [`specs/004-system-hardening/plan.md`](specs/004-system-hardening/plan.md)

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :core:overlay:testDebugUnitTest
./gradlew :core:database:testDebugUnitTest
./gradlew :app:lintDebug
```

Requiere JDK 17+ y Android SDK 37. El archivo `local.properties` no se versiona.
