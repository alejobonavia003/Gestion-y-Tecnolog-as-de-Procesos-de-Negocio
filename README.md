# Gestión y Tecnologías de Procesos de Negocio

Proyecto de organización de cursos de capacitación para empresas, basado en BPMN y orientado a su implementación en **Flowable**.

Una institución recibe solicitudes de capacitación y gestiona su viabilidad, la negociación de una propuesta, la preparación del curso, el dictado y el cierre. El proceso contempla recordatorios, cancelaciones y compensaciones para liberar recursos.

## Estado actual

El repositorio contiene el enunciado, dos modelos BPMN y un entorno local con Docker Compose. Todavía no incluye el código de las aplicaciones externas, los listeners, las reglas de decisión ni los scripts de la base de datos propia de la institución.

Los modelos son material de referencia y trabajo. Que un archivo declare el proceso como ejecutable no garantiza que tenga todas las configuraciones necesarias para desplegarlo y completar los escenarios del enunciado.

## Contenido

| Archivo | Descripción |
| --- | --- |
| [`contexto/ProyectoCursos_V2.pdf`](contexto/ProyectoCursos_V2.pdf) | Enunciado, responsabilidades, requisitos y escenarios de demostración. |
| [`contexto/Proyecto_Organizacion_cursos.bpmn`](contexto/Proyecto_Organizacion_cursos.bpmn) | Modelo de referencia exportado desde Camunda Modeler; declara el proceso como no ejecutable. |
| [`contexto/Proyecto_Gestion_y_Tecnolog´ıas_de_Procesos_de_Negocio_version_2.bpmn20.xml`](contexto/Proyecto_Gestion_y_Tecnolog´ıas_de_Procesos_de_Negocio_version_2.bpmn20.xml) | Modelo exportado desde Flowable Modeler 6.8.0; declara el proceso como ejecutable. |
| [`docker-compose.yml`](docker-compose.yml) | Servicios de Flowable UI, PostgreSQL y pgAdmin. |
| [`env`](env) | Variables de conexión a PostgreSQL para el entorno local. |

## Proceso de negocio

1. **Evaluar viabilidad.** Una regla de decisión debe contar las otras instancias activas del proceso, incluyendo distintas versiones desplegadas. Con cinco o más, la solicitud se cierra como no viable.
2. **Acordar la propuesta.** El jefe administrativo registra y elabora la propuesta. La empresa puede aceptarla, rechazarla o pedir modificaciones. Cada versión admite hasta dos recordatorios, separados por cinco días; otro período sin respuesta conduce al archivo.
3. **Preparar el curso.** Se gestionan en paralelo la lista de participantes, los materiales y los recursos. Debe seleccionarse al menos un recurso entre aula física, aula virtual y equipamiento especial, y completarse todas las alternativas elegidas.
4. **Atender cancelaciones.** Durante la preparación, una cancelación interrumpe el trabajo y compensa únicamente los recursos cuya gestión ya haya finalizado correctamente.
5. **Desarrollar y cerrar.** El jefe docente selecciona al docente responsable. El inicio depende de la fecha acordada; luego se registra la finalización, se elabora y envía el informe, se archiva el expediente y se liberan los recursos.

La institución cuenta con dos administrativos y tres docentes. Cada grupo incluye a su jefe. Las tareas compartidas requieren reclamo previo; las asignadas directamente solo pueden ser completadas por su responsable.

## Entorno local

### Requisitos

- Docker Engine en ejecución y Docker Compose con el comando `docker compose`.
- Puertos locales `8080` y `5050` disponibles.

### Configuración

Revisar las variables de [`env`](env):

```dotenv
POSTGRES_DB=flowable
POSTGRES_USER=flowable
POSTGRES_PASSWORD=<contraseña-local>
```

El archivo se llama `env`, sin punto inicial, por lo que debe indicarse con `--env-file` en los comandos de Compose. Las credenciales incluidas son de desarrollo local.

Antes de iniciar pgAdmin, corregir `PGADMIN_DEFAULT_EMAIL` en `docker-compose.yml`: actualmente tiene el valor `lauti`; usar una dirección con formato de correo, por ejemplo `admin@example.com`. La contraseña de pgAdmin se configura por separado en `PGADMIN_DEFAULT_PASSWORD`.

### Iniciar los servicios

Desde la raíz del repositorio:

```bash
docker compose --env-file env config --quiet
docker compose --env-file env up -d
docker compose --env-file env ps
```

| Servicio | Imagen | Acceso |
| --- | --- | --- |
| Flowable UI | `flowable/flowable-ui:6.8.0` | http://localhost:8080/flowable-ui/ |
| PostgreSQL | `postgres:15` | `postgres:5432` desde la red de Compose; sin puerto publicado al host. |
| pgAdmin | `dpage/pgadmin4` | http://localhost:5050/ |

Los puertos de las interfaces se publican únicamente en `127.0.0.1`. El archivo Compose no configura credenciales de acceso a Flowable UI; las variables `POSTGRES_*` corresponden a la base de datos.

Para registrar PostgreSQL en pgAdmin, usar `postgres` como servidor, `5432` como puerto y los valores de `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` del archivo `env`.

### Logs y apagado

```bash
docker compose --env-file env logs -f flowable
docker compose --env-file env logs -f postgres
docker compose --env-file env down
```

PostgreSQL y pgAdmin conservan sus datos en los volúmenes `postgres_data` y `pgadmin_data`. El comando `down` conserva esos volúmenes; agregar `-v` elimina sus datos.

Flowable depende del inicio del contenedor PostgreSQL, pero Compose no espera a que su comprobación de salud sea satisfactoria. Si falla la conexión inicial, revisar los logs y, una vez disponible PostgreSQL, reiniciar Flowable:

```bash
docker compose --env-file env restart flowable
```

El arranque de los contenedores no importa ni despliega automáticamente los modelos BPMN, ni configura los usuarios del proyecto.

## Implementación requerida

Según el enunciado, la solución deberá incorporar:

- Un modelo BPMN ejecutable con reglas de viabilidad, mensajes, temporizadores y compensaciones.
- Una base de datos de la institución para empresas y cursos, vinculando cada curso con su instancia de Flowable, junto con scripts de creación y datos de prueba.
- Cinco usuarios, los grupos Administración y Docentes y las asignaciones correspondientes.
- Un Task Listener para asignar la confección de la lista al administrativo con menos tareas pendientes asignadas.
- Un Execution Listener para registrar el resultado del curso: finalizado, cancelado, rechazado, cerrado por falta de respuesta o no viable.
- Un sitio web que use la API REST para solicitudes, acceso de empresas, seguimiento de cursos y gestión de tareas según el usuario. Debe contemplar propuestas en PDF y la selección del docente responsable.
- Una aplicación auxiliar con interfaz gráfica para preparar materiales mediante la API Java de Flowable.
- Una aplicación auxiliar para simular comunicaciones de la empresa mediante la API REST. Para grupos de hasta tres personas, el enunciado permite realizar estas acciones por línea de comandos.

El PDF menciona una aplicación móvil para definir docente en la descripción del proceso, y también incluye esa función entre las del sitio web. Ese punto requiere aclaración al definir las interfaces de la entrega.

## Demostración y entrega

La demostración debe cubrir el curso completo, la revisión y el rechazo de propuestas, el cierre sin respuesta, el límite de instancias activas, la cancelación con compensaciones, la falta de aulas y las asignaciones de tareas. También debe mostrar la solicitud desde el sitio, el acceso de cada empresa exclusivamente a sus cursos y las integraciones mediante las API Java y REST.

La entrega incluye el modelo ejecutable, las reglas, el código de los servicios y aplicaciones, las instrucciones de configuración y ejecución y un informe breve de variables, listeners y decisiones de implementación. Consultar el [enunciado completo](contexto/ProyectoCursos_V2.pdf) para los criterios detallados.
