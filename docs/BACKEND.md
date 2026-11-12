# Backend

El módulo `backend/` usa Java 21, Maven y Spring Boot 3.5.16. Incluye sesiones, solicitudes y la etapa 6 de tareas mediante REST de Flowable y PostgreSQL.

## Estructura

- `src/main/java/ar/edu/cursos/CursosApplication.java`: inicio de Spring Boot.
- `src/main/java/ar/edu/cursos/EstadoController.java`: endpoint de estado.
- `src/main/resources/application.properties`: nombre de la aplicación, dirección y puerto.
- `controllers/`, `services/`, `dto/`: API, lógica y contratos de sesiones, solicitudes y tareas.
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

La ejecución requiere la configuración de `.env` y PostgreSQL institucional. Dentro
de Docker se deben configurar las URLs usando los nombres de servicio (`postgres`,
`flowable`) y pasar las variables al contenedor; la publicación de Compose está
prevista para la etapa 11.

También se puede iniciar solo el backend con Docker desde la raíz, sin instalar Java o Maven:

```bash
docker compose up -d --build backend
docker compose logs -f backend
```

Compose configura `SERVER_ADDRESS=0.0.0.0` dentro del contenedor y publica el puerto en `127.0.0.1:8081` del equipo. Después de cambiar el código, repetir el comando con `--build` para aplicar los cambios.

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
nómina para lista, motivo para archivo y datos de curso/informe para cierre.
La lista de docentes y disponibilidad real del PDF quedan para las etapas 9 y 7.

Verificación automatizada: `mvn -f backend/pom.xml verify`.
Pruebas de permisos, doble reclamo, validaciones, serialización y cliente REST
con servidor HTTP de prueba; no requieren PostgreSQL ni Flowable reales.

Para validar contra el motor, levantar Postgres y Flowable, publicar el BPMN
actualizado e iniciar el backend desde `backend/` con su `.env` configurado.
Iniciar sesión con `POST /api/v1/sesiones/personal`, conservar la cookie y probar
las dos bandejas, detalle, reclamo y completado. La etapa 5 permite crear una
solicitud para obtener la tarea de `Jefe-Administrativo`.

**Pendiente de integración:** los IDs de la web aún corresponden al modelo viejo.
Los exports `.zip` y `.bar` aún no incorporan las correcciones del XML de etapa 6;
ver `motor-flowable/modelos/README.md` antes de desplegar. No se ha validado desde
la web ni contra un motor real en esta ejecución.

Contrato remoto consultado: [API REST oficial de Flowable](https://www.flowable.com/open-source/docs/bpmn/ch14-REST).
