# Instrucciones rápidas — Organización de Cursos (GyTPN)

Guía rápida para levantar la infraestructura, las aplicaciones del proyecto y consultar los accesos y claves.

## ¿Qué incluye el proyecto?

Implementación en **Flowable 6.8** del proceso de organización de cursos de capacitación para empresas:
- **Motor Flowable + extensiones:** modelo BPMN, regla DMN (`reglaViabilidad`), listeners Java y usuarios/grupos IDM.
- **Backend REST + Sitio Web:** API Spring Boot con base PostgreSQL (`institucion`) y aplicación web React para solicitudes públicas, portal de empresas y bandejas del personal.
- **`app-materiales`:** aplicación de escritorio Java (API Java de Flowable) para preparar materiales y ejecutar las 3 consultas históricas.
- **`simulador-rest`:** aplicación de escritorio Java (API REST) para simular las comunicaciones de la empresa.
- **`mineria-procesos`:** scripts en Python con PM4Py para exportar el historial y generar el análisis de la negociación.

> Para ver el detalle completo, arquitectura, variables y los 11 escenarios de prueba paso a paso, consultar:
> - [`README.md`](README.md) — guía completa de instalación y uso
> - [`docs/INFORME_ENTREGA.md`](docs/INFORME_ENTREGA.md) — arquitectura, variables, listeners y escenarios de demostración
> - [`docs/BACKEND.md`](docs/BACKEND.md) — endpoints REST y colecciones de Postman
> - [`mineria-procesos/README.md`](mineria-procesos/README.md) — análisis PM4Py y resultados

---

## Paso 1 — Levantar los contenedores y preparar Flowable

1. **Crear el `.env`** en la raíz del proyecto (si todavía no existe):
   ```bash
   cp env.example .env
   ```
   *(Verificar/ajustar las claves en `.env`, por ejemplo `POSTGRES_PASSWORD`).*

2. **Levantar Docker** (PostgreSQL, Flowable, Backend, Sitio Web y pgAdmin):
   ```bash
   docker compose up -d --build
   ```
   Esperar ~40–60 segundos a que Flowable termine de iniciar (`docker logs -f flowable-persistente` hasta ver `Started FlowableUiApplication`).

3. **Inicializar usuarios, listeners y desplegar el modelo BPMN + DMN** (en orden):
   ```bash
   bash motor-flowable/idm/crear-usuarios.sh
   docker cp motor-flowable/extensiones/flowable-extensiones.jar flowable-persistente:/app/WEB-INF/lib/flowable-extensiones.jar
   docker restart flowable-persistente
   ```
   Esperar a que `flowable-persistente` termine de reiniciar (~40 segundos) y desplegar el paquete `.bar`:
   ```bash
   bash motor-flowable/modelos/desplegar.sh
   ```

---

## Paso 2 — Levantar las aplicaciones del proyecto

- **Sitio Web y Backend REST:** ya quedan corriendo automáticamente con Docker en `http://localhost:5173` (Web) y `http://localhost:8081` (Backend).

- **App de Preparación de Materiales y Consultas Históricas (`app-materiales`, API Java):**
  ```bash
  mvn -f app-materiales/pom.xml compile
  ./app-materiales/ejecutar.sh
  ```
  *(Desde la barra superior con el botón **«Análisis histórico (§4.3)»** o ejecutando `./app-materiales/ejecutar.sh --analisis` se ven las 3 consultas históricas).*

- **Simulador de Comunicaciones de la Empresa (`simulador-rest`, API REST):**
  ```bash
  mvn -f simulador-rest/pom.xml compile
  ./simulador-rest/ejecutar.sh
  ```

- **Minería de Procesos (`mineria-procesos`, PM4Py):**
  ```bash
  python3 mineria-procesos/exportar_history.py
  python3 mineria-procesos/analizar.py
  ```

---

## Accesos y Claves

### Servicios y URLs

| Servicio | URL / Puerto |
|---|---|
| **Sitio Web** | http://localhost:5173 |
| **Backend REST** | http://localhost:8081/api/v1/estado |
| **Flowable UI** | http://localhost:8080/flowable-ui/ |
| **PostgreSQL** (`flowable` e `institucion`) | `localhost:5433` |
| **pgAdmin 4** | http://localhost:5050 |

### Usuarios y Contraseñas

| Sistema / Rol | Usuario | Contraseña |
|---|---|---|
| **Flowable UI / API REST** | `admin` | `test` |
| **Personal (Web, Flowable y `app-materiales`)** | `Jefe-Administrativo`, `empleado-administrativo`, `Jefe-Docente`, `docente-1`, `docente-2` | `1234` |
| **Empresas (Portal Web)** | Cuenta habilitada al aceptar propuesta (o `empresa-demo` si `CURSOS_DATOS_DEMO=true`) | `1234` |
| **PostgreSQL** | `flowable` | `flow10` *(o la definida en `.env`)* |
| **pgAdmin 4** | `admin@example.com` | `lauti` |
