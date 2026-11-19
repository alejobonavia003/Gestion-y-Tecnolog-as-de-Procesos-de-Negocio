# Gestión y Tecnologías de Procesos de Negocio — Organización de Cursos

Proyecto integrador (GyTPN, UNRC): implementación en **Flowable 6.8** del proceso de **organización de cursos de capacitación para empresas**, junto con su servicio REST y base de datos institucional, sitio web, aplicaciones auxiliares de escritorio (API Java y API REST), consultas históricas y análisis de minería de procesos con PM4Py.

**Integrantes:** Alejo, Lauty y Luka.

---

## 1. Documentación de Entrega (§5 de la consigna)

Toda la documentación exigida para la entrega y defensa está organizada en:

| Documento | Contenido principal |
|---|---|
| **[README.md](README.md) (este archivo)** | **Instrucciones completas para configurar usuarios y grupos, desplegar el motor y configurar/ejecutar las 3 aplicaciones externas y los análisis.** |
| **[docs/INFORME_ENTREGA.md](docs/INFORME_ENTREGA.md)** | **Informe breve de entrega:** arquitectura, diccionario de variables del proceso, regla de negocio DMN, listeners (`AsignarListaListener` y `FinProcesoListener`), decisiones de implementación y **guía paso a paso de los 11 escenarios de demostración**. |
| **[mineria-procesos/README.md](mineria-procesos/README.md)** | **Minería de procesos (§4.4):** scripts `exportar_history.py` y `analizar.py` (PM4Py), CSV utilizado, grafo DFG, los 3 recorridos más frecuentes y conclusión comparativa con el BPMN. |
| **[docs/BACKEND.md](docs/BACKEND.md)** | Contrato detallado de endpoints REST (`/api/v1`), arquitectura del backend y pruebas con Postman. |
| **[docs/MODELO_Y_INTEGRACION.md](docs/MODELO_Y_INTEGRACION.md)** | Acuerdo de integración, detalle del modelo BPMN y manual de la app de materiales. |

---

## 2. Estructura del repositorio y estado de entregables

| Ruta | Entregable de la consigna | Estado |
|---|---|---|
| [`motor-flowable/modelos/`](motor-flowable/modelos/) | Modelo BPMN ejecutable (`institucion-de-capacitacion.bpmn20.xml`), regla DMN (`reglaViabilidad.dmn`), paquete `.bar` (`Organizacion_de_cursos.bar`) y script `desplegar.sh`. | ✅ Completo |
| [`motor-flowable/extensiones/`](motor-flowable/extensiones/) | Componentes incorporados al motor: `AsignarListaListener` y `FinProcesoListener` (`flowable-extensiones.jar`). | ✅ Completo |
| [`motor-flowable/idm/`](motor-flowable/idm/) | Script `crear-usuarios.sh` para configurar usuarios, grupos y permisos REST en Flowable. | ✅ Completo |
| [`backend/`](backend/) | Servicio REST (Spring Boot 3.5, Java 21) y base de datos institucional PostgreSQL (`institucion`), generación de PDFs con OpenPDF y protección CSRF. | ✅ Completo |
| [`web/`](web/) | **Aplicación Externa 1 (§4.1):** Sitio web (React + TypeScript + Vite) con formulario público de solicitudes, portal de empresas y bandeja de tareas del personal. | ✅ Completo |
| [`app-materiales/`](app-materiales/) | **Aplicación Externa 2 (§4.2.1 y §4.3):** App de escritorio Java Swing con la **API Java de Flowable** para *Preparar materiales* y las **3 consultas históricas** (`HistoryService`). | ✅ Completo |
| [`simulador-rest/`](simulador-rest/) | **Aplicación Externa 3 (§4.2.2):** App de escritorio Java Swing que simula las 5 comunicaciones de la empresa mediante la **API REST**. | ✅ Completo |
| [`mineria-procesos/`](mineria-procesos/) | **Minería de Procesos (§4.4):** `exportar_history.py`, `analizar.py`, `negociacion_history.csv`, grafo del comportamiento observado y conclusión. | ✅ Completo |

---

## 3. Instrucciones para configurar y ejecutar la solución

### Requisitos previos
- **Docker** con **Docker Compose** (levanta PostgreSQL, Flowable UI 6.8.0, el Backend, el Sitio Web y pgAdmin).
- **JDK 21** y **Maven** (para compilar y ejecutar las aplicaciones de escritorio `app-materiales` y `simulador-rest`, o correr el backend en modo desarrollo).
- **Python 3** (con `pm4py`, `pandas`, `requests` y `graphviz` en un entorno virtual `.venv` si se desea regenerar el análisis con PM4Py).

---

### Paso 1: Configurar el archivo `.env` y levantar los contenedores

Desde la raíz del repositorio, crear el archivo `.env` a partir de la plantilla (una sola vez por máquina):

```bash
cp env.example .env
```

> **Importante:** Conservar el formato `CLAVE=valor` **sin espacios alrededor del `=`**, ya que tanto Docker Compose como los scripts de bash leen este archivo.

| Variable en `.env` | Descripción y valor por defecto |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base y credenciales de PostgreSQL (`flowable` / `flowable` / `flow10`). |
| `INSTITUCION_DB_NAME` | Nombre de la base de datos institucional (`institucion`, creada automáticamente por el servicio `institucion-db`). |
| `FLOWABLE_REST_USUARIO`, `FLOWABLE_REST_CLAVE` | Usuario técnico con acceso a la API REST de Flowable (`admin` / `test`). |
| `FLOWABLE_PROCESO_CLAVE` | Clave del proceso BPMN (`process`). |
| `CURSOS_DATOS_DEMO` | `true` para crear automáticamente la cuenta de prueba `empresa-demo` / `1234`. |

Levantar toda la infraestructura con Docker Compose:

```bash
docker compose up -d --build
docker compose ps -a
```

Esperar ~40-60 segundos hasta que Flowable termine de iniciar (`docker logs -f flowable-persistente` hasta ver `Started FlowableUiApplication`). El servicio `institucion-db` crea la base `institucion` y finaliza con `Exited (0)`.

---

### Paso 2: Configurar usuarios y grupos en Flowable (§3.3)

Con el contenedor de Flowable levantado, ejecutar desde la raíz del repositorio:

```bash
bash motor-flowable/idm/crear-usuarios.sh
```

Este script es idempotente y crea en Flowable IDM los grupos y usuarios requeridos por la consigna, otorgándoles el privilegio `access-rest-api`:

| Usuario (ID en Flowable) | Contraseña | Grupo en Flowable | Rol / Responsabilidad |
|---|---|---|---|
| `Jefe-Administrativo` | `1234` | `Equipo-Administrativo` | Jefe administrativo (propuestas, revisiones, archivo y cierre de expediente). |
| `empleado-administrativo` | `1234` | `Equipo-Administrativo` | Administrativo (lista definitiva, materiales, evaluación y gestión/liberación de recursos). |
| `Jefe-Docente` | `1234` | `Equipo-Docente` | Jefe del equipo docente (definir docente, elaborar informe y puede dictar cursos). |
| `docente-1` | `1234` | `Equipo-Docente` | Docente (desarrollar curso cuando es seleccionado). |
| `docente-2` | `1234` | `Equipo-Docente` | Docente (desarrollar curso cuando es seleccionado). |

---

### Paso 3: Instalar los Listeners en el motor y desplegar el modelo BPMN + DMN

1. **Copiar el JAR de listeners (`AsignarListaListener` y `FinProcesoListener`) dentro del contenedor de Flowable y reiniciarlo:**
   ```bash
   docker cp motor-flowable/extensiones/flowable-extensiones.jar flowable-persistente:/app/WEB-INF/lib/flowable-extensiones.jar
   docker restart flowable-persistente
   ```
   *(Si se modifica el código de `motor-flowable/extensiones/`, recompilar antes con `mvn -f motor-flowable/extensiones/pom.xml clean package` y copiar `target/flowable-extensiones.jar`).*

2. **Desplegar el proceso BPMN y la regla DMN mediante `desplegar.sh`:**
   *(Esperar a que `flowable-persistente` termine de reiniciar y ejecutar):*
   ```bash
   bash motor-flowable/modelos/desplegar.sh
   ```
   > **Nota:** Usar siempre `desplegar.sh` (que despliega `Organizacion_de_cursos.bar` y verifica las 3 etiquetas `<association>` de compensación). **No usar el botón *Publish* del Modeler 6.8.0**, ya que un bug del Modeler con *Pools* convierte las asociaciones de compensación en `sequenceFlow`.

---

### Paso 4: Configurar y ejecutar las 3 aplicaciones externas (§4.1 y §4.2)

#### 4.1 Sitio Web (`web/`) y Servicio REST (`backend/`)
Si se ejecutó `docker compose up -d --build`, **ambos servicios ya están corriendo en Docker**:
- **Sitio Web:** [http://localhost:5173](http://localhost:5173)
  -Formulario público de solicitud: `http://localhost:5173/solicitar`
  -Ingreso del personal (credenciales de Flowable): `http://localhost:5173/personal/ingresar`
  -Portal de empresas: `http://localhost:5173/empresa/ingresar` (se habilita automáticamente la cuenta de la empresa con clave `1234` al aceptar una propuesta, o `empresa-demo` / `1234` si `CURSOS_DATOS_DEMO=true`).
- **Servicio REST (Backend):** [http://localhost:8081/api/v1/estado](http://localhost:8081/api/v1/estado)

*(Opcional — Ejecución fuera de Docker para desarrollo):*
- Backend con Maven: `docker compose stop backend && cd backend && mvn spring-boot:run`
- Web con Node.js: `cd web && npm ci && npm run dev`

#### 4.2 App de preparación de materiales por API Java (`app-materiales/`, §4.2.1)
Se conecta **directamente al PostgreSQL del motor (`localhost:5433/flowable`) usando la API Java de Flowable** (`ProcessEngine`, `IdentityService`, `TaskService`). Lee automáticamente el `.env` de la raíz.

Para compilarla y abrir la ventana gráfica:

```bash
mvn -f app-materiales/pom.xml compile
./app-materiales/ejecutar.sh
```

- Ingresar con `Jefe-Administrativo` o `empleado-administrativo` (clave `1234`).
- Permite listar tareas `preparar_materiales` en las bandejas **Asignadas** y **Disponibles para reclamar**, ver el detalle del curso, reclamar tareas libres (`claim`) y completarlas (`complete`).

#### 4.3 Simulador de comunicaciones de la empresa por API REST (`simulador-rest/`, §4.2.2)
Consume los endpoints REST `/api/v1/simulacion/solicitudes` del backend. Lee automáticamente el `.env` de la raíz.

Para compilarlo y abrir la ventana gráfica:

```bash
mvn -f simulador-rest/pom.xml compile
./simulador-rest/ejecutar.sh
```

- Muestra todas las solicitudes y habilita **únicamente** los botones de las comunicaciones válidas según el estado actual de la instancia en Flowable:
  1. **Ver propuesta recibida** / **Abrir PDF de propuesta**
  2. **Aceptar propuesta** (seleccionando o ingresando `fechaCurso` ISO-8601 y habilitando la cuenta de la empresa)
  3. **Solicitar modificaciones** (enviando `observacionesEmpresa`)
  4. **Rechazar propuesta**
  5. **Enviar nómina** (lista de participantes durante la preparación)
  6. **Cancelar curso** (interrumpe la preparación y dispara las compensaciones)

---

### Paso 5: Ejecutar el análisis de ejecuciones (§4.3) y la minería de procesos (§4.4)

#### 5.1 Consultas históricas sobre Flowable (§4.3)
Implementadas con `HistoryService` en [`AnalisisHistoricoService.java`](app-materiales/src/main/java/ar/edu/cursos/materiales/AnalisisHistoricoService.java) dentro de `app-materiales`. Pueden ejecutarse de dos formas:
1. **En la interfaz gráfica de `app-materiales`:** Pulsando el botón **«Análisis histórico (§4.3)»** en la barra superior para abrir la ventana con las 3 tablas (`4.3.1 Resultados de las solicitudes`, `4.3.2 Análisis de la preparación paralela` y `4.3.3 Distribución de tareas`).
2. **Por consola:**
   ```bash
   ./app-materiales/ejecutar.sh --analisis
   ```

#### 5.2 Minería de procesos con PM4Py (§4.4)
Ubicada en [`mineria-procesos/`](mineria-procesos/). Para exportar el historial de la etapa de negociación y ejecutar el análisis:

```bash
python3 mineria-procesos/exportar_history.py
python3 mineria-procesos/analizar.py
```

*(Para regenerar con el lote histórico de 25 instancias de referencia sin requerir Flowable activo: `python3 mineria-procesos/exportar_history.py --ejemplo && python3 mineria-procesos/analizar.py`).*
El detalle de los 3 recorridos más frecuentes, los gráficos generados y la conclusión comparativa se encuentran en **[mineria-procesos/README.md](mineria-procesos/README.md)**.

---

## 4. Resumen de accesos y puertos locales

| Servicio | URL / Puerto | Credenciales |
|---|---|---|
| **Sitio Web** | [http://localhost:5173](http://localhost:5173) | Personal: usuarios de Flowable (`1234`) · Empresa: usuario habilitado al aceptar propuesta (`1234`) |
| **Backend REST** | [http://localhost:8081/api/v1/estado](http://localhost:8081/api/v1/estado) | Sesión por cookie HttpOnly + header `X-XSRF-TOKEN` |
| **Flowable UI (Modeler, Task, Admin, IDM)** | [http://localhost:8080/flowable-ui/](http://localhost:8080/flowable-ui/) | `admin` / `test` (o cualquiera de los 5 usuarios del personal con clave `1234`) |
| **API REST nativa de Flowable** | `http://localhost:8080/flowable-ui/process-api` | Basic Auth (`FLOWABLE_REST_USUARIO` / `FLOWABLE_REST_CLAVE`) |
| **PostgreSQL (bases `flowable` e `institucion`)** | `localhost:5433` (interno en Docker: `postgres:5432`) | Definidas en `.env` (`POSTGRES_USER` / `POSTGRES_PASSWORD`) |
| **pgAdmin 4** | [http://localhost:5050](http://localhost:5050) | `admin@example.com` / `lauti` |
