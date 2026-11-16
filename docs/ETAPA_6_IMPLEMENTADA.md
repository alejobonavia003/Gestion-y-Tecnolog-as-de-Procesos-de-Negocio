# Etapa 6 — Implementación de tareas

Fecha: 12/11/2026. Rama: `backend-alejo`.
Commit de implementación: `627047c`.

## Base integrada

La rama inicialmente tenía el backend básico. Se integraron `backend` y luego
`origin/backend`, incluyendo las etapas 1 a 5 que necesita esta implementación:
modelo institucional, repositorios, errores, cliente Flowable, sesiones y solicitudes.

Se resolvieron los conflictos de `.gitignore` y `docker-compose.yml`. Se conservaron
los servicios web/backend de la rama de trabajo y las reglas de archivos generados
y configuración local. No se hizo push ni se desplegó el proceso.

## Endpoints implementados

Todos usan la base `/api/v1/tareas` y requieren una sesión de personal.
El usuario y sus grupos se obtienen de la sesión HTTP.

| Método y ruta | Comportamiento |
|---|---|
| `GET /api/v1/tareas?bandeja=asignadas` | Lista tareas asignadas al usuario de sesión. |
| `GET /api/v1/tareas?bandeja=disponibles` | Lista tareas sin asignar de sus grupos candidatos. |
| `GET /api/v1/tareas/{taskId}` | Devuelve resumen y contexto institucional de la tarea. |
| `POST /api/v1/tareas/{taskId}/reclamo` | Reclama para el usuario de sesión y devuelve el resumen actualizado. |
| `POST /api/v1/tareas/{taskId}/completado` | Valida y completa las tareas simples. |

La bandeja admite `page` (predeterminado 0) y `size` (predeterminado 20,
entre 1 y 100). Filtra por la clave del proceso configurado en el backend.
Devuelve `items`, `total`, `page` y `size`.

Cada resumen incluye `taskId`, `taskDefinitionKey`, `nombre`, `cursoId`,
`cursoNombre`, `empresaNombre`, `creada`, `asignado` y `reclamable`.
El detalle agrega `contexto` al mismo nivel.

## Autorización y errores

- Sin sesión: `401 NO_AUTENTICADO`.
- Sesión de empresa: `403 SIN_PERMISO`.
- Para consultar: ser el asignado o candidato por grupo, verificado con identity links.
- Para reclamar: tener acceso y que no haya asignado. Un segundo reclamo,
  incluso del mismo usuario, devuelve `409 YA_RECLAMADA`.
- Un conflicto de Flowable durante claim se traduce también a `YA_RECLAMADA`.
- Para completar: ser el asignado; de lo contrario, `403 SIN_PERMISO`.
- La tarea debe estar vinculada por `processInstanceId` a un curso institucional.
- Tarea inexistente o curso no vinculado: `404 NO_ENCONTRADO`.
- Datos inválidos: `400 VALIDACION`.
- Tarea no admitida en completado simple: `409 ESTADO_INCOMPATIBLE`.
- Se conserva la traducción de fallas del motor a `503 MOTOR_NO_DISPONIBLE`.

## Datos de completado

El cuerpo tiene la forma `{"datos": {...}}`. Se rechazan campos extra.

| ID actual de tarea | Datos admitidos |
|---|---|
| `confeccionar_lista` | `participantes`: lista no vacía de objetos con `nombre` textual no vacío. |
| `evaluar_recursos` | `requiereAula`, `requierePlataforma`, `requiereEquipamiento`: los tres booleanos; al menos uno verdadero. |
| `archivar_solicitud` | Objeto vacío. |
| `enviar_informe` | Objeto vacío. |
| `archivar_expediente` | Objeto vacío. |

Ejemplo de lista:

```json
{"datos":{"participantes":[{"nombre":"Ana Pérez"}]}}
```

Ejemplo de recursos:

```json
{"datos":{"requiereAula":true,"requierePlataforma":false,"requiereEquipamiento":true}}
```

Las variables se envían a Flowable después de validar. Los participantes se
conservan como variable REST de tipo `json`; los recursos son booleanos.
La respuesta exitosa es `{"completada":true}`.
Propuesta, revisión, docente, dictado y tareas externas no pueden completarse
mediante esta operación genérica.

## Contexto del detalle

Los datos institucionales se consultan con JPA y las variables mediante REST.
No se accede directamente a tablas internas `ACT_*` del motor.

| Tarea | Contexto incorporado |
|---|---|
| `registrar_elaborar_propuesta` | Solicitud: temática, descripción, cantidad estimada y fechas preferidas. |
| `revisar_propuesta` | Solicitud, propuesta vigente si existe y observaciones de la empresa. |
| `confeccionar_lista` | Participantes de la variable del proceso, o lista vacía si no existe. |
| `archivar_solicitud` | Motivo `RECHAZADA` si la respuesta es rechazo; en otro caso `SIN_RESPUESTA`. |
| `evaluar_recursos` | Sin datos adicionales. |
| `definir_docente`, `desarrollar_curso` | Descripción del curso, cantidad de participantes y fecha. |
| `enviar_informe`, `archivar_expediente` | Datos del curso e informe con resumen y `pdfDisponible=false`. |

La propuesta vigente incluye versión, programa, presupuesto, fechas posibles,
estado, observaciones y fecha de creación. La generación/descarga de PDFs y la
lista de docentes corresponden a etapas posteriores.

## Archivos de código

Rutas relativas a `backend/src/main/java/ar/edu/cursos/`:

| Archivo | Responsabilidad |
|---|---|
| `controllers/TareaController.java` | Rutas HTTP y exigencia de sesión de personal. |
| `services/TareaService.java` | Bandejas, permisos, contexto, reclamo y validación del completado. |
| `services/TareasProceso.java` | Constantes de IDs de tareas usados en esta etapa. |
| `dto/TareaResumen.java` | Contrato del resumen de tarea. |
| `dto/TareaDetalle.java` | Resumen sin anidar y mapa de contexto. |
| `clients/FlowableClient.java` | Nuevas operaciones de tareas y soporte de variables JSON. |

Se agregaron al cliente `tareas`, `tarea`, `identityLinks`, `variables`,
`reclamar` y `completar`. Las lecturas institucionales del servicio usan una
transacción de solo lectura para resolver las relaciones de las entidades.

## Correcciones del BPMN

En `motor-flowable/modelos/organizacion-cursos.bpmn20.xml`:

1. Se eliminó la multiinstancia de `confeccionar_lista`: ahora es una única tarea.
2. `archivar_expediente` quedó asignada a `Jefe-Administrativo`.
3. Se agregaron las condiciones del gateway inclusivo de recursos:
   `requiereAula` para aula física, `requierePlataforma` para aula virtual
   y `requiereEquipamiento` para equipamiento.

Se comprobó que el XML sea válido y que los dos primeros cambios estén presentes.
No se publicó el modelo ni se migraron instancias. Los archivos `.zip` y `.bar`
exportados previamente siguen sin estos cambios: hay que importar el XML actualizado
en Modeler, incorporarlo a la app, publicar y regenerar los exports.

## Pruebas realizadas

Comando ejecutado satisfactoriamente:

```bash
mvn -f backend/pom.xml verify -Dstyle.color=never
```

Resultado: **BUILD SUCCESS; 11 pruebas, 0 fallas, 0 errores, 0 omitidas**.
Se generó el JAR ejecutable en `backend/target/`.

`TareaServiceTest` contiene nueve pruebas:

- Completar sin ser asignado se rechaza.
- Primer reclamo exitoso y segundo reclamo con conflicto.
- Consulta ajena rechazada por grupo.
- Recursos: rechazo de todos falsos y envío de booleanos válidos.
- Participantes: envío como JSON y rechazo de lista vacía.
- Propuesta rechazada en completado simple.
- Detalle serializado con campos de tarea al nivel superior.
- Bandeja asignada con empresa y sin posibilidad de reclamo.
- Campos extra rechazados sin enviar completado al motor.

`FlowableClientTest` contiene dos pruebas:

- Servidor HTTP de prueba: filtros de proceso, grupos y paginación;
  cuerpo de claim con usuario y traducción del conflicto 409.
- Conversión de participantes a variable REST JSON.

También pasó `git diff --check`. Estas pruebas usan mocks y un servidor HTTP de
prueba; no certifican el recorrido real del BPMN.

## Documentación actualizada

- `docs/BACKEND.md`: implementación, contratos y verificación.
- `docs/BACKEND_PROGRESO_Y_ETAPAS.md`: avance de etapa 6.
- `docs/ENDPOINTS_WEB.md`: nota de implementación y compatibilidad de IDs.
- `contexto/arquitectura-y-base-de-datos.md`: arquitectura efectiva de tareas.
- `motor-flowable/modelos/README.md`: correcciones y necesidad de republicación.

## Pendientes de integración

No había contenedores de Flowable ni PostgreSQL de este proyecto levantados.
No se probó con curl contra un motor real ni desde la web. Por eso los endpoints
no se marcaron como 🚀, cuyo significado documental exige validación desde la web.

Para cerrar la validación integrada:

1. Levantar PostgreSQL/Flowable y configurar `.env` y la base `institucion`.
2. Publicar el BPMN actualizado y regenerar los exports de la app.
3. Iniciar el backend y crear una solicitud con la etapa 5.
4. Verificar la bandeja del jefe administrativo y las tareas disponibles para
   el empleado administrativo cuando el proceso llegue a tareas de grupo.
5. Probar detalle, reclamo repetido y completado por un usuario distinto al asignado.
6. Adaptar catálogo, selección de formularios y mocks de la web a los IDs actuales;
   el frontend aún usa los anteriores. Validar luego los cuatro endpoints desde la web.

Las etapas 7 a 11, listeners del motor, asignación automática de la lista,
PDFs, simulador y despliegue Docker final mantienen su alcance pendiente.
