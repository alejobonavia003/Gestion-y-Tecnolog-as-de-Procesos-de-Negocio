# Postman — Colección general y etapa 9

| Archivo | Qué es |
|---|---|
| `cursos-backend.postman_collection.json` | Colección **Cursos GyTPN - Backend**: sesiones, solicitudes, tareas, atajos de Flowable y la etapa 9 paso a paso. Es la colección que se sigue ampliando con las etapas que faltan. |
| `cursos-local.postman_environment.json` | Entorno **Cursos - Local**: URLs del backend y de Flowable, clave de los usuarios demo y usuario técnico de Flowable. |

## Importar

1. Postman de escritorio (la versión web no llega a `127.0.0.1` sin el agente).
2. **Import** → los dos `.json`. Si ya tenías una versión anterior de la colección,
   borrala antes para no tener dos con el mismo nombre.
3. Arriba a la derecha, seleccionar el entorno **Cursos - Local**. Si las variables
   `{{baseUrl}}` o `{{flowableUrl}}` aparecen en rojo, no está seleccionado.
4. En el entorno, completar `flowablePass` en la columna **Current value** con el
   `FLOWABLE_REST_CLAVE` del `.env`. El *current value* queda solo en tu máquina; dejar
   vacío el *initial value*, que es lo que se exporta.

## Cómo funciona

- **Sesión:** los login guardan la cookie `JSESSIONID` y Postman la manda sola. Para
  cambiar de usuario, ejecutar otro login. Los requests al backend usan **No Auth**.
- **Flowable directo:** los requests marcados `[Flowable]` y la carpeta
  `9. Flowable directo` usan Basic Auth con `{{flowableUser}}` / `{{flowablePass}}`.
- **Variables automáticas:** `Crear solicitud` guarda `cursoId` y `processInstanceId`;
  las bandejas guardan `taskId` de la tarea del curso actual.
- **Tests:** cada request verifica el código esperado (pestaña *Test Results*).

## Carpetas

| Carpeta | Uso |
|---|---|
| 0. Utilidades | Estado del backend y diagnóstico de Flowable. |
| 1. Sesiones (etapa 4) | Login de los cinco usuarios del personal y de `empresa-demo`. |
| 2. Solicitudes (etapa 5) | Crear, reintento idempotente, 409 y 400. Se puede correr con **Run folder**. |
| 3. Tareas (etapa 6) - escenario | Escenario hecho para otra versión de la etapa 6: algunos tests pueden fallar con la implementación actual. Para la etapa 6 usar `docs/postman/etapa 6/`. |
| **4. Etapa 9 - paso a paso** | Escenario completo de la etapa 9 (#17, #18 y #19). Se corre con **Run folder**. |
| 9. Flowable directo (debug) | Instancias, tareas, variables, historial, usuarios del grupo docente, fijar `fechaCurso`, saltar de actividad y borrar la instancia. |

## Etapa 9 completa (#17, #18 y #19)

Requisitos: backend en 8081, Flowable con la app publicada (`desarrollar_curso` con
assignee `${docenteId}`, ver `motor-flowable/modelos/README.md`), usuarios creados y
**`CURSOS_DATOS_DEMO=true`** en el `.env` (el último paso usa `empresa-demo`).

Clic derecho en **4. Etapa 9 - paso a paso** → **Run folder**. En la pantalla del Runner,
elegir el entorno **Cursos - Local** antes de **Run** (si queda en *none*, falla con
"Request could not be sent").

| Paso | Request | Espera |
|---|---|---|
| Preparación | Login Jefe-Administrativo → Crear solicitud | 200 → 201 |
| | [Flowable] Fijar fechaCurso (hace 1 minuto) | 200/201: el timer *Fecha programa* se dispara enseguida |
| | [Flowable] Saltar de registrar_elaborar_propuesta a definir_docente | 200 (atajo con `change-state`) |
| **#17** | Login Jefe-Docente → Bandeja asignadas: definir_docente | Guarda `taskId` |
| | Detalle: trae los docentes | `contexto.docentes` con 3, `Jefe-Docente` con `esJefe: true` |
| | Definir docente que no es docente | 400 |
| | Definir docente: docente-1 | 200 |
| | Definir docente otra vez | 404 o 409 |
| **#18** | Login docente-1 → Bandeja asignadas: desarrollar_curso | Espera 20 s al timer y la encuentra |
| | Login docente-2 → Docente ajeno finaliza | 403 |
| | Login docente-1 → Finalizar dictado | 200 |
| | Finalizar otra vez | 409 `ESTADO_INCOMPATIBLE` |
| | Curso en EN_ELABORACION_INFORME | `estado: EN_ELABORACION_INFORME` |
| | [Flowable] Buscar elaborar_informe | La tarea existe |
| **#19** | Login Jefe-Administrativo → Informe PDF todavía sin informe | 404 |
| | [Flowable] Completar elaborar_informe con informeResumen | 200 (simula el formulario de Flowable UI) |
| | [Flowable] Buscar la tarea de cierre | `enviar_informe` o `archivar_expediente` |
| | Detalle de cierre: trae el informe | `contexto.informe.resumen` y `pdfDisponible: true` |
| | Informe PDF (jefe administrativo) | 200 `application/pdf` (se ve en *Body*) |
| | Login docente-1 → Informe PDF como docente | 403 |
| | Login empresa-demo → Informe PDF de curso ajeno | 404 |
| Limpieza | [Flowable] Borrar la instancia de prueba | 204 |

Resultado al 17/11/2026: **30 de 30 en verde** contra Flowable y Postgres reales.

**Si algo falla:**

| Falla | Causa |
|---|---|
| `desarrollar_curso` no aparece | La app publicada no tiene `${docenteId}` en esa tarea, o el timer falló (`docker logs --tail 50 flowable-persistente`). |
| `pdfDisponible: false` | El campo del formulario no se llama exactamente `informeResumen`. |
| Login empresa-demo da 401 | Falta `CURSOS_DATOS_DEMO=true` en el `.env` (y reiniciar el backend). El request siguiente da 403 porque sigue la sesión de `docente-1`. |

**Probar el informe desde Flowable UI:** correr la carpeta hasta *[Flowable] Buscar
elaborar_informe*, entrar a Flowable UI como `Jefe-Docente` / `1234` → Task app →
*Elaborar informe* → completar el formulario → **Complete**, y después pedir el PDF en
Postman como `Jefe-Administrativo`.

## Buenas prácticas

- Las instancias de prueba cuentan para la regla de viabilidad (`cantCursos`, máximo 5):
  borrarlas al terminar. Si un escenario falla a mitad de camino, usar
  `9. Flowable directo → Borrar instancia actual`.
- No compartir cookies ni claves reales al pasar resultados.
- Al agregar requests, exportar la colección (v2.1), reemplazar el `.json` de esta
  carpeta y commitear.
