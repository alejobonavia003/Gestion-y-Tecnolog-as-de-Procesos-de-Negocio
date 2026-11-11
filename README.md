# Gestión y Tecnologías de Procesos de Negocio

Proyecto académico para implementar la gestión de cursos de capacitación mediante Flowable.

## Estado actual

- Backend Spring Boot con endpoint de estado.
- Entorno Docker para la web, Flowable, PostgreSQL y pgAdmin.
- Consigna y modelos BPMN en `contexto/`.

La conexión del backend con Flowable y las funciones del proceso todavía están pendientes. Arrancar el backend no despliega los modelos BPMN.

## Requisitos

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
docker compose up -d
curl -i http://localhost:8081/api/estado
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

El backend participa de la red `flowable-net` y se reinicia automáticamente
con la misma política que Flowable. Actualmente funciona de forma independiente:
todavía no consume Flowable ni PostgreSQL, por lo que no necesita esperar su arranque.

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
curl -i http://localhost:8081/api/estado
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

http://localhost:8081/api/estado

Esta prueba no necesita que Flowable o PostgreSQL estén ejecutándose.

Para detener el backend, presionar Ctrl+C en la terminal donde se inició.

## Ejecutar el entorno completo con Docker

Desde la raíz del repositorio, revisar las variables requeridas por
`docker-compose.yml`.

Los comandos siguientes utilizan el archivo local `env`. El ejemplo
`env.example` todavía no está disponible. El inventario de Git muestra
`env` y `.env` rastreados, aunque estén enumerados en `.gitignore`;
no se revisa ni publica su contenido en esta tarea. Consultar la sección J
del [acuerdo de integración](docs/ACUERDO_DE_INTEGRACION.md) antes de
acordar ejemplos de configuración o incorporar credenciales nuevas.

Validar y levantar el entorno:

```bash
docker compose --env-file env config --quiet
docker compose --env-file env up -d --build
docker compose --env-file env ps
```

Las variables y sus nombres deben coincidir con los definidos en
`docker-compose.yml`.

Con los puertos actuales, los accesos son:

- Flowable: http://localhost:8080/flowable-ui/
- Backend: http://localhost:8081/api/estado
- pgAdmin: http://localhost:5050
- Web: http://localhost:5173

También se puede iniciar todo con `docker compose up -d --build`, usando el
archivo `.env` que Compose carga automáticamente. La web se compila dentro de
Docker y se sirve con Nginx. Las peticiones `/api/` se reenvían al backend del
equipo en el puerto 8081; el backend se inicia por separado con Maven.
Ver [web/README.md](web/README.md) para configurar otro destino de la API.

Las credenciales corresponden a la configuración del entorno.

Para detener los contenedores conservando los volúmenes:

```bash
docker compose --env-file env stop
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
