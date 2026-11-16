# Web — Guía rápida

Comandos para correr la web y usuarios de prueba del modo mock.

---

## Comandos

Todos se corren desde la carpeta `web/`.

| Comando | Para qué |
|---|---|
| `npm install` | Instalar dependencias (la primera vez, o si cambia `package.json`) |
| `npm run dev:mock` | Levantar la web **con datos de prueba**, sin backend → http://localhost:5173 |
| `npm run dev` | Levantar la web **contra el backend real** (`localhost:8081`) |
| `npm run dev:mock -- --host` | Igual que `dev:mock`, pero accesible desde el celular en la misma red (usar la URL "Network" que muestra la consola) |
| `npm run build` | Compilar y verificar tipos. **Correrlo antes de cada commit** |
| `npm run lint` | Revisar el código con el linter |
| `npx msw init public --save` | Regenerar `public/mockServiceWorker.js` (solo si se borró o se actualizó MSW) |

Para cortar el servidor: **Ctrl+C** en la terminal.

### Entorno completo (backend real)

Desde la raíz del repo:

```bash
# 1. Flowable + PostgreSQL + pgAdmin
docker compose --env-file env up -d
docker compose --env-file env ps

# 2. Backend (otra terminal)
cd backend
mvn spring-boot:run

# 3. Web (otra terminal)
cd web
npm run dev
```

| Servicio | URL |
|---|---|
| Web | http://localhost:5173 |
| Backend | http://localhost:8081/api/estado |
| Flowable UI | http://localhost:8080/flowable-ui/ |
| API REST de Flowable | http://localhost:8080/flowable-ui/process-api |
| pgAdmin | http://localhost:5050 |

Para apagar Docker sin borrar datos: `docker compose --env-file env stop`. **No usar `down -v`**: borra todas las instancias y datos.

---

## Usuarios de prueba (modo mock)

Contraseña de **todos**: **`1234`**

### Personal — login en `/login`

| Usuario | Rol | Grupo | Tareas iniciales |
|---|---|---|---|
| `admin_jefe` | Jefa administrativa | Administración | Registrar y elaborar propuesta · Revisar propuesta · Archivar solicitud · Enviar informe y archivar expediente |
| `admin_01` | Administrativo | Administración | Confeccionar lista definitiva |
| `docente_jefe` | Jefe del equipo docente | Docentes | Definir docente |
| `docente_01` | Docente | Docentes | Desarrollar curso |
| `docente_02` | Docente | Docentes | — (recibe "Desarrollar curso" si el jefe lo elige) |

Tareas libres del grupo Administración (se ven en "Disponibles" de `admin_jefe` y `admin_01`):

- **Evaluar recursos** → se reclama y se completa en la web
- **Preparar materiales** → se completa desde la app de materiales (en la web solo se muestra)

### Empresas — login en `/empresa/login`

| Usuario | Empresa | Cursos |
|---|---|---|
| `empresa-demo` | Empresa Demo S.A. | Atención al cliente (en preparación, 3 actividades en paralelo) · Liderazgo (en desarrollo) · Seguridad informática (finalizado) · Gestión del tiempo (cierre en curso) |
| `otra-empresa` | Otra Empresa SRL | Seguridad e higiene (confirmado y pendiente de inicio) · Primeros auxilios (en preparación, esperando la nómina) · Excel inicial (cancelado) |

> Los usuarios y contraseñas son **solo del mock**. En el sistema real, el personal entra con los usuarios que se crean en Flowable, y las empresas con la cuenta que se habilita al aceptar una propuesta.

---

## Resetear los datos del mock

Los datos de prueba (tareas, cursos, propuestas) y la sesión se guardan en el navegador. Para volver al estado inicial:

1. Con la web abierta, **F12** (o **Ctrl+Shift+J** en Chrome) → pestaña **Console**.
2. Escribir y apretar Enter:
   ```js
   localStorage.clear()
   ```
3. Recargar con **F5** y volver a loguearse.

Si la consola no deja pegar, escribir `allow pasting` y Enter primero.

---

## Recorridos para probar

| Escenario | Pasos |
|---|---|
| 9 · Solicitud desde la web | Sin login → "Solicitar capacitación" → completar y enviar |
| 2 · Propuesta y revisión | `admin_jefe` → Registrar y elaborar propuesta → luego Revisar propuesta (ver PDF de la versión 1, generar la 2) |
| 3 / 4 · Archivar | `admin_jefe` → Archivar solicitud |
| 8 · Reclamo de tareas | `admin_01` → reclamar Evaluar recursos → elegir recursos · Confeccionar lista definitiva |
| 1 · Desarrollo y cierre | `docente_jefe` → Definir docente → `docente_0X` → Desarrollar curso → `admin_jefe` → Enviar informe |
| 10 · Portal de empresa | `empresa-demo` → ver sus cursos y las actividades en paralelo · probar `/empresa/cursos/c-4` (es de la otra empresa → no lo muestra) |

Para ver la web como en un celular: **F12 → Ctrl+Shift+M** (*Toggle device toolbar*).

---

## Si algo no anda

| Síntoma | Causa probable |
|---|---|
| No aparece la franja amarilla "Modo mock" | Se levantó con `npm run dev` en vez de `dev:mock`, o falta `web/.env.mock` (`echo "VITE_USAR_MOCKS=true" > .env.mock`) |
| El login dice "No se pudo conectar con el sistema" | Los mocks están apagados (ver arriba) o el backend no está levantado |
| No aparecen las tareas o cursos nuevos | Datos viejos guardados → `localStorage.clear()` |
| Error 502 en `/api/...` con `npm run dev` | El backend no está corriendo en el puerto 8081 |
| `npm run build` falla con "Cannot find name …" | Falta un import o un `export` en algún archivo |
