# Web — Arquitectura y estado actual

Sitio web de la institución: sección pública, portal de empresas y funciones del personal. Hecho con **React + Vite + TypeScript + React Router**. Mientras el backend no tenga los endpoints, usa **MSW** (Mock Service Worker) para simular la API.

**Estado:** etapas 1 a 9 terminadas: **todas las funcionalidades de la web están hechas** (con mocks). Falta la etapa 10: conectar con el backend real.

---

## Cómo correrla

Desde `web/`:

| Comando | Para qué |
|---|---|
| `npm install` | Instalar dependencias (la primera vez o si cambia `package.json`) |
| `npm run dev:mock` | Con datos de prueba, sin backend. Muestra una franja amarilla "Modo mock" |
| `npm run dev` | Contra el backend real en `localhost:8081` (proxy de Vite: `/api` → `8081`) |
| `npm run build` | Compila y verifica tipos. Usarlo antes de cada commit |

### Usuarios de prueba (solo en modo mock)

Contraseña de todos: **`1234`**.

| Login | Usuarios |
|---|---|
| Personal (`/login`) | `admin_jefe`, `admin_01`, `docente_jefe`, `docente_01`, `docente_02` |
| Empresa (`/empresa/login`) | `empresa-demo`, `otra-empresa` |

Para volver a los datos iniciales del mock: F12 → Console → `localStorage.clear()` y recargar (también cierra la sesión). **Hacelo cada vez que una etapa agregue datos de prueba nuevos**, porque si no seguís viendo los guardados de antes.

| Usuario | Tareas de prueba iniciales |
|---|---|
| `admin_jefe` | Registrar y elaborar propuesta, Archivar solicitud, Revisar propuesta, Enviar informe y archivar expediente |
| `admin_01` | Confeccionar lista definitiva |
| Grupo administración (libres) | Evaluar recursos, Preparar materiales |
| `docente_jefe` | Definir docente |
| `docente_01` | Desarrollar curso |

| Empresa | Cursos de prueba en el portal |
|---|---|
| `empresa-demo` | Atención al cliente (en preparación, 3 actividades en paralelo), Liderazgo (en desarrollo), Seguridad informática (finalizado), Gestión del tiempo (cierre en curso) |
| `otra-empresa` | Seguridad e higiene (confirmado y pendiente de inicio), Primeros auxilios (en preparación, esperando la nómina), Excel inicial (cancelado) |

---

## Estructura

```
web/
├── .env.mock                → VITE_USAR_MOCKS=true (solo con npm run dev:mock)
├── ENDPOINTS_WEB.md         → registro de endpoints que necesita la web (para el back)
├── vite.config.ts           → proxy /api → http://localhost:8081
├── public/
│   └── mockServiceWorker.js → generado por `npx msw init public --save`; no editar
└── src/
    ├── main.tsx             → si hay mocks, arranca MSW; después AuthProvider + Router
    ├── router.tsx           → todas las rutas y cuáles están protegidas
    ├── index.css            → estilos (cabecera, formularios, bandeja…)
    ├── vite-env.d.ts        → tipos de las variables de entorno (VITE_USAR_MOCKS)
    ├── api/
    │   ├── cliente.ts       → api(): TODAS las llamadas pasan por acá; abrirArchivo() para PDFs
    │   ├── tipos.ts         → tipos del contrato (Sesion, NuevaSolicitud, TareaResumen, TareaDetalle, Propuesta…)
    │   ├── tareas.ts        → listar, reclamar, obtener y completar tareas; propuestas, docente, dictado e informe
    │   └── cursos.ts        → cursos de la empresa, detalle y actividades en curso
    ├── auth/
    │   ├── contexto.ts      → define qué guarda la sesión
    │   ├── AuthContext.tsx  → al abrir la web pregunta GET /sesiones/actual
    │   ├── useAuth.ts       → hook para usar la sesión en cualquier página
    │   └── RutaProtegida.tsx→ si no tenés sesión del tipo correcto, te manda al login
    ├── cursos/
    │   ├── estados.ts       → etiqueta y color de cada estado del curso; etapas de la línea de progreso
    │   └── EstadoCurso.tsx  → etiqueta de color con el estado
    ├── components/
    │   └── Layout.tsx       → cabecera con menú según quién esté logueado + aviso de modo mock
    ├── tareas/
    │   ├── catalogo.ts      → título de cada tarea y dónde se completa, según su taskDefinitionKey
    │   ├── DatosCurso.tsx   → bloque con los datos del curso (tareas de docentes y de cierre)
    │   ├── formato.ts       → formato de fechas e importes; conversión datetime-local ↔ ISO (-03:00)
    │   └── formularios/
    │       ├── FormPropuesta.tsx → Registrar y elaborar propuesta / Revisar propuesta
    │       ├── FormArchivar.tsx  → Archivar solicitud
    │       ├── FormLista.tsx     → Confeccionar lista definitiva
    │       ├── FormRecursos.tsx  → Evaluar recursos
    │       ├── FormDocente.tsx   → Definir docente (jefe docente)
    │       ├── FormDictado.tsx   → Desarrollar curso (docente elegido)
    │       └── FormEnviarInforme.tsx → Enviar informe a empresa y archivar expediente
    ├── mocks/
    │   ├── browser.ts       → crea el worker de MSW
    │   ├── datos.ts         → usuarios, empresas, cursos, tareas, propuestas, nóminas, cursos del portal e informes de prueba
    │   ├── handlers.ts      → respuestas falsas de la API, con el formato de ENDPOINTS_WEB.md
    │   └── pdf.ts           → genera un PDF simple para simular los del backend
    └── pages/
        ├── Inicio.tsx           → portada pública
        ├── Solicitar.tsx        → ✅ formulario público de solicitud
        ├── LoginPersonal.tsx    → ✅ login con usuario/contraseña de Flowable
        ├── NoEncontrado.tsx     → 404
        ├── empresa/
        │   ├── LoginEmpresa.tsx → ✅ login de empresas
        │   ├── MisCursos.tsx    → ✅ cursos de la empresa con su estado
        │   └── DetalleCurso.tsx → ✅ estado, progreso, datos, propuesta aceptada y actividades en paralelo
        └── personal/
            ├── Bandeja.tsx      → ✅ tareas asignadas y disponibles, reclamar
            └── DetalleTarea.tsx → ✅ abre una tarea y muestra su formulario según el tipo
```

---

## Rutas

| Ruta | Quién | Estado |
|---|---|---|
| `/` | Público | ✅ |
| `/solicitar` | Público | ✅ |
| `/login` | Personal | ✅ |
| `/empresa/login` | Empresa | ✅ |
| `/tareas` | Personal (protegida) | ✅ |
| `/tareas/:taskId` | Personal (protegida) | ✅ todas las tareas que se completan en la web |
| `/empresa/cursos` | Empresa (protegida) | ✅ |
| `/empresa/cursos/:cursoId` | Empresa (protegida) | ✅ |

---

## Ideas clave

### `api()` en `api/cliente.ts`
- Agrega `/api/v1` adelante, manda la cookie de sesión (`credentials: 'include'`) y pone el `Content-Type` cuando hay body.
- Si el backend responde con error, lanza un `ApiError` con `status` y `codigo` del contrato (`NO_AUTENTICADO`, `YA_RECLAMADA`, `VERSION_DESACTUALIZADA`…).
- **Las páginas nunca usan `fetch` directo**, siempre `api()` o las funciones de `api/tareas.ts`.

### Sesión: `useAuth()`
- Devuelve `sesion`, que puede ser:
  - `null` (no logueado);
  - `{ tipo: 'PERSONAL', usuarioId, grupos, esJefe }`;
  - `{ tipo: 'EMPRESA', empresaId, nombre }`.
- También expone `refrescar()` (se llama después del login) y `cerrarSesion()`.
- **La web nunca envía `usuarioId`, `assignee` ni `empresaId` al backend**: el backend los saca de la sesión.

### `RutaProtegida`
Solo mejora la experiencia en la UI (redirige al login que corresponde). La seguridad real la valida siempre el backend.

### Mocks (MSW)
- Se activan solo con `npm run dev:mock` (lee `.env.mock`).
- Los handlers imitan las reglas del backend real: 401 sin sesión, 403 si no sos candidato, 409 si la tarea ya fue reclamada, idempotencia en solicitudes, etc.
- Sesión, cursos y tareas se guardan en `localStorage`, así sobreviven a recargar y a cambiar de usuario.
- Cuando el back implemente un endpoint, se borra su handler y la web pasa a usar el real con `npm run dev`.

### Detalle de tarea (`/tareas/:taskId`)
- Pide `GET /tareas/{taskId}`, que devuelve la tarea más un `contexto` con lo que necesita su formulario (datos de la solicitud, propuesta vigente, observaciones de la empresa, motivo de archivo).
- `DetalleTarea.tsx` elige el formulario con un `switch` sobre `taskDefinitionKey`. **Para agregar el formulario de una tarea nueva**: crear el componente en `tareas/formularios/` y sumar un `case`.
- Cada formulario recibe `tarea` y `alCompletar(mensaje)`. Al terminar bien, vuelve a la bandeja y muestra el mensaje en verde.
- Si la tarea es de grupo y nadie la reclamó (`reclamable: true`), no muestra el formulario: pide reclamarla desde la bandeja.

| `taskDefinitionKey` | Formulario | Cómo completa |
|---|---|---|
| `Propuesta`, `Revisar` | `FormPropuesta` | `POST /tareas/{id}/propuesta` |
| `Archivar` | `FormArchivar` | `POST /tareas/{id}/completado` con `{}` |
| `Lista` | `FormLista` | `POST /tareas/{id}/completado` con `{ participantes: [{ nombre }] }` |
| `Activity_12i2hpf` | `FormRecursos` | `POST /tareas/{id}/completado` con `{ requiereAula, requierePlataforma, requiereEquipamiento }` |
| `Activity_1018bdz` | `FormDocente` | `POST /tareas/{id}/docente` con `{ docenteId }` |
| `Dictar` | `FormDictado` | `POST /tareas/{id}/finalizacion-dictado` con `{}` |
| `EnviarInforme` | `FormEnviarInforme` | `POST /tareas/{id}/completado` con `{}` |

### PDFs
`abrirArchivo()` abre la pestaña **antes** de descargar (para que el navegador no la bloquee), baja el PDF con la cookie de sesión y lo muestra. Así funciona igual con el mock y con el backend real.

### Fechas
Los `<input type="datetime-local">` se convierten a ISO con offset de Argentina (`2026-12-01T09:00:00-03:00`) con `localAIso()`, y al revés con `isoALocal()`. Argentina no tiene horario de verano, así que el offset es fijo.

### Catálogo de tareas (`tareas/catalogo.ts`)
Asocia cada `taskDefinitionKey` del BPMN con un título legible y dónde se completa:

| Dónde | Tareas | En la bandeja |
|---|---|---|
| `web` | Propuesta, Revisar, Archivar, EnviarInforme, Lista, Evaluar recursos, Definir docente, Desarrollar curso | Botón **Abrir** / **Reclamar** |
| `app-materiales` | Preparar materiales | Etiqueta "Se completa desde la app de materiales" |
| `flowable` | Aula virtual, Equipamiento, Liberar equipamiento, Elaborar informe | Etiqueta "Se completa desde Flowable" |

Si Alejo cambia algún ID en el BPMN, se actualiza acá.

---

## Funcionalidades hechas

### Formulario público de solicitud (`/solicitar`) — escenario 9
- Campos del enunciado (§4.1.1): empresa, contacto, temática, descripción, cantidad de participantes y fechas preferidas. CUIT y teléfono son opcionales.
- Validaciones en la web: obligatorios, email, cantidad entera > 0, descripción de al menos 10 caracteres (el back vuelve a validar).
- **`Idempotency-Key`**: se genera una vez al abrir el formulario. Si el envío falla y se reintenta, va la misma clave y el back no duplica la solicitud.
- Pantalla de confirmación con el número de solicitud (`cursoId`).

### Logins
- Personal con usuario y contraseña de Flowable; empresas con su cuenta institucional.
- 401 → "usuario o contraseña incorrectos"; otro error → "no se pudo conectar".
- Después del login vuelve a la página a la que se intentaba entrar.

### Bandeja de tareas (`/tareas`) — escenario 8
- Dos listas: **Asignadas a mí** y **Disponibles para mi grupo**.
- Las tareas asignadas directamente no tienen botón Reclamar.
- **Reclamar** solo aparece si el back devuelve `reclamable: true` y la tarea se completa en la web.
- Si otra persona la reclamó antes (`409 YA_RECLAMADA`), avisa y refresca la lista.

### Propuesta y revisión (jefe administrativo) — escenario 2
- **Registrar y elaborar propuesta**: muestra los datos de la solicitud y pide programa, presupuesto (importe + moneda) y una o más fechas posibles. Al enviarlo, el back genera la versión con su PDF y completa la tarea.
- **Revisar propuesta**: muestra las **modificaciones pedidas por la empresa**, las versiones anteriores con botón **Ver PDF**, y el formulario precargado con la versión vigente. Genera la versión siguiente (1 → 2 → …) sin pisar las anteriores.
- `409 VERSION_DESACTUALIZADA` → pide volver a abrir la tarea.

### Archivar solicitud — escenarios 3 y 4
Muestra el motivo (propuesta rechazada o sin respuesta tras dos recordatorios) y completa la tarea.

### Confeccionar lista definitiva — escenario 8
- La asigna el Task Listener al administrativo con menos tareas (por eso aparece directo en "Asignadas a mí", sin reclamar).
- Muestra la **nómina enviada por la empresa** (`contexto.participantes`). Se pueden corregir nombres, quitar y agregar. No se verifica a cada participante (lo aclara el enunciado).
- Exige al menos un participante.

### Evaluar recursos — escenarios 6, 7 y 8
- Es tarea de grupo: hay que reclamarla primero.
- Tres opciones: **aula física** (se reserva automáticamente), **aula virtual** y **equipamiento especial** (generan tareas que se completan desde Flowable). Se puede elegir más de una y **al menos una es obligatoria** (el botón queda deshabilitado si no hay ninguna).
- Los nombres de las variables (`requiereAula`, `requierePlataforma`, `requiereEquipamiento`) tienen que coincidir con las condiciones del gateway inclusivo del BPMN.
- La falta de aulas (escenario 7) la maneja el motor después de completar esta tarea; la web no hace nada especial.
- En el mock, al elegir aula virtual o equipamiento aparecen las tareas nuevas en "Disponibles" con la etiqueta "Se completa desde Flowable".

### Docentes y cierre — escenario 1
- **Definir docente** (jefe docente): muestra los datos del curso y los **tres docentes, él incluido**, para elegir uno. El elegido queda asignado a "Desarrollar curso" (`docenteId`). Los docentes vienen en `contexto.docentes`.
- **Desarrollar curso** (docente elegido): la tarea aparece cuando llega `fechaCurso` (timer del BPMN). El docente registra la finalización del dictado, con una confirmación antes de enviar.
- **Elaborar informe de resultados** (jefe docente): se hace desde Flowable; en la bandeja aparece con la etiqueta "Se completa desde Flowable".
- **Enviar informe a empresa y archivar expediente** (jefe administrativo): muestra el informe (resumen y PDF) y, al completar, el motor envía el informe, archiva y **libera los recursos** (compensación del cierre normal).
- En el mock, cada paso crea la tarea siguiente y cambia el estado del curso que ve la empresa (en desarrollo → en elaboración del informe → finalizado).

### Celular
El enunciado (§1.4) dice que el jefe docente define el docente "mediante la aplicación móvil". La web es **responsive** (`@media (max-width: 600px)` al final de `index.css`):
- botones de ancho completo, fáciles de tocar;
- datos en una sola columna;
- línea de progreso vertical;
- cabecera y márgenes más compactos.

Se prueba con F12 → ícono de celular (*Toggle device toolbar*) o abriendo la web desde el teléfono en la misma red con `npm run dev:mock -- --host`.

### Portal de empresa — escenario 10
- **Mis cursos** (`/empresa/cursos`): solo los cursos de la empresa logueada (el backend filtra por la sesión; la web nunca manda `empresaId`). Cada uno con su estado y fecha de inicio.
- **Detalle** (`/empresa/cursos/:id`):
  - Estado con etiqueta de color y **línea de progreso**: en preparación → confirmado y pendiente de inicio → en desarrollo → en elaboración del informe → finalizado. Si está cancelado, muestra un aviso en lugar del progreso.
  - Datos del curso y, si hay, presupuesto y **PDF de la propuesta aceptada**.
  - Durante la preparación: **actividades en curso**, que pueden ser varias a la vez (ramas paralelas): tareas en curso y esperas ("Esperando a la empresa", por ejemplo la nómina). Botón Actualizar.
- Si una empresa entra a la URL de un curso ajeno, el back responde 404 y la web muestra "No encontramos ese curso entre los de tu empresa" (no revela que existe).

| Código de estado | Etiqueta |
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

---

## Endpoints en uso

Detalle completo en `ENDPOINTS_WEB.md`. Todos están en 🧪 Mock por ahora.

| Método y ruta | Usado en |
|---|---|
| `POST /solicitudes` | Formulario público |
| `POST /sesiones/personal` | Login del personal |
| `POST /sesiones/empresa` | Login de empresa |
| `GET /sesiones/actual` | Al abrir la web |
| `DELETE /sesiones/actual` | Botón Salir |
| `GET /tareas?bandeja=asignadas\|disponibles` | Bandeja |
| `POST /tareas/{taskId}/reclamo` | Botón Reclamar |
| `GET /tareas/{taskId}` | Abrir una tarea (con su `contexto`) |
| `POST /tareas/{taskId}/completado` | Archivar solicitud, Lista definitiva, Evaluar recursos |
| `POST /tareas/{taskId}/propuesta` | Propuesta y Revisar propuesta |
| `GET /cursos/{cursoId}/propuestas` | Versiones anteriores en Revisar |
| `GET /cursos/{cursoId}/propuestas/{version}/pdf` | Botón Ver PDF (personal y empresa) |
| `GET /cursos` | Mis cursos (empresa) |
| `GET /cursos/{cursoId}` | Detalle del curso |
| `GET /cursos/{cursoId}/actividades` | Actividades en curso durante la preparación |
| `POST /tareas/{taskId}/docente` | Definir docente |
| `POST /tareas/{taskId}/finalizacion-dictado` | Desarrollar curso |
| `GET /cursos/{cursoId}/informe/pdf` | Ver informe (PDF) en Enviar informe |

---

## Próximas etapas

| Etapa | Qué | Escenarios |
|---|---|---|
| 10 | Cambiar los mocks por el backend real | Todos |
