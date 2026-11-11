# Backend: qué está hecho y qué falta

Proyecto **Organización de cursos de capacitación** (GyTPN, UNRC). Estado al **11/11/2026**.

Este documento sirve para seguir el avance: qué quedó implementado y probado, y qué etapas faltan para completar todos los endpoints que usa la web (`docs/ENDPOINTS_WEB.md`).

---

## 1. Resumen

| Parte | Estado |
|---|---|
| Infraestructura (Docker, Postgres, `.env`, `.gitignore`) | ✅ Funcionando en las máquinas de Luka y Lauty |
| Usuarios y grupos en Flowable (`motor-flowable/idm`) | ✅ Script idempotente |
| Modelo del proceso publicado en Flowable | ✅ Se despliega (con marcadores en tareas automáticas) |
| Backend etapas 1 a 5 | ✅ Implementadas y probadas |
| Backend etapas 6 a 11 | ⏳ Pendientes |
| Web (etapas 1 a 9) | ✅ Completa contra mocks; falta conectarla al backend real |
| Extensiones del motor (listeners en Java) | ⏳ Pendiente |

### Endpoints

| # | Endpoint | Etapa | Estado backend |
|---|---|---|---|
| 1 | `POST /solicitudes` | 5 | 🚀 |
| 2 | `POST /sesiones/personal` | 4 | 🚀 |
| 3 | `POST /sesiones/empresa` | 4 | 🚀 |
| 4 | `GET /sesiones/actual` | 4 | 🚀 |
| 5 | `DELETE /sesiones/actual` | 4 | 🚀 |
| 9 | `GET /tareas?bandeja=...` | 6 | ⏳ |
| 10 | `GET /tareas/{taskId}` | 6 | ⏳ |
| 11 | `POST /tareas/{taskId}/reclamo` | 6 | ⏳ |
| 12 | `POST /tareas/{taskId}/completado` | 6 | ⏳ |
| 13 | `POST /tareas/{taskId}/propuesta` | 7 | ⏳ |
| 14 | `GET /cursos/{cursoId}/propuestas` | 7 | ⏳ |
| 15 | `GET /cursos/{cursoId}/propuestas/{version}/pdf` | 7 | ⏳ |
| 6 | `GET /cursos` | 8 | ⏳ |
| 7 | `GET /cursos/{cursoId}` | 8 | ⏳ |
| 8 | `GET /cursos/{cursoId}/actividades` | 8 | ⏳ |
| 17 | `POST /tareas/{taskId}/docente` | 9 | ⏳ |
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
- **El servicio `backend` del compose está comentado** hasta la etapa 11. Mientras tanto, el backend corre con `mvn spring-boot:run` en el puerto **8081**, que es el que usa el proxy de la web.
- **`.env`** en la raíz (no se sube): credenciales de Postgres, pgAdmin, la cuenta REST de Flowable y `CURSOS_DATOS_DEMO=true`. La plantilla es `env.example`.
- **`.gitignore` raíz:** `target/`, `node_modules/`, `dist/`, `.env*` (salvo los ejemplos), `application-local.*`, `backend/almacenamiento/`, `backend/archivos/`.

### 2.2 `motor-flowable/`
| Carpeta | Contenido |
|---|---|
| `idm/crear-usuarios.sh` | Crea los grupos `Equipo-Administrativo` y `Equipo-Docente` y los usuarios `Jefe-Administrativo`, `empleado-administrativo`, `Jefe-Docente`, `docente-1` y `docente-2` (clave `1234` por defecto). Es idempotente. |
| `modelos/` | App exportada del Modeler (`.zip`), BPMN y DMN, con un README para importarla y publicarla. |
| `prueba/` | Proceso mínimo de prueba. Se puede borrar cuando el modelo real esté estable. |

**Correcciones al modelo hechas hoy** (hay que dejarlas commiteadas en `modelos/`):
- Las 6 script tasks tienen scripts Groovy marcadores: `notificar_decision` además fija `resultadoProceso=NO_VIABLE`.
- "Reservar aula Virtual" y "Liberar aula virtual" usan una expresión que fija `aulaVirtualReservada`. Es provisorio.
- El inicio del subproceso de cancelación tiene `messageRef="cancelacionEmpresa"`.
- La tabla `reglaViabilidad` usa la entrada **`cantCursos`** (antes era `otrasInstanciasActivas`) y la salida `resultadoViabilidad`: `< 5` da `true` y `>= 5` da `false`.
- El id del proceso es **`process`**, que es el valor de `FLOWABLE_PROCESO_CLAVE` en el backend.

### 2.3 Backend (Spring Boot 3.5, Java 21, rama `backend`)
El backend habla con Flowable **solo por REST**: no incluye el motor como dependencia ni escribe en las tablas `ACT_*`.

| Etapa | Qué incluye |
|---|---|
| **1. Base** | `pom.xml` con web, validation, data-jpa, spring-security-crypto (BCrypt) y postgresql. `application.properties` lee `../.env`. `GET /api/v1/estado`. |
| **2. Modelo de datos** | Entidades `Empresa`, `CuentaEmpresa`, `Curso` (UUID, estado, `processInstanceId` único, `claveIdempotencia` única, `hashSolicitud`, `@Version`) y `Propuesta` (versiones, fechas posibles, ruta del PDF). Enum `EstadoCurso`. Repositorios. |
| **3. Errores y cliente de Flowable** | Formato `{codigo, mensaje}` con `ApiException` y `ManejadorErrores`. `FlowableClient` con `RestClient`, timeouts de 3 y 15 s y traducción de errores: sin conexión, 401/403 o 5xx → 503 `MOTOR_NO_DISPONIBLE`, y se loguea `Flowable respondió ...`. `GET /api/v1/diagnostico/flowable` (temporal). |
| **4. Sesiones** | Login del personal validando contra la identidad de Flowable, filtrado por los dos grupos (si no pertenece → 403). Login de empresa con BCrypt. Sesión con cookie `JSESSIONID` HttpOnly. `GET` y `DELETE /sesiones/actual`. `DatosDemo` crea `empresa-demo` / `1234`. |
| **5. Solicitudes** | `POST /solicitudes` con `Idempotency-Key` y hash SHA-256 del cuerpo. Guarda la empresa (la reutiliza por identificador) y el curso como `SOLICITADO`. Cuenta las instancias activas → `cantCursos`. Inicia el proceso con `businessKey=curso:<uuid>` y las variables. Pasa a `EN_ACUERDO`, o a `NO_VIABLE` si la instancia terminó enseguida. Si el motor falla, el reintento con la misma clave retoma el mismo curso. |

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

### 2.4 Web (React + Vite + TypeScript, rama `luka/web-base`)
- Etapas 1 a 9 completas **contra mocks (MSW)**: formulario público, login de personal y de empresa, bandeja de tareas, todos los formularios de tareas, portal de empresa con estado y actividades, y PDFs.
- `npm run dev:mock` (mocks) o `npm run dev` (backend real; el proxy manda `/api` a `localhost:8081`).
- Contra el backend real ya funciona el login (probado con `git worktree`).

---

## 3. Etapas que faltan

Cada etapa se cierra igual:
1. Probar con `curl` (y desde la web si ya está conectada).
2. Commitear.
3. Marcar los endpoints como 🚀 en `ENDPOINTS_WEB.md`.
4. Actualizar `ARQUITECTURA.md`.

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

**A implementar:**
- `POST /tareas/{id}/docente` (tarea `definir_docente`):
  - Validar que `docenteId` sea miembro de `Equipo-Docente` (`GET identity/users?memberOfGroup=...`).
  - Completar la tarea con la variable `docenteId`.
  - El contexto del #10 incluye `docentes` (los tres, con `esJefe`).
- `POST /tareas/{id}/finalizacion-dictado` (tarea `desarrollar_curso`, solo el asignado). Si la tarea no está activa → 409.
- `GET /cursos/{id}/informe/pdf`. Si el informe tiene PDF, se sirve. El resumen va en el contexto de `enviar_informe` (`informe.resumen`, `pdfDisponible`).
- Opcional, según el acuerdo: `POST /cursos/{id}/informe` (subir un PDF o generarlo desde el contenido).

**Depende del modelo:** `desarrollar_curso` tiene que tener `flowable:assignee="${docenteId}"` (hoy está asignada al grupo).

---

### Etapa 10 — Simulador de comunicaciones de la empresa
**Objetivo:** simular lo que la empresa haría por mail: aceptar, rechazar, pedir modificaciones, enviar la nómina y cancelar. Lo usa la GUI del simulador (4º integrante).

| Endpoint | Qué hace |
|---|---|
| `GET /simulacion/solicitudes` | Lista los cursos con las acciones habilitadas en su estado actual. |
| `GET /simulacion/solicitudes/{cursoId}/comunicaciones` | Muestra las acciones admitidas y la versión de propuesta vigente. |
| `POST /simulacion/solicitudes/{cursoId}/comunicaciones` | Aplica una comunicación. Es idempotente por `comunicacionId`. |

**Tipos de comunicación y su efecto:**

| `tipo` | Datos | Variables que fija | Mensaje BPMN |
|---|---|---|---|
| `ACEPTACION` | `propuestaVersion`, `fechaCurso` | `respuestaEmpresa=ACEPTADA`, `fechaCurso` | definir con Alejo |
| `RECHAZO` | `propuestaVersion` | `respuestaEmpresa=RECHAZADA` | definir con Alejo |
| `MODIFICACION` | `propuestaVersion`, `observacionesEmpresa` | `respuestaEmpresa=MODIFICACIONES`, `observacionesEmpresa` | definir con Alejo |
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
- **Escenarios de punta a punta:** viable, no viable, rechazo, modificaciones, falta de respuesta, cancelación con compensaciones y curso completo con informe.

---

## 4. Temas transversales

- **Estado del curso (`EstadoCurso`).** El backend lo actualiza cuando la acción pasa por él:
  - `EN_ACUERDO`: al iniciar.
  - `EN_PREPARACION`: al aceptar.
  - `EN_DESARROLLO`: cuando empieza el dictado.
  - ...y así con los demás.

  Las transiciones que hace solo el motor (timers, fin del proceso) las informa un listener. **Acordar la tabla completa** con Alejo y Lauty antes de la etapa 8.
- **Una sola variable de resultado:** `resultadoProceso` (`NO_VIABLE`, `CANCELADO`, `RECHAZADO`, `SIN_RESPUESTA`, `FINALIZADO`). El modelo todavía usa `resultado = 'cancelado'` en algún lugar.
- **Instancias de prueba:** cuentan para `cantCursos`. Borrarlas después de probar (`DELETE runtime/process-instances/{id}`).
- **La web nunca manda** `usuarioId`, `assignee` ni `empresaId`: el backend los toma de la sesión.

---

## 5. Pendientes del motor (`motor-flowable/`)

**Extensiones (jar con `maven.compiler.release=11`, que se carga en el contenedor de Flowable UI):**
- `AsignarListaListener`: Task Listener que asigna `confeccionar_lista` al administrativo con menos tareas.
- `FinProcesoListener`: Execution Listener de fin; escribe el estado final en `institucion` por JDBC (`postgres:5432`).
- Dockerfile de Flowable UI que agregue el jar. Hace falta la ruta de librerías de la imagen (está en el material de la cátedra).

**Correcciones del modelo que quedan** (de `INICIO_Y_VIABILIDAD.md`, sección 7):
- [ ] `desarrollar_curso` con `assignee="${docenteId}"`.
- [ ] `confeccionar_lista`: una sola tarea, no multi-instancia.
- [ ] Aula física y aula virtual invertidas: la física es la automática (web service simulado) y la virtual es la del administrativo.
- [ ] Error por falta de aulas (escenario 7).
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
- [ ] CSRF (etapa 11).
