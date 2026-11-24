#!/usr/bin/env python3
"""
Sección 4.4.1 — Exportación de actividades históricas de Flowable (Etapa de Negociación).

Consulta la API REST histórica de Flowable (/history/historic-activity-instances) para el
proceso implementado (clave 'process'), filtra las actividades correspondientes a la etapa
de negociación (previa al subproceso 'preparar_curso') y genera el archivo CSV compatible
con PM4Py (columnas: case_id, activity, activity_id, timestamp, end_time, assignee, duration_ms).

Uso:
  python3 exportar_history.py                        # Exporta desde Flowable REST el historial REAL (si falla, no genera nada)
  python3 exportar_history.py --ejemplo              # Genera CSV con 25 ejecuciones históricas de prueba (marcadas con case_id 'ejemplo-XXX')
"""

import argparse
import csv
import datetime
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from base64 import b64encode

# Actividades de la etapa de negociación en motor-flowable/modelos/institucion-de-capacitacion.bpmn20.xml
# (Etapa previa al subproceso 'preparar_curso')
ACTIVIDADES_NEGOCIACION = {
    "recibir_solicitud": "Solicitud recibida",
    "evaluar_viabilidad": "Evaluar viabilidad",
    "notificar_decision": "Notificar decision",
    "registrar_elaborar_propuesta": "Registrar y elaborar propuesta",
    "enviar_propuesta": "Enviar propuesta",
    "evento-modificacion": "Modificaciones",
    "revisar_propuesta": "Revisar propuesta",
    "evento-5-dias": "Vencimiento sin respuesta",
    "enviar_recordatorios": "Enviar recordatorios",
    "evento-rechazo": "Rechazo",
    "archivar_solicitud": "Archivar solicitud",
    "evento-aceptacion": "Aceptacion",
    "habilitar_acceso_empresa": "Habilitar acceso de la empresa",
}

# Orden de desempate cuando dos actividades automáticas ocurren en el mismo milisegundo
ORDEN_LOGICO = {
    "recibir_solicitud": 1, "evaluar_viabilidad": 2, "notificar_decision": 3,
    "registrar_elaborar_propuesta": 4, "enviar_propuesta": 5, "evento-5-dias": 6,
    "enviar_recordatorios": 7, "evento-modificacion": 8, "revisar_propuesta": 9,
    "evento-rechazo": 10, "archivar_solicitud": 11, "evento-aceptacion": 12,
    "habilitar_acceso_empresa": 13,
}
# Cada evento del gateway se reconoce como "ocurrido" por la actividad que lo sigue en el modelo.
SUCESOR_DE_EVENTO = {
    "evento-aceptacion": "habilitar_acceso_empresa",
    "evento-modificacion": "revisar_propuesta",
    "evento-rechazo": "archivar_solicitud",
    "evento-5-dias": "gw_recordatorios",
}
# Si dos sucesores empiezan en el mismo milisegundo, el gateway del timer va antes que archivar
PRIORIDAD_SUCESOR = {"gw_recordatorios": 0, "revisar_propuesta": 1, "habilitar_acceso_empresa": 1,
                     "archivar_solicitud": 2}
FIN_NEGOCIACION = {"habilitar_acceso_empresa", "notificar_decision", "archivar_solicitud"}


def cargar_env() -> dict:
    """Lee las variables del archivo .env de la raíz del repositorio y del entorno."""
    valores = {}
    posibles = [
        Path(__file__).resolve().parent.parent / ".env",
        Path(__file__).resolve().parent.parent / "env.example",
    ]
    for ruta in posibles:
        if ruta.is_file():
            for linea in ruta.read_text(encoding="utf-8").splitlines():
                s = linea.strip()
                if not s or s.startswith("#") or "=" not in s:
                    continue
                if s.startswith("export "):
                    s = s[7:].strip()
                k, v = s.split("=", 1)
                v = v.strip().strip('"').strip("'")
                valores.setdefault(k.strip(), v)
    valores.update(os.environ)
    return valores


def consultar_flowable_rest(
    base_url: str, usuario: str, clave: str, process_key: str, resumen: dict | None = None
) -> list[dict]:
    """Obtiene todas las actividades históricas del proceso desde la API REST de Flowable."""
    credenciales = b64encode(f"{usuario}:{clave}".encode("utf-8")).decode("ascii")
    headers = {
        "Authorization": f"Basic {credenciales}",
        "Accept": "application/json",
    }

    # 1. Consultar definiciones del proceso para incluir todas las versiones desplegadas
    url_defs = f"{base_url.rstrip('/')}/repository/process-definitions?key={urllib.parse.quote(process_key)}&size=100"
    req_defs = urllib.request.Request(url_defs, headers=headers)
    with urllib.request.urlopen(req_defs, timeout=10) as resp:
        defs_data = json.loads(resp.read().decode("utf-8"))

    def_ids = [d["id"] for d in defs_data.get("data", []) if "id" in d]
    if not def_ids:
        if resumen is not None:
            resumen["excluidos"] = 0
        return []

    instancias_borradas: set[str] = set()
    actividades_crudas = []
    for def_id in def_ids:
        start = 0
        size = 500
        while True:
            params_inst = urllib.parse.urlencode({
                "processDefinitionId": def_id,
                "size": size,
                "start": start,
            })
            url_inst = f"{base_url.rstrip('/')}/history/historic-process-instances?{params_inst}"
            req_inst = urllib.request.Request(url_inst, headers=headers)
            with urllib.request.urlopen(req_inst, timeout=15) as resp:
                pagina_inst = json.loads(resp.read().decode("utf-8"))
            insts = pagina_inst.get("data", [])
            for inst in insts:
                if inst.get("id") and inst.get("deleteReason"):
                    instancias_borradas.add(inst["id"])
            total_inst = pagina_inst.get("total", len(insts))
            start += len(insts)
            if start >= total_inst or not insts:
                break

        start = 0
        while True:
            params = urllib.parse.urlencode({
                "processDefinitionId": def_id,
                "size": size,
                "start": start,
                "sort": "startTime",
                "order": "asc",
            })
            url_acts = f"{base_url.rstrip('/')}/history/historic-activity-instances?{params}"
            req_acts = urllib.request.Request(url_acts, headers=headers)
            with urllib.request.urlopen(req_acts, timeout=15) as resp:
                pagina = json.loads(resp.read().decode("utf-8"))
            items = pagina.get("data", [])
            actividades_crudas.extend(items)
            total = pagina.get("total", len(items))
            start += len(items)
            if start >= total or not items:
                break

    return transformar_actividades(actividades_crudas, resumen, instancias_borradas)


def _evento_ocurrio(ev: dict, actividades_caso: list[dict]) -> bool:
    """Decide si un evento del gateway por eventos se disparó de verdad o fue cancelado."""
    if ev.get("deleteReason"):
        return False
    fin_ev = ev.get("endTime")
    if not fin_ev:
        return False
    fin_dt = parsear_iso(fin_ev)
    candidatos = [a for a in actividades_caso
                  if a.get("activityId") in PRIORIDAD_SUCESOR and a.get("startTime")
                  and parsear_iso(a["startTime"]) >= fin_dt]
    if not candidatos:
        return False
    siguiente = min(candidatos, key=lambda a: (parsear_iso(a["startTime"]),
                                               PRIORIDAD_SUCESOR[a["activityId"]]))
    return siguiente["activityId"] == SUCESOR_DE_EVENTO[ev["activityId"]]


def transformar_actividades(
    actividades_crudas: list[dict],
    resumen: dict | None = None,
    instancias_borradas: set[str] | None = None,
) -> list[dict]:
    por_caso: dict[str, list[dict]] = {}
    for item in actividades_crudas:
        if item.get("processInstanceId") and item.get("activityId") and item.get("startTime"):
            por_caso.setdefault(item["processInstanceId"], []).append(item)
    filas, excluidos = [], 0
    for case_id in sorted(por_caso):
        if instancias_borradas and case_id in instancias_borradas:
            excluidos += 1          # instancia eliminada administrativamente (deleteReason)
            continue
        actividades_caso = por_caso[case_id]
        eventos = []
        for item in actividades_caso:
            act_id = item["activityId"]
            if act_id not in ACTIVIDADES_NEGOCIACION:
                continue
            if act_id in SUCESOR_DE_EVENTO and not _evento_ocurrio(item, actividades_caso):
                continue
            # Los eventos del gateway empiezan todos juntos; el momento en que OCURREN es su fin.
            momento = item["endTime"] if act_id in SUCESOR_DE_EVENTO else item["startTime"]
            eventos.append({"case_id": case_id, "activity": ACTIVIDADES_NEGOCIACION[act_id],
                            "activity_id": act_id, "_inicio": parsear_iso(momento),
                            "_fin": item.get("endTime"), "assignee": item.get("assignee") or "",
                            "duration_ms": item.get("durationInMillis") or 0,
                            "_orden": ORDEN_LOGICO.get(act_id, 99)})
        eventos.sort(key=lambda e: (e["_inicio"], e["_orden"]))
        ultimo = eventos[-1] if eventos else None
        if not (ultimo and ultimo["activity_id"] in FIN_NEGOCIACION and ultimo["_fin"]):
            excluidos += 1          # negociación en curso o instancia borrada a mitad
            continue
        anterior = None
        for ev in eventos:
            dt = ev["_inicio"]
            if anterior is not None and dt <= anterior:
                dt = anterior + datetime.timedelta(milliseconds=1)
            anterior = dt
            filas.append({"case_id": case_id, "activity": ev["activity"], "activity_id": ev["activity_id"],
                          "timestamp": dt.isoformat(timespec="milliseconds"),
                          "end_time": parsear_iso(ev["_fin"]).isoformat(timespec="milliseconds"),
                          "assignee": ev["assignee"], "duration_ms": ev["duration_ms"]})
    if resumen is not None:
        resumen["excluidos"] = excluidos
    return filas


def parsear_iso(texto: str) -> datetime.datetime:
    try:
        return datetime.datetime.fromisoformat(texto.replace("Z", "+00:00"))
    except ValueError:
        return datetime.datetime(2026, 11, 1, 9, 0, 0, tzinfo=datetime.timezone.utc)


def generar_datos_ejemplo() -> list[dict]:
    """
    Genera un conjunto de 25 ejecuciones históricas de la etapa de negociación
    que refleja los recorridos del proceso implementado en Flowable:
      1. Aceptación directa de la propuesta v1 (10 casos - 40.0%)
      2. Solicitud de modificaciones, revisión a v2 y aceptación (6 casos - 24.0%)
      3. Rechazo de la propuesta y archivo de la solicitud (4 casos - 16.0%)
      4. Solicitud no viable por límite de >= 5 cursos en ejecución (3 casos - 12.0%)
      5. Cierre por falta de respuesta luego de 2 recordatorios (2 casos - 8.0%)
    """
    recorridos = [
        # Recorrido 1 (10 instancias): Aceptación directa
        (
            10,
            [
                ("recibir_solicitud", "", 150),
                ("evaluar_viabilidad", "", 320),
                ("registrar_elaborar_propuesta", "Jefe-Administrativo", 1_800_000),
                ("enviar_propuesta", "", 210),
                ("evento-aceptacion", "", 54_000_000),
                ("habilitar_acceso_empresa", "", 420),
            ],
        ),
        # Recorrido 2 (6 instancias): Solicitud de modificaciones -> Revisión -> Aceptación
        (
            6,
            [
                ("recibir_solicitud", "", 140),
                ("evaluar_viabilidad", "", 290),
                ("registrar_elaborar_propuesta", "Jefe-Administrativo", 2_100_000),
                ("enviar_propuesta", "", 190),
                ("evento-modificacion", "", 43_200_000),
                ("revisar_propuesta", "Jefe-Administrativo", 1_500_000),
                ("enviar_propuesta", "", 180),
                ("evento-aceptacion", "", 36_000_000),
                ("habilitar_acceso_empresa", "", 390),
            ],
        ),
        # Recorrido 3 (4 instancias): Rechazo de la propuesta y archivo
        (
            4,
            [
                ("recibir_solicitud", "", 130),
                ("evaluar_viabilidad", "", 280),
                ("registrar_elaborar_propuesta", "Jefe-Administrativo", 1_650_000),
                ("enviar_propuesta", "", 200),
                ("evento-rechazo", "", 72_000_000),
                ("archivar_solicitud", "Jefe-Administrativo", 600_000),
            ],
        ),
        # Recorrido 4 (3 instancias): No viable (5 o más cursos activos)
        (
            3,
            [
                ("recibir_solicitud", "", 120),
                ("evaluar_viabilidad", "", 250),
                ("notificar_decision", "", 180),
            ],
        ),
        # Recorrido 5 (2 instancias): Cierre por falta de respuesta tras 2 recordatorios
        (
            2,
            [
                ("recibir_solicitud", "", 160),
                ("evaluar_viabilidad", "", 310),
                ("registrar_elaborar_propuesta", "Jefe-Administrativo", 1_900_000),
                ("enviar_propuesta", "", 205),
                ("evento-5-dias", "", 432_000_000),
                ("enviar_recordatorios", "", 220),
                ("evento-5-dias", "", 432_000_000),
                ("enviar_recordatorios", "", 220),
                ("evento-5-dias", "", 432_000_000),
                ("archivar_solicitud", "Jefe-Administrativo", 540_000),
            ],
        ),
    ]

    tz = datetime.timezone(datetime.timedelta(hours=-3))
    inicio_base = datetime.datetime(2026, 11, 1, 9, 0, 0, tzinfo=tz)
    filas = []
    nro_caso = 1

    for cantidad, pasos in recorridos:
        for i in range(cantidad):
            case_id = f"ejemplo-{nro_caso:03d}"
            reloj = inicio_base + datetime.timedelta(hours=nro_caso * 6 + i)
            for act_id, assignee, duracion_ms in pasos:
                inicio_ev = reloj
                fin_ev = inicio_ev + datetime.timedelta(milliseconds=max(duracion_ms, 1000))
                filas.append({
                    "case_id": case_id,
                    "activity": ACTIVIDADES_NEGOCIACION[act_id],
                    "activity_id": act_id,
                    "timestamp": inicio_ev.strftime("%Y-%m-%dT%H:%M:%S-03:00"),
                    "end_time": fin_ev.strftime("%Y-%m-%dT%H:%M:%S-03:00"),
                    "assignee": assignee,
                    "duration_ms": duracion_ms,
                })
                reloj = fin_ev + datetime.timedelta(minutes=5)
            nro_caso += 1

    return filas


def guardar_csv(filas: list[dict], salida: Path) -> None:
    salida.parent.mkdir(parents=True, exist_ok=True)
    campos = ["case_id", "activity", "activity_id", "timestamp", "end_time", "assignee", "duration_ms"]
    with salida.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=campos)
        writer.writeheader()
        writer.writerows(filas)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Exporta las actividades históricas de la etapa de negociación de Flowable a CSV."
    )
    parser.add_argument(
        "--salida",
        default=str(Path(__file__).resolve().parent / "negociacion_history.csv"),
        help="Ruta del archivo CSV a generar (por defecto: mineria-procesos/negociacion_history.csv)",
    )
    parser.add_argument(
        "--ejemplo",
        action="store_true",
        help="Generar dataset histórico representativo de la etapa de negociación sin requerir Flowable activo.",
    )
    args = parser.parse_args()
    salida = Path(args.salida)

    if args.ejemplo:
        filas = generar_datos_ejemplo()
        guardar_csv(filas, salida)
        casos = len({f["case_id"] for f in filas})
        print(f"[exportar_history] CSV de ejemplo generado en '{salida}' ({len(filas)} eventos en {casos} instancias).")
        return 0

    env = cargar_env()
    base_url = env.get("FLOWABLE_REST_URL", "http://localhost:8080/flowable-ui/process-api")
    usuario = env.get("FLOWABLE_REST_USUARIO", "admin")
    clave = env.get("FLOWABLE_REST_CLAVE", "test")
    process_key = env.get("FLOWABLE_PROCESO_CLAVE", "process")

    resumen: dict = {"excluidos": 0}
    try:
        print(f"[exportar_history] Consultando historial en {base_url} (proceso='{process_key}')...")
        filas = consultar_flowable_rest(base_url, usuario, clave, process_key, resumen)
    except (urllib.error.URLError, TimeoutError, ConnectionError, OSError, json.JSONDecodeError) as exc:
        print(
            f"[exportar_history] ERROR: No se pudo conectar a Flowable REST ({exc}). "
            "Verificá que Flowable esté levantado y las credenciales configuradas en .env.",
            file=sys.stderr,
        )
        return 1

    excluidos = resumen.get("excluidos", 0)
    if not filas:
        print(
            f"[exportar_history] ERROR: Flowable no tiene instancias con la negociación terminada "
            f"(se descartaron {excluidos} instancias en curso o borradas a mitad de camino).",
            file=sys.stderr,
        )
        return 1

    guardar_csv(filas, salida)
    casos = len({f["case_id"] for f in filas})
    print(
        f"[exportar_history] Archivo CSV exportado en '{salida}': {len(filas)} eventos de {casos} instancias "
        f"(se descartaron {excluidos} instancias en curso o borradas)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
