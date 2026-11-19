# Guía del equipo

Responsables: **Alejo, Lauty y Luka**; cada uno mantiene los acuerdos de su componente. Este directorio no contiene credenciales ni reemplaza los materiales de [contexto/](../contexto/).

## Índice

La documentación está unificada en tres documentos:

| Documento | Contenido |
|---|---|
| [README.md](README.md) (este) | Instalación con Docker, almacenamiento de PDFs, notas y tips de las clases grabadas. |
| [BACKEND.md](BACKEND.md) | Guía del backend, avance por etapas, contrato de endpoints de la web, etapa 6, integración del primer recorrido y **correcciones pendientes del BPMN**. **Empezar por «Avance y etapas».** |
| [MODELO_Y_INTEGRACION.md](MODELO_Y_INTEGRACION.md) | Acuerdo de integración (ids, variables, estados, permisos), referencias del modelo BPMN y guía de la app de materiales. |

### Colecciones de Postman

| Carpeta | Contenido |
|---|---|
| [postman/etapa 6/](postman/etapa%206/) | Tareas: bandejas, detalle, reclamo y completado. |
| [postman/etapa 7/](postman/etapa%207/) | Propuestas, versiones y PDF. |
| [postman/etapa 8/](postman/etapa%208/) | Portal de empresa y actividades. |
| [postman/etapa 9/](postman/etapa%209/) | **Colección general** (`Cursos GyTPN - Backend`): sesiones, solicitudes, atajos de Flowable y la etapa 9 paso a paso. Es la que se sigue ampliando. |
| [postman/etapa 10/](postman/etapa%2010/) | **Etapa 10** (`Etapa_10.postman_collection.json`): comunicaciones de la empresa (`/simulacion/**`) y pruebas integradas contra el modelo BPMN publicado. |

Las colecciones usan la cookie de sesión del login (no Basic Auth) contra el backend, y Basic Auth con el usuario técnico solo en los requests que van directo a Flowable.

---

## Instalación del equipo con Docker

### Código y configuración

Descargar la rama completa; no copiar archivos sueltos del backend:

```bash
git fetch origin
git switch backend-alejo
git pull --ff-only origin backend-alejo
```

En una instalación nueva, desde la raíz:

```bash
cp env.example .env
```

Editar `.env` antes de iniciar. No sobrescribir el de una instalación existente.

| Variable | Valor que debe configurar cada integrante |
| --- | --- |
| `POSTGRES_DB` | Base del motor; `flowable` en una instalación nueva. |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | Credenciales de PostgreSQL. Si ya existe el volumen, conservar las originales. |
| `INSTITUCION_DB_NAME` | `institucion`; Compose crea esta base si falta. |
| `FLOWABLE_REST_USUARIO`, `FLOWABLE_REST_CLAVE` | Cuenta con acceso a la API REST del Flowable local. La plantilla propone `admin` / `test`; verificar en cada instalación. |
| `FLOWABLE_PROCESO_CLAVE` | `process`, correspondiente al BPMN del repositorio. |
| `CURSOS_DATOS_DEMO` | `false`; usar `true` si se necesita la empresa demo. |

Para Docker no hace falta otro `.env` en `backend/` ni en `web/`: Compose pasa
las variables y configura las direcciones internas. La ejecución con Maven desde
`backend/` lee el mismo `.env` de la raíz y usa puertos locales por defecto.

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps -a
```

Esperar a que Flowable termine de arrancar. `institucion-db` con `Exited (0)` es normal.
No borrar volúmenes para actualizar: contienen los datos y los modelos publicados.

### Usuarios y modelos de Flowable

Con Flowable disponible y las credenciales técnicas configuradas:

```bash
bash motor-flowable/idm/crear-usuarios.sh
```

El script crea los usuarios y grupos faltantes; por defecto los usuarios nuevos
tienen clave `1234`. No cambia la clave de usuarios que ya existían.

### Extensiones (listeners) y despliegue del modelo Flowable

Con Flowable levantado, copiar el JAR de listeners al contenedor y reiniciarlo (ver [motor-flowable/README.md](../motor-flowable/README.md)):

```bash
docker cp motor-flowable/extensiones/flowable-extensiones.jar flowable-persistente:/app/WEB-INF/lib/flowable-extensiones.jar
docker restart flowable-persistente
```

Luego desplegar el archivo `.bar` actualizado con `desplegar.sh` (ver [motor-flowable/modelos/README.md](../motor-flowable/modelos/README.md); **no usar *Publish* en el Modeler**, porque rompe las asociaciones de compensación):

```bash
bash motor-flowable/modelos/desplegar.sh
```

La tabla `reglaViabilidad` incluida en el `.bar` lee `cantCursos` (number) y devuelve
`resultadoViabilidad` (boolean): `< 5` → `true`, `>= 5` → `false`. Las instancias existentes conservan
la definición de proceso con la que se iniciaron.

### Comprobación y archivos para compartir

```bash
curl -i http://localhost:8081/api/v1/estado
```

La web está en http://localhost:5173 y Flowable en http://localhost:8080/flowable-ui/.
*(Nota: `GET /api/v1/diagnostico/flowable` fue eliminado en la etapa 11 como parte de la limpieza de entrega).*
Importar en Postman `docs/postman/etapa 9/` o `docs/postman/etapa 10/`, ajustar usuarios y claves y seguir
el orden de sus respectivos README.

Compartir la rama y `env.example`, ambos disponibles en Git. No hace falta enviar
el `.env` personal: cada integrante lo crea con sus credenciales. Si se comparten
credenciales de un entorno común, hacerlo por un canal privado. No enviar cookies
de Postman. Los cambios hechos únicamente en Modeler no viajan con Git: exportar
los modelos actualizados y la app después de publicarlos para compartirlos.

La aprobación de pruebas Maven no sustituye las pruebas de Postman contra el
modelo publicado en la máquina de cada integrante.

---

## Almacenamiento de PDFs en Docker

Cambio I2 del 17/11/2026. El backend sigue ejecutándose como UID/GID `10001`.
La imagen crea `/app/almacenamiento/propuestas` con dueño `10001:10001` y permisos
`750`. Compose monta el volumen **`backend_archivos`** en `/app/almacenamiento`,
y configura `CURSOS_PROPUESTAS_DIR=/app/almacenamiento/propuestas`.

El nombre real del volumen lleva el prefijo del proyecto Compose. La base guarda
la ruta del archivo, no los bytes; la API conserva las rutas de descarga y valida
sesión y acceso al curso. No se exponen directorios como contenido público Nginx.
Los archivos conservan el formato `<cursoId>-v<version>.pdf`.

### Aplicar

Desde la raíz, conservando el `.env` existente:

```bash
docker compose config --quiet
docker compose up -d --build backend
```

En un volumen nuevo Docker copia los permisos del directorio preparado en la
imagen. Reiniciar, reconstruir o recrear el backend conserva los PDFs mientras
se use el mismo volumen/proyecto Compose. `docker compose down` también conserva
los volúmenes; **`down -v` los elimina**, incluidas las bases del proyecto.

La ejecución local con Maven mantiene `almacenamiento/propuestas` relativo al
directorio donde se ejecuta el backend, salvo que se defina `CURSOS_PROPUESTAS_DIR`.
No se cambiaron rutas persistidas anteriormente: Docker mantiene `/app/almacenamiento`.
Si había PDFs en el host por ejecución Maven, no se copian automáticamente al
volumen; antes de mezclar entornos hay que copiar esos archivos y conciliar sus
rutas en BD conservando un respaldo. En la comprobación inicial de este entorno
no existía el directorio de PDFs dentro del contenedor anterior.

### Comprobación mínima

```bash
docker compose exec backend sh -c 'test -w /app/almacenamiento/propuestas && echo PDF_DIRECTORIO_ESCRIBIBLE'
```

Luego, usando la colección Postman de etapa 7 o la API:

1. Login del jefe administrativo y creación de una solicitud ficticia.
2. Crear propuesta con `POST /api/v1/tareas/{taskId}/propuesta`: esperar `201`.
3. Descargar `GET /api/v1/cursos/{cursoId}/propuestas/1/pdf`: esperar `200`,
   `Content-Type: application/pdf` y comienzo `%PDF-`. Guardar una copia.
4. Recrear solamente el backend:

   ```bash
   docker compose up -d --no-deps --force-recreate backend
   ```

5. Esperar que responda `/api/v1/estado` y volver a iniciar sesión, porque las
   sesiones HTTP actuales viven en memoria. Descargar el mismo PDF y comparar
   el contenido o su SHA-256 con la copia anterior.

No hace falta agregar tests unitarios que comprueben líneas del Dockerfile: esta
verificación real comprueba permisos, generación, BD, proxy y persistencia.

### Operación

Para ubicar el volumen sin imprimir credenciales:

```bash
docker inspect "$(docker compose ps -q backend)" --format '{{range .Mounts}}{{println .Name "->" .Destination}}{{end}}'
```

Respaldar **base institucional y volumen de archivos juntos**. Restaurar solo la
BD puede dejar referencias a PDFs ausentes; restaurar solo archivos no recupera
las propuestas. Cambiar el nombre del proyecto Compose utiliza otro volumen.
No se incorporó una migración automática desde directorios locales ni se cambia
la propiedad de volúmenes previos con permisos diferentes de forma silenciosa.

Si un volumen existente tiene permisos incorrectos, verificar sus propietarios
y ajustar solamente `/app/almacenamiento` a `10001:10001` con acceso `750` para
los directorios, conservando los archivos. No usar `chmod 777` ni ejecutar el
backend permanentemente como root.

### Resultado verificado

Prueba real del 17/11/2026, usando la API a través del proxy web:

- Usuario del backend `10001:10001`, directorio escribible y volumen montado.
- Alta ficticia y propuesta: `201`; descarga: `200 application/pdf`, cabecera `%PDF-`.
- PDF de **1519 bytes**, SHA-256
  `5558fd3a207b9d50ff3184391e45b29db916bf17ac49a2549563a50dc6284cca`.
- Backend recreado con `--force-recreate`, login nuevo y segunda descarga `200`,
  con exactamente el mismo SHA-256 y tamaño.
- La reconstrucción ejecutó los **20 tests existentes del backend: 0 fallas y 0
  errores, BUILD SUCCESS**. No se agregó una suite nueva para esta corrección.
- Instancia, historial, solicitud, propuesta, fechas, empresa y archivo ficticios
  de I2 eliminados después de verificar. Las instancias y datos preexistentes se
  conservaron. Backend/web quedaron funcionando.

La prueba cubre el almacenamiento de PDFs; no implica que ya estén alineados los
formularios web o implementadas las etapas 9–10.

---

## Notas y tips de las clases grabadas

Este documento recopila las aclaraciones técnicas, atajos prácticos, advertencias de errores comunes y criterios de evaluación que el docente mencionó en los audios de clase respecto al proyecto en **Flowable**, y que no figuran textualmente en la consigna (`ProyectoCursos_V3_168631696404.pdf`).

---

### 1. Despliegue de Código Java en Docker (Flowable)
En el PDF se pide implementar *Listeners* y tareas automáticas, pero en clase se explicó el procedimiento exacto para que el contenedor de Flowable reconozca nuestras clases compiladas en ejecución:

1. **Empaquetado:** Compilar las clases Java (Task Listeners, Execution Listeners, JavaDelegates) en un archivo `.jar` (ej. `misclases.jar`).
2. **Copia al contenedor:** Copiar el `.jar` dentro de la carpeta `WEB-INF/lib/` de la aplicación desplegada en Tomcat dentro del contenedor Docker:
   ```bash
   docker cp /ruta/local/misclases.jar flowable-persistente:/app/WEB-INF/lib/
   ```
3. **Reinicio obligatorio:** Tomcat no carga nuevos `.jar` en caliente en esa ruta, por lo que es indispensable reiniciar el contenedor:
   ```bash
   docker restart flowable-persistente
   ```
4. **Coincidencia de nombres:** Los nombres de paquetes y clases configurados en el modelador BPMN deben coincidir **exactamente** con la estructura dentro del `.jar` (si no coincide, lanzará `ClassNotFoundException` en tiempo de ejecución).
5. **Logs en tiempo real:** Para depurar qué hace el motor o si falló algún *listener* al ejecutar desde la UI/REST, usar:
   ```bash
   docker logs -f --tail 100 flowable-persistente
   ```
6. **Si se usa Drools (`.drl`):** Si en lugar de DMN se opta por reglas en Drools, todos los `.jar` de dependencias de Drools también deben copiarse dentro de `/app/WEB-INF/lib/` y reiniciar el contenedor.

---

### 2. Simplificaciones y Atajos Permitidos para el Proyecto
Para evitar que los grupos se traben con configuraciones externas irrelevantes para la materia, el profesor autorizó las siguientes simplificaciones:

* **Envío de correos electrónicos (Mail Tasks):** No es obligatorio configurar un servidor SMTP real (como Gmail). Si el modelador o el motor exige una implementación para las tareas de notificación/envío de correo a la empresa, se pueden reemplazar por una *Service Task* (JavaDelegate) o *Script Task* que imprima un mensaje en consola (`System.out.println("Simulando envío de mail...")`).
* **Temporizadores (Timers) para la defensa:** Mantener los eventos temporizadores en el BPMN con la lógica pedida (recordatorios cada 5 días, espera hasta fecha de curso), pero **configurarlos con duraciones cortas (ej. 1 o 5 segundos / ISO 8601 `PT5S`)** durante las pruebas y la demostración para poder mostrar los escenarios de vencimiento en vivo sin esperar días.
* **Aplicación Móvil:** Se descartó el desarrollo de una app móvil nativa; las acciones correspondientes a docentes o participantes se integran en la aplicación web o local según corresponda.
* **Interfaz de usuario (Frontend):** El docente recalcó repetidamente **no perder tiempo en diseño estético** ("que sea linda, CSS complejo, etc."). Se evalúa la funcionalidad, la correcta invocación a Flowable y el cumplimiento de los escenarios, no el diseño gráfico.
* **Uso de Postman para disparar eventos:** Durante el desarrollo, antes de tener terminada la interfaz externa o el simulador, se recomienda usar Postman (con *Basic Auth*) para disparar mensajes/señales REST y hacer avanzar instancias detenidas.

---

### 3. Detalles de Implementación y Modelado BPMN
* **Formularios en Flowable UI:** Si se prueban formularios desde la interfaz web propia de Flowable (`flowable-ui`), **no usar formularios embebidos (*form properties*)** porque la UI de esta versión no los renderiza; deben crearse como formularios independientes y vincularse por **referencia** (*Form reference*). Además, cada `id` de campo del formulario se convierte automáticamente en una variable de proceso.
* **Orden en la sincronización por Señales (*Signal*):** Si se utilizan eventos de señal para sincronizar ramas paralelas (en lugar de una compuerta paralela `AND` de cierre), el flujo que espera la señal debe llegar al evento de captura **antes** de que la otra rama dispare la señal; de lo contrario, el evento se pierde porque no queda en estado de escucha.
* **Compensaciones:**
  * Solo se ejecutan si la actividad compensable ya fue completada previamente.
  * Si se usa un lanzador de compensación sin especificar actividad (*broadcast*), compensa todas las actividades completadas dentro del ámbito; si se necesita compensar en un orden estricto, deben usarse lanzadores específicos apuntando a cada actividad en secuencia.
  * Ante una cancelación durante la preparación del curso, asegurarse de que el flujo interrumpido **no continúe hacia la confirmación/ejecución del curso**, sino que derive exclusivamente a la compensación y cierre como cancelado.
* **Errores conceptuales comunes a evitar en el BPMN:**
  * Nunca modelar "Esperar fecha del curso" o "Esperar respuesta" como una **User Task** (tarea humana); las esperas pasivas se modelan con **Eventos Intermedios** (Timer o Message Catch).
  * No crear tareas genéricas llamadas "Gestionar inscripciones" cuando ese nombre engloba todo un subproceso compuesto por actividades concretas (recibir nómina, confeccionar lista).
  * No usar envío/recepción de **Mensajes** entre roles internos del mismo *Pool* (participante); los mensajes son únicamente para comunicación entre participantes externos (ej. Empresa $\leftrightarrow$ Institución). Internamente se usan flujos de secuencia, variables o señales.
  * No dejar eventos intermedios de envío sin flujo de secuencia saliente (el token quedaría bloqueado).

---

### 4. Base de Datos e Integración API Java vs. API REST
* **Persistencia crítica:** El motor Flowable es sin estado (*stateless*); todo el estado de las instancias, variables e historial reside en PostgreSQL.
* **Conexión directa en la App de Materiales:** La aplicación local que utiliza la API Java (`ProcessEngine`, `TaskService`, `IdentityService`) **no debe usar la base en memoria H2 por defecto**, sino apuntar exactamente a la misma instancia de PostgreSQL (`flowable-postgres`) que usa el contenedor Docker, para compartir las instancias en curso.
* **Permisos REST:** Todo usuario creado en Flowable IDM que deba interactuar mediante peticiones HTTP (como el usuario técnico del backend o los usuarios administrativos) debe tener asignado explícitamente el privilegio **`access-rest-api`**.
* **Variables desde el inicio:** Al iniciar una instancia desde el formulario web mediante la API REST (`/runtime/process-instances`), enviar en el cuerpo JSON el arreglo `variables` con los datos iniciales de la solicitud para que viajen durante todo el ciclo de vida del proceso.
* **Historial y trazas:** Para las consultas históricas y la extracción de logs hacia PM4Py, tener en cuenta que la API de historial (`HistoryService` / `/history/historic-activity-instances`) devuelve todos los elementos ejecutados (incluyendo compuertas y flujos de secuencia); se debe filtrar por tipo de actividad (`activityType == "userTask"`, `"serviceTask"`, etc.) y asegurarse de que **todos los elementos del BPMN tengan `ID` y `Name` descriptivos**.

---

### 5. Uso de Inteligencia Artificial y Criterios de Evaluación (Defensa)
* **Uso permitido pero supervisado:** El docente alienta el uso de IA (Cursor, ChatGPT, Claude) para acelerar el desarrollo de SQL, frontend y llamadas REST, pero advirtió sobre dos fallos frecuentes:
  1. **Eliminación silenciosa de código:** Al pedir modificaciones sobre archivos grandes, los asistentes suelen borrar funciones previas que ya andaban.
  2. **Complacencia (*Sycophancy*):** La IA suele dar la razón ante premisas incorrectas del usuario sobre cómo funciona Flowable.
* **Criterio de aprobación y nota:**
  * No es excluyente tener el 100% de las funcionalidades secundarias terminadas para aprobar; la nota es proporcional al alcance funcional logrado y a la correcta ejecución de los escenarios.
  * **Defensa oral:** Dado que se asume el uso de IA para generar código, la evaluación se centrará en **preguntas técnicas sobre el código y el modelo BPMN** durante la demostración para comprobar que el grupo comprende cómo funciona la solución entregada.
