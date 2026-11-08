# Feature Specification: Controles Premium Bloqueados y Visibles

**Feature Branch**: `N/A (no branch hook configured)`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "Actualizar la capa de UI en la pestaña Blocking (`BlockingScreen` y componentes asociados) para implementar el bloqueo visual interactivo de funciones Premium (deshabilitadas) en lugar de ocultarlas. Estado de Habilitación por Suscripción: inyectar el estado actual de `IsPremiumState` (proveniente de `PremiumRepository`) en la pantalla de bloqueo y sus componentes hijos; evaluar si cada opción/interruptor maestro requiere una licencia Premium (ej. restricciones estrictas de Shorts/Reels, personalización del tiempo de fricción, partner de responsabilidad). Comportamiento Visual y de Interacción (Disabled State): mantener visibles todas las opciones bloqueadas en sus respectivas categorías (Content Blocking, Advanced Blocking, Customize Blocked screen), asegurando que muestren el indicador visual de Corona/Candado; si `isPremium == false` y el usuario pulsa sobre un interruptor o ajuste protegido, el switch debe aparecer deshabilitado (opacidad reducida o tintado grisáceo) y al intentar interactuar o pulsar sobre él se debe interceptar la acción para no cambiar el estado y en su lugar lanzar la pantalla o diálogo de suscripción simulada ('Upgrade to Premium'); si `isPremium == true`, todos los elementos se habilitan por completo para interactuar con normalidad. Restricciones: mantener la arquitectura modular intacta; no alterar la lógica de persistencia de Room ni los presets ya funcionales para las funciones gratuitas; todo en Jetpack Compose puro utilizando los estados reactivos existentes."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver todas las funciones disponibles aunque estén bloqueadas (Priority: P1)

La persona usuaria con cuenta Free abre la pestaña Blocking y ve todas las
opciones de protección, incluidas las avanzadas. Las funciones que requieren
Premium permanecen visibles en sus categorías, pero aparecen atenuadas y con un
indicador de candado o corona que explica que necesitan una suscripción.

**Why this priority**: Mostrar el valor completo del producto permite que la
persona entienda qué puede desbloquear, en lugar de encontrar secciones vacías
o funciones ocultas.

**Independent Test**: Abrir Blocking con una cuenta Free y comprobar que las
restricciones de Shorts/Reels, la personalización del tiempo de fricción y el
partner de responsabilidad están visibles, atenuados y marcados como Premium.

**Acceptance Scenarios**:

1. **Given** una cuenta Free en la pestaña Blocking, **When** la persona consulta Advanced Blocking, **Then** ve la opción de restricciones estrictas de Shorts/Reels con indicador Premium.
2. **Given** una cuenta Free en la pestaña Blocking, **When** la persona consulta Customize Blocked screen, **Then** ve la personalización del tiempo de fricción con indicador Premium.
3. **Given** una cuenta Free en la pestaña Blocking, **When** la persona consulta Advanced Blocking, **Then** ve el partner de responsabilidad con indicador Premium.
4. **Given** una opción Premium bloqueada, **When** la persona la inspecciona visualmente, **Then** el control aparece deshabilitado con opacidad reducida o tintado grisáceo, sin desaparecer de la pantalla.
5. **Given** una opción Premium bloqueada, **When** la persona usa un lector de pantalla, **Then** el estado bloqueado y el requisito de Premium son comprensibles.

---

### User Story 2 - Intentar usar una función Premium sin suscripción (Priority: P1)

La persona usuaria Free intenta activar un interruptor o abrir un ajuste
protegido. La interacción no cambia ningún estado; en su lugar se abre la
pantalla de suscripción "Upgrade to Premium" para ofrecer el desbloqueo.

**Why this priority**: Es el comportamiento central solicitado: bloquear la
acción sin ocultar la función y convertir el intento en una oportunidad de
suscripción.

**Independent Test**: Pulsar el interruptor de restricciones de Shorts/Reels y
el ajuste de tiempo de fricción con una cuenta Free; comprobar que el estado no
cambia, que no se persiste ningún valor y que aparece la pantalla de
suscripción en ambos casos.

**Acceptance Scenarios**:

1. **Given** una cuenta Free con el interruptor Premium apagado, **When** la persona pulsa el interruptor, **Then** el interruptor no cambia de estado y se abre la pantalla de suscripción.
2. **Given** una cuenta Free con el interruptor Premium encendido por una suscripción anterior revocada, **When** la persona intenta apagarlo, **Then** el estado no cambia y se abre la pantalla de suscripción.
3. **Given** una cuenta Free, **When** la persona pulsa la fila de personalización del tiempo de fricción, **Then** no se abre el editor de tiempo y se abre la pantalla de suscripción.
4. **Given** una cuenta Free, **When** la persona pulsa la fila del partner de responsabilidad, **Then** no se abre el editor de partner y se abre la pantalla de suscripción.
5. **Given** la pantalla de suscripción abierta desde una opción bloqueada, **When** la persona vuelve sin comprar, **Then** sigue en Free y la opción conserva su estado anterior.
6. **Given** una cuenta Free, **When** la persona intenta activar una opción Premium por cualquier vía de interacción disponible, **Then** el sistema nunca persiste el cambio.

---

### User Story 3 - Desbloqueo inmediato tras suscribirse (Priority: P2)

La persona usuaria completa la suscripción simulada y vuelve a Blocking. Las
opciones que antes estaban atenuadas se habilitan de inmediato, mantienen su
estado persistido y responden con normalidad.

**Why this priority**: El valor de la suscripción debe reflejarse sin reiniciar
ni recargar manualmente, y las opciones deben comportarse como controles
normales.

**Independent Test**: Completar la compra simulada desde una opción bloqueada y
comprobar que, al volver, todos los controles Premium están habilitados y que
sus estados se pueden modificar y conservar.

**Acceptance Scenarios**:

1. **Given** una cuenta Free con una opción Premium bloqueada, **When** la persona completa la suscripción simulada, **Then** el estado de la cuenta cambia a Premium y la opción se habilita sin reiniciar.
2. **Given** una cuenta Premium en Blocking, **When** la persona alterna el interruptor de restricciones de Shorts/Reels, **Then** el cambio se aplica y persiste con normalidad.
3. **Given** una cuenta Premium en Blocking, **When** la persona abre el editor de tiempo de fricción, **Then** puede modificarlo y guardar un valor válido.
4. **Given** una cuenta Premium en Blocking, **When** la persona abre el editor del partner de responsabilidad, **Then** puede modificarlo y guardar el valor.
5. **Given** una cuenta Premium, **When** la persona reinicia la aplicación, **Then** las opciones siguen habilitadas y conservan sus valores.

---

### User Story 4 - Preservar la protección gratuita (Priority: P3)

La persona usuaria Free sigue usando los interruptores maestros gratuitos de
Content Blocking sin restricciones adicionales. El nuevo tratamiento visual de
Premium no altera los presets, la persistencia ni el bloqueo ya existente.

**Why this priority**: La mejora de monetización no debe degradar la protección
básica ni introducir regresiones en funciones gratuitas.

**Independent Test**: Con una cuenta Free, activar y desactivar los presets de
Content Blocking y comprobar que el comportamiento, el contador y la
persistencia son idénticos a los actuales.

**Acceptance Scenarios**:

1. **Given** una cuenta Free, **When** la persona activa un preset de Content Blocking, **Then** el preset se activa y persiste sin abrir la pantalla de suscripción.
2. **Given** una cuenta Free, **When** la persona desactiva un preset gratuito, **Then** el cambio se aplica y persiste con normalidad.
3. **Given** una cuenta Free, **When** se muestran las opciones Premium bloqueadas, **Then** los presets gratuitos siguen visibles, habilitados y sin indicadores de pago.
4. **Given** una actualización de la aplicación, **When** la persona abre Blocking con datos previos, **Then** los presets y las reglas persistidas no se alteran por este cambio de interfaz.

---

### Edge Cases

- El estado Premium no puede leerse: la cuenta se trata como Free, las opciones permanecen visibles y bloqueadas, y la app no se cierra.
- La suscripción se revoca mientras la pantalla Blocking está abierta: los controles pasan a deshabilitados de forma reactiva y cualquier intento abre la suscripción.
- La persona pulsa repetidamente un control bloqueado: no se cambia el estado y no se abren múltiples pantallas de suscripción en cascada.
- La persona pulsa directamente sobre el área del interruptor deshabilitado, no solo sobre la fila: la acción se intercepta y se abre la suscripción.
- La persona pulsa una fila Premium pero el diálogo de suscripción ya está abierto: no se duplica la navegación.
- La compra se cancela o falla: el estado Free y todos los valores previos se conservan.
- El valor persistido de una opción Premium existe de una etapa anterior: se conserva visible, pero no se puede modificar mientras la cuenta sea Free.
- El dispositivo usa fuentes grandes o lector de pantalla: el indicador Premium, el estado deshabilitado y la acción de suscripción siguen siendo comprensibles.
- La persona rota la pantalla o cambia de pestaña: los estados de bloqueo/desbloqueo se mantienen.
- La persona vuelve a Blocking después de suscribirse: no se requiere refrescar ni reiniciar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La pestaña Blocking MUST reflejar el estado de suscripción actual de la cuenta.
- **FR-002**: El sistema MUST mantener visibles todas las opciones Premium en sus categorías correspondientes, incluso para cuentas Free.
- **FR-003**: Las opciones Premium MUST incluir, al menos, restricciones estrictas de Shorts/Reels, personalización del tiempo de fricción y partner de responsabilidad.
- **FR-004**: Cada opción Premium MUST mostrar un indicador visual de candado o corona cuando la cuenta sea Free.
- **FR-005**: Los interruptores Premium MUST mostrarse deshabilitados visualmente con opacidad reducida o tintado grisáceo cuando la cuenta sea Free.
- **FR-006**: Las filas o ajustes Premium MUST mantener su contenido visible y legible cuando estén bloqueados.
- **FR-007**: Si una cuenta Free pulsa un interruptor Premium, el sistema MUST NOT cambiar el estado del interruptor.
- **FR-008**: Si una cuenta Free pulsa un interruptor Premium, el sistema MUST abrir la pantalla o diálogo de suscripción "Upgrade to Premium".
- **FR-009**: Si una cuenta Free pulsa una fila o ajuste Premium, el sistema MUST NOT abrir el editor correspondiente.
- **FR-010**: Si una cuenta Free pulsa una fila o ajuste Premium, el sistema MUST abrir la pantalla o diálogo de suscripción.
- **FR-011**: La interacción con una opción bloqueada MUST cubrir tanto el control como el área de la fila para interceptar todos los intentos.
- **FR-012**: Ningún intento de una cuenta Free MUST persistir cambios en opciones Premium.
- **FR-013**: Cuando la cuenta pase a Premium, todos los controles Premium MUST habilitarse de forma reactiva sin reiniciar la aplicación.
- **FR-014**: Cuando la cuenta pase a Free, los controles Premium MUST volver a mostrarse bloqueados de forma reactiva.
- **FR-015**: En estado Premium, los interruptores y ajustes MUST conservar el comportamiento normal de edición y persistencia ya definido.
- **FR-016**: Los presets gratuitos de Content Blocking MUST seguir visibles, habilitados y funcionales para cuentas Free.
- **FR-017**: La funcionalidad MUST NOT modificar la lógica de persistencia de Room ni los datos de presets existentes.
- **FR-018**: La funcionalidad MUST NOT cambiar el comportamiento de bloqueo del motor de accesibilidad para funciones gratuitas.
- **FR-019**: El estado Premium MUST propagarse a la pantalla y a sus componentes hijos mediante los flujos reactivos existentes.
- **FR-020**: Si el estado Premium no está disponible, el sistema MUST tratar la cuenta como Free sin cerrar la aplicación.
- **FR-021**: La pantalla MUST mantener la estética oscura corporativa y los componentes Material 3 existentes.
- **FR-022**: El indicador Premium y el estado deshabilitado MUST ser comprensibles para tecnologías de asistencia.
- **FR-023**: Pulsar repetidamente una opción bloqueada MUST NOT cambiar el estado ni producir navegaciones duplicadas.
- **FR-024**: Cancelar o fallar la suscripción MUST dejar intactos el estado Free y los valores previos de las opciones.
- **FR-025**: Al volver de la suscripción, el estado visual de los controles MUST actualizarse sin requerir una recarga manual.

### Key Entities

- **Estado de suscripción**: Indica si la cuenta es Free o Premium y determina la habilitación de los controles.
- **Opción Premium**: Interruptor o ajuste avanzado que requiere suscripción, con identificador, categoría, estado bloqueado/desbloqueado y acción asociada.
- **Indicador Premium**: Elemento visual de candado o corona que comunica el requisito de suscripción.
- **Estado de control**: Combinación de valor persistido y habilitación efectiva según la suscripción.
- **Pantalla de suscripción**: Flujo simulado de mejora a Premium existente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las opciones Premium permanece visible en Blocking para cuentas Free.
- **SC-002**: El 100% de las opciones Premium muestra un indicador de candado o corona cuando la cuenta es Free.
- **SC-003**: El 100% de los intentos de una cuenta Free sobre un control Premium abre la suscripción y deja el estado sin cambios.
- **SC-004**: Cero cambios de estado Premium se persisten desde una cuenta Free en las pruebas de aceptación.
- **SC-005**: Tras una suscripción simulada, los controles Premium se habilitan en menos de 1 segundo sin reiniciar.
- **SC-006**: El 100% de los presets gratuitos conserva su comportamiento y persistencia actuales.
- **SC-007**: El 100% de las opciones Premium recupera su valor persistido al volver a Premium.
- **SC-008**: El 100% de los fallos de lectura del estado Premium se presenta como estado Free sin cierre inesperado.
- **SC-009**: Ninguna persona puede modificar una opción Premium desde la interfaz mientras su cuenta sea Free.
- **SC-010**: La pantalla mantiene el tema oscuro y los componentes Material 3 en el 100% de los dispositivos comprobados.

## Assumptions

- El estado de suscripción y la compra simulada ya existen y se rigen por la especificación `007-tabs-focus-premium`.
- Las cuentas nuevas empiezan en Free y el estado se persiste localmente.
- Las funciones Premium actuales son restricciones estrictas de Shorts/Reels, personalización del tiempo de fricción y partner de responsabilidad.
- Los presets maestros de Content Blocking siguen siendo gratuitos.
- El estado deshabilitado se comunica visualmente con opacidad reducida o tintado grisáceo, sin ocultar la opción.
- La pantalla de suscripción simulada existente es el destino de todas las acciones bloqueadas.
- Los valores persistidos de opciones Premium se conservan aunque la cuenta vuelva a Free; solo se impide su modificación.
- El cambio es exclusivamente de interfaz y estado reactivo; no requiere cambios en Room, presets, accesibilidad u overlay.
- Restricciones técnicas: arquitectura modular existente, Jetpack Compose puro, Material 3 y tema oscuro corporativo.
