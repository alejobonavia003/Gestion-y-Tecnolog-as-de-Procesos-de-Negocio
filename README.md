# Gestión y Tecnologías de Procesos de Negocio

Proyecto académico (GyTPN, UNRC): proceso de **organización de cursos de capacitación**
para empresas, ejecutado en **Flowable 6.8** con un backend propio, una web y aplicaciones
auxiliares.

## Estado actual (17/11/2026)

| Parte | Estado |
|---|---|
| Infraestructura Docker (Flowable UI, PostgreSQL, pgAdmin, web) | ✅ Funcionando |
| Usuarios y grupos de Flowable (`motor-flowable/idm/crear-usuarios.sh`) | ✅ |
| Modelo del proceso + regla DMN de viabilidad | ✅ Se publica (con tareas automáticas marcadoras) |
| Backend etapas 1 a 5 (base, datos, cliente Flowable, sesiones, solicitudes) | ✅ Implementadas y probadas |
| Backend etapas 6 a 8 (tareas, propuestas y PDF, portal de empresa) | 🔧 Implementadas; falta validarlas desde la web |
| Backend etapa 9 (docente, dictado, informe) | ✅ Completa: #17, #18 y #19 probados con Postman contra Flowable real |
| Backend etapa 10 (simulación de la empresa) | 🔧 Implementada; falta validarla con Flowable real |
| Backend etapa 11 (integración final) | ⏳ Pendiente; antes hay que cerrar el modelo BPMN (ver [docs/BACKEND.md](docs/BACKEND.md#correcciones-pendientes-del-bpmn)) |
| Web (React + Vite) | ✅ Completa contra mocks; falta conectarla al backend real |
| App de materiales y simulador REST | 🔧 Código en el repo; falta validarlos de punta a punta |
| Listeners del motor | ⏳ Pendientes |

El detalle de qué está hecho y qué falta está en
[docs/BACKEND.md (avance y etapas)](docs/BACKEND.md#avance-y-etapas).

## Estructura y responsabilidades

| Ruta | Objetivo | Responsable |
|---|---|---|
| [motor-flowable/](motor-flowable/) | BPMN ejecutable, DMN, usuarios y grupos, listeners, mensajes, timers y compensación. | Alejo |
| [backend/](backend/) | API REST (Spring Boot) y base institucional: empresas, cursos, propuestas, PDFs, vínculo con las instancias de Flowable. | Lauty |
| [web/](web/) | Sitio público, portal de empresas y bandeja del personal. | Luka |
| [app-materiales/](app-materiales/) | App de escritorio para "Preparar materiales" con la API Java de Flowable. | Luka |
| [simulador-rest/](simulador-rest/) | CLI que simula las comunicaciones de la empresa. | Luka |
| [docs/](docs/) | Acuerdos, contratos, progreso y colecciones de Postman. | Todos |
| [contexto/](contexto/) | Consigna y modelos de referencia. | Referencia común |

## Requisitos

- Docker con Docker Compose.
- JDK 21 y Maven (para correr el backend en desarrollo).
- Git. Node.js 20.19+ solo si se trabaja en la web fuera de Docker.
- Postman de escritorio (para probar el backend).

## Primera instalación en una máquina

Hacer esto **una vez** por máquina (o cuando se borre el volumen de Postgres).
Todos los comandos se corren desde la raíz del repo. La guía detallada de Docker está en
[docs/README.md (instalación con Docker)](docs/README.md#instalación-del-equipo-con-docker).

### 1. Archivo `.env`

Crear el `.env` en la raíz a partir de la plantilla (no se sube a git):

```bash
cp env.example .env
```

Editarlo antes de levantar nada. En una instalación existente **no** sobrescribirlo:
conservar el usuario y la clave de Postgres con los que se creó el volumen.

| Variable | Valor |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Credenciales de Postgres (`flowable` en una instalación nueva). |
| `INSTITUCION_DB_NAME` | `institucion`. |
| `FLOWABLE_REST_USUARIO`, `FLOWABLE_REST_CLAVE` | Cuenta con acceso a la API REST de Flowable (por defecto `admin` / `test`). |
| `FLOWABLE_PROCESO_CLAVE` | `process`. |
| `CURSOS_DATOS_DEMO` | `true` para tener la empresa demo (la usan las pruebas de Postman). |

Formato: **sin espacios alrededor del `=`** y comentarios en líneas aparte. El
backend y `crear-usuarios.sh` lo leen como archivo de bash; una línea como
`EMAIL= admin@example.com` rompe la carga. `CURSOS_DATOS_DEMO=true` crea la empresa
de prueba `empresa-demo` / `1234`.

### 2. Levantar la infraestructura

Hay dos formas, según lo que se vaya a hacer:

**Todo en Docker** (para probar o para la demo; no hace falta Java ni Maven):

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps -a
```

**Desarrollo del backend** (el backend corre con Maven, paso 6, y se ven los cambios al
instante): levantar todo menos el servicio `backend`, que si no ocupa el puerto 8081.

```bash
docker compose up -d postgres institucion-db flowable pgadmin
```

Flowable tarda alrededor de un minuto en arrancar:
`docker logs -f flowable-persistente` hasta ver `Started FlowableUiApplication`.
Si Docker pide `sudo`, agregar el usuario al grupo `docker`.

### 3. Base `institucion`

La crea sola el servicio `institucion-db` si falta. Es normal que aparezca como
`Exited (0)`: corre una vez y termina. Las tablas las crea Hibernate al arrancar el backend.

Si el backend dice `database "institucion" does not exist` (por ejemplo, porque no se
levantó `institucion-db`), crearla a mano con el `POSTGRES_USER` y `POSTGRES_DB` del `.env`:

```bash
docker exec -it flowable-postgres psql -U flowable -d flowable -c "CREATE DATABASE institucion;"
```

### 4. Usuarios y grupos de Flowable

```bash
bash motor-flowable/idm/crear-usuarios.sh
```

Crea `Equipo-Administrativo`, `Equipo-Docente` y los usuarios `Jefe-Administrativo`,
`empleado-administrativo`, `Jefe-Docente`, `docente-1` y `docente-2` (clave `1234`).
Correrlo con `bash` (en fish o si el archivo no tiene permiso de ejecución,
`./crear-usuarios.sh` falla).

### 5. Importar y publicar el proceso

Ver [motor-flowable/modelos/README.md](motor-flowable/modelos/README.md). Resumen:
Flowable UI → Modeler → Apps → Import App → `Organizacion de cursos.zip` →
**verificar que la tabla `reglaViabilidad` use la variable `cantCursos`** → Publish.

### 6. Backend

Si se levantó todo con Docker, el backend ya está corriendo (logs: `docker compose logs -f backend`;
después de cambiar código: `docker compose up -d --build backend`). En desarrollo:

```bash
cd backend
mvn spring-boot:run
```

Tiene que aparecer `Tomcat started on port 8081`. Comprobar:

```bash
curl -i http://localhost:8081/api/v1/estado
curl -i http://localhost:8081/api/v1/diagnostico/flowable
```

Más detalle en [docs/BACKEND.md](docs/BACKEND.md).

### 7. Postman

Importar la colección y el entorno de [docs/postman/etapa 9/](docs/postman/etapa%209/)
y seleccionar el entorno **Cursos - Local**. Ver su README.

## Accesos

| Servicio | URL |
|---|---|
| Flowable UI (Modeler, Task, Admin) | http://localhost:8080/flowable-ui/ |
| API REST de Flowable | http://localhost:8080/flowable-ui/process-api |
| Backend | http://localhost:8081/api/v1/estado |
| pgAdmin | http://localhost:5050 |
| Web (Docker) | http://localhost:5173 |

pgAdmin: al registrar el servidor usar host `postgres` y puerto `5432` (pgAdmin corre
en Docker). Desde un cliente instalado en la PC: `localhost`, puerto `5433`.

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `password authentication failed for user "${POSTGRES_USER}"` | El backend no encontró el `.env`: tiene que estar en la raíz y `mvn` se corre desde `backend/`. |
| `database "institucion" does not exist` | No corrió `institucion-db`: ver el paso 3. |
| Postman: `ECONNREFUSED 127.0.0.1:8081` | El backend no está corriendo o no arrancó (consola de Maven o `docker compose logs backend`). |
| `Port 8081 was already in use` al correr Maven | Está corriendo el contenedor del backend: `docker compose stop backend`. |
| Postman: `ECONNREFUSED 127.0.0.1:8080` | Flowable no está levantado o sigue arrancando. |
| 503 `MOTOR_NO_DISPONIBLE` | Mirar la línea `Flowable respondió ...` en el log del backend: app sin publicar (`No process definition found`), DMN viejo (`otrasInstanciasActivas`) o clave de Flowable incorrecta (401). |
| Variables en rojo en Postman | No está seleccionado el entorno **Cursos - Local**. |

Para detener los contenedores conservando los datos: `docker compose stop`.

## Documentación

- [docs/BACKEND.md (avance y etapas)](docs/BACKEND.md#avance-y-etapas): avance y etapas pendientes.
- [docs/BACKEND.md (contrato de endpoints)](docs/BACKEND.md#contrato-de-endpoints-de-la-web): contrato de los endpoints que usa la web.
- [docs/MODELO_Y_INTEGRACION.md (acuerdo de integración)](docs/MODELO_Y_INTEGRACION.md#acuerdo-de-integración): identificadores, variables, estados y permisos.
- [docs/README.md (instalación con Docker)](docs/README.md#instalación-del-equipo-con-docker): instalación con Docker paso a paso.
- [docs/MODELO_Y_INTEGRACION.md](docs/MODELO_Y_INTEGRACION.md): acuerdo, modelo BPMN y app de materiales. Los 3 documentos de `docs/` son: `README.md` (guía del equipo), `BACKEND.md` y `MODELO_Y_INTEGRACION.md`.
