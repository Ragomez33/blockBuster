# Quickstart: Validación de persistencia de reglas de bloqueo

**Feature**: `001-block-rules-persistence`  
**Prerequisites**: JDK 17+, Android SDK 37, `local.properties` con `sdk.dir`.

## Comandos

```bash
# 1. Compilar el módulo de base de datos y sus consumidores
./gradlew :core:database:assembleDebug :app:assembleDebug

# 2. Ejecutar pruebas JVM (DAOs + repositorio con Room en memoria)
./gradlew :core:database:testDebugUnitTest

# 3. Verificar lint y manifiestos
./gradlew :app:lintDebug
```

## Escenario 1 — Crear y observar reglas activas

1. Insertar una regla con `pattern = "example.com"`, `type = DOMAIN`,
   `isEnabled = true`.
2. Suscribirse a `observeActiveRules()`.
3. Insertar una segunda regla habilitada de tipo `KEYWORD_SHORTS`.
4. Deshabilitar la primera regla.

**Resultado esperado**:

- `insertRule` devuelve un ID mayor que 0.
- La primera emisión contiene las dos reglas habilitadas.
- Tras deshabilitar la primera, la siguiente emisión contiene solo la regla de
  tipo `KEYWORD_SHORTS`.
- La actualización llega a la suscripción en menos de un segundo.

## Escenario 2 — Filtrar reglas activas por tipo

1. Insertar reglas habilitadas de los tres tipos.
2. Insertar una regla `DOMAIN` deshabilitada.
3. Ejecutar `getActiveRulesByType(DOMAIN)`.

**Resultado esperado**:

- Solo se devuelve la regla `DOMAIN` habilitada.
- Las reglas de otros tipos y la deshabilitada no aparecen.

## Escenario 3 — Actualizar y eliminar reglas

1. Insertar una regla.
2. Actualizar su `pattern` y `isEnabled`.
3. Eliminarla por ID.
4. Intentar actualizar/eliminar el mismo ID otra vez.

**Resultado esperado**:

- La actualización devuelve `AppResult.Success(Unit)` y la regla refleja los
  nuevos valores.
- La eliminación devuelve éxito y la regla deja de emitirse.
- La segunda operación devuelve `AppResult.Failure(AppError.NotFound)`.

## Escenario 4 — Auditoría local

1. Insertar una regla y registrar un bloqueo con `ruleId`, `target` y
   `timestamp`.
2. Consultar `observeBlockLogs(ruleId)`.
3. Eliminar la regla.
4. Volver a consultar los logs del `ruleId` eliminado.

**Resultado esperado**:

- El log conserva el `ruleId`, `target` y `timestamp` originales.
- Tras eliminar la regla, el log sigue disponible.

## Escenario 5 — Errores controlados

1. Simular un fallo de almacenamiento en una operación de escritura.
2. Ejecutar una actualización o eliminación con un ID inexistente.

**Resultado esperado**:

- La escritura fallida devuelve `AppResult.Failure(AppError.Storage)`.
- La operación sobre ID inexistente devuelve `AppResult.Failure(AppError.NotFound)`.
- No se propaga una excepción no controlada al consumidor.

## Escenario 6 — Persistencia tras reinicio

1. Insertar reglas en la app instalada.
2. Cerrar y volver a abrir la aplicación.

**Resultado esperado**:

- Las reglas siguen presentes con los mismos IDs, patrones, tipos y estado.
- La UI las muestra en la pantalla de gestión.

## Escenario 7 — Consumidores

1. Abrir la pantalla de reglas y crear una regla con un patrón y un tipo.
2. Habilitar/deshabilitar la regla.
3. Abrir una app bloqueada (para reglas `DOMAIN` cuyo patrón coincida con el
   paquete en primer plano).

**Resultado esperado**:

- La lista se actualiza sin reiniciar la app.
- El switch y la eliminación funcionan por ID.
- El overlay muestra la pantalla de fricción según el puente temporal de
  `DOMAIN`.
- Las reglas de palabra clave quedan persistidas aunque su evaluación de
  contenido aún no esté implementada.

## Referencias

- Modelo de datos: [`data-model.md`](data-model.md)
- Contratos: [`contracts/block-rules-contracts.md`](contracts/block-rules-contracts.md)
- Investigación y decisiones: [`research.md`](research.md)

## Resultados de la implementación (2026-11-05)

Validación automatizada ejecutada:

| Comando | Resultado |
|---|---|
| `./gradlew :core:database:testDebugUnitTest` | PASS — 20 pruebas (DAOs, repositorio, flujos y auditoría) |
| `./gradlew :app:assembleDebug` | PASS — APK generado |
| `./gradlew :app:lintDebug` | PASS — sin errores de lint |

También se verificó la exportación de
`core/database/schemas/com.blockbuster.core.database.AppDatabase/2.json` con
las tablas `block_rules` y `block_logs` y sus índices.

**Pendiente de validación manual** (requiere dispositivo o emulador): los
escenarios 6 y 7 de persistencia tras reinicio y de interacción con la UI y el
overlay. La lógica de persistencia que los respalda está cubierta por las
pruebas JVM con Room en memoria.

**Limitaciones conocidas**:

- La migración es destructiva en pre-release; los datos legacy de la etapa
  anterior se descartan.
- Las reglas `KEYWORD_SHORTS` y `KEYWORD_REELS` se persisten, pero su evaluación
  contra contenido de pantalla queda para una iteración posterior.
- El overlay solo aplica el puente temporal `DOMAIN` cuando el patrón coincide
  exactamente con el paquete en primer plano.
