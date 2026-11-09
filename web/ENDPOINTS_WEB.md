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
| 6 | `GET /cursos` | Mis cursos | Empresa | Sí | 🧪 |
| 7 | `GET /cursos/{cursoId}` | Detalle y estado del curso | Empresa dueña / personal | Sí | 🧪 |
| 8 | `GET /cursos/{cursoId}/actividades` | Actividades en curso (ramas paralelas) | Empresa dueña / personal | Sí | 🧪 |
| 9 | `GET /tareas?bandeja=asignadas\|disponibles` | Bandeja de tareas | Personal | Sí | 🧪 |
| 10 | `GET /tareas/{taskId}` | Abrir el formulario de una tarea | Personal (asignado o candidato) | **No — nuevo** | 🧪 |
| 11 | `POST /tareas/{taskId}/reclamo` | Botón Reclamar | Personal candidato | Sí | 🧪 |
| 12 | `POST /tareas/{taskId}/completado` | Completar tareas simples | Asignado | Sí | 🧪 |
| 13 | `POST /tareas/{taskId}/propuesta` | Registrar/elaborar y revisar propuesta | Jefe administrativo | Sí | 🧪 |
| 14 | `GET /cursos/{cursoId}/propuestas` | Historial de versiones; propuesta aceptada en el portal | Jefe admin / empresa dueña | **No — nuevo** | 🧪 |
| 15 | `GET /cursos/{cursoId}/propuestas/{version}/pdf` | Ver el PDF de la propuesta (Revisar y portal de empresa) | Jefe admin / empresa dueña | Sí | 🧪 |
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
- **Response 200:**
```json
{
  "cursoId": "...",
  "nombre": "Atención al cliente",
  "estado": "EN_PREPARACION",
  "estadoEtiqueta": "En preparación",
  "fechaCurso": "2026-11-25T09:00:00-03:00",
  "descripcion": "Comunicación y manejo de reclamos para el equipo de atención.",
  "cantidadParticipantes": 5
}
```
- **Errores:** `404` también si el curso es de otra empresa (no revelar que existe).
- **Notas:** la web muestra una línea de progreso según `estado`. Códigos que usa:

| `estado` | Etiqueta |
|---|---|
| `EN_ACUERDO` | En evaluación o acuerdo |
| `EN_PREPARACION` | En preparación |
| `CONFIRMADO_PENDIENTE_INICIO` | Confirmado y pendiente de inicio |
| `EN_DESARROLLO` | En desarrollo |
| `EN_ELABORACION_INFORME` | En elaboración del informe |
| `EN_CIERRE` | Cierre en curso |
| `CANCELACION_EN_CURSO` | Cancelación en curso |
| `FINALIZADO` | Finalizado |
| `CANCELADO` | Cancelado |

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
- **Notas:**
  - `tipo` puede ser `TAREA_USUARIO` (se muestra "En curso"), `ESPERA_MENSAJE` ("Esperando a la empresa", por ejemplo la nómina) o `AUTOMATICA`.
  - `nombre` tiene que ser legible para la empresa, sin exponer nombres del personal.
  - Durante la preparación hay que devolver **todas** las actividades activas (una por rama paralela). Fuera de la preparación, `items` vacío.
  - Mismo control de acceso que el #7: `404` si el curso es de otra empresa.

### 9. `GET /tareas?bandeja=asignadas|disponibles`
- **Response 200:**
```json
{"items": [{"taskId": "...", "taskDefinitionKey": "Propuesta", "nombre": "Registrar y elaborar propuesta", "cursoId": "...", "cursoNombre": "...", "empresaNombre": "...", "creada": "2026-11-09T18:00:00-03:00", "asignado": "admin_jefe", "reclamable": false}], "total": 1, "page": 0, "size": 20}
```
- **Notas:**
  - `asignadas`: tareas con `assignee` = usuario de la sesión. `disponibles`: tareas sin asignar cuyo grupo candidato incluye al usuario.
  - La web muestra el botón Reclamar solo si `reclamable` es `true` (lo calcula el back). Con `409 YA_RECLAMADA` refresca la lista.
  - Las tareas que no se completan en la web (Preparar materiales, aula virtual, equipamiento, liberar equipamiento, elaborar informe) se muestran con una etiqueta, sin botón.

### 10. `GET /tareas/{taskId}` — nuevo
- **Uso:** abrir `/tareas/:id` y elegir el formulario por `taskDefinitionKey`.
- **Response 200:** datos de la tarea más un `contexto` que depende del tipo:

| `taskDefinitionKey` | `contexto` necesario |
|---|---|
| `Propuesta` | `solicitud`: `{ tematica, descripcion, cantidadEstimadaParticipantes, fechasPreferidas }` |
| `Revisar` | `solicitud` + `propuestaVigente`: `{ version, programa, presupuesto, fechasPosibles, estado, observacionesEmpresa, creada }` + `observacionesEmpresa` |
| `Archivar` | `motivoArchivo`: `RECHAZADA` o `SIN_RESPUESTA` |
| `Lista` | `participantes`: `[{ "nombre": "..." }]` (la nómina que envió la empresa) |
| `Activity_12i2hpf` | Nada extra |
| `EnviarInforme` | `informeId` / informe disponible |
| `Activity_1018bdz` | Datos del curso y `fechaCurso` |
| `Dictar` | Datos del curso y `fechaCurso` |

- **Ejemplo (Revisar):**
```json
{
  "taskId": "t-8",
  "taskDefinitionKey": "Revisar",
  "nombre": "Revisar propuesta",
  "cursoId": "c-6",
  "cursoNombre": "Ventas consultivas",
  "empresaNombre": "Otra Empresa SRL",
  "creada": "2026-11-09T12:30:00-03:00",
  "asignado": "admin_jefe",
  "reclamable": false,
  "contexto": {
    "solicitud": {"tematica": "Ventas consultivas", "descripcion": "...", "cantidadEstimadaParticipantes": 8, "fechasPreferidas": "Martes y jueves por la tarde"},
    "propuestaVigente": {"version": 1, "programa": "...", "presupuesto": {"importe": "200000.00", "moneda": "ARS"}, "fechasPosibles": ["2026-12-02T14:00:00-03:00"], "estado": "MODIFICACIONES", "observacionesEmpresa": "...", "creada": "..."},
    "observacionesEmpresa": "El presupuesto nos queda alto..."
  }
}
```
- **Errores:** `403` si no es asignado ni candidato, `404` si no existe o ya fue completada.
- **Notas:** si la tarea es de grupo y nadie la reclamó (`reclamable: true`), la web no muestra el formulario: pide reclamarla desde la bandeja.

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

- **Response 200:** `{"completada": true}` (la web no usa el cuerpo).
- **Errores:** `400 VALIDACION` con mensaje (la web lo muestra tal cual), `403` si no es el asignado, `409`.
- **Notas:**
  - Validar en el back: lista definitiva con al menos un participante; en Evaluar recursos, al menos un recurso en `true`.
  - Los nombres `requiereAula`, `requierePlataforma`, `requiereEquipamiento` tienen que coincidir con las condiciones del gateway inclusivo del BPMN.

### 13. `POST /tareas/{taskId}/propuesta`
- **Uso:** tareas `Propuesta` y `Revisar`. Genera el PDF y completa la tarea.
- **Request:**
```json
{"programa": "...", "presupuesto": {"importe": "150000.00", "moneda": "ARS"}, "fechasPosibles": ["2026-12-01T09:00:00-03:00"]}
```
- **Response 201:** `{"propuestaId": "...", "propuestaVersion": 1, "pdfDisponible": true}`
- **Errores:** `400` (programa vacío, importe ≤ 0 o sin fechas), `403`, `409 VERSION_DESACTUALIZADA` (la web pide reabrir la tarea).
- **Notas:** importe como texto con 2 decimales (evita redondeos). Fechas en ISO con offset de Argentina (`-03:00`). En `Revisar` se crea la versión siguiente sin pisar las anteriores.

### 14. `GET /cursos/{cursoId}/propuestas` — nuevo
- **Uso:** en Revisar propuesta, mostrar las versiones anteriores con su PDF. En el portal de empresa, encontrar la propuesta `ACEPTADA` para mostrar presupuesto y PDF.
- **Response 200:**
```json
{"items": [{"version": 1, "programa": "...", "presupuesto": {"importe": "200000.00", "moneda": "ARS"}, "fechasPosibles": ["2026-12-02T14:00:00-03:00"], "estado": "MODIFICACIONES", "observacionesEmpresa": "...", "creada": "2026-11-05T10:00:00-03:00"}]}
```
- **Notas:** `estado` de cada versión: `ENVIADA`, `MODIFICACIONES`, `ACEPTADA` o `RECHAZADA`. La empresa dueña del curso también lo usa (filtrar por sesión).

### 15. `GET /cursos/{cursoId}/propuestas/{version}/pdf`
- **Response 200:** `application/pdf`. La web lo descarga con la cookie de sesión y lo abre en otra pestaña.
- **Errores:** `404` si no existe la versión o el curso es de otra empresa.
- **Notas:** la empresa dueña del curso también lo usa, para ver la propuesta aceptada en su portal.

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
