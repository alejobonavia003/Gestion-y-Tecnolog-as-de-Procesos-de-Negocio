# Guía paso a paso para probar todo el sistema en Linux

Esta guía detalla cómo levantar, configurar y verificar en **Linux** (Ubuntu / Debian) todo el flujo del proyecto, incluyendo las últimas correcciones de zona horaria (`app-materiales`), el script SQL histórico y el análisis de minería de procesos con **PM4Py** y **Graphviz** (`mineria-procesos/`).

> [!CAUTION]
> - **No ejecutes `docker compose down -v`** salvo que quieras borrar las bases de datos y los modelos desplegados en los volúmenes persistentes.
> - **No uses el botón *Publish* de Flowable Modeler 6.8.0**: rompe las 3 asociaciones de compensación del BPMN. Desplegá siempre con `bash motor-flowable/modelos/desplegar.sh`.

---

## 0. Requisitos previos en Linux

Verificá que tengas instalados **Docker + Docker Compose v2**, **JDK 21**, **Maven**, **Python 3 (`venv`)**, **`curl`** y **Graphviz** (`dot`, necesario para que PM4Py genere también `modelo_descubierto.png` y el DFG PNG directamente):

```bash
docker --version
docker compose version
java -version
mvn -version
python3 --version
```

En Ubuntu / Debian, si falta `python3-venv`, `curl` o `graphviz`:

```bash
sudo apt update && sudo apt install -y curl python3-venv graphviz
```

---

## 1. Obtener los últimos cambios y preparar `.env`

Desde la raíz del repositorio:

```bash
git fetch origin
git pull --ff-only
```

Si es una instalación nueva (y todavía no tenés `.env`), crealo desde la plantilla:

```bash
cp -n env.example .env
```

Validá que el `docker-compose.yml` resuelva bien las variables:

```bash
docker compose config --quiet
```

---

## 2. Levantar los contenedores y verificar servicios

Levantá todos los servicios (reconstruyendo `web` y `backend` para asegurar que queden en la última versión):

```bash
docker compose up -d --build
docker compose ps -a
```

> Esperá ~40–60 segundos a que Flowable termine de arrancar (`institucion-db` termina normalmente con `Exited (0)`).

Verificá que **Flowable**, el **Backend** y el almacenamiento de **PDFs** respondan correctamente:

```bash
# 1) Flowable UI / API REST
curl -fsS -u admin:test "http://localhost:8080/flowable-ui/process-api/repository/process-definitions?size=1" && echo "OK Flowable"

# 2) Backend Spring Boot
curl -fsS http://localhost:8081/api/v1/estado && echo

# 3) Permisos de escritura para PDFs de propuestas dentro del contenedor
docker compose exec backend sh -c 'test -w /app/almacenamiento/propuestas && echo PDF_DIRECTORIO_ESCRIBIBLE'
```

*(Si Flowable todavía está iniciando, podés seguir sus logs con `docker logs -f flowable-persistente` hasta ver `Started FlowableUiApplication`).*

---

## 3. Configurar listeners, usuarios IDM y modelo BPMN + DMN

Si es la primera vez que levantás el entorno (o si recreaste contenedores):

```bash
# 1) Copiar el JAR de listeners al contenedor de Flowable y reiniciar
docker cp motor-flowable/extensiones/flowable-extensiones.jar flowable-persistente:/app/WEB-INF/lib/flowable-extensiones.jar
docker restart flowable-persistente
```

Esperá a que `flowable-persistente` vuelva a responder (`curl -fsS -u admin:test "http://localhost:8080/flowable-ui/process-api/repository/process-definitions?size=1"`), y ejecutá los scripts idempotentes:

```bash
# 2) Crear grupos (Equipo-Administrativo, Equipo-Docente) y los 5 usuarios (clave 1234)
bash motor-flowable/idm/crear-usuarios.sh

# 3) Desplegar Organizacion_de_cursos.bar y verificar las 3 <association> de compensación
bash motor-flowable/modelos/desplegar.sh
```

Verificá el login del personal pasando por el proxy web (`http://localhost:5173`):

```bash
curl -i -s -c /tmp/cookies.txt \
  -X POST http://localhost:5173/api/v1/sesiones/personal \
  -H "Content-Type: application/json" \
  -d '{"usuarioId":"Jefe-Administrativo","clave":"1234"}'
rm -f /tmp/cookies.txt
```

Debe devolver **`HTTP/1.1 200`** (o **`200 OK`**).

---

## 4. Compilar apps de escritorio y verificar zona horaria + §4.3 (`app-materiales`)

1. **Confirmar que Flowable corre en UTC** (el motor embebido de `app-materiales` usa `UTC` por defecto vía `ZONA_MOTOR` / `FLOWABLE_ZONA_HORARIA`):

   ```bash
   docker exec flowable-persistente date
   ```

2. **Compilar `app-materiales` y `simulador-rest`:**

   ```bash
   mvn -f app-materiales/pom.xml compile
   mvn -f simulador-rest/pom.xml compile
   ```

3. **(Solo si tenés historial viejo previo al fix de zona horaria)** verificá primero si hay registros afectados de `preparar_materiales`:

   ```bash
   docker exec flowable-postgres psql -U flowable -d flowable \
     -c "SELECT COUNT(*) FROM act_hi_actinst WHERE act_id_='preparar_materiales' AND end_time_ < start_time_;"
   ```

   - Si devuelve **`0`**, no hace falta correr el script SQL.
   - Si devuelve **`> 0`**, ejecutalo **una sola vez**:

     ```bash
     docker exec -i flowable-postgres psql -U flowable -d flowable < app-materiales/sql/corregir_desfase_materiales.sql
     ```

     El `SELECT COUNT(*) AS filas_con_fin_antes_del_inicio` al final del script debe devolver **`0`**.

4. **Verificar las 3 consultas históricas (§4.3) por consola:**

   ```bash
   ./app-materiales/ejecutar.sh --analisis
   ```

   - Si todavía no tenés cursos finalizados en tu máquina, las tablas `4.3.1` y `4.3.2` mostrarán `0`.
   - En cuanto completes al menos un curso hasta el final (Paso 5), verificá que en **4.3.2 Análisis de la preparación paralela**, **Rama de materiales** muestre una duración promedio real **distinta de `0 ms`**.

---

## 5. Probar el circuito funcional completo (Web + Apps Java)

Podés abrir las interfaces en paralelo:

- **Sitio Web:** [http://localhost:5173](http://localhost:5173)
- **Flowable UI (Admin / Task):** [http://127.0.0.1:8080/flowable-ui/](http://127.0.0.1:8080/flowable-ui/) *(usar `127.0.0.1` separado de `localhost` para no mezclar cookies)*
- **App de Materiales (API Java):**
  ```bash
  ./app-materiales/ejecutar.sh
  ```
- **Simulador de Empresa (API REST):**
  ```bash
  ./simulador-rest/ejecutar.sh
  ```

Si querés preparar automáticamente los escenarios de demostración (`B`, `C`, `D`, `E`) usando la herramienta del repo:

```bash
python3 demo/preparar_escenarios.py estado
# Opcional (borra instancias activas y vacía BD 'institucion', conservando el historial de Flowable):
# python3 demo/preparar_escenarios.py preparar --limpiar
```

*(Consultá [demo/GUION_DEMO.md](../demo/GUION_DEMO.md) para el paso a paso de los 11 escenarios).*

---

## 6. Probar Minería de Procesos (§4.4) con PM4Py y Graphviz

1. **Preparar el entorno virtual Python e instalar dependencias:**

   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   pip install pm4py pandas
   ```

2. **Probar el modo de exportación real contra Flowable:**

   ```bash
   python3 mineria-procesos/exportar_history.py
   ```

   - Si tenés instancias con la etapa de negociación terminada (`aceptada`, `rechazada`, `sin respuesta` o `no viable`), exportará `mineria-procesos/negociacion_history.csv` indicando cuántas instancias se exportaron y cuántas en curso/borradas se descartaron.
   - Si tu Flowable local todavía no tiene negociaciones terminadas (o si apagás temporalmente Flowable / apuntás `FLOWABLE_REST_URL` a un puerto cerrado), debe **fallar con error claro y código de salida `1`**, sin generar datos de ejemplo en silencio.

3. **Ejecutar el análisis con PM4Py:**

   ```bash
   python3 mineria-procesos/analizar.py
   ```

   Verificá en la salida por consola y en `mineria-procesos/resultados_analisis.txt`:
   - `Motor de análisis      : PM4Py <versión>`
   - `Verificación           : PM4Py y el cálculo interno coinciden en recorridos y DFG`
   - Que **no** aparezca el cartel de datos de ejemplo (salvo si el CSV vino de `--ejemplo`).
   - Que se generen en `mineria-procesos/`:
     - `comportamiento_negociacion.png` (generado por `pm4py.save_vis_dfg` al tener `graphviz` instalado) y `comportamiento_negociacion.svg`
     - `captura_resultados.png` y `resultados_analisis.txt`
     - `modelo_descubierto.bpmn` y `modelo_descubierto.png` (descubiertos con `pm4py.discover_bpmn_inductive`)

4. **Probar el dataset de ejemplo en una copia aislada (sin pisar los archivos del repo):**

   ```bash
   rm -rf /tmp/mineria_test && cp -r mineria-procesos /tmp/mineria_test
   python3 /tmp/mineria_test/exportar_history.py --ejemplo
   python3 /tmp/mineria_test/analizar.py
   ```

   En este caso sí debe mostrar al principio el cartel:
   `!!! ATENCIÓN: DATOS DE EJEMPLO generados con --ejemplo. NO provienen del historial de Flowable. !!!`
   y coincidir entre PM4Py y el cálculo interno incluyendo `Vencimiento sin respuesta`.

---

## 7. Checklist rápido de verificación

| Prueba | Comando | Resultado esperado |
|---|---|---|
| Contenedor Flowable en UTC | `docker exec flowable-persistente date` | Fecha/hora en `UTC` |
| Backend operativo | `curl -fsS http://localhost:8081/api/v1/estado` | Respuesta HTTP `200` |
| Login personal | `POST /api/v1/sesiones/personal` (`Jefe-Administrativo` / `1234`) | `HTTP 200` |
| Despliegue BPMN | `bash motor-flowable/modelos/desplegar.sh` | Confirma `3` `<association>` de compensación |
| Análisis §4.3 | `./app-materiales/ejecutar.sh --analisis` | Tablas `4.3.1`, `4.3.2` y `4.3.3`; con cursos completos, `Rama de materiales` > `0 ms` |
| Exportación §4.4 | `python3 mineria-procesos/exportar_history.py` | CSV real o error claro (`exit 1`) si falta Flowable/instancias |
| Análisis PM4Py §4.4 | `python3 mineria-procesos/analizar.py` | `PM4Py` activo, `Verificación` coincidente, DFG + `modelo_descubierto.bpmn`/`.png` |
