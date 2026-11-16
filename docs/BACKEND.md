# Backend

El módulo `backend/` usa Java 21, Maven y Spring Boot 3.5.16. Actualmente expone un endpoint de estado; la gestión de empresas y cursos y la integración con Flowable y PostgreSQL están pendientes.

## Estructura

- `src/main/java/ar/edu/cursos/CursosApplication.java`: inicio de Spring Boot.
- `src/main/java/ar/edu/cursos/EstadoController.java`: endpoint de estado.
- `src/main/resources/application.properties`: nombre de la aplicación, dirección y puerto.
- `src/main/java/unrc/Main.java`: prueba independiente de Flowable 6.8.0 con H2 en memoria. No se ejecuta al iniciar Spring Boot ni se conecta al motor de Docker.
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
curl -i http://localhost:8081/api/estado
```

`GET /api/estado` devuelve HTTP 200 y este JSON (el orden de las propiedades puede variar):

```json
{
  "aplicacion": "cursos-backend",
  "estado": "funcionando"
}
```

La respuesta confirma que el backend está funcionando; no verifica Flowable ni la base de datos.

También se puede iniciar solo el backend con Docker desde la raíz, sin instalar Java o Maven:

```bash
docker compose up -d --build backend
docker compose logs -f backend
```

Compose configura `SERVER_ADDRESS=0.0.0.0` dentro del contenedor y publica el puerto en `127.0.0.1:8081` del equipo. Después de cambiar el código, repetir el comando con `--build` para aplicar los cambios.

Para los contratos previstos entre componentes, consultar el [acuerdo de integración](ACUERDO_DE_INTEGRACION.md).
