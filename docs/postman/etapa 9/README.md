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
| **4. Etapa 9 - paso a paso** | Escenario completo del paso 1 (definir docente). Se corre con **Run folder**. |
| 9. Flowable directo (debug) | Instancias, tareas, variables, historial, usuarios del grupo docente, fijar `fechaCurso`, saltar de actividad y borrar la instancia. |

## Etapa 9 — definir docente (#17)

Requisitos: backend en 8081, Flowable con la app publicada y **`desarrollar_curso` con
assignee `${docenteId}`** (ver `motor-flowable/modelos/README.md`), usuarios creados.

Clic derecho en **4. Etapa 9 - paso a paso** → **Run folder**:

| Request | Espera |
|---|---|
| Login Jefe-Administrativo → Crear solicitud | 200 → 201 |
| [Flowable] Fijar fechaCurso (hace 1 minuto) | 200/201. Fecha ya pasada para que el timer *Fecha programa* se dispare enseguida. |
| [Flowable] Saltar de registrar_elaborar_propuesta a definir_docente | 200. Atajo con `change-state` para no recorrer todo el proceso. |
| Login Jefe-Docente → Bandeja asignadas: definir_docente | Guarda `taskId` |
| Detalle: trae los docentes | `contexto.docentes` con 3 docentes, `Jefe-Docente` con `esJefe: true` |
| Definir docente que no es docente | 400 |
| Definir docente: docente-1 | 200 `{"completada": true}` |
| Definir docente otra vez | 404 o 409 (la tarea ya no existe) |
| Login docente-1 → Bandeja asignadas: desarrollar_curso | Espera 20 s al timer y encuentra la tarea asignada a `docente-1` |
| [Flowable] Borrar la instancia de prueba | 204 |

Resultado al 17/11/2026: **todos los pasos en verde** contra Flowable y Postgres reales.

Si falla `desarrollar_curso`: revisar con `9. Flowable directo → Tareas de la instancia
actual` a quién quedó asignada. Si figura el grupo, la app publicada no tiene el
`${docenteId}`. Si no existe la tarea, mirar `docker logs --tail 50 flowable-persistente`.

## Buenas prácticas

- Las instancias de prueba cuentan para la regla de viabilidad (`cantCursos`, máximo 5):
  borrarlas al terminar. Si un escenario falla a mitad de camino, usar
  `9. Flowable directo → Borrar instancia actual`.
- No compartir cookies ni claves reales al pasar resultados.
- Al agregar requests, exportar la colección (v2.1), reemplazar el `.json` de esta
  carpeta y commitear.
