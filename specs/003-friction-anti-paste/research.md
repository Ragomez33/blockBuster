# Research: Pantalla de fricción y anti-portapapeles

**Feature**: `003-friction-anti-paste`  
**Date**: 2026-11-05  
**Status**: Complete — no unresolved NEEDS CLARIFICATION

## Contexto técnico

La feature se implementa sobre `:core:overlay`:
- `FrictionScreen` actual: pantalla Compose con botón de cierre inmediato.
- `OverlayComposeHost`: owners de lifecycle/saved state/ViewModel y `ComposeView`.
- `OverlayManager`: crea/destruye la ventana y controla `isOverlayVisible`.
- `OverlayCoordinator`: decide mostrar/ocultar y audita activaciones.

La nueva pantalla elimina el cierre inmediato, introduce una espera estricta de
60 segundos, una transcripción exacta de ~300 palabras, anti-paste y un evento
de desbloqueo que el coordinador debe procesar.

## Decisiones

### D1. Texto objetivo como recurso fijo de producto

**Decision**: Empaquetar un párrafo fijo en español de aproximadamente 300
palabras sobre control de impulsos y enfoque en
`core/overlay/src/main/res/values/friction_texts.xml`, con un nombre estable
(`friction_discipline_text`). `FrictionScreen` recibe el texto como parámetro
para poder probarse con textos cortos.

**Rationale**: La comparación es exacta carácter por carácter; el texto debe ser
inmutable, versionado y compartido entre sesiones. Un parámetro permite probar
la lógica sin escribir 300 palabras en cada test.

**Alternatives considered**:
- Texto generado en tiempo de ejecución: rechazado porque la referencia exacta
  debe ser estable.
- Texto hardcodeado en Kotlin: rechazado por mezclar contenido de producto con
  lógica y dificultar traducciones.
- Texto aleatorio por sesión: rechazado porque rompe la reproducibilidad y la
  restauración tras rotación.

### D2. Temporizador basado en tiempo transcurrido real

**Decision**: Guardar `startedAtElapsedRealtime` en la sesión y calcular el
tiempo restante como `60_000 - (elapsedRealtime - startedAt)`. La UI usa un
`CountDownTimer` (o ticker equivalente) inicializado con el tiempo restante para
refrescar la cuenta regresiva; al llegar a cero se muestra 0 y se habilita el
botón solo si el texto coincide.

**Rationale**: Un contador basado únicamente en ticks se desincroniza al
rotar/recrear la vista o al pausar el proceso. La marca de tiempo real conserva
la duración estricta de 60 segundos.

**Alternatives considered**:
- Contador que se reinicia al recomponer: rechazado; viola la preservación de
  estado.
- `delay(1_000)` acumulativo sin marca de inicio: aceptable visualmente, pero
  menos robusto ante recreación; se usa la marca de tiempo como fuente de
  verdad.

### D3. Sesión de fricción retenida fuera de la composición

**Decision**: Introducir `FrictionSession` (`target`, `requiredText`,
`startedAtElapsedRealtime`, `input`) y retenerla en `OverlayManager` mientras el
overlay está visible. `OverlayComposeHost` recibe la sesión y notifica cambios
de input. Si el host se recrea por rotación, se reconstruye desde la sesión
retenida. Una nueva activación para otro objetivo crea una sesión nueva y
reinicia el temporizador.

**Rationale**: La ventana de overlay no es una Activity y no recibe
`onSaveInstanceState` de forma directa. Mantener la sesión en el manager
singleton permite sobrevivir a la recreación de la vista y conservar tiempo y
texto. La restauración tras muerte del proceso queda como best-effort.

**Alternatives considered**:
- Solo `rememberSaveable` en Compose: no cubre la recreación completa del host
  ni la pérdida del `SavedStateRegistry` del overlay.
- Guardar la sesión en disco: excesivo para una fricción de 60 segundos y
  complica la limpieza.
- ViewModel del overlay: posible, pero requiere owners adicionales y no aporta
  ventaja frente al estado en el manager.

### D4. Anti-paste por diferencia de inserción

**Decision**: Implementar `insertedCharacterCount(old, new)` calculando el
prefijo y sufijo comunes entre el texto anterior y el nuevo. Si la cantidad
insertada es mayor que 3, se considera pegado: se vacía el campo, se actualiza
la sesión y se muestra el aviso durante 3 segundos. El temporizador continúa.

**Rationale**: El requisito define pegado como una inserción repentina de más de
tres caracteres en un solo cambio. El cálculo por diferencia detecta pegado en
cualquier posición, no solo al final.

**Alternatives considered**:
- Comparar solo longitudes: falla si se selecciona y reemplaza texto.
- Leer el portapapeles en cada cambio: no detecta dictado/autocompletado y
  requiere permisos/estado adicional.
- Bloquear cualquier cambio de más de un carácter: rechazaría tecleo rápido y
  autocompletado legítimo; el requisito fija el umbral en 3.

### D5. Desactivar acciones contextuales y portapapeles

**Decision**:
- Proveer un `TextToolbar` no-op mediante `LocalTextToolbar`.
- Usar `OutlinedTextField` con `TextFieldValue` y forzar la selección colapsada
  al final tras cada cambio.
- Limpiar el portapapeles primario con `ClipboardManager` al mostrar la
  pantalla y tras detectar un pegado.
- No usar librerías externas.

**Rationale**: Compose no expone `textIsSelectable` como una propiedad del
`TextField`; el equivalente práctico es anular el toolbar contextual, impedir
selecciones persistentes y eliminar el contenido del portapapeles. La detección
por diferencia cubre pegados realizados por IME o atajos que no pasan por el
toolbar.

**Alternatives considered**:
- Solo `LocalTextToolbar` no-op: no impide Ctrl+V ni el pegado del IME.
- Solo detección: permite que el texto pegado llegue a existir durante un
  frame; el requisito pide vaciarlo inmediatamente y evitarlo.
- Limpiar el portapapeles desde el sistema: requeriríaprivilegios especiales;
  no es viable.

**Risk**: Limpiar el portapapeles es intrusivo. Se acepta como parte explícita
del mecanismo de fricción y se documenta.

### D6. Botón habilitado solo por tiempo + coincidencia exacta

**Decision**: Derivar `canSubmit = remainingMillis == 0L && input ==
requiredText`. El botón se renderiza con `enabled = canSubmit`. Al pulsarlo, se
invoca `onUnlock` una sola vez.

**Rationale**: Evita condiciones dispersas y garantiza que un cambio de texto
después de llegar a cero vuelva a deshabilitar el botón.

**Alternatives considered**:
- Habilitar por tiempo y validar al pulsar: rechazado porque el requisito exige
  que el botón permanezca deshabilitado hasta cumplir ambas condiciones.

### D7. Evento de desbloqueo reactivo hacia el coordinador

**Decision**: Extender `OverlayController` con
`unlockEvents: SharedFlow<OverlayUnlock>`. `OverlayManager` emite el evento
cuando `FrictionScreen` completa la fricción. `OverlayCoordinator` lo colecta,
oculta el overlay, limpia la activación y vuelve a `Idle`. No se cierra el
overlay desde la pantalla directamente.

**Rationale**: Mantiene la separación de responsabilidades y permite que el
coordinador actualice su estado/auditoría sin acoplar la UI al manager.

**Alternatives considered**:
- Callback directo `onDismiss` al manager: ya existe, pero no notifica al
  coordinador ni permite una transición de estado limpia.
- Emitir un evento en el bus global: mezcla responsabilidades y expone un
  detalle de UI a módulos no interesados.

### D8. Bloqueo del botón atrás y ausencia de cierre inmediato

**Decision**: Eliminar el botón de cierre. Añadir `OnBackPressedDispatcherOwner`
a los owners del overlay y un `BackHandler(enabled = true) {}` para consumir el
botón atrás mientras la fricción está activa. El único cierre válido es el
evento de desbloqueo o una decisión del coordinador.

**Rationale**: El requisito prohíbe opciones de cierre inmediato; el botón atrás
no debe saltarse la fricción.

**Alternatives considered**:
- Dejar que atrás cierre el overlay: viola el requisito.
- Capturar teclas a nivel de WindowManager: más frágil y dependiente del
  fabricante.

### D9. Pruebas de lógica pura y validación manual de UI

**Decision**:
- Pruebas unitarias de `insertedCharacterCount`, sesión/temporizador y estado
  `canSubmit`.
- Pruebas del coordinador para el evento de desbloqueo.
- Pruebas de `FrictionScreen` con un texto corto si el entorno Compose lo
  permite; validación manual de teclado, rotación, portapapeles y aspecto visual
  en dispositivo.

**Rationale**: La lógica de anti-paste y temporizador es determinista y no
necesita emulador. La interacción real con IME, selección y rotación requiere
prueba manual.

## Dependencias

- `androidx.activity:activity-compose` para `BackHandler` y
  `OnBackPressedDispatcher`.
- `androidx.compose.ui` (TextToolbar, LocalTextToolbar, OutlinedTextField).
- `android.os.ClipboardManager`/`ClipData` nativos.
- Sin librerías de terceros para portapapeles o teclado.
