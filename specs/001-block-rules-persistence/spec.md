# Feature Specification: Persistencia y repositorio de reglas de bloqueo

**Feature Branch**: `N/A (no branch hook configured)`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "Implementar la capa de persistencia y repositorio de reglas de bloqueo en Room para el módulo `:core:database`.

### Componentes y Estructuras Requeridas
1. **Entidades Room (`entity/`)**:
   - `BlockRuleEntity`: Tabla con campos `id` (Long, Primary Key auto-generate), `pattern` (String, dominio o palabra clave), `type` (Enum: `DOMAIN`, `KEYWORD_SHORTS`, `KEYWORD_REELS`), `isEnabled` (Boolean), `createdAt` (Long).
   - `BlockLogEntity`: Tabla para auditoría local de bloqueos con `id`, `ruleId` (Long), `target` (String), `timestamp` (Long).
2. **DAOs (`dao/`)**:
   - `BlockRuleDao`: Métodos reactivos usando `Flow<List<BlockRuleEntity>>` para observar todas las reglas activas, además de operaciones `suspend fun insertRule`, `suspend fun updateRule`, `suspend fun deleteRule`, y `suspend fun getActiveRulesByType(type: String): List<BlockRuleEntity>`.
3. **Database (`AppDatabase.kt`)**:
   - Clase abstracta RoomDatabase con exportSchema = true (apuntando al directorio de schemas configurado previamente).
4. **Repositorio (`repository/`)**:
   - Implementar `BlockRulesRepositoryImpl` inyectado con Hilt, que exponga los datos mapeados a modelos de dominio limpios y devuelva resultados envueltos en `AppResult<T>` (éxito/error).

### Restricciones Técnicas
- Todas las consultas a base de datos deben ejecutarse estrictamente sobre `Dispatchers.IO`.
- No usar LiveData; todo debe exponerse exclusivamente mediante `kotlinx.coroutines.flow.Flow`."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Gestionar reglas de bloqueo persistentes (Priority: P1)

Una persona que configura BlockBuster necesita crear, actualizar, habilitar o
deshabilitar y eliminar reglas de bloqueo, y que esas reglas sigan existiendo
después de cerrar y volver a abrir la aplicación.

**Why this priority**: Sin reglas duraderas no es posible aplicar ningún
bloqueo. Es la base de toda la funcionalidad de control de pantalla.

**Independent Test**: Crear una regla con un patrón y un tipo, reiniciar la
aplicación y comprobar que la regla conserva sus valores. Repetir con una
actualización y con una eliminación.

**Acceptance Scenarios**:

1. **Given** que no existen reglas, **When** se crea una regla con patrón, tipo y estado, **Then** la operación devuelve éxito y la regla puede leerse con un identificador generado y los mismos valores.
2. **Given** una regla existente, **When** se actualizan su patrón, tipo o estado de habilitación, **Then** la operación devuelve éxito y las lecturas posteriores reflejan los nuevos valores.
3. **Given** una regla existente, **When** se elimina, **Then** la operación devuelve éxito y la regla deja de estar disponible.
4. **Given** reglas almacenadas, **When** la aplicación se reinicia, **Then** todas las reglas siguen disponibles con sus datos intactos.
5. **Given** un fallo del almacenamiento local, **When** se intenta crear, actualizar o eliminar una regla, **Then** se obtiene un resultado de error controlado y la aplicación no se bloquea.

---

### User Story 2 - Observar reglas activas en tiempo real (Priority: P2)

Un componente de monitoreo o superposición necesita recibir de forma reactiva
la lista de reglas activas para reaccionar a los cambios sin reiniciar la
aplicación ni consultar manualmente.

**Why this priority**: El bloqueo debe adaptarse en el momento en que la persona
activa, desactiva o modifica una regla. Una actualización retrasada produce
bloqueos incorrectos.

**Independent Test**: Suscribirse al flujo de reglas activas, crear o modificar
una regla y comprobar que la suscripción recibe la lista actualizada sin
reiniciar la aplicación. Repetir con una consulta filtrada por tipo.

**Acceptance Scenarios**:

1. **Given** una suscripción activa al flujo de reglas, **When** se inserta, actualiza o elimina una regla habilitada, **Then** la suscripción recibe una nueva lista en menos de un segundo.
2. **Given** reglas habilitadas y deshabilitadas, **When** se observa el flujo de reglas activas, **Then** la lista contiene únicamente las reglas habilitadas.
3. **Given** reglas de varios tipos, **When** se solicitan las reglas activas de un tipo concreto, **Then** solo se devuelven las reglas habilitadas de ese tipo.
4. **Given** que no existe ninguna regla activa, **When** se observa el flujo, **Then** se emite una lista vacía sin producir un error.

---

### User Story 3 - Auditar localmente los bloqueos (Priority: P3)

Una persona que revisa el comportamiento de BlockBuster necesita que cada
bloqueo quede registrado localmente con la regla aplicada, el elemento
afectado y el momento en que ocurrió.

**Why this priority**: La auditoría permite explicar por qué se bloqueó una
aplicación y facilita el diagnóstico. No es necesaria para el bloqueo en sí,
por eso queda después de las reglas y la observación reactiva.

**Independent Test**: Registrar un bloqueo asociado a una regla y comprobar que
el registro conserva el identificador de la regla, el objetivo y la marca de
tiempo después de reiniciar la aplicación.

**Acceptance Scenarios**:

1. **Given** una regla existente, **When** se registra un bloqueo, **Then** se persiste una entrada con el identificador de la regla, el objetivo bloqueado y la marca de tiempo.
2. **Given** una entrada de auditoría almacenada, **When** la aplicación se reinicia, **Then** la entrada sigue disponible con los mismos valores.
3. **Given** que se elimina una regla que tiene registros de auditoría, **When** se consultan los registros históricos, **Then** los registros anteriores se conservan.

---

### Edge Cases

- Qué ocurre cuando se intenta guardar una regla con un patrón vacío o compuesto solo por espacios?
- Qué ocurre cuando se repite el mismo patrón para el mismo tipo de regla?
- Qué ocurre cuando se intenta actualizar o eliminar una regla que ya no existe?
- Qué ocurre cuando se solicita un tipo de regla que no es uno de los tres tipos permitidos?
- Qué ocurre si el almacenamiento local falla, está corrupto o no puede abrirse?
- Qué ocurre si dos cambios sobre la misma regla se producen casi simultáneamente?
- Qué ocurre si se registra un bloqueo después de que su regla fue eliminada?
- Qué ocurre con patrones u objetivos extremadamente largos?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir crear una regla de bloqueo con un patrón, un tipo, un estado de habilitación y una marca de tiempo de creación.
- **FR-002**: El sistema MUST asignar un identificador único y auto-generado a cada regla creada.
- **FR-003**: El sistema MUST aceptar exclusivamente los tipos de regla `DOMAIN`, `KEYWORD_SHORTS` y `KEYWORD_REELS`.
- **FR-004**: El sistema MUST permitir actualizar el patrón, el tipo y el estado de habilitación de una regla existente.
- **FR-005**: El sistema MUST permitir eliminar una regla existente por su identificador.
- **FR-006**: El sistema MUST exponer un flujo reactivo con todas las reglas habilitadas, de modo que cada cambio se refleje sin reiniciar la aplicación.
- **FR-007**: El sistema MUST permitir obtener las reglas habilitadas de un tipo concreto.
- **FR-008**: El sistema MUST persistir cada regla de forma duradera para que sobreviva al cierre y reinicio de la aplicación.
- **FR-009**: El sistema MUST persistir registros de auditoría de bloqueos con el identificador de la regla, el objetivo afectado y la marca de tiempo.
- **FR-010**: El sistema MUST conservar los registros de auditoría aunque la regla asociada se elimine.
- **FR-011**: El sistema MUST entregar los datos a los consumidores como modelos de dominio limpios, sin exponer detalles del almacenamiento.
- **FR-012**: El sistema MUST devolver un resultado explícito de éxito o error en cada operación de escritura, sin propagar excepciones no controladas a los consumidores.
- **FR-013**: El sistema MUST ejecutar todas las operaciones de lectura y escritura fuera del hilo principal, en un contexto dedicado a entrada/salida.
- **FR-014**: El sistema MUST exponer la observación de datos exclusivamente mediante flujos reactivos, sin mecanismos de observación no reactivos.
- **FR-015**: El sistema MUST mantener actualizada la suscripción reactiva después de cada inserción, actualización o eliminación.
- **FR-016**: El sistema MUST exportar el esquema de la base de datos para permitir la evolución controlada de su estructura.
- **FR-017**: El sistema MUST devolver un resultado de error controlado cuando se intente actualizar o eliminar una regla inexistente.
- **FR-018**: El sistema MUST permitir que las reglas se inyecten como dependencia en los módulos consumidores sin acoplarlos a la implementación de almacenamiento.

### Key Entities *(include if feature involves data)*

- **Regla de bloqueo**: Representa un criterio configurado para bloquear contenido. Contiene un identificador único, un patrón (dominio o palabra clave), un tipo (`DOMAIN`, `KEYWORD_SHORTS`, `KEYWORD_REELS`), un estado de habilitación y la fecha de creación.
- **Registro de auditoría de bloqueo**: Representa un bloqueo ocurrido. Contiene un identificador único, la referencia a la regla aplicada, el objetivo bloqueado y la marca de tiempo del bloqueo.
- **Tipo de regla**: Categoría cerrada que determina cómo se interpreta el patrón: dominio, palabra clave para Shorts o palabra clave para Reels.
- **Resultado de operación**: Envoltura explícita que indica si una operación de persistencia terminó correctamente o con un error controlado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las reglas creadas, actualizadas o eliminadas correctamente se reflejan de forma consistente después de reiniciar la aplicación.
- **SC-002**: El 100 % de los cambios en reglas se entrega a los suscriptores en menos de 1 segundo, sin reiniciar la aplicación ni realizar consultas manuales.
- **SC-003**: El 100 % de las consultas de reglas activas devuelve únicamente reglas habilitadas y del tipo solicitado.
- **SC-004**: El 100 % de los fallos de almacenamiento simulados se comunica como un resultado de error controlado, con cero bloqueos o cierres inesperados de la aplicación.
- **SC-005**: El 100 % de los registros de auditoría conserva el identificador de regla, el objetivo y la marca de tiempo originales después de reiniciar la aplicación.
- **SC-006**: Las operaciones de almacenamiento no introducen pausas perceptibles en la interfaz ni bloquean el hilo principal.

## Assumptions

- Una regla se considera "activa" cuando su estado de habilitación es verdadero.
- Los patrones duplicados para el mismo tipo están permitidos porque el requisito no define una restricción de unicidad.
- Las marcas de tiempo se almacenan como milisegundos desde la época y no dependen de la zona horaria del dispositivo.
- Los registros de auditoría se conservan de forma independiente de la regla referenciada; no se eliminan en cascada.
- La validación de patrones vacíos o demasiado largos se resolverá en la capa que crea la regla; esta capa debe aceptar el valor tal como llega y no corromperlo.
- El alcance de esta iteración es local: no incluye sincronización remota, cuentas de usuario ni exportación de datos.
- La capa de persistencia es consumida por otros módulos de la aplicación; no incluye pantallas ni flujos de interfaz propios.
- Se reutiliza el envoltorio de resultados y los modelos base existentes en `:core:common` cuando sean compatibles.
- Restricciones técnicas impuestas por el solicitante: persistencia local con Room, inyección de dependencias con Hilt, acceso a datos estrictamente sobre un contexto de entrada/salida, observación exclusiva mediante flujos reactivos, ausencia de LiveData y exportación del esquema de base de datos.
