# Feature Specification: Asistente de Configuración Inicial (Onboarding Setup Wizard)

**Feature Branch**: `N/A (no branch hook configured)`

**Created**: 2026-11-05

**Status**: Implemented

**Input**: User description: "Implementar el asistente de configuración inicial (Onboarding Setup Wizard) en `:app` para la verificación reactiva y solicitud guiada de permisos críticos del sistema. Componentes y Requisitos: 1. Estado y Evaluación de Permisos (`OnboardingViewModel` / `OnboardingRepository`): crear un ViewModel reactivo en `:app` que evalúe de forma continua el estado de tres permisos fundamentales: Overlay (`Settings.canDrawOverlays(context)`), Accessibility (presencia y estado activo de `BlockerAccessibilityService` en `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`) y DeviceAdmin (estado activo de `BlockerDeviceAdminReceiver` mediante `DevicePolicyManager.isAdminActive()`). Exponer una estructura de estado (`OnboardingState`) que determine si el onboarding está completo (`isComplete = overlay && accessibility && deviceAdmin`). Refrescar automáticamente el estado en el ciclo de vida de la actividad principal (`onResume`). 2. Interfaz del Asistente de Configuración (`OnboardingScreen`): diseñar una pantalla en Jetpack Compose que bloquee el acceso al dashboard principal mientras `isComplete == false`. Presentar tres tarjetas de acción independientes y secuenciales: Paso 1 (Superposición): botón que lance el intent directo `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` con URI del paquete; Paso 2 (Accesibilidad): botón que lance `Settings.ACTION_ACCESSIBILITY_SETTINGS`; Paso 3 (Administrador de Dispositivo): botón que lance `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN` con `EXTRA_DEVICE_ADMIN` y el `ComponentName` de `BlockerDeviceAdminReceiver`. Reflejar visualmente el estado concedido ("✔ Activo") en cada tarjeta apenas el usuario regrese del menú del sistema. 3. Routing y Orquestación en `MainActivity`: condicionar la vista raíz; si `isComplete == false`, renderizar obligatoriamente `OnboardingScreen`; si `isComplete == true`, dar paso a la vista de control principal. Restricciones Técnicas: sin dependencias externas ni librerías de onboarding adicionales; todo en Jetpack Compose puro; garantizar que los intents no provoquen cierres inesperados utilizando `runCatching` o validando la resolución de intents según el nivel de API de Android objetivo."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configuración guiada antes de usar la aplicación (Priority: P1)

Una persona instala la aplicación por primera vez y la abre. En lugar del
dashboard de interruptores maestros, ve un asistente que le explica qué tres
permisos necesita conceder para que el bloqueo funcione: superposición sobre
otras aplicaciones, accesibilidad y administración del dispositivo. El
asistente indica el orden de los pasos y no permite usar el dashboard hasta que
los tres permisos estén concedidos.

**Why this priority**: Sin los tres permisos la función principal de la
aplicación no puede operar. Guiar a la persona evita que llegue a un panel que
no funciona y reduce el abandono durante la primera configuración.

**Independent Test**: Instalar la aplicación en un dispositivo sin ninguno de
los tres permisos, abrirla y comprobar que se muestra el asistente en lugar del
dashboard; conceder los tres permisos y comprobar que aparece el dashboard de
interruptores maestros.

**Acceptance Scenarios**:

1. **Given** una instalación nueva sin permisos concedidos, **When** la persona abre la aplicación, **Then** se muestra el asistente de configuración y no el dashboard de interruptores maestros.
2. **Given** el asistente visible con los tres pasos pendientes, **When** la persona pulsa la acción del paso de superposición, **Then** se abre la pantalla del sistema correspondiente a ese permiso para esta aplicación.
3. **Given** el asistente visible con los tres pasos pendientes, **When** la persona pulsa la acción del paso de accesibilidad, **Then** se abre la pantalla del sistema de servicios de accesibilidad.
4. **Given** el asistente visible con los tres pasos pendientes, **When** la persona pulsa la acción del paso de administración del dispositivo, **Then** se abre la pantalla del sistema para activar la administración del dispositivo para esta aplicación.
5. **Given** el asistente visible, **When** los tres permisos están concedidos, **Then** el asistente deja de mostrarse y se da paso al dashboard de interruptores maestros.

---

### User Story 2 - Confirmación inmediata al volver del menú del sistema (Priority: P2)

La persona concede un permiso en la pantalla del sistema y vuelve a la
aplicación. El paso correspondiente del asistente muestra de inmediato que el
permiso está activo, sin necesidad de reiniciar la aplicación ni de pulsar
ningún botón de actualización.

**Why this priority**: La confirmación visual inmediata da seguridad de que el
paso se completó y evita que la persona repita acciones o abandone por duda.

**Independent Test**: Abrir el asistente, conceder un permiso en el menú del
sistema y volver a la aplicación; comprobar que la tarjeta de ese paso muestra
el estado activo sin reiniciar.

**Acceptance Scenarios**:

1. **Given** una tarjeta de paso con el permiso pendiente, **When** la persona concede el permiso en el menú del sistema y vuelve a la aplicación, **Then** la tarjeta muestra "✔ Activo".
2. **Given** dos permisos ya concedidos y uno pendiente, **When** la persona vuelve a la aplicación, **Then** solo la tarjeta del permiso pendiente sigue mostrando la acción de concesión.
3. **Given** el permiso concedido y reflejado como activo, **When** la persona revoca el permiso desde el menú del sistema y vuelve a la aplicación, **Then** la tarjeta vuelve a mostrar el estado pendiente y el asistente vuelve a bloquear el panel si corresponde.

---

### User Story 3 - Continuidad ante pantallas del sistema no disponibles (Priority: P3)

La persona usa un dispositivo cuyo fabricante no ofrece exactamente la misma
pantalla de ajustes o donde un intent no puede resolverse. La aplicación no se
cierra de forma inesperada y le informa de que debe conceder el permiso
manualmente desde los ajustes del sistema.

**Why this priority**: La variedad de fabricantes y versiones del sistema hace
que algunas pantallas no existan o estén restringidas. La aplicación debe
mantenerse estable y ofrecer una salida comprensible.

**Independent Test**: Simular o provocar la indisponibilidad de una de las
pantallas de ajustes y comprobar que la aplicación permanece abierta y muestra
un mensaje comprensible.

**Acceptance Scenarios**:

1. **Given** una tarjeta de paso, **When** la pantalla del sistema asociada no puede abrirse, **Then** la aplicación permanece abierta y muestra un mensaje que indica cómo conceder el permiso manualmente.
2. **Given** una apertura de ajustes fallida, **When** la persona vuelve a la aplicación, **Then** el estado de los permisos se reevalúa y el asistente sigue siendo utilizable.
3. **Given** cualquier paso del asistente, **When** la persona pulsa repetidamente la acción, **Then** la aplicación no se cierra ni muestra un comportamiento errático.

---

### Edge Cases

- La persona vuelve del menú del sistema sin conceder el permiso: el paso sigue pendiente y el panel permanece bloqueado.
- La persona concede un permiso y lo revoca después con la aplicación en segundo plano: al volver, el asistente vuelve a bloquear el acceso y la tarjeta muestra el estado pendiente.
- La persona ya tenía uno o varios permisos concedidos antes de abrir el asistente por primera vez: esos pasos aparecen directamente como activos y solo se solicitan los pendientes.
- El servicio de accesibilidad aparece habilitado en los ajustes pero no está realmente operativo: se considera no concedido hasta que esté activo de forma efectiva.
- La pantalla de ajustes concreta no existe o está restringida por el fabricante: se informa a la persona y no se produce un cierre inesperado.
- La aplicación se cierra o el proceso se reinicia durante el asistente: al volver a abrir, el estado de los permisos se reevalúa desde cero.
- La persona concede los tres permisos mientras el asistente está en segundo plano: al volver, el dashboard de interruptores maestros se muestra directamente.
- El dispositivo no permite activar la administración del dispositivo: el paso permanece pendiente y el asistente explica la situación sin bloquearse.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST evaluar de forma continua el estado de tres permisos críticos: superposición sobre otras aplicaciones, accesibilidad y administración del dispositivo.
- **FR-002**: El sistema MUST considerar el onboarding completo únicamente cuando los tres permisos estén concedidos y activos.
- **FR-003**: El sistema MUST mostrar el asistente de configuración en lugar del dashboard de interruptores maestros mientras el onboarding no esté completo.
- **FR-004**: El sistema MUST permitir acceder al dashboard de interruptores maestros únicamente cuando el onboarding esté completo.
- **FR-005**: El asistente MUST presentar tres tarjetas o pasos independientes, uno por cada permiso, con una explicación breve de su propósito.
- **FR-006**: El sistema MUST presentar los pasos en un orden recomendado: superposición, accesibilidad y administración del dispositivo.
- **FR-007**: La tarjeta del paso de superposición MUST ofrecer una acción que abra la pantalla del sistema para conceder ese permiso a esta aplicación.
- **FR-008**: La tarjeta del paso de accesibilidad MUST ofrecer una acción que abra la pantalla del sistema de servicios de accesibilidad.
- **FR-009**: La tarjeta del paso de administración del dispositivo MUST ofrecer una acción que abra la pantalla del sistema para activar la administración del dispositivo para esta aplicación.
- **FR-010**: Cada tarjeta MUST reflejar visualmente si el permiso correspondiente está activo, incluyendo la etiqueta "✔ Activo" cuando lo esté.
- **FR-011**: El sistema MUST reevaluar el estado de los permisos cada vez que la persona vuelve a la aplicación desde una pantalla del sistema o desde segundo plano.
- **FR-012**: El sistema MUST actualizar las tarjetas con el nuevo estado sin requerir reinicio de la aplicación ni actualización manual.
- **FR-013**: El sistema MUST mantener el bloqueo del panel si, tras volver al asistente, algún permiso sigue pendiente.
- **FR-014**: El sistema MUST manejar de forma segura la imposibilidad de abrir una pantalla del sistema, permaneciendo abierto y mostrando un mensaje comprensible con una alternativa manual.
- **FR-015**: El sistema MUST evitar cierres inesperados ante acciones repetidas o fallidas sobre las tarjetas.
- **FR-016**: El sistema MUST funcionar sin dependencias externas ni flujos de onboarding de terceros.
- **FR-017**: El sistema MUST adaptarse a distintas versiones de Android soportadas por la aplicación, ofreciendo una alternativa cuando una pantalla del sistema no esté disponible en esa versión.
- **FR-018**: Al completarse los tres permisos, el sistema MUST mostrar de inmediato el dashboard de preset con los interruptores maestros, sin repetir el onboarding mientras los permisos sigan activos.
- **FR-019**: El dashboard mostrado tras el onboarding MUST sustituir la creación manual de reglas por los interruptores maestros definidos en la especificación `006-preset-master-dashboard`.

### Key Entities

- **Estado de Onboarding**: Representa la situación actual de la configuración inicial. Contiene el estado de cada uno de los tres permisos y un indicador de completitud global.
- **Permiso Crítico**: Cada uno de los tres permisos requeridos, con su nombre, propósito, estado (concedido o pendiente) y la acción del sistema asociada.
- **Paso del Asistente**: Unidad visual del asistente que agrupa un permiso crítico, su explicación, su acción y su estado actual.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las instalaciones nuevas ven el asistente de configuración antes que el dashboard de interruptores maestros.
- **SC-002**: La persona puede completar los tres pasos de configuración en menos de 3 minutos en un dispositivo estándar.
- **SC-003**: El estado de un permiso concedido se refleja como "✔ Activo" en la tarjeta correspondiente en menos de 1 segundo tras volver a la aplicación.
- **SC-004**: El dashboard de interruptores maestros solo se muestra cuando los tres permisos están activos, en el 100% de los casos comprobados.
- **SC-005**: El 95% de los intentos de abrir una pantalla de ajustes finaliza sin cierres inesperados de la aplicación, incluso cuando la pantalla no está disponible.
- **SC-006**: Al menos el 90% de las personas que abren el asistente por primera vez completan los tres pasos en la misma sesión.
- **SC-007**: Ninguna persona puede acceder al dashboard de interruptores maestros con algún permiso pendiente en las pruebas de aceptación.
- **SC-008**: El 100% de las personas que completan los tres permisos acceden al dashboard de presets en la misma sesión, sin reiniciar la aplicación.

## Assumptions

- El dashboard de interruptores maestros se define en la especificación `006-preset-master-dashboard`; esta funcionalidad solo condiciona su acceso.
- El onboarding no se repite mientras los tres permisos permanezcan activos; no se persiste un indicador de "onboarding visto" que pueda desincronizarse del estado real.
- Los tres permisos se consideran requisitos obligatorios para el funcionamiento de la aplicación y no existe un modo de uso reducido sin ellos.
- La aplicación se distribuye a dispositivos Android dentro de las versiones soportadas por el proyecto.
- La persona tiene acceso a los ajustes del sistema de su dispositivo y puede conceder permisos manualmente.
- Las pantallas del sistema para conceder cada permiso pueden variar entre fabricantes y versiones; la aplicación debe tolerar esa variación.
- El estado de los permisos es consultable en cualquier momento sin necesidad de que la persona permanezca dentro del asistente.
- No se requiere persistir un indicador de "onboarding visto": la completitud se deriva siempre del estado real de los permisos.
- La administración del dispositivo puede ser revocada por el sistema o por la persona; la aplicación no puede impedirlo por completo y debe reflejarlo.
- La interfaz se implementa con medios nativos de la aplicación, sin servicios ni librerías de onboarding de terceros.
