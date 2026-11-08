# Research: Onboarding Setup Wizard

**Feature**: `005-onboarding-setup-wizard`
**Date**: 2026-11-05
**Status**: Complete — no `NEEDS CLARIFICATION` remaining

## Research Tasks

| # | Unknown / Topic | Resolution |
|---|-----------------|------------|
| R1 | Cómo mantener el estado de permisos reactivo sin polling | StateFlow + refresh en `onResume` + flujo de conexión de accesibilidad |
| R2 | Cómo detectar de forma fiable el servicio de accesibilidad | `AccessibilityManager` + `Settings.Secure` con `ComponentName` completo |
| R3 | Cómo abrir ajustes del sistema sin cierres inesperados | Validación con `resolveActivity` + `runCatching` + fallbacks ordenados |
| R4 | Dónde aplicar el gating para no filtrar el dashboard | Composable raíz en `MainActivity`, estado inicial síncrono |
| R5 | Cómo evitar dependencias nuevas y respetar la arquitectura | Hilt + Compose + StateFlow existentes; sin módulos nuevos |
| R6 | Cómo verificar la funcionalidad | Robolectric para lógica/intents + quickstart manual para flujos del sistema |

---

## R1 — Estrategia de reactividad

**Decision**: `OnboardingRepository` expone un `StateFlow<OnboardingState>` cuyo
valor se recalcula:
1. en su construcción (valor inicial síncrono);
2. cuando `refresh()` es invocado desde `MainActivity.onResume()`;
3. cuando cambia el `StateFlow` de conexión de `AccessibilityEngine`, para
   reflejar en caliente la conexión/desconexión del servicio mientras la app
   está visible.

La UI consume el flujo con `collectAsStateWithLifecycle()`. No hay sondeo
periódico ni `ContentObserver`.

**Rationale**: Los permisos de superposición y administrador solo cambian
mediante pantallas del sistema, por lo que la vuelta a la actividad es el punto
natural de reevaluación. La accesibilidad sí puede cambiar de estado en caliente
y ya existe un flujo observable en el proyecto. La combinación cubre el
requisito de "evaluación continua" sin coste en segundo plano.

**Alternatives considered**:
- Polling cada N segundos: descartado por consumo y porque no aporta precisión
  frente a `ON_RESUME`.
- Broadcasts del sistema para cada permiso: no existen broadcasts públicos
  fiables para overlay ni para administrador de dispositivo.
- `ContentObserver` sobre `Settings.Secure`: limitado a accesibilidad, no cubre
  los otros dos permisos y añade complejidad.

---

## R2 — Detección del servicio de accesibilidad

**Decision**: Comprobar que el componente
`com.blockbuster.core.accessibility.BlockBusterAccessibilityService` está
habilitado comparando el `ComponentName` aplanado (`package/clase`) contra:
1. la lista devuelta por `AccessibilityManager.getEnabledAccessibilityServiceList(...)`;
2. el valor de `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.

Se reutiliza/expone la lógica de `ProtectionHealthChecker`, que ya implementa
ambas comprobaciones.

**Rationale**: Buscar solo el nombre del paquete (comportamiento actual de
respaldo) puede dar falsos positivos con otros servicios del mismo paquete y no
garantiza que el servicio concreto esté activo. Comparar el componente completo
es más preciso. El prompt original mencionaba `BlockerAccessibilityService`;
el componente realmente registrado en el manifiesto es
`BlockBusterAccessibilityService`, por lo que el plan usa el nombre real.

**Alternatives considered**:
- Usar solo `AccessibilityManager`: suficiente en la mayoría de casos, pero se
  mantiene el respaldo por `Settings.Secure` para dispositivos/OEM con
  comportamientos inconsistentes.
- Usar solo `Settings.Secure`: no refleja el estado efectivo de conexión.
- Usar `AccessibilityEngine.connectionState == CONNECTED`: mide conexión en
  proceso, no permiso concedido; se usa como señal reactiva adicional, no como
  fuente de verdad del permiso.

---

## R3 — Apertura segura de ajustes del sistema

**Decision**: Un helper `OnboardingIntents` construye cada intent y lo lanza con
esta secuencia:
1. comprobar `intent.resolveActivity(context.packageManager) != null`;
2. `context.startActivity(intent)` dentro de `runCatching`;
3. si falla o no resuelve, intentar un fallback;
4. si todos fallan, devolver un resultado de error que la UI muestra en
   `Snackbar`.

Fallbacks por paso:
- **Superposición**: `ACTION_MANAGE_OVERLAY_PERMISSION` con
  `package:<packageName>` → mismo action sin URI → `ACTION_MANAGE_APPLICATIONS_SETTINGS`
  → `ACTION_SETTINGS`.
- **Accesibilidad**: `ACTION_ACCESSIBILITY_SETTINGS` → `ACTION_SETTINGS`.
- **Administrador**: `ACTION_ADD_DEVICE_ADMIN` con `EXTRA_DEVICE_ADMIN` →
  `ACTION_SECURITY_SETTINGS` → `ACTION_SETTINGS`.

**Rationale**: `resolveActivity` evita `ActivityNotFoundException`; `runCatching`
protege frente a `SecurityException` u otras excepciones de OEM. La cadena de
fallbacks garantiza que el usuario siempre pueda llegar a una pantalla de
ajustes útil. `minSdk = 26` permite usar `ACTION_MANAGE_OVERLAY_PERMISSION` con
URI de paquete sin ramas por API.

**Alternatives considered**:
- Solo `runCatching` sin validar resolución: funcional, pero no permite
  distinguir "no existe" de "falló" ni elegir un fallback antes de intentar.
- Solo validar resolución: no cubre excepciones en tiempo de lanzamiento.
- Abrir siempre `ACTION_SETTINGS`: peor experiencia y no cumple el requisito de
  intent directo.

---

## R4 — Gating del dashboard

**Decision**: `MainActivity` obtiene `OnboardingViewModel` con `by viewModels()`,
llama `refresh()` en `onResume()` y renderiza un composable raíz que muestra
`OnboardingScreen` si `isComplete == false` y `BlockBusterNavHost` si es `true`.
El estado inicial del repositorio se calcula síncronamente en el constructor,
por lo que no hay frame con dashboard visible antes de la comprobación.

**Rationale**: El gating en la raíz es imposible de eludir desde la navegación
interna y cumple literalmente el requisito de condicionar la vista raíz. Un
estado inicial real evita el parpadeo del onboarding para usuarios que ya tienen
todo concedido.

**Alternatives considered**:
- Gate dentro de `MainDashboardScreen`: permitiría seguir navegando a otras rutas
  y no bloquea realmente el dashboard.
- Gate en `BlockBusterNavHost` con una ruta de onboarding: añade navegación y
  back-stack innecesarios para un estado que no debe poder saltarse.
- Persistir un flag "onboarding completado": se desincroniza si el usuario
  revoca permisos; el estado derivado es siempre correcto.

---

## R5 — Arquitectura y dependencias

**Decision**: Implementar todo en `:app`:
`OnboardingState` (modelo inmutable), `OnboardingRepository` (`@Singleton`,
`@ApplicationContext`), `OnboardingViewModel` (`@HiltViewModel`),
`OnboardingIntents` (helper sin estado) y `OnboardingScreen` (Compose).
Reutilizar `ProtectionHealthChecker` promoviendo sus comprobaciones a métodos
públicos. Sin dependencias nuevas.

**Rationale**: Es funcionalidad específica de la app y no de los módulos core.
Sigue el patrón existente de Hilt + StateFlow + Compose y evita duplicar la
lógica de detección ya probada.

**Alternatives considered**:
- Nuevo módulo `:core:onboarding`: sobredimensionado y no reutilizable por otros
  consumidores.
- Duplicar las comprobaciones en el repositorio: riesgo de divergencia con
  `ProtectionHealthChecker`.
- Usar `DataStore` para persistir estado: innecesario y propenso a quedar
  obsoleto frente al estado real del sistema.

---

## R6 — Estrategia de verificación

**Decision**:
- Pruebas unitarias Robolectric (`@Config(sdk = [36])`, como las existentes)
  para: cálculo de `isComplete`, transiciones de estado al refrescar, detección
  del componente de accesibilidad, construcción de intents (action, data, extra
  `ComponentName`) y rutas de fallback/error.
- Verificación manual guiada por `quickstart.md` para los flujos que dependen de
  pantallas reales del sistema (conceder/revocar permisos, volver a la app).
- `./gradlew :app:testDebugUnitTest` como comando de CI.

**Rationale**: Robolectric permite aislar la lógica y los intents sin dispositivo;
los cambios de permisos reales solo pueden validarse de extremo a extremo en un
dispositivo o emulador.

**Alternatives considered**:
- Pruebas instrumentadas Compose: requerirían `androidTest` y dependencias no
  declaradas; se evita para respetar "sin dependencias nuevas".
- Solo pruebas manuales: no protege contra regresiones de lógica ni de intents.
