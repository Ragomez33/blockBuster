# Tasks: Hardening del sistema y prevención de desinstalación

**Input**: Design documents from `specs/004-system-hardening/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/hardening-contracts.md`, `quickstart.md`

**Tests**: Incluidos porque `plan.md` (Phase F) y `quickstart.md` los requieren
para validar interceptor, amenazas, estado, receptores y coordinador.

**Organization**: Tareas agrupadas por historia de usuario para permitir
implementación y validación independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia de usuario (`US1`, `US2`, `US3`)
- Todas las tareas incluyen rutas de archivo exactas

## Path Conventions

- Modelos/contratos: `core/common/src/main/kotlin/com/blockbuster/core/common/`
- Accesibilidad: `core/accessibility/src/main/kotlin/com/blockbuster/core/accessibility/`
- Base de datos: `core/database/src/main/kotlin/com/blockbuster/core/database/`
- Overlay: `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/`
- App: `app/src/main/kotlin/com/blockbuster/app/`
- Recursos: `app/src/main/res/xml/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencias de prueba para receptores y estado.

- [X] T001 [P] Agregar `testImplementation(libs.robolectric)` y `testImplementation(libs.androidx.test.core)` en `app/build.gradle.kts` para probar `BootReceiver`/DeviceAdmin con Robolectric
- [X] T002 [P] Configurar `testOptions { unitTests.isIncludeAndroidResources = true }` y los `jvmArgs --add-opens` de JDK 25 en `app/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Modelos, bus de amenazas, estado de protección y contrato de overlay.

**⚠️ CRITICAL**: No iniciar US1, US2 ni US3 hasta completar esta fase.

- [X] T003 [P] Crear `SensitiveSystemScreen` con `category`, `packageName`, `className`, `detectedAtEpochMillis` en `core/common/src/main/kotlin/com/blockbuster/core/common/model/SensitiveSystemScreen.kt`
- [X] T004 [P] Crear `ProtectionThreat` y `ProtectionThreatType` (`SENSITIVE_SCREEN`, `ADMIN_DISABLE_REQUESTED`, `ADMIN_DISABLED`) en `core/common/src/main/kotlin/com/blockbuster/core/common/model/ProtectionThreat.kt`
- [X] T005 [P] Crear `ProtectionState` con `enabled`, `lastUpdatedEpochMillis` y `source` en `core/common/src/main/kotlin/com/blockbuster/core/common/model/ProtectionState.kt`
- [X] T006 [P] Crear `ProtectionEventBus` con `events: SharedFlow<ProtectionThreat>`, `currentThreat: StateFlow<ProtectionThreat?>` y `publish/clear` en `core/common/src/main/kotlin/com/blockbuster/core/common/event/ProtectionEventBus.kt`
- [X] T007 [P] Crear el contrato `ProtectionStateRepository` con lectura/observación de estado principal y boot-safe en `core/common/src/main/kotlin/com/blockbuster/core/common/contract/ProtectionStateRepository.kt`
- [X] T008 [P] Implementar `ProtectionStateRepositoryImpl` con DataStore principal y DataStore sobre `createDeviceProtectedStorageContext()` en `core/database/src/main/kotlin/com/blockbuster/core/database/protection/ProtectionStateRepositoryImpl.kt`
- [X] T009 [P] Añadir `FrictionMode` (`STANDARD`, `PROTECTION`), `mode` a `FrictionConfig` y `OverlayState.ProtectionBlocking(screen)` en `core/common/src/main/kotlin/com/blockbuster/core/common/contract/OverlayController.kt` y `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayState.kt`
- [X] T010 Actualizar los módulos Hilt en `core/database/src/main/kotlin/com/blockbuster/core/database/di/DatabaseModule.kt` y `app/src/main/kotlin/com/blockbuster/app/di/AppModule.kt` para proveer `ProtectionEventBus`, `ProtectionStateRepository` y sus bindings

**Checkpoint**: Contratos, estado y bus listos; las historias pueden comenzar.

---

## Phase 3: User Story 1 - Bloquear pantallas del sistema sensibles (Priority: P1) 🎯 MVP

**Goal**: Detectar Ajustes de Accesibilidad, información de app y administradores;
mostrar un overlay de protección no desbloqueable y retirarlo al salir.

**Independent Test**: Abrir cada pantalla sensible y comprobar el overlay; salir
a una pantalla normal y comprobar que se retira.

### Tests for User Story 1

- [X] T011 [P] [US1] Crear pruebas de clasificación de accesibilidad, app info, device admin, paquetes de fabricante y pantallas no sensibles en `core/accessibility/src/test/kotlin/com/blockbuster/core/accessibility/settings/DefaultSettingsInterceptorTest.kt`
- [X] T012 [P] [US1] Crear pruebas del coordinador para amenaza prioritaria, `ProtectionBlocking`, salida de pantalla y no duplicación en `core/overlay/src/test/kotlin/com/blockbuster/core/overlay/OverlayCoordinatorProtectionTest.kt`

### Implementation for User Story 1

- [X] T013 [US1] Implementar `SettingsInterceptor` y `DefaultSettingsInterceptor` con heurísticas de paquete/clase en `core/accessibility/src/main/kotlin/com/blockbuster/core/accessibility/settings/`
- [X] T014 [US1] Integrar el interceptor en `BlockBusterAccessibilityService` para publicar/limpiar `ProtectionThreat(SENSITIVE_SCREEN)` en cada cambio de ventana en `core/accessibility/src/main/kotlin/com/blockbuster/core/accessibility/BlockBusterAccessibilityService.kt`
- [X] T015 [US1] Implementar el modo `PROTECTION` en `FrictionScreen` (bloqueo oscuro, sin timer, campo ni botón de desbloqueo) en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/FrictionScreen.kt`
- [X] T016 [US1] Añadir al `OverlayCoordinator` el colector prioritario de `currentThreat`, la publicación de `ProtectionBlocking` y el retiro al limpiar la amenaza en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayCoordinator.kt`
- [X] T017 [US1] Propagar `FrictionMode` desde `OverlayManager`/`FrictionConfig` y asegurar que el overlay de protección no se confunde con el bloqueo normal en `core/overlay/src/main/kotlin/com/blockbuster/core/overlay/OverlayManager.kt`
- [X] T018 [US1] Ejecutar `./gradlew :core:accessibility:testDebugUnitTest :core:overlay:testDebugUnitTest :app:assembleDebug` y corregir fallos de US1

**Checkpoint**: US1 funcional y testeable de forma independiente. Es el MVP.

---

## Phase 4: User Story 2 - Responder a la desactivación del administrador (Priority: P2)

**Goal**: Advertir y signalizar intentos de desactivación del administrador, sin
prometer un bloqueo absoluto de la remoción.

**Independent Test**: Solicitar la desactivación del administrador y comprobar
advertencia/señal; confirmar la desactivación y comprobar el registro/estado.

### Tests for User Story 2

- [X] T019 [P] [US2] Crear pruebas Robolectric de `onDisableRequested`/`onDisabled`/`onEnabled` y emisión de amenazas en `app/src/test/kotlin/com/blockbuster/app/admin/BlockerDeviceAdminReceiverTest.kt`

### Implementation for User Story 2

- [X] T020 [US2] Crear `res/xml/device_admin_policies.xml` con `force-lock` y `limit-password` en `app/src/main/res/xml/device_admin_policies.xml`
- [X] T021 [US2] Crear `BlockerDeviceAdminReceiver` con EntryPoint Hilt, advertencia, amenazas de alta prioridad y actualización de estado; eliminar/reemplazar `BlockBusterDeviceAdminReceiver.kt` en `app/src/main/kotlin/com/blockbuster/app/admin/`
- [X] T022 [US2] Actualizar `app/src/main/AndroidManifest.xml` con `BlockerDeviceAdminReceiver`, permiso `BIND_DEVICE_ADMIN`, metadata `@xml/device_admin_policies` y `exported="true"`
- [X] T023 [US2] Ejecutar `./gradlew :app:testDebugUnitTest :app:assembleDebug` y corregir fallos de US2

**Checkpoint**: US1 y US2 funcionan; el administrador responde a la desactivación.

---

## Phase 5: User Story 3 - Reiniciar la protección de forma autónoma (Priority: P3)

**Goal**: Rearmar la monitorización tras boot, boot bloqueado o actualización solo
si la protección estaba activa.

**Independent Test**: Activar protección, reiniciar/actualizar y comprobar el
servicio; desactivar protección y comprobar que no arranca.

### Tests for User Story 3

- [X] T024 [P] [US3] Crear pruebas de `ProtectionStateRepository` principal/boot-safe y mirroring en `core/database/src/test/kotlin/com/blockbuster/core/database/protection/ProtectionStateRepositoryTest.kt`
- [X] T025 [P] [US3] Crear pruebas Robolectric de `BootReceiver` para los tres intents, protección activa/inactiva y fallo de arranque en `app/src/test/kotlin/com/blockbuster/app/receiver/BootReceiverTest.kt`

### Implementation for User Story 3

- [X] T026 [US3] Implementar `ProtectionHealthChecker` y `ProtectionHealth` para recalibrar accesibilidad, overlay y device admin en `app/src/main/kotlin/com/blockbuster/app/protection/ProtectionHealthChecker.kt`
- [X] T027 [US3] Reescribir `BootReceiver` con `goAsync`, lectura del estado, arranque condicional de `MonitoringForegroundService`, recalibración y manejo de `ForegroundServiceStartNotAllowedException` en `app/src/main/kotlin/com/blockbuster/app/receiver/BootReceiver.kt`
- [X] T028 [US3] Actualizar `app/src/main/AndroidManifest.xml` para `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` y `android:directBootAware="true"`
- [X] T029 [US3] Ejecutar `./gradlew :core:database:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` y corregir fallos de US3

**Checkpoint**: Las tres historias funcionan de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentación, validación integral y limitaciones del sistema.

- [X] T030 [P] Actualizar `README.md` y `specs/004-system-hardening/quickstart.md` con el flujo final, comandos y limitaciones (admin removal, Android 14+, OEM)
- [X] T031 Ejecutar `./gradlew :core:accessibility:testDebugUnitTest :core:overlay:testDebugUnitTest :core:database:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` y corregir fallos restantes
- [X] T032 [P] Verificar `app/src/main/res/xml/device_admin_policies.xml`, el manifiesto y que no queden referencias al receiver antiguo
- [X] T033 [P] Documentar en `specs/004-system-hardening/quickstart.md` que la validación manual en dispositivo de device admin, accesibilidad, boot/locked-boot y Android 14+ queda pendiente por falta de dispositivo/emulador

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup y bloquea las historias.
- **US1 (Phase 3)**: depende de Phase 2. Es el MVP.
- **US2 (Phase 4)**: depende de Phase 2; puede ejecutarse en paralelo con US1.
- **US3 (Phase 5)**: depende de Phase 2 y del estado de protección; puede ejecutarse en paralelo con US1/US2.
- **Polish (Phase 6)**: depende de las historias deseadas.

### User Story Dependencies

- **US1 (P1)**: sin dependencia de otras historias; usa interceptor/bus/coordinador.
- **US2 (P2)**: sin dependencia funcional de US1; emite amenazas que el coordinador consume.
- **US3 (P3)**: sin dependencia funcional de US1/US2; usa `ProtectionStateRepository` y FGS.

### Within Each User Story

- Tests antes de la implementación correspondiente cuando se trabaje en TDD.
- Modelos/contratos foundationales ya completos.
- Interceptor antes de integrarlo en el servicio.
- Pantalla/coordinador antes de validar US1.
- Receiver/manifest antes de las pruebas de receptor.
- Validación de build al final de cada historia.

---

## Parallel Execution Examples

### User Story 1

```text
T011 DefaultSettingsInterceptorTest.kt
T012 OverlayCoordinatorProtectionTest.kt
```

### User Story 2

```text
T019 BlockerDeviceAdminReceiverTest.kt
```

### User Story 3

```text
T024 ProtectionStateRepositoryTest.kt
T025 BootReceiverTest.kt
```

### Foundational

```text
T003 SensitiveSystemScreen.kt
T004 ProtectionThreat.kt
T005 ProtectionState.kt
T006 ProtectionEventBus.kt
T007 ProtectionStateRepository.kt
T008 ProtectionStateRepositoryImpl.kt
T009 FrictionMode/OverlayState.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 (Setup).
2. Completar Phase 2 (Foundational).
3. Completar Phase 3 (US1): interceptor, overlay de protección y coordinador.
4. **STOP and VALIDATE**: pruebas de US1 y `:app:assembleDebug`.
5. Entregar/demostrar el MVP.

### Incremental Delivery

1. Setup + Foundational → modelos, bus y estado listos.
2. US1 → bloqueo de pantallas sensibles (MVP).
3. US2 → respuesta al administrador.
4. US3 → arranque autónomo y salud.
5. Polish → documentación, lint y validación manual.

### Parallel Team Strategy

1. Equipo completa Setup + Foundational.
2. Luego:
   - Developer A: US1 (interceptor + overlay prioritario).
   - Developer B: US2 (DeviceAdmin).
   - Developer C: US3 (Boot + estado + salud).
3. Integrar al final y ejecutar Phase 6.

---

## Notes

- `[P]` indica archivos distintos y ausencia de dependencias pendientes.
- Los tests se incluyen porque el plan y el quickstart los exigen.
- No se promete impedir de forma absoluta la remoción del administrador ni el apagado del servicio.
- El overlay de protección no ofrece desbloqueo y se retira al salir de la pantalla sensible.
- `LOCKED_BOOT_COMPLETED` usa la copia boot-safe; si no está disponible, se difiere a `BOOT_COMPLETED`.
- Android 14+ puede bloquear FGS/overlays desde background; se maneja con diagnóstico y sin bucles.
- No se modifican Room ni el esquema de reglas.
- Commit después de cada tarea o grupo lógico.
