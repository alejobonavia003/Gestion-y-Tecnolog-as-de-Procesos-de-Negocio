# Backend: guía, avance y contratos

Un solo documento con: guía del backend, avance por etapas, contrato de endpoints de la web, detalle de la etapa 6 y la integración del primer recorrido.

---

## Guía del backend

El módulo `backend/` usa Java 21, Maven y Spring Boot 3.5.16. Habla con Flowable solo por su API REST y guarda los datos institucionales (empresas, cursos, propuestas) en la base `institucion` de PostgreSQL. Implementa las etapas 1 a 11 (ver [avance y etapas](#avance-y-etapas)).

### Estructura

- `src/main/java/ar/edu/cursos/CursosApplication.java`: inicio de Spring Boot.
- `src/main/java/ar/edu/cursos/EstadoController.java`: `GET /api/v1/estado`.
- `src/main/resources/application.properties`: puerto, base de datos, URLs de Flowable, grupos y jefes. Lee las credenciales de `../.env`.
- `config/DatosDemo.java` y `config/CsrfFilter.java`: datos demo opcionales y protección CSRF (`XSRF-TOKEN` / `X-XSRF-TOKEN`).
- `controllers/`, `services/`, `dto/`: API, lógica y contratos (sesiones, solicitudes, tareas, propuestas, cursos, dictado, informe y simulación).
- `services/TareasProceso.java`: **único lugar** con los IDs de las tareas del BPMN.
- `config/DatosDemo.java`: crea `empresa-demo` / `1234` si `CURSOS_DATOS_DEMO=true`.
- `clients/FlowableClient.java`: único cliente REST del motor.
- `model/` y `repositories/`: persistencia institucional con JPA.
- `Dockerfile`: compila con Maven y ejecuta el JAR con Java 21.

### Ejecutar y comprobar

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
el Postgres de Docker (`localhost:5433`) y Flowable levantado con la app desplegada (`motor-flowable/modelos/desplegar.sh`).
Los pasos de la primera instalación están en el [README principal](../README.md).
*(Nota: `DiagnosticoController` y su ruta `/api/v1/diagnostico/flowable` fueron eliminados en la etapa 11).*

El servicio `backend` del `docker-compose.yml` ya recibe las variables de Postgres y Flowable, y también puede correrse en desarrollo con Maven (`mvn spring-boot:run`).

Para los contratos previstos entre componentes, consultar el [acuerdo de integración](MODELO_Y_INTEGRACION.md#acuerdo-de-integración).

### Etapa 6: tareas

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

### Etapa 7: propuestas y PDF

- `POST /api/v1/tareas/{taskId}/propuesta`: solo `Jefe-Administrativo`, en
  `registrar_elaborar_propuesta` o `revisar_propuesta`. Crea la versión siguiente,
  genera el PDF (OpenPDF) en `backend/almacenamiento/propuestas/` y recién después
  completa la tarea con `propuestaVersion` y `recordatorios=0`.
- `GET /api/v1/cursos/{cursoId}/propuestas`: historial de versiones.
- `GET /api/v1/cursos/{cursoId}/propuestas/{version}/pdf`: `application/pdf`.
- Una empresa solo ve sus cursos; un curso ajeno responde 404.

### Etapa 8: portal de empresa

- `GET /api/v1/cursos?page&size`: solo los cursos de la empresa de la sesión.
- `GET /api/v1/cursos/{cursoId}`: detalle y estado (404 si es de otra empresa).
- `GET /api/v1/cursos/{cursoId}/actividades`: ramas activas durante la preparación
  (tareas y esperas de mensaje), con nombres legibles para la empresa.

### Etapa 9: docente, dictado e informe

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

---

## Avance y etapas

Proyecto **Organización de cursos de capacitación** (GyTPN, UNRC). Estado al **18/11/2026**, rama **`backend-lauty`**.

Este documento sirve para seguir el avance: qué quedó implementado y probado, y qué etapas faltan para completar todos los endpoints que usa la web (sección «Contrato de endpoints de la web») y los entregables de la consigna V3.

---

### 1. Resumen

| Parte | Estado |
|---|---|
| Infraestructura (Docker, Postgres, `.env`, `.gitignore`) | ✅ Funcionando con `web`, `backend`, `flowable`, `postgres`, `institucion-db` y `pgadmin` |
| Usuarios y grupos en Flowable (`motor-flowable/idm`) | ✅ Script idempotente |
| Modelo del proceso desplegado en Flowable | ✅ Desplegado vía `desplegar.sh` (`Organizacion_de_cursos.bar`, con las 3 `<association>` de compensación, `AsignarListaListener` y `FinProcesoListener`) |
| Backend etapas 1 a 10 | ✅ Implementadas (solicitudes, sesiones, tareas, propuestas/PDF, portal empresa, docente/dictado/informe y simulación) |
| Backend etapa 11 (integración final) | ✅ Implementada (`CursoService`/`TareaService` con sincronización de estados, `CsrfFilter`, limpieza de `DiagnosticoController`, Docker). Pendiente: validación E2E completa de los 11 escenarios |
| Web (etapas 1 a 9 + CSRF) | 🚀 Conectada al backend real con los `taskDefinitionKey` reales del BPMN y envío de `X-XSRF-TOKEN` (`web/src/api/cliente.ts`) |
| Extensiones del motor (listeners en Java) | ✅ Implementadas en `motor-flowable/extensiones/` (`AsignarListaListener`, `FinProcesoListener`, 4 tests en verde), instaladas en `flowable-persistente:/app/WEB-INF/lib/` y asociadas al BPMN |
| App de Materiales (`app-materiales/`, API Java) | 🚀 Probada contra el proceso publicado (reclamo y completado de `preparar_materiales` por API Java) |
| Simulador REST (`simulador-rest/`) | 🚀 Probado contra el backend y el motor (recepción y apertura de PDF de propuesta, modificaciones, aceptación con habilitación de cuenta de empresa y envío de nómina; botonera en 2 filas con `Cancelar curso` visible) |
| Consultas históricas (4.3) | ✅ Implementadas en `app-materiales` (`AnalisisHistoricoService`, botón en GUI y `--analisis` por consola, con tests unitarios en verde) |
| Minería de procesos PM4Py (4.4) | ✅ Implementada en `mineria-procesos/` (`exportar_history.py`, `analizar.py`, `negociacion_history.csv`, grafo `comportamiento_negociacion.png`, `captura_resultados.png` y conclusión en `mineria-procesos/README.md`) |

#### Endpoints

| Estado | Significado |
|---|---|
| 🚀 | Implementado y probado desde la web |
| ✅ | Implementado y probado con Postman contra Flowable y Postgres reales |
| 🔧 | Implementado con pruebas unitarias; falta la validación integrada |
| ⏳ | Pendiente |
| ➖ | Descartado |

| # | Endpoint | Etapa | Estado backend |
|---|---|---|---|
| 1 | `POST /solicitudes` | 5 | 🚀 |
| 2 | `POST /sesiones/personal` | 4 | 🚀 |
| 3 | `POST /sesiones/empresa` | 4 | 🚀 |
| 4 | `GET /sesiones/actual` | 4 | 🚀 |
| 5 | `DELETE /sesiones/actual` | 4 | 🚀 |
| 9 | `GET /tareas?bandeja=...` | 6 | 🔧 |
| 10 | `GET /tareas/{taskId}` | 6 | 🔧 |
| 11 | `POST /tareas/{taskId}/reclamo` | 6 | 🔧 |
| 12 | `POST /tareas/{taskId}/completado` | 6 | 🔧 |
| 13 | `POST /tareas/{taskId}/propuesta` | 7 | 🔧 |
| 14 | `GET /cursos/{cursoId}/propuestas` | 7 | 🔧 |
| 15 | `GET /cursos/{cursoId}/propuestas/{version}/pdf` | 7 | 🔧 |
| 6 | `GET /cursos` | 8 | 🔧 |
| 7 | `GET /cursos/{cursoId}` | 8 | 🔧 |
| 8 | `GET /cursos/{cursoId}/actividades` | 8 | 🔧 |
| 17 | `POST /tareas/{taskId}/docente` | 9 | ✅ |
| 18 | `POST /tareas/{taskId}/finalizacion-dictado` | 9 | ✅ |
| 19 | `GET /cursos/{cursoId}/informe/pdf` | 9 | ✅ |
| — | ~~`POST /cursos/{cursoId}/informe`~~ (del acuerdo; opcional) | 9 | ➖ Descartado (el informe se carga en Flowable UI) |
| — | `GET /simulacion/solicitudes` | 10 | 🔧 |
| — | `GET /simulacion/solicitudes/{cursoId}/comunicaciones` | 10 | 🔧 |
| — | `GET /simulacion/solicitudes/{cursoId}/propuesta-pdf` | 10 | 🔧 |
| — | `POST /simulacion/solicitudes/{cursoId}/comunicaciones` | 10 | 🔧 |
| 16 | ~~`GET /docentes`~~ | — | ➖ Descartado (viene en el contexto del #10) |

---

### 2. Lo que ya está implementado

#### 2.1 Infraestructura
- **Docker Compose:** Flowable UI 6.8 (`:8080`), Postgres (publicado en **`127.0.0.1:5433`** para todo el equipo) y pgAdmin.
- **Base `institucion`** dentro del mismo Postgres (dueño `flowable`). Hibernate crea las tablas (`ddl-auto=update`).
- **El servicio `backend` del compose ya recibe las variables de Postgres y Flowable.** Para desarrollar con `mvn spring-boot:run` (puerto **8081**, el del proxy de la web) hay que no levantar ese servicio: `docker compose up -d postgres institucion-db flowable pgadmin`.
- **La base `institucion`** la crea el servicio `institucion-db` (corre una vez y termina). Si falta: `docker exec -it flowable-postgres psql -U <POSTGRES_USER> -d <POSTGRES_DB> -c "CREATE DATABASE institucion;"`.
- **`.env`** en la raíz (no se sube): credenciales de Postgres, la cuenta REST de Flowable y `CURSOS_DATOS_DEMO=true`. **Sin espacios alrededor del `=`**: lo leen bash (`crear-usuarios.sh`) y Spring. La plantilla es `env.example`.
- **`.gitignore` raíz:** `target/`, `node_modules/`, `dist/`, `.env*` (salvo los ejemplos), `application-local.*`, `backend/almacenamiento/`, `backend/archivos/`.

#### 2.2 `motor-flowable/`
| Carpeta | Contenido |
|---|---|
| `idm/crear-usuarios.sh` | Crea los grupos `Equipo-Administrativo` y `Equipo-Docente` y los usuarios `Jefe-Administrativo`, `empleado-administrativo`, `Jefe-Docente`, `docente-1` y `docente-2` (clave `1234` por defecto). Es idempotente. Correrlo con `bash motor-flowable/idm/crear-usuarios.sh` (no tiene permiso de ejecución en git). |
| `modelos/` | App exportada del Modeler (`.zip`), BPMN y DMN, con un README para importarla y publicarla. |
| `extensiones/` | Lugar previsto para el `.jar` de listeners (vacío). |

**Correcciones al modelo del 11/11** (⚠️ el `Organizacion de cursos.zip` del repo **no las tiene**: sigue con el DMN viejo `otrasInstanciasActivas`. Al importarlo hay que corregir la variable a `cantCursos` antes de publicar y después volver a exportarlo):
- Las 6 script tasks tienen scripts Groovy marcadores: `notificar_decision` además fija `resultadoProceso=NO_VIABLE`.
- "Reservar aula Virtual" y "Liberar aula virtual" usan una expresión que fija `aulaVirtualReservada`. Es provisorio.
- El inicio del subproceso de cancelación tiene `messageRef="cancelacionEmpresa"`.
- La tabla `reglaViabilidad` usa la entrada **`cantCursos`** (antes era `otrasInstanciasActivas`) y la salida `resultadoViabilidad`: `< 5` da `true` y `>= 5` da `false`.
- El id del proceso es **`process`**, que es el valor de `FLOWABLE_PROCESO_CLAVE` en el backend.

**Cambios del modelo para la etapa 9 (17/11):**
- `desarrollar_curso`: assignee **`${docenteId}`** (Assignments → *Fixed values*), sin grupo candidato. ✅ Publicado y probado en el Flowable de Luka; falta exportarlo al `.zip`.
- Timer *Fecha programa* (`fecha_programa`): ya usa `timeDate = ${fechaCurso}` (ISO 8601). Con una fecha pasada se dispara enseguida.
- `elaborar_informe`: assignee `Jefe-Docente` + formulario por referencia *Informe de resultados* con el campo `informeResumen`. ✅ Publicado en el Flowable de Luka; falta exportarlo al `.zip`.
- `enviar_informe`: hoy es script. Decidir con Alejo si pasa a tarea de usuario de `Jefe-Administrativo`; el backend funciona con las dos opciones.

Las instrucciones para asignar a una variable y crear formularios por referencia están en `motor-flowable/modelos/README.md`.

#### 2.3 Backend (Spring Boot 3.5, Java 21, rama `backend-lauty`)
El backend habla con Flowable **solo por REST**: no incluye el motor como dependencia ni escribe en las tablas `ACT_*`.

| Etapa | Qué incluye |
|---|---|
| **1. Base** | `pom.xml` con web, validation, data-jpa, spring-security-crypto (BCrypt) y postgresql. `application.properties` lee `../.env`. `GET /api/v1/estado`. |
| **2. Modelo de datos** | Entidades `Empresa`, `CuentaEmpresa`, `Curso` (UUID, estado, `processInstanceId` único, `claveIdempotencia` única, `hashSolicitud`, `@Version`) y `Propuesta` (versiones, fechas posibles, ruta del PDF). Enum `EstadoCurso`. Repositorios. |
| **3. Errores y cliente de Flowable** | Formato `{codigo, mensaje}` con `ApiException` y `ManejadorErrores`. `FlowableClient` con `RestClient`, timeouts de 3 y 15 s y traducción de errores: sin conexión, 401/403 o 5xx → 503 `MOTOR_NO_DISPONIBLE`, y se loguea `Flowable respondió ...`. `GET /api/v1/diagnostico/flowable` (temporal). |
| **4. Sesiones** | Login del personal validando contra la identidad de Flowable, filtrado por los dos grupos (si no pertenece → 403). Login de empresa con BCrypt. Sesión con cookie `JSESSIONID` HttpOnly. `GET` y `DELETE /sesiones/actual`. `DatosDemo` crea `empresa-demo` / `1234`. |
| **5. Solicitudes** | `POST /solicitudes` con `Idempotency-Key` y hash SHA-256 del cuerpo. Guarda la empresa (la reutiliza por identificador) y el curso como `SOLICITADO`. Cuenta las instancias activas → `cantCursos`. Inicia el proceso con `businessKey=curso:<uuid>` y las variables. Pasa a `EN_ACUERDO`, o a `NO_VIABLE` si la instancia terminó enseguida. Si el motor falla, el reintento con la misma clave retoma el mismo curso. |
| **6. Tareas** | `TareaController` / `TareaService`: bandejas, detalle con contexto, reclamo y completado de tareas simples. IDs en `TareasProceso`. Ver `ETAPA_6_IMPLEMENTADA.md`. |
| **7. Propuestas y PDF** | `PropuestaController` / `PropuestaService`: versiones, PDF con OpenPDF en `backend/almacenamiento/propuestas/`, historial y descarga. |
| **8. Portal de empresa** | `CursoController` / `CursoService`: cursos de la empresa de la sesión, detalle y actividades en preparación. |
| **9. Docente, dictado e informe** | `DictadoController` / `DictadoService` (#17 y #18), `InformeController` / `InformeService` (#19, PDF al vuelo), `TareaService.listarDocentes()` e informe en el contexto, `FlowableClient.variableHistorica`. 14 pruebas en `DictadoServiceTest` e `InformeServiceTest`. |

**Probado en la etapa 5:**
- Creación.
- Reintento idempotente: misma respuesta y misma instancia.
- Misma clave con otros datos → 409.
- Retomar un curso que había quedado sin instancia.
- La tarea `registrar_elaborar_propuesta` queda asignada a `Jefe-Administrativo`.
- Caso no viable con 5 instancias activas.

**Errores resueltos en el camino** (por si reaparecen):

| Síntoma | Causa y solución |
|---|---|
| `LazyInitializationException` en `Empresa` | Se agregó `@EntityGraph(attributePaths = "empresa")` en `CursoRepository.findByClaveIdempotencia` y `findByProcessInstanceId`. |
| 503 al crear | Mirar `Flowable respondió ...` en el log. Fue el DMN con una variable con otro nombre. |
| `Errors while parsing` al publicar | Tareas automáticas sin script o sin implementación, o mensajes sin `messageRef`. Correr **Validate** en el Modeler. |
| Login 404 `NO_ENCONTRADO` | La URL de identidad tiene que terminar en `/identity`. |
| No conecta a Postgres | El contenedor no estaba levantado, o hay un Postgres nativo en 5432. Usamos **5433**. |
| 8081 ocupado | Un contenedor viejo del backend. Bajarlo; no cambiar el puerto. |
| `password authentication failed for user "${POSTGRES_USER}"` | El backend no leyó el `.env`: tiene que estar en la raíz y `mvn` se corre desde `backend/`. |
| `database "institucion" does not exist` | Base nueva de Postgres: crear `institucion` a mano (ver 2.1). |
| 503 con `No process definition found for key 'process'` | La app no está publicada en ese Flowable (por ejemplo, en una instalación nueva). |
| 503 con `Unknown property ... otrasInstanciasActivas` | Se publicó el `.zip` con el DMN viejo: cambiar la variable a `cantCursos` y volver a publicar. |
| `crear-usuarios.sh`: `no es un archivo ejecutable` / `orden no encontrada` | Correrlo con `bash ...`; si menciona una línea del `.env`, esa línea tiene espacios alrededor del `=`. |
| Postman `ECONNREFUSED` en 8081 u 8080 | El backend o Flowable no están corriendo (o siguen arrancando). |

#### 2.4 Web (React + Vite + TypeScript, rama `luka/web-base`)
- Etapas 1 a 9 completas **contra mocks (MSW)**: formulario público, login de personal y de empresa, bandeja de tareas, todos los formularios de tareas, portal de empresa con estado y actividades, y PDFs.
- `npm run dev:mock` (mocks) o `npm run dev` (backend real; el proxy manda `/api` a `localhost:8081`).
- Contra el backend real ya funciona el login (probado con `git worktree`).

---

### 3. Etapas que faltan

Cada etapa se cierra igual:
1. Probar con Postman (colección `docs/postman/etapa 9/`, una carpeta por etapa) y desde la web si ya está conectada.
2. Commitear.
3. Marcar los endpoints como 🚀 en la sección «Contrato de endpoints de la web» de este documento.
4. Actualizar `BACKEND.md` y este documento.

#### Antes de la etapa 6: IDs de las tareas
La web todavía usa los IDs del modelo viejo. El backend y la web tienen que usar los del modelo actual. **Verificarlos con Alejo contra el BPMN publicado:**

| Tarea | ID viejo (web) | ID actual (BPMN) |
|---|---|---|
| Registrar y elaborar propuesta | `Propuesta` | `registrar_elaborar_propuesta` |
| Revisar propuesta | `Revisar` | `revisar_propuesta` |
| Archivar solicitud | `Archivar` | `archivar_solicitud` |
| Confeccionar lista definitiva | `Lista` | `confeccionar_lista` |
| Evaluar recursos | `Activity_12i2hpf` | `evaluar_recursos` |
| Preparar materiales | `Material` | `preparar_materiales` |
| Definir docente | `Activity_1018bdz` | `definir_docente` |
| Desarrollar curso | `Dictar` | `desarrollar_curso` |
| Elaborar informe | `Informe` | `elaborar_informe` |
| Enviar informe y archivar | `EnviarInforme` | `enviar_informe` / `archivar_expediente` |

Conviene definirlos **una sola vez** en una clase del backend (por ejemplo `TareasProceso`) y en `web/src/tareas/catalogo.ts`.

---

#### Etapa 6 — Tareas (#9, #10, #11, #12)

Implementada en `backend-alejo`: controlador, servicio, DTOs, cliente REST y pruebas automatizadas. La validación real con Flowable y web sigue pendiente; no se marcan como 🚀 hasta realizarla. Ver `BACKEND.md`.
**Objetivo:** que el personal vea su bandeja, abra una tarea con su contexto, la reclame y complete las tareas simples.

| Endpoint | Llamadas a Flowable |
|---|---|
| `GET /tareas?bandeja=asignadas` | `GET runtime/tasks?assignee={usuario}&processDefinitionKey=process` |
| `GET /tareas?bandeja=disponibles` | `GET runtime/tasks?candidateGroups={grupos de la sesión}&unassigned=true` |
| `GET /tareas/{taskId}` | `GET runtime/tasks/{id}` + `GET runtime/tasks/{id}/identitylinks`. El contexto se arma desde la base `institucion` (curso, propuestas) y con variables del proceso. |
| `POST /tareas/{taskId}/reclamo` | `POST runtime/tasks/{id}` `{"action":"claim","assignee":"<usuario de la sesión>"}`. Si Flowable responde 409 → `YA_RECLAMADA`. |
| `POST /tareas/{taskId}/completado` | `POST runtime/tasks/{id}` `{"action":"complete","variables":[...]}` |

**A implementar:**
- `TareaService` y `TareaController`. Métodos nuevos en `FlowableClient`: `tareas(...)`, `tarea(id)`, `identityLinks(id)`, `reclamar(id, usuario)`, `completar(id, vars)` y `variables(processInstanceId)`.
- DTOs: `TareaResumen` (con `reclamable`) y `TareaDetalle` (con `contexto`).
- **Autorización:**
  - Para ver una tarea: ser el asignado o ser candidato por grupo. Si no → 403.
  - Para completar: ser el asignado.
- **Datos admitidos por tarea en `/completado`**, validados en el backend:
  - `confeccionar_lista`: `participantes`, con al menos uno.
  - `evaluar_recursos`: `requiereAula`, `requierePlataforma` y `requiereEquipamiento`, con al menos uno en `true`. Los nombres tienen que coincidir con las condiciones del gateway inclusivo.
  - `archivar_solicitud`, `enviar_informe` y `archivar_expediente`: sin datos.
- Rechazar las tareas que tienen su propio endpoint (propuesta, docente, dictado), con 409.

**Depende del modelo:** `confeccionar_lista` tiene que ser una sola tarea, no multi-instancia, y `archivar_expediente` tiene que ser del jefe administrativo.

**Pruebas:**
- Bandeja de `Jefe-Administrativo` con la tarea de la etapa 5.
- Disponibles para `empleado-administrativo`.
- Reclamar dos veces → la segunda da 409.
- Completar sin ser el asignado → 403.

---

#### Etapa 7 — Propuestas y PDF (#13, #14, #15)

Implementada en `backend`: controlador (`PropuestaController`), servicio (`PropuestaService`), DTOs (`NuevaPropuestaRequest`, `PropuestaCreadaResponse`, `PropuestaItemResponse`), generación de PDF con OpenPDF (`2.0.3` en `pom.xml`), pruebas unitarias (`PropuestaServiceTest`) y colección Postman (`docs/postman/`). La validación integrada con Flowable y web sigue pendiente; no se marcan como 🚀 hasta realizarla.
**Objetivo:** el jefe administrativo elabora y revisa propuestas; se genera un PDF por cada versión.

**A implementar:**
- `PropuestaService` y `PropuestaController`.
- `POST /tareas/{id}/propuesta` (tareas `registrar_elaborar_propuesta` y `revisar_propuesta`):
  1. Validar los datos: programa no vacío, importe mayor a 0, al menos una fecha.
  2. Crear la versión siguiente en la tabla `propuesta`.
  3. Generar el PDF con una librería simple, por ejemplo **OpenPDF**, y guardarlo en `backend/almacenamiento/` (está en el `.gitignore`).
  4. **Recién después**, completar la tarea con las variables `propuestaVersion` y `recordatorios=0`.
- `GET /cursos/{id}/propuestas`: historial de versiones. Lo puede ver el jefe administrativo o la empresa dueña del curso.
- `GET /cursos/{id}/propuestas/{v}/pdf`: devuelve `application/pdf` sin exponer la ruta en disco. Si no existe o el curso es de otra empresa → 404.
- Si la versión que manda la web no es la vigente → 409 `VERSION_DESACTUALIZADA`.
- Contexto de `revisar_propuesta` en el #10: `propuestaVigente` y `observacionesEmpresa`.

**Depende del modelo:** "Enviar propuesta" es automática: solo notifica, y es un script por ahora.

**Pruebas:** versión 1, PDF descargable, revisión que crea la versión 2 sin pisar la 1, y 409 con versión vieja.

---

#### Etapa 8 — Portal de empresa y habilitación de acceso (#6, #7, #8)

Implementada en `backend`: controlador (`CursoController`), servicio (`CursoService`), DTOs (`CursoResumenResponse`, `CursoDetalleResponse`, `ActividadCursoResponse`, `ActividadesCursoResponse`), consultas en `CursoRepository` y `FlowableClient` (`tareasDeInstancia`, `ejecucionesDeInstancia`), pruebas unitarias (`CursoServiceTest`) y colección Postman (`Etapa_8.postman_collection.json`). Queda pendiente el disparo automático de habilitación de `CuentaEmpresa` al aceptar la propuesta (etapa 10) y la validación integrada con la web; no se marcan como 🚀 hasta realizarla.
**Objetivo:** la empresa entra con su cuenta y ve sus cursos, el estado de cada uno y las actividades en curso.

**A implementar:**
- `CursoController`:
  - `GET /cursos`: paginado, **solo los cursos de la empresa de la sesión**.
  - `GET /cursos/{id}`: si el curso es de otra empresa → 404, sin revelar que existe.
  - `GET /cursos/{id}/actividades`: `GET runtime/tasks?processInstanceId=...` + `GET runtime/executions?processInstanceId=...` para las esperas de mensaje (nómina). Durante la preparación devuelve **todas** las ramas activas; fuera de ella, la lista vacía. Los nombres tienen que ser legibles para la empresa, sin exponer al personal.
- **Habilitación de acceso:** al aceptar la propuesta se crea (o reutiliza) la `CuentaEmpresa` con una clave generada y hasheada con BCrypt. La comunicación de las credenciales se simula (log o respuesta del simulador). **Decidir con Alejo** quién la dispara:
  - **Opción simple (recomendada):** el backend, al procesar la comunicación `ACEPTACION` en la etapa 10.
  - **Opción del acuerdo:** una tarea automática del motor que llama a `POST /interno/cursos/{id}/habilitacion-empresa`. Esta opción necesita autenticación interna.
- Actualizar el `estado` del curso cuando cambia de fase (ver la sección 4).

**Pruebas:** `empresa-demo` ve sus cursos; `otra-empresa` recibe 404 en un curso ajeno; actividades con varias ramas durante la preparación.

---

#### Etapa 9 — Docente, dictado e informe (#17, #18, #19)

✅ **Completa** (17/11/2026, rama `backend-lauty`). Probada de punta a punta con Postman contra Flowable y Postgres reales (carpeta `4. Etapa 9 - paso a paso` de `docs/postman/etapa 9/`, 30 requests en verde) y con 14 pruebas unitarias (`DictadoServiceTest` y `InformeServiceTest`).

**Objetivo:** el jefe docente elige al docente, el docente marca el dictado como terminado, el jefe docente carga el informe y el informe se puede ver en PDF.

| Paso | Qué incluye | Estado |
|---|---|---|
| 0 | Cambios del modelo: `desarrollar_curso` con `${docenteId}`, `elaborar_informe` con `Jefe-Docente` + formulario | ✅ |
| 1 | #17 `POST /tareas/{id}/docente` + `docentes` en el contexto | ✅ |
| 2 | #18 `POST /tareas/{id}/finalizacion-dictado` | ✅ |
| 3 | Informe en el contexto de cierre + #19 `GET /cursos/{id}/informe/pdf` | ✅ |
| 4 | Escenario completo en Postman y docs | ✅ |

**Flujo implementado:**
```
definir_docente (Jefe-Docente)  →  POST /tareas/{id}/docente {docenteId}
        │                            → variable docenteId, curso CONFIRMADO_PENDIENTE_INICIO
timer "Fecha programa" (${fechaCurso})
        │
desarrollar_curso (${docenteId}) →  POST /tareas/{id}/finalizacion-dictado
        │                            → curso EN_ELABORACION_INFORME
elaborar_informe (Jefe-Docente)  →  Flowable UI, formulario "Informe de resultados"
        │                            → variable informeResumen
enviar_informe / archivar_expediente → contexto.informe {resumen, pdfDisponible}
                                       GET /cursos/{id}/informe/pdf
```

**#17 — Definir docente:**
- Solo el asignado de `definir_docente` (403), solo esa tarea (409).
- `docenteId` tiene que ser miembro de `Equipo-Docente` (`TareaService.listarDocentes()`, con `FlowableClient.miembrosDe` y `buscarUsuario`) → 400 si no.
- Completa con la variable `docenteId` y pasa el curso a `CONFIRMADO_PENDIENTE_INICIO`.
- El contexto de `definir_docente` trae `docentes: [{usuarioId, nombre, esJefe}]`.

**#18 — Finalización del dictado:**
- Body `{}`. Solo `desarrollar_curso` y solo el docente asignado (403).
- Si la tarea ya no existe (completada o el timer todavía no venció) → 409 `ESTADO_INCOMPATIBLE` ("El curso no está en desarrollo"). Otra tarea → 409.
- Completa sin variables y pasa el curso a `EN_ELABORACION_INFORME`.

**Informe y #19:**
- El jefe docente completa `elaborar_informe` en Flowable UI (la web la muestra como etiqueta). El campo `informeResumen` del formulario queda como variable del proceso.
- `GET /tareas/{id}` de `enviar_informe` / `archivar_expediente` trae `informe: {resumen, pdfDisponible}` (variable del proceso; si no está, la columna `curso.informe_resumen`).
- `GET /cursos/{id}/informe/pdf`: PDF generado al vuelo con OpenPDF (temática, empresa, docente, fecha, participantes y resumen). No se guarda ningún archivo. Lee `informeResumen` del **historial** de Flowable (`FlowableClient.variableHistorica`), así que funciona aunque el proceso haya terminado, y la primera vez lo copia a `curso.informe_resumen`.
- Permisos: `Jefe-Administrativo` y `Jefe-Docente` ven cualquier informe; la empresa solo los de sus cursos (404 si es ajeno); otro personal → 403; sin informe → 404.
- `POST /cursos/{id}/informe` del acuerdo: ➖ **no se implementa**. El informe se carga desde el formulario de Flowable UI, la web no lo usa y en la V3 el PDF no es obligatorio.

**Clases nuevas o modificadas:** `DictadoController`, `DictadoService`, `DefinirDocenteRequest`, `InformeController`, `InformeService`, `TareaService` (constructor con grupo y jefe docente, `listarDocentes()`, informe en el contexto), `TareasProceso` (`DEFINIR_DOCENTE`, `DESARROLLAR_CURSO`, `ELABORAR_INFORME`), `FlowableClient.variableHistorica`.

**Cómo se prueba sin recorrer todo el proceso:** atajos de Flowable en Postman: fijar `fechaCurso` en una fecha pasada (el timer se dispara enseguida), mover la instancia con `POST runtime/process-instances/{id}/change-state` y completar `elaborar_informe` por REST con la variable `informeResumen`.

**Queda para otras etapas:**
- `fechaCurso` la fija la comunicación `ACEPTACION` (etapa 10); el diseño no tiene fechas fijas (sección 3.5 de la V3). Hasta entonces se fija a mano.
- `EN_DESARROLLO` (cuando vence el timer) y `EN_CIERRE` / estado final los marca el motor con listeners (etapa 11).
- `enviar_informe`: decidir con Alejo si queda script o pasa a tarea de usuario de `Jefe-Administrativo`. El backend funciona con las dos opciones.

---

#### Etapa 10 — Simulador de comunicaciones de la empresa
**Objetivo:** simular lo que la empresa haría por mail: aceptar, rechazar, pedir modificaciones, enviar la nómina y cancelar. Lo usa el simulador (`simulador-rest/`, sección 4.2.2 de la V3: los grupos de hasta 3 integrantes pueden hacerlo por CLI sin GUI).

| Endpoint | Qué hace |
|---|---|
| `GET /simulacion/solicitudes` | Lista los cursos con las acciones habilitadas en su estado actual. |
| `GET /simulacion/solicitudes/{cursoId}/comunicaciones` | Muestra las acciones admitidas y la versión de propuesta vigente. |
| `POST /simulacion/solicitudes/{cursoId}/comunicaciones` | Aplica una comunicación. Es idempotente por `comunicacionId`. |

**Tipos de comunicación y su efecto:**

| `tipo` | Datos | Variables que fija | Mensaje BPMN |
|---|---|---|---|
| `ACEPTACION` | `propuestaVersion`, `fechaCurso` | `respuestaEmpresa=ACEPTADA`, `fechaCurso` | `aceptacion` (verificar en BPMN) |
| `RECHAZO` | `propuestaVersion` | `respuestaEmpresa=RECHAZADA` | `rechazo` (verificar en BPMN) |
| `MODIFICACION` | `propuestaVersion`, `observacionesEmpresa` | `respuestaEmpresa=MODIFICACIONES`, `observacionesEmpresa` | `modificaciones` (ya verificado en BPMN) |
| `NOMINA` | `participantes` | `participantes` | definir con Alejo |
| `CANCELACION` | — | — (el motor fija `resultadoProceso=CANCELADO`) | **`cancelacionEmpresa`** (ya definido) |

**Cómo se entrega un mensaje al motor:**
```
GET runtime/executions?processInstanceId={pid}&messageEventSubscriptionName={mensaje}
PUT runtime/executions/{executionId}  {"action":"messageEventReceived","messageName":"{mensaje}","variables":[...]}
```
Si no hay una ejecución suscrita, la acción no está habilitada en ese momento → 409.

**A implementar:**
- `SimulacionService` y `SimulacionController`. Los endpoints de simulación **no requieren sesión**: representan a la empresa (que responde por mail), no al personal.
- Una tabla para registrar cada comunicación (`comunicacionId` único) y así responder igual ante una repetición.
- Validar la versión de la propuesta: si no coincide con la vigente → 409.

**Depende del modelo:**
- `messageRef` en todos los mensajes, y la tabla de nombres acordada con Alejo.
- "Recibir nómina" como evento intermedio de mensaje.
- Timers de recordatorio con su contador.

---

#### Etapa 11 — Integración final (implementada el 18/11/2026)
- **Cierre y sincronización de estados:**
  - `FinProcesoListener` (en `motor-flowable/extensiones/`) actualiza `curso.estado` en `institucion` por JDBC al terminar la instancia según `resultadoProceso` (`FINALIZADO`, `CANCELADO`, `RECHAZADO`, `CERRADO_SIN_RESPUESTA`, `NO_VIABLE`).
  - `CursoService` sincroniza estados intermedios/finales desde Flowable al consultar cursos (`CONFIRMADO_PENDIENTE_INICIO`, `EN_DESARROLLO`, `EN_ELABORACION_INFORME`, `EN_CIERRE`, o terminal), y `TareaService` actualiza al completar `archivar_solicitud`, `enviar_informe` y `archivar_expediente`.
- **Asignación automática de lista:** `AsignarListaListener` (`create` en `confeccionar_lista`) asigna al integrante de `Equipo-Administrativo` con menos tareas activas asignadas (desempate por ID alfabético).
- **CSRF:** `CsrfFilter` emite la cookie `XSRF-TOKEN` y exige el header `X-XSRF-TOKEN` en peticiones mutables de navegador (salvo `/api/v1/simulacion/**`). `web/src/api/cliente.ts` lee la cookie y envía el header.
- **Limpieza y Docker:** eliminado `DiagnosticoController`; backend y web reconstruidos con `docker compose up -d --build` (41 tests en backend + 4 tests en extensiones en verde).
- **Pendiente de validación en ejecución:** correr y documentar los 11 escenarios de demostración de punta a punta contra el entorno levantado.

---

### 4. Temas transversales

- **Estado del curso (`EstadoCurso`).** El backend lo actualiza cuando la acción pasa por él:
  - `EN_ACUERDO`: al iniciar.
  - `EN_PREPARACION`: al aceptar.
  - `EN_DESARROLLO`: cuando empieza el dictado.
  - ...y así con los demás.

  Las transiciones que hace solo el motor (timers, fin del proceso) las informa el `FinProcesoListener`. Terminar de cerrar la tabla completa de transiciones con el equipo antes de la etapa 10.
- **Una sola variable de resultado:** `resultadoProceso` (`NO_VIABLE`, `CANCELADO`, `RECHAZADO`, `SIN_RESPUESTA`, `FINALIZADO`). El modelo todavía usa `resultado = 'cancelado'` en algún lugar.
- **Instancias de prueba:** cuentan para `cantCursos`. Borrarlas después de probar (`DELETE runtime/process-instances/{id}`).
- **La web nunca manda** `usuarioId`, `assignee` ni `empresaId`: el backend los toma de la sesión.

---

### 5. Extensiones del motor (`motor-flowable/extensiones/`)

**Implementado (jar con `maven.compiler.release=11`, cargado en `/app/WEB-INF/lib/` de `flowable-persistente`):**
- `ar.edu.cursos.motor.AsignarListaListener`: Task Listener en el evento `create` de `confeccionar_lista`; consulta el grupo `Equipo-Administrativo`, cuenta solo tareas de usuario pendientes **asignadas** (sin contar candidatas sin reclamar) y asigna al que tenga menos.
- `ar.edu.cursos.motor.FinProcesoListener`: Execution Listener en el evento `end` de `process`; escribe el estado final en `institucion` por JDBC según `resultadoProceso`.
- JAR compilado en `motor-flowable/extensiones/flowable-extensiones.jar` (ver [motor-flowable/README.md](../motor-flowable/README.md)).

**Estado de configuración del modelo BPMN (`institucion-de-capacitacion.bpmn20.xml` y `Organizacion_de_cursos.bar`):**
- [x] `desarrollar_curso` con `assignee="${docenteId}"` y timer previo *Fecha programada* leyendo `${fechaCurso}`.
- [x] `elaborar_informe`: assignee `Jefe-Docente` + formulario por referencia con `informeResumen`.
- [x] `confeccionar_lista`: una sola tarea con `AsignarListaListener` en `create`.
- [x] `reservar_aula_fisica`: automática (web service simulado con manejo de `simularSinAulas`) y `reservar_aula_virtual` del administrativo.
- [x] `habilitar_acceso_empresa`: automática.
- [x] `archivar_expediente`: asignada a `Jefe-Administrativo`.
- [x] `messageRef` del inicio de la cancelación (`cancelacion`) y 3 `<association>` de compensación verificadas con `corregir-asociaciones.py` y `desplegar.sh`.
- [x] Recordatorios: condición del gateway (`recordatorios >= 2` / `< 2`) y contador reiniciado en cada envío de propuesta.
- [x] Variable de resultado unificada en `resultadoProceso` y leída por `FinProcesoListener`.

---

### 6. Estado de la web
- [x] IDs de las tareas reales del BPMN en `web/src/tareas/catalogo.ts` y usuarios/grupos de Flowable.
- [x] Conectada al backend real (`/api/v1`) con soporte para todas las etapas (1 a 11).
- [x] Etapa 9 en la web: Definir docente con `contexto.docentes`, Desarrollar curso con `POST .../finalizacion-dictado`, y Archivar expediente con `contexto.informe` y descarga de `/cursos/{id}/informe/pdf`.
- [x] CSRF (etapa 11): `CsrfFilter` en backend y envío de `X-XSRF-TOKEN` en `web/src/api/cliente.ts`.

---

### 7. Entregables de la consigna fuera del backend (V3)

#### 7.1 App de Materiales (`app-materiales/`, sección 4.2.1)
- [x] Aplicación de escritorio con interfaz gráfica en Java conectada **directamente a la base `flowable` de Postgres mediante la API Java de Flowable** (`TaskService`, `IdentityService`; sin pasar por el backend REST ni usar H2 en memoria).
- [x] Permitir identificarse a un administrativo, consultar tareas `preparar_materiales` asignadas o disponibles para su grupo, reclamar y completar la tarea.

#### 7.2 Consultas históricas sobre Flowable (sección 4.3)
Integradas en `app-materiales/` (botón **«Análisis histórico (§4.3)»** en la GUI y flag `--analisis` por consola):
- [x] **4.3.1 Resultados de las solicitudes:** tabla con cantidad y porcentaje sobre el total de instancias terminadas (`FINALIZADO`, `CANCELADO`, `RECHAZADO`, `NO_VIABLE`, `CERRADO_SIN_RESPUESTA`).
- [x] **4.3.2 Análisis de la preparación paralela:** duración promedio de las ramas de inscripciones, materiales y recursos (desde activación hasta finalización completa, excluyendo preparaciones canceladas).
- [x] **4.3.3 Distribución de tareas:** tabla con cantidad de tareas de usuario completadas por cada administrativo y docente (excluyendo pendientes, interrumpidas o canceladas).

#### 7.3 Introducción a la minería de procesos con PM4Py (sección 4.4)
Implementada en [`mineria-procesos/`](../mineria-procesos/README.md):
- [x] Exportar CSV de actividades históricas de la etapa de negociación adaptando `exportar_history.py` al proceso implementado (`negociacion_history.csv`).
- [x] Ejecutar análisis con `analizar.py` / PM4Py para obtener representación gráfica (`comportamiento_negociacion.png` / `.svg`) e identificar los **3 recorridos más frecuentes** (cantidad y %).
- [x] Redactar conclusión breve comparando los resultados con el modelo BPMN (coincidencias, diferencias y limitaciones) y guardar CSV, código/instrucciones y captura (`captura_resultados.png` y `mineria-procesos/README.md`).

#### 7.4 Entregables escritos y de demostración (sección 5)
- [x] Informe breve explicando variables del proceso, *listeners*, decisiones de implementación y guía de los 11 escenarios: **[docs/INFORME_ENTREGA.md](INFORME_ENTREGA.md)**.
- [x] Instrucciones para configurar usuarios/grupos y ejecutar el motor y las 3 aplicaciones externas (`web`/`backend`, `app-materiales`, `simulador-rest`): **[README.md](../README.md)**.

---

## Contrato de endpoints de la web

Registro de los endpoints del backend que consume el sitio web. Se completa **a medida que la web los necesita**; el backend los implementa a partir de este archivo.

- Base: `/api/v1` (según `docs/MODELO_Y_INTEGRACION.md#acuerdo-de-integración` §F). ⚠️ Confirmar con el back: el documento de arquitectura usa `/api`.
- Sesión por cookie HttpOnly. La web envía siempre `credentials: "include"`.
- **La web nunca envía** `usuarioId`, `assignee`, `empresaId` ni grupos: el backend los obtiene de la sesión.
- Errores con forma `{"codigo": "...", "mensaje": "..."}`. Códigos: `400 VALIDACION`, `401 NO_AUTENTICADO`, `403 SIN_PERMISO`, `404 NO_ENCONTRADO`, `409 ESTADO_INCOMPATIBLE | VERSION_DESACTUALIZADA | YA_RECLAMADA`, `503 MOTOR_NO_DISPONIBLE`.

### Estados de cada endpoint

| Estado | Significado |
|---|---|
| 📝 Pedido | La web lo necesita; falta acordarlo |
| ✅ Acordado | El contrato está confirmado con el back |
| 🧪 Mock | La web lo usa con un mock (MSW) |
| 🚀 Implementado | Está en el backend y probado desde la web |
| ➖ Descartado | Se resolvió de otra forma |

### Resumen

| # | Método y ruta | Pantalla / uso | Rol | En el acuerdo | Estado |
|---|---|---|---|---|---|
| 1 | `POST /solicitudes` | Formulario público | Público | Sí | 🚀 |
| 2 | `POST /sesiones/personal` | Login del personal | Público | Sí | 🚀 |
| 3 | `POST /sesiones/empresa` | Login de empresa | Público | Sí | 🚀 |
| 4 | `GET /sesiones/actual` | Saber quién está logueado al recargar | Cualquiera logueado | **No — nuevo** | 🚀 |
| 5 | `DELETE /sesiones/actual` | Logout | Cualquiera logueado | Sí | 🚀 |
| 6 | `GET /cursos` | Mis cursos | Empresa | Sí | 🧪 |
| 7 | `GET /cursos/{cursoId}` | Detalle y estado del curso | Empresa dueña / personal | Sí | 🧪 |
| 8 | `GET /cursos/{cursoId}/actividades` | Actividades en curso (ramas paralelas) | Empresa dueña / personal | Sí | 🧪 |
| 9 | `GET /tareas?bandeja=asignadas\|disponibles` | Bandeja de tareas | Personal | Sí | 🧪 |
| 10 | `GET /tareas/{taskId}` | Abrir el formulario de una tarea | Personal (asignado o candidato) | **No — nuevo** | 🧪 |
| 11 | `POST /tareas/{taskId}/reclamo` | Botón Reclamar | Personal candidato | Sí | 🧪 |
| 12 | `POST /tareas/{taskId}/completado` | Completar tareas simples | Asignado | Sí | 🧪 |
| 13 | `POST /tareas/{taskId}/propuesta` | Registrar/elaborar y revisar propuesta | Jefe administrativo | Sí | 🧪 |
| 14 | `GET /cursos/{cursoId}/propuestas` | Historial de versiones; propuesta aceptada en el portal | Jefe admin / empresa dueña | **No — nuevo** | 🧪 |
| 15 | `GET /cursos/{cursoId}/propuestas/{version}/pdf` | Ver el PDF de la propuesta (Revisar y portal de empresa) | Jefe admin / empresa dueña | Sí | 🧪 |
| 16 | ~~`GET /docentes`~~ | **No hace falta**: los docentes vienen en el `contexto` del #10 | — | — | ➖ |
| 17 | `POST /tareas/{taskId}/docente` | Definir docente | Jefe docente | Sí | 🧪 |
| 18 | `POST /tareas/{taskId}/finalizacion-dictado` | Desarrollar curso | Docente asignado | Sí | 🧪 |
| 19 | `GET /cursos/{cursoId}/informe/pdf` | Ver el informe antes de enviarlo | Jefe admin / jefe docente / empresa dueña | Sí | 🧪 |

---

### Detalle

#### 1. `POST /solicitudes`
- **Pantalla:** `/solicitar` (pública).
- **Header:** `Idempotency-Key: <uuid generado por la web al abrir el formulario>`.
- **Request:**
```json
{
  "empresa": {"nombre": "Empresa Demo", "identificador": "empresa-demo"},
  "contacto": {"nombre": "Contacto Demo", "email": "contacto@example.invalid", "telefono": "358..."},
  "tematica": "Procesos de negocio",
  "descripcion": "Capacitación introductoria",
  "cantidadEstimadaParticipantes": 12,
  "fechasPreferidas": "Primera semana de diciembre, por la mañana"
}
```
- **Response 201:** `{"cursoId": "...", "processInstanceId": "...", "businessKey": "curso:..."}`
- **Errores:** `400` (campos inválidos), `409` (misma clave con otros datos), `503`.
- **Notas:** `empresa.identificador` (CUIT u otro) y `contacto.telefono` son **opcionales**: la web no los envía si están vacíos. El teléfono no está en el ejemplo del acuerdo, pero sí en la arquitectura. Si se reintenta con la misma `Idempotency-Key` y los mismos datos, devolver la misma respuesta (201); con datos distintos, 409.

#### 2. `POST /sesiones/personal`
- **Request:** `{"usuarioId": "admin_jefe", "clave": "..."}`
- **Response 200:** `{"usuarioId": "admin_jefe", "nombre": "...", "grupos": ["administracion"], "esJefe": true}`
- **Errores:** `401`, `503`.
- **Notas:** pedir que devuelva `esJefe` (o un `rol`) para no deducirlo en la web a partir del ID.

#### 3. `POST /sesiones/empresa`
- **Request:** `{"usuario": "...", "clave": "..."}`
- **Response 200:** `{"empresaId": "...", "nombre": "Empresa Demo"}`
- **Errores:** `401`.

#### 4. `GET /sesiones/actual` — nuevo
- **Uso:** al cargar la app, saber si hay sesión y de qué tipo, para proteger rutas y armar el menú.
- **Response 200:**
```json
{"tipo": "PERSONAL", "usuarioId": "admin_jefe", "grupos": ["administracion"], "esJefe": true}
```
o
```json
{"tipo": "EMPRESA", "empresaId": "...", "nombre": "Empresa Demo"}
```
- **Errores:** `401` si no hay sesión.

#### 5. `DELETE /sesiones/actual`
- **Response:** `204`.

#### 6. `GET /cursos`
- **Query:** `page`, `size` (opcionales).
- **Response 200:**
```json
{"items": [{"cursoId": "...", "nombre": "...", "estado": "EN_PREPARACION", "estadoEtiqueta": "En preparación", "fechaCurso": null}], "total": 1, "page": 0, "size": 20}
```
- **Notas:** solo cursos de la empresa de la sesión (escenario 10).

#### 7. `GET /cursos/{cursoId}`
- **Response 200:**
```json
{
  "cursoId": "...",
  "nombre": "Atención al cliente",
  "estado": "EN_PREPARACION",
  "estadoEtiqueta": "En preparación",
  "fechaCurso": "2026-11-25T09:00:00-03:00",
  "descripcion": "Comunicación y manejo de reclamos para el equipo de atención.",
  "cantidadParticipantes": 5
}
```
- **Errores:** `404` también si el curso es de otra empresa (no revelar que existe).
- **Notas:** la web muestra una línea de progreso según `estado`. Códigos que usa:

| `estado` | Etiqueta |
|---|---|
| `EN_ACUERDO` | En evaluación o acuerdo |
| `EN_PREPARACION` | En preparación |
| `CONFIRMADO_PENDIENTE_INICIO` | Confirmado y pendiente de inicio |
| `EN_DESARROLLO` | En desarrollo |
| `EN_ELABORACION_INFORME` | En elaboración del informe |
| `EN_CIERRE` | Cierre en curso |
| `CANCELACION_EN_CURSO` | Cancelación en curso |
| `FINALIZADO` | Finalizado |
| `CANCELADO` | Cancelado |

#### 8. `GET /cursos/{cursoId}/actividades`
- **Response 200:**
```json
{
  "estado": "EN_PREPARACION",
  "items": [
    {"activityId": "Material", "nombre": "Preparar materiales", "tipo": "TAREA_USUARIO"},
    {"activityId": "Nomina", "nombre": "Recibir nómina", "tipo": "ESPERA_MENSAJE"}
  ]
}
```
- **Notas:**
  - `tipo` puede ser `TAREA_USUARIO` (se muestra "En curso"), `ESPERA_MENSAJE` ("Esperando a la empresa", por ejemplo la nómina) o `AUTOMATICA`.
  - `nombre` tiene que ser legible para la empresa, sin exponer nombres del personal.
  - Durante la preparación hay que devolver **todas** las actividades activas (una por rama paralela). Fuera de la preparación, `items` vacío.
  - Mismo control de acceso que el #7: `404` si el curso es de otra empresa.

#### 9. `GET /tareas?bandeja=asignadas|disponibles`
- **Response 200:**
```json
{"items": [{"taskId": "...", "taskDefinitionKey": "Propuesta", "nombre": "Registrar y elaborar propuesta", "cursoId": "...", "cursoNombre": "...", "empresaNombre": "...", "creada": "2026-11-09T18:00:00-03:00", "asignado": "admin_jefe", "reclamable": false}], "total": 1, "page": 0, "size": 20}
```
- **Notas:**
  - `asignadas`: tareas con `assignee` = usuario de la sesión. `disponibles`: tareas sin asignar cuyo grupo candidato incluye al usuario.
  - La web muestra el botón Reclamar solo si `reclamable` es `true` (lo calcula el back). Con `409 YA_RECLAMADA` refresca la lista.
  - Las tareas que no se completan en la web (Preparar materiales, aula virtual, equipamiento, liberar equipamiento, elaborar informe) se muestran con una etiqueta, sin botón.

#### 10. `GET /tareas/{taskId}` — nuevo
- **Uso:** abrir `/tareas/:id` y elegir el formulario por `taskDefinitionKey`.
- **Response 200:** datos de la tarea más un `contexto` que depende del tipo:

| `taskDefinitionKey` | `contexto` necesario |
|---|---|
| `Propuesta` | `solicitud`: `{ tematica, descripcion, cantidadEstimadaParticipantes, fechasPreferidas }` |
| `Revisar` | `solicitud` + `propuestaVigente`: `{ version, programa, presupuesto, fechasPosibles, estado, observacionesEmpresa, creada }` + `observacionesEmpresa` |
| `Archivar` | `motivoArchivo`: `RECHAZADA` o `SIN_RESPUESTA` |
| `Lista` | `participantes`: `[{ "nombre": "..." }]` (la nómina que envió la empresa) |
| `Activity_12i2hpf` | Nada extra |
| `Activity_1018bdz` | `curso`: `{ descripcion, cantidadParticipantes, fechaCurso }` + `docentes`: `[{ usuarioId, nombre, esJefe }]` (los tres del equipo, el jefe incluido) |
| `Dictar` | `curso`: `{ descripcion, cantidadParticipantes, fechaCurso }` |
| `EnviarInforme` | `curso` + `informe`: `{ resumen, pdfDisponible }` |

- **Ejemplo (Revisar):**
```json
{
  "taskId": "t-8",
  "taskDefinitionKey": "Revisar",
  "nombre": "Revisar propuesta",
  "cursoId": "c-6",
  "cursoNombre": "Ventas consultivas",
  "empresaNombre": "Otra Empresa SRL",
  "creada": "2026-11-09T12:30:00-03:00",
  "asignado": "admin_jefe",
  "reclamable": false,
  "contexto": {
    "solicitud": {"tematica": "Ventas consultivas", "descripcion": "...", "cantidadEstimadaParticipantes": 8, "fechasPreferidas": "Martes y jueves por la tarde"},
    "propuestaVigente": {"version": 1, "programa": "...", "presupuesto": {"importe": "200000.00", "moneda": "ARS"}, "fechasPosibles": ["2026-12-02T14:00:00-03:00"], "estado": "MODIFICACIONES", "observacionesEmpresa": "...", "creada": "..."},
    "observacionesEmpresa": "El presupuesto nos queda alto..."
  }
}
```
- **Errores:** `403` si no es asignado ni candidato, `404` si no existe o ya fue completada.
- **Notas:** si la tarea es de grupo y nadie la reclamó (`reclamable: true`), la web no muestra el formulario: pide reclamarla desde la bandeja.

#### 11. `POST /tareas/{taskId}/reclamo`
- **Request:** `{}`
- **Response 200:** la tarea, ya asignada al usuario de la sesión.
- **Errores:** `409 YA_RECLAMADA` → la web refresca la bandeja.

#### 12. `POST /tareas/{taskId}/completado`
- **Request:** `{"datos": {...}}`, con datos admitidos según la tarea:

| `taskDefinitionKey` | `datos` |
|---|---|
| `Lista` | `{"participantes": [{"nombre": "..."}]}` |
| `Activity_12i2hpf` | `{"requiereAula": true, "requierePlataforma": false, "requiereEquipamiento": true}` (al menos uno `true`) |
| `Archivar`, `EnviarInforme` | `{}` |
| `Enviar` | `{}` (solo si Alejo la deja como tarea humana) |

- **Response 200:** `{"completada": true}` (la web no usa el cuerpo).
- **Errores:** `400 VALIDACION` con mensaje (la web lo muestra tal cual), `403` si no es el asignado, `409`.
- **Notas:**
  - Validar en el back: lista definitiva con al menos un participante; en Evaluar recursos, al menos un recurso en `true`.
  - Los nombres `requiereAula`, `requierePlataforma`, `requiereEquipamiento` tienen que coincidir con las condiciones del gateway inclusivo del BPMN.

#### 13. `POST /tareas/{taskId}/propuesta`
- **Uso:** tareas `Propuesta` y `Revisar`. Genera el PDF y completa la tarea.
- **Request:**
```json
{"programa": "...", "presupuesto": {"importe": "150000.00", "moneda": "ARS"}, "fechasPosibles": ["2026-12-01T09:00:00-03:00"]}
```
- **Response 201:** `{"propuestaId": "...", "propuestaVersion": 1, "pdfDisponible": true}`
- **Errores:** `400` (programa vacío, importe ≤ 0 o sin fechas), `403`, `409 VERSION_DESACTUALIZADA` (la web pide reabrir la tarea).
- **Notas:** importe como texto con 2 decimales (evita redondeos). Fechas en ISO con offset de Argentina (`-03:00`). En `Revisar` se crea la versión siguiente sin pisar las anteriores.

#### 14. `GET /cursos/{cursoId}/propuestas` — nuevo
- **Uso:** en Revisar propuesta, mostrar las versiones anteriores con su PDF. En el portal de empresa, encontrar la propuesta `ACEPTADA` para mostrar presupuesto y PDF.
- **Response 200:**
```json
{"items": [{"version": 1, "programa": "...", "presupuesto": {"importe": "200000.00", "moneda": "ARS"}, "fechasPosibles": ["2026-12-02T14:00:00-03:00"], "estado": "MODIFICACIONES", "observacionesEmpresa": "...", "creada": "2026-11-05T10:00:00-03:00"}]}
```
- **Notas:** `estado` de cada versión: `ENVIADA`, `MODIFICACIONES`, `ACEPTADA` o `RECHAZADA`. La empresa dueña del curso también lo usa (filtrar por sesión).

#### 15. `GET /cursos/{cursoId}/propuestas/{version}/pdf`
- **Response 200:** `application/pdf`. La web lo descarga con la cookie de sesión y lo abre en otra pestaña.
- **Errores:** `404` si no existe la versión o el curso es de otra empresa.
- **Notas:** la empresa dueña del curso también lo usa, para ver la propuesta aceptada en su portal.

#### 16. ~~`GET /docentes`~~ — descartado
Se eligió la alternativa más simple: la lista de docentes viene en `contexto.docentes` de `GET /tareas/{taskId}` cuando la tarea es `Activity_1018bdz`. El back no necesita implementar este endpoint.

#### 17. `POST /tareas/{taskId}/docente`
- **Uso:** tarea `Activity_1018bdz` (Definir docente), solo el jefe docente asignado.
- **Request:** `{"docenteId": "docente_01"}`
- **Response 200:** `{"completada": true}`.
- **Errores:** `400 VALIDACION` si no es uno de los tres docentes, `403` si no es el asignado, `409` si la tarea no es de este tipo.
- **Notas:** el back guarda `docenteId` como variable del proceso y completa la tarea. "Desarrollar curso" queda asignada directamente a ese usuario (sin reclamo).

#### 18. `POST /tareas/{taskId}/finalizacion-dictado`
- **Uso:** tarea `Dictar` (Desarrollar curso), solo el docente asignado.
- **Request:** `{}`
- **Response 200:** `{"completada": true}`.
- **Errores:** `403` si no es el asignado, `409` si la tarea no está activa (la web muestra "el curso no está en desarrollo").

#### 19. `GET /cursos/{cursoId}/informe/pdf`
- **Uso:** botón "Ver informe (PDF)" en Enviar informe y archivar expediente.
- **Response 200:** `application/pdf`.
- **Errores:** `404` si no hay PDF.
- **Notas:** el PDF del informe ya no es obligatorio en la V3 (está tachado en rojo). La web muestra siempre `contexto.informe.resumen` y el botón del PDF solo si `pdfDisponible` es `true`.

---

### Pendientes de definir con el back
- [ ] Base `/api/v1` o `/api` (el acuerdo y la arquitectura no coinciden).
- [ ] Nombres de variables definitivos (deben coincidir con el BPMN): `requierePlataforma` vs `requiereAulaVirtual`, `fechaCurso` vs `fechaInicioCurso`, `docenteId` vs `docenteSeleccionado`.
- [x] Protección CSRF: cookie `XSRF-TOKEN` (`HttpOnly=false`) y header `X-XSRF-TOKEN` (`CsrfFilter`).
- [ ] Si la web se sirve desde el backend (`static/`) o por separado (y si hace falta CORS).
- [ ] Si `Enviar propuesta` es tarea humana (necesita pantalla) o automática.

### Implementación etapa 6 (backend-alejo)

Los endpoints #9–#12 están implementados en `/api/v1`, con pruebas automatizadas.
Se conserva 🧪 en la tabla hasta comprobarlos desde la web. Devuelven los IDs
actuales del BPMN (tabla en «Avance y etapas», más arriba), sin traducirlos a los
IDs viejos de los mocks. `archivar_expediente` también admite completado con datos
vacíos. La adaptación de los formularios web a esos IDs queda pendiente.

### Implementación etapa 9 (backend-lauty)

Los endpoints #17, #18 y #19 están implementados en `/api/v1` y probados con Postman
contra Flowable real (`docs/postman/etapa 9/`). Se conserva 🧪 en la tabla hasta
probarlos desde la web.

- #17: `contexto.docentes` usa `usuarioId` (no `id`). Con un docente que no es del
  grupo responde 400; si la tarea no es `definir_docente`, 409.
- #18: si la tarea ya no existe (completada o el curso todavía no empezó) responde
  409 `ESTADO_INCOMPATIBLE`.
- #19: el PDF se genera desde `contexto.informe.resumen`. Si no hay informe, 404.
  Lo ven los dos jefes y la empresa dueña; otro personal recibe 403.
- `elaborar_informe` se completa en Flowable UI (formulario *Informe de resultados*):
  la web la muestra solo como etiqueta. `POST /cursos/{id}/informe` no se implementa.
- Los IDs de tarea que devuelve el backend son los del BPMN actual (`definir_docente`,
  `desarrollar_curso`, `enviar_informe`, `archivar_expediente`), no los de los mocks.

---

## Etapa 6 implementada (detalle)

Fecha: 12/11/2026. Rama: `backend-alejo`.
Commit de implementación: `627047c`.

### Base integrada

La rama inicialmente tenía el backend básico. Se integraron `backend` y luego
`origin/backend`, incluyendo las etapas 1 a 5 que necesita esta implementación:
modelo institucional, repositorios, errores, cliente Flowable, sesiones y solicitudes.

Se resolvieron los conflictos de `.gitignore` y `docker-compose.yml`. Se conservaron
los servicios web/backend de la rama de trabajo y las reglas de archivos generados
y configuración local. No se hizo push ni se desplegó el proceso.

### Endpoints implementados

Todos usan la base `/api/v1/tareas` y requieren una sesión de personal.
El usuario y sus grupos se obtienen de la sesión HTTP.

| Método y ruta | Comportamiento |
|---|---|
| `GET /api/v1/tareas?bandeja=asignadas` | Lista tareas asignadas al usuario de sesión. |
| `GET /api/v1/tareas?bandeja=disponibles` | Lista tareas sin asignar de sus grupos candidatos. |
| `GET /api/v1/tareas/{taskId}` | Devuelve resumen y contexto institucional de la tarea. |
| `POST /api/v1/tareas/{taskId}/reclamo` | Reclama para el usuario de sesión y devuelve el resumen actualizado. |
| `POST /api/v1/tareas/{taskId}/completado` | Valida y completa las tareas simples. |

La bandeja admite `page` (predeterminado 0) y `size` (predeterminado 20,
entre 1 y 100). Filtra por la clave del proceso configurado en el backend.
Devuelve `items`, `total`, `page` y `size`.

Cada resumen incluye `taskId`, `taskDefinitionKey`, `nombre`, `cursoId`,
`cursoNombre`, `empresaNombre`, `creada`, `asignado` y `reclamable`.
El detalle agrega `contexto` al mismo nivel.

### Autorización y errores

- Sin sesión: `401 NO_AUTENTICADO`.
- Sesión de empresa: `403 SIN_PERMISO`.
- Para consultar: ser el asignado o candidato por grupo, verificado con identity links.
- Para reclamar: tener acceso y que no haya asignado. Un segundo reclamo,
  incluso del mismo usuario, devuelve `409 YA_RECLAMADA`.
- Un conflicto de Flowable durante claim se traduce también a `YA_RECLAMADA`.
- Para completar: ser el asignado; de lo contrario, `403 SIN_PERMISO`.
- La tarea debe estar vinculada por `processInstanceId` a un curso institucional.
- Tarea inexistente o curso no vinculado: `404 NO_ENCONTRADO`.
- Datos inválidos: `400 VALIDACION`.
- Tarea no admitida en completado simple: `409 ESTADO_INCOMPATIBLE`.
- Se conserva la traducción de fallas del motor a `503 MOTOR_NO_DISPONIBLE`.

### Datos de completado

El cuerpo tiene la forma `{"datos": {...}}`. Se rechazan campos extra.

| ID actual de tarea | Datos admitidos |
|---|---|
| `confeccionar_lista` | `participantes`: lista no vacía de objetos con `nombre` textual no vacío. |
| `evaluar_recursos` | `requiereAula`, `requierePlataforma`, `requiereEquipamiento`: los tres booleanos; al menos uno verdadero. |
| `archivar_solicitud` | Objeto vacío. |
| `enviar_informe` | Objeto vacío. |
| `archivar_expediente` | Objeto vacío. |

Ejemplo de lista:

```json
{"datos":{"participantes":[{"nombre":"Ana Pérez"}]}}
```

Ejemplo de recursos:

```json
{"datos":{"requiereAula":true,"requierePlataforma":false,"requiereEquipamiento":true}}
```

Las variables se envían a Flowable después de validar. Los participantes se
conservan como variable REST de tipo `json`; los recursos son booleanos.
La respuesta exitosa es `{"completada":true}`.
Propuesta, revisión, docente, dictado y tareas externas no pueden completarse
mediante esta operación genérica.

### Contexto del detalle

Los datos institucionales se consultan con JPA y las variables mediante REST.
No se accede directamente a tablas internas `ACT_*` del motor.

| Tarea | Contexto incorporado |
|---|---|
| `registrar_elaborar_propuesta` | Solicitud: temática, descripción, cantidad estimada y fechas preferidas. |
| `revisar_propuesta` | Solicitud, propuesta vigente si existe y observaciones de la empresa. |
| `confeccionar_lista` | Participantes de la variable del proceso, o lista vacía si no existe. |
| `archivar_solicitud` | Motivo `RECHAZADA` si la respuesta es rechazo; en otro caso `SIN_RESPUESTA`. |
| `evaluar_recursos` | Sin datos adicionales. |
| `definir_docente`, `desarrollar_curso` | Descripción del curso, cantidad de participantes y fecha. |
| `enviar_informe`, `archivar_expediente` | Datos del curso e informe con resumen y `pdfDisponible=false`. |

La propuesta vigente incluye versión, programa, presupuesto, fechas posibles,
estado, observaciones y fecha de creación. La generación/descarga de PDFs y la
lista de docentes corresponden a etapas posteriores.

### Archivos de código

Rutas relativas a `backend/src/main/java/ar/edu/cursos/`:

| Archivo | Responsabilidad |
|---|---|
| `controllers/TareaController.java` | Rutas HTTP y exigencia de sesión de personal. |
| `services/TareaService.java` | Bandejas, permisos, contexto, reclamo y validación del completado. |
| `services/TareasProceso.java` | Constantes de IDs de tareas usados en esta etapa. |
| `dto/TareaResumen.java` | Contrato del resumen de tarea. |
| `dto/TareaDetalle.java` | Resumen sin anidar y mapa de contexto. |
| `clients/FlowableClient.java` | Nuevas operaciones de tareas y soporte de variables JSON. |

Se agregaron al cliente `tareas`, `tarea`, `identityLinks`, `variables`,
`reclamar` y `completar`. Las lecturas institucionales del servicio usan una
transacción de solo lectura para resolver las relaciones de las entidades.

### Correcciones del BPMN

En `motor-flowable/modelos/organizacion-cursos.bpmn20.xml`:

1. Se eliminó la multiinstancia de `confeccionar_lista`: ahora es una única tarea.
2. `archivar_expediente` quedó asignada a `Jefe-Administrativo`.
3. Se agregaron las condiciones del gateway inclusivo de recursos:
   `requiereAula` para aula física, `requierePlataforma` para aula virtual
   y `requiereEquipamiento` para equipamiento.

Se comprobó que el XML sea válido y que los dos primeros cambios estén presentes.
No se publicó el modelo ni se migraron instancias. Los archivos `.zip` y `.bar`
exportados previamente siguen sin estos cambios: hay que importar el XML actualizado
en Modeler, incorporarlo a la app, publicar y regenerar los exports.

### Pruebas realizadas

Comando ejecutado satisfactoriamente:

```bash
mvn -f backend/pom.xml verify -Dstyle.color=never
```

Resultado: **BUILD SUCCESS; 11 pruebas, 0 fallas, 0 errores, 0 omitidas**.
Se generó el JAR ejecutable en `backend/target/`.

`TareaServiceTest` contiene nueve pruebas:

- Completar sin ser asignado se rechaza.
- Primer reclamo exitoso y segundo reclamo con conflicto.
- Consulta ajena rechazada por grupo.
- Recursos: rechazo de todos falsos y envío de booleanos válidos.
- Participantes: envío como JSON y rechazo de lista vacía.
- Propuesta rechazada en completado simple.
- Detalle serializado con campos de tarea al nivel superior.
- Bandeja asignada con empresa y sin posibilidad de reclamo.
- Campos extra rechazados sin enviar completado al motor.

`FlowableClientTest` contiene dos pruebas:

- Servidor HTTP de prueba: filtros de proceso, grupos y paginación;
  cuerpo de claim con usuario y traducción del conflicto 409.
- Conversión de participantes a variable REST JSON.

También pasó `git diff --check`. Estas pruebas usan mocks y un servidor HTTP de
prueba; no certifican el recorrido real del BPMN.

### Documentación actualizada

- `docs/BACKEND.md`: implementación, contratos y verificación.
- `docs/BACKEND.md#avance-y-etapas`: avance de etapa 6.
- `docs/BACKEND.md#contrato-de-endpoints-de-la-web`: nota de implementación y compatibilidad de IDs.
- `contexto/arquitectura-y-base-de-datos.md`: arquitectura efectiva de tareas.
- `motor-flowable/modelos/README.md`: correcciones y necesidad de republicación.

### Pendientes de integración

No había contenedores de Flowable ni PostgreSQL de este proyecto levantados.
No se probó con curl contra un motor real ni desde la web. Por eso los endpoints
no se marcaron como 🚀, cuyo significado documental exige validación desde la web.

Para cerrar la validación integrada:

1. Levantar PostgreSQL/Flowable y configurar `.env` y la base `institucion`.
2. Publicar el BPMN actualizado y regenerar los exports de la app.
3. Iniciar el backend y crear una solicitud con la etapa 5.
4. Verificar la bandeja del jefe administrativo y las tareas disponibles para
   el empleado administrativo cuando el proceso llegue a tareas de grupo.
5. Probar detalle, reclamo repetido y completado por un usuario distinto al asignado.
6. Adaptar catálogo, selección de formularios y mocks de la web a los IDs actuales;
   el frontend aún usa los anteriores. Validar luego los cuatro endpoints desde la web.

Las etapas 7 a 11, listeners del motor, asignación automática de la lista,
PDFs, simulador y despliegue Docker final mantienen su alcance pendiente.

---

## Integración del primer recorrido

Referencia fijada el 17/11/2026 en rama `app-materiales`, base `backend-lauty`
`228af43`. I1 documenta lo observado y propone los acuerdos para etapas 9–10;
las propuestas de esas partes requieren confirmación del compañero. I2 resuelve
almacenamiento Docker. I3–I6 todavía no están implementadas por este documento.

### Base verificable

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

### IDs que la web debe consumir en I3

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

### Variables y mensajes

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

### Preguntas para el compañero antes de I4–I5

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

### Reparto de transiciones a confirmar

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

### Estado de I1 e I2

- [x] I1: copia real del BPMN publicado y metadatos de referencia guardados.
- [x] I1: IDs, variables, diferencias y decisiones pendientes documentados.
- [x] I2: directorio de PDFs escribible por UID/GID `10001`, sin ejecutar como root.
- [x] I2: volumen persistente, configuración explícita y guía de operación.
- [x] I2: PDF generado/descargado por API y conservado tras recrear el backend, con el mismo SHA-256; fixtures eliminados.
- [ ] Confirmación del compañero sobre nómina, transiciones y cierre antes de I4–I5.

La prueba real y el mantenimiento del volumen se describen en
[Almacenamiento de PDFs en Docker](README.md#almacenamiento-de-pdfs-en-docker).

---

## Correcciones pendientes del BPMN

Revisión del 18/11/2026 contra la consigna V3 (`contexto/ProyectoCursos_V3_168631696404.pdf`, Figura 1) y el modelo `docs/proyecto-institucion.bpmn20.xml`. Es lo que hay que cerrar **antes de la etapa 11**.

**Aclaraciones de la V3 que cierran dudas anteriores:**
- «Enviar propuesta» y «Habilitar acceso de la empresa» son **automáticas** (script en la Figura 1). Solo «Archivar solicitud» es humana.
- «Recibir nómina» es una *receive task* (se destraba con señal): **no hace falta cambiarla a mensaje**.
- Solo son humanas: solicitar aula virtual, contratar equipamiento y liberar equipamiento. **Reservar y liberar aula física, y liberar aula virtual, son automáticas** (el aula física simula un web service).
- Hay **una sola tarea** «Enviar informe a empresa y archivar expediente» (jefe administrativo). Dividirla en dos es una modificación menor que habría que justificar.
- El resultado del proceso se fija en cada camino de cierre: `FINALIZADO`, `CANCELADO`, `RECHAZADO`, `CERRADO_SIN_RESPUESTA`, `NO_VIABLE`.

**Ya resuelto en el modelo:** `confeccionar_lista` sin multi-instancia, gateway inclusivo con condiciones, `desarrollar_curso` con `${docenteId}`, timer sobre `${fechaCurso}`, aula física automática y virtual humana, falta de aulas con `aulaDisponible=false` → `gw_hay_aula` → compensación → fin de terminación, y compuerta `gw_curso_cancelado` a la salida de `preparar_curso`.

**Estado tras la revisión del modelo del 18/11 (`motor-flowable/modelos/proyecto-institucion.bpmn20.xml` y `.zip`):**

| # | Corrección | Estado |
|---|---|---|
| 1 | `archivar_solicitud` assignee `Jefe-Administrativo` | ✅ |
| 2 | `elaborar_informe`: assignee `Jefe-Docente` y Form reference `formInforme` | 🔧 assignee hecho; **falta el formulario** (el backend funciona igual con `informeResumen` por REST) |
| 3 | `habilitar_acceso_empresa` automática | ✅ script (la lógica real está en `SimulacionService`) |
| 4 | `reservar_aula_fisica` / `liberar_aula` con script; falta de aulas sin `BpmnError` | ✅ con `simularSinAulas=true` el script pone `aulaDisponible=false` (ya no lanza `BpmnError('SIN_AULAS')`) |
| 5 | Errores `SIN_AULAS` y `CANCELADO_EMPRESA` | ✅ ya no hacen falta: el modelo **no usa eventos de error**. Las dos salidas anormales (cancelación de la empresa y falta de aulas) ponen `cursoCancelado=true`, y la compuerta `gw_curso_cancelado`, a la salida de `preparar_curso`, decide entre `notificarCierre` (`CANCELADO`) y `enviar_confirmacion`. El camino sin aulas (`gw_hay_aula` → compensar aula virtual y equipamiento → `marcar_sin_aulas`) termina con un **fin de terminación** (`fin_sin_aulas`) que cierra el subproceso |
| 6 | `liberar_aula_virtual` automática | ❌ sigue siendo tarea humana |
| 7 | Recordatorios (contador y condiciones) | ✅ lógica; el timer sigue en `P5D` (la etapa 10 lo dispara por API) |
| 8 | `resultadoProceso` en cada cierre | ✅ `NO_VIABLE`, `RECHAZADO`, `CERRADO_SIN_RESPUESTA`, `CANCELADO` (incluye sin aulas), `FINALIZADO` |
| 9 | «Enviar informe» y «Archivar expediente» en una sola tarea | ❌ siguen `enviar_informe` (script) + `archivar_expediente` (humana) |
| 10 | Viabilidad: contar instancias dentro del modelo | ❌ sigue dependiendo de `cantCursos` del backend |
| 11 | Scripts marcadores | 🔧 quedan `println` en `enviar_confirmacion`, `enviar_informe`, `habilitar_acceso_empresa`, `notificarCierre` |
| 12 | Ids y nombres | 🔧 quedan ids `sid-...` y los nombres «Liberar equipamento» y «confeccionar lista» |
| 13 | Listeners `AsignarListaListener` y `FinProcesoListener` | ✅ Implementados en `motor-flowable/extensiones/`, compilados en `flowable-extensiones.jar`, instalados en `flowable-persistente:/app/WEB-INF/lib/` y asociados en `institucion-de-capacitacion.bpmn20.xml` + `Organizacion_de_cursos.bar` |
| 14 | `.zip` y `.bar` actualizados | ✅ del 18/11. El `.bar` se genera con `corregir-asociaciones.py --bar` y es **lo que se despliega**; el `.zip` es el export del Modeler (sirve para editar, trae las asociaciones rotas) |
| 15 | **Conectores de los 3 eventos de borde de compensación** (`liberar_aula`, `liberar_aula_virtual`, `liberar_equipamiento`) | ✅ Son `<association ... associationDirection="One">` dentro de `preparar_curso` (versión 11 de `process`, 18/11). El Modeler 6.8.0 las vuelve a convertir en `<sequenceFlow>` cuando el modelo tiene pool (al importar, exportar y en **Publish**), y entonces el motor lanza `Compensation activity could not be found` al completar `reservar_aula_fisica`, `reservar_aula_virtual` o `contratar_equipamiento`. Por eso **no se publica desde el Modeler**: se exporta el XML, se corre `motor-flowable/modelos/corregir-asociaciones.py --bar` y se despliega con `desplegar.sh` (ver `motor-flowable/modelos/README.md`). Verificado por REST: cancelación con las tres compensaciones y cierre `CANCELADO` |

Pruebas del modelo contra el backend: `docs/postman/etapa 10/`.
