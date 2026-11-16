# Postman — Backend etapa 7

## Importar

En Postman, usar **Import** y seleccionar ambos archivos:

- `Etapa_7.postman_collection.json`: pruebas de `POST /tareas/{taskId}/propuesta`, `GET /cursos/{cursoId}/propuestas` y `GET /cursos/{cursoId}/propuestas/{version}/pdf`.
- `Local_Etapa_7.postman_environment.json`: variables de conexión y usuarios de ejemplo.

Seleccionar el environment **Cursos - Local etapa 7**. Mantener habilitado el cookie jar de Postman y usar siempre el mismo host (`localhost`).

## Requisitos

Backend en puerto 8081, PostgreSQL institucional disponible, Flowable levantado, usuarios/grupos creados y proceso publicado con los IDs actuales. La empresa demo es opcional y requiere `CURSOS_DATOS_DEMO=true`.

## Orden recomendado

No correr toda la colección con Runner: la carpeta 04 necesita avanzar el proceso entre la v1 y la revisión.

1. **01 — Sesión y solicitud inicial:** login de `Jefe-Administrativo`, creación de solicitud (guarda `cursoId` y `processInstanceId`) y captura de `taskId` para `registrar_elaborar_propuesta`.
2. **02 — Validaciones y permisos:** probar programa vacío, importe `0.00`, lista de fechas vacía y moneda inválida (`400 VALIDACION`). Luego probar con `empleado-administrativo` (`403 SIN_PERMISO`) y volver a loguear al jefe.
3. **03 — Registrar propuesta v1 y PDF:** envía la propuesta inicial (`201`, `propuestaVersion = 1`), lista el historial (`200`), descarga el PDF v1 (`200 application/pdf` empezando con `%PDF-`) y verifica `404` en versión inexistente.
4. **04 — Revisar propuesta (v2 y control 409):**
   - **Precondición:** como la etapa 10 (simulador) todavía no está implementada, avanzar la espera de respuesta de la empresa en Flowable fijando `respuestaEmpresa = MODIFICACIONES` y `observacionesEmpresa`, y pasar el estado de la propuesta v1 a `MODIFICACIONES` en la tabla `propuesta`.
   - Capturar `taskId` de `revisar_propuesta`, verificar el detalle, probar `versionBase: 999` (`409 VERSION_DESACTUALIZADA`) y luego enviar la revisión válida con `versionBase: 1` (`201`, versión 2). Verificar que el listado devuelve v2 y v1, y que ambos PDFs siguen descargándose.
5. **05 — Acceso sin sesión y empresa:** cerrar sesión, comprobar `401` en listado/PDF, y opcionalmente loguear `empresa-demo` para comprobar `403` al intentar crear propuesta y `404` sobre un curso ajeno.
