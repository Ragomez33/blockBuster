# Quickstart: Pantalla de fricción y anti-portapapeles

**Feature**: `003-friction-anti-paste`  
**Prerequisites**: JDK 17+, Android SDK 37, dispositivo/emulador para validación
de teclado, portapapeles y rotación.

## Comandos

```bash
# Compilar overlay y app
./gradlew :core:overlay:assembleDebug :app:assembleDebug

# Pruebas JVM de lógica de fricción y coordinador
./gradlew :core:overlay:testDebugUnitTest :core:database:testDebugUnitTest

# Lint
./gradlew :app:lintDebug
```

## Escenario 1 — Temporizador y botón deshabilitado

1. Abrir una pantalla de fricción para un objetivo bloqueado.
2. Observar el contador y el botón durante los primeros 60 segundos.
3. Escribir el texto exacto antes de que el contador llegue a cero.

**Resultado esperado**:

- El contador comienza en 60 s.
- El botón permanece deshabilitado en todo momento antes de 0.
- No existe acción de cierre inmediato.

## Escenario 2 — Validación exacta y desbloqueo

1. Esperar a que el contador llegue a 0.
2. Introducir un texto con una diferencia mínima (espacio, tilde o puntuación).
3. Corregir hasta que coincida exactamente.
4. Pulsar el botón.

**Resultado esperado**:

- Con texto no idéntico, el botón sigue deshabilitado.
- Con coincidencia exacta, el botón se habilita.
- Al pulsar, se emite `OverlayUnlock`, el coordinador oculta el overlay y el
  estado vuelve a `Idle`.

## Escenario 3 — Anti-paste

1. Copiar un fragmento de más de tres caracteres al portapapeles.
2. Intentar pegarlo en el campo de transcripción.
3. Repetir con una inserción de exactamente tres caracteres.

**Resultado esperado**:

- El pegado de 4+ caracteres vacía el campo y muestra el aviso durante 3 s.
- El temporizador continúa.
- La inserción de 3 caracteres no activa el aviso.
- El botón permanece deshabilitado si el campo está vacío o incompleto.

## Escenario 4 — Acciones contextuales deshabilitadas

1. Mantener pulsado sobre el campo.
2. Intentar seleccionar, copiar, cortar o pegar desde el menú contextual.
3. Probar atajos de teclado cuando el entorno lo permita.

**Resultado esperado**:

- No aparece un menú contextual de texto.
- No se conserva una selección persistente.
- Cualquier inserción masiva se detecta y se revierte.

## Escenario 5 — Rotación

1. Abrir la fricción y escribir parte del texto.
2. Rotar el dispositivo.
3. Comprobar el contador y el texto.

**Resultado esperado**:

- El tiempo restante continúa; no vuelve a 60.
- El texto introducido se conserva.
- El botón se recalcula con el tiempo y la coincidencia actuales.

## Escenario 6 — Nueva activación

1. Completar o cerrar una fricción para un objetivo.
2. Provocar una nueva activación para otro objetivo.

**Resultado esperado**:

- El contador vuelve a 60.
- El campo comienza vacío.
- El aviso de pegado no se arrastra.

## Escenario 7 — Botón atrás

1. Con la fricción visible, pulsar atrás o el gesto equivalente.

**Resultado esperado**:

- La pantalla de fricción no se cierra.
- El contador continúa.
- El único cierre válido es completar tiempo + texto exacto.

## Escenario 8 — Rotación/recreación del host

1. Mostrar la fricción y esperar 15 s.
2. Forzar una recreación de la ventana/vista si el entorno lo permite.
3. Observar el contador y el texto.

**Resultado esperado**:

- La sesión se reutiliza; el tiempo no se reinicia.
- El texto se conserva.
- La composición anterior se destruye sin dejar ventanas huérfanas.

## Escenario 9 — Validación manual de teclado y portapapeles

1. Probar dictado por voz, autocompletado y texto predictivo.
2. Probar pegado desde el teclado y desde el menú del sistema.
3. Probar la limpieza del portapapeles al entrar en la fricción.

**Resultado esperado**:

- Las inserciones de más de 3 caracteres se rechazan.
- El portapapeles queda vacío mientras la fricción está activa.
- La escritura manual carácter a carácter funciona.
- El comportamiento puede variar por fabricante/IME y se documenta.

## Referencias

- Modelo de datos: [`data-model.md`](data-model.md)
- Contratos: [`contracts/friction-screen-contracts.md`](contracts/friction-screen-contracts.md)
- Investigación: [`research.md`](research.md)

## Resultados de la implementación (2026-11-05)

Validación automatizada ejecutada:

| Comando | Resultado |
|---|---|
| `./gradlew :core:overlay:testDebugUnitTest` | PASS — 32 pruebas (input, sesión, reutilización, unlock, coordinador y host) |
| `./gradlew :core:database:testDebugUnitTest` | PASS — 20 pruebas |
| `./gradlew :app:assembleDebug` | PASS — APK generado |
| `./gradlew :app:lintDebug` | PASS — sin errores de lint |

También se verificó que `friction_discipline_text` contiene 300 palabras.

**Pendiente de validación manual** (requiere dispositivo/emulador): teclado e
IME, selección contextual, pegado real desde el portapapeles, rotación,
recreación de la ventana y botón atrás. No hay dispositivo conectado en este
entorno.

**Limitaciones conocidas**:

- Compose no expone `textIsSelectable`; se anulan el toolbar contextual y la
  selección persistente, pero los handles de selección pueden variar por IME.
- Limpiar el portapapeles es intencional mientras la fricción está activa.
- El autocompletado o dictado que inserte más de 3 caracteres se trata como
  pegado.
- La sesión sobrevive a la recreación del host mientras el proceso viva; no se
  persiste tras muerte del proceso.
- La supresión total de Home/Recientes sigue dependiendo de kiosco/device owner.
