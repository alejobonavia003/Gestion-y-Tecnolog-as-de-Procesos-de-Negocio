# Referencias del modelo BPMN para el equipo

Separado entre lo que **ya está en el modelo** y lo que **todavía es provisorio**, para no apoyarse en algo que va a cambiar.

## Proceso, usuarios y grupos

| Dato | Valor |
| --- | --- |
| Process key | `process` hoy. **Se va a cambiar** (por ejemplo `organizacionCursos`). No dejarla fija en el código todavía |
| Usuarios con rol | `Jefe-Administrativo`, `Jefe-Docente` (pasar los ids de los otros 3 usuarios creados) |
| Grupos | `Equipo-Administrativo`, `Equipo-Docente` |
| Login | Con usuario y contraseña de Flowable IDM, sin cuentas propias en la BD |

## Mensajes (nombres exactos)

| Mensaje | Evento | Variables que debe llevar |
| --- | --- | --- |
| `solicitudRecibida` | Start del proceso | datos de la solicitud |
| `modificaciones` | Espera de respuesta | observaciones |
| `rechazo` | Espera de respuesta | ninguna |
| `aceptacion` | Espera de respuesta | **`fechaCurso`** |
| `cancelacion` | Event subprocess de cancelación | ninguna |

- La **nómina** no es un mensaje: "Recibir nómina" (`recibir_nomina`) se destraba con un *signal* sobre esa ejecución.
- Para encontrar la ejecución correcta, se pueden consultar las ejecuciones por `activityId` (nómina) o por nombre de suscripción de mensaje. Conviene probarlo.
- Al iniciar el proceso por REST, probar si alcanza con la key o hay que iniciar por el mensaje (todavía sin confirmar).
- **Falta en el modelo**: el start de "Cancelación" aún no tiene el mensaje `cancelacion` asociado.

## Variables

| Variable | Tipo | Quién la escribe | Estado |
| --- | --- | --- | --- |
| `otrasInstanciasActivas` | Integer | Delegate de viabilidad | Fijada (el DMN la lee) |
| `resultadoViabilidad` | boolean | DMN | Fijada (el gateway la lee) |
| `fechaCurso` | texto ISO 8601 con zona, ej. `2026-11-20T09:00:00-03:00` | App REST, junto con `aceptacion` | Fijada. Tiene que ser futura |
| `resultado` | texto | Listeners del modelo | Hoy solo `cancelado` |
| `docenteSeleccionado` | id de usuario | Formulario de "Definir docente" | Planeada |

- Valores previstos de `resultado`: `finalizado`, `cancelado`, `rechazado`, `sin_respuesta`, `no_viable`. El listener de fin de proceso los usa para actualizar el estado en la BD. Los textos de la BD tienen que coincidir con estos.
- Todavía **sin nombre definido**: la nómina y la lista definitiva, los recursos elegidos (los booleanos para aula física, virtual y equipamiento), el aula asignada y el informe. Se van a ir fijando.

## Tareas de usuario (ids para filtrar)

| Id | Responsable |
| --- | --- |
| `registrar_elaborar_propuesta`, `revisar_propuesta`, `archivar_solicitud` | Assignee `Jefe-Administrativo` |
| `preparar_materiales`, `evaluar_recursos`, `confeccionar_lista` | Candidate group `Equipo-Administrativo` |
| `definr_docente` | Assignee `Jefe-Docente` |

Tres cosas a tener en cuenta:

- **Typo**: `definr_docente` va a pasar a `definir_docente`. No fijarlo todavía.
- **Cambios pendientes**: `archivar_expediente` pasa a assignee `Jefe-Administrativo` (hoy tiene grupo), `elaborar_informe` a `Jefe-Docente`, y `desarrollar_curso` a `${docenteSeleccionado}`. Hoy estos tres figuran con grupo, así que no hay que basarse en eso.
- **Confeccionar lista** va a quedar asignada por un listener al crearse, por eso su responsable puede aparecer ya asignado.

El resto de las tareas de recursos tiene id provisorio (`sid-...`) y no necesita pantalla propia en el sitio.
