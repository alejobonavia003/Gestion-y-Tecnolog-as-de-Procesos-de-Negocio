#!/usr/bin/env python3
"""
VERSION 8 (23/11)
Preparación de los escenarios de la defensa (GyTPN, proceso de organización de cursos).

Deja 4 instancias reales avanzadas hasta el punto justo, usando la API del backend
(como la web y el simulador) y la API REST de Flowable (para lo que en la demo se hace
desde Flowable UI). No usa mocks: todo queda en el motor y en la BD institucional.

  B  Lácteos del Sur      propuesta v1 enviada, esperando respuesta      → escenario 4
  C  Metalúrgica Río IV   en preparación, aula física + equipamiento OK  → escenario 6
  D  Agro Pampa           en preparación, simularSinAulas=true           → escenario 7
  E  Software Andino      en preparación, falta lista y materiales       → escenarios 8, 11, 1, 10

Con 4 activas, la solicitud que se cree en vivo llega con cantCursos=4 (viable, escenario 9)
y la siguiente con cantCursos=5 (NO_VIABLE, escenario 5).

Uso (desde la raíz del repo, con docker compose levantado y el modelo desplegado):

  python3 demo/preparar_escenarios.py estado
  python3 demo/preparar_escenarios.py preparar --limpiar
  python3 demo/preparar_escenarios.py preparar --limpiar --solo-solicitudes   # solo las 4 solicitudes
  python3 demo/preparar_escenarios.py escenario4 [B]   # demo interactiva del escenario 4
  python3 demo/preparar_escenarios.py timer B          # dispara el timer de 5 días
  python3 demo/preparar_escenarios.py informe E        # completa elaborar_informe con texto
  python3 demo/preparar_escenarios.py backup [nombre]
  python3 demo/preparar_escenarios.py restaurar [nombre]
  python3 demo/preparar_escenarios.py limpiar

Solo usa la biblioteca estándar de Python 3.8+.
"""

import argparse
import base64
import http.cookiejar
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DEMO_DIR = Path(__file__).resolve().parent
ESTADO_ARCHIVO = DEMO_DIR / "escenarios.json"
BACKUPS_DIR = DEMO_DIR / "backups"

TZ_AR = timezone(timedelta(hours=-3))


# --------------------------------------------------------------------------------------
# Configuración
# --------------------------------------------------------------------------------------

def leer_env():
    valores = {}
    archivo = RAIZ / ".env"
    if archivo.exists():
        for linea in archivo.read_text(encoding="utf-8").splitlines():
            linea = linea.strip()
            if not linea or linea.startswith("#") or "=" not in linea:
                continue
            clave, valor = linea.split("=", 1)
            valores[clave.strip()] = valor.strip().strip('"').strip("'")
    # Las variables de entorno del shell tienen prioridad sobre el .env
    for clave in list(valores):
        if clave in os.environ:
            valores[clave] = os.environ[clave]
    return valores


ENV = leer_env()


def cfg(clave, defecto):
    return os.environ.get(clave) or ENV.get(clave) or defecto


BACKEND = cfg("DEMO_BACKEND_URL", "http://localhost:8081/api/v1").rstrip("/")
FLOWABLE = cfg("DEMO_FLOWABLE_URL", "http://localhost:8080/flowable-ui/process-api").rstrip("/")
FLOWABLE_USR = cfg("FLOWABLE_REST_USUARIO", "admin")
FLOWABLE_PWD = cfg("FLOWABLE_REST_CLAVE", "test")
PROCESO = cfg("FLOWABLE_PROCESO_CLAVE", "process")
CLAVE_PERSONAL = cfg("DEMO_CLAVE_PERSONAL", "1234")
PG_CONTENEDOR = cfg("DEMO_PG_CONTENEDOR", "flowable-postgres")
PG_USUARIO = cfg("POSTGRES_USER", "flowable")
DB_FLOWABLE = cfg("POSTGRES_DB", "flowable")
DB_INSTITUCION = cfg("INSTITUCION_DB_NAME", "institucion")

JEFE_ADM = "Jefe-Administrativo"
EMPLEADO_ADM = "empleado-administrativo"


# --------------------------------------------------------------------------------------
# Salida
# --------------------------------------------------------------------------------------

def paso(msg):
    print(f"   · {msg}")


def titulo(msg):
    print(f"\n▶ {msg}")


def ok(msg):
    print(f"   ✔ {msg}")


def aviso(msg):
    print(f"   ⚠ {msg}")


class ErrorDemo(Exception):
    pass


# --------------------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------------------

def _leer_respuesta(resp):
    cuerpo = resp.read()
    if not cuerpo:
        return None
    tipo = resp.headers.get("Content-Type", "")
    if "json" in tipo:
        try:
            return json.loads(cuerpo.decode("utf-8"))
        except ValueError:
            # Flowable a veces devuelve recursos (XML BPMN) con Content-Type JSON
            return cuerpo
    return cuerpo


def _error_http(metodo, url, e):
    try:
        detalle = e.read().decode("utf-8", "replace")[:600]
    except Exception:
        detalle = ""
    return ErrorDemo(f"{metodo} {url} → HTTP {e.code}\n     {detalle}")


class Flowable:
    """Cliente mínimo de la API REST de Flowable (Basic Auth del usuario técnico)."""

    def __init__(self):
        token = base64.b64encode(f"{FLOWABLE_USR}:{FLOWABLE_PWD}".encode()).decode()
        self.auth = f"Basic {token}"

    def req(self, metodo, ruta, cuerpo=None, ok_404=False):
        url = FLOWABLE + ruta
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        r = urllib.request.Request(url, data=datos, method=metodo)
        r.add_header("Authorization", self.auth)
        r.add_header("Accept", "application/json")
        if datos is not None:
            r.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(r, timeout=30) as resp:
                return _leer_respuesta(resp)
        except urllib.error.HTTPError as e:
            if ok_404 and e.code == 404:
                return None
            raise _error_http(metodo, url, e)
        except urllib.error.URLError as e:
            raise ErrorDemo(f"No se pudo conectar con Flowable ({url}): {e.reason}")
        except (ConnectionError, TimeoutError, OSError) as e:
            raise ErrorDemo(f"Flowable cortó la conexión ({url}): {e}. ¿Todavía está arrancando?")

    # --- consultas ---
    def instancias_activas(self):
        r = self.req("GET", f"/runtime/process-instances?processDefinitionKey={PROCESO}&size=500")
        return r.get("data", [])

    def tareas(self, pid, clave=None):
        q = f"/runtime/tasks?processInstanceId={pid}&size=100"
        if clave:
            q += f"&taskDefinitionKey={clave}"
        return self.req("GET", q).get("data", [])

    def tarea_unica(self, pid, clave, intentos=10):
        for _ in range(intentos):
            ts = self.tareas(pid, clave)
            if ts:
                return ts[0]
            time.sleep(0.5)
        raise ErrorDemo(f"No apareció la tarea '{clave}' en la instancia {pid}.")

    def ejecuciones(self, pid):
        return self.req("GET", f"/runtime/executions?processInstanceId={pid}&size=100").get("data", [])

    def variable(self, pid, nombre):
        r = self.req("GET", f"/runtime/process-instances/{pid}/variables/{nombre}", ok_404=True)
        return None if r is None else r.get("value")

    def variable_historica(self, pid, nombre):
        r = self.req("GET", f"/history/historic-variable-instances?processInstanceId={pid}&variableName={nombre}")
        data = (r or {}).get("data", [])
        return data[0].get("variable", {}).get("value") if data else None

    def termino(self, pid):
        r = self.req("GET", f"/history/historic-process-instances/{pid}", ok_404=True)
        return bool(r and r.get("endTime"))

    # --- acciones ---
    def reclamar(self, task_id, usuario):
        self.req("POST", f"/runtime/tasks/{task_id}", {"action": "claim", "assignee": usuario})

    def completar(self, task_id, variables=None):
        cuerpo = {"action": "complete"}
        if variables:
            cuerpo["variables"] = [{"name": k, "value": v} for k, v in variables.items()]
        self.req("POST", f"/runtime/tasks/{task_id}", cuerpo)

    def fijar_variable(self, pid, nombre, valor, tipo="string"):
        self.req("PUT", f"/runtime/process-instances/{pid}/variables",
                 [{"name": nombre, "type": tipo, "value": valor}])

    def borrar_instancia(self, pid):
        self.req("DELETE", f"/runtime/process-instances/{pid}?deleteReason=limpieza-demo", ok_404=True)

    def timer_jobs(self, pid):
        return self.req("GET", f"/management/timer-jobs?processInstanceId={pid}").get("data", [])


class Backend:
    """Sesión contra el backend institucional, con cookie de sesión y token CSRF."""

    def __init__(self, usuario=None):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.usuario = usuario
        if usuario:
            self.req("POST", "/sesiones/personal", {"usuarioId": usuario, "clave": CLAVE_PERSONAL})

    def _xsrf(self):
        for c in self.jar:
            if c.name == "XSRF-TOKEN":
                return c.value
        return None

    def req(self, metodo, ruta, cuerpo=None, headers=None):
        url = BACKEND + ruta
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        r = urllib.request.Request(url, data=datos, method=metodo)
        r.add_header("Accept", "application/json")
        if datos is not None:
            r.add_header("Content-Type", "application/json")
        token = self._xsrf()
        if token and metodo not in ("GET", "HEAD"):
            r.add_header("X-XSRF-TOKEN", token)
        for k, v in (headers or {}).items():
            r.add_header(k, v)
        try:
            with self.opener.open(r, timeout=60) as resp:
                return _leer_respuesta(resp)
        except urllib.error.HTTPError as e:
            raise _error_http(metodo, url, e)
        except urllib.error.URLError as e:
            raise ErrorDemo(f"No se pudo conectar con el backend ({url}): {e.reason}")
        except (ConnectionError, TimeoutError, OSError) as e:
            raise ErrorDemo(f"El backend cortó la conexión ({url}): {e}. ¿Todavía está arrancando?")


# --------------------------------------------------------------------------------------
# Pasos de negocio reutilizables
# --------------------------------------------------------------------------------------

def iso(dt):
    return dt.astimezone(TZ_AR).replace(microsecond=0).isoformat()


def crear_solicitud(empresa, identificador, tematica, descripcion, participantes=12):
    cuerpo = {
        "empresa": {"nombre": empresa, "identificador": identificador},
        "contacto": {"nombre": "Contacto " + empresa.split()[0],
                     "email": f"rrhh@{identificador}.example.com", "telefono": "358 4000000"},
        "tematica": tematica,
        "descripcion": descripcion,
        "cantidadEstimadaParticipantes": participantes,
        "fechasPreferidas": "Martes y jueves por la mañana",
    }
    r = Backend().req("POST", "/solicitudes", cuerpo, {"Idempotency-Key": str(uuid.uuid4())})
    curso_id = r["cursoId"]
    pid = r["processInstanceId"]
    paso(f"solicitud creada · curso {curso_id[:8]}… · instancia {pid}")
    return curso_id, pid


def registrar_propuesta(fl, jefe, pid, importe="150000.00"):
    tarea = fl.tarea_unica(pid, "registrar_elaborar_propuesta")
    hoy = datetime.now(TZ_AR)
    cuerpo = {
        "programa": "Módulo 1: Introducción.\nMódulo 2: Práctica guiada.\nMódulo 3: Evaluación.",
        "presupuesto": {"importe": importe, "moneda": "ARS"},
        "fechasPosibles": [iso(hoy + timedelta(days=10)), iso(hoy + timedelta(days=17))],
    }
    jefe.req("POST", f"/tareas/{tarea['id']}/propuesta", cuerpo)
    paso("propuesta v1 registrada (PDF generado) y enviada")


def comunicar(curso_id, tipo, **extra):
    cuerpo = {"comunicacionId": f"demo-{tipo.lower()}-{uuid.uuid4()}", "tipo": tipo}
    cuerpo.update(extra)
    return Backend().req("POST", f"/simulacion/solicitudes/{curso_id}/comunicaciones", cuerpo)


def aceptar(curso_id, fecha_curso):
    r = comunicar(curso_id, "ACEPTACION", propuestaVersion=1, fechaCurso=iso(fecha_curso))
    usuario = (r or {}).get("cuentaEmpresaUsuario")
    paso(f"empresa aceptó la v1 (fechaCurso {iso(fecha_curso)}) · cuenta habilitada: {usuario} / 1234")
    return usuario


def evaluar_recursos(fl, pid, aula, plataforma, equipamiento):
    emp = Backend(EMPLEADO_ADM)
    tarea = fl.tarea_unica(pid, "evaluar_recursos")
    emp.req("POST", f"/tareas/{tarea['id']}/reclamo")
    emp.req("POST", f"/tareas/{tarea['id']}/completado",
            {"datos": {"requiereAula": aula, "requierePlataforma": plataforma,
                       "requiereEquipamiento": equipamiento}})
    elegidos = [n for n, v in (("aula física", aula), ("aula virtual", plataforma),
                               ("equipamiento", equipamiento)) if v]
    paso(f"evaluar_recursos reclamada y completada por {EMPLEADO_ADM}: {', '.join(elegidos)}")


def completar_en_flowable(fl, pid, clave, usuario=EMPLEADO_ADM):
    """Lo que en la demo se haría desde la Task app de Flowable UI."""
    tarea = fl.tarea_unica(pid, clave)
    if not tarea.get("assignee"):
        fl.reclamar(tarea["id"], usuario)
    try:
        fl.completar(tarea["id"])
    except ErrorDemo as e:
        if "Compensation" in str(e) or "compensation" in str(e):
            raise ErrorDemo(
                f"{e}\n\n     El modelo desplegado no tiene las <association> de compensación.\n"
                "     Volvé a desplegar con: bash motor-flowable/modelos/desplegar.sh (NO usar Publish).")
        raise
    paso(f"{clave} completada (como en Flowable UI)")


# --------------------------------------------------------------------------------------
# Comprobaciones previas
# --------------------------------------------------------------------------------------

def _esperar_listo(prueba, nombre, segundos=180):
    inicio = time.time()
    avisado = False
    while True:
        try:
            prueba()
            ok(f"{nombre} responde")
            return
        except ErrorDemo as e:
            if time.time() - inicio > segundos:
                raise ErrorDemo(f"{nombre} no respondió en {segundos} s. Revisá 'docker compose ps' "
                                f"y los logs ('docker compose logs backend' / 'docker logs flowable-persistente').\n     {e}")
            if not avisado:
                paso(f"{nombre} todavía no está listo, espero (hasta {segundos // 60} min)...")
                avisado = True
            time.sleep(5)


def comprobar_servicios(fl):
    titulo("Comprobando servicios")
    _esperar_listo(lambda: Backend().req("GET", "/estado"), f"backend ({BACKEND})")
    _esperar_listo(lambda: fl.req("GET", f"/repository/process-definitions?key={PROCESO}&latest=true"),
                   "Flowable")
    defs = fl.req("GET", f"/repository/process-definitions?key={PROCESO}&latest=true").get("data", [])
    if not defs:
        raise ErrorDemo(f"No hay proceso '{PROCESO}' desplegado. Corré: bash motor-flowable/modelos/desplegar.sh")
    d = defs[0]
    ok(f"proceso '{PROCESO}' desplegado (versión {d.get('version')})")
    xml = _xml_definicion(fl, d)
    if xml is None:
        aviso("no pude descargar el XML del proceso para revisar las asociaciones; sigo igual")
        return
    n = xml.count("<association ")
    if n == 3:
        ok("el modelo tiene las 3 asociaciones de compensación")
    else:
        raise ErrorDemo(f"El modelo desplegado (versión {d.get('version')}) tiene {n} asociaciones de compensación "
                        "(deben ser 3).\n     Seguramente se publicó desde el Modeler. Desplegá con: "
                        "bash motor-flowable/modelos/desplegar.sh")


def _xml_definicion(fl, d):
    """XML BPMN de la definición: primero por ID de definición, si no buscando el recurso en el deployment."""
    intentos = [f"/repository/process-definitions/{d['id']}/resourcedata"]
    try:
        recursos = fl.req("GET", f"/repository/deployments/{d['deploymentId']}/resources") or []
        for r in recursos:
            nombre = r.get("id") or ""
            if nombre.endswith((".bpmn", ".bpmn20.xml")) or r.get("type") == "processDefinition":
                intentos.append(f"/repository/deployments/{d['deploymentId']}/resourcedata/"
                                + urllib.parse.quote(nombre))
    except ErrorDemo:
        pass
    for ruta in intentos:
        try:
            contenido = fl.req("GET", ruta)
        except ErrorDemo:
            continue
        if isinstance(contenido, (bytes, bytearray)):
            texto = contenido.decode("utf-8", "replace")
            if "<process" in texto:
                return texto
    return None


# --------------------------------------------------------------------------------------
# Comandos
# --------------------------------------------------------------------------------------

def cmd_limpiar(fl, confirmar=True):
    activas = fl.instancias_activas()
    titulo(f"Limpieza: {len(activas)} instancia(s) activa(s) del proceso '{PROCESO}'")
    if confirmar:
        resp = input("   Se borran TODAS las instancias activas y los datos de la BD 'institucion'. "
                     "¿Continuar? [s/N] ").strip().lower()
        if resp not in ("s", "si", "sí", "y"):
            print("   Cancelado.")
            sys.exit(1)
    for inst in activas:
        fl.borrar_instancia(inst["id"])
    ok(f"{len(activas)} instancia(s) borrada(s) del motor (el historial se conserva)")
    sql = "TRUNCATE comunicacion_empresa, propuesta, cuenta_empresa, curso, empresa CASCADE;"
    try:
        subprocess.run(["docker", "exec", PG_CONTENEDOR, "psql", "-U", PG_USUARIO, "-d", DB_INSTITUCION,
                        "-v", "ON_ERROR_STOP=1", "-q", "-c", sql],
                       check=True, capture_output=True, text=True)
        ok(f"tablas de la BD '{DB_INSTITUCION}' vaciadas")
    except FileNotFoundError:
        aviso("no se encontró el comando 'docker': vaciá la BD institucional a mano (ver GUION_DEMO.md)")
    except subprocess.CalledProcessError as e:
        aviso(f"no se pudo vaciar la BD institucional: {e.stderr.strip()}")
    if ESTADO_ARCHIVO.exists():
        ESTADO_ARCHIVO.unlink()


EMPRESAS = {
    "B": ("Lácteos del Sur SA", "lacteos-sur", "Buenas prácticas de manufactura",
          "Capacitación en BPM e inocuidad alimentaria para la planta de envasado."),
    "C": ("Metalúrgica Río Cuarto SA", "metalurgica-rc", "Seguridad e higiene en planta",
          "Capacitación para operarios sobre uso de EPP y procedimientos de emergencia."),
    "D": ("Agro Pampa SRL", "agro-pampa", "Manejo de maquinaria agrícola",
          "Curso de operación segura de maquinaria para el personal de campo."),
    "E": ("Software Andino SA", "software-andino", "Testing automatizado",
          "Capacitación en testing automatizado y CI para el equipo de desarrollo."),
}


def cmd_solo_solicitudes(fl):
    """Crea las 4 solicitudes y no avanza ninguna: todas quedan en 'Registrar y elaborar propuesta'."""
    ahora = datetime.now(TZ_AR)
    escenarios = {}
    for letra, (empresa, ident, tematica, descripcion) in EMPRESAS.items():
        titulo(f"Solicitud {letra} · {empresa}")
        cid, pid = crear_solicitud(empresa, ident, tematica, descripcion)
        fl.tarea_unica(pid, "registrar_elaborar_propuesta")
        ok("viable · queda en 'Registrar y elaborar propuesta' (Jefe-Administrativo)")
        escenarios[letra] = {"cursoId": cid, "pid": pid, "empresa": empresa, "usuarioEmpresa": None,
                             "identificador": ident, "escenarios": []}
    ESTADO_ARCHIVO.write_text(json.dumps({"creado": iso(ahora), "modo": "solo-solicitudes",
                                          "instancias": escenarios}, indent=2, ensure_ascii=False),
                              encoding="utf-8")
    titulo("Listo")
    ok("4 solicitudes cargadas, sin propuestas ni aceptaciones")
    print("   Las cuentas de empresa se crean recién cuando cada una acepte su propuesta en el simulador")
    print("   (usuario = identificador: lacteos-sur, metalurgica-rc, agro-pampa, software-andino · clave 1234).")
    aviso("Con 4 activas, la próxima solicitud es viable y la siguiente da NO_VIABLE.")
    cmd_estado(fl)


def cmd_preparar(fl, limpiar, solo_solicitudes=False):
    comprobar_servicios(fl)
    activas = fl.instancias_activas()
    if activas:
        if limpiar:
            cmd_limpiar(fl, confirmar=False)
        else:
            raise ErrorDemo(
                f"Hay {len(activas)} instancia(s) activa(s). Cuentan para cantCursos y arruinan los escenarios 9 y 5.\n"
                "     Corré de nuevo con --limpiar (o el comando 'limpiar').")

    if solo_solicitudes:
        cmd_solo_solicitudes(fl)
        return

    jefe = Backend(JEFE_ADM)
    ahora = datetime.now(TZ_AR)
    escenarios = {}

    # ---------- B: esperando respuesta (escenario 4) ----------
    titulo("Instancia B · Lácteos del Sur · esperando respuesta (escenario 4)")
    cid, pid = crear_solicitud("Lácteos del Sur SA", "lacteos-sur", "Buenas prácticas de manufactura",
                               "Capacitación en BPM e inocuidad alimentaria para la planta de envasado.")
    registrar_propuesta(fl, jefe, pid)
    jobs = fl.timer_jobs(pid)
    ok(f"timer de 5 días pendiente (vence {jobs[0].get('dueDate') if jobs else '?'})")
    escenarios["B"] = {"cursoId": cid, "pid": pid, "empresa": "Lácteos del Sur SA", "usuarioEmpresa": None,
                       "escenarios": [4]}

    # ---------- C: en preparación con recursos gestionados (escenario 6) ----------
    titulo("Instancia C · Metalúrgica Río Cuarto · recursos gestionados (escenario 6)")
    cid, pid = crear_solicitud("Metalúrgica Río Cuarto SA", "metalurgica-rc", "Seguridad e higiene en planta",
                               "Capacitación para operarios sobre uso de EPP y procedimientos de emergencia.")
    registrar_propuesta(fl, jefe, pid)
    usuario = aceptar(cid, ahora + timedelta(days=20))
    evaluar_recursos(fl, pid, aula=True, plataforma=False, equipamiento=True)
    if fl.variable(pid, "aulaGestionada") is True:
        ok(f"aula física reservada automáticamente ({fl.variable(pid, 'aulaAsignada')})")
    completar_en_flowable(fl, pid, "contratar_equipamiento")
    ok("quedan pendientes: nómina, materiales → listos para cancelar")
    escenarios["C"] = {"cursoId": cid, "pid": pid, "empresa": "Metalúrgica Río Cuarto SA",
                       "usuarioEmpresa": usuario, "escenarios": [6]}

    # ---------- D: falta de aulas (escenario 7) ----------
    titulo("Instancia D · Agro Pampa · sin aulas (escenario 7)")
    cid, pid = crear_solicitud("Agro Pampa SRL", "agro-pampa", "Manejo de maquinaria agrícola",
                               "Curso de operación segura de maquinaria para el personal de campo.")
    registrar_propuesta(fl, jefe, pid)
    usuario = aceptar(cid, ahora + timedelta(days=25))
    fl.fijar_variable(pid, "simularSinAulas", "true")
    paso("variable simularSinAulas = 'true' (el web service de aulas va a responder que no hay)")
    ok("evaluar_recursos queda disponible para reclamar en vivo")
    escenarios["D"] = {"cursoId": cid, "pid": pid, "empresa": "Agro Pampa SRL",
                       "usuarioEmpresa": usuario, "escenarios": [7]}

    # ---------- E: camino completo (escenarios 8, 11, 1, 10) ----------
    titulo("Instancia E · Software Andino · camino feliz (escenarios 8, 11, 1, 10)")
    cid, pid = crear_solicitud("Software Andino SA", "software-andino", "Testing automatizado",
                               "Capacitación en testing automatizado y CI para el equipo de desarrollo.")
    registrar_propuesta(fl, jefe, pid)
    # fechaCurso ya pasada: el timer fecha_programa dispara apenas se define el docente.
    usuario = aceptar(cid, ahora - timedelta(minutes=5))
    evaluar_recursos(fl, pid, aula=True, plataforma=True, equipamiento=False)
    completar_en_flowable(fl, pid, "reservar_aula_virtual")
    comunicar(cid, "NOMINA", participantes=[{"nombre": n} for n in
                                            ("Lucía Gómez", "Martín Díaz", "Sofía Ruiz", "Tomás Pérez")])
    paso("empresa envió la nómina (4 participantes)")
    lista = fl.tarea_unica(pid, "confeccionar_lista")
    ok(f"confeccionar_lista creada y asignada por AsignarListaListener a: {lista.get('assignee')}")
    ok("quedan pendientes: confeccionar_lista (web) y preparar_materiales (app Java)")
    escenarios["E"] = {"cursoId": cid, "pid": pid, "empresa": "Software Andino SA",
                       "usuarioEmpresa": usuario, "asignadoLista": lista.get("assignee"),
                       "escenarios": [8, 11, 1, 10]}

    ESTADO_ARCHIVO.write_text(json.dumps({"creado": iso(ahora), "instancias": escenarios},
                                         indent=2, ensure_ascii=False), encoding="utf-8")

    n = len(fl.instancias_activas())
    titulo("Listo")
    if n == 4:
        ok("4 instancias activas: la próxima solicitud será viable (cantCursos=4) y la siguiente NO_VIABLE")
    else:
        aviso(f"hay {n} instancias activas (se esperaban 4): revisá antes de la demo")
    print(f"   Datos guardados en {ESTADO_ARCHIVO.relative_to(RAIZ)}")
    aviso("El timer de B vence en 5 días: prepará como máximo 4 días antes de la defensa.")
    print("   Siguiente paso recomendado: python3 demo/preparar_escenarios.py backup")
    cmd_estado(fl)


def cargar_estado():
    if not ESTADO_ARCHIVO.exists():
        return {}
    return json.loads(ESTADO_ARCHIVO.read_text(encoding="utf-8")).get("instancias", {})


def resolver_pid(objetivo):
    """Acepta una letra (B, C, D, E), un cursoId o un processInstanceId."""
    est = cargar_estado()
    if objetivo.upper() in est:
        return est[objetivo.upper()]["pid"]
    for datos in est.values():
        if datos["cursoId"] == objetivo:
            return datos["pid"]
    return objetivo


def cmd_estado(fl):
    est = cargar_estado()
    por_pid = {v["pid"]: k for k, v in est.items()}
    activas = fl.instancias_activas()
    titulo(f"{len(activas)} instancia(s) activa(s) · la próxima solicitud llega con cantCursos={len(activas)}"
           f" → {'VIABLE' if len(activas) < 5 else 'NO_VIABLE'}")
    for inst in activas:
        pid = inst["id"]
        letra = por_pid.get(pid, "·")
        empresa = fl.variable(pid, "empresaNombre") or "?"
        tareas = [f"{t['taskDefinitionKey']}({t.get('assignee') or 'grupo'})" for t in fl.tareas(pid)]
        esperas = sorted({e.get("activityId") for e in fl.ejecuciones(pid)
                          if e.get("activityId") and e.get("activityId") not in {t["taskDefinitionKey"] for t in fl.tareas(pid)}})
        print(f"   [{letra}] {empresa:<28} pid={pid}")
        if tareas:
            print(f"        tareas: {', '.join(tareas)}")
        if esperas:
            print(f"        esperando: {', '.join(esperas)}")
        if letra in est and est[letra].get("usuarioEmpresa"):
            print(f"        login empresa: {est[letra]['usuarioEmpresa']} / 1234")


def _jobs(fl, lista, pid):
    return (fl.req("GET", f"/management/{lista}?processInstanceId={pid}") or {}).get("data", [])


def _error_de_jobs(fl, pid):
    """Primer mensaje de error que Flowable tenga registrado en los jobs de la instancia."""
    for lista in ("deadletter-jobs", "jobs", "timer-jobs"):
        for j in _jobs(fl, lista, pid):
            if j.get("exceptionMessage"):
                return f"[{lista}] {j['exceptionMessage']}"
    return None


def _forzar_timer(fl, pid):
    """Ejecuta YA el timer pendiente, sin depender del ejecutor asíncrono de Flowable.

    1. Recupera jobs que quedaron apartados (dead letter) por fallos anteriores.
    2. Pasa el timer pendiente a la cola de ejecución ('move').
    3. Ejecuta esos jobs en el momento ('execute'): si falla, Flowable devuelve el error real.
    """
    for j in _jobs(fl, "deadletter-jobs", pid):
        fl.req("POST", f"/management/deadletter-jobs/{j['id']}", {"action": "move"})
        paso("se recuperó un job que había fallado antes")
    timers = fl.timer_jobs(pid)
    for t in timers:
        fl.req("POST", f"/management/timer-jobs/{t['id']}", {"action": "move"})
    ejecutables = _jobs(fl, "jobs", pid)
    if not timers and not ejecutables:
        raise ErrorDemo(f"La instancia {pid} no tiene timers pendientes.")
    for j in ejecutables:
        try:
            fl.req("POST", f"/management/jobs/{j['id']}", {"action": "execute"})
        except ErrorDemo as e:
            if "HTTP 404" in str(e):
                continue  # ya lo había tomado el ejecutor asíncrono
            detalle = _error_de_jobs(fl, pid) or str(e)
            raise ErrorDemo("Flowable no pudo ejecutar el timer. Error del motor:\n     " + detalle)


def _esperando_fecha(fl, pid):
    return any(e.get("activityId") == "fecha_programa" for e in fl.ejecuciones(pid))


def cmd_timer(fl, objetivo):
    pid = resolver_pid(objetivo)
    jobs = fl.timer_jobs(pid)
    vence = jobs[0].get("dueDate") if jobs else "?"
    viejos = {j["id"] for j in jobs}
    es_fecha = _esperando_fecha(fl, pid)
    if es_fecha:
        titulo(f"Adelantando la fecha del curso (estaba programada para {vence})")
    else:
        titulo(f"Adelantando el timer de 5 días (vencía {vence}) · recordatorios={fl.variable(pid, 'recordatorios')}")
    _forzar_timer(fl, pid)
    for _ in range(60):
        dictado = fl.tareas(pid, "desarrollar_curso")
        if dictado:
            ok(f"llegó la fecha del curso · 'Desarrollar curso' asignada a {dictado[0].get('assignee')}")
            return
        if fl.tareas(pid, "archivar_solicitud"):
            ok(f"cierre por falta de respuesta · archivar_solicitud creada para {JEFE_ADM} · "
               f"resultadoProceso={fl.variable(pid, 'resultadoProceso')}")
            return
        if not es_fecha:
            nuevos = [j for j in fl.timer_jobs(pid) if j["id"] not in viejos]
            if nuevos:
                ok(f"recordatorio enviado · recordatorios={fl.variable(pid, 'recordatorios')} · "
                   f"nuevo timer vence {nuevos[0].get('dueDate')}")
                return
        time.sleep(0.5)
    error = _error_de_jobs(fl, pid)
    if error:
        raise ErrorDemo(f"El timer no avanzó. Error del motor:\n     {error}")
    aviso("El timer se ejecutó pero no veo el resultado esperado. Corré 'estado' para ver en qué quedó.")


def cmd_informe(fl, objetivo, texto):
    pid = resolver_pid(objetivo)
    tarea = fl.tarea_unica(pid, "elaborar_informe", intentos=1)
    texto = texto or ("Asistieron 4 de 4 participantes. Se cubrieron los 3 módulos previstos. "
                      "Evaluación final aprobada por todos. Se recomienda un módulo avanzado.")
    fl.completar(tarea["id"], {"informeResumen": texto})
    ok("elaborar_informe completada con informeResumen (el backend ya puede generar el PDF del informe)")


def _docker(*args, check=True, entrada=None, salida=None):
    return subprocess.run(["docker", *args], check=check, stdin=entrada, stdout=salida,
                          stderr=subprocess.PIPE, text=salida is None)


def _compose(*args, check=True):
    return subprocess.run(["docker", "compose", "-f", str(RAIZ / "docker-compose.yml"), *args],
                          check=check, capture_output=True, text=True)


def _contenedor_backend():
    cid = _compose("ps", "-aq", "backend").stdout.strip()
    if not cid:
        raise ErrorDemo("No se encontró el contenedor del backend (docker compose ps).")
    return cid


def cmd_backup(nombre):
    destino = BACKUPS_DIR / nombre
    destino.mkdir(parents=True, exist_ok=True)
    titulo(f"Backup en {destino.relative_to(RAIZ)}")
    for db in (DB_FLOWABLE, DB_INSTITUCION):
        with open(destino / f"{db}.dump", "wb") as f:
            _docker("exec", PG_CONTENEDOR, "pg_dump", "-U", PG_USUARIO, "-Fc", db, salida=f)
        ok(f"base '{db}' respaldada")
    pdfs = destino / "almacenamiento"
    if pdfs.exists():
        subprocess.run(["rm", "-rf", str(pdfs)], check=True)
    _docker("cp", f"{_contenedor_backend()}:/app/almacenamiento", str(pdfs))
    ok("PDFs de propuestas respaldados")
    if ESTADO_ARCHIVO.exists():
        (destino / "escenarios.json").write_bytes(ESTADO_ARCHIVO.read_bytes())
    print(f"   Para volver a este punto: python3 demo/preparar_escenarios.py restaurar {nombre}")


def _esperar(url_ok, descripcion, segundos=240):
    for _ in range(segundos // 3):
        try:
            url_ok()
            ok(f"{descripcion} disponible")
            return
        except Exception:
            time.sleep(3)
    raise ErrorDemo(f"{descripcion} no respondió a tiempo.")


def cmd_restaurar(fl, nombre):
    origen = BACKUPS_DIR / nombre
    if not (origen / f"{DB_FLOWABLE}.dump").exists():
        raise ErrorDemo(f"No existe el backup {origen}")
    titulo(f"Restaurando {origen.relative_to(RAIZ)} (tarda ~1-2 minutos)")
    aviso("Cerrá antes app-materiales: tiene su propio motor conectado a la base 'flowable'.")
    _compose("stop", "backend", "flowable")
    ok("backend y flowable detenidos")
    for db in (DB_FLOWABLE, DB_INSTITUCION):
        _docker("exec", PG_CONTENEDOR, "dropdb", "-U", PG_USUARIO, "--if-exists", "--force", db)
        _docker("exec", PG_CONTENEDOR, "createdb", "-U", PG_USUARIO, db)
        with open(origen / f"{db}.dump", "rb") as f:
            r = _docker("exec", "-i", PG_CONTENEDOR, "pg_restore", "-U", PG_USUARIO, "-d", db, "--no-owner",
                        entrada=f, salida=subprocess.DEVNULL, check=False)
        if r.returncode != 0:
            aviso(f"pg_restore terminó con advertencias en '{db}':\n     "
                  + r.stderr.decode("utf-8", "replace").strip()[:500])
        ok(f"base '{db}' restaurada")
    backend_cid = _contenedor_backend()
    if (origen / "almacenamiento").exists():
        _docker("cp", f"{origen / 'almacenamiento'}/.", f"{backend_cid}:/app/almacenamiento")
    _compose("start", "flowable")
    _esperar(lambda: fl.req("GET", f"/repository/process-definitions?key={PROCESO}"), "Flowable")
    _compose("start", "backend")
    _docker("exec", "-u", "0", backend_cid, "chown", "-R", "10001:10001", "/app/almacenamiento", check=False)
    _esperar(lambda: Backend().req("GET", "/estado"), "Backend")
    if (origen / "escenarios.json").exists():
        ESTADO_ARCHIVO.write_bytes((origen / "escenarios.json").read_bytes())
    aviso("Las sesiones de la web se perdieron: volvé a iniciar sesión en el navegador.")
    cmd_estado(fl)



# --------------------------------------------------------------------------------------
# Escenario 4 interactivo: recordatorios y cierre por falta de respuesta
# --------------------------------------------------------------------------------------

def _pausa(texto):
    try:
        return input(f"\n   ⏎  {texto} ").strip().lower()
    except (EOFError, KeyboardInterrupt):
        print("\n   Interrumpido. La instancia queda en el punto en que estaba.")
        sys.exit(1)


def _curso_id_de(fl, pid):
    r = fl.req("GET", f"/runtime/process-instances/{pid}", ok_404=True)
    if r is None:
        r = fl.req("GET", f"/history/historic-process-instances/{pid}", ok_404=True)
    bk = (r or {}).get("businessKey") or ""
    return bk.split(":", 1)[1] if bk.startswith("curso:") else None


def _estado_institucion(curso_id):
    """Estado del curso en la BD institucional (solo informativo: si falla, devuelve '?')."""
    if not curso_id:
        return "?"
    try:
        r = Backend().req("GET", f"/simulacion/solicitudes/{curso_id}/comunicaciones")
        if isinstance(r, dict) and r.get("estado"):
            return r["estado"]
    except Exception:
        pass
    return "?"


def _disparar_y_esperar(fl, pid):
    """Dispara el timer pendiente. Devuelve ('recordatorio', n, vence) o ('cierre', resultado, None)."""
    jobs = fl.timer_jobs(pid)
    if not jobs and not _jobs(fl, "deadletter-jobs", pid):
        raise ErrorDemo("La instancia no tiene el timer de 5 días pendiente (¿ya respondió la empresa?).")
    viejos = {j["id"] for j in jobs}
    _forzar_timer(fl, pid)
    for _ in range(60):
        if fl.tareas(pid, "archivar_solicitud"):
            return "cierre", fl.variable(pid, "resultadoProceso"), None
        nuevos = [j for j in fl.timer_jobs(pid) if j["id"] not in viejos]
        if nuevos:
            return "recordatorio", fl.variable(pid, "recordatorios"), nuevos[0].get("dueDate")
        time.sleep(0.5)
    error = _error_de_jobs(fl, pid)
    raise ErrorDemo("El timer no avanzó." + (f" Error del motor:\n     {error}" if error else " Corré 'estado'."))


def _buscar_instancia_escenario4(fl):
    est = cargar_estado()
    if "B" in est:
        return est["B"]["pid"]
    activas = fl.instancias_activas()
    con_timer = [i["id"] for i in activas if fl.timer_jobs(i["id"])]
    if con_timer:
        return con_timer[0]
    sin_propuesta = [i["id"] for i in activas if fl.tareas(i["id"], "registrar_elaborar_propuesta")]
    if sin_propuesta:
        return sin_propuesta[0]
    raise ErrorDemo("No encontré ninguna instancia esperando respuesta ni con la propuesta pendiente.\n"
                    "     Indicá cuál usar: escenario4 <letra | cursoId | processInstanceId>")


def _fecha_corta(iso_txt):
    if not iso_txt:
        return "?"
    try:
        return datetime.fromisoformat(iso_txt.replace("Z", "+00:00")).astimezone(TZ_AR).strftime("%d/%m %H:%M")
    except ValueError:
        return iso_txt


def cmd_escenario4(fl, objetivo):
    pid = resolver_pid(objetivo) if objetivo else _buscar_instancia_escenario4(fl)
    if fl.termino(pid) or not fl.req("GET", f"/runtime/process-instances/{pid}", ok_404=True):
        raise ErrorDemo(f"La instancia {pid} no está activa.")
    empresa = fl.variable(pid, "empresaNombre") or "?"
    curso_id = _curso_id_de(fl, pid)

    print("\n" + "═" * 70)
    print("  ESCENARIO 4 · Cierre por falta de respuesta luego de los recordatorios")
    print("═" * 70)
    print(f"  Empresa: {empresa}")
    print(f"  Curso:   {curso_id}   ·   estado: {_estado_institucion(curso_id)}")
    print(f"  Proceso: {pid}")
    print("  Regla del modelo: cada 5 días sin respuesta se manda un recordatorio;")
    print("  con 2 recordatorios enviados, el siguiente vencimiento cierra la solicitud.")

    # Paso 0: si todavía no hay propuesta, se envía
    if fl.tareas(pid, "registrar_elaborar_propuesta"):
        _pausa("Enter → el Jefe-Administrativo elabora y envía la propuesta v1")
        registrar_propuesta(fl, Backend(JEFE_ADM), pid)

    jobs = fl.timer_jobs(pid)
    if not jobs:
        raise ErrorDemo("La instancia no está esperando respuesta de la empresa (no hay timer de 5 días).")
    rec = fl.variable(pid, "recordatorios") or 0
    print(f"\n   📤 Propuesta enviada. Esperando respuesta de la empresa…")
    print(f"      recordatorios enviados: {rec}   ·   el timer vence el {_fecha_corta(jobs[0].get('dueDate'))}")

    vencimiento = 0
    while True:
        vencimiento += 1
        _pausa(f"Enter → pasan 5 días sin respuesta (vencimiento {vencimiento})")
        print("      ⏩ adelantando el timer de 5 días…")
        tipo, valor, vence = _disparar_y_esperar(fl, pid)
        if tipo == "recordatorio":
            print(f"\n   📨 RECORDATORIO {valor} enviado a la empresa")
            print(f"      recordatorios enviados: {valor}   ·   nuevo vencimiento: {_fecha_corta(vence)}")
            print("      (la empresa todavía podría aceptar, pedir cambios o rechazar)")
        else:
            print("\n   ⌛ CIERRE POR FALTA DE RESPUESTA")
            print(f"      ya se habían enviado 2 recordatorios → resultadoProceso = {valor}")
            print(f"      se creó la tarea 'Archivar solicitud' para {JEFE_ADM}")
            break

    eleccion = _pausa("Enter → archivar la solicitud desde acá   ·   escribí 'w' + Enter para hacerlo en la web")
    tarea = fl.tarea_unica(pid, "archivar_solicitud")
    if eleccion == "w":
        print(f"      Entrá a la web como {JEFE_ADM} → 'Archivar solicitud' (motivo: sin respuesta) → completar.")
        print("      Espero a que la completes…")
        while not fl.termino(pid):
            time.sleep(2)
    else:
        jefe = Backend(JEFE_ADM)
        jefe.req("POST", f"/tareas/{tarea['id']}/completado", {"datos": {}})
        for _ in range(20):
            if fl.termino(pid):
                break
            time.sleep(0.5)

    print("\n   ✅ Proceso terminado")
    print(f"      estado del curso en la BD institucional: {_estado_institucion(curso_id)}")
    print("═" * 70)


def main():
    p = argparse.ArgumentParser(description="Prepara y maneja los escenarios de la defensa.")
    sub = p.add_subparsers(dest="cmd", required=True)
    pp = sub.add_parser("preparar", help="crea las instancias B, C, D y E")
    pp.add_argument("--limpiar", action="store_true", help="borra antes las instancias activas y la BD institucional")
    pp.add_argument("--solo-solicitudes", action="store_true",
                    help="solo crea las 4 solicitudes, sin propuestas ni aceptaciones")
    sub.add_parser("estado", help="muestra las instancias activas y en qué punto están")
    pl = sub.add_parser("limpiar", help="borra instancias activas y vacía la BD institucional")
    pl.add_argument("--si", action="store_true", help="no pedir confirmación")
    pt = sub.add_parser("timer", help="dispara ahora el timer pendiente (5 días o fecha del curso)")
    pt.add_argument("objetivo", help="letra (B), cursoId o processInstanceId")
    pe = sub.add_parser("escenario4", help="demo interactiva: recordatorios y cierre por falta de respuesta")
    pe.add_argument("objetivo", nargs="?", help="letra, cursoId o processInstanceId (por defecto B o la que esté esperando)")
    pi = sub.add_parser("informe", help="completa elaborar_informe con informeResumen")
    pi.add_argument("objetivo", help="letra (E), cursoId o processInstanceId")
    pi.add_argument("texto", nargs="?", help="texto del informe (opcional)")
    pb = sub.add_parser("backup", help="respalda las 2 bases y los PDFs")
    pb.add_argument("nombre", nargs="?", default="escenarios")
    pr = sub.add_parser("restaurar", help="vuelve al estado de un backup")
    pr.add_argument("nombre", nargs="?", default="escenarios")
    args = p.parse_args()

    fl = Flowable()
    try:
        if args.cmd == "preparar":
            cmd_preparar(fl, args.limpiar, args.solo_solicitudes)
        elif args.cmd == "estado":
            cmd_estado(fl)
        elif args.cmd == "limpiar":
            cmd_limpiar(fl, confirmar=not args.si)
        elif args.cmd == "timer":
            cmd_timer(fl, args.objetivo)
        elif args.cmd == "escenario4":
            cmd_escenario4(fl, args.objetivo)
        elif args.cmd == "informe":
            cmd_informe(fl, args.objetivo, args.texto)
        elif args.cmd == "backup":
            cmd_backup(args.nombre)
        elif args.cmd == "restaurar":
            cmd_restaurar(fl, args.nombre)
    except ErrorDemo as e:
        print(f"\n✖ {e}", file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        print(f"\n✖ Falló un comando de docker: {' '.join(e.cmd)}\n  {e.stderr}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
