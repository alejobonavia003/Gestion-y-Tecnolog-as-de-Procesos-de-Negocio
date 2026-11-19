# Motor Flowable

Responsable: **Alejo**.

Todo lo que se carga en el Flowable UI 6.8 del `docker-compose.yml` (contenedor
`flowable-persistente`, datos en el Postgres `flowable-postgres`). Esta carpeta no es un
módulo Maven ni levanta un motor propio.

| Carpeta | Contenido |
|---|---|
| [idm/](idm/) | `crear-usuarios.sh`: crea los grupos `Equipo-Administrativo` y `Equipo-Docente` y los usuarios `Jefe-Administrativo`, `empleado-administrativo`, `Jefe-Docente`, `docente-1` y `docente-2` (clave `1234`). Es idempotente. |
| [modelos/](modelos/) | Proceso `institucion-de-capacitacion.bpmn20.xml`, `.bar` desplegable (`Organizacion_de_cursos.bar`), DMN `reglaViabilidad.dmn`, `corregir-asociaciones.py` y `desplegar.sh`. |
| [extensiones/](extensiones/) | Módulo Maven (Java 11) con los listeners `AsignarListaListener` y `FinProcesoListener`, compilado en `flowable-extensiones.jar`. |

## Usuarios y grupos

Con Flowable levantado y el `.env` en la raíz (lee `FLOWABLE_REST_USUARIO` y
`FLOWABLE_REST_CLAVE`), desde la raíz del repo:

```bash
bash motor-flowable/idm/crear-usuarios.sh
```

Usar `bash` explícitamente: el archivo no tiene permiso de ejecución en git y en
shells como fish `./crear-usuarios.sh` falla. Si el script da `orden no encontrada`
apuntando a una línea del `.env`, esa línea tiene un espacio alrededor del `=`.

## Extensiones implementadas (listeners Java)

Ubicadas en [extensiones/](extensiones/), compiladas con Java 11 (`maven.compiler.release=11`):

- `ar.edu.cursos.motor.AsignarListaListener` (`TaskListener`, evento `create` en `confeccionar_lista`):
  consulta el grupo `Equipo-Administrativo`, cuenta las tareas activas **asignadas directamente**
  a cada integrante (sin contar tareas candidatas) y asigna la tarea al de menor carga (desempate por ID alfabético).
- `ar.edu.cursos.motor.FinProcesoListener` (`ExecutionListener`, evento `end` en `<process id="process">`):
  lee `cursoId` (o el `businessKey`) y `resultadoProceso` (`FINALIZADO`, `CANCELADO`, `RECHAZADO`,
  `CERRADO_SIN_RESPUESTA` o `NO_VIABLE`) y actualiza `curso.estado` en la base `institucion` por JDBC.

### Compilar e instalar en Docker

```bash
mvn -B -f motor-flowable/extensiones/pom.xml clean package
cp motor-flowable/extensiones/target/flowable-extensiones.jar motor-flowable/extensiones/flowable-extensiones.jar
docker cp motor-flowable/extensiones/flowable-extensiones.jar flowable-persistente:/app/WEB-INF/lib/flowable-extensiones.jar
docker restart flowable-persistente
```

*(Si en algún momento se recrea el contenedor `flowable-persistente` desde cero, volver a copiar el `.jar` y reiniciar).*

Dependencias: [acuerdo de integración](../docs/MODELO_Y_INTEGRACION.md#acuerdo-de-integración) e IDs de
tareas centralizados en `backend/.../services/TareasProceso.java`.
