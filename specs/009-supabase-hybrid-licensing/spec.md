# Feature Specification: Licencias Híbridas con Supabase y Pasarelas

**Feature Branch**: `N/A (no branch hook configured)`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "Implementar la integración del SDK de Supabase (`supabase-kt`) en `:core:network` o `:core:common` y transformar `PremiumRepository` hacia una arquitectura de licencias híbrida (Offline-First con sincronización en cold start), conectando los botones de suscripción a los flujos de Binance Pay y PayPal. Configuración del Cliente Supabase: agregar dependencias oficiales Auth y Postgrest, proveer una instancia singleton de `SupabaseClient` mediante Hilt inicializada con URL y clave pública anónima desde BuildConfig. Esquema de Suscripciones y DTOs: crear `SubscriptionResponse` con `user_id`, `is_premium`, `expires_at`, `payment_provider` e implementar consultas remotas por user_id o ID de dispositivo. Repositorio Híbrido: en `checkSubscriptionStatus` (cold start/onResume) consultar Supabase con `runCatching`; si es exitoso actualizar DataStore con isPremium y expiresAt; si falla la red hacer fallback transparente al último valor local sin bloquear la UI. Pasarelas y UI: actualizar botones de pago para lanzar intents a Binance Pay y PayPal; agregar acción 'Verificar Pago' que fuerce sincronización inmediata y muestre indicador de carga. Restricciones: modularidad, consultas en Dispatchers.IO, no romper tests existentes, mockear SupabaseClient/SubscriptionRemoteDataSource."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mantener la licencia sin conexión (Priority: P1)

La persona usuaria abre la aplicación sin conexión o con una red inestable. La
aplicación recuerda su última licencia conocida, muestra el estado Premium o
Free que tenía y permite seguir usando las funciones correspondientes sin
esperar a la red ni ver pantallas de carga bloqueantes.

**Why this priority**: La protección y el acceso a funciones no pueden depender
de la conectividad. La continuidad operativa es la base de una licencia
offline-first.

**Independent Test**: Con una licencia Premium previamente guardada, desactivar
la red, reiniciar la aplicación y comprobar que el estado Premium y las funciones
desbloqueadas se mantienen desde el primer momento.

**Acceptance Scenarios**:

1. **Given** una licencia Premium guardada localmente y sin conexión, **When** la persona abre la aplicación, **Then** el estado Premium se muestra de inmediato y las funciones avanzadas siguen disponibles.
2. **Given** una licencia Free guardada localmente y sin conexión, **When** la persona abre la aplicación, **Then** el estado Free se mantiene y no se produce un cierre inesperado.
3. **Given** que existe una verificación remota pendiente, **When** la persona navega por la aplicación, **Then** la interfaz no queda bloqueada esperando la red.
4. **Given** un fallo de red durante la sincronización, **When** la aplicación vuelve a leer la licencia, **Then** conserva el último valor local sin mostrar un error bloqueante.

---

### User Story 2 - Sincronizar la licencia al iniciar y al volver a la app (Priority: P1)

Cuando hay conexión, la aplicación comprueba la licencia en la nube al arrancar
y al volver desde segundo plano. Si la nube confirma una licencia nueva o una
expiración, la aplicación actualiza el estado local y la interfaz de forma
transparente.

**Why this priority**: La licencia debe mantenerse actualizada cuando cambia el
estado de pago, sin requerir una acción manual ni un reinicio.

**Independent Test**: Cambiar el estado de la licencia en el servicio remoto,
reiniciar la aplicación o volver a primer plano y comprobar que la interfaz
refleja el nuevo estado en menos de un segundo tras la sincronización.

**Acceptance Scenarios**:

1. **Given** una cuenta Free local y una licencia Premium activa en la nube, **When** la aplicación sincroniza al iniciar, **Then** el estado local pasa a Premium y las funciones se desbloquean.
2. **Given** una cuenta Premium local y una licencia expirada en la nube, **When** la aplicación sincroniza, **Then** el estado local pasa a Free y las funciones avanzadas se bloquean.
3. **Given** una licencia Premium actualizada en la nube, **When** la persona vuelve a la app desde segundo plano, **Then** la sincronización se ejecuta y la interfaz refleja el cambio sin reiniciar.
4. **Given** una respuesta remota con fecha de expiración, **When** la aplicación guarda la licencia, **Then** conserva la expiración para futuras comprobaciones offline.
5. **Given** el servicio remoto no disponible, **When** la sincronización falla, **Then** la aplicación mantiene el estado local y registra el fallo sin interrumpir la UI.

---

### User Story 3 - Pagar con Binance Pay o PayPal (Priority: P2)

La persona usuaria decide suscribirse y elige una pasarela. La aplicación abre
el flujo externo correspondiente de Binance Pay o PayPal con la información de
compra configurada y, al volver, le permite verificar el pago.

**Why this priority**: Es el camino de conversión y debe ser claro, seguro y
tolerante a la ausencia de navegador o a la cancelación.

**Independent Test**: Desde la pantalla de suscripción, abrir Binance Pay y
PayPal y comprobar que cada acción lanza el flujo externo correcto o muestra un
error comprensible si no está disponible.

**Acceptance Scenarios**:

1. **Given** la pantalla de suscripción, **When** la persona pulsa "Binance Pay", **Then** se abre el flujo externo configurado de Binance Pay.
2. **Given** la pantalla de suscripción, **When** la persona pulsa "PayPal", **Then** se abre el flujo externo configurado de PayPal.
3. **Given** que el flujo externo no puede abrirse, **When** la persona pulsa una pasarela, **Then** la aplicación permanece abierta y muestra un mensaje comprensible.
4. **Given** que la persona abandona la pasarela sin pagar, **When** vuelve a la aplicación, **Then** el estado de la cuenta no cambia.
5. **Given** una compra ya completada y verificada, **When** la persona vuelve a abrir la pantalla, **Then** la aplicación muestra el estado Premium y no duplica la compra.

---

### User Story 4 - Verificar el pago manualmente (Priority: P3)

Después de pagar en la pasarela, la persona pulsa "Verificar Pago". La
aplicación consulta la nube, muestra un indicador breve de validación y confirma
si la licencia ya fue activada; si el webhook aún no llegó, informa de que puede
reintentar más tarde.

**Why this priority**: Cierra el ciclo de compra cuando el webhook tarda y da
control a la persona, sin bloquear la interfaz.

**Independent Test**: Simular una tabla remota actualizada y pulsar "Verificar
Pago"; comprobar el indicador de carga, la actualización a Premium y el mensaje
de éxito. Repetir sin actualización remota y comprobar el mensaje de reintento.

**Acceptance Scenarios**:

1. **Given** una cuenta Free con un pago reciente, **When** la persona pulsa "Verificar Pago", **Then** se muestra un indicador de carga y se ejecuta una sincronización inmediata.
2. **Given** que la nube ya registra la licencia Premium, **When** la verificación termina, **Then** la cuenta pasa a Premium, las funciones se desbloquean y se muestra un mensaje de éxito.
3. **Given** que la nube todavía no registra el pago, **When** la verificación termina, **Then** la cuenta sigue Free y se muestra un mensaje para reintentar más tarde.
4. **Given** un fallo de red durante la verificación, **When** termina el intento, **Then** se muestra un mensaje de error comprensible y se conserva el estado local.
5. **Given** una verificación en curso, **When** la persona pulsa repetidamente "Verificar Pago", **Then** no se lanzan verificaciones ni navegaciones duplicadas.

---

### Edge Cases

- No existe ninguna licencia local y la app está sin conexión: se trata como Free y no se muestra un error bloqueante.
- La licencia remota indica Premium pero la fecha de expiración ya pasó: se trata como Free.
- La licencia remota indica Free aunque la local fuera Premium: se aplica el estado remoto y se revoca el acceso avanzado.
- La respuesta remota no se puede interpretar o tiene datos inválidos: se conserva la licencia local y se registra el fallo.
- El dispositivo no tiene navegador o la URL de la pasarela no puede resolverse: se informa sin cerrar la aplicación.
- La persona cancela el pago: no se altera el estado.
- El webhook de la pasarela tarda en actualizar la tabla: "Verificar Pago" informa de que el pago aún no se detecta y permite reintentar.
- La verificación se ejecuta mientras la app está en segundo plano: no se producen fugas ni actualizaciones duplicadas.
- La identidad anónima del dispositivo cambia o se borran los datos: la licencia asociada a la identidad anterior no se aplica automáticamente.
- El reloj del dispositivo está desincronizado: la expiración se interpreta a partir del valor remoto y, si no es válido, se mantiene la licencia local.
- La persona ya es Premium y pulsa una pasarela: se le muestra su estado y no se le obliga a pagar de nuevo.
- Hay múltiples respuestas de red en poco tiempo: prevalece la última respuesta aplicada de forma consistente.

## Requirements *(mandatory)*

### Functional Requirements

**Licencia híbrida y offline-first**

- **FR-001**: El sistema MUST cargar el estado de licencia local de forma inmediata al iniciar la aplicación, sin esperar a la red.
- **FR-002**: El sistema MUST persistir localmente el estado Premium y la fecha de expiración recibidos del servicio remoto.
- **FR-003**: El sistema MUST intentar sincronizar la licencia con el servicio remoto al arrancar la aplicación y al volver a primer plano.
- **FR-004**: Si la sincronización remota es exitosa, el sistema MUST actualizar el estado local y la interfaz con el valor remoto más reciente.
- **FR-005**: Si la sincronización falla por red, tiempo de espera o error remoto, el sistema MUST continuar usando el último estado local sin bloquear la interfaz.
- **FR-006**: La aplicación MUST permanecer usable mientras una sincronización remota está en curso.
- **FR-007**: La sincronización remota MUST ejecutarse fuera del hilo principal.
- **FR-008**: Una licencia con fecha de expiración vencida MUST tratarse como Free.
- **FR-009**: Una licencia sin fecha de expiración válida y marcada como Premium MUST conservar el estado Premium hasta que el servicio remoto indique lo contrario.
- **FR-010**: La interfaz Premium/Free MUST actualizarse de forma reactiva tras una sincronización exitosa, sin reiniciar la aplicación.
- **FR-011**: El sistema MUST asociar la licencia a una identidad estable del dispositivo o de la persona usuaria.
- **FR-012**: Si no existe identidad o licencia local y no hay red, el sistema MUST tratar la cuenta como Free.

**Esquema de suscripción remoto**

- **FR-013**: El sistema MUST consultar un registro de suscripción remoto que contenga identificador de usuario/dispositivo, indicador Premium, fecha de expiración y proveedor de pago.
- **FR-014**: El sistema MUST poder consultar la licencia por el identificador de la persona o dispositivo registrado.
- **FR-015**: El sistema MUST interpretar correctamente respuestas con fecha de expiración presente o ausente.
- **FR-016**: El sistema MUST tratar respuestas remotas inválidas o incompletas como un fallo controlado, conservando la licencia local.

**Pasarelas de pago**

- **FR-017**: La pantalla de suscripción MUST ofrecer acciones separadas para Binance Pay y PayPal.
- **FR-018**: Cada acción MUST abrir el flujo externo configurado para su pasarela.
- **FR-019**: Si el flujo externo no puede abrirse, el sistema MUST informar de forma comprensible sin cerrar la aplicación.
- **FR-020**: Abrir o cancelar una pasarela MUST NOT modificar por sí solo el estado de licencia.
- **FR-021**: La aplicación MUST mostrar el estado Premium y evitar ofrecer una nueva compra cuando la licencia ya está activa.

**Verificación de pago**

- **FR-022**: La pantalla de suscripción MUST ofrecer una acción explícita "Verificar Pago".
- **FR-023**: "Verificar Pago" MUST forzar una sincronización inmediata contra el servicio remoto.
- **FR-024**: Durante la verificación, el sistema MUST mostrar un indicador de carga breve.
- **FR-025**: Si la verificación confirma Premium, el sistema MUST actualizar el estado, desbloquear funciones y mostrar un mensaje de éxito.
- **FR-026**: Si la verificación no confirma Premium, el sistema MUST mantener el estado actual e informar de que puede reintentarse.
- **FR-027**: Si la verificación falla por red, el sistema MUST informar del error y conservar el estado local.
- **FR-028**: Pulsaciones repetidas durante una verificación en curso MUST NOT lanzar verificaciones duplicadas.
- **FR-029**: La verificación MUST finalizar en un tiempo acotado y no dejar la interfaz cargando indefinidamente.

**Seguridad, arquitectura y compatibilidad**

- **FR-030**: El sistema MUST usar únicamente configuración pública apta para el cliente y MUST NOT incluir secretos de servicio en la aplicación.
- **FR-031**: La funcionalidad MUST mantener la separación modular entre aplicación, red/datos y dominio.
- **FR-032**: La funcionalidad MUST conservar el comportamiento actual de bloqueo de funciones Premium y de la pantalla Blocking.
- **FR-033**: La funcionalidad MUST NOT romper las pruebas unitarias existentes.
- **FR-034**: Las pruebas del repositorio de licencias MUST poder ejecutarse con una fuente remota simulada, sin depender de la red real.
- **FR-035**: La interfaz MUST mantener el tema oscuro corporativo y Material 3.

### Key Entities

- **Licencia**: Estado efectivo Premium/Free de la cuenta, con fecha de expiración y proveedor de pago.
- **Registro de suscripción remoto**: Estructura de la tabla de suscripciones con identificador, indicador Premium, expiración y proveedor.
- **Identidad de licencia**: Identificador estable de la persona o dispositivo usado para consultar la suscripción.
- **Caché local de licencia**: Último estado conocido, usado cuando no hay red.
- **Pasarela de pago**: Binance Pay o PayPal, con su flujo externo configurado.
- **Estado de verificación de pago**: Resultado de una comprobación manual: en curso, confirmada, no detectada o fallida.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las aperturas con licencia local disponible muestra el estado Premium/Free sin esperar a la red.
- **SC-002**: Las consultas remotas no bloquean la interfaz en ningún cold start.
- **SC-003**: El 100% de los fallos de red conserva el último estado local y permite seguir usando la aplicación.
- **SC-004**: El 100% de las licencias vencidas se trata como Free tras la sincronización.
- **SC-005**: Los cambios remotos de licencia se reflejan en la interfaz en menos de 1 segundo después de la sincronización.
- **SC-006**: El 100% de los botones de Binance Pay y PayPal abre su flujo o muestra un error comprensible.
- **SC-007**: "Verificar Pago" muestra un resultado en menos de 10 segundos en condiciones normales de red.
- **SC-008**: El 100% de las verificaciones exitosas desbloquea Premium sin reiniciar.
- **SC-009**: Cero verificaciones duplicadas ante pulsaciones repetidas.
- **SC-010**: Ningún secreto de servicio se incluye en el artefacto de la aplicación.
- **SC-011**: Las pruebas existentes siguen pasando al 100% y las nuevas pruebas de licencia usan una fuente remota simulada.

## Assumptions

- El servicio remoto expone una tabla `subscriptions` con `user_id`, `is_premium`, `expires_at` y `payment_provider`.
- La identidad de licencia es un identificador anónimo estable generado y persistido en el dispositivo; no se requiere cuenta de usuario en esta iteración.
- La URL de la instancia y la clave pública anónima se proporcionan mediante configuración de compilación; no se incluyen claves de servicio.
- El pago se completa en flujos web externos de Binance Pay y PayPal; un webhook del proveedor actualiza la tabla de suscripciones.
- La aplicación no implementa el webhook ni la lógica de backend en esta iteración.
- "Verificar Pago" realiza una consulta puntual o un número acotado de reintentos; no mantiene sondeo indefinido.
- La verificación de licencia en cold start y `onResume` se ejecuta sin bloquear el hilo principal ni la primera composición.
- La licencia local usa el estado existente de suscripción y se amplía con la fecha de expiración.
- La pantalla de suscripción deja de depender de la compra simulada como camino principal y pasa a ofrecer Binance Pay/PayPal más la verificación.
- Las funciones Premium y su bloqueo visual se rigen por `007-tabs-focus-premium` y `008-premium-locked-controls`.
- Restricciones técnicas: arquitectura modular, consultas en contexto de entrada/salida, pruebas con fuente remota simulada, Compose + Material 3 + tema oscuro.
