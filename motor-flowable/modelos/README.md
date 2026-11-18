# Modelos del proceso

| Archivo | Qué es |
|---|---|
| `institucion-de-capacitacion.bpmn20.xml` | El proceso (`process`) **corregido** (con las 3 `<association>` de compensación). Es la fuente de verdad. |
| `Organizacion_de_cursos.bar` | Lo que **se despliega** (`desplegar.sh`). Lo genera `corregir-asociaciones.py --bar`: `organizacionCursos.app`, `process.bpmn` y `dmn-reglaViabilidad.dmn`. |
| `Organizacion_de_cursos.zip` | App exportada del Modeler (proceso, tabla de decisión y formularios). Solo sirve para **editar** en el Modeler: trae las asociaciones rotas (ver abajo). |
| `reglaViabilidad.dmn` | La tabla de decisión correcta. Entra en el `.bar`. |
| `corregir-asociaciones.py` | Repara las asociaciones que rompe el Modeler y arma el `.bar` (Python 3, sin dependencias). |
| `desplegar.sh` | Despliega el `.bar` por REST y verifica que la versión nueva tenga las 3 asociaciones. |

> [!CAUTION]
> **No usar *Publish* en el Modeler** (ni desplegar el `.zip` tal cual). La versión que
> queda en el motor no tiene las asociaciones de compensación y se rompen
> `reservar_aula_virtual`, `contratar_equipamiento` y `evaluar_recursos` (ver «El bug»).

## Desplegar el modelo en tu Flowable (una vez, y cada vez que cambie)

Con Flowable levantado (`http://localhost:8080/flowable-ui`):

```bash
cd motor-flowable/modelos
./desplegar.sh
```

El script toma `FLOWABLE_URL` (por defecto `http://localhost:8080/flowable-ui`),
`FLOWABLE_REST_USUARIO` (`admin`) y `FLOWABLE_REST_CLAVE` (`test`). Hace el `POST` del
`.bar` a `app-api/app-repository/deployments` y después descarga el XML de la nueva
versión `latest` de `process`: si no tiene exactamente 3 `<association`, sale con error.

Las instancias que ya estaban en curso conservan la definición con la que arrancaron;
para probar un cambio, crear una instancia nueva. Para ver las que quedaron en versiones
viejas:

```bash
curl -s -u admin:<clave> "http://localhost:8080/flowable-ui/process-api/runtime/process-instances?processDefinitionKey=process&size=500"
# y, si hay que borrar alguna:
curl -s -u admin:<clave> -X DELETE "http://localhost:8080/flowable-ui/process-api/runtime/process-instances/<id>"
```

## Al modificar el modelo

1. **Editar en el Modeler.** Importar el proceso (**Processes → Import Process** →
   `institucion-de-capacitacion.bpmn20.xml`) o la app (**Apps → Import App** →
   `Organizacion_de_cursos.zip`), hacer los cambios, **Validate** y **Save**.
   - Al importar el XML, la tarea *Evaluar viabilidad* **pierde la referencia a la tabla
     de decisión**: seleccionarla y volver a elegir `reglaViabilidad` en *Decision table
     reference* antes de guardar.
   - Verificar la tabla: **Decision tables → `reglaViabilidad`** → la variable de entrada
     tiene que ser **`cantCursos`** (no `otrasInstanciasActivas`). Reglas: `< 5` →
     `resultadoViabilidad = true`, `>= 5` → `false`. Si cambia, exportarla y reemplazar
     `reglaViabilidad.dmn`.
2. **Exportar el XML** del proceso (en el editor, botón *Download / Export BPMN 2.0 XML*)
   y reemplazar `institucion-de-capacitacion.bpmn20.xml`.
3. **Corregir y armar el `.bar`:**
   ```bash
   python3 corregir-asociaciones.py institucion-de-capacitacion.bpmn20.xml --bar
   ```
   Tiene que decir `Reemplazadas: 3` (o 0 si el XML ya estaba bien) y `Asociaciones en el
   XML: 3`. Reemplaza los `sequenceFlow` por `<association>` sin reformatear el resto
   del archivo y valida que el XML parsee.
4. **Desplegar** con `./desplegar.sh` (nunca *Publish*).
5. Opcional: exportar la app (**Apps → Organizacion de cursos → Export**) y reemplazar el
   `.zip`, para que el resto del equipo tenga el modelo actualizado en su Modeler.
6. Commitear el XML, el `.bar` (y el `.dmn`/`.zip` si cambiaron) y avisar al equipo para
   que corran `desplegar.sh`.

## El bug del Modeler 6.8.0

Cuando el modelo tiene un **pool** (el nuestro tiene `participant_institucion`),
`BpmnJsonConverter` convierte las **asociaciones** en **sequenceFlow** al importar el zip
de la app, al exportar y al hacer **Publish**. Pasa con las tres flechas que van de los
eventos de borde de compensación a sus tareas compensadoras (`liberar_aula`,
`liberar_aula_virtual`, `liberar_equipamiento`).

Cuando termina una actividad con borde de compensación, el motor
(`BoundaryCompensateEventActivityBehavior`) busca esa asociación. Si no la encuentra lanza
`Compensation activity could not be found` y el completado se revierte. Síntomas:

- `contratar_equipamiento` y `reservar_aula_virtual` muestran *"Task ... can't be
  completed"* en el Task app.
- Completar `evaluar_recursos` con `requiereAula=true` da 503 en el backend
  (`/api/v1/tareas/{id}/completado`), porque el script `reservar_aula_fisica` corre en la
  misma transacción.

Diagnóstico rápido: contar las asociaciones de la versión desplegada (tienen que ser 3).

```bash
curl -s -u admin:<clave> "http://localhost:8080/flowable-ui/process-api/repository/process-definitions?key=process&latest=true"
# con el deploymentId de la respuesta:
curl -s -u admin:<clave> "http://localhost:8080/flowable-ui/process-api/repository/deployments/<deploymentId>/resourcedata/process.bpmn" | grep -c '<association '
```

Si al crear una solicitud el backend responde 503, el log del backend dice el motivo:

| Línea `Flowable respondió ...` | Solución |
|---|---|
| `No process definition found for key 'process'` | El modelo no está desplegado: `./desplegar.sh`. |
| `Unknown property used in expression: #{otrasInstanciasActivas ...}` | Se desplegó el DMN viejo: corregir `reglaViabilidad.dmn`, regenerar el `.bar` y desplegar. |
| `Compensation activity could not be found` | La versión desplegada no tiene las asociaciones (se usó *Publish*): `corregir-asociaciones.py --bar` y `./desplegar.sh`. |

## Configuración que necesita el backend

| Elemento | Configuración | Etapa |
|---|---|---|
| Proceso | id `process` (= `FLOWABLE_PROCESO_CLAVE`) | 5 |
| `reglaViabilidad` | entrada `cantCursos`, salida `resultadoViabilidad` | 5 |
| `registrar_elaborar_propuesta`, `revisar_propuesta` | assignee `Jefe-Administrativo` | 6–7 |
| `confeccionar_lista` | una sola tarea (no multi-instancia) | 6 |
| Gateway inclusivo después de `evaluar_recursos` | condiciones `${requiereAula}`, `${requierePlataforma}`, `${requiereEquipamiento}` en cada salida | 6 |
| `archivar_expediente` | assignee `Jefe-Administrativo` | 6 |
| `definir_docente` | assignee `Jefe-Docente` | 9 |
| Timer *Fecha programa* (`fecha_programa`) | `timeDate = ${fechaCurso}` (fecha ISO 8601) | 9 |
| `desarrollar_curso` | assignee **`${docenteId}`**, sin grupo candidato | 9 |
| `elaborar_informe` | assignee `Jefe-Docente` + formulario por referencia *Informe de resultados* con el campo **`informeResumen`** (el id exacto: es el nombre de la variable que lee el backend) | 9 |
| `enviar_informe` | hoy es script; a decidir si pasa a tarea de usuario de `Jefe-Administrativo` (el backend funciona con las dos opciones) | 9 |

### Cómo asignar a una variable (`${docenteId}`)

Seleccionar la tarea → **Assignments** → pestaña **Fixed values** → en **Assignee**
escribir `${docenteId}` y dejar vacíos *Candidate users* y *Candidate groups* → Save.
Para un usuario fijo (por ejemplo `Jefe-Docente`) usar **Identity store → Assigned to
single user**.

### Cómo crear un formulario por referencia

1. Modeler → **Forms → Create Form** (por ejemplo key `formInforme`).
2. Agregar un campo **Multiline text** con **Id `informeResumen`** (el id del campo se
   convierte en la variable del proceso) y marcarlo Required. Save.
3. En el proceso, seleccionar la tarea → propiedad **Referenced form** → elegir el
   formulario. No usar *Form properties* (formularios embebidos): la UI de esta versión
   no los muestra.
4. Volver a exportar la app y desplegar. Ojo: el `.bar` que arma `corregir-asociaciones.py`
   solo lleva el proceso y el DMN; si se agregan formularios hay que sumarlos al `.bar`.
