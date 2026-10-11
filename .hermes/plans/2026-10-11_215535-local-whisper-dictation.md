# Dictado local con Whisper — Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. If unavailable, load the available TDD, Thumb-Key and review skills and use fresh sequential implementation subagents with separate specification and quality review. Never invent an unavailable skill.

**Goal:** Añadir dictado completamente local al fork de Thumb-Key conservando el modo de edición y las macros personales.

**Architecture:** Dominio Kotlin independiente de Android, con puertos para grabación, reconocimiento, almacenamiento de modelos y escritura en el editor. Adaptadores Android para AudioRecord e InputConnection y un adaptador JNI pequeño para whisper.cpp. IMEService es dueño de la sesión de dictado; Compose solo representa estado y despacha acciones.

**Tech Stack:** Kotlin, Jetpack Compose, Room existente, coroutines, Android AudioRecord, CMake/NDK, whisper.cpp fijado a un commit, modelos Whisper GGML multilingües descargados por HTTPS.

---

## 0. Pausa obligatoria y autorización

**Este archivo es el entregable de planificación. El usuario ha pedido cambiar de modelo antes de implementar.**

- El alcance está confirmado; NO autoriza ejecutar ahora la implementación.
- No crear rama, instalar dependencias, descargar modelos, lanzar agentes, compilar, hacer commits ni push hasta que el usuario indique reanudar.
- Al reanudar, leer este archivo completo y cargar `thumb-key-development`, `thumbkey-development`, `android-ime-development`, `test-driven-development`, `requesting-code-review` y las habilidades de emulador/Git que correspondan.
- Ejecutar tareas de implementación con agentes nuevos **uno a uno**, no varios modificando el repositorio simultáneamente. Revisar especificación, calidad y pruebas; commit por unidad antes de continuar.
- No abrir PR upstream ni mezclar esta feature personal en main. Push/PR solo según la autorización de entrega vigente; el presente encargo es únicamente el plan.

## 1. Base real comprobada

Repositorio: `/workspace/repos/thumb-key`.

- Origin: `https://github.com/Ironjanowar/thumb-key.git`.
- Upstream: `https://github.com/dessalines/thumb-key.git`.
- Rama actual: `feat/personal-macros`.
- HEAD completo: `984ac26160dc28fe3e04eb7ef2c4d0fa1b3c0fed`.
- `git ls-remote --heads origin` confirmó ese mismo tip remoto.
- `82acee25149cfa1477109674a53cdd95a5b27fa3` (`feat/emacs-editing-mode`) es antecesor del tip de macros, comprobado con merge-base.
- Árbol limpio antes de escribir este plan; la única escritura de esta sesión es el plan.
- Build: minSdk 24, compileSdk/targetSdk 37, Java 17; debug `com.dessalines.thumbkey.debug`; versión actual 5.1.18, versionCode 188. No cambiar las versiones de release para esta prueba.
- Room AppDb está en versión 29; el registro de AppSettings, repositorio y ViewModel comparten `db/AppDb.kt`.
- El manifest actual no declara INTERNET ni RECORD_AUDIO. Añadir ambos explícitamente para esta feature y explicar que internet se usa solo para descargar modelos.
- No se encontró AGENTS.md del proyecto en la exploración; volver a comprobar instrucciones aplicables al reanudar.

Comprobaciones iniciales al reanudar:

```bash
cd /workspace/repos/thumb-key
git status --short --branch
git remote -v
git rev-parse HEAD
git ls-remote --heads origin
git merge-base --is-ancestor 82acee25149cfa1477109674a53cdd95a5b27fa3 HEAD
```

Si sigue siendo la base acordada y solo está este plan, crear `feat/local-whisper-dictation` desde el tip de macros. Si hay cambios posteriores, comprobar si contienen las features previas y resolver la base explícitamente; no descartar trabajo ni sustituirla por main/upstream. No crear la rama durante la fase de planificación.

## 2. Contrato confirmado por el usuario

### Entrada y uso

1. Función opcional, desactivada de serie.
2. Deslizamiento **hacia la derecha en la tecla numérica** inicia el dictado. Icono pequeño de micrófono cuando esté habilitado.
3. Respetar gestos personalizados existentes. No añadir barra permanente, quitar teclas ni cambiar la geometría habitual.
4. El gesto abre un panel y comienza a grabar. Controles **Parar** y **Cancelar**, y contador visible.
5. Parar termina la captura, transcribe, inserta el resultado y devuelve el teclado normal. No hay transcripción incremental.
6. Límite de **60 segundos**; al alcanzarlo se detiene y transcribe automáticamente.
7. Texto editable con el teclado normal después de insertarlo.
8. Una selección inicial se sustituye únicamente cuando la transcripción termina correctamente. Cancelación o error no alteran texto ni selección.
9. Ocultar el teclado o cambiar de campo/aplicación cancela grabación o transcripción, descarta el audio y no inserta texto. El micrófono no sigue escuchando oculto.

### Ajustes y modelos

10. Solicitar permiso de micrófono al activar la función desde ajustes; si se deniega, el teclado continúa funcionando y se explica cómo habilitarlo.
11. Español por defecto; selector de idioma y detección automática como alternativa.
12. Catálogo cerrado: **Tiny, Base y Small multilingües**, Base recomendado.
13. Descarga explícita desde Hugging Face, mostrando tamaño y progreso; selección y borrado de modelos descargados.
14. No descargar nada sin acción del usuario. Tras descargar el modelo, dictado sin conexión.
15. No subir audio ni conservar grabaciones tras usarlas. No añadir un LLM de reescritura ni una API externa de reconocimiento.

### Dispositivo y entrega

16. Teléfono del usuario: OnePlus 13R, Snapdragon 8 Gen 3, 12 GB RAM física (+12 GB virtual), OxygenOS 16.0.10. No inferir otros detalles del sistema a partir de ese número.
17. Entregar APK debug instalable que mantenga edición y macros. No prometer latencia ni consumo antes de medir en el dispositivo.

## 3. Defaults de ingeniería propuestos, NO decisiones expresamente votadas

Estos defaults completan detalles técnicos; no ampliar el alcance silenciosamente. Informar si una restricción obliga a cambiar el comportamiento visible acordado.

- Usar variantes GGML estándar sin cuantizar inicialmente: tiny ~75 MiB, base ~142 MiB, small ~466 MiB, según documentación consultada. El catálogo mostrará tamaños exactos verificados para los artefactos elegidos, no estas aproximaciones. Cuantización es optimización posterior si las mediciones la justifican; no sustituir formatos sin verificar compatibilidad.
- CPU como backend inicial; no prometer GPU/NPU. ABI arm64-v8a para el teléfono y x86_64 para el emulador. Revisar el soporte anterior antes de eliminar cualquier ABI existente; fallar con mensaje claro en dispositivos no compatibles, no al iniciar todo el teclado.
- En campos password, number-password y TYPE_NULL, deshabilitar dictado con explicación. No aplicar por copia las restricciones completas de macros: el dictado debe funcionar en editores Compose y dentro del campo de prueba de Thumb-Key cuando el editor sea compatible.
- Cambios de selección/texto externos durante una sesión invalidan el destino y cancelan; una notificación duplicada sin cambio real no debe cancelar. Preferir seguridad a escribir en una selección diferente.
- No insertar resultados vacíos ni puntuación inventada por la aplicación; silencio se trata como ausencia de voz, sin modificar selección. Usar detección de energía conservadora como filtro inicial y evaluar filtros del motor; no asegurar que Whisper nunca alucina.
- Texto reconocido se inserta tal cual, sin traducción, sin expansión de macros, sin pulsar Enter/enviar, sin portapapeles y sin espacios extra automáticos en esta versión.
- No grabar hasta que el modelo esté disponible y el motor listo. Mostrar «Preparando…» antes de «Escuchando…» si la carga tarda. AudioRecord falla cerrado ante permiso revocado, micrófono ocupado o error de captura.
- Panel reemplaza temporalmente la cuadrícula; no es overlay sobre teclas interactivas. Durante transcripción hay estado visible y Cancelar. Error ofrece volver al teclado; no retiene audio para reintentos.
- Audio solo en memoria, PCM mono 16 kHz para Whisper. Liberar referencias tras consumo/cancelación; no prometer borrado físico inmediato de RAM gestionada. Ningún audio/transcripción en logs, crash metadata o archivos temporales.
- Una inferencia activa a la vez; motor en dispatcher dedicado. Cancelación debe abortar trabajo nativo mediante mecanismo soportado y evitar liberar el contexto mientras está en uso. No basta cancelar una coroutine alrededor de una llamada C bloqueante.
- Modelos en almacenamiento privado excluido de backup, publicación atómica tras verificar SHA-256 y tamaño. Estado «descargado» derivado de archivo válido, no de un booleano persistido independiente.
- Una descarga a la vez, cancelable y tolerante a proceso muerto: nunca exponer `.part` como modelo listo. Descarga en primer plano de ajustes; no incorporar servicios de fondo persistentes salvo necesidad demostrada. Reiniciar descarga incompleta es aceptable inicialmente, sin prometer reanudación por rangos.
- Sin token de Hugging Face para artefactos públicos. URLs/revisión/bytes/SHA-256 fijados; no aceptar URLs arbitrarias ni ejecutar código remoto. Permitir redirecciones HTTPS legítimas del CDN de HF de forma controlada, nunca downgrade a HTTP.
- Al borrar el modelo seleccionado, cancelar su uso de forma segura y dejar selección sin modelo listo; no descargar/reemplazarlo automáticamente. Un modelo en inferencia no se borra físicamente hasta soltar el handle.
- Idiomas: nombres traducidos y códigos soportados por Whisper, incluyendo `es` y auto; modo `transcribe`, nunca `translate`. Fijar idioma/modelo al comenzar cada sesión.

## 4. Evidencia y referencias de implementación

- Motor y Android: https://github.com/ggml-org/whisper.cpp
- Ejemplo Android: https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android
- Formatos/modelos: https://github.com/ggml-org/whisper.cpp/blob/master/models/README.md
- Alojamiento: https://huggingface.co/ggerganov/whisper.cpp/tree/main

La viabilidad general y los tamaños vienen de documentación; **no se ha compilado ni medido Whisper en este proyecto**. Antes de fijar NDK, CMake, revisión JNI o checksums, leer las fuentes exactas. No inventar hashes, APIs ni resultados. Comprobar restricciones Android vigentes de RECORD_AUDIO en IME visible y oculto en https://developer.android.com/; no resolver problemas manteniendo grabación en segundo plano ni lanzando otra app que provoque cancelación al perder el editor.

## 5. Mapa concreto de archivos

Rutas relativas a `/workspace/repos/thumb-key`.

Existentes a integrar:

- `app/build.gradle.kts`: native build, dependencias estrictamente necesarias, test config.
- `app/proguard-rules.pro`: keep JNI solo si requerido.
- `app/src/main/AndroidManifest.xml`: permisos.
- `app/src/main/java/com/dessalines/thumbkey/IMEService.kt`: sesión, lifecycle y editor.
- `app/src/main/java/com/dessalines/thumbkey/ThumbkeyApplication.kt`: repositorios compartidos apropiados, sin poseer una sesión de grabación global.
- `app/src/main/java/com/dessalines/thumbkey/MainActivity.kt`: navegación y permiso en Activity.
- `app/src/main/java/com/dessalines/thumbkey/db/AppDb.kt` y `db/Migrations.kt`: settings/migración no destructiva.
- `app/src/main/java/com/dessalines/thumbkey/utils/Types.kt`: acción de dictado.
- `app/src/main/java/com/dessalines/thumbkey/utils/Utils.kt`: dispatch único de acciones.
- `app/src/main/java/com/dessalines/thumbkey/utils/KeyboardModificationService.kt`: serialización YAML y transformación después de personalizaciones.
- `app/src/main/java/com/dessalines/thumbkey/ui/components/keyboard/KeyboardScreen.kt`: panel transitorio y configuración.
- `app/src/main/java/com/dessalines/thumbkey/ui/components/settings/SettingsScreen.kt`: entrada a dictado.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml`: todos los textos y accesibilidad.
- `app/src/debug/res/values/strings.xml`: marcador de APK distinto de R3, solo para entrega.
- `app/schemas/`: esquema Room exportado siguiente.

Nuevos propuestos (crear solo si su responsabilidad lo necesita):

- `app/src/main/java/com/dessalines/thumbkey/dictation/DictationState.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/DictationController.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/DictationPorts.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/DictationFieldPolicy.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/AndroidDictationEditorPort.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/AndroidAudioRecorder.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/WhisperEngine.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/WhisperNative.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/models/WhisperModelCatalog.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/models/ModelRepository.kt`
- `app/src/main/java/com/dessalines/thumbkey/dictation/models/ModelDownloader.kt`
- `app/src/main/java/com/dessalines/thumbkey/keyboards/DictationModeEntry.kt`
- `app/src/main/java/com/dessalines/thumbkey/ui/components/keyboard/DictationPanel.kt`
- `app/src/main/java/com/dessalines/thumbkey/ui/components/settings/dictation/DictationSettingsScreen.kt`
- `app/src/main/java/com/dessalines/thumbkey/ui/components/settings/dictation/DictationSettingsViewModel.kt`
- `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/whisper_jni.cpp`
- `third_party/whisper.cpp/`: dependencia fijada, licencia y método reproducible de obtención. Decidir vendoring/submodule verificando convenciones; nada de descargar HEAD durante cada build.
- `docs/local-dictation.md`: guía, privacidad, límites, dependencias nativas y reproducción de pruebas.

## 6. Invariantes de arquitectura

### Sesión y editor

El controlador tiene estados mutuamente excluyentes: Idle, Preparing, Recording, Transcribing, Error. Contiene identificador de sesión que invalida resultados antiguos; no proliferar flags paralelos como `isRecording/isProcessing/isCancelled`.

- Cada resultado lleva el identificador de la sesión que lo produjo.
- Iniciar nueva sesión invalida la anterior.
- InputConnection queda encapsulada en adaptador, no guardada en trabajo nativo ni usada desde worker.
- Antes de `commitText`, en hilo UI, confirmar misma sesión de entrada, campo y destino. Mismo package/fieldId por sí solos NO bastan: Android puede reutilizarlos.
- Una selección no se borra al iniciar/parar. Usar commit único al terminar, no borrar y luego insertar. Verificar el destino con información fresca disponible; no exigir siempre `getExtractedText` completo, porque eso excluye editores Compose.
- Investigar `getSelectedText`, callbacks de selección y snapshots limitados. Si no se puede confirmar una selección que se pretende reemplazar, fallar cerrado sin modificarla. Documentar la limitación concreta, no «no soporta Compose» por defecto.
- Reconciliar callbacks duplicados/atrasados contra información fresca; no copiar flags de supresión de selección frágiles. Cubrir editor que cambia sin callback o que devuelve snapshot incompleto.
- Cancelar desde `onFinishInputView`, `onFinishInput`, `onWindowHidden`, destrucción del servicio y nuevo input donde corresponda. No condicionar cancelación a `finishingInput == true` únicamente.
- No mutar el editor al cancelar. Preservar las llamadas existentes del text processor en `onFinishInputView`; detectar composición preexistente y definir integración sin doble commit ni pérdida del contenido.
- Coordinar `macroController.beforeExplicitEdit()` y edición al realizar el commit real; dictado no pasa por la ruta de cada tecla que expande macros.

### Grabación y motor

- Nunca AudioRecord, carga de modelo, IO de descarga o inferencia en UI thread.
- Terminar micrófono inmediatamente al pulsar Parar, antes de inferencia.
- Tiempo máximo con reloj monotónico y límite de muestras; timer y toque Parar simultáneos terminan una sola vez.
- Gestionar lecturas parciales, error/zero-read, interrupciones y release exactamente una vez.
- No abortar proceso Android por error JNI. Devolver error acotado y mantener teclado usable.
- Descargar/cargar modelos no afecta teclas normales; librería nativa se carga perezosamente.

### Tecla

`ESMessagEase.kt` usa `NUMERIC_KEY_ITEM`. En `CommonKeys.kt`, `textEditKeyItem()` ocupa arriba/copiar, arriba-izquierda/seleccionar todo, arriba-derecha/cortar, abajo-izquierda/undo, abajo-derecha/redo y abajo/pegar. Derecha queda libre. La transformación de edición ocupa izquierda.

Añadir transformación pura e inmutable, posterior a YAML y compatible con `installEditingModeEntry()`. Solo modificar anclas `ToggleNumericMode(true)` con derecha libre. No mutar constantes comunes ni enum ordinal de layouts. Mapear la acción nueva en Types, serialización YAML y dispatcher. IconDisplay evita que «ocultar símbolos» haga desaparecer el micrófono. En layouts sin hueco, no sobreescribir nada ni inventar ubicación alternativa.

## 7. Plan secuencial TDD

Cada tarea sigue RED → ejecución real del fallo → implementación mínima → GREEN → revisión de especificación → revisión de calidad → commit explícito. Los grupos siguientes se descomponen en esos pasos pequeños; no son una invitación a un único cambio gigante. No inventar número de tests ni salida esperada como si se hubiera ejecutado.

Prefijos de tests: `app/src/test/java/com/dessalines/thumbkey/`. El paquete raíz de tests coincide con producción.

### Tarea 1 — Baseline y riesgo nativo temprano

1. Revalidar base y crear rama solo tras autorización de reanudar.
2. Cargar skills y comprobar Java, SDK, NDK/CMake disponibles y límites de procesos.
3. Ejecutar baseline JVM/build antes de cambios; registrar errores preexistentes.
4. Elegir commit de whisper.cpp, verificar licencia y requisitos; fijarlo reproduciblemente.
5. Crear bridge mínimo y prueba real con fixture de voz conocida, tanto cargar/inferir/liberar como cancelar. El fixture tiene origen/licencia documentados; no sintetizar resultados.
6. Compilar arm64-v8a y x86_64 y verificar empaquetado de `.so`; comprobar compatibilidad 16 KiB de bibliotecas nativas con Android moderno.
7. Si Android IME/NDK resulta bloqueante, reportar antes de construir toda la UI. No sustituir por reconocimiento de Google o nube.

Archivos: native/Gradle indicados arriba. Tests nuevos `dictation/WhisperEngineContractTest.kt` para contrato con fake y smoke nativo Android separado; el fake no demuestra que JNI funcione. Incorporar instrumentation mínima solo si necesaria para hacer reproducible la prueba nativa.

### Tarea 2 — Catálogo verificable

Test `dictation/models/WhisperModelCatalogTest.kt`: exactamente tiny/base/small multilingües, ids únicos, Base recomendado, revisión fija, bytes positivos, SHA-256 con formato correcto, URL HTTPS, sin `.en`.

Después implementar catálogo con metadatos obtenidos y verificados de los artefactos elegidos. No escribir checksums de ejemplo ni convertir modelos dentro del móvil. Documentar revisión/origen.

### Tarea 3 — Descarga y almacenamiento

Tests `dictation/models/ModelRepositoryTest.kt` y `ModelDownloaderTest.kt`, usando transporte y filesystem temporales/controlados:

- éxito, progreso, respuesta sin Content-Length, HTTP error, corte de conexión;
- espacio insuficiente, cancelación, proceso interrumpido y `.part` residual;
- bytes/hash incorrectos nunca publican archivo listo;
- borrar seleccionado/no seleccionado, doble descarga y carrera borrado/inferencia;
- redirección HTTPS aceptada y downgrade rechazado;
- no borrar modelo válido previo por fallo de sustitución.

Implementar validación antes de rename atómico, UI no bloqueante, almacenamiento excluido de backup. La validez inicial debe comprobar integridad, no solo longitud.

### Tarea 4 — Ajustes y migración

Tests `db/DictationSettingsMigrationTest.kt`, ampliar `db/AppSettingsDefaultsTest.kt`.

Agregar habilitado=false, idioma=`es`, selección inicial Base como recomendación (NO como «descargado»). Usar patrones actuales de Room/partial updates y una migración explícita siguiente a 29 preservando settings, edición y base separada de macros. Exportar schema. Test de actualización real desde esquema previo y defaults de instalación nueva. No migración destructiva.

### Tarea 5 — Máquina de estados y puertos

Tests `dictation/DictationControllerTest.kt`, fakes de grabador, motor, reloj y editor. Casos obligatorios:

- start→preparing→recording→stop→transcribing→commit único→idle;
- cancelar en cada estado; resultados/error tardíos no reabren UI ni escriben;
- timeout exactamente una vez, concurrente con Stop;
- cambio de campo/ocultación/destrucción;
- error/resultado vacío conserva selección;
- modelo no listo/permiso ausente no inician micrófono;
- tap repetido no crea dos grabaciones ni motores;
- sesión nueva no recibe resultado de sesión vieja;
- modelo/idioma fijados por sesión.

Implementar puertos pequeños, estados inmutables y métodos cortos; separar Android del dominio.

### Tarea 6 — AudioRecord

Tests `dictation/AudioRecorderContractTest.kt` sobre adaptador de captura inyectable: lecturas parciales, límite, permiso revocado, busy/error, stop/cancel/release idempotente. Añadir prueba Android real del ciclo de AudioRecord mientras IME está visible.

Implementar PCM esperado por motor, memoria acotada y contador monotónico. Probar silencio/ruido sin insertar texto al usuario como si fuera voz reconocida. No persistir WAV de grabación real.

### Tarea 7 — Ciclo de vida del motor

Tests `dictation/WhisperEngineContractTest.kt`: carga diferida, inferencia serial, errores, cancelación, liberación segura, cambio de modelo y no acceso concurrente al contexto liberado. Probar bridge real con fixture después de cambios.

Conectar cancelación nativa soportada por la revisión elegida. Ajustar número de threads de forma conservadora sin saturar UI. Liberar modelo al salir del flujo según coste medido; no dejar memoria pesada permanente por defecto sin justificarlo.

### Tarea 8 — Seguridad de InputConnection e IMEService

Tests `dictation/DictationFieldPolicyTest.kt`, `dictation/AndroidDictationEditorPortTest.kt` (Robolectric donde necesario) y `dictation/DictationLifecycleTest.kt`.

Casos: cursor vacío, selección normal/invertida, texto Unicode/emoji, editor Compose sin extractedText completo, conexión inválida, campo cambia manteniendo mismo id, selección se mueve, callback duplicado/atrasado, fallo de commit, composing previo, password y TYPE_NULL. Commit real solo si destino sigue válido; nunca Enter ni macros automáticas.

Conectar hooks del servicio sin romper textProcessor, edición, macros ni inputRedirect de sketch. Dictado no se inicia cuando sketch es dueño del destino.

### Tarea 9 — Gesto y YAML

Crear `keyboards/DictationModeEntryTest.kt` y `utils/KeyboardModificationDictationActionsTest.kt`.

Primero tests de identidad deshabilitada, idempotencia, inmutabilidad, derecha ocupada respetada, izquierda edición conservada, YAML action round-trip y todos los KeyboardLayout.entries. Contar por código layouts afectados/no afectados, no adivinar. Verificar específicamente español MessagEase y que no se tocan copiar/cortar/pegar/undo/redo.

Implementar acción y transformación posterior a personalizaciones, pasar setting por la misma ruta que edición. Habilitado sin modelo ofrece explicación en lugar de grabar o descargar automáticamente.

### Tarea 10 — UI de ajustes y permisos

Crear tests `dictation/DictationSettingsViewModelTest.kt`: defaults, permiso aprobado/denegado/permanente/revocado, no descarga automática, progreso/error/cancelación/borrado, selección válida y persistencia tras reiniciar.

Implementar pantalla dedicada y navegación; solicitud de permiso en Activity vía API de resultados, no dialog Activity lanzada desde IME que rompa foco. Explicar dictado offline y permiso INTERNET. No bloquear uso normal de teclado ante denegación. Textos EN/ES y content descriptions. Mantener las pantallas existentes.

### Tarea 11 — Panel y wiring end-to-end

Implementar `DictationPanel.kt` y el cambio en KeyboardScreen. Probar estados Preparing/Recording/Transcribing/Error y acciones. UI muestra tiempo y Stop solo cuando procede; Cancelar accesible durante inferencia. Panel no tapa teclas que sigan recibiendo eventos por debajo.

Verificar en emulador el gesto real mediante touch/swipe, no solo invocando el controlador. Teclado normal reaparece tras éxito/cancelación/error. No agregar barra permanente ni cambiar tamaño habitual al desactivar la función.

### Tarea 12 — Regresiones, revisión y documentación

Ejecutar suite completa y lint/build, revisar privacidad de red/logs/backup, errores nativos y concurrencia. Revisión independiente de especificación y después calidad; arreglar y repetir pruebas afectadas.

Documentar `docs/local-dictation.md`: activación, modelos/tamaños, idioma, gesto, detener/cancelar, límite, cambio de campo, funcionamiento offline, consumo pendiente de medir, campos incompatibles y soporte de backup. No afirmar que backup incluye modelos (se excluyen), ni macros (su DB separada no está cubierta actualmente).

Preservar funciones anteriores y no refactorizar áreas ajenas. Al cierre final autorizado, el usuario prefiere eliminar `.hermes/plans/` completo, incluso planes antiguos tracked; conservar este archivo durante implementación y no eliminarlo ahora.

### Tarea 13 — APK y prueba en dispositivo

Dar etiqueta debug inequívoca posterior a R3 (por ejemplo `Thumb-Key (Debug R4 Whisper)`, comprobar que no esté ya usada), APK con nombre equivalente y SHA-256 real. No sobrescribir release ni cambiar versionCode/versionName de release.

Entregar instalación, habilitación y re-selección del IME, distribución español MessagEase y ajustes de dictado. Capturas en español desde campo neutral. Especificar qué pasó en JVM/emulador y qué queda por validar en OnePlus.

## 8. Comandos de verificación

Ejecutar con emulador APAGADO durante builds; el sandbox tiene límite de procesos y el emulador puede agotar threads. No ocultar exit code con pipes.

```bash
cd /workspace/repos/thumb-key
/usr/local/bin/mise exec java@17 -- ./gradlew --no-daemon --no-configuration-cache testDebugUnitTest --tests 'com.dessalines.thumbkey.dictation.*'
/usr/local/bin/mise exec java@17 -- ./gradlew --no-daemon --no-configuration-cache formatKotlin lintKotlin testDebugUnitTest lintDebug assembleDebug
```

Ejecutar también los nuevos tests de db, keyboards y utils; la suite completa debe incluirlos. Parsear XML en `app/build/test-results/testDebugUnitTest/` para totales y fallos; no confundir BUILD SUCCESSFUL con cobertura nativa. Si se añade instrumentation, configurar runner/dependencias y ejecutar `connectedDebugAndroidTest` de forma que el build previo no compita con el emulador.

Emulador existente (revalidar entorno):

```bash
$ANDROID_HOME/emulator/emulator @fretboard-api37 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot-load -no-snapshot-save -cores 2 -memory 1536
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable com.dessalines.thumbkey.debug/com.dessalines.thumbkey.IMEService
adb shell ime set com.dessalines.thumbkey.debug/com.dessalines.thumbkey.IMEService
adb shell settings put secure show_ime_with_hard_keyboard 1
```

El emulador con `-no-audio` no prueba captura acústica. Usar fixture para inferencia Android y comprobar ciclo de permiso/captura con método apropiado; reservar micrófono real y latencia para teléfono. No presentar fixture inyectado como audio capturado realmente.

Después de **cada instalación o force-stop**, re-seleccionar el IME: Android puede volver al teclado anterior. Verificar paquete/etiqueta antes de valorar gestos. Verificar APK con `sha256sum app/build/outputs/apk/debug/app-debug.apk` y herramientas Android de inspección de ABI/permisos. Si hay push autorizado, verificar rama remota con `git ls-remote` y divergencia antes de afirmar publicado.

## 9. Matriz de aceptación real

- [ ] APK instala y conserva ajustes/macros de la build debug anterior, con migración no destructiva.
- [ ] Función desactivada: teclado/gestos anteriores idénticos, sin red ni micrófono ni carga del modelo.
- [ ] Permiso denegado y revocado no rompen teclado.
- [ ] Descarga real de un modelo; bytes/hash comprobados; error/cancelación no dejan modelo listo falso.
- [ ] Modelo descargado permite transcripción real con red deshabilitada.
- [ ] Tiny/Base/Small cargan con el formato elegido; al menos smoke nativo por modelo. Si no se verifica alguno, declararlo pendiente.
- [ ] Gesto derecha numérica probado en español MessagEase; izquierda edición y clipboard intactos.
- [ ] Stop corta micrófono antes de transcribir; Cancelar y ocultación liberan captura.
- [ ] Temporizador de 60 s y toque Stop no duplican resultados.
- [ ] Selección solo se reemplaza al éxito; error/silencio/cancelar conservan contenido.
- [ ] Cambio de app/campo, teclado oculto y resultado tardío jamás escriben en otro destino.
- [ ] EditText y Compose probados; restricciones de seguridad documentadas por caso real.
- [ ] Texto dictado no dispara macros ni envía formulario.
- [ ] Modo edición, macros manuales/inmediatas/separador, undo y sketch no regresan.
- [ ] Datos de voz y transcripciones ausentes en almacenamiento/logs/red por diseño e inspección verificable.
- [ ] Build, tests, lint y revisión independiente reales; informe distingue inspección, ejecución y pendientes.
- [ ] APK debug con etiqueta inequívoca y SHA-256 entregado.

Prueba posterior en OnePlus: frases españolas cortas y dictado de 60 s con Base/Small; medir tiempo de carga en frío, demora tras Parar, precisión percibida, memoria y calentamiento. Sin objetivos de velocidad inventados: informar mediciones y ajustar modelo recomendado con el usuario. No afirmar feature validada en teléfono hasta recibir evidencia de esa prueba.

## 10. Criterio de finalización y handoff

La implementación no está hecha por tener UI o mocks verdes: requiere motor nativo ejecutado con modelo real, descarga verificada, integración del IME ejercitada y APK generado. Si falta hardware, distinguir claramente la validación del emulador de la del OnePlus. Un bloqueo real se reporta; no se sustituye por salidas simuladas ni servicios online.

**Estado al guardar este plan:** requisitos confirmados, base y puntos de integración inspeccionados; ninguna implementación, rama nueva, dependencia nativa, descarga de modelo, build o prueba de Whisper ejecutada. Esperar instrucción explícita del usuario tras su cambio de modelo para reanudar.
