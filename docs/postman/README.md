# Postman — Backend etapa 6

## Importar

En Postman, usar **Import** y seleccionar ambos archivos:

- `Etapa_6.postman_collection.json`: solicitudes con cuerpos JSON y verificaciones.
- `Local.postman_environment.json`: variables de conexión y usuarios de ejemplo.

Seleccionar el environment **Cursos - Local etapa 6**. Revisar `baseUrl` y las
credenciales: `1234` es la clave de demo documentada, no una credencial verificada
en la instalación de cada compañero. Mantener habilitado el cookie jar de Postman.
No configurar Basic Auth para estos endpoints: la sesión se obtiene con el login.
Usar siempre el mismo host (no alternar localhost con 127.0.0.1).

## Requisitos

Backend en puerto 8081, PostgreSQL institucional disponible, Flowable levantado,
usuarios/grupos creados y proceso publicado con los IDs actuales. La aplicación
backend debe tener configurado su `.env`. La empresa demo es opcional y requiere
`CURSOS_DATOS_DEMO=true`.

**Atención al modelo:** al preparar esta colección, el archivo
`motor-flowable/modelos/organizacion-cursos.bpmn20.xml` figuraba eliminado en el
working tree. Confirmar qué modelo se publicó antes de probar. Esta colección
no restaura ni publica modelos.

## Orden recomendado

No ejecutar toda la colección con Runner: las carpetas necesitan estados y tareas
diferentes. Cada carpeta contiene su explicación en Postman.

1. **01 — Sesión y solicitud:** login del jefe y consulta de sesión. Crear solicitud
   solo si hace falta: inicia una instancia real y guarda `cursoId` y
   `processInstanceId`. Repetir con la misma clave y cuerpo reutiliza la solicitud;
   cambiar `idempotencyKey` para crear otra. Con 5 instancias activas puede resultar
   no viable y no generar tarea.
2. **02 — Bandeja y detalle:** mantener `taskDefinitionKey` en
   `registrar_elaborar_propuesta`. La bandeja guarda `taskId` de una coincidencia
   con ese tipo y con `cursoId` si está informado. Probar detalle y errores.
   El rechazo de completado simple requiere que sea una propuesta asignada al jefe.
3. **03 — Reclamo:** avanzar previamente el proceso hasta una tarea de grupo sin
   asignar, como `evaluar_recursos`, desde Flowable o con las operaciones que tenga
   implementadas el equipo. Cambiar `taskDefinitionKey` a `evaluar_recursos`.
   Login del empleado, disponibles, detalle, claim y segundo claim (409).
   Cambiar al jefe para intentar completar la tarea del empleado (403) y volver
   al empleado para continuar.
4. **04 — Recursos:** con esa tarea asignada al empleado, ejecutar errores primero
   (400) y completado válido al final (200). Este último hace avanzar el proceso.
5. **05 — Lista:** obtener una tarea activa `confeccionar_lista`; cambiar
   `taskDefinitionKey`, capturar su `taskId` y, si está disponible, reclamarla.
   Ejecutar errores y luego lista válida.
6. **06 — Archivo/informe:** obtener una tarea activa `archivar_solicitud`,
   `enviar_informe` o `archivar_expediente`, iniciar sesión con su asignado,
   capturar `taskId` y probar datos extra y completado vacío.
7. **07 — Permisos:** guardar en `taskIdAjena` una tarea administrativa activa.
   El jefe docente debe obtener 403 al consultarla. Luego logout y consulta sin
   sesión (401). Opcionalmente, login empresa demo y consulta de tareas (403).

La búsqueda automática solo revisa los primeros 20 resultados. Si no encuentra
coincidencia, borra `taskId` y escribe una advertencia en Postman Console. Revisar
el filtro, `cursoId`, la página o copiar manualmente un ID del listado.
Si se trabaja con otro curso, cambiar `cursoId` o vaciarlo para no filtrar por curso.

Una tarea completada deja de estar activa: volver a consultar el mismo ID debe
responder 404. No volver a ejecutar sus casos esperando 200/400. No usar los IDs
viejos (`Propuesta`, `Lista`, etc.).

## JSON de los cuerpos principales

Login personal:

```json
{"usuarioId":"Jefe-Administrativo","clave":"1234"}
```

Reclamo de tarea (el backend toma el usuario de la sesión):

```json
{}
```

Lista definitiva:

```json
{"datos":{"participantes":[{"nombre":"Ana Pérez"},{"nombre":"Juan Gómez"}]}}
```

Evaluación de recursos:

```json
{"datos":{"requiereAula":true,"requierePlataforma":false,"requiereEquipamiento":true}}
```

Archivo/envío de informe:

```json
{"datos":{}}
```

## Qué se comprueba

Las solicitudes tienen scripts de pruebas para el código HTTP esperado; en los
casos de error verifican además `codigo`. El reclamo comprueba asignado y
`reclamable=false`; los completados válidos comprueban `completada=true`.

Estas son pruebas preparadas para ejecutar en cada instalación. No se ejecutó
esta colección contra un servidor real durante su preparación. La implementación
sí tiene una ejecución previa de Maven con 11 pruebas aprobadas; esos resultados
no reemplazan las pruebas integradas de Postman.

Si aparece 503, comprobar conexión/credenciales de Flowable y los logs del backend.
Si no hay tareas disponibles, verificar la fase del proceso y si alguien ya las
reclamó. Para compartir resultados, enviar la respuesta, el tipo de tarea y el
resultado del test; no compartir cookies de sesión ni claves reales.
