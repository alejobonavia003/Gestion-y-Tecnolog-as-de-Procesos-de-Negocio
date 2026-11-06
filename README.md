# Gestión y Tecnologías de Procesos de Negocio

Proyecto académico para implementar la gestión de cursos de capacitación mediante Flowable.

## Estado actual

- Backend Spring Boot con endpoint de estado.
- Entorno Docker para Flowable, PostgreSQL y pgAdmin.
- Consigna y modelos BPMN en `contexto/`.

La conexión del backend con Flowable y las funciones del proceso todavía están pendientes. Arrancar el backend no despliega los modelos BPMN.

## Requisitos

- JDK 21.
- Maven.
- Docker con Docker Compose, para ejecutar el entorno Flowable.
- Git.

## Estructura

- `backend/`: aplicación Spring Boot.
- `contexto/`: consigna y modelos BPMN.
- `docker-compose.yml`: configuración del entorno Docker.
- `README.md`: instrucciones de ejecución.

## Probar el backend

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

## Ejecutar el entorno Flowable

Desde la raíz del repositorio, revisar las variables requeridas por
`docker-compose.yml`.

Si utiliza un archivo local `env`, crear uno tomando `env.example`
como referencia y completar sus valores. El archivo real no se incluye
en Git porque puede contener contraseñas.

Validar y levantar el entorno:

```bash
docker compose --env-file env config --quiet
docker compose --env-file env up -d
docker compose --env-file env ps
```

Las variables y sus nombres deben coincidir con los definidos en
`docker-compose.yml`.

Con los puertos actuales, los accesos son:

- Flowable: http://localhost:8080/flowable-ui/
- pgAdmin: http://localhost:5050

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
