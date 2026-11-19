# Informe de Implementación y Entrega

**Asignatura:** Gestión y Tecnologías de Procesos de Negocio (UNRC)  
**Proyecto:** Proceso de organización de cursos de capacitación para empresas (Flowable 6.8)  
**Integrantes:** Alejo, Lauty y Luka

---

## 1. Arquitectura general de la solución

La solución separa el motor de procesos (**Flowable 6.8**) y su base de datos interna (`flowable`) de la base de datos propia de la institución (`institucion`) y las aplicaciones externas:

```mermaid
flowchart LR
  W["Sitio Web (React / Vite :5173)"] -->|REST /api/v1| B["Backend Institucional (Spring Boot :8081)"]
  S["Simulador REST (Java Swing)"] -->|REST /api/v1/simulacion| B
  B -->|API REST Flowable| F["Motor Flowable 6.8 (:8080)"]
  F -->|FinProcesoListener JDBC| I[("BD Institucional (PostgreSQL)")]
  B -->|JPA / Hibernate| I
  F --> D[("BD Flowable (PostgreSQL)")]
  M["App de Materiales y §4.3 (Java Swing)"] -->|API Java Flowable (TaskService / HistoryService)| D
  P["Minería de Procesos §4.4 (PM4Py)"] -->|API REST Historial| F
```

| Componente | Ubicación | Tecnología y rol |
|---|---|---|
| **Modelo BPMN y Regla DMN** | [`motor-flowable/modelos/`](../motor-flowable/modelos/) | Proceso ejecutable `process` ([`institucion-de-capacitacion.bpmn20.xml`](../motor-flowable/modelos/institucion-de-capacitacion.bpmn20.xml)), tabla DMN [`reglaViabilidad.dmn`](../motor-flowable/modelos/reglaViabilidad.dmn) y paquete desplegable [`Organizacion_de_cursos.bar`](../motor-flowable/modelos/Organizacion_de_cursos.bar). |
| **Listeners del motor** | [`motor-flowable/extensiones/`](../motor-flowable/extensiones/) | Módulo Java 11 empaquetado en `flowable-extensiones.jar` con `AsignarListaListener` y `FinProcesoListener`, instalado en `/app/WEB-INF/lib/` del contenedor Flowable. |
| **Usuarios y grupos (IDM)** | [`motor-flowable/idm/crear-usuarios.sh`](../motor-flowable/idm/crear-usuarios.sh) | Script idempotente que aprovisiona en Flowable IDM los 2 grupos y los 5 usuarios del personal con permiso `access-rest-api`. |
| **Servicio REST y BD institucional** | [`backend/`](../backend/) | API REST en Spring Boot 3.5 (Java 21) que administra la base PostgreSQL `institucion`, autentica al personal contra Flowable IDM y a las empresas con BCrypt, genera los PDFs con OpenPDF y se comunica con Flowable por su API REST. |
| **Aplicación Externa 1: Sitio Web** | [`web/`](../web/) | SPA en React + TypeScript + Vite con sección pública de solicitud de cursos, portal de seguimiento para empresas y bandeja de tareas para Administración y Equipo Docente. |
| **Aplicación Externa 2: App de Materiales y Consultas Históricas (§4.2.1 y §4.3)** | [`app-materiales/`](../app-materiales/) | Aplicación de escritorio Java Swing conectada a la BD PostgreSQL del motor mediante la **API Java de Flowable** (`ProcessEngine`, `IdentityService`, `TaskService`, `HistoryService`). |
| **Aplicación Externa 3: Simulador de Comunicaciones (§4.2.2)** | [`simulador-rest/`](../simulador-rest/) | Aplicación de escritorio Java Swing que consume la API REST para simular las 5 comunicaciones de la empresa (`ACEPTACION`, `RECHAZO`, `MODIFICACION`, `NOMINA`, `CANCELACION`). |
| **Minería de Procesos (§4.4)** | [`mineria-procesos/`](../mineria-procesos/) | Scripts [`exportar_history.py`](../mineria-procesos/exportar_history.py) y [`analizar.py`](../mineria-procesos/analizar.py) con **PM4Py**, CSV de eventos históricos, grafo DFG y conclusión comparativa. |

---

## 2. Variables del proceso BPMN

Todas las variables tienen alcance de instancia de proceso en Flowable y mantienen tipos simples (`string`, `integer`, `boolean`, `json`) para garantizar interoperabilidad entre la API REST, la API Java, los formularios de Flowable UI y la tabla de decisión DMN.

| Variable | Tipo | Quién la define / actualiza | Propósito en el proceso |
|---|---|---|---|
| `cursoId` | `string` (UUID) | Backend al iniciar la instancia | Identificador del curso en la BD `institucion`. Además se fija `businessKey = curso:<cursoId>`. |
| `empresaNombre`, `contactoNombre`, `contactoEmail`, `tematica`, `descripcion`, `cantidadParticipantes`, `fechasPreferidas` | `string` / `integer` | Backend al iniciar la instancia | Datos descriptivos de la solicitud disponibles para consulta desde Flowable UI y desde la App Java de Materiales. |
| `cantCursos` | `integer` | Backend al iniciar (`GET runtime/process-instances` activas) | Cantidad de otras instancias activas del proceso `process` (en todas sus versiones desplegadas). Entrada de la tabla DMN `reglaViabilidad`. |
| `resultadoViabilidad` | `boolean` | Decision Task `evaluar_viabilidad` (`reglaViabilidad.dmn`) | Salida de la regla de negocio: `true` si `cantCursos < 5`, `false` si `cantCursos >= 5`. Dirige la compuerta `gw_es_viable`. |
| `propuestaVersion` | `integer` | Backend al completar `registrar_elaborar_propuesta` o `revisar_propuesta` | Número de versión correlativo (`1, 2, ...`) de la propuesta enviada a la empresa. |
| `recordatorios` | `integer` | Script `enviar_propuesta` (reinicia en `0`) y `enviar_recordatorios` (`+1`) | Contador de recordatorios enviados tras cada vencimiento del temporizador `evento-5-dias` (`P5D`). Cuando `recordatorios >= 2` en `gw_recordatorios`, deriva a `archivar_solicitud`. |
| `respuestaEmpresa` | `string` | Backend al entregar mensaje desde el Simulador REST | Resultado de la respuesta de la empresa a la propuesta: `ACEPTADA`, `RECHAZADA` o `MODIFICACIONES`. |
| `observacionesEmpresa` | `string` | Backend al entregar el mensaje `modificaciones` | Observaciones enviadas por la empresa para que el Jefe Administrativo revise la propuesta en `revisar_propuesta`. |
| `fechaCurso` | `string` (ISO-8601) | Backend al entregar el mensaje `aceptacion` | Fecha y hora acordadas de inicio del curso (ej. `2026-12-01T09:00:00-03:00`). Utilizada dinámicamente por el temporizador `fecha_programa` (`<timeDate>${fechaCurso}</timeDate>`). |
| `participantes` | `json` (lista) | Backend en recepción de nómina (`recibir_nomina`) y administrativo en `confeccionar_lista` | Nómina enviada por la empresa y lista definitiva confeccionada por Administración (`[{"nombre": "..."}]`). |
| `requiereAula`, `requierePlataforma`, `requiereEquipamiento` | `boolean` | Administrativo al completar `evaluar_recursos` | Selección de recursos (al menos uno en `true`). Controlan las condiciones de las tres ramas del gateway inclusivo `gw_recursos`. |
| `aulaDisponible`, `aulaGestionada`, `aulaAsignada`, `simularSinAulas` | `boolean` / `string` | Script `reservar_aula_fisica` y compensador `liberar_aula` | Estado de la reserva automática del aula física (web service simulado). Si `simularSinAulas == 'true'`, `aulaDisponible` queda en `false` y activa la rama de excepción por falta de aulas. |
| `cursoCancelado` | `boolean` | Inicio de `preparar_curso` (`false`), `marcar_cancelado` (`true`) y `marcar_sin_aulas` (`true`) | Indica a la compuerta `gw_curso_cancelado` al salir de `preparar_curso` si la preparación concluyó normalmente o fue interrumpida por cancelación/falta de aulas. |
| `docenteId` | `string` | `Jefe-Docente` al completar `definir_docente` | ID del docente elegido (`Jefe-Docente`, `docente-1` o `docente-2`). Asigna directamente la tarea `desarrollar_curso` (`flowable:assignee="${docenteId}"`). |
| `informeResumen` | `string` | `Jefe-Docente` en `elaborar_informe` (formulario de Flowable UI) | Texto del informe de resultados elaborado por el Jefe Docente, a partir del cual el backend genera el PDF del informe final. |
| `resultadoProceso` | `string` | Eventos/scripts de cada camino de cierre | Resultado terminal de la instancia: `FINALIZADO`, `CANCELADO`, `RECHAZADO`, `CERRADO_SIN_RESPUESTA` o `NO_VIABLE`. Leído por `FinProcesoListener`. |

---

## 3. Regla de negocio y Listeners implementados

### 3.1 Regla de Negocio DMN: Viabilidad (`reglaViabilidad.dmn`)
- **Implementación:** Tabla de decisión DMN 1.2 con política de acierto `FIRST`, invocada por la *Decision Task* `evaluar_viabilidad`.
- **Cálculo de la entrada (`cantCursos`):** Antes de iniciar una nueva instancia, `SolicitudService` consulta al motor (`GET runtime/process-instances?processDefinitionKey=process`) la cantidad de instancias activas existentes (considerando todas las versiones desplegadas del proceso y excluyendo la nueva solicitud que aún no fue iniciada).
- **Reglas:**
  - `cantCursos >= 5` → `resultadoViabilidad = false` (deriva a `notificar_decision`, fija `resultadoProceso = 'NO_VIABLE'` y termina en `fin_no_viable`).
  - `cantCursos < 5` → `resultadoViabilidad = true` (continúa a `registrar_elaborar_propuesta`).

### 3.2 Task Listener: `AsignarListaListener` (§3.4.1)
- **Clase:** [`ar.edu.cursos.motor.AsignarListaListener`](../motor-flowable/extensiones/src/main/java/ar/edu/cursos/motor/AsignarListaListener.java)
- **Asociación en el BPMN:** Evento `create` de la tarea de usuario `confeccionar_lista`.
- **Funcionamiento:**
  1. Obtiene `IdentityService` y `TaskService` del contexto activo del motor (`CommandContextUtil.getProcessEngineConfiguration()`).
  2. Lista los integrantes del grupo `Equipo-Administrativo` (`Jefe-Administrativo` y `empleado-administrativo`).
  3. Para cada administrativo, ejecuta `taskService.createTaskQuery().taskAssignee(userId).active().count()`, contando **únicamente las tareas de usuario pendientes asignadas directamente** a ese usuario (excluyendo tareas candidatas no reclamadas).
  4. Asigna la tarea (`delegateTask.setAssignee(elegido)`) a quien tenga menor cantidad de tareas asignadas. En caso de empate, desempata por orden alfabético ascendente del ID de usuario (`empleado-administrativo` antes que `Jefe-Administrativo` o viceversa según corresponda).

### 3.3 Execution Listener: `FinProcesoListener` (§3.4.2)
- **Clase:** [`ar.edu.cursos.motor.FinProcesoListener`](../motor-flowable/extensiones/src/main/java/ar/edu/cursos/motor/FinProcesoListener.java)
- **Asociación en el BPMN:** Evento `end` del proceso `<process id="process">`.
- **Funcionamiento:**
  1. Al concluir la instancia por cualquiera de sus eventos de fin (`fin_no_viable`, `fin_rechazado_o_sin_respuesta`, `fin_cancelado`, `fin_finalizado`), lee la variable `resultadoProceso` (`FINALIZADO`, `CANCELADO`, `RECHAZADO`, `CERRADO_SIN_RESPUESTA` o `NO_VIABLE`) y el identificador `cursoId` (o lo extrae del `businessKey` `curso:<uuid>`).
  2. Abre una conexión JDBC a la base de datos institucional (`institucion`) y ejecuta `UPDATE curso SET estado = ?, actualizado_en = NOW() WHERE id = CAST(? AS uuid)` (con respaldo por `process_instance_id`).

---

## 4. Decisiones de implementación relevantes

1. **Separación de bases de datos (`flowable` vs. `institucion`):**
   - Cumpliendo con §3.2 y §3.3, la base `institucion` almacena únicamente las entidades de negocio (`empresa`, `cuenta_empresa`, `curso`, `propuesta`, `comunicacion_empresa`), vinculando cada curso con su instancia en Flowable mediante `process_instance_id` y `business_key`.
   - No se duplican cuentas del personal en la BD institucional: tanto el sitio web (vía `FlowableClient`) como `app-materiales` (vía `IdentityService.checkPassword`) validan las credenciales de los 5 integrantes contra Flowable IDM.
2. **Idempotencia y tolerancia a fallos en el inicio y las comunicaciones:**
   - El alta de solicitud (`POST /api/v1/solicitudes`) persiste primero el curso en una transacción corta con su `Idempotency-Key` y hash SHA-256 antes de invocar a Flowable. Así, si la solicitud resulta `NO_VIABLE` y la instancia finaliza sincrónicamente dentro de la misma llamada, `FinProcesoListener` ya encuentra la fila del curso en la BD `institucion`.
   - Las comunicaciones del simulador (`POST /api/v1/simulacion/solicitudes/{cursoId}/comunicaciones`) registran `comunicacionId` y hash del payload para evitar entregas duplicadas de mensajes al motor.
3. **Generación y versionado de PDFs (OpenPDF):**
   - Cada vez que el Jefe Administrativo completa `registrar_elaborar_propuesta` (`v1`) o `revisar_propuesta` (`v2, v3...`), `PropuestaService` genera un documento PDF inmutable `<cursoId>-v<version>.pdf` en el volumen persistente del backend antes de completar la tarea en Flowable.
   - El PDF del informe final (`GET /api/v1/cursos/{cursoId}/informe/pdf`) se genera al vuelo con OpenPDF tomando la variable `informeResumen` cargada por el Jefe Docente en el formulario de Flowable UI (consultada incluso desde el historial del motor una vez finalizada la instancia).
4. **Compensación de recursos y reparación automática del bug del Modeler 6.8.0:**
   - Flowable Modeler 6.8.0 tiene un bug conocido (`BpmnJsonConverter`): cuando un diagrama utiliza un *Pool* (`<collaboration>`), al exportar o publicar desde la UI convierte las etiquetas `<association>` de los eventos de borde de compensación en `<sequenceFlow>`, provocando el error `Compensation activity could not be found` en tiempo de ejecución.
   - Para solucionarlo de raíz, implementamos [`corregir-asociaciones.py`](../motor-flowable/modelos/corregir-asociaciones.py) y [`desplegar.sh`](../motor-flowable/modelos/desplegar.sh), que restauran las 3 `<association>` hacia `liberar_aula`, `liberar_aula_virtual` y `liberar_equipamiento`, empaquetan el `.bar` junto con `reglaViabilidad.dmn` y verifican tras el despliegue REST que el XML activo en el motor conserve exactamente las 3 asociaciones.
5. **Interacción directa por API Java en `app-materiales` (§4.2.1 y §4.3):**
   - `app-materiales` levanta un `ProcessEngine` standalone configurado contra el PostgreSQL compartido (`DB_SCHEMA_UPDATE_FALSE` y `asyncExecutorActivate=false`, dejando los jobs/timers a cargo del contenedor principal de Flowable).
   - Opera `preparar_materiales` exclusivamente mediante `IdentityService`, `TaskService` y `RuntimeService`, e implementa en `AnalisisHistoricoService` las tres consultas históricas de la sección 4.3 mediante `HistoryService` (disponibles tanto en la ventana gráfica como por consola con `--analisis`).

---

## 5. Guía rápida para los 11 escenarios de la demostración (Defensa — §5)

| # | Escenario exigido | Cómo demostrarlo en la solución |
|---|---|---|
| **1** | **Aceptación de la propuesta y finalización normal del curso, con liberación de recursos** | Crear solicitud en la Web → elaborar propuesta v1 (`Jefe-Administrativo`) → aceptar desde `simulador-rest` con fecha pasada/cercana → en paralelo: enviar nómina (`simulador-rest`) y completar `confeccionar_lista` (Web), completar `preparar_materiales` (`app-materiales`), completar `evaluar_recursos` (Web) y tareas de recursos seleccionadas (Flowable UI) → `Jefe-Docente` define docente (Web) → el docente completa `desarrollar_curso` (Web) → `Jefe-Docente` completa `elaborar_informe` (Flowable UI) → `Jefe-Administrativo` completa `archivar_expediente` (Web) → se disparan las compensaciones de liberación y el curso pasa a `FINALIZADO`. |
| **2** | **Solicitud de modificaciones y envío de una nueva propuesta** | Con la propuesta `v1` enviada, en `simulador-rest` pulsar **«Solicitar modificaciones»** ingresando observaciones → en la Web (`Jefe-Administrativo`) aparece la tarea `Revisar propuesta` mostrando las observaciones de la empresa y la `v1` → cargar nuevos datos y enviar → se genera el PDF `v2` sin sobrescribir el `v1` y se reinicia `recordatorios = 0`. |
| **3** | **Rechazo de la propuesta** | Con la propuesta enviada, en `simulador-rest` pulsar **«Rechazar propuesta»** → en la Web (`Jefe-Administrativo`) aparece `Archivar solicitud` con motivo `RECHAZADA` → al completarla, el proceso termina y `FinProcesoListener` deja el curso en estado `RECHAZADO`. |
| **4** | **Cierre por falta de respuesta, luego de los recordatorios** | Tras enviar la propuesta, al dispararse 2 veces el timer `evento-5-dias` (`enviar_recordatorios` incrementa `recordatorios` a 1 y luego a 2), el tercer vencimiento evalúa `recordatorios >= 2` en `gw_recordatorios`, fija `resultadoProceso = 'CERRADO_SIN_RESPUESTA'` y habilita `Archivar solicitud` para `Jefe-Administrativo`. |
| **5** | **Imposibilidad de continuar por el límite de cursos en ejecución** | Con 5 instancias activas en el motor, registrar una 6.ª solicitud desde la Web → `SolicitudService` envía `cantCursos = 5` → `reglaViabilidad.dmn` devuelve `resultadoViabilidad = false` → ejecuta `notificar_decision` y cierra inmediatamente la instancia con estado `NO_VIABLE`. |
| **6** | **Cancelación durante la preparación, con recursos ya gestionados y ejecución de sus compensaciones** | Con el curso en `EN_PREPARACION` y habiendo completado al menos un recurso (ej. aula física y/o equipamiento), pulsar **«Cancelar curso»** en `simulador-rest` → el subproceso por evento `sp_cancelacion` interrumpe las ramas pendientes de `preparar_curso`, lanza las compensaciones de los recursos efectivamente completados y finaliza en `CANCELADO`. |
| **7** | **Falta de aulas disponibles y generación de la excepción correspondiente** | Cuando la variable `simularSinAulas` es `'true'` al ejecutar `reservar_aula_fisica`, el script simula que el web service no dispone de aulas (`aulaDisponible = false`), desvía por `gw_hay_aula` hacia la compensación de los otros recursos (`sa_compensar_aula_virtual`, `sa_compensar_equipamiento`), marca `cursoCancelado = true` (`marcar_sin_aulas`) y termina el subproceso en `fin_sin_aulas`, cerrando el proceso por `notificarCierre` (`CANCELADO`). |
| **8** | **Asignación, reclamo y finalización de tareas según los responsables definidos** | En la Web y en `app-materiales`: las tareas de grupo (`Equipo-Administrativo`) aparecen en la bandeja *Disponibles* y exigen **Reclamar** antes de poder completarse; las tareas exclusivas de jefe (`Jefe-Administrativo`, `Jefe-Docente`), del docente elegido (`${docenteId}`) o asignadas por `AsignarListaListener` (`confeccionar_lista`) aparecen directamente en *Asignadas* sin requerir reclamo. |
| **9** | **Registro de una solicitud desde el sitio web e inicio de su instancia** | Completar el formulario público en `http://localhost:5173/solicitar` → crea el curso en PostgreSQL (`institucion`) e inicia la instancia en Flowable con `businessKey = curso:<cursoId>`. |
| **10** | **Habilitación del acceso de la empresa y consulta exclusiva de sus cursos** | Al aceptar la propuesta desde `simulador-rest`, se crea (o reutiliza) automáticamente la cuenta de la empresa (clave demo `1234`) y el simulador informa el usuario habilitado → al ingresar en `http://localhost:5173/empresa/ingresar`, la empresa ve únicamente sus propios cursos, su estado actual y las actividades paralelas activas durante la preparación. |
| **11** | **Preparación de materiales mediante la API Java y simulación de comunicaciones mediante la API REST** | Ejecutar `./app-materiales/ejecutar.sh` (login con usuario de `Equipo-Administrativo`, reclamo y completado de `preparar_materiales` por API Java, más consultas históricas §4.3) y `./simulador-rest/ejecutar.sh` (lectura de propuesta PDF y envío de las 5 comunicaciones por API REST). |
