# Implementation Plan: Asistente de Configuración Inicial (Onboarding Setup Wizard)

**Branch**: `005-onboarding-setup-wizard` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-onboarding-setup-wizard/spec.md`

## Summary

Añadir un asistente de configuración inicial en `:app` que bloquea el acceso al
panel principal hasta que los tres permisos críticos estén activos:
superposición, accesibilidad y administración del dispositivo. El estado se
evalúa de forma reactiva mediante un `StateFlow` en `OnboardingViewModel` +
`OnboardingRepository`, se refresca al volver a la actividad (`onResume`) y se
refleja en tres tarjetas Compose con acciones que abren los ajustes del sistema
con degradación segura ante intents no resolubles. No se añaden dependencias.

## Technical Context

**Language/Version**: Kotlin 2.4.21, JVM 17, Android Gradle Plugin 9.5.0

**Primary Dependencies**: Jetpack Compose (BOM 2026.09.00, Material 3), AndroidX Lifecycle 2.11.0, Hilt 2.60.1, Navigation Compose 2.10.2, Kotlin Coroutines 1.11.0. Sin dependencias nuevas.

**Storage**: N/A. El estado se deriva siempre de las APIs del sistema en tiempo de ejecución; no se persiste ningún indicador de "onboarding visto".

**Testing**: JUnit 4.13.2 + Robolectric 4.17 + AndroidX Test Core en `app/src/test`. Verificación manual de flujos de sistema en dispositivo/emulador mediante `quickstart.md`.

**Target Platform**: Android `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`.

**Project Type**: Aplicación móvil Android multi-módulo (`:app` consume `:core:common`, `:core:database`, `:core:accessibility`, `:core:overlay`).

**Performance Goals**: La reevaluación del estado debe completarse en < 100 ms y reflejarse en la UI en < 1 s tras `ON_RESUME` (SC-003). Sin trabajo en segundo plano continuo ni polling.

**Constraints**: Sin librerías de onboarding; Compose puro; los intents deben abrirse con `runCatching` y validación de resolución, con alternativas ante pantallas no disponibles; el panel principal nunca debe mostrarse con permisos pendientes.

**Scale/Scope**: Una pantalla nueva (`OnboardingScreen`), un ViewModel, un repositorio, un helper de intents, cambios de routing en `MainActivity`, cadenas de texto y pruebas unitarias. Aproximadamente 8–10 archivos tocados.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` contiene únicamente la plantilla sin ratificar
(marcadores `[PRINCIPLE_*]`, `[SECTION_*]`, `[GOVERNANCE_RULES]`). No existen
principios vinculantes que evaluar. Se aplican como gates las convenciones
observadas del proyecto:

| Gate | Resultado |
|------|-----------|
| Sin dependencias externas nuevas | PASS — solo APIs de plataforma y dependencias ya declaradas. |
| Respetar límites de módulos | PASS — toda la funcionalidad nueva vive en `:app`; consume contratos/componentes existentes de `:core:accessibility` y el receiver de `:app`. |
| Patrón de presentación existente | PASS — `@HiltViewModel` + `StateFlow` + `collectAsStateWithLifecycle`, igual que `MainDashboardViewModel`/`MainDashboardScreen`. |
| UI en Compose puro | PASS — Material 3 y Compose Foundation ya presentes. |
| Seguridad/estabilidad de intents | PASS — validación de resolución y `runCatching`, con fallbacks y mensaje al usuario. |
| Testing | PASS — pruebas Robolectric en `app/src/test`, coherentes con las existentes. |
| Simplicidad | PASS — sin persistencia, sin polling, sin máquina de estados adicional. |

No hay violaciones que justifiquen entrada en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/005-onboarding-setup-wizard/
├── plan.md              # Este archivo
├── research.md          # Fase 0
├── data-model.md        # Fase 1
├── quickstart.md        # Fase 1
├── contracts/
│   └── onboarding-ui-contract.md
└── tasks.md             # Fase 2 (/speckit.tasks, no creado aquí)
```

### Source Code (repository root)

```text
app/src/main/kotlin/com/blockbuster/app/
├── MainActivity.kt                         # MODIFICAR: routing raíz onboarding/dashboard
├── onboarding/
│   ├── OnboardingState.kt                  # NUEVO: estado derivado y estado de UI
│   ├── OnboardingRepository.kt             # NUEVO: evaluación de los tres permisos
│   ├── OnboardingIntents.kt                # NUEVO: construcción segura de intents
│   └── OnboardingViewModel.kt              # NUEVO: StateFlow + refresh
├── protection/
│   └── ProtectionHealthChecker.kt          # MODIFICAR: exponer comprobaciones reutilizables
└── ui/onboarding/
    └── OnboardingScreen.kt                 # NUEVO: asistente Compose de tres pasos

app/src/main/res/values/strings.xml          # MODIFICAR: textos del asistente y errores
app/src/test/kotlin/com/blockbuster/app/onboarding/
├── OnboardingRepositoryTest.kt             # NUEVO
├── OnboardingIntentsTest.kt                # NUEVO
└── OnboardingViewModelTest.kt              # NUEVO
```

**Structure Decision**: Se mantiene la estructura de aplicación única `:app`
con paquetes por responsabilidad (`onboarding` para dominio/estado,
`ui/onboarding` para presentación), replicando la separación ya usada en
`ui/home` y `ui/rules`. No se crea un módulo `:core:onboarding` porque el
asistente es específico de la app y no lo consumen otros módulos. La lógica de
comprobación de accesibilidad/administrador se reutiliza desde
`ProtectionHealthChecker` para evitar duplicar reglas de detección.

## Design Decisions (resumen)

1. **Estado inicial síncrono**: el repositorio calcula el estado en su
   constructor, de modo que `StateFlow` arranca con un valor real y nunca se
   muestra el dashboard durante un frame antes de la primera comprobación.
2. **Reactividad híbrida**: `refresh()` se invoca desde `MainActivity.onResume`
   (requisito explícito) y el repositorio combina ese disparador con el
   `StateFlow` de conexión de `AccessibilityEngine` para actualizar en caliente
   si el servicio se conecta o desconecta.
3. **Detección de accesibilidad robusta**: se comprueba el componente real
   `BlockBusterAccessibilityService` (nombre existente en
   `:core:accessibility`; el prompt lo llamaba `BlockerAccessibilityService`).
   Se valida contra la lista de servicios habilitados del
   `AccessibilityManager` y, como respaldo, contra
   `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` usando el
   `ComponentName` aplanado, no solo el nombre del paquete.
4. **Intents con fallback en cadena**: cada acción valida
   `resolveActivity(packageManager)` antes de lanzar y envuelve el lanzamiento
   en `runCatching`. Si el intent específico falla, se intenta una pantalla
   general equivalente; si todo falla, se emite un evento de error que la UI
   muestra en un `Snackbar`.
5. **Gating en la raíz**: `MainActivity` obtiene el `OnboardingViewModel` a
   nivel de actividad (`by viewModels()`), llama `refresh()` en `onResume()` y
   pasa el estado a un composable raíz que decide entre `OnboardingScreen` y
   `BlockBusterNavHost`. El destino inicial de la navegación es el dashboard de
   interruptores maestros definido en `006-preset-master-dashboard`.

## Phase 0: Research

Ver [research.md](./research.md). Todas las incógnitas del contexto técnico
quedaron resueltas; no quedan `NEEDS CLARIFICATION`.

## Phase 1: Design & Contracts

- Modelo de datos: [data-model.md](./data-model.md)
- Contrato de UI y acciones: [contracts/onboarding-ui-contract.md](./contracts/onboarding-ui-contract.md)
- Guía de validación: [quickstart.md](./quickstart.md)

## Post-Design Constitution Check

Tras el diseño se mantienen los siete gates en PASS: no se añadieron
dependencias, no se cruzaron límites de módulos, el estado sigue siendo
derivado y efímero, y las acciones del sistema están encapsuladas con
validación y manejo de errores. No hay complejidad adicional que justificar.

## Complexity Tracking

No aplica: no existen violaciones de la constitución ni de las convenciones del
proyecto.
