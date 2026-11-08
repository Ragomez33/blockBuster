# Feature Specification: Navegación por Pestañas, Focus Mode y Freemium

**Feature Branch**: `N/A (no branch hook configured)`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "Evolucionar la arquitectura y la interfaz de BlockBuster para replicar la estructura de navegación por pestañas de Blocker Hero (Blocking, Blocklist, Settings) e integrar un módulo de productividad y monetización freemium. Estructura de Navegación Inferior: reemplazar la pantalla única por una barra de navegación inferior de 3 pestañas principales basadas en Jetpack Compose: Blocking (interruptores maestros categorizados: Content Blocking, Advanced Blocking, Customize Blocked screen, con indicadores visuales de funciones Premium), Blocklist (listas negras y blancas personalizadas de aplicaciones y palabras clave/sitios web, con segmentación por pestañas secundarias y botón flotante de adición) y Settings (ajustes generales, estado de cuenta, control de actualizaciones, política de privacidad y opciones de soporte). Módulo de Productividad y Focus Mode: botón flotante global de acceso rápido que despliegue un temporizador estilo Pomodoro; permitir configurar ciclos de concentración (ej. 25 min de trabajo / 5 min de descanso) donde el motor de accesibilidad incremente el nivel de bloqueo sobre apps de ocio. Estructura de Monetización (Freemium Flags): introducir un modelo de estado de suscripción (IsPremiumState) en la capa de datos/DataStore; bloquear visualmente las opciones avanzadas (restricciones específicas de Shorts/Reels, personalización del tiempo de fricción y el partner de responsabilidad) mostrando indicadores de pago, redirigiendo a una pantalla de pasarela/suscripción simulada o real al pulsarlas. Restricciones: mantener la modularidad estricta (:app, :core:accessibility, :core:overlay, :core:database, :core:common); conservar intacta la lógica de los switches maestros existentes bajo la pestaña Blocking; UI con Jetpack Compose y Material 3 manteniendo el tema oscuro corporativo."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Navegación por pestañas con Blocking como inicio (Priority: P1) 🎯 MVP

La persona usuaria completa el onboarding y llega a una aplicación organizada
en tres pestañas inferiores: Blocking, Blocklist y Settings. La pestaña
Blocking se abre por defecto y conserva los interruptores maestros existentes,
organizados en secciones claras y con marcas visuales en las opciones que
requieren una suscripción.

**Why this priority**: Es la nueva estructura base de toda la aplicación y
garantiza que la protección ya configurada siga funcionando sin cambios.

**Independent Test**: Completar el onboarding, abrir la app y comprobar que
aparecen las tres pestañas, que Blocking está seleccionada y que los
interruptores maestros conservan sus estados y comportamiento.

**Acceptance Scenarios**:

1. **Given** el onboarding completado, **When** la persona abre la aplicación, **Then** se muestra la barra inferior con las pestañas Blocking, Blocklist y Settings, con Blocking seleccionada.
2. **Given** la barra inferior visible, **When** la persona pulsa Blocklist o Settings, **Then** la pestaña correspondiente se muestra sin reiniciar la aplicación.
3. **Given** la pestaña Blocking visible, **When** la persona activa o desactiva un interruptor maestro, **Then** el comportamiento existente de protección se mantiene intacto.
4. **Given** una opción avanzada reservada a Premium, **When** la persona la ve en Blocking, **Then** aparece con un indicador de bloqueo o corona.
5. **Given** la persona cambia de pestaña, **When** vuelve a Blocking, **Then** los estados de los interruptores siguen siendo los mismos.
6. **Given** el onboarding pendiente, **When** la persona abre la aplicación, **Then** se sigue mostrando el asistente y no la navegación por pestañas.

---

### User Story 2 - Gestionar listas negras y blancas personalizadas (Priority: P2)

La persona usuaria entra en la pestaña Blocklist para añadir aplicaciones,
sitios web o palabras clave que quiere bloquear o permitir explícitamente.
Puede alternar entre listas negras y blancas y entre tipos de objetivo
mediante pestañas secundarias, y añadir entradas con un botón flotante.

**Why this priority**: Las listas personalizadas complementan los presets y
permiten ajustar la protección a necesidades concretas sin crear reglas desde
cero en la pantalla principal.

**Independent Test**: Añadir una aplicación a la lista negra, comprobar que
aparece de inmediato y que produce bloqueo; moverla a la lista blanca y
comprobar que queda exenta; eliminar la entrada y verificar que desaparece.

**Acceptance Scenarios**:

1. **Given** la pestaña Blocklist abierta, **When** la persona selecciona el tipo de objetivo "Aplicaciones", **Then** ve las listas negra y blanca de aplicaciones.
2. **Given** la pestaña Blocklist abierta, **When** la persona selecciona "Sitios y palabras clave", **Then** ve las listas negra y blanca de sitios y palabras clave.
3. **Given** una lista visible, **When** la persona pulsa el botón flotante de adición, **Then** se abre un flujo para elegir tipo de lista, tipo de objetivo y valor.
4. **Given** el flujo de adición completo con datos válidos, **When** la persona confirma, **Then** la entrada aparece en la lista correspondiente sin reiniciar la aplicación.
5. **Given** una entrada existente, **When** la persona la elimina, **Then** deja de aplicarse de inmediato.
6. **Given** una aplicación incluida en la lista negra y en la lista blanca, **When** se evalúa una detección, **Then** la lista blanca tiene prioridad y la aplicación no se bloquea.
7. **Given** una lista vacía, **When** la persona la consulta, **Then** ve un estado vacío con una explicación y una acción para añadir la primera entrada.
8. **Given** un valor duplicado en la misma lista y tipo, **When** la persona intenta añadirlo, **Then** el sistema evita el duplicado y muestra un mensaje comprensible.

---

### User Story 3 - Focus Mode con temporizador Pomodoro (Priority: P3)

Desde cualquier pestaña, la persona puede abrir un botón flotante global de
Focus Mode. Allí configura ciclos de concentración y descanso, inicia una
sesión y hace que la aplicación aumente el bloqueo sobre aplicaciones de ocio
durante el trabajo, relajándolo durante los descansos.

**Why this priority**: Aporta valor de productividad y diferenciación, pero la
protección principal y la organización por pestañas deben existir primero.

**Independent Test**: Abrir Focus Mode desde cada pestaña, iniciar una sesión
con ciclos cortos y comprobar que el temporizador avanza, que las apps de ocio
se bloquean durante el trabajo y que el bloqueo adicional se relaja en el
descanso y al finalizar.

**Acceptance Scenarios**:

1. **Given** cualquier pestaña visible, **When** la persona pulsa el botón flotante global de Focus Mode, **Then** se abre el temporizador sin perder la pestaña actual.
2. **Given** el temporizador abierto sin configuración previa, **When** la persona inicia una sesión, **Then** se aplican ciclos por defecto de 25 minutos de trabajo y 5 de descanso.
3. **Given** la persona configura duraciones válidas, **When** inicia la sesión, **Then** el temporizador usa esas duraciones y las conserva para futuras sesiones.
4. **Given** una sesión en fase de trabajo, **When** la persona abre una aplicación de ocio, **Then** el motor de accesibilidad incrementa el bloqueo sobre esa aplicación aunque el interruptor maestro correspondiente esté desactivado.
5. **Given** una fase de descanso activa, **When** la persona usa una aplicación de ocio, **Then** el bloqueo adicional de Focus Mode se relaja.
6. **Given** una sesión activa, **When** la persona pausa, reanuda o detiene, **Then** el temporizador refleja el estado correcto y el bloqueo adicional se ajusta en consecuencia.
7. **Given** una sesión activa, **When** la persona cambia de pestaña o envía la app a segundo plano, **Then** la sesión continúa y el tiempo restante se conserva.
8. **Given** una sesión activa y el proceso reiniciado, **When** la persona vuelve a abrir la aplicación, **Then** la sesión se restaura con el tiempo restante correcto o se marca como finalizada si ya expiró.

---

### User Story 4 - Ajustes, cuenta y desbloqueo Premium (Priority: P4)

La persona entra en Settings para consultar su estado de cuenta, comprobar
actualizaciones, leer la política de privacidad y acceder a soporte. También
puede intentar usar funciones avanzadas; si no es Premium, ve el candado y una
pantalla de suscripción simulada que, al completarse, desbloquea esas funciones.

**Why this priority**: Completa la experiencia de producto y el modelo freemium,
pero depende de que la navegación, Blocking y Focus Mode ya existan.

**Independent Test**: Abrir Settings con una cuenta gratuita, comprobar el
estado y las secciones; pulsar una opción Premium, completar la suscripción
simulada y verificar que el estado cambia a Premium, que las opciones se
desbloquean y que el estado persiste tras reiniciar.

**Acceptance Scenarios**:

1. **Given** la pestaña Settings abierta, **When** la persona consulta la pantalla, **Then** ve el estado de cuenta (Free o Premium), control de actualizaciones, política de privacidad y opciones de soporte.
2. **Given** una cuenta gratuita, **When** la persona pulsa una opción Premium bloqueada, **Then** se abre una pantalla de suscripción con el beneficio y el precio, sin cambiar el estado de cuenta automáticamente.
3. **Given** la pantalla de suscripción abierta, **When** la persona completa la compra simulada, **Then** el estado pasa a Premium y las opciones avanzadas se desbloquean de inmediato.
4. **Given** una compra cancelada o fallida, **When** la persona vuelve a la aplicación, **Then** el estado sigue siendo Free y no se produce un cierre inesperado.
5. **Given** una cuenta Premium activa, **When** la persona reinicia la aplicación, **Then** el estado Premium persiste y las funciones siguen desbloqueadas.
6. **Given** una cuenta gratuita, **When** la persona intenta activar una restricción avanzada, **Then** el sistema no la aplica y ofrece la pantalla de suscripción.
7. **Given** la persona pulsa "Buscar actualizaciones", **When** la comprobación termina, **Then** se muestra un resultado comprensible sin cerrar la aplicación.

---

### Edge Cases

- El onboarding no está completo: la navegación por pestañas y Focus Mode no son accesibles.
- La persona cambia de pestaña mientras un interruptor se está guardando: el estado final debe ser consistente.
- La persona añade una entrada a la lista blanca que ya existía en la lista negra: se aplica la prioridad de la lista blanca y se informa de la situación.
- La lista contiene aplicaciones desinstaladas o valores no disponibles: la entrada se conserva y se marca como no disponible cuando corresponda.
- El temporizador de Focus Mode llega a cero: la fase cambia automáticamente y el bloqueo adicional se ajusta.
- La persona detiene la app o reinicia el dispositivo durante una sesión: al volver, la sesión se restaura o se cierra si el tiempo ya expiró.
- La persona desactiva un interruptor maestro durante una sesión de Focus Mode: el bloqueo adicional de la sesión sigue aplicándose hasta que termine la fase.
- La persona añade a la lista blanca una aplicación de ocio: la lista blanca tiene prioridad sobre el bloqueo adicional de Focus Mode.
- El estado Premium no puede leerse o está corrupto: la cuenta se trata como Free y se ofrece reintentar.
- La persona cierra la pantalla de suscripción antes de pagar: no cambia ningún estado.
- La compra simulada se repite con una cuenta ya Premium: no se duplica la suscripción ni se altera el estado.
- Las duraciones de Focus Mode están fuera de rango o son inválidas: se rechazan con un mensaje claro.
- La persona pulsa repetidamente el botón de Focus Mode o los botones de la suscripción: la aplicación no se cierra ni genera estados inconsistentes.

## Requirements *(mandatory)*

### Functional Requirements

**Navegación y Blocking**

- **FR-001**: El sistema MUST mostrar una barra de navegación inferior con las pestañas Blocking, Blocklist y Settings cuando el onboarding esté completo.
- **FR-002**: La pestaña Blocking MUST ser la pestaña inicial tras completar el onboarding.
- **FR-003**: El sistema MUST conservar sin cambios la lógica y el estado de los interruptores maestros existentes dentro de la pestaña Blocking.
- **FR-004**: La pestaña Blocking MUST organizar las opciones en las secciones "Content Blocking", "Advanced Blocking" y "Customize Blocked screen".
- **FR-005**: Las opciones reservadas a Premium MUST mostrar un indicador visual de bloqueo o corona.
- **FR-006**: El sistema MUST conservar la pestaña seleccionada y los estados visibles al cambiar de pestaña o rotar la pantalla.
- **FR-007**: El sistema MUST mantener el bloqueo del onboarding y no mostrar la navegación por pestañas si falta algún permiso crítico.

**Blocklist**

- **FR-008**: La pestaña Blocklist MUST permitir gestionar listas negras y listas blancas de forma independiente.
- **FR-009**: La pestaña Blocklist MUST permitir gestionar aplicaciones y, por separado, sitios web y palabras clave.
- **FR-010**: La pestaña Blocklist MUST ofrecer pestañas secundarias o segmentación equivalente para alternar entre tipo de objetivo y tipo de lista.
- **FR-011**: La pestaña Blocklist MUST ofrecer un botón flotante de adición para crear entradas.
- **FR-012**: El flujo de adición MUST solicitar tipo de lista, tipo de objetivo y valor, y MUST validar que el valor no esté vacío.
- **FR-013**: El sistema MUST impedir duplicados del mismo valor en la misma lista y tipo, informando a la persona.
- **FR-014**: El sistema MUST permitir eliminar entradas y MUST reflejar la eliminación de inmediato.
- **FR-015**: Las listas blancas MUST tener prioridad sobre las listas negras y sobre el bloqueo adicional de Focus Mode.
- **FR-016**: Las listas MUST actualizarse de forma reactiva sin reiniciar la aplicación.
- **FR-017**: Cada lista vacía MUST mostrar un estado vacío con una explicación y una acción para añadir la primera entrada.
- **FR-018**: Las entradas de aplicaciones MUST referirse a identificadores de paquetes; las entradas de sitios y palabras clave MUST admitir valores textuales.

**Focus Mode**

- **FR-019**: El sistema MUST ofrecer un botón flotante global de Focus Mode accesible desde las tres pestañas principales.
- **FR-020**: Focus Mode MUST presentar un temporizador estilo Pomodoro con fases de trabajo y descanso.
- **FR-021**: El sistema MUST usar por defecto ciclos de 25 minutos de trabajo y 5 minutos de descanso.
- **FR-022**: La persona MUST poder configurar la duración de trabajo y de descanso dentro de rangos válidos.
- **FR-023**: La persona MUST poder iniciar, pausar, reanudar y detener una sesión de Focus Mode.
- **FR-024**: Durante la fase de trabajo, el sistema MUST incrementar el bloqueo sobre aplicaciones de ocio, con independencia del estado de los interruptores maestros.
- **FR-025**: Durante la fase de descanso, el sistema MUST relajar el bloqueo adicional de Focus Mode.
- **FR-026**: Al terminar una fase, el sistema MUST cambiar automáticamente a la fase siguiente según el ciclo configurado.
- **FR-027**: La sesión activa MUST conservar su tiempo restante al cambiar de pestaña o enviar la aplicación a segundo plano.
- **FR-028**: El sistema MUST restaurar una sesión activa tras un reinicio del proceso o marcarla como finalizada si el tiempo ya expiró.
- **FR-029**: Las duraciones inválidas MUST rechazarse con un mensaje comprensible y sin iniciar la sesión.
- **FR-030**: Detener una sesión MUST retirar el bloqueo adicional de Focus Mode y dejar los interruptores maestros como estaban.

**Freemium y Settings**

- **FR-031**: El sistema MUST mantener un estado de suscripción con los valores Free y Premium, accesible desde toda la aplicación.
- **FR-032**: Las cuentas nuevas MUST empezar en estado Free.
- **FR-033**: El estado de suscripción MUST persistir entre sesiones y reinicios.
- **FR-034**: Las funciones avanzadas reservadas a Premium MUST incluir, al menos, restricciones específicas de Shorts/Reels, personalización del tiempo de fricción y partner de responsabilidad.
- **FR-035**: Las funciones Premium MUST permanecer bloqueadas para cuentas Free y MUST mostrar una acción que abra la pantalla de suscripción.
- **FR-036**: La pantalla de suscripción MUST describir el beneficio y el precio y MUST permitir completar una compra simulada.
- **FR-037**: Una compra simulada completada MUST actualizar el estado a Premium y desbloquear las funciones avanzadas de inmediato.
- **FR-038**: Cancelar o abandonar la compra MUST dejar el estado de suscripción sin cambios.
- **FR-039**: La pestaña Settings MUST mostrar el estado de cuenta actual y permitir acceder a la pantalla de suscripción cuando corresponda.
- **FR-040**: La pestaña Settings MUST ofrecer control de actualizaciones, política de privacidad y opciones de soporte.
- **FR-041**: El control de actualizaciones MUST mostrar un resultado comprensible sin cerrar la aplicación.
- **FR-042**: La política de privacidad MUST ser accesible desde Settings y mostrar contenido legible dentro de la aplicación.
- **FR-043**: Las opciones de soporte MUST permitir contactar o consultar ayuda sin abandonar la aplicación de forma inesperada.
- **FR-044**: Un estado Premium no disponible o corrupto MUST tratarse como Free sin bloquear la aplicación.

**Restricciones transversales**

- **FR-045**: Toda la interfaz MUST usar Material 3 y mantener el tema oscuro corporativo.
- **FR-046**: El sistema MUST NOT introducir dependencias externas innecesarias.
- **FR-047**: La navegación por pestañas, los estados de presets, las listas y el estado Premium MUST sincronizarse mediante flujos reactivos.
- **FR-048**: La nueva estructura MUST conservar el comportamiento existente de onboarding, presets, motor de accesibilidad y overlay.

### Key Entities

- **Pestaña principal**: Sección de navegación persistente: Blocking, Blocklist o Settings.
- **Estado de suscripción**: Indica si la cuenta es Free o Premium y se persiste de forma duradera.
- **Función Premium**: Capacidad avanzada que requiere suscripción y se muestra bloqueada para cuentas Free.
- **Entrada de Blocklist**: Elemento de una lista negra o blanca; contiene tipo de lista, tipo de objetivo (aplicación, sitio o palabra clave) y valor.
- **Sesión de Focus Mode**: Ciclo de productividad con fase actual (trabajo o descanso), duración configurada, tiempo restante y estado (activa, pausada o detenida).
- **Aplicación de ocio**: Aplicación o categoría sobre la que Focus Mode incrementa el bloqueo durante la fase de trabajo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las personas que completan el onboarding ven las tres pestañas principales con Blocking seleccionada.
- **SC-002**: El cambio entre pestañas se percibe en menos de 1 segundo y no pierde el estado de la pantalla anterior.
- **SC-003**: El 100% de los interruptores maestros existentes conserva su comportamiento y su estado tras la nueva navegación.
- **SC-004**: La persona puede añadir una entrada a una lista en menos de 30 segundos y verla reflejada en menos de 1 segundo.
- **SC-005**: El 100% de los conflictos entre lista negra y blanca respeta la prioridad de la lista blanca.
- **SC-006**: Iniciar una sesión de Focus Mode tarda menos de 2 segundos y el bloqueo adicional se aplica en menos de 1 segundo.
- **SC-007**: El 100% de las sesiones activas conserva el tiempo restante al cambiar de pestaña o enviar la app a segundo plano.
- **SC-008**: El 100% de las cuentas Free ven las funciones Premium bloqueadas con un indicador visible.
- **SC-009**: Tras una compra simulada, las funciones Premium se desbloquean en menos de 1 segundo y el estado persiste tras reiniciar.
- **SC-010**: El 100% de las compras canceladas o fallidas deja el estado de cuenta sin cambios.
- **SC-011**: Ninguna persona puede acceder a la navegación principal con el onboarding pendiente.
- **SC-012**: El 100% de los fallos simulados de actualización, soporte o lectura del estado Premium se comunica sin cerrar la aplicación.

## Assumptions

- La pasarela de pago se implementa como una simulación funcional dentro de esta iteración; no se integra un proveedor real de facturación ni se realizan cobros reales.
- El estado de cuenta es local y no requiere inicio de sesión, backend ni sincronización entre dispositivos.
- Las cuentas nuevas empiezan en estado Free.
- Las funciones Premium iniciales son las tres indicadas: restricciones específicas de Shorts/Reels, personalización del tiempo de fricción y partner de responsabilidad.
- Los interruptores maestros existentes siguen siendo gratuitos y no se bloquean con el modelo Premium.
- Las "aplicaciones de ocio" se determinan con la clasificación de categorías y presets ya existente; si una aplicación no está clasificada, no se añade bloqueo adicional por Focus Mode.
- La lista blanca tiene prioridad sobre las reglas normales y sobre el bloqueo adicional de Focus Mode, salvo que una protección del sistema lo impida.
- Las listas personalizadas y las sesiones de Focus Mode se guardan localmente y sobreviven al reinicio de la aplicación.
- El temporizador de Focus Mode se basa en marcas de tiempo para poder restaurar el tiempo restante tras un reinicio del proceso.
- El control de actualizaciones es una comprobación simulada/local; no se implementa un sistema real de distribución de actualizaciones.
- La política de privacidad se muestra como contenido estático dentro de la aplicación.
- El soporte se ofrece mediante opciones locales como preguntas frecuentes o un enlace de contacto; no se integra un sistema de tickets.
- El onboarding, los presets, el motor de accesibilidad y el overlay conservan su comportamiento actual; esta funcionalidad los reorganiza y los extiende.
- Restricciones técnicas impuestas por el solicitante: arquitectura modular existente, Jetpack Compose, Material 3, tema oscuro corporativo y ausencia de dependencias externas innecesarias.
