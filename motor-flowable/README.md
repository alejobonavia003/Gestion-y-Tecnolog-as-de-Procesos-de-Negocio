# Motor Flowable

Responsable: **Alejo**.

Todo lo que se carga en el Flowable UI 6.8 del `docker-compose.yml` (contenedor
`flowable-persistente`, datos en el Postgres `flowable-postgres`). Esta carpeta no es un
módulo Maven ni levanta un motor propio.

| Carpeta | Contenido |
|---|---|
| [idm/](idm/) | `crear-usuarios.sh`: crea los grupos `Equipo-Administrativo` y `Equipo-Docente` y los usuarios `Jefe-Administrativo`, `empleado-administrativo`, `Jefe-Docente`, `docente-1` y `docente-2` (clave `1234`). Es idempotente. |
| [modelos/](modelos/) | App exportada del Modeler (proceso `process`, DMN `reglaViabilidad`, formularios). Ver su README para importarla y publicarla. |
| `extensiones/` | Lugar previsto para el `.jar` de listeners (todavía vacío). |

## Usuarios y grupos

Con Flowable levantado y el `.env` en la raíz (lee `FLOWABLE_REST_USUARIO` y
`FLOWABLE_REST_CLAVE`), desde la raíz del repo:

```bash
bash motor-flowable/idm/crear-usuarios.sh
```

Usar `bash` explícitamente: el archivo no tiene permiso de ejecución en git y en
shells como fish `./crear-usuarios.sh` falla. Si el script da `orden no encontrada`
apuntando a una línea del `.env`, esa línea tiene un espacio alrededor del `=`.

## Extensiones pendientes (listeners Java)

- `AsignarListaListener`: asigna `confeccionar_lista` al administrativo con menos tareas.
- `FinProcesoListener`: escribe el estado final del curso en la base `institucion`.

Se compilan en un `.jar` (Java 11), se copian a `flowable-persistente:/app/WEB-INF/lib/`
y se reinicia el contenedor (ver `docs/notas_y_tips_de_clases_grabadas_sobre_el_proyecto.md`).

Dependencias: [acuerdo de integración](../docs/ACUERDO_DE_INTEGRACION.md) e IDs de
tareas centralizados en `backend/.../services/TareasProceso.java`.
