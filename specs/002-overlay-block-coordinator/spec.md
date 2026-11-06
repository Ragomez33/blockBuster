# Feature Specification: Coordinador central de bloqueos y activación del Overlay

**Feature Branch**: `N/A (no branch hook configured)`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "Implementar el coordinador central de bloqueos y el flujo de activación del Overlay en el módulo `:core:overlay` y `:app`.

### Componentes Requeridos
1. **OverlayCoordinator (o BlockDispatcher)**:
   - Un componente inyectado con Hilt (con ciclo de vida de `Singleton` o ejecutándose dentro del `MonitoringForegroundService`) que colecta los eventos del `SharedFlow` emitidos por el `BlockerAccessibilityService`.
   - Consulta de forma asíncrona (`Dispatchers.IO`) el `BlockRulesRepository` para verificar si el objetivo detectado (URL o palabra clave) coincide con una regla activa.
   - Si hay coincidencia, emite una orden reactiva para activar el estado de bloqueo en el `OverlayController`.

2. **OverlayController e Implementación**:
   - Administrar la vista flotante usando `WindowManager.LayoutParams` con `TYPE_APPLICATION_OVERLAY`.
   - Asegurar que la ventana cobre toda la pantalla (`MATCH_PARENT`), con flags para bloquear eventos táctiles hacia las aplicaciones de fondo (`FLAG_NOT_TOUCH_MODAL` manejado adecuadamente o bloqueo de touch según el estado) y evitar el botón de inicio/recientes si es necesario.
   - Inyectar la pantalla de Compose (`FrictionScreen`) dentro de la vista flotante utilizando un `ComposeView` adherido a un `LifecycleOwner` y `SavedStateRegistryOwner` de ventana propios de Android Compose en WindowManager.

3. **Auditoría de Infracciones**:
   - Registrar cada evento bloqueado insertando un registro en la tabla `BlockLogEntity` a través de Room para mantener estadísticas locales.

### Restricciones Técnicas
- El `OverlayCoordinator` no debe bloquear el hilo principal al evaluar las reglas de Room.
- El manejo del ciclo de vida del `ComposeView` dentro del `WindowManager` debe evitar fugas de memoria (`memory leaks`) al destruir o reabrir el overlay."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bloquear objetivos que coinciden con una regla (Priority: P1) 🎯 MVP

Una persona usuaria navega hacia un dominio o contenido con una palabra clave
que decidió bloquear. El coordinador detecta el objetivo, comprueba las reglas
activas y activa una pantalla de fricción a pantalla completa que impide seguir
interactuando con la aplicación de fondo.

**Why this priority**: Es el valor central del producto. Sin activación del
overlay cuando hay una coincidencia, las reglas no protegen al usuario.

**Independent Test**: Activar una regla para un objetivo conocido, simular una
detección de ese objetivo y comprobar que aparece el overlay a pantalla completa
en menos de un segundo. Repetir con un objetivo sin regla y comprobar que no
aparece.

**Acceptance Scenarios**:

1. **Given** una regla activa que coincide con el objetivo detectado, **When** el coordinador recibe la detección, **Then** el overlay se muestra a pantalla completa en menos de un segundo.
2. **Given** una detección cuyo objetivo no coincide con ninguna regla activa, **When** el coordinador la procesa, **Then** no se muestra ningún overlay.
3. **Given** un overlay visible y una detección posterior que ya no coincide, **When** el coordinador reevalúa, **Then** el overlay se oculta.
4. **Given** que el permiso de superposición no está concedido, **When** se detecta una coincidencia, **Then** el sistema no se bloquea, no se produce un cierre inesperado y queda un diagnóstico disponible.

---

### User Story 2 - Gestionar el ciclo de vida del overlay sin fugas (Priority: P2)

La persona usuaria cierra la pantalla de fricción o cambia de objetivo. El
overlay debe desaparecer por completo, liberar sus recursos y poder mostrarse de
nuevo más tarde sin ventanas duplicadas ni fugas de memoria.

**Why this priority**: Un overlay que no se destruye correctamente puede dejar la
pantalla bloqueada, duplicar ventanas o consumir memoria de forma progresiva.

**Independent Test**: Mostrar y ocultar el overlay cincuenta veces, alternando
entre objetivos bloqueados y no bloqueados, y verificar que no quedan ventanas
huérfanas, que no hay cierres inesperados y que la memoria no crece de forma
sostenida.

**Acceptance Scenarios**:

1. **Given** un overlay ya visible para un objetivo, **When** llega otra detección del mismo objetivo, **Then** no se crea una segunda ventana ni se duplica la pantalla.
2. **Given** un overlay visible, **When** la persona usuaria pulsa la acción de salida, **Then** el overlay desaparece y la composición se destruye.
3. **Given** un overlay oculto, **When** se detecta de nuevo el mismo objetivo, **Then** el overlay puede mostrarse otra vez correctamente.
4. **Given** cincuenta ciclos de mostrar y ocultar, **When** se inspecciona el estado final, **Then** no existen ventanas retenidas ni referencias al ciclo de vida anterior.
5. **Given** que la aplicación de fondo cambia o se cierra, **When** el objetivo deja de estar activo, **Then** el overlay se retira.

---

### User Story 3 - Auditar cada activación de bloqueo (Priority: P3)

Quien revisa el comportamiento de BlockBuster necesita un registro local de cada
activación de bloqueo para entender qué se bloqueó, con qué regla y cuándo.

**Why this priority**: La auditoría aporta trazabilidad y estadísticas, pero el
bloqueo puede funcionar sin ella. Por eso queda después del overlay y su ciclo
de vida.

**Independent Test**: Provocar una activación de bloqueo y comprobar que se crea
exactamente un registro con la regla, el objetivo y la marca de tiempo; repetir
detecciones del mismo objetivo y verificar que no se duplica el registro.

**Acceptance Scenarios**:

1. **Given** una coincidencia que activa el overlay, **When** el bloqueo se confirma, **Then** se persiste exactamente un registro de auditoría con la regla aplicada, el objetivo y la marca de tiempo.
2. **Given** un overlay ya activo para el mismo objetivo, **When** llegan detecciones repetidas, **Then** no se insertan registros duplicados.
3. **Given** que la regla se elimina después del bloqueo, **When** se consulta el historial, **Then** el registro de auditoría sigue disponible.
4. **Given** un fallo al guardar la auditoría, **When** el overlay se activa, **Then** el bloqueo visual sigue funcionando y el fallo no interrumpe la experiencia.

---

### Edge Cases

- Qué ocurre si el evento de detección no incluye un objetivo utilizable (URL o palabra clave)?
- Qué ocurre si varias reglas activas coinciden con el mismo objetivo?
- Qué ocurre si la regla se deshabilita o elimina mientras el overlay está visible?
- Qué ocurre si el objetivo cambia varias veces en menos de un segundo?
- Qué ocurre si la persona usuaria pulsa inicio o recientes mientras el overlay está visible?
- Qué ocurre si la pantalla se apaga o el dispositivo se bloquea con el overlay activo?
- Qué ocurre si el servicio de monitoreo se reinicia mientras hay un overlay visible?
- Qué ocurre si el permiso de superposición se revoca con el overlay activo?
- Qué ocurre si WindowManager rechaza añadir la vista?
- Qué ocurre si la base de datos falla al consultar reglas o al escribir la auditoría?
- Qué ocurre si llegan eventos duplicados del mismo objetivo en ráfaga?
- Qué ocurre si el overlay se muestra y oculta repetidamente en poco tiempo?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST recopilar los eventos de detección emitidos por el servicio de accesibilidad mediante un flujo reactivo compartido.
- **FR-002**: Cada evento de detección MUST exponer el objetivo detectado (URL o palabra clave) y una marca de tiempo para poder evaluarlo.
- **FR-003**: El sistema MUST evaluar las reglas activas de forma asíncrona, sin bloquear el hilo principal.
- **FR-004**: El sistema MUST considerar que hay coincidencia cuando una regla activa corresponde al objetivo detectado según el tipo de regla.
- **FR-005**: El sistema MUST mostrar el overlay cuando exista coincidencia y MUST ocultarlo cuando no exista.
- **FR-006**: El overlay MUST cubrir toda la pantalla y MUST consumir los eventos táctiles para que la aplicación de fondo no reciba interacción.
- **FR-007**: El overlay MUST permanecer visible hasta que la persona usuaria lo cierre, el objetivo deje de coincidir o el bloqueo ya no sea aplicable.
- **FR-008**: El sistema MUST evitar ventanas duplicadas cuando se reciben detecciones repetidas del mismo objetivo.
- **FR-009**: El sistema MUST destruir la composición y liberar la ventana al ocultar o cerrar el overlay.
- **FR-010**: El sistema MUST registrar exactamente una entrada de auditoría por activación de bloqueo, no por cada evento repetido.
- **FR-011**: La entrada de auditoría MUST contener la regla aplicada, el objetivo y la marca de tiempo del bloqueo.
- **FR-012**: El sistema MUST conservar los registros de auditoría aunque la regla asociada se elimine.
- **FR-013**: Un fallo al guardar la auditoría MUST NOT impedir que el overlay se muestre.
- **FR-014**: El sistema MUST manejar de forma controlada la ausencia del permiso de superposición, sin producir un cierre inesperado.
- **FR-015**: El sistema MUST reevaluar el bloqueo cuando cambian las reglas activas mientras el overlay está visible.
- **FR-016**: El sistema MUST cerrar el overlay cuando la pantalla se apaga, la aplicación deja de estar activa o el servicio se detiene.
- **FR-017**: El sistema MUST aplicar una precedencia determinista cuando varias reglas coinciden con el mismo objetivo y MUST auditar una sola activación.
- **FR-018**: El sistema MUST permitir que la persona usuaria cierre la pantalla de fricción mediante una acción explícita.
- **FR-019**: El sistema MUST reducir los eventos duplicados en ráfaga sin perder la primera detección válida.
- **FR-020**: El sistema MUST funcionar tanto si el coordinador vive como singleton de aplicación como si se ejecuta dentro del servicio de monitoreo.

### Key Entities *(include if feature involves data)*

- **Evento de detección**: Señal emitida cuando el servicio de accesibilidad identifica un objetivo potencialmente bloqueable. Contiene el objetivo (URL o palabra clave), el contexto de la aplicación y la marca de tiempo.
- **Coincidencia de bloqueo**: Resultado de evaluar un objetivo contra las reglas activas. Contiene la regla aplicada y el objetivo bloqueado.
- **Estado de bloqueo del overlay**: Indica si el overlay está visible, qué objetivo representa y si la activación ya fue auditada.
- **Registro de auditoría**: Entrada local inmutable con regla, objetivo y momento del bloqueo, usada para estadísticas e historial.
- **Regla activa**: Criterio habilitado que puede producir un bloqueo; su tipo determina cómo se compara con el objetivo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 95 % de las detecciones con coincidencia muestran el overlay en menos de 1 segundo.
- **SC-002**: El 100 % de las activaciones de bloqueo genera exactamente un registro de auditoría.
- **SC-003**: El 100 % de las detecciones sin coincidencia no muestra ningún overlay.
- **SC-004**: Cincuenta ciclos consecutivos de mostrar y ocultar no producen ventanas huérfanas, cierres inesperados ni crecimiento sostenido de memoria.
- **SC-005**: El 100 % de los eventos repetidos del mismo objetivo no crean overlays duplicados ni registros de auditoría duplicados.
- **SC-006**: La evaluación de reglas no produce bloqueos del hilo principal ni pausas perceptibles en la interfaz.
- **SC-007**: El 100 % de los fallos simulados de auditoría se toleran sin impedir el bloqueo visual.
- **SC-008**: El 100 % de las detecciones con coincidencia y sin permiso de superposición terminan sin cierre inesperado y con un diagnóstico disponible.

## Assumptions

- El servicio de accesibilidad puede proporcionar el objetivo detectable (URL o palabra clave) en el evento; si hoy no lo hace, se requiere una extensión del servicio como dependencia de esta feature.
- Para reglas de dominio, la coincidencia es por host exacto o subdominio del patrón, sin distinguir mayúsculas y minúsculas. Para palabras clave, es una contención sin distinguir mayúsculas y minúsculas.
- Cuando varias reglas coinciden, se aplica la primera regla activa según el orden determinista definido por el repositorio; se audita una sola activación.
- Una "activación" es la transición de no bloqueado a bloqueado para un objetivo. Las detecciones repetidas del mismo objetivo mientras el overlay sigue visible no generan nuevas auditorías.
- Cerrar manualmente el overlay finaliza el bloqueo del objetivo actual. Una nueva detección del mismo objetivo puede volver a activarlo.
- El bloqueo total de inicio y recientes requiere privilegios de dispositivo propietario, modo kiosco o administración del dispositivo. Sin ellos, el sistema aplica la mejor contención disponible y no promete suprimir por completo esos botones.
- El permiso de superposición se solicita y se concede fuera de esta feature; su ausencia se maneja como un caso controlado.
- El coordinador puede ser un singleton de aplicación o ejecutarse dentro del servicio de monitoreo; ambos ciclos de vida deben ser válidos.
- Restricciones técnicas impuestas por el solicitante: Hilt, evaluación asíncrona sobre un contexto de entrada/salida, `WindowManager` con `TYPE_APPLICATION_OVERLAY`, `ComposeView` con `LifecycleOwner` y `SavedStateRegistryOwner` propios, y auditoría mediante `BlockLogEntity` en Room.
