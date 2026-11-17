# Contrato para el primer recorrido integrado

Referencia fijada el 17/11/2026 en rama `app-materiales`, base `backend-lauty`
`228af43`. I1 documenta lo observado y propone los acuerdos para etapas 9–10;
las propuestas de esas partes requieren confirmación del compañero. I2 resuelve
almacenamiento Docker. I3–I6 todavía no están implementadas por este documento.

## Base verificable

- API institucional: `/api/v1`, sesión por cookie; usuario y empresa se toman de la
  sesión, no de campos arbitrarios enviados por la web.
- Proceso publicado: `process:4:51c6f829-e293-11f1-a462-721b934a1603`.
- Deployment: `50e21666-e293-11f1-a462-721b934a1603`, app `Organizacion de cursos`.
- [XML exportado del motor](../motor-flowable/modelos/publicado-v4/proceso.bpmn20.xml)
  y [metadatos/huella](../motor-flowable/modelos/publicado-v4/revision.json).
- Correlación: `cursoId` UUID institucional, `businessKey=curso:<uuid>` y
  `processInstanceId` persistido. Las operaciones usan `taskId`/instancia exactos.
- Materiales: API Java 6.8.0 contra PostgreSQL del motor; la web solo orienta a la
  app de escritorio. No se agrega un endpoint REST para completar materiales.
- Las etapas 6 y 7 tienen validación Postman informada por el equipo; etapa 8 está
  implementada. Eso no significa que los formularios web ya estén integrados.

## IDs que la web debe consumir en I3

| ID publicado | Función/interfaz | Ajuste posterior necesario |
|---|---|---|
| `registrar_elaborar_propuesta` | Web, propuesta inicial | Sustituir `Propuesta` |
| `revisar_propuesta` | Web, nueva versión | Sustituir `Revisar`, enviar `versionBase` |
| `archivar_solicitud` | Web, archivo | Sustituir `Archivar` |
| `habilitar_acceso_empresa` | Actualmente humana | Acordar automatización con etapa 10 |
| `confeccionar_lista` | Web, lista definitiva | Sustituir `Lista`; quitar multiinstancia en BPMN |
| `evaluar_recursos` | Web, selección de recursos | Sustituir `Activity_12i2hpf` |
| `preparar_materiales` | App Java | Sustituir referencia web `Material` |
| `definir_docente` | Web, selección | Sustituir `Activity_1018bdz`; endpoint de etapa 9 |
| `desarrollar_curso` | Web, fin del dictado | Sustituir `Dictar`; asignar `${docenteId}` |
| `elaborar_informe` | Flowable UI | Actualmente grupo docente; asignar al jefe y acordar persistencia del resumen |
| `enviar_informe` | Script automático en v4 | No tratarlo como tarea humana web |
| `archivar_expediente` | Web, tarea de cierre | Tiene ID propio; asignar al jefe administrativo |

Recursos publicados conservan IDs técnicos: aula física
`sid-EF3846A7-97AF-4125-BD28-2C448092597D`, equipamiento
`sid-47962569-4911-4D06-94FD-21AE786C3BBF` y aula virtual
`sid-CA7FB99A-93C3-4E18-A119-B6BBE442249A`. No renombrarlos en un solo consumidor:
coordinar los cambios de I4 y publicar juntos modelo y catálogo. En v4 la física
es humana y la virtual automática; hay que invertir responsabilidades para
ajustarse al contrato previsto. Sus liberaciones también deben corresponderse.

## Variables y mensajes

| Dato | Contrato de integración | Estado actual |
|---|---|---|
| `cantCursos` | Conteo numérico calculado por backend antes del inicio | Implementado |
| `propuestaVersion` | Entero, versión vigente | Backend de propuestas lo fija |
| `recordatorios` | Entero, reiniciar en 0 por versión | Backend lo reinicia; lógica de recordatorios pendiente |
| `respuestaEmpresa` | `ACEPTADA`, `RECHAZADA`, `MODIFICACIONES` | Propuesta de contrato de etapa 10 |
| `observacionesEmpresa` | Texto al pedir modificación | Consumido por detalle de revisión |
| `fechaCurso` | Instante ISO 8601 con offset, por ejemplo `2026-12-01T09:00:00-03:00` | Timer v4 ya usa `${fechaCurso}`; etapa 10 debe guardarla también en Curso |
| `participantes` | Lista `[{"nombre":"Ana Pérez"}]`, variable REST tipo `json` | Backend usa este formato; BPMN pide `nominaParticipantes` y debe corregirse |
| `requiereAula`, `requierePlataforma`, `requiereEquipamiento` | Booleanos, al menos uno verdadero | Backend valida; faltan condiciones de salidas inclusivas en v4 |
| `docenteId` | ID IDM miembro de `Equipo-Docente` | Etapa 9 debe validarlo y BPMN usarlo como assignee |
| `resultadoProceso` | `FINALIZADO`, `CANCELADO`, `RECHAZADO`, `SIN_RESPUESTA`, `NO_VIABLE` | Unificar; la cancelación todavía escribe `resultado='cancelado'` |

Los nombres de mensajes realmente declarados en v4 son **`aceptacion`**, **`rechazo`**,
**`modificaciones`**, **`cancelacion`** y `solicitudRecibida`. No usar
`cancelacionEmpresa` suponiendo que ya está en esta versión. Revisar la asociación
real de cancelación y la declaración de su error al implementar ese escenario.

`recibir_nomina` es actualmente una **receiveTask**, no un evento suscrito a un
mensaje `nomina`. Para I4 se propone convertirla en evento intermedio con mensaje
`nomina`, y usar desde etapa 10 la misma correlación REST de los otros mensajes.
La alternativa es conservar receiveTask y dispararla con `trigger`; ambas formas
son posibles, pero el backend y el modelo tienen que elegir la misma. No mezclar
`messageEventReceived` con una actividad que no tiene suscripción de mensaje.

Para comunicaciones: seleccionar ejecución por `processInstanceId` y nombre
acordado; rechazar comunicación fuera de estado o propuesta desactualizada.
La propuesta de etapa 10 usa `comunicacionId` para idempotencia. El contrato de
solicitudes ya usa `Idempotency-Key`; son operaciones distintas.

## Preguntas para el compañero antes de I4–I5

Podés enviarle este texto:

> Estoy preparando web/BPMN mientras vos terminás 9–10. La base es `process` v4.
> Necesitamos confirmar estos puntos antes de integrar:
>
> 1. **Nómina:** ¿hacemos `recibir_nomina` como evento de mensaje `nomina`? Yo adapto
>    el BPMN y vos correlacionás ese mensaje desde etapa 10. La lista sería
>    `participantes=[{"nombre":"..."}]` como JSON, sin `nominaParticipantes` ni
>    multiinstancia. Si ya lo resolviste con `trigger`, avisame para no cambiarlo.
> 2. **Aceptación/cuenta:** ¿tu aceptación guarda `fechaCurso` en Flowable y en
>    Curso, marca la propuesta `ACEPTADA`, crea/reutiliza CuentaEmpresa y cambia
>    el curso a `EN_PREPARACION`? ¿Quién y cómo reemplaza/completa la tarea humana
>    `habilitar_acceso_empresa` para que no quede bloqueada?
> 3. **Estados intermedios:** ¿qué mecanismo actualiza Curso cuando terminan las
>    ramas, llega la fecha y se completa el dictado? Propongo que el backend
>    actualice lo que pasa por sus endpoints y que un componente del motor cubra
>    cambios que ocurren solo en Flowable, incluida la app Java. Definamos un único
>    responsable por transición para que no haya actualizaciones contradictorias.
> 4. **Docente e informe:** ¿etapa 9 entrega `contexto.docentes`, valida `docenteId`
>    y completa las tareas específicas? Yo configuro `${docenteId}` y el timer
>    `${fechaCurso}`. ¿Cómo llega el resumen del informe desde Flowable UI a
>    `Curso.informeResumen` y cuándo pasa a `EN_CIERRE`?
> 5. **Cierre:** ¿hacés vos `FinProcesoListener` y el mecanismo para persistir el
>    resultado, o lo hacemos de este lado? Hay que definirlo antes de I5. El
>    `FINALIZADO` debe persistirse cuando termine el proceso después de sus
>    liberaciones, no simplemente cuando se archive el expediente.
> 6. **Integración:** pasame rama/commit y contratos finales de 9–10. Yo te paso
>    esta tabla y luego el XML corregido. Publicamos juntos y arrancamos una
>    instancia nueva, sin migrar ni borrar las anteriores.

Estas preguntas fijan decisiones de integración, no requisitos nuevos. No hace
falta esperar a todas las respuestas para avanzar en PDFs e IDs web; sí antes
de cambiar la recepción de nómina o el mecanismo de sincronización terminal.

## Reparto de transiciones a confirmar

| Transición | Responsable propuesto |
|---|---|
| Solicitud → `EN_ACUERDO`/`NO_VIABLE` inmediato | Backend existente |
| Aceptación → `EN_PREPARACION`, fecha y cuenta | Backend etapa 10 |
| Fin de preparación → `CONFIRMADO_PENDIENTE_INICIO` | Evento/listener del motor, también si la última rama termina desde Java |
| Timer alcanzado → `EN_DESARROLLO` | Motor |
| Fin de dictado → `EN_ELABORACION_INFORME` | Backend etapa 9, coordinado con motor |
| Informe listo → `EN_CIERRE` | Integración de informe por acordar |
| Fin real y liberaciones → `FINALIZADO` | Sincronización terminal por acordar |

Por ejemplo, no alcanza con actualizar estados solo desde el backend: si la
última rama es materiales, el avance lo dispara la JVM de nuestra app Java.
Cualquier listener/delegate que continúe ejecutándose desde allí debe estar
incluido en su classpath y tener configuración compatible.

## Estado de I1 e I2

- [x] I1: copia real del BPMN publicado y metadatos de referencia guardados.
- [x] I1: IDs, variables, diferencias y decisiones pendientes documentados.
- [x] I2: directorio de PDFs escribible por UID/GID `10001`, sin ejecutar como root.
- [x] I2: volumen persistente, configuración explícita y guía de operación.
- [x] I2: PDF generado/descargado por API y conservado tras recrear el backend, con el mismo SHA-256; fixtures eliminados.
- [ ] Confirmación del compañero sobre nómina, transiciones y cierre antes de I4–I5.

La prueba real y el mantenimiento del volumen se describen en
[Almacenamiento de PDFs en Docker](ALMACENAMIENTO_PDF_DOCKER.md).
