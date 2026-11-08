# Feature Specification: Interruptores Maestros y Dashboard de Presets

**Feature Branch**: `N/A (no branch hook configured)`

**Created**: 2026-11-06

**Status**: Implemented

**Input**: User description: "Transformar la capa de interfaz y la lógica de negocio de BlockBuster para implementar un modelo de protección basado en Interruptores Maestros Preconfigurados (Presets) y un flujo de onboarding estricto, eliminando la creación manual de reglas desde cero en la vista principal. Catálogo de Presets Maestros: ADULT_CONTENT (filtros estrictos de palabras clave explícitas y dominios bloqueados), SHORT_VIDEOS (bloqueo automatizado de feeds de videos cortos: Instagram Reels, YouTube Shorts, TikTok), VISUAL_SEARCH (restricción de motores de búsqueda de imágenes y videos). Cada preset debe mapear automáticamente un conjunto interno de reglas de accesibilidad y firmas de paquetes sin intervención manual. Panel de Control Principal: rediseñar la pantalla principal con Jetpack Compose, estética moderna, limpia y en modo oscuro; reemplazar el listado vacío de reglas manuales por una lista interactiva de Switches Maestros organizados por categorías de contenido; al alternar un interruptor, persistir el estado en Room/DataStore y notificar al motor de accesibilidad en tiempo real. Onboarding Obligatorio y Bloqueo de Estado: mantener el gating estricto de permisos críticos y mostrar el panel de switches maestros al completar los permisos. Restricciones: mantener la arquitectura modular, sin dependencias externas innecesarias, UI con Material 3 y Compose puro, estados sincronizados reactivamente con StateFlow/Coroutines."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Activar categorías completas con un interruptor (Priority: P1) 🎯 MVP

La persona usuaria abre el panel principal después de completar el onboarding y
encuentra tres interruptores maestros: contenido adulto, videos cortos y
búsquedas visuales. Al activar uno, la aplicación aplica de inmediato todas las
reglas preconfiguradas de esa categoría, sin que tenga que escribir dominios,
palabras clave ni identificadores de aplicaciones.

**Why this priority**: Es el valor central de esta iteración. Sustituye la
configuración manual de reglas por decisiones simples de una sola acción y hace
que la protección sea utilizable desde el primer uso.

**Independent Test**: Abrir el panel, activar el interruptor de contenido adulto
y comprobar que la categoría queda activa, que el contador de protecciones
aumenta y que el estado sobrevive al reinicio de la aplicación; repetir con los
otros dos interruptores.

**Acceptance Scenarios**:

1. **Given** el panel de presets visible con los tres interruptores apagados, **When** la persona activa "Contenido adulto", **Then** la categoría aparece activa y sus reglas preconfiguradas quedan en vigor.
2. **Given** el panel de presets visible, **When** la persona activa "Videos cortos", **Then** se habilitan las reglas de TikTok, Reels y Shorts definidas por el catálogo.
3. **Given** el panel de presets visible, **When** la persona activa "Búsquedas visuales", **Then** se habilitan las reglas de motores de búsqueda de imágenes y videos.
4. **Given** una categoría activa, **When** la persona la desactiva, **Then** sus reglas dejan de aplicarse y el contador de protecciones disminuye.
5. **Given** una categoría activa, **When** la persona cierra y vuelve a abrir la aplicación, **Then** el interruptor conserva su estado.

---

### User Story 2 - Aplicación reactiva de los cambios (Priority: P2)

La persona alterna un interruptor y el motor de bloqueo recibe el cambio sin
reiniciar la aplicación ni navegar a otra pantalla. El contador de protecciones
activas y el estado del panel se actualizan en el mismo instante.

**Why this priority**: Sin reactividad, el usuario no sabría si el cambio se
aplicó y la protección podría quedar desincronizada respecto de la interfaz.

**Independent Test**: Activar y desactivar un interruptor con la aplicación
abierta, comprobando que el número de protecciones activas cambia en menos de un
segundo y que el motor de bloqueo usa el nuevo conjunto de reglas.

**Acceptance Scenarios**:

1. **Given** un interruptor apagado, **When** la persona lo activa, **Then** el contador de protecciones activas se actualiza sin reiniciar.
2. **Given** un interruptor activo, **When** la persona lo desactiva, **Then** las reglas de esa categoría dejan de estar disponibles para el bloqueo de inmediato.
3. **Given** la pantalla del panel visible, **When** el estado persistido cambia, **Then** el interruptor refleja el nuevo estado mediante un flujo reactivo.
4. **Given** un fallo al guardar el cambio, **When** la operación termina, **Then** el panel informa del error y no se produce un cierre inesperado.

---

### User Story 3 - Dashboard oscuro y estado de protección (Priority: P3)

La persona ve un panel moderno en modo oscuro con una cabecera clara, el estado
del monitoreo, la conexión de accesibilidad y las tres categorías maestras. El
panel comunica de un vistazo cuántas protecciones están activas y permite
iniciar el monitoreo.

**Why this priority**: La claridad visual y el estado operativo mejoran la
confianza, pero la funcionalidad principal ya está cubierta por los
interruptores y su reactividad.

**Independent Test**: Abrir el panel en un dispositivo con tema claro y
comprobar que la interfaz se muestra en modo oscuro, con los tres interruptores,
el contador de protecciones y el estado de monitoreo visibles.

**Acceptance Scenarios**:

1. **Given** la aplicación abierta, **When** se muestra el panel principal, **Then** la interfaz usa una estética oscura basada en Material 3.
2. **Given** el monitoreo detenido, **When** la persona pulsa "Activar monitoreo", **Then** se inicia el servicio y el panel refleja el nuevo estado.
3. **Given** el monitoreo activo y accesibilidad conectada, **When** la persona consulta el panel, **Then** el estado de protección se muestra como activo.
4. **Given** los tres interruptores apagados, **When** la persona consulta el panel, **Then** las tres categorías siguen visibles y utilizables, sin un estado vacío que impida la interacción.

---

### Edge Cases

- La persona desactiva todos los interruptores: el panel sigue mostrando las tres categorías y el contador queda en cero.
- La persona revoca un permiso crítico mientras el panel está abierto: al volver a la aplicación, el onboarding vuelve a bloquear el acceso.
- El proceso se reinicia: los estados de los interruptores se recuperan desde el almacenamiento persistente.
- El catálogo de presets incorpora nuevas reglas en una actualización: las categorías activas aplican automáticamente el catálogo vigente, sin migrar filas manuales.
- Dos reglas de la misma categoría coinciden con una detección: se aplica una precedencia determinista y se audita una sola activación.
- Una categoría contiene reglas de dominio, palabra clave y firma de paquete: cualquiera de ellas puede producir el bloqueo según el tipo de detección.
- El servicio de accesibilidad entrega solo el paquete o solo la clase de la pantalla: las reglas de firma de paquete y de palabra clave deben poder evaluarlo sin un objetivo textual.
- El almacenamiento local falla al guardar un interruptor: el panel informa del error sin cerrarse y mantiene el último estado conocido.
- La persona completa el onboarding y vuelve a abrir la app: el onboarding no se repite mientras los tres permisos sigan activos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST ofrecer tres presets maestros: `ADULT_CONTENT`, `SHORT_VIDEOS` y `VISUAL_SEARCH`.
- **FR-002**: El preset `ADULT_CONTENT` MUST incluir un conjunto estricto de dominios y palabras clave de contenido explícito.
- **FR-003**: El preset `SHORT_VIDEOS` MUST incluir firmas de paquetes, dominios y palabras clave para TikTok, Instagram Reels, YouTube Shorts y feeds equivalentes.
- **FR-004**: El preset `VISUAL_SEARCH` MUST incluir firmas de dominios y URLs de motores de búsqueda de imágenes y videos.
- **FR-005**: Cada preset MUST mapear automáticamente su conjunto interno de reglas sin que la persona cree reglas manualmente.
- **FR-006**: El panel principal MUST mostrar un interruptor maestro independiente por cada preset.
- **FR-007**: El panel MUST mostrar las categorías con título, descripción y número de protecciones incluidas.
- **FR-008**: El sistema MUST persistir el estado de cada interruptor de forma duradera.
- **FR-009**: El estado de los interruptores MUST sobrevivir al cierre y reinicio de la aplicación.
- **FR-010**: Al activar un interruptor, el sistema MUST incorporar sus reglas al conjunto de reglas activas del motor de bloqueo.
- **FR-011**: Al desactivar un interruptor, el sistema MUST retirar sus reglas del conjunto de reglas activas.
- **FR-012**: Los cambios de estado MUST propagarse de forma reactiva al motor de bloqueo sin reiniciar la aplicación ni requerir una actualización manual.
- **FR-013**: El panel MUST mostrar un contador de protecciones activas que se actualice en tiempo real.
- **FR-014**: El panel MUST comunicar el estado del monitoreo y de la conexión de accesibilidad.
- **FR-015**: El panel MUST permitir iniciar el monitoreo desde la propia pantalla.
- **FR-016**: El motor de bloqueo MUST poder evaluar reglas de dominio, palabra clave, firma de paquete y clase de pantalla.
- **FR-017**: Las reglas de firma de paquete MUST poder bloquear una aplicación aunque la detección no incluya un objetivo textual.
- **FR-018**: Las reglas de palabra clave MUST poder evaluar el texto detectado y la clase de pantalla de accesibilidad.
- **FR-019**: El panel principal MUST usar una estética oscura basada en Material 3 y Compose puro.
- **FR-020**: El panel principal MUST sustituir la creación manual de reglas desde cero por los interruptores maestros.
- **FR-021**: El onboarding MUST seguir bloqueando el acceso al panel mientras cualquiera de los tres permisos críticos no esté activo.
- **FR-022**: Al completarse los tres permisos, el sistema MUST mostrar de inmediato el panel de interruptores maestros.
- **FR-023**: El sistema MUST funcionar sin dependencias externas adicionales.
- **FR-024**: Los estados del panel MUST sincronizarse mediante flujos reactivos y corrutinas.
- **FR-025**: Un fallo al persistir un interruptor MUST NOT producir un cierre inesperado; el panel MUST informar del error.

### Key Entities

- **Preset maestro**: Categoría de protección preconfigurada con identificador, título, descripción y un conjunto de reglas internas.
- **Estado de preset**: Indica si una categoría está activa y se persiste de forma duradera.
- **Regla preconfigurada**: Regla interna de un preset; puede ser de dominio, palabra clave o firma de paquete.
- **Regla activa**: Unión de las reglas persistidas por la persona y las reglas generadas por los presets habilitados.
- **Estado del panel**: Conjunto de interruptores, contador de protecciones, estado de monitoreo, conexión de accesibilidad y error de persistencia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las instalaciones que completan el onboarding acceden al panel de interruptores maestros.
- **SC-002**: La persona puede activar cualquiera de las tres categorías con una sola acción.
- **SC-003**: El contador de protecciones activas refleja el cambio en menos de 1 segundo tras alternar un interruptor.
- **SC-004**: El 100 % de los estados de interruptores se conserva después de reiniciar la aplicación.
- **SC-005**: El 100 % de las detecciones de paquete de una categoría activa produce coincidencia sin requerir texto en pantalla.
- **SC-006**: El panel mantiene las tres categorías visibles y utilizables aunque todas estén desactivadas.
- **SC-007**: El 100 % de los fallos simulados al persistir un interruptor se comunica sin cerrar la aplicación.
- **SC-008**: Ninguna persona puede acceder al panel de presets con un permiso crítico pendiente.
- **SC-009**: El panel se muestra en modo oscuro en el 100 % de los dispositivos, con independencia del tema del sistema.

## Assumptions

- Los presets son catálogos estáticos versionados con la aplicación; no son editables por la persona usuaria.
- Los interruptores empiezan desactivados en una instalación nueva para respetar la elección de la persona.
- El estado de los interruptores se guarda en el almacenamiento de preferencias local; las reglas de los presets no se duplican en la base de datos.
- Las reglas preconfiguradas se generan en memoria con identificadores estables y se combinan con las reglas persistidas para formar el conjunto activo.
- La creación manual de reglas ya no forma parte de la navegación principal de la aplicación.
- El onboarding y el bloqueo por permisos se rigen por la especificación `005-onboarding-setup-wizard`.
- La evaluación de reglas y el bloqueo visual se rigen por la especificación `002-overlay-block-coordinator`.
- La persistencia de reglas y tipos se rige por la especificación `001-block-rules-persistence`.
- Las firmas de paquetes cubren las variantes conocidas de TikTok; si un fabricante usa otro identificador, se requerirá actualizar el catálogo.
- Restricciones técnicas: arquitectura modular existente, Material 3, Compose puro, Room/DataStore, StateFlow y corrutinas, sin librerías externas de onboarding o dashboard.
