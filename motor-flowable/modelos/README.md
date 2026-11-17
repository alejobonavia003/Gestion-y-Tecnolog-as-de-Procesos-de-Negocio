# Modelos del proceso

| Archivo | Qué es |
|---|---|
| `Organizacion de cursos.zip` | App exportada del Modeler: proceso (`process`), tabla de decisión `reglaViabilidad` y formularios. **Es lo que se importa.** |
| `Organizacion de cursos.bar` | Export para despliegue directo (no hace falta para trabajar). |
| `reglaViabilidad.dmn` | La tabla de decisión correcta, como referencia. |

## Cargar la app en tu Flowable (una vez, y cada vez que cambie)

1. Flowable UI (`http://localhost:8080/flowable-ui`, usuario `admin`) → **Modeler → Apps
   → Import App** → elegir `Organizacion de cursos.zip`. Si pregunta, aceptar reemplazar
   los modelos existentes.
2. **Verificar la tabla de decisión** antes de publicar: **Decision tables →
   `reglaViabilidad`** → clic en el encabezado de la columna de entrada → la
   **variable** tiene que ser **`cantCursos`** (no `otrasInstanciasActivas`; la etiqueta
   visible no importa). Reglas: `< 5` → `resultadoViabilidad = true`, `>= 5` → `false`.
3. Abrir la app → **Publish**. Guardar un modelo no lo despliega: sin Publish el motor
   sigue usando la versión anterior.
4. Verificar:
   ```bash
   curl -s -u admin:<clave> "http://localhost:8080/flowable-ui/process-api/repository/process-definitions?key=process&latest=true"
   ```

Si al crear una solicitud el backend responde 503, el log del backend dice el motivo:

| Línea `Flowable respondió ...` | Solución |
|---|---|
| `No process definition found for key 'process'` | La app no está publicada (paso 3). |
| `Unknown property used in expression: #{otrasInstanciasActivas ...}` | Se publicó el DMN viejo: corregir la variable (paso 2) y volver a publicar. |

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
| `elaborar_informe` | assignee `Jefe-Docente` + formulario por referencia con el campo `informeResumen` | 9 (paso 3) |
| `enviar_informe` | tarea de usuario de `Jefe-Administrativo` (hoy es script) | 9 (paso 3) |

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
4. Publicar la app: los formularios referenciados se despliegan con ella.

## Al modificar el modelo

1. Editar en el Modeler, validar (**Validate**) y publicar.
2. Volver a exportar la app (**Apps → Organizacion de cursos → Export**) y reemplazar
   el `.zip` de esta carpeta (y el `.dmn` si cambió la tabla).
3. Commitear y avisar al equipo para que reimporten y publiquen.

Las instancias que ya estaban en curso conservan la definición con la que arrancaron;
para probar un cambio, crear una instancia nueva.

**Estado del `.zip` en esta rama:** todavía es el export del 11/11, con el DMN viejo
(`otrasInstanciasActivas`) y sin los cambios de la etapa 9. Hay que reemplazarlo por un
export actualizado.
