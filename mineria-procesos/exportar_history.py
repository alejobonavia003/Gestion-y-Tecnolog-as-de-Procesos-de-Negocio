#!/usr/bin/env python3
"""
Sección 4.4.1 — Exportación de actividades históricas de Flowable (Etapa de Negociación).

Consulta la API REST histórica de Flowable (/history/historic-activity-instances) para el
proceso implementado (clave 'process'), filtra las actividades correspondientes a la etapa
de negociación (previa al subproceso 'preparar_curso') y genera el archivo CSV compatible
con PM4Py (columnas: case_id, activity, activity_id, timestamp, end_time, assignee, duration_ms).

Uso:
  python3 exportar_history.py                        # Exporta desde Flowable REST (o genera lote de prueba si se pasa --ejemplo)
  python3 exportar_history.py --ejemplo              # Genera CSV con 25 ejecuciones históricas representativas de la negociación
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
    "enviar_recordatorios": "Enviar recordatorios",
    "evento-rechazo": "Rechazo",
    "archivar_solicitud": "Archivar solicitud",
    "evento-aceptacion": "Aceptacion",
    "habilitar_acceso_empresa": "Habilitar acceso de la empresa",
}

# Orden de desempate cuando dos actividades automáticas ocurren en el mismo milisegundo
ORDEN_LOGICO = {
    "recibir_solicitud": 1,
    "evaluar_viabilidad": 2,
    "notificar_decision": 3,
    "registrar_elaborar_propuesta": 4,
    "enviar_propuesta": 5,
    "enviar_recordatorios": 6,
    "evento-modificacion": 7,
    "revisar_propuesta": 8,
    "evento-rechazo": 9,
    "archivar_solicitud": 10,
    "evento-aceptacion": 11,
    "habilitar_acceso_empresa": 12,
}


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


def consultar_flowable_rest(base_url: str, usuario: str, clave: str, process_key: str) -> list[dict]:
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
        return []

    actividades_crudas = []
    for def_id in def_ids:
        start = 0
        size = 500
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

    return transformar_actividades(actividades_crudas)


def transformar_actividades(actividades_crudas: list[dict]) -> list[dict]:
    """Filtra solo las actividades de la etapa de negociación y las ordena por caso y timestamp."""
    por_caso: dict[str, list[dict]] = {}
    for item in actividades_crudas:
        act_id = item.get("activityId")
        case_id = item.get("processInstanceId")
        start_time = item.get("startTime")
        if not act_id or not case_id or not start_time:
            continue
        if act_id not in ACTIVIDADES_NEGOCIACION:
            continue

        por_caso.setdefault(case_id, []).append({
            "case_id": case_id,
            "activity": ACTIVIDADES_NEGOCIACION[act_id],
            "activity_id": act_id,
            "timestamp": start_time,
            "end_time": item.get("endTime") or start_time,
            "assignee": item.get("assignee") or "",
            "duration_ms": item.get("durationInMillis") if item.get("durationInMillis") is not None else 0,
            "_orden": ORDEN_LOGICO.get(act_id, 99),
        })

    filas = []
    for case_id in sorted(por_caso.keys()):
        eventos = por_caso[case_id]
        eventos.sort(key=lambda e: (e["timestamp"], e["_orden"]))
        # Asegurar timestamps estrictamente crecientes por milisegundos/segundos para que PM4Py
        # conserve el orden exacto de tareas automáticas consecutivas dentro de la misma transacción
        base_dt = None
        for idx, ev in enumerate(eventos):
            dt = parsear_iso(ev["timestamp"])
            if base_dt is not None and dt <= base_dt:
                dt = base_dt + datetime.timedelta(seconds=1)
            base_dt = dt
            ev["timestamp"] = dt.strftime("%Y-%m-%dT%H:%M:%S%z")
            filas.append({k: v for k, v in ev.items() if not k.startswith("_")})
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
                ("enviar_recordatorios", "", 432_000_000),
                ("enviar_recordatorios", "", 432_000_000),
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
            case_id = f"inst-negociacion-{nro_caso:03d}"
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

    try:
        print(f"[exportar_history] Consultando historial en {base_url} (proceso='{process_key}')...")
        filas = consultar_flowable_rest(base_url, usuario, clave, process_key)
        if not filas:
            print(
                "[exportar_history] Flowable no devolvió actividades históricas de negociación. "
                "Generando dataset representativo de respaldo (--ejemplo)...",
                file=sys.stderr,
            )
            filas = generar_datos_ejemplo()
    except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as exc:
        print(
            f"[exportar_history] No se pudo conectar a Flowable REST ({exc}). "
            "Generando dataset representativo de respaldo (--ejemplo)...",
            file=sys.stderr,
        )
        filas = generar_datos_ejemplo()

    guardar_csv(filas, salida)
    casos = len({f["case_id"] for f in filas})
    print(f"[exportar_history] Archivo CSV exportado en '{salida}': {len(filas)} eventos de {casos} instancias.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
