# Guion de la demo: los 11 escenarios

> Objetivo: mostrar los 11 escenarios en unos **25–30 minutos**, sin esperas ni improvisación. Todo corre sobre el motor real, sin mocks. Lo lento (crear instancias y avanzarlas) se deja hecho de antemano con `demo/preparar_escenarios.py`.

---

## 1. La idea en una tabla

El script deja **4 instancias preparadas**. Las otras dos se crean en vivo:

| Instancia | Empresa (login portal) | Cómo queda preparada | Escenarios |
|---|---|---|---|
| **A** (en vivo) | Transportes Córdoba (`transportes-cba`) | — | **9** → **2** → **3** |
| **N** (en vivo) | Constructora Centro | — | **5** |
| **B** | Lácteos del Sur | Propuesta v1 enviada, timer de 5 días pendiente | **4** |
| **C** | Metalúrgica Río Cuarto (`metalurgica-rc`) | En preparación, **aula física y equipamiento ya gestionados**; nómina y materiales pendientes | **6** |
| **D** | Agro Pampa (`agro-pampa`) | En preparación, `simularSinAulas = 'true'`, `evaluar_recursos` sin reclamar | **7** |
| **E** | Software Andino (`software-andino`) | En preparación, recursos (aula física y virtual) y nómina listos; faltan `confeccionar_lista` y `preparar_materiales`; `fechaCurso` ya pasada | **10**, **8**, **11**, **1** |

> ⚠️ **Por qué exactamente 4:** `cantCursos` cuenta **todas** las instancias activas. Con 4 preparadas, la solicitud A llega con `cantCursos = 4` (viable) y la siguiente, N, con `cantCursos = 5` (NO_VIABLE). Si hubiera 5 o más, no se podría mostrar el escenario 9.

---

## 2. Preparación (el día anterior)

### 2.1 Levantar todo (si no está levantado)

```bash
docker compose up -d --build
bash motor-flowable/idm/crear-usuarios.sh
docker cp motor-flowable/extensiones/flowable-extensiones.jar flowable-persistente:/app/WEB-INF/lib/flowable-extensiones.jar
docker restart flowable-persistente          # esperar ~1 min
bash motor-flowable/modelos/desplegar.sh
mvn -f app-materiales/pom.xml compile
mvn -f simulador-rest/pom.xml compile
```

### 2.2 Preparar los escenarios

```bash
python3 demo/preparar_escenarios.py preparar --limpiar
```

- `--limpiar` **borra las instancias activas** (las pruebas viejas también cuentan para `cantCursos`) y **vacía la BD `institucion`**. El historial de Flowable se conserva, y sirve para las consultas de §4.3.
- Al empezar, el script verifica que el backend responda, que el proceso esté desplegado y que tenga las 3 asociaciones de compensación.
- Al terminar muestra en qué punto quedó cada instancia, y lo guarda en `demo/escenarios.json`.
- Si algo falla, el script se detiene y dice en qué paso falló.

### 2.3 Revisar y guardar el estado

```bash
python3 demo/preparar_escenarios.py estado     # tiene que decir: 4 activas → VIABLE
python3 demo/preparar_escenarios.py backup     # guarda las 2 bases + los PDFs en demo/backups/escenarios/
```

### 2.4 Ensayar las veces que quieran

Hagan la demo completa de punta a punta (sección 4) y después vuelvan al punto de partida:

```bash
python3 demo/preparar_escenarios.py restaurar  # ~1-2 min; cerrar antes app-materiales
```

> ⏰ **Límite de tiempo:** el timer de B vence **5 días después de preparar**. Preparen **como máximo 4 días antes** de la defensa. Si pasa ese plazo, B manda recordatorios sola, y restaurar el backup no lo evita porque la fecha de vencimiento queda guardada. En ese caso, vuelvan a correr `preparar --limpiar` y `backup`.

---

## 3. Antes de entrar (10 minutos antes)

1. `python3 demo/preparar_escenarios.py restaurar` (si ensayaron después del backup) y después `estado`: **4 activas**.
2. Abrir las ventanas en este orden:

| Ventana | Dirección / comando | Para qué |
|---|---|---|
| Navegador normal | `http://localhost:5173/login` | Personal (se cambia de usuario cerrando sesión) |
| Navegador **incógnito** | `http://localhost:5173/empresa/login` | Portal de la empresa |
| Otro navegador o perfil | `http://127.0.0.1:8080/flowable-ui` | Flowable UI: Task app y Admin |
| App de escritorio | `./simulador-rest/ejecutar.sh` | Comunicaciones de la empresa (API REST) |
| App de escritorio | `./app-materiales/ejecutar.sh` | Materiales (API Java) y §4.3 |
| Terminal | en la raíz del repo | `estado`, `timer`, `informe` |

> 💡 Flowable UI va en **`127.0.0.1`** y la web en **`localhost`**. Así los navegadores las tratan como sitios distintos y no se mezclan las cookies de sesión (`JSESSIONID`) de las dos aplicaciones.

Todas las claves son `1234`.

---

## 4. Guion en vivo

### Bloque 1: Solicitud y regla de negocio (escenarios 9 y 5) · ~3 min

**Escenario 9: registro desde la web**
1. Incógnito → `http://localhost:5173/solicitar`: empresa **Transportes Córdoba SA**, identificador **`transportes-cba`**, temática y descripción a gusto.
2. Mostrar en Flowable UI (Admin → Process instances) la instancia nueva con `businessKey = curso:<id>` y la variable `cantCursos = 4`.
3. Web como `Jefe-Administrativo`: aparece **Registrar y elaborar propuesta**.

**Escenario 5: límite de cursos**
4. Crear otra solicitud: **Constructora Centro**.
5. `python3 demo/preparar_escenarios.py estado` → *"5 activas → NO_VIABLE"*. En Flowable (historial) la instancia nueva ya terminó: `cantCursos = 5`, `resultadoViabilidad = false`, y pasó por `notificar_decision`. En la BD el curso queda `NO_VIABLE`, lo escribió `FinProcesoListener`.

> Qué decir: la regla está en `reglaViabilidad.dmn` (hit policy FIRST: `< 5` da `true`, `>= 5` da `false`). El backend calcula `cantCursos` contando las instancias activas antes de iniciar la nueva.

### Bloque 2: Negociación (escenarios 2 y 3) con la instancia A · ~5 min

**Escenario 2: modificaciones**
1. Web (`Jefe-Administrativo`): completar la propuesta v1 (programa, presupuesto y fechas) → se genera el PDF.
2. Simulador → seleccionar Transportes Córdoba → **Ver propuesta / Abrir PDF** → **Solicitar modificaciones** con un texto, por ejemplo "bajar el presupuesto".
3. Web: aparece **Revisar propuesta**, con las observaciones y la v1 → cargar los cambios → se genera la **v2** sin pisar la v1. `recordatorios` vuelve a 0.

**Escenario 3: rechazo**
4. Simulador → **Rechazar propuesta** (sobre la v2).
5. Web: **Archivar solicitud**, con motivo *RECHAZADA* → completar → la instancia termina y el curso queda **RECHAZADO**.

### Bloque 3: Falta de respuesta (escenario 4) con la instancia B · ~2 min

1. Mostrar en Flowable Admin el timer `evento-5-dias` de B (`P5D`).
2. En la terminal:
   ```bash
   python3 demo/preparar_escenarios.py timer B   # recordatorios = 1
   python3 demo/preparar_escenarios.py timer B   # recordatorios = 2
   python3 demo/preparar_escenarios.py timer B   # 3er vencimiento → archivar_solicitud
   ```
3. Web (`Jefe-Administrativo`): **Archivar solicitud**, motivo *SIN_RESPUESTA* → completar → curso **CERRADO_SIN_RESPUESTA**.

> Qué decir: el comando hace lo mismo que la colección Postman de etapa 10. Toma el *timer job* y lo ejecuta ahora (`POST management/timer-jobs/{id}` con `{"action":"move"}`) en vez de esperar 5 días. El modelo no cambia: sigue siendo `P5D`.

### Bloque 4: Falta de aulas (escenario 7) con la instancia D · ~2 min

1. Web como `empleado-administrativo` → **Disponibles** → **Evaluar recursos** (Agro Pampa) → **Reclamar**.
2. Marcar **aula física** (y, si quieren, aula virtual) → completar.
3. `reservar_aula_fisica` simula el web service: `aulaDisponible = false`. La compuerta *¿Hay aula?* va a la rama de excepción: compensaciones, `marcar_sin_aulas` y fin *terminate*. Después pasa por `notificarCierre` y el curso queda **CANCELADO**.
4. Mostrarlo en el historial de Flowable. También se puede ver la línea del script en el log: `docker logs flowable-persistente 2>&1 | grep "Reservar aula"`.

> Qué decir: la falta de aulas se fuerza con la variable `simularSinAulas`, que el script dejó en `'true'`. Como el aula física se reserva en la misma transacción en que se completa *Evaluar recursos*, todavía no hay otros recursos completados, así que los eventos de compensación de esa rama no tienen nada que deshacer. El terminate corta el resto de las ramas pendientes. Para ver la compensación con recursos ya gestionados está el escenario 6.

### Bloque 5: Cancelación con compensaciones (escenario 6) con la instancia C · ~3 min

1. Mostrar en Flowable que C tiene **aula física reservada** (`aulaGestionada = true`) y **equipamiento contratado** (completados), y que la nómina y los materiales siguen pendientes.
2. Simulador → Metalúrgica Río Cuarto → **Cancelar curso**.
3. Se dispara el subproceso por evento `sp_cancelacion` (interrumpiente):
   - `liberar_aula` se ejecuta sola. Se ve en `docker logs flowable-persistente 2>&1 | grep "Liberar aula"`.
   - En la Task app (`empleado-administrativo`) aparece **Liberar equipamiento** (compensación) → reclamar y completar.
   - Después aparece **Confirmar cancelación** → reclamar y completar.
4. El curso queda **CANCELADO**.

> Qué decir: solo se compensa lo que estaba completado. El aula virtual no se pidió, así que no aparece `liberar_aula_virtual`. Las ramas pendientes (nómina y materiales) se interrumpen.

### Bloque 6: Camino completo (escenarios 10, 8, 11 y 1) con la instancia E · ~8 min

**Escenario 10: acceso de la empresa**
1. Incógnito → `/empresa/login` con **`software-andino` / `1234`**. La cuenta se habilitó al aceptar la propuesta.
2. La empresa ve **solo su curso**, en estado *En preparación*, con las actividades paralelas activas.

**Escenario 8: asignación, reclamo y completado**
3. Web: `confeccionar_lista` ya aparece en **Asignadas** de quien eligió `AsignarListaListener` (el script lo muestra en `estado`; normalmente es `Jefe-Administrativo`). **No hace falta reclamar** → confirmar la lista y completar.
4. Comparar con las tareas de grupo: aparecen en **Disponibles** y **hay que reclamarlas** (lo que pasa en el paso siguiente).

**Escenario 11: materiales por API Java**
5. `app-materiales` → login `empleado-administrativo` → **Disponibles** → `preparar_materiales` (Software Andino) → **Reclamar** → **Completar**.
6. Ya que están en la app: botón **«Análisis histórico (§4.3)»** para mostrar las 3 consultas con `HistoryService`.
7. (El simulador ya se usó en los bloques 2, 5 y 6, así que la API REST también queda mostrada).

**Escenario 1: dictado y finalización con liberación de recursos**
8. Con las tres ramas completas, el subproceso termina y `enviar_confirmacion` deja la tarea **Definir docente** para `Jefe-Docente` → web → elegir **`docente-1`**.
9. El timer `fecha_programa` dispara solo, porque la `fechaCurso` ya pasó (puede tardar unos segundos). Web como **`docente-1`**: **Desarrollar curso** → finalizar dictado.
10. Informe:
    ```bash
    python3 demo/preparar_escenarios.py informe E
    ```
    (ver la nota ⚠️ más abajo).
11. Web (`Jefe-Administrativo`): **Archivar expediente**. Ahí se puede abrir el PDF del informe → completar.
12. Se dispara la compensación de `preparar_curso`: `liberar_aula` sola y **Liberar aula virtual** en la Task app (`empleado-administrativo`) → completar.
13. El curso queda **FINALIZADO**. En el portal de la empresa (incógnito, refrescar) también se ve finalizado.

> ⚠️ **`elaborar_informe` no tiene formulario en el modelo desplegado** (el `.bar` no incluye forms). Si se completa desde la Task app, no se carga `informeResumen` y el PDF del informe queda "no disponible". Por eso el comando `informe` la completa por la API REST con ese dato. Si el profe pregunta: *"el formulario de Flowable UI no viajó en la exportación del `.bar`; para la demo la completamos por la API REST con la misma variable"*. Mejor todavía: agréguenle un formulario con el campo `informeResumen` en el Modeler y vuelvan a desplegar con `desplegar.sh`.

### Cierre · ~1 min

```bash
python3 demo/preparar_escenarios.py estado   # 0 activas
```

Resultado: un curso NO_VIABLE, uno RECHAZADO, uno CERRADO_SIN_RESPUESTA, dos CANCELADOS (cancelación y falta de aulas) y uno FINALIZADO. Son los 6 finales posibles del proceso.

---

## 5. Si algo sale mal

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| La solicitud A da NO_VIABLE | Hay más de 4 instancias activas | `estado`. Si no queda otra, `restaurar` |
| 503 al completar recursos | El modelo se publicó desde el Modeler (faltan asociaciones) | `bash motor-flowable/modelos/desplegar.sh` y volver a preparar |
| El timer no avanza | El async executor de Flowable tarda | Esperar unos segundos y correr `estado` |
| `fecha_programa` no dispara | Igual que arriba | Esperar o mostrar el job en Flowable Admin |
| La web pide login otra vez | Se reinició el backend (las sesiones viven en memoria) | Volver a entrar |
| Se rompió una instancia en plena demo | — | `python3 demo/preparar_escenarios.py restaurar` y seguir desde ese bloque |
| El simulador no habilita un botón | La comunicación no es válida en ese estado (es lo esperado) | Explicarlo: el backend responde 409 `ESTADO_INCOMPATIBLE` |

---

## 6. Referencia rápida del script

| Comando | Qué hace |
|---|---|
| `preparar [--limpiar]` | Crea y avanza B, C, D y E (con `--limpiar`, borra antes lo que haya) |
| `estado` | Instancias activas, tareas pendientes, esperas y logins de empresa |
| `timer B` | Dispara el timer de 5 días de B (o de un `cursoId` o `processInstanceId`) |
| `informe E ["texto"]` | Completa `elaborar_informe` cargando `informeResumen` |
| `backup [nombre]` | Respalda las bases `flowable` e `institucion` y los PDFs |
| `restaurar [nombre]` | Vuelve exactamente al estado del backup |
| `limpiar [--si]` | Borra las instancias activas y vacía la BD `institucion` |

Lee la configuración del `.env` de la raíz (`FLOWABLE_REST_USUARIO`, `FLOWABLE_REST_CLAVE`, `POSTGRES_USER`, etc.). Si cambiaron puertos o nombres, se pueden sobreescribir con `DEMO_BACKEND_URL`, `DEMO_FLOWABLE_URL` y `DEMO_PG_CONTENEDOR`.
