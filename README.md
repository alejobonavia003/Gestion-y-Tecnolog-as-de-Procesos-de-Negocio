# Gestión y Tecnologías de Procesos de Negocio

Proyecto académico para implementar la gestión de cursos de capacitación mediante Flowable.

## Estado actual

- Backend Spring Boot con endpoint de estado.
- Entorno Docker para la web, Flowable, PostgreSQL y pgAdmin.
- Consigna y modelos BPMN en `contexto/`.

El backend se conecta por REST a Flowable y usa una base institucional separada en PostgreSQL. Arrancar el backend no despliega los modelos BPMN.

## Requisitos

Para instalar esta versión en otra máquina, seguir [Instalación para el equipo](docs/INSTALACION_EQUIPO.md).

- Docker con Docker Compose, para ejecutar todo el entorno.
- JDK 21 y Maven, solo para ejecutar el backend fuera de Docker.
- Git.

## Estructura y responsabilidades

| Ruta real | Objetivo | Responsable |
|---|---|---|
| [motor-flowable/](motor-flowable/) | BPMN ejecutable, DMN, identidades, listeners, mensajes, temporizadores y compensación. | Alejo |
| [backend/](backend/) | Backend y BD institucional, empresas, cursos, vínculo con instancias, acceso empresarial y PDFs. Equivale a `backend-institucion/`; conserva su nombre. | Lauty |
| [web/](web/) | Sitio público y funciones de empresas/personal. | Luka |
| [app-materiales/](app-materiales/) | Aplicación gráfica intranet mediante API Java de Flowable. | Luka |
| [simulador-rest/](simulador-rest/) | CLI de comunicaciones REST; permitida para el equipo de tres. | Luka |
| [docs/](docs/) | Acuerdos y documentación compartida. | Los tres |
| [contexto/](contexto/) | Consigna y modelos de referencia existentes. | Referencia común |
| [docker-compose.yml](docker-compose.yml) | Infraestructura existente; cambios coordinados. | Los tres |

El [acuerdo de integración](docs/ACUERDO_DE_INTEGRACION.md) define contratos iniciales, identificadores y decisiones pendientes. Las carpetas nuevas contienen documentación preparatoria; no implican funcionalidades implementadas ni motores adicionales.

## Probar el backend

Desde la raíz, iniciar todos los servicios (incluido el backend):

```bash
cp env.example .env # Solo en la primera instalación; ajustar las credenciales
docker compose up -d --build
curl -i http://localhost:8081/api/v1/estado
```

Compose construye la imagen del backend en el primer inicio; no hace falta
tener Java ni Maven instalados en la máquina. **No tiene hot reload:** los
cambios en el código no se aplican automáticamente al contenedor. Para
actualizar el backend después de modificarlo, ejecutar desde la raíz:

```bash
docker compose up -d --build backend
```

Este comando reconstruye la imagen y recrea el contenedor con los cambios.
Para ver los logs del backend:

```bash
docker compose logs -f backend
```

El backend usa `postgres:5432` y `flowable:8080` dentro de la red de Docker.
Compose espera a que PostgreSQL esté disponible y crea la base `institucion`
si falta, conservando los datos existentes. La web reenvía `/api/` al servicio
`backend:8081`. Flowable puede tardar en iniciar; comprobar la integración con:

```bash
curl -i http://localhost:8081/api/v1/diagnostico/flowable
```

### Ejecución local sin Docker

Clonar el repositorio usando su URL de GitHub y entrar en la carpeta descargada.

Desde la raíz:

```bash
cd backend
mvn clean verify
mvn spring-boot:run
```

Dejar esa terminal abierta. El backend escucha en:

http://localhost:8081

Desde otra terminal:

```bash
curl -i http://localhost:8081/api/v1/estado
```

Resultado esperado: HTTP 200 y un JSON equivalente a:

```json
{
  "aplicacion": "cursos-backend",
  "estado": "funcionando"
}
```

El orden de las propiedades puede variar.

También se puede abrir en el navegador:

http://localhost:8081/api/v1/estado

El backend necesita PostgreSQL y la configuración de `.env` también al ejecutarse
fuera de Docker. Para probar la integración completa, Flowable debe estar disponible.

Para detener el backend, presionar Ctrl+C en la terminal donde se inició.

## Ejecutar el entorno completo con Docker

Desde la raíz, copiar `env.example` a `.env` y ajustar sus valores.
En una instalación existente, conservar el usuario y la contraseña con los que
se inicializó el volumen PostgreSQL: cambiar `.env` no cambia esas credenciales.
`FLOWABLE_REST_USUARIO` y `FLOWABLE_REST_CLAVE` deben corresponder a una cuenta
con acceso a la API REST de Flowable.

```bash
cp env.example .env # Solo si todavía no existe .env
# Editar .env antes de continuar
docker compose config --quiet
docker compose up -d --build
docker compose ps -a
```

El servicio `institucion-db` termina con código 0 después de comprobar o crear
la base institucional. Es normal que aparezca como `Exited (0)`.

Con los puertos actuales, los accesos son:

- Flowable: http://localhost:8080/flowable-ui/
- Backend: http://localhost:8081/api/v1/estado
- pgAdmin: http://localhost:5050
- Web: http://localhost:5173

La web se compila dentro de Docker y se sirve con Nginx. No hace falta iniciar
Maven fuera de Docker. Los cambios de código requieren reconstruir la imagen.
Ver [web/README.md](web/README.md) para configurar otro destino de la API.

Las credenciales corresponden a la configuración del entorno.

Para detener los contenedores conservando los volúmenes:

```bash
docker compose stop
```

## Clases de inicio

- `ar.edu.cursos.CursosApplication`: inicia el backend Spring Boot.
- `unrc.Main`: prueba independiente que crea un motor Flowable con H2
  en memoria, cuenta las definiciones desplegadas y cierra el motor.

La prueba `unrc.Main` no se conecta al Flowable de Docker y sus datos
no se conservan al terminar el programa.

## Próximos pasos

- Conectar el backend con la API REST de Flowable.
- Configurar usuarios, grupos y permisos.
- Completar y desplegar el BPMN ejecutable.
- Implementar la regla de viabilidad y los listeners.
- Crear la base institucional de empresas y cursos.
- Desarrollar las interfaces requeridas por la consigna.
