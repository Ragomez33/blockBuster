# Contract: Tabs, Focus Mode & Premium

**Feature**: `007-tabs-focus-premium`
**Type**: Contratos internos de UI, repositorios y motor de bloqueo
**Date**: 2026-11-06

Este contrato fija las interfaces entre navegación, ViewModels, persistencia y
el coordinador de overlay. No expone APIs fuera de la aplicación.

## 1. Navegación

```text
tabs/blocking   -> BlockingScreen
tabs/blocklist  -> BlocklistScreen
tabs/settings   -> SettingsScreen
subscription    -> SubscriptionScreen
settings/privacy -> PrivacyPolicyScreen
```

**Contrato**

- `MainTabsScaffold` contiene `NavigationBar` con tres ítems y el FAB global de
  Focus Mode.
- La pestaña inicial es `tabs/blocking`.
- Cambiar de pestaña usa `saveState`/`restoreState` y `launchSingleTop`.
- El FAB de Focus Mode está visible en las tres pestañas.
- `subscription` y `settings/privacy` se abren fuera de la barra inferior y
  permiten volver a la pestaña anterior.
- El grafo de pestañas solo se monta si el onboarding está completo.

## 2. BlocklistRepository

```kotlin
interface BlocklistRepository {
    fun observeEntries(): Flow<List<BlocklistEntry>>
    fun observeEntries(
        listType: BlocklistType,
        targetType: BlocklistTargetType,
    ): Flow<List<BlocklistEntry>>
    suspend fun addEntry(draft: BlocklistEntryDraft): AppResult<Long>
    suspend fun deleteEntry(id: Long): AppResult<Unit>
}
```

**Contrato**

- `addEntry` valida valor no vacío, formato y duplicado.
- Duplicado exacto en `(value, targetType, listType)` devuelve
  `AppResult.Failure(AppError.Conflict)` sin insertar.
- `deleteEntry` devuelve `AppResult.Failure(AppError.NotFound)` si el id no
  existe.
- Las lecturas son reactivas y reflejan inserciones/eliminaciones sin reiniciar.
- La lista blanca tiene prioridad de evaluación; el repositorio no cambia el
  orden de las entradas.

## 3. BlocklistMatcher (core/overlay)

```kotlin
interface BlocklistMatcher {
    fun isWhitelisted(
        packageName: String?,
        target: String?,
        className: String?,
        entries: List<BlocklistEntry>,
    ): Boolean

    fun blacklistRules(entries: List<BlocklistEntry>): List<BlockRule>
}
```

**Contrato**

- `isWhitelisted` evalúa solo entradas `WHITELIST`.
- `blacklistRules` convierte entradas `BLACKLIST`:
  - `APP` -> `BlockRuleType.PACKAGE`
  - `WEBSITE` -> `BlockRuleType.DOMAIN`
  - `KEYWORD` -> `BlockRuleType.KEYWORD`
- Los IDs sintéticos son negativos y estables para no colisionar con Room.
- Si la whitelist coincide, el coordinador no evalúa reglas, presets ni Focus.

## 4. FocusSessionRepository

```kotlin
interface FocusSessionRepository {
    fun observeSession(): Flow<FocusSession>
    suspend fun start(workMinutes: Int, breakMinutes: Int): AppResult<Unit>
    suspend fun pause(): AppResult<Unit>
    suspend fun resume(): AppResult<Unit>
    suspend fun stop(): AppResult<Unit>
    suspend fun saveDurations(workMinutes: Int, breakMinutes: Int): AppResult<Unit>
}
```

**Contrato**

- `start` valida `workMinutes` (1–120) y `breakMinutes` (1–60); si falla, no
  modifica la sesión.
- `pause` solo es válido desde `RUNNING`; `resume` solo desde `PAUSED`.
- `stop` deja `IDLE` y limpia campos temporales.
- El repositorio guarda marcas de tiempo absolutas para restaurar la sesión.
- La UI calcula el tiempo restante a partir de la sesión, sin escribir en
  DataStore en cada tick.

## 5. FocusSessionCoordinator

```kotlin
@Singleton
class FocusSessionCoordinator {
    fun start()
}
```

**Contrato**

- `start()` es idempotente.
- Tick aproximado de 1 segundo mientras haya sesión `RUNNING`.
- Al expirar `WORK`, persiste `BREAK`; al expirar `BREAK`, persiste `WORK`.
- Al reanudar tras un reinicio, calcula las transiciones vencidas usando las
  marcas de tiempo guardadas.
- Se arranca desde `MonitoringForegroundService` y desde la raíz de la app.
- No cancela la sesión si la app pasa a segundo plano.

## 6. PremiumRepository

```kotlin
interface PremiumRepository {
    fun observePremiumState(): Flow<IsPremiumState>
    fun observeSettings(): Flow<PremiumSettings>
    suspend fun setPremium(premium: Boolean): AppResult<Unit>
    suspend fun setStrictShortsReels(enabled: Boolean): AppResult<Unit>
    suspend fun setFrictionWindowMillis(value: Long): AppResult<Unit>
    suspend fun setAccountabilityPartner(value: String?): AppResult<Unit>
}
```

**Contrato**

- Estado por defecto: Free.
- Los setters de ajustes avanzados solo persisten si el estado es Premium; si no,
  devuelven un error controlado de permiso.
- `setPremium(true)` es la única operación que activa Premium en esta iteración.
- Si la lectura de DataStore falla, el estado efectivo es Free.
- La compra simulada llama a `setPremium(true)`.

## 7. Integración con OverlayCoordinator

```kotlin
combine(
    eventBus.currentDetection,
    activeRules,
    timer,
    overlayController.isOverlayVisible,
    blocklistEntries,
    focusSession,
) { ... }
```

**Contrato de evaluación**

1. Si `blocklistMatcher.isWhitelisted(...)` -> `Idle`; ocultar overlay.
2. Reglas efectivas = reglas activas + blacklist rules + focus rules (si
   `FocusSession` está en `WORK`/`RUNNING`).
3. Evaluar con `BlockRuleMatcher` incluyendo `packageName` y `className`.
4. Aplicar `FrictionConfig.frictionWindowMillis` desde Premium si corresponde;
   en caso contrario, 60 s.
5. Auditar una sola activación por objetivo.

## 8. UI Contracts

### BlockingScreen

- Secciones: `Content Blocking`, `Advanced Blocking`, `Customize Blocked screen`.
- `Content Blocking` conserva los tres switches de presets existentes.
- `Advanced Blocking` y `Customize Blocked screen` muestran `PremiumGate` si la
  cuenta es Free.
- Pulsar una opción Premium envía el evento `OpenSubscription(feature)`.
- Los switches maestros no se ven afectados por el estado Premium.

### BlocklistScreen

- Pestañas secundarias: `Apps` y `Sites & Keywords`.
- Selector `Blacklist` / `Whitelist`.
- FAB de adición visible.
- Estado vacío con acción de añadir.
- Entrada inválida o duplicada muestra mensaje y no cierra la pantalla.

### SettingsScreen

- Muestra `Free` o `Premium`.
- Acciones: abrir suscripción, buscar actualizaciones, abrir privacidad, abrir
  soporte.
- "Buscar actualizaciones" muestra un resultado local simulado.
- Privacidad abre `PrivacyPolicyScreen`.
- Soporte abre un diálogo/hoja con FAQ y opción de contacto local.

### FocusModeSheet

- Visible desde el FAB global.
- Muestra fase, tiempo restante, ciclos completados y controles
  Iniciar/Pausar/Reanudar/Detener.
- Permite editar duraciones de trabajo y descanso con validación.
- No bloquea la navegación por pestañas.

### SubscriptionScreen

- Muestra beneficio, precio simulado y estado actual.
- Botón "Suscribirse" ejecuta la compra simulada.
- Botón "Volver" no cambia el estado.
- Tras la compra, vuelve a la pantalla anterior y las opciones quedan
  desbloqueadas por el flujo reactivo.
- Si ya es Premium, muestra el estado y no permite duplicar la compra.

## 9. Textos requeridos

Recursos nuevos en `app/src/main/res/values/strings.xml`:

- `tab_blocking`, `tab_blocklist`, `tab_settings`
- `blocking_section_content`, `blocking_section_advanced`, `blocking_section_customize`
- `premium_locked`, `premium_crown`, `premium_subscribe`, `premium_price`
- `blocklist_tab_apps`, `blocklist_tab_sites_keywords`, `blocklist_blacklist`, `blocklist_whitelist`
- `blocklist_add`, `blocklist_empty`, `blocklist_duplicate`, `blocklist_invalid_app`
- `focus_mode_title`, `focus_work`, `focus_break`, `focus_start`, `focus_pause`, `focus_resume`, `focus_stop`, `focus_cycles`
- `focus_error_duration`
- `settings_account`, `settings_free`, `settings_premium`, `settings_check_updates`, `settings_privacy`, `settings_support`
- `settings_privacy_title`, `settings_support_title`, `settings_update_result`
- `subscription_title`, `subscription_description`, `subscription_success`
