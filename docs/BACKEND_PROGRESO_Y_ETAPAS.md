# Backend: qué está hecho y qué falta

Proyecto **Organización de cursos de capacitación** (GyTPN, UNRC). Estado al **17/11/2026**, rama **`backend-lauty`** (todavía sin el merge de `main`).

Este documento sirve para seguir el avance: qué quedó implementado y probado, y qué etapas faltan para completar todos los endpoints que usa la web (`docs/ENDPOINTS_WEB.md`) y los entregables de la consigna V3.

---

## 1. Resumen

| Parte | Estado |
|---|---|
| Infraestructura (Docker, Postgres, `.env`, `.gitignore`) | ✅ Funcionando en las máquinas de Luka y Lauty (reinstalada desde cero en la PC nueva de Luka el 17/11) |
| Usuarios y grupos en Flowable (`motor-flowable/idm`) | ✅ Script idempotente |
| Modelo del proceso publicado en Flowable | ✅ Se despliega (con marcadores en tareas automáticas). ⚠️ El `.zip` del repo está desactualizado (ver 2.2) |
| Backend etapas 1 a 5 | ✅ Implementadas y probadas |
| Backend etapa 6 | Implementada; validación integrada con motor/web pendiente |
| Backend etapa 7 | Implementada; validación integrada con motor/web pendiente |
| Backend etapa 8 | Implementada; validación integrada con motor/web pendiente |
| Backend etapa 9 | 🔄 En curso: paso 1 (definir docente, #17) implementado y probado con Postman contra Flowable real |
| Backend etapas 10 y 11 | ⏳ Pendientes |
| Web (etapas 1 a 9) | ✅ Completa contra mocks; falta conectarla al backend real |
| Extensiones del motor (listeners en Java) | ⏳ Pendiente |
| App de Materiales (`app-materiales/`, API Java) | ⏳ Pendiente |
| Consultas históricas (4.3) y Minería PM4Py (4.4) | ⏳ Pendiente |

### Endpoints

| Estado | Significado |
|---|---|
| 🚀 | Implementado y probado desde la web |
| ✅ | Implementado y probado con Postman contra Flowable y Postgres reales |
| 🔧 | Implementado con pruebas unitarias; falta la validación integrada |
| ⏳ | Pendiente |

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
| 18 | `POST /tareas/{taskId}/finalizacion-dictado` | 9 | ⏳ |
| 19 | `GET /cursos/{cursoId}/informe/pdf` | 9 | ⏳ |
| — | `POST /cursos/{cursoId}/informe` (del acuerdo; opcional para la web) | 9 | ⏳ |
| — | `GET /simulacion/solicitudes` | 10 | ⏳ |
| — | `GET /simulacion/solicitudes/{cursoId}/comunicaciones` | 10 | ⏳ |
| — | `POST /simulacion/solicitudes/{cursoId}/comunicaciones` | 10 | ⏳ |
| 16 | ~~`GET /docentes`~~ | — | ➖ Descartado (viene en el contexto del #10) |

---

## 2. Lo que ya está implementado

### 2.1 Infraestructura
- **Docker Compose:** Flowable UI 6.8 (`:8080`), Postgres (publicado en **`127.0.0.1:5433`** para todo el equipo) y pgAdmin.
- **Base `institucion`** dentro del mismo Postgres (dueño `flowable`). Hibernate crea las tablas (`ddl-auto=update`).
- **El servicio `backend` del compose existe pero no recibe las variables de Postgres ni de Flowable** en esta rama (la configuración completa llega con el merge de `main`). No levantarlo: ocupa el 8081. El backend corre con `mvn spring-boot:run` en el puerto **8081**, que es el que usa el proxy de la web. Levantar solo `docker compose up -d postgres flowable pgadmin`.
- **La base `institucion` se crea a mano** en esta rama: `docker exec -it flowable-postgres psql -U <POSTGRES_USER> -d <POSTGRES_DB> -c "CREATE DATABASE institucion;"`. En `main` la crea el servicio `institucion-db`.
- **`.env`** en la raíz (no se sube): credenciales de Postgres, la cuenta REST de Flowable y `CURSOS_DATOS_DEMO=true`. **Sin espacios alrededor del `=`**: lo leen bash (`crear-usuarios.sh`) y Spring. La plantilla `env.example` llega con `main`; mientras tanto, las variables mínimas están en el README principal.
- **`.gitignore` raíz:** `target/`, `node_modules/`, `dist/`, `.env*` (salvo los ejemplos), `application-local.*`, `backend/almacenamiento/`, `backend/archivos/`.

### 2.2 `motor-flowable/`
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
- `elaborar_informe`: assignee `Jefe-Docente` + formulario por referencia con el campo `informeResumen`. ⏳ Para el paso 3.
- `enviar_informe`: pasar de script a tarea de usuario de `Jefe-Administrativo`. ⏳ A confirmar con Alejo (paso 3).

Las instrucciones para asignar a una variable y crear formularios por referencia están en `motor-flowable/modelos/README.md`.

### 2.3 Backend (Spring Boot 3.5, Java 21, rama `backend-lauty`)
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
| **9. Dictado (paso 1)** | `DictadoController` / `DictadoService`: `POST /tareas/{id}/docente`. `TareaService.listarDocentes()` y `docentes` en el contexto de `definir_docente`. `DictadoServiceTest` (4 pruebas). |

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

### 2.4 Web (React + Vite + TypeScript, rama `luka/web-base`)
- Etapas 1 a 9 completas **contra mocks (MSW)**: formulario público, login de personal y de empresa, bandeja de tareas, todos los formularios de tareas, portal de empresa con estado y actividades, y PDFs.
- `npm run dev:mock` (mocks) o `npm run dev` (backend real; el proxy manda `/api` a `localhost:8081`).
- Contra el backend real ya funciona el login (probado con `git worktree`).

---

## 3. Etapas que faltan

Cada etapa se cierra igual:
1. Probar con Postman (colección `docs/postman/etapa 9/`, una carpeta por etapa) y desde la web si ya está conectada.
2. Commitear.
3. Marcar los endpoints como 🚀 en `ENDPOINTS_WEB.md`.
4. Actualizar `BACKEND.md` y este documento.

### Antes de la etapa 6: IDs de las tareas
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

### Etapa 6 — Tareas (#9, #10, #11, #12)

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

### Etapa 7 — Propuestas y PDF (#13, #14, #15)

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

### Etapa 8 — Portal de empresa y habilitación de acceso (#6, #7, #8)

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

### Etapa 9 — Docente, dictado e informe (#17, #18, #19)
**Objetivo:** el jefe docente elige al docente, el docente marca el dictado como terminado y el informe se puede ver.

Se implementa por pasos. Para probar sin recorrer todo el proceso se usan dos atajos de Flowable (carpeta `9. Flowable directo` de Postman): fijar `fechaCurso` en una fecha pasada y mover la instancia con `POST runtime/process-instances/{id}/change-state`.

| Paso | Qué incluye | Estado |
|---|---|---|
| 0 | Cambios del modelo (ver 2.2) | 🔄 `desarrollar_curso` listo; informe pendiente |
| 1 | #17 `POST /tareas/{id}/docente` + `docentes` en el contexto | ✅ Probado con Postman (17/11) |
| 2 | #18 `POST /tareas/{id}/finalizacion-dictado` | ⏳ |
| 3 | Informe: `informeResumen` en el contexto de `enviar_informe` + #19 `GET /cursos/{id}/informe/pdf` | ⏳ |
| 4 | Escenario completo en Postman y docs | ⏳ |

**Paso 1 — Definir docente (hecho):**
- `POST /tareas/{id}/docente` con `{"docenteId": "docente-1"}`, solo el asignado de `definir_docente` (403 si no), solo esa tarea (409 si es otra).
- Valida que `docenteId` sea miembro de `Equipo-Docente` (`TareaService.listarDocentes()`, que usa `FlowableClient.miembrosDe` y `buscarUsuario`) → 400 si no.
- Completa la tarea con la variable `docenteId` y pasa el curso a `CONFIRMADO_PENDIENTE_INICIO`.
- El contexto de `definir_docente` trae `docentes: [{usuarioId, nombre, esJefe}]`.
- `TareaService` tiene un constructor nuevo que recibe `cursos.grupo.docentes` y `cursos.usuario.jefe-docente`; el de 3 parámetros se conserva para los tests.
- Probado (carpeta `4. Etapa 9 - paso a paso`): 400 con un no docente, 200 con `docente-1`, 404/409 al repetir, y `desarrollar_curso` aparece asignada a `docente-1` después del timer.

**Paso 2 — Finalización del dictado (pendiente):**
- `POST /tareas/{id}/finalizacion-dictado` con `{}`: solo `desarrollar_curso` y solo el asignado (403). Si la tarea no está activa → 409.
- Completa la tarea y pasa el curso a `EN_ELABORACION_INFORME`.
- `EN_DESARROLLO` lo marca el motor cuando vence el timer (listener, etapa 11).

**Paso 3 — Informe (pendiente):**
- El jefe docente completa `elaborar_informe` desde Flowable UI con el formulario por referencia (campo `informeResumen`). La web la muestra solo como etiqueta.
- El backend lee `informeResumen` y lo pone en el contexto de `enviar_informe` / `archivar_expediente` (`informe.resumen`, `pdfDisponible`).
- `GET /cursos/{id}/informe/pdf`: PDF generado al vuelo con OpenPDF desde el resumen. Lo ven el jefe administrativo, el jefe docente y la empresa dueña; si no hay informe o el curso es ajeno → 404. *(En la V3 el PDF del informe ya no es obligatorio).*
- Opcional, según el acuerdo: `POST /cursos/{id}/informe`.

**Depende del modelo:** `desarrollar_curso` con `${docenteId}` (hecho), timer con `${fechaCurso}` (hecho), formulario de `elaborar_informe` y `enviar_informe` como tarea de usuario (pendientes).
`fechaCurso` la fija la comunicación `ACEPTACION` de la etapa 10; el diseño no tiene fechas fijas (sección 3.5 de la V3). Hasta entonces se fija a mano con Postman.

---

### Etapa 10 — Simulador de comunicaciones de la empresa
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
- `SimulacionService` y `SimulacionController`. El permiso de simulación es para una sesión de personal.
- Una tabla para registrar cada comunicación (`comunicacionId` único) y así responder igual ante una repetición.
- Validar la versión de la propuesta: si no coincide con la vigente → 409.

**Depende del modelo:**
- `messageRef` en todos los mensajes, y la tabla de nombres acordada con Alejo.
- "Recibir nómina" como evento intermedio de mensaje.
- Timers de recordatorio con su contador.

---

### Etapa 11 — Integración final
- **Cierre del curso:** el estado final (`FINALIZADO`, `CANCELADO`, `RECHAZADO`, `CERRADO_SIN_RESPUESTA`, `NO_VIABLE`) lo escribe el `FinProcesoListener` del motor (ver la sección 5) a partir de `resultadoProceso`.
- **CSRF** en las peticiones que modifican datos: token en una cookie y header `X-XSRF-TOKEN` en la web.
- **Servir la web:** copiar el `dist/` de la web a `static/` del backend, o seguir sirviéndola aparte con el proxy (en ese caso, revisar CORS).
- **Backend en Docker:** descomentar el servicio del compose (el Dockerfile multi-stage ya existe) y usar `FLOWABLE_REST_URL=http://flowable:8080/...` y `postgres:5432` dentro de la red.
- **Sacar** `DiagnosticoController` y `motor-flowable/prueba/`.
- **Escenarios de punta a punta:** verificar los 11 escenarios de demostración de la sección 5 de la V3 (aceptación y finalización con liberación de recursos, modificaciones, rechazo, falta de respuesta, no viable por límite de 5 cursos, cancelación con compensaciones, falta de aulas disponibles, asignación/reclamo de tareas, solicitud web, portal de empresa, y app de materiales por API Java + simulador REST).

---

## 4. Temas transversales

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

## 5. Pendientes del motor (`motor-flowable/`)

**Extensiones (jar con `maven.compiler.release=11`, que se carga en `/app/WEB-INF/lib/` del contenedor de Flowable UI):**
- `AsignarListaListener`: Task Listener en el evento `create` de `confeccionar_lista`; consulta el grupo `Equipo-Administrativo`, cuenta solo tareas de usuario pendientes **asignadas** (sin contar candidatas sin reclamar) y asigna al que tenga menos.
- `FinProcesoListener`: Execution Listener de fin; escribe el estado final en `institucion` por JDBC (`postgres:5432`) según `resultadoProceso`.
- Dockerfile o script de copia del `.jar` a `flowable-persistente:/app/WEB-INF/lib/` y reinicio del contenedor.

**Correcciones del modelo que quedan** (de `INICIO_Y_VIABILIDAD.md`, sección 7):
- [x] `desarrollar_curso` con `assignee="${docenteId}"` y timer previo *Fecha programada* leyendo `${fechaCurso}` (publicado en el Flowable de Luka; falta exportar el `.zip`).
- [ ] Volver a exportar `Organizacion de cursos.zip` con el DMN `cantCursos` y los cambios de la etapa 9.
- [ ] `elaborar_informe`: assignee `Jefe-Docente` + formulario por referencia con `informeResumen`.
- [ ] `enviar_informe`: tarea de usuario de `Jefe-Administrativo` (hoy es script).
- [ ] `confeccionar_lista`: una sola tarea, no multi-instancia.
- [ ] Aula física y aula virtual invertidas: la física es la automática (web service simulado) y la virtual es la del administrativo.
- [ ] Error por falta de aulas disponibles y generación de la excepción correspondiente (escenario 7).
- [ ] "Habilitar acceso de la empresa": automática.
- [ ] `archivar_expediente`: asignada al jefe administrativo.
- [x] `messageRef` del inicio de la cancelación (`cancelacionEmpresa`).
- [ ] Declarar `<error id="CANCELADO_EMPRESA">`.
- [ ] Recordatorios: condición del gateway y contador.
- [ ] "Recibir nómina" como evento intermedio de mensaje.
- [ ] Unificar la variable de resultado en `resultadoProceso`.
- [ ] Reemplazar los scripts y las expresiones marcadores por la lógica real, donde corresponda.

---

## 6. Pendientes de la web
- [ ] Actualizar los IDs de las tareas (tabla de la sección 3) y los usuarios y grupos (`Jefe-Administrativo`, `Equipo-Administrativo`, etc.) en los mocks.
- [ ] PR de `luka/web-base` → `main`, y después merge de `main` en `backend`.
- [ ] Pasar del mock al backend real endpoint por endpoint, a medida que cada etapa queda 🚀.
- [ ] Formulario de Definir docente: tomar la lista de `contexto.docentes` (`usuarioId`, `nombre`, `esJefe`) que ya devuelve el backend.
- [ ] CSRF (etapa 11).

---

## 7. Pendientes de la consigna fuera del backend (V3)

### 7.1 App de Materiales (`app-materiales/`, sección 4.2.1)
- [ ] Aplicación de escritorio con interfaz gráfica en Java conectada **directamente a la base `flowable` de Postgres mediante la API Java de Flowable** (`TaskService`, `IdentityService`; sin pasar por el backend REST ni usar H2 en memoria).
- [ ] Permitir identificarse a un administrativo, consultar tareas `preparar_materiales` asignadas o disponibles para su grupo, reclamar y completar la tarea.

### 7.2 Consultas históricas sobre Flowable (sección 4.3)
Pueden integrarse en `app-materiales/` o ejecutarse y mostrarse por consola:
- [ ] **4.3.1 Resultados de las solicitudes:** tabla con cantidad y porcentaje sobre el total de instancias terminadas (`FINALIZADO`, `CANCELADO`, `RECHAZADO`, `NO_VIABLE`, `CERRADO_SIN_RESPUESTA`).
- [ ] **4.3.2 Análisis de la preparación paralela:** duración promedio de las ramas de inscripciones, materiales y recursos (desde activación hasta finalización completa, excluyendo preparaciones canceladas).
- [ ] **4.3.3 Distribución de tareas:** tabla con cantidad de tareas de usuario completadas por cada administrativo y docente (excluyendo pendientes, interrumpidas o canceladas).

### 7.3 Introducción a la minería de procesos con PM4Py (sección 4.4)
- [ ] Exportar CSV de actividades históricas de la etapa de negociación adaptando `exportar_history.py` al proceso implementado.
- [ ] Ejecutar análisis con `analizar.py` / PM4Py para obtener representación gráfica e identificar los **3 recorridos más frecuentes** (cantidad y %).
- [ ] Redactar conclusión breve comparando los resultados con el modelo BPMN (coincidencias, diferencias y limitaciones) y guardar CSV, código/instrucciones y captura.

### 7.4 Entregables escritos y de demostración (sección 5)
- [ ] Informe breve explicando variables del proceso, *listeners* y decisiones de implementación.
- [ ] Instrucciones para configurar usuarios/grupos y ejecutar el motor y las 3 aplicaciones externas (`web`/`backend`, `app-materiales`, `simulador-rest`).