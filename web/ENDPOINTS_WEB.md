# Endpoints que necesita la web

Registro de los endpoints del backend que consume el sitio web. Se completa **a medida que la web los necesita**; el backend los implementa a partir de este archivo.

- Base: `/api/v1` (según `docs/ACUERDO_DE_INTEGRACION.md` §F). ⚠️ Confirmar con el back: el documento de arquitectura usa `/api`.
- Sesión por cookie HttpOnly. La web envía siempre `credentials: "include"`.
- **La web nunca envía** `usuarioId`, `assignee`, `empresaId` ni grupos: el backend los obtiene de la sesión.
- Errores con forma `{"codigo": "...", "mensaje": "..."}`. Códigos: `400 VALIDACION`, `401 NO_AUTENTICADO`, `403 SIN_PERMISO`, `404 NO_ENCONTRADO`, `409 ESTADO_INCOMPATIBLE | VERSION_DESACTUALIZADA | YA_RECLAMADA`, `503 MOTOR_NO_DISPONIBLE`.

## Estados de cada endpoint

| Estado | Significado |
|---|---|
| 📝 Pedido | La web lo necesita; falta acordarlo |
| ✅ Acordado | El contrato está confirmado con el back |
| 🧪 Mock | La web lo usa con un mock (MSW) |
| 🚀 Implementado | Está en el backend y probado desde la web |

## Resumen

| # | Método y ruta | Pantalla / uso | Rol | En el acuerdo | Estado |
|---|---|---|---|---|---|
| 1 | `POST /solicitudes` | Formulario público | Público | Sí | 🧪 |
| 2 | `POST /sesiones/personal` | Login del personal | Público | Sí | 🧪 |
| 3 | `POST /sesiones/empresa` | Login de empresa | Público | Sí | 🧪 |
| 4 | `GET /sesiones/actual` | Saber quién está logueado al recargar | Cualquiera logueado | **No — nuevo** | 🧪 |
| 5 | `DELETE /sesiones/actual` | Logout | Cualquiera logueado | Sí | 🧪 |
| 6 | `GET /cursos` | Mis cursos | Empresa | Sí | 📝 |
| 7 | `GET /cursos/{cursoId}` | Detalle y estado del curso | Empresa dueña / personal | Sí | 📝 |
| 8 | `GET /cursos/{cursoId}/actividades` | Actividades en curso (ramas paralelas) | Empresa dueña / personal | Sí | 📝 |
| 9 | `GET /tareas?bandeja=asignadas\|disponibles` | Bandeja de tareas | Personal | Sí | 🧪 |
| 10 | `GET /tareas/{taskId}` | Abrir el formulario de una tarea | Personal (asignado o candidato) | **No — nuevo** | 📝 |
| 11 | `POST /tareas/{taskId}/reclamo` | Botón Reclamar | Personal candidato | Sí | 🧪 |
| 12 | `POST /tareas/{taskId}/completado` | Completar tareas simples | Asignado | Sí | 📝 |
| 13 | `POST /tareas/{taskId}/propuesta` | Registrar/elaborar y revisar propuesta | Jefe administrativo | Sí | 📝 |
| 14 | `GET /cursos/{cursoId}/propuestas` | Historial de versiones de la propuesta | Jefe admin / empresa dueña | **No — nuevo** | 📝 |
| 15 | `GET /cursos/{cursoId}/propuestas/{version}/pdf` | Ver o descargar el PDF de la propuesta | Jefe admin / empresa dueña | Sí | 📝 |
| 16 | `GET /docentes` | Opciones del selector de docente | Jefe docente | **No — nuevo** | 📝 |
| 17 | `POST /tareas/{taskId}/docente` | Definir docente | Jefe docente | Sí | 📝 |
| 18 | `POST /tareas/{taskId}/finalizacion-dictado` | Desarrollar curso | Docente asignado | Sí | 📝 |
| 19 | `GET /cursos/{cursoId}/informe/pdf` | Ver el informe antes de enviarlo | Jefe admin / jefe docente / empresa dueña | Sí | 📝 |

---

## Detalle

### 1. `POST /solicitudes`
- **Pantalla:** `/solicitar` (pública).
- **Header:** `Idempotency-Key: <uuid generado por la web al abrir el formulario>`.
- **Request:**
```json
{
  "empresa": {"nombre": "Empresa Demo", "identificador": "empresa-demo"},
  "contacto": {"nombre": "Contacto Demo", "email": "contacto@example.invalid", "telefono": "358..."},
  "tematica": "Procesos de negocio",
  "descripcion": "Capacitación introductoria",
  "cantidadEstimadaParticipantes": 12,
  "fechasPreferidas": "Primera semana de diciembre, por la mañana"
}
```
- **Response 201:** `{"cursoId": "...", "processInstanceId": "...", "businessKey": "curso:..."}`
- **Errores:** `400` (campos inválidos), `409` (misma clave con otros datos), `503`.
- **Notas:** `empresa.identificador` (CUIT u otro) y `contacto.telefono` son **opcionales**: la web no los envía si están vacíos. El teléfono no está en el ejemplo del acuerdo, pero sí en la arquitectura. Si se reintenta con la misma `Idempotency-Key` y los mismos datos, devolver la misma respuesta (201); con datos distintos, 409.

### 2. `POST /sesiones/personal`
- **Request:** `{"usuarioId": "admin_jefe", "clave": "..."}`
- **Response 200:** `{"usuarioId": "admin_jefe", "nombre": "...", "grupos": ["administracion"], "esJefe": true}`
- **Errores:** `401`, `503`.
- **Notas:** pedir que devuelva `esJefe` (o un `rol`) para no deducirlo en la web a partir del ID.

### 3. `POST /sesiones/empresa`
- **Request:** `{"usuario": "...", "clave": "..."}`
- **Response 200:** `{"empresaId": "...", "nombre": "Empresa Demo"}`
- **Errores:** `401`.

### 4. `GET /sesiones/actual` — nuevo
- **Uso:** al cargar la app, saber si hay sesión y de qué tipo, para proteger rutas y armar el menú.
- **Response 200:**
```json
{"tipo": "PERSONAL", "usuarioId": "admin_jefe", "grupos": ["administracion"], "esJefe": true}
```
o
```json
{"tipo": "EMPRESA", "empresaId": "...", "nombre": "Empresa Demo"}
```
- **Errores:** `401` si no hay sesión.

### 5. `DELETE /sesiones/actual`
- **Response:** `204`.

### 6. `GET /cursos`
- **Query:** `page`, `size` (opcionales).
- **Response 200:**
```json
{"items": [{"cursoId": "...", "nombre": "...", "estado": "EN_PREPARACION", "estadoEtiqueta": "En preparación", "fechaCurso": null}], "total": 1, "page": 0, "size": 20}
```
- **Notas:** solo cursos de la empresa de la sesión (escenario 10).

### 7. `GET /cursos/{cursoId}`
- **Response 200:** datos de la solicitud, `estado`, `estadoEtiqueta`, `resultadoFinal` (nullable), `fechaCurso`, versión de propuesta vigente y documentos disponibles.
- **Errores:** `404` también si el curso es de otra empresa.

### 8. `GET /cursos/{cursoId}/actividades`
- **Response 200:**
```json
{
  "estado": "EN_PREPARACION",
  "items": [
    {"activityId": "Material", "nombre": "Preparar materiales", "tipo": "TAREA_USUARIO"},
    {"activityId": "Nomina", "nombre": "Recibir nómina", "tipo": "ESPERA_MENSAJE"}
  ]
}
```
- **Notas:** pedir que incluya `nombre` legible para mostrar a la empresa sin exponer IDs internos ni nombres del personal.

### 9. `GET /tareas?bandeja=asignadas|disponibles`
- **Response 200:**
```json
{"items": [{"taskId": "...", "taskDefinitionKey": "Propuesta", "nombre": "Registrar y elaborar propuesta", "cursoId": "...", "cursoNombre": "...", "empresaNombre": "...", "creada": "2026-11-09T18:00:00-03:00", "asignado": "admin_jefe", "reclamable": false}], "total": 1, "page": 0, "size": 20}
```
- **Notas:** pedir `reclamable` calculado por el back para mostrar u ocultar el botón Reclamar.

### 10. `GET /tareas/{taskId}` — nuevo
- **Uso:** abrir `/tareas/:id` y elegir el formulario por `taskDefinitionKey`.
- **Response 200:** datos de la tarea más un `contexto` que depende del tipo:

| `taskDefinitionKey` | `contexto` necesario |
|---|---|
| `Propuesta` | Datos de la solicitud (temática, descripción, cantidad, fechas preferidas) |
| `Revisar` | Propuesta vigente + `observacionesEmpresa` |
| `Archivar` | Motivo (`RECHAZADA` / `SIN_RESPUESTA`) |
| `Lista` | `participantes` (nómina recibida) |
| `Activity_12i2hpf` | Nada extra |
| `EnviarInforme` | `informeId` / informe disponible |
| `Activity_1018bdz` | Datos del curso y `fechaCurso` |
| `Dictar` | Datos del curso y `fechaCurso` |

- **Errores:** `403` si no es asignado ni candidato, `404`.

### 11. `POST /tareas/{taskId}/reclamo`
- **Request:** `{}`
- **Response 200:** la tarea, ya asignada al usuario de la sesión.
- **Errores:** `409 YA_RECLAMADA` → la web refresca la bandeja.

### 12. `POST /tareas/{taskId}/completado`
- **Request:** `{"datos": {...}}`, con datos admitidos según la tarea:

| `taskDefinitionKey` | `datos` |
|---|---|
| `Lista` | `{"participantes": [{"nombre": "..."}]}` |
| `Activity_12i2hpf` | `{"requiereAula": true, "requierePlataforma": false, "requiereEquipamiento": true}` (al menos uno `true`) |
| `Archivar`, `EnviarInforme` | `{}` |
| `Enviar` | `{}` (solo si Alejo la deja como tarea humana) |

- **Errores:** `400`, `403`, `409`.

### 13. `POST /tareas/{taskId}/propuesta`
- **Uso:** tareas `Propuesta` y `Revisar`. Genera el PDF y completa la tarea.
- **Request:**
```json
{"programa": "...", "presupuesto": {"importe": "150000.00", "moneda": "ARS"}, "fechasPosibles": ["2026-12-01T09:00:00-03:00"]}
```
- **Response 201:** `{"propuestaId": "...", "propuestaVersion": 1, "pdfDisponible": true}`
- **Errores:** `400`, `409 VERSION_DESACTUALIZADA`.

### 14. `GET /cursos/{cursoId}/propuestas` — nuevo
- **Uso:** en Revisar propuesta, mostrar las versiones anteriores con su PDF.
- **Response 200:** `{"items": [{"version": 1, "estado": "MODIFICACIONES", "creada": "...", "observacionesEmpresa": "..."}]}`

### 15. `GET /cursos/{cursoId}/propuestas/{version}/pdf`
- **Response 200:** `application/pdf`. La web lo abre en otra pestaña.

### 16. `GET /docentes` — nuevo
- **Uso:** selector de "Definir docente".
- **Response 200:** `{"items": [{"usuarioId": "docente_jefe", "nombre": "..."}, {"usuarioId": "docente_01", "nombre": "..."}, {"usuarioId": "docente_02", "nombre": "..."}]}`
- **Notas:** alternativa más simple: incluir la lista en el `contexto` de `GET /tareas/{taskId}`.

### 17. `POST /tareas/{taskId}/docente`
- **Request:** `{"docenteId": "docente_01"}`
- **Response 200.** **Errores:** `400` si no es uno de los tres.

### 18. `POST /tareas/{taskId}/finalizacion-dictado`
- **Request:** `{}`
- **Response 200.** **Errores:** `409` si la tarea no está activa.

### 19. `GET /cursos/{cursoId}/informe/pdf`
- **Response 200:** `application/pdf`.

---

## Pendientes de definir con el back
- [ ] Base `/api/v1` o `/api` (el acuerdo y la arquitectura no coinciden).
- [ ] Nombres de variables definitivos (deben coincidir con el BPMN): `requierePlataforma` vs `requiereAulaVirtual`, `fechaCurso` vs `fechaInicioCurso`, `docenteId` vs `docenteSeleccionado`.
- [ ] Protección CSRF: cómo obtiene la web el token y en qué header lo envía.
- [ ] Si la web se sirve desde el backend (`static/`) o por separado (y si hace falta CORS).
- [ ] Si `Enviar propuesta` es tarea humana (necesita pantalla) o automática.
