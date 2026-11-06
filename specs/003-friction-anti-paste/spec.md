# Feature Specification: Pantalla de fricción y anti-portapapeles

**Feature Branch**: `N/A (no branch hook configured)`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "Implementar la interfaz de usuario de fricción y el componente anti-portapapeles en el módulo `:core:overlay`.

### Componentes y Requisitos
1. **FrictionScreen (Jetpack Compose)**:
   - Pantalla completa con diseño restrictivo (fondo oscuro, sin opciones de cierre inmediato).
   - Indicador visual de temporizador regresivo (`CountDownTimer`) configurado estrictamente a **60 segundos**. El botón de confirmación / desbloqueo debe permanecer inhabilitado (`enabled = false`) hasta que el contador llegue a 0.
   - Campo de texto (`OutlinedTextField`) para la transcripción obligatoria de un texto de disciplina predefinido (ej. un párrafo de 300 palabras sobre control de impulsos y enfoque).

2. **Mecanismo Anti-Portapapeles (Anti-Paste)**:
   - Desactivar las acciones contextuales de texto (`textIsSelectable = false`, interceptar o anular `LocalTextToolbar`).
   - Monitorear los cambios en el input de texto: si se detecta una inserción repentina de una cadena con longitud mayor a 3 caracteres en un solo ciclo de cambio de estado (lo que indica un intento de pegado), vaciar el campo inmediatamente (`text = ""`) y mostrar un aviso visual temporal indicando "Acción no permitida: Debes escribir el texto manualmente".

3. **Validación de Coincidencia**:
   - El botón de liberación solo se habilitará cuando: (a) el temporizador haya llegado a 0, y (b) el texto ingresado coincida exactamente carácter por carácter con el texto objetivo requerido.
   - Al cumplirse ambas condiciones y presionar el botón, se emitirá el evento de cierre del overlay hacia el `OverlayCoordinator`.

### Restricciones Técnicas
- No utilizar librerías externas de terceros para la manipulación del portapapeles o el teclado; resolver mediante la API nativa de Compose y `ClipboardManager`.
- Asegurar que la vista conserve su estado ante rotaciones o cambios de configuración si el dispositivo lo permite."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Completar la fricción de 60 segundos y la transcripción (Priority: P1) 🎯 MVP

La persona usuaria encuentra una pantalla de bloqueo a pantalla completa. Debe
esperar sesenta segundos y transcribir manualmente un texto de disciplina antes
de poder desbloquear el contenido.

**Why this priority**: Es el mecanismo central de fricción. Sin el temporizador
y la transcripción obligatoria, el bloqueo puede evadirse de inmediato.

**Independent Test**: Abrir la pantalla de fricción, comprobar que el botón está
deshabilitado durante 60 segundos, verificar que sigue deshabilitado con texto
incorrecto y que se habilita únicamente con el texto exacto al llegar a cero.

**Acceptance Scenarios**:

1. **Given** que se abre la pantalla de fricción, **When** se muestra por primera vez, **Then** el contador comienza en 60 segundos y el botón de desbloqueo está deshabilitado.
2. **Given** el contador en cualquier valor mayor que cero, **When** la persona introduce el texto correcto, **Then** el botón permanece deshabilitado.
3. **Given** el contador en cero y un texto que no coincide exactamente, **When** la persona intenta confirmar, **Then** el botón permanece deshabilitado.
4. **Given** el contador en cero y el texto transcrito coincide exactamente carácter por carácter, **When** la persona pulsa el botón, **Then** se emite un evento de desbloqueo al coordinador y el overlay se cierra.
5. **Given** la pantalla de fricción visible, **When** la persona busca una acción de cierre inmediato, **Then** no existe ningún control que permita cerrar antes de completar las condiciones.

---

### User Story 2 - Impedir el pegado de texto (Priority: P2)

La persona usuaria intenta pegar el texto requerido desde el portapapeles. El
sistema rechaza la inserción, vacía el campo y muestra un aviso temporal.

**Why this priority**: Sin anti-paste, la transcripción deja de ser una fricción
real y el bloqueo se vuelve trivial de evadir.

**Independent Test**: Intentar pegar un texto de más de tres caracteres en el
campo, comprobar que el campo queda vacío, que aparece el aviso y que el
temporizador sigue avanzando.

**Acceptance Scenarios**:

1. **Given** el campo de transcripción enfocado, **When** la persona mantiene pulsado el texto, **Then** no aparecen acciones de seleccionar, copiar, cortar o pegar.
2. **Given** el campo vacío, **When** se insertan más de tres caracteres en un único cambio de estado, **Then** el campo se vacía inmediatamente y aparece el aviso "Acción no permitida: Debes escribir el texto manualmente".
3. **Given** el aviso visible, **When** transcurre el tiempo de aviso, **Then** el aviso desaparece sin alterar el temporizador ni el estado del botón.
4. **Given** el campo vacío, **When** la persona escribe manualmente un carácter a la vez, **Then** el texto se conserva y no aparece el aviso.
5. **Given** una inserción de exactamente tres caracteres, **When** se produce en un solo cambio, **Then** no se considera pegado según el umbral definido.

---

### User Story 3 - Conservar el progreso ante rotaciones (Priority: P3)

La persona usuaria rota el dispositivo mientras espera o escribe. El tiempo
restante y el texto introducido se conservan, evitando reiniciar la fricción.

**Why this priority**: Reiniciar el contador por una rotación castiga
innecesariamente a la persona y rompe la experiencia.

**Independent Test**: Iniciar la fricción, escribir parte del texto, rotar el
dispositivo y comprobar que el tiempo restante y el texto se mantienen.

**Acceptance Scenarios**:

1. **Given** el contador en un valor intermedio, **When** el dispositivo rota, **Then** el contador continúa desde el tiempo restante y no vuelve a 60.
2. **Given** texto parcialmente escrito, **When** el dispositivo rota, **Then** el texto se conserva y el botón se recalcula según el temporizador y la coincidencia.
3. **Given** una nueva activación de bloqueo para otro objetivo, **When** se muestra una nueva pantalla de fricción, **Then** el contador vuelve a 60 y el campo comienza vacío.

---

### Edge Cases

- Qué ocurre si el temporizador llega a cero mientras la persona está escribiendo?
- Qué ocurre si se pega texto en medio del texto ya escrito?
- Qué ocurre si se pega un texto de exactamente tres caracteres?
- Qué ocurre si el teclado inserta autocompletado o texto predictivo de más de tres caracteres?
- Qué ocurre si el portapapeles contiene el texto exacto requerido?
- Qué ocurre si la persona borra y vuelve a escribir después de llegar a cero?
- Qué ocurre si el dispositivo se apaga o el proceso se recrea con la pantalla visible?
- Qué ocurre si el overlay se recrea para el mismo objetivo tras una rotación?
- Qué ocurre si el texto contiene saltos de línea, tildes o signos de puntuación?
- Qué ocurre si se intenta cerrar con el botón atrás o con gestos del sistema?
- Qué ocurre si el temporizador o el estado guardado no están disponibles al restaurar?
- Qué ocurre si la persona pega repetidamente en rápida sucesión?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La pantalla de fricción MUST ocupar toda la pantalla y usar un diseño restrictivo con fondo oscuro.
- **FR-002**: La pantalla MUST NOT ofrecer ninguna acción de cierre inmediato antes de completar las condiciones de desbloqueo.
- **FR-003**: La pantalla MUST mostrar un temporizador regresivo visible que comienza estrictamente en 60 segundos.
- **FR-004**: El botón de desbloqueo MUST permanecer deshabilitado mientras el temporizador sea mayor que cero.
- **FR-005**: La pantalla MUST incluir un campo de transcripción para un texto objetivo fijo de aproximadamente 300 palabras sobre disciplina, control de impulsos y enfoque.
- **FR-006**: El texto introducido MUST compararse exactamente carácter por carácter con el texto objetivo, incluyendo mayúsculas, minúsculas, espacios, saltos de línea y puntuación.
- **FR-007**: El botón de desbloqueo MUST habilitarse únicamente cuando el temporizador haya llegado a cero y el texto coincida exactamente.
- **FR-008**: Al pulsar el botón habilitado, el sistema MUST emitir un evento de desbloqueo hacia el coordinador del overlay.
- **FR-009**: El campo MUST impedir la selección de texto y las acciones contextuales de copiar, cortar, seleccionar todo y pegar.
- **FR-010**: El sistema MUST detectar como intento de pegado cualquier cambio de estado que inserte más de tres caracteres a la vez.
- **FR-011**: Ante un intento de pegado, el sistema MUST vaciar inmediatamente el campo de texto.
- **FR-012**: Ante un intento de pegado, el sistema MUST mostrar el aviso temporal "Acción no permitida: Debes escribir el texto manualmente".
- **FR-013**: El aviso de pegado MUST desaparecer automáticamente sin modificar el temporizador ni habilitar el botón.
- **FR-014**: La escritura manual carácter a carácter MUST conservarse sin activar el mecanismo anti-paste.
- **FR-015**: El temporizador MUST continuar avanzando aunque se produzca un intento de pegado.
- **FR-016**: El tiempo restante y el texto introducido MUST conservarse ante una rotación o cambio de configuración cuando el sistema lo permita.
- **FR-017**: La restauración del estado MUST NOT reiniciar el temporizador ni habilitar prematuramente el botón.
- **FR-018**: Una nueva activación de bloqueo para un objetivo distinto MUST reiniciar el temporizador a 60 segundos y vaciar el campo.
- **FR-019**: La pantalla MUST impedir que el botón atrás u otra acción del sistema cierren el bloqueo sin completar las condiciones.
- **FR-020**: La solución MUST usar únicamente APIs nativas de la plataforma y no librerías externas para portapapeles o teclado.

### Key Entities *(include if feature involves data)*

- **Sesión de fricción**: Estado de una pantalla de bloqueo concreta. Contiene el objetivo bloqueado, el tiempo restante, el texto objetivo, el texto introducido, el estado del aviso y si el desbloqueo está habilitado.
- **Texto objetivo**: Párrafo fijo de aproximadamente 300 palabras sobre control de impulsos y enfoque. Es la referencia exacta para la transcripción.
- **Evento de desbloqueo**: Señal emitida cuando la persona completa el tiempo y la transcripción exacta. Contiene el objetivo y la marca de tiempo.
- **Intento de pegado**: Cambio de entrada que inserta más de tres caracteres en un solo ciclo de estado. Se descarta y produce un aviso temporal.
- **Aviso de pegado**: Mensaje temporal que informa que la acción no está permitida.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las sesiones de fricción comienza con un temporizador de 60 segundos y el botón deshabilitado.
- **SC-002**: El 0 % de las sesiones permite desbloquear antes de que el temporizador llegue a cero.
- **SC-003**: El 100 % de los intentos de desbloqueo con texto no idéntico permanece bloqueado.
- **SC-004**: El 100 % de los intentos de pegar más de tres caracteres vacía el campo y muestra el aviso en menos de 200 milisegundos.
- **SC-005**: El 100 % de las rotaciones conserva el tiempo restante con una diferencia menor o igual a 1 segundo y conserva el texto introducido.
- **SC-006**: El 100 % de las sesiones nuevas para un objetivo distinto reinicia el temporizador y el campo.
- **SC-007**: El 0 % de las sesiones ofrece una acción de cierre inmediato antes de completar las condiciones.
- **SC-008**: La escritura manual carácter a carácter no genera avisos de pegado en el 100 % de los casos.

## Assumptions

- El texto objetivo es un párrafo fijo de aproximadamente 300 palabras sobre control de impulsos y enfoque, definido como contenido de producto y empaquetado con la aplicación. Su redacción exacta se fija antes de la implementación y no cambia entre sesiones.
- El aviso de pegado permanece visible durante 3 segundos y luego desaparece automáticamente.
- El umbral de pegado es estricto: se rechazan inserciones de 4 o más caracteres en un único cambio; se permiten inserciones de hasta 3 caracteres.
- El autocompletado, texto predictivo o dictado que inserten más de 3 caracteres en un solo cambio se tratan como pegado y se rechazan. La persona debe escribir manualmente.
- El temporizador comienza cuando la pantalla se muestra por primera vez para una activación. No se pausa al perder el foco ni al abrir el teclado.
- La rotación conserva tiempo y texto mediante el estado guardado de la pantalla. La restauración tras muerte del proceso es best-effort según las capacidades del sistema.
- Una nueva activación para el mismo objetivo después de un desbloqueo o cierre se considera una sesión nueva y reinicia la fricción.
- No se ofrecen exenciones de accesibilidad ni atajos para completar la transcripción en esta iteración.
- No existe una acción de cierre inmediato; el único desbloqueo válido es el evento emitido tras cumplir tiempo y coincidencia.
- Restricciones técnicas impuestas por el solicitante: Jetpack Compose, API nativa de portapapeles/teclado, sin librerías externas, temporizador estricto de 60 segundos y estado preservado ante configuración cuando sea posible.
