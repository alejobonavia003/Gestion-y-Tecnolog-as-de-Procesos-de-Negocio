# Gestión y Tecnologías de Procesos de Negocio

Proyecto académico (GyTPN, UNRC): proceso de **organización de cursos de capacitación**
para empresas, ejecutado en **Flowable 6.8** con un backend propio, una web y aplicaciones
auxiliares.

## Estado actual (rama `backend-lauty`, 17/11/2026)

| Parte | Estado |
|---|---|
| Infraestructura Docker (Flowable UI, PostgreSQL, pgAdmin, web) | ✅ Funcionando |
| Usuarios y grupos de Flowable (`motor-flowable/idm/crear-usuarios.sh`) | ✅ |
| Modelo del proceso + regla DMN de viabilidad | ✅ Se publica (con tareas automáticas marcadoras) |
| Backend etapas 1 a 5 (base, datos, cliente Flowable, sesiones, solicitudes) | ✅ Implementadas y probadas |
| Backend etapas 6 a 8 (tareas, propuestas y PDF, portal de empresa) | 🔧 Implementadas; falta validarlas desde la web |
| Backend etapa 9 (docente, dictado, informe) | ✅ Completa: #17, #18 y #19 probados con Postman contra Flowable real |
| Backend etapas 10 y 11 (simulador, integración final) | ⏳ Pendientes |
| Web (React + Vite) | ✅ Completa contra mocks; falta conectarla al backend real |
| App de materiales, simulador REST, listeners del motor | ⏳ Pendientes |

El detalle de qué está hecho y qué falta está en
[docs/BACKEND_PROGRESO_Y_ETAPAS.md](docs/BACKEND_PROGRESO_Y_ETAPAS.md).

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
Todos los comandos se corren desde la raíz del repo.

### 1. Archivo `.env`

Crear `.env` en la raíz (no se sube a git). Variables mínimas:

```
POSTGRES_DB=flowable
POSTGRES_USER=flowable
POSTGRES_PASSWORD=<clave-local>
FLOWABLE_REST_USUARIO=admin
FLOWABLE_REST_CLAVE=test
FLOWABLE_PROCESO_CLAVE=process
CURSOS_DATOS_DEMO=true
```

Formato: **sin espacios alrededor del `=`** y comentarios en líneas aparte. El
backend y `crear-usuarios.sh` lo leen como archivo de bash; una línea como
`EMAIL= admin@example.com` rompe la carga. `CURSOS_DATOS_DEMO=true` crea la empresa
de prueba `empresa-demo` / `1234`.

### 2. Levantar la infraestructura

```bash
docker compose up -d postgres flowable pgadmin
```

No levantar todo con `docker compose up -d`: en esta rama el servicio `backend` del
compose todavía no tiene la configuración para conectarse a Postgres y Flowable
(llega con la integración Docker de `main`) y ocuparía el puerto 8081. En desarrollo
el backend se corre con Maven (paso 6).

Flowable tarda alrededor de un minuto en arrancar:
`docker logs -f flowable-persistente` hasta ver `Started FlowableUiApplication`.

### 3. Crear la base `institucion`

```bash
docker exec -it flowable-postgres psql -U flowable -d flowable -c "CREATE DATABASE institucion;"
```

(Usar el `POSTGRES_USER` y `POSTGRES_DB` del `.env`.) Las tablas las crea Hibernate
al arrancar el backend. Si Docker pide `sudo`, agregar el usuario al grupo `docker`.

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
| `database "institucion" does not exist` | Falta el paso 3. |
| Postman: `ECONNREFUSED 127.0.0.1:8081` | El backend no está corriendo o no arrancó (ver la consola de Maven). |
| Postman: `ECONNREFUSED 127.0.0.1:8080` | Flowable no está levantado o sigue arrancando. |
| 503 `MOTOR_NO_DISPONIBLE` | Mirar la línea `Flowable respondió ...` en el log del backend: app sin publicar (`No process definition found`), DMN viejo (`otrasInstanciasActivas`) o clave de Flowable incorrecta (401). |
| Variables en rojo en Postman | No está seleccionado el entorno **Cursos - Local**. |

Para detener los contenedores conservando los datos: `docker compose stop`.

## Documentación

- [docs/BACKEND_PROGRESO_Y_ETAPAS.md](docs/BACKEND_PROGRESO_Y_ETAPAS.md): avance y etapas pendientes.
- [docs/ENDPOINTS_WEB.md](docs/ENDPOINTS_WEB.md): contrato de los endpoints que usa la web.
- [docs/ACUERDO_DE_INTEGRACION.md](docs/ACUERDO_DE_INTEGRACION.md): identificadores, variables, estados y permisos.
- [docs/README.md](docs/README.md): índice completo.
