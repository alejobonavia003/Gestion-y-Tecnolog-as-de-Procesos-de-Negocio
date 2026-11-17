# Backend

El módulo `backend/` usa Java 21, Maven y Spring Boot 3.5.16. Habla con Flowable solo por su API REST y guarda los datos institucionales (empresas, cursos, propuestas) en la base `institucion` de PostgreSQL. Implementa las etapas 1 a 9 (ver [BACKEND_PROGRESO_Y_ETAPAS.md](BACKEND_PROGRESO_Y_ETAPAS.md)).

## Estructura

- `src/main/java/ar/edu/cursos/CursosApplication.java`: inicio de Spring Boot.
- `src/main/java/ar/edu/cursos/EstadoController.java`: `GET /api/v1/estado`.
- `src/main/resources/application.properties`: puerto, base de datos, URLs de Flowable, grupos y jefes. Lee las credenciales de `../.env`.
- `controllers/`, `services/`, `dto/`: API, lógica y contratos (sesiones, solicitudes, tareas, propuestas, cursos y dictado).
- `services/TareasProceso.java`: **único lugar** con los IDs de las tareas del BPMN.
- `config/DatosDemo.java`: crea `empresa-demo` / `1234` si `CURSOS_DATOS_DEMO=true`.
- `clients/FlowableClient.java`: único cliente REST del motor.
- `model/` y `repositories/`: persistencia institucional con JPA.
- `Dockerfile`: compila con Maven y ejecuta el JAR con Java 21.

## Ejecutar y comprobar

Con JDK 21 y Maven instalados, desde la raíz del repositorio:

```bash
cd backend
mvn clean verify
mvn spring-boot:run
```

Por defecto escucha en `127.0.0.1:8081`. Desde otra terminal:

```bash
curl -i http://localhost:8081/api/v1/estado
```

`GET /api/v1/estado` devuelve HTTP 200 y este JSON (el orden de las propiedades puede variar):

```json
{
  "aplicacion": "cursos-backend",
  "estado": "funcionando"
}
```

La respuesta confirma que el backend está funcionando; no verifica Flowable ni la base de datos.

La ejecución requiere el `.env` en la raíz del repo, la base `institucion` creada en
el Postgres de Docker (`localhost:5433`) y Flowable levantado con la app publicada.
Los pasos de la primera instalación están en el [README principal](../README.md).
Para comprobar la conexión con Flowable: `GET /api/v1/diagnostico/flowable`.

En esta rama el servicio `backend` del `docker-compose.yml` todavía no recibe las
variables de Postgres y Flowable, así que en desarrollo se corre con Maven. La
configuración completa del backend en Docker llega con la integración de `main`
(etapa 11).

Para los contratos previstos entre componentes, consultar el [acuerdo de integración](ACUERDO_DE_INTEGRACION.md).

## Etapa 6: tareas

Base `/api/v1/tareas`, sesión de personal obligatoria:

- `GET ?bandeja=asignadas|disponibles&page=0&size=20`: bandeja paginada (máximo 100).
- `GET /{taskId}`: resumen y contexto; permite asignado o candidato por grupo.
- `POST /{taskId}/reclamo`: reclama para el usuario de sesión; repetir devuelve `409 YA_RECLAMADA`.
- `POST /{taskId}/completado`: solo el asignado; cuerpo `{"datos": {...}}`.

Completado admite `confeccionar_lista` (participantes con nombre, al menos uno),
`evaluar_recursos` (tres booleanos `requiereAula`, `requierePlataforma`,
`requiereEquipamiento`, al menos uno verdadero), `archivar_solicitud`,
`enviar_informe` y `archivar_expediente` (datos vacíos). Rechaza campos extra y
tareas con operaciones propias con `409 ESTADO_INCOMPATIBLE`.
Los participantes se envían como variable REST `json`, conservando la lista.
Los IDs actuales se centralizan en `TareasProceso`.

El contexto incluye solicitud, propuesta vigente y observaciones para revisión,
nómina para lista, motivo para archivo, datos de curso/informe para cierre y la
lista de docentes para `definir_docente` (etapa 9). La disponibilidad real del PDF
del informe queda para el paso 3 de la etapa 9.

Verificación automatizada: `mvn -f backend/pom.xml verify`.
Pruebas de permisos, doble reclamo, validaciones, serialización y cliente REST
con servidor HTTP de prueba; no requieren PostgreSQL ni Flowable reales.

Para validar contra el motor, levantar Postgres y Flowable, publicar el BPMN
actualizado e iniciar el backend desde `backend/` con su `.env` configurado.
Iniciar sesión con `POST /api/v1/sesiones/personal`, conservar la cookie y probar
las dos bandejas, detalle, reclamo y completado. La etapa 5 permite crear una
solicitud para obtener la tarea de `Jefe-Administrativo`.

**Pendiente de integración:** los IDs de la web aún corresponden al modelo viejo.
Antes de desplegar, ver `motor-flowable/modelos/README.md`. Falta validar desde la web.

## Etapa 7: propuestas y PDF

- `POST /api/v1/tareas/{taskId}/propuesta`: solo `Jefe-Administrativo`, en
  `registrar_elaborar_propuesta` o `revisar_propuesta`. Crea la versión siguiente,
  genera el PDF (OpenPDF) en `backend/almacenamiento/propuestas/` y recién después
  completa la tarea con `propuestaVersion` y `recordatorios=0`.
- `GET /api/v1/cursos/{cursoId}/propuestas`: historial de versiones.
- `GET /api/v1/cursos/{cursoId}/propuestas/{version}/pdf`: `application/pdf`.
- Una empresa solo ve sus cursos; un curso ajeno responde 404.

## Etapa 8: portal de empresa

- `GET /api/v1/cursos?page&size`: solo los cursos de la empresa de la sesión.
- `GET /api/v1/cursos/{cursoId}`: detalle y estado (404 si es de otra empresa).
- `GET /api/v1/cursos/{cursoId}/actividades`: ramas activas durante la preparación
  (tareas y esperas de mensaje), con nombres legibles para la empresa.

## Etapa 9: docente, dictado e informe

- `POST /api/v1/tareas/{taskId}/docente` con `{"docenteId": "docente-1"}`: solo el
  asignado de `definir_docente` (`Jefe-Docente`). Valida que `docenteId` sea miembro
  de `Equipo-Docente` (400), completa con la variable `docenteId` y pasa el curso a
  `CONFIRMADO_PENDIENTE_INICIO`. Otra tarea → 409; otro usuario → 403.
- `POST /api/v1/tareas/{taskId}/finalizacion-dictado` con `{}`: solo el docente
  asignado de `desarrollar_curso` (403). Si la tarea ya no está activa → 409. Pasa el
  curso a `EN_ELABORACION_INFORME`.
- `GET /api/v1/cursos/{cursoId}/informe/pdf`: PDF generado al vuelo con el resumen que
  el jefe docente cargó en el formulario de `elaborar_informe` (variable
  `informeResumen`, leída del historial de Flowable y copiada a `curso.informe_resumen`).
  Lo ven los dos jefes y la empresa dueña (404 si es ajeno); otro personal → 403; sin
  informe → 404.
- Contexto en `GET /tareas/{taskId}`: `docentes: [{usuarioId, nombre, esJefe}]` en
  `definir_docente` e `informe: {resumen, pdfDisponible}` en `enviar_informe` y
  `archivar_expediente`.
- Clases: `DictadoController`, `DictadoService`, `DefinirDocenteRequest`,
  `InformeController`, `InformeService`, `TareaService.listarDocentes()` y
  `FlowableClient.variableHistorica`. Pruebas: `DictadoServiceTest`, `InformeServiceTest`.

Requiere en el modelo: `desarrollar_curso` con assignee `${docenteId}`, el timer
*Fecha programa* con `${fechaCurso}` y `elaborar_informe` asignada a `Jefe-Docente`
con el formulario *Informe de resultados* (campo `informeResumen`).

Contrato remoto consultado: [API REST oficial de Flowable](https://www.flowable.com/open-source/docs/bpmn/ch14-REST).
