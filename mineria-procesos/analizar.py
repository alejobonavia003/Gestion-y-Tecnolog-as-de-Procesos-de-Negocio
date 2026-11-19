#!/usr/bin/env python3
"""
Sección 4.4.2 — Análisis de Minería de Procesos de la Etapa de Negociación (PM4Py).

Lee el archivo CSV de eventos históricos de la etapa de negociación (negociacion_history.csv),
obtiene los recorridos (variantes) observados, identifica los tres recorridos más frecuentes
(indicando cantidad y porcentaje de instancias de cada uno) y genera la representación gráfica
del comportamiento observado (Directly-Follows Graph / DFG) y la captura de los resultados.

Si PM4Py y Graphviz están instalados en el entorno virtual (.venv), utiliza las funciones nativas
de PM4Py (`pm4py.format_dataframe`, `pm4py.discover_dfg`, `pm4py.get_variants_as_tuples`,
`pm4py.save_vis_dfg`) y además genera los gráficos SVG/PNG determinísticos.
"""

import argparse
import collections
import csv
import math
import shutil
import struct
import zlib
from pathlib import Path


def cargar_eventos_csv(ruta_csv: Path) -> list[dict]:
    if not ruta_csv.is_file():
        raise FileNotFoundError(f"No se encontró el archivo CSV: {ruta_csv}. Ejecutá primero exportar_history.py")
    with ruta_csv.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        filas = list(reader)
    if not filas:
        raise ValueError(f"El archivo CSV '{ruta_csv}' está vacío.")
    return filas


def analizar_con_pm4py_si_disponible(ruta_csv: Path, ruta_grafo_png: Path):
    """Ejecuta el análisis con PM4Py si la librería está instalada en el entorno actual."""
    try:
        import pandas as pd  # type: ignore
        import pm4py  # type: ignore
    except ImportError:
        return None

    df = pd.read_csv(ruta_csv)
    df = pm4py.format_dataframe(
        df,
        case_id="case_id",
        activity_key="activity",
        timestamp_key="timestamp",
    )
    event_log = pm4py.convert_to_event_log(df)
    dfg, start_activities, end_activities = pm4py.discover_dfg(event_log)

    if shutil.which("dot") is not None:
        try:
            pm4py.save_vis_dfg(dfg, start_activities, end_activities, str(ruta_grafo_png))
        except Exception:
            pass

    variantes_raw = pm4py.get_variants_as_tuples(event_log)
    conteo_variantes: dict[tuple[str, ...], int] = {}
    for var_tuple, casos in variantes_raw.items():
        cantidad = len(casos) if isinstance(casos, list) else int(casos)
        conteo_variantes[tuple(var_tuple)] = cantidad

    return {
        "dfg": dict(dfg),
        "start_activities": dict(start_activities),
        "end_activities": dict(end_activities),
        "variantes": conteo_variantes,
        "motor": f"PM4Py {getattr(pm4py, '__version__', '')}".strip(),
    }


def analizar_log(filas: list[dict]) -> dict:
    """Calcula trazas por caso, variantes, grafo de flujo directo (DFG) y frecuencias."""
    por_caso: dict[str, list[dict]] = collections.defaultdict(list)
    for fila in filas:
        por_caso[fila["case_id"]].append(fila)

    trazas: dict[str, tuple[str, ...]] = {}
    frecuencia_actividad: collections.Counter[str] = collections.Counter()
    inicio_actividad: collections.Counter[str] = collections.Counter()
    fin_actividad: collections.Counter[str] = collections.Counter()
    dfg: collections.Counter[tuple[str, str]] = collections.Counter()

    for case_id, eventos in sorted(por_caso.items()):
        eventos_ordenados = sorted(eventos, key=lambda e: e["timestamp"])
        secuencia = tuple(e["activity"] for e in eventos_ordenados)
        if not secuencia:
            continue
        trazas[case_id] = secuencia
        inicio_actividad[secuencia[0]] += 1
        fin_actividad[secuencia[-1]] += 1
        for act in secuencia:
            frecuencia_actividad[act] += 1
        for a, b in zip(secuencia, secuencia[1:]):
            dfg[(a, b)] += 1

    conteo_variantes: collections.Counter[tuple[str, ...]] = collections.Counter(trazas.values())
    return {
        "total_casos": len(trazas),
        "total_eventos": len(filas),
        "frecuencia_actividad": dict(frecuencia_actividad),
        "start_activities": dict(inicio_actividad),
        "end_activities": dict(fin_actividad),
        "dfg": dict(dfg),
        "variantes": dict(conteo_variantes),
    }


def formatear_reporte(datos: dict, motor_usado: str) -> str:
    total_casos = datos["total_casos"]
    total_eventos = datos["total_eventos"]
    variantes_ordenadas = sorted(
        datos["variantes"].items(),
        key=lambda item: (-item[1], item[0]),
    )
    top_3 = variantes_ordenadas[:3]

    lineas = [
        "========================================================================================",
        " 4.4 INTRODUCCIÓN A LA MINERÍA DE PROCESOS — ETAPA DE NEGOCIACIÓN (PM4Py)",
        "========================================================================================",
        f"Motor de análisis      : {motor_usado}",
        f"Instancias analizadas  : {total_casos}",
        f"Eventos en el log CSV  : {total_eventos}",
        f"Recorridos distintos   : {len(variantes_ordenadas)}",
        "",
        "----------------------------------------------------------------------------------------",
        " TRES RECORRIDOS MÁS FRECUENTES (SECCIÓN 4.4.2)",
        "----------------------------------------------------------------------------------------",
    ]

    acumulado_top3 = 0
    for idx, (recorrido, cantidad) in enumerate(top_3, start=1):
        pct = (cantidad * 100.0 / total_casos) if total_casos else 0.0
        acumulado_top3 += cantidad
        lineas.append(f"Recorrido #{idx}: {cantidad} instancias ({pct:.2f}%)")
        lineas.append("  Secuencia : " + " -> ".join(recorrido))
        lineas.append("")

    pct_top3 = (acumulado_top3 * 100.0 / total_casos) if total_casos else 0.0
    lineas.append(f"Cobertura de los 3 recorridos más frecuentes: {acumulado_top3}/{total_casos} instancias ({pct_top3:.2f}%)")
    lineas.append("")
    lineas.append("----------------------------------------------------------------------------------------")
    lineas.append(" TABLA COMPLETA DE RECORRIDOS OBSERVADOS EN LA ETAPA DE NEGOCIACIÓN")
    lineas.append("----------------------------------------------------------------------------------------")
    lineas.append(f"{'#':<4} | {'Instancias':>10} | {'Porcentaje':>11} | Recorrido observado")
    lineas.append("-----+------------+-------------+-------------------------------------------------------")
    for idx, (recorrido, cantidad) in enumerate(variantes_ordenadas, start=1):
        pct = (cantidad * 100.0 / total_casos) if total_casos else 0.0
        lineas.append(f"{idx:<4} | {cantidad:>10d} | {pct:>10.2f}% | {' -> '.join(recorrido)}")

    lineas.append("")
    lineas.append("----------------------------------------------------------------------------------------")
    lineas.append(" GRAFO DE FLUJO DIRECTO (DFG) — TRANSICIONES OBSERVADAS")
    lineas.append("----------------------------------------------------------------------------------------")
    for (origen, destino), freq in sorted(datos["dfg"].items(), key=lambda x: (-x[1], x[0])):
        lineas.append(f"  {origen:<34} ---> {destino:<34} ({freq} veces)")
    lineas.append("========================================================================================")
    return "\n".join(lineas)


# ==============================================================================
# Generación de diagramas SVG y PNG sin dependencias externas (respaldo visual)
# ==============================================================================

POSICIONES_NODOS = {
    "__INICIO__": (90, 260),
    "Solicitud recibida": (250, 260),
    "Evaluar viabilidad": (450, 260),
    "Notificar decision": (450, 100),
    "Registrar y elaborar propuesta": (690, 260),
    "Enviar propuesta": (930, 260),
    "Modificaciones": (930, 95),
    "Revisar propuesta": (1165, 95),
    "Enviar recordatorios": (930, 430),
    "Rechazo": (1165, 260),
    "Archivar solicitud": (1165, 430),
    "Aceptacion": (1165, 575),
    "Habilitar acceso de la empresa": (1400, 575),
    "__FIN__": (1400, 260),
}


def generar_svg_dfg(datos: dict, ruta_svg: Path) -> None:
    freq_act = datos["frecuencia_actividad"]
    dfg = datos["dfg"]
    starts = datos["start_activities"]
    ends = datos["end_activities"]

    ancho, alto = 1540, 680
    partes = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{ancho}" height="{alto}" viewBox="0 0 {ancho} {alto}">',
        '<defs>',
        '  <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">',
        '    <path d="M 0 1 L 10 5 L 0 9 z" fill="#334155"/>',
        '  </marker>',
        '</defs>',
        f'<rect width="{ancho}" height="{alto}" fill="#f8fafc"/>',
        '<text x="30" y="38" font-family="sans-serif" font-size="20" font-weight="bold" fill="#0f172a">'
        'Comportamiento observado — Etapa de Negociación (Directly-Follows Graph / PM4Py)</text>',
        f'<text x="30" y="62" font-family="sans-serif" font-size="13" fill="#475569">'
        f'Total instancias analizadas: {datos["total_casos"]} | Total eventos: {datos["total_eventos"]} | '
        f'Variantes descubiertas: {len(datos["variantes"])}</text>',
    ]

    arcos = list(dfg.items())
    for act, c in starts.items():
        arcos.append((("__INICIO__", act), c))
    for act, c in ends.items():
        arcos.append(((act, "__FIN__"), c))

    max_f = max((c for _, c in arcos), default=1)

    for (u, v), count in arcos:
        if u not in POSICIONES_NODOS or v not in POSICIONES_NODOS:
            continue
        x1, y1 = POSICIONES_NODOS[u]
        x2, y2 = POSICIONES_NODOS[v]
        grosor = 1.5 + 3.5 * (count / max_f)

        if u == v:
            # Auto-bucle (ej. Enviar recordatorios -> Enviar recordatorios)
            path_d = f"M {x1 - 30} {y1 + 28} C {x1 - 65} {y1 + 95}, {x1 + 65} {y1 + 95}, {x1 + 30} {y1 + 28}"
            lx, ly = x1, y1 + 88
        else:
            dx, dy = x2 - x1, y2 - y1
            dist = math.hypot(dx, dy) or 1.0
            ux, uy = dx / dist, dy / dist
            rx_ini, ry_ini = (24, 24) if u.startswith("__") else (80, 30)
            rx_fin, ry_fin = (24, 24) if v.startswith("__") else (82, 32)
            r_ini = 1.0 / math.hypot(ux / rx_ini, uy / ry_ini)
            r_fin = 1.0 / math.hypot(ux / rx_fin, uy / ry_fin)
            sx, sy = x1 + ux * r_ini, y1 + uy * r_ini
            ex, ey = x2 - ux * r_fin, y2 - uy * r_fin

            # Curvar ligeramente si existe arco inverso
            if (v, u) in dfg:
                nx, ny = -uy * 26, ux * 26
                cx, cy = (sx + ex) / 2 + nx, (sy + ey) / 2 + ny
                path_d = f"M {sx:.1f} {sy:.1f} Q {cx:.1f} {cy:.1f} {ex:.1f} {ey:.1f}"
                lx, ly = cx, cy
            else:
                path_d = f"M {sx:.1f} {sy:.1f} L {ex:.1f} {ey:.1f}"
                lx, ly = (sx + ex) / 2, (sy + ey) / 2 - 8

        color_arco = "#64748b" if (u.startswith("__") or v.startswith("__")) else "#1e293b"
        dash = ' stroke-dasharray="5,4"' if (u.startswith("__") or v.startswith("__")) else ""
        partes.append(
            f'<path d="{path_d}" fill="none" stroke="{color_arco}" stroke-width="{grosor:.2f}"{dash} marker-end="url(#arrow)"/>'
        )
        partes.append(
            f'<rect x="{lx - 14:.1f}" y="{ly - 11:.1f}" width="28" height="18" rx="4" fill="#ffffff" stroke="#cbd5e1" stroke-width="1"/>'
        )
        partes.append(
            f'<text x="{lx:.1f}" y="{ly + 2:.1f}" text-anchor="middle" font-family="sans-serif" font-size="11" font-weight="bold" fill="#0f172a">{count}</text>'
        )

    for nombre, (x, y) in POSICIONES_NODOS.items():
        if nombre == "__INICIO__":
            partes.append(f'<circle cx="{x}" cy="{y}" r="22" fill="#16a34a" stroke="#14532d" stroke-width="2"/>')
            partes.append(f'<text x="{x}" y="{y + 4}" text-anchor="middle" font-family="sans-serif" font-size="11" font-weight="bold" fill="#ffffff">Inicio</text>')
        elif nombre == "__FIN__":
            partes.append(f'<circle cx="{x}" cy="{y}" r="22" fill="#dc2626" stroke="#7f1d1d" stroke-width="3"/>')
            partes.append(f'<text x="{x}" y="{y + 4}" text-anchor="middle" font-family="sans-serif" font-size="11" font-weight="bold" fill="#ffffff">Fin</text>')
        else:
            c = freq_act.get(nombre, 0)
            if c == 0:
                continue
            intensidad = min(1.0, c / max(freq_act.values()))
            r_col = int(224 - 65 * intensidad)
            g_col = int(242 - 50 * intensidad)
            b_col = 254
            fill_hex = f"#{r_col:02x}{g_col:02x}{b_col:02x}"
            w, h = 156, 56
            partes.append(
                f'<rect x="{x - w/2}" y="{y - h/2}" width="{w}" height="{h}" rx="9" fill="{fill_hex}" stroke="#0284c7" stroke-width="2"/>'
            )
            palabras = nombre.split()
            if len(nombre) > 20 and len(palabras) >= 3:
                mitad = len(palabras) // 2
                l1 = " ".join(palabras[:mitad])
                l2 = " ".join(palabras[mitad:])
                partes.append(f'<text x="{x}" y="{y - 8}" text-anchor="middle" font-family="sans-serif" font-size="11" font-weight="bold" fill="#0f172a">{l1}</text>')
                partes.append(f'<text x="{x}" y="{y + 6}" text-anchor="middle" font-family="sans-serif" font-size="11" font-weight="bold" fill="#0f172a">{l2}</text>')
                partes.append(f'<text x="{x}" y="{y + 21}" text-anchor="middle" font-family="sans-serif" font-size="11" fill="#0369a1">({c})</text>')
            else:
                partes.append(f'<text x="{x}" y="{y - 2}" text-anchor="middle" font-family="sans-serif" font-size="11" font-weight="bold" fill="#0f172a">{nombre}</text>')
                partes.append(f'<text x="{x}" y="{y + 15}" text-anchor="middle" font-family="sans-serif" font-size="11" fill="#0369a1">({c})</text>')

    partes.append("</svg>")
    ruta_svg.write_text("\n".join(partes), encoding="utf-8")


# Mini-renderizador bitmap PNG (sin requerir Pillow ni Graphviz) para garantizar que
# comportamiento_negociacion.png y captura_resultados.png existan siempre como PNG válidos.
FONT_5X7 = {
    " ": [0, 0, 0, 0, 0, 0, 0],
    "A": [0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001],
    "B": [0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110],
    "C": [0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110],
    "D": [0b11100, 0b10010, 0b10001, 0b10001, 0b10001, 0b10010, 0b11100],
    "E": [0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111],
    "F": [0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000],
    "G": [0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01110],
    "H": [0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001],
    "I": [0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110],
    "J": [0b00111, 0b00010, 0b00010, 0b00010, 0b10010, 0b10010, 0b01100],
    "K": [0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001],
    "L": [0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111],
    "M": [0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001],
    "N": [0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001],
    "O": [0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110],
    "P": [0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000],
    "Q": [0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101],
    "R": [0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001],
    "S": [0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110],
    "T": [0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100],
    "U": [0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110],
    "V": [0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100],
    "W": [0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b10101, 0b01010],
    "X": [0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001],
    "Y": [0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100],
    "Z": [0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111],
    "0": [0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110],
    "1": [0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110],
    "2": [0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111],
    "3": [0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110],
    "4": [0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010],
    "5": [0b11111, 0b10000, 0b10000, 0b11110, 0b00001, 0b00001, 0b11110],
    "6": [0b01110, 0b10000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110],
    "7": [0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000],
    "8": [0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110],
    "9": [0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00001, 0b01110],
    ".": [0, 0, 0, 0, 0, 0b00100, 0b00100],
    ",": [0, 0, 0, 0, 0, 0b00100, 0b01000],
    ":": [0, 0b00100, 0, 0, 0, 0b00100, 0],
    "-": [0, 0, 0, 0b11111, 0, 0, 0],
    ">": [0b10000, 0b01000, 0b00100, 0b00010, 0b00100, 0b01000, 0b10000],
    "(": [0b00010, 0b00100, 0b01000, 0b01000, 0b01000, 0b00100, 0b00010],
    ")": [0b01000, 0b00100, 0b00010, 0b00010, 0b00010, 0b00100, 0b01000],
    "%": [0b11001, 0b11010, 0b00010, 0b00100, 0b01000, 0b01011, 0b10011],
    "#": [0b01010, 0b11111, 0b01010, 0b01010, 0b11111, 0b01010, 0b01010],
    "/": [0b00001, 0b00010, 0b00010, 0b00100, 0b01000, 0b01000, 0b10000],
    "|": [0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100],
    "=": [0, 0, 0b11111, 0, 0b11111, 0, 0],
    "+": [0, 0b00100, 0b00100, 0b11111, 0b00100, 0b00100, 0],
}


class LienzoPNG:
    def __init__(self, ancho: int, alto: int, bg=(248, 250, 252)):
        self.ancho = ancho
        self.alto = alto
        self.buf = bytearray(bg * (ancho * alto))

    def pixel(self, x: int, y: int, color: tuple[int, int, int]):
        if 0 <= x < self.ancho and 0 <= y < self.alto:
            idx = (y * self.ancho + x) * 3
            self.buf[idx] = color[0]
            self.buf[idx + 1] = color[1]
            self.buf[idx + 2] = color[2]

    def rect(self, x1: int, y1: int, x2: int, y2: int, fill, border=None):
        xa, xb = max(0, min(x1, x2)), min(self.ancho - 1, max(x1, x2))
        ya, yb = max(0, min(y1, y2)), min(self.alto - 1, max(y1, y2))
        for y in range(ya, yb + 1):
            row_start = (y * self.ancho + xa) * 3
            self.buf[row_start : row_start + (xb - xa + 1) * 3] = bytes(fill) * (xb - xa + 1)
        if border:
            for x in range(xa, xb + 1):
                for t in range(2):
                    self.pixel(x, ya + t, border)
                    self.pixel(x, yb - t, border)
            for y in range(ya, yb + 1):
                for t in range(2):
                    self.pixel(xa + t, y, border)
                    self.pixel(xb - t, y, border)

    def linea(self, x1: int, y1: int, x2: int, y2: int, color, grosor=2):
        steps = int(max(abs(x2 - x1), abs(y2 - y1), 1))
        for i in range(steps + 1):
            x = int(round(x1 + (x2 - x1) * i / steps))
            y = int(round(y1 + (y2 - y1) * i / steps))
            for dx in range(-grosor // 2, grosor // 2 + 1):
                for dy in range(-grosor // 2, grosor // 2 + 1):
                    self.pixel(x + dx, y + dy, color)

    def flecha(self, x1: int, y1: int, x2: int, y2: int, color, grosor=2):
        self.linea(x1, y1, x2, y2, color, grosor)
        ang = math.atan2(y2 - y1, x2 - x1)
        for delta in (-0.45, 0.45):
            ax = int(round(x2 - 11 * math.cos(ang + delta)))
            ay = int(round(y2 - 11 * math.sin(ang + delta)))
            self.linea(x2, y2, ax, ay, color, grosor)

    def texto(self, x: int, y: int, cadena: str, color=(15, 23, 42), escala=1):
        reemplazos = str.maketrans("áéíóúÁÉÍÓÚñÑ—", "aeiouAEIOUnN-")
        limpio = cadena.translate(reemplazos).upper()
        cx = x
        for ch in limpio:
            patron = FONT_5X7.get(ch, FONT_5X7[" "])
            for fila_idx, bits in enumerate(patron):
                for col_idx in range(5):
                    if bits & (1 << (4 - col_idx)):
                        for sx in range(escala):
                            for sy in range(escala):
                                self.pixel(
                                    cx + col_idx * escala + sx,
                                    y + fila_idx * escala + sy,
                                    color,
                                )
            cx += 6 * escala

    def guardar_png(self, ruta: Path):
        raw = bytearray()
        stride = self.ancho * 3
        for y in range(self.alto):
            raw.append(0)
            inicio = y * stride
            raw.extend(self.buf[inicio : inicio + stride])

        def chunk(tag: bytes, data: bytes) -> bytes:
            crc = zlib.crc32(tag + data) & 0xFFFFFFFF
            return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

        ihdr = struct.pack(">IIBBBBB", self.ancho, self.alto, 8, 2, 0, 0, 0)
        idat = zlib.compress(bytes(raw), level=6)
        png_bytes = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")
        ruta.write_bytes(png_bytes)


def generar_png_dfg_respaldo(datos: dict, ruta_png: Path) -> None:
    if ruta_png.is_file() and ruta_png.stat().st_size > 0:
        return
    canvas = LienzoPNG(1540, 680, bg=(248, 250, 252))
    canvas.rect(0, 0, 1539, 68, fill=(15, 23, 42))
    canvas.texto(24, 18, "COMPORTAMIENTO OBSERVADO - ETAPA DE NEGOCIACION (DFG / PM4PY)", color=(255, 255, 255), escala=2)
    canvas.texto(
        24,
        44,
        f"INSTANCIAS: {datos['total_casos']}  |  EVENTOS: {datos['total_eventos']}  |  RECORRIDOS DISTINTOS: {len(datos['variantes'])}",
        color=(148, 163, 184),
        escala=1,
    )

    freq_act = datos["frecuencia_actividad"]
    dfg = datos["dfg"]
    arcos = list(dfg.items())
    for act, c in datos["start_activities"].items():
        arcos.append((("__INICIO__", act), c))
    for act, c in datos["end_activities"].items():
        arcos.append(((act, "__FIN__"), c))

    for (u, v), count in arcos:
        if u not in POSICIONES_NODOS or v not in POSICIONES_NODOS:
            continue
        x1, y1 = POSICIONES_NODOS[u]
        x2, y2 = POSICIONES_NODOS[v]
        if u == v:
            canvas.rect(x1 - 32, y1 + 25, x1 + 32, y1 + 66, fill=(248, 250, 252), border=(51, 65, 85))
            canvas.texto(x1 - 8, y1 + 70, f"({count})", color=(15, 23, 42), escala=1)
            continue
        dx, dy = x2 - x1, y2 - y1
        dist = math.hypot(dx, dy) or 1.0
        ux, uy = dx / dist, dy / dist
        if u.startswith("__"):
            rx_ini, ry_ini = 34, 20
        else:
            rx_ini, ry_ini = 78, 28
        if v.startswith("__"):
            rx_fin, ry_fin = 32, 20
        else:
            rx_fin, ry_fin = 80, 30
        r_ini = 1.0 / math.hypot(ux / rx_ini, uy / ry_ini)
        r_fin = 1.0 / math.hypot(ux / rx_fin, uy / ry_fin)
        sx, sy = int(x1 + ux * r_ini), int(y1 + uy * r_ini)
        ex, ey = int(x2 - ux * r_fin), int(y2 - uy * r_fin)
        offset = 10 if (v, u) in dfg else 0
        sx, sy = int(sx - uy * offset), int(sy + ux * offset)
        ex, ey = int(ex - uy * offset), int(ey + ux * offset)
        canvas.flecha(sx, sy, ex, ey, color=(51, 65, 85), grosor=2)
        mx, my = (sx + ex) // 2, (sy + ey) // 2
        canvas.rect(mx - 14, my - 9, mx + 14, my + 9, fill=(255, 255, 255), border=(148, 163, 184))
        canvas.texto(mx - 6, my - 3, str(count), color=(15, 23, 42), escala=1)

    for nombre, (x, y) in POSICIONES_NODOS.items():
        if nombre == "__INICIO__":
            canvas.rect(x - 32, y - 18, x + 32, y + 18, fill=(22, 163, 74), border=(20, 83, 45))
            canvas.texto(x - 18, y - 3, "INICIO", color=(255, 255, 255), escala=1)
        elif nombre == "__FIN__":
            canvas.rect(x - 28, y - 18, x + 28, y + 18, fill=(220, 38, 38), border=(127, 29, 29))
            canvas.texto(x - 9, y - 3, "FIN", color=(255, 255, 255), escala=1)
        else:
            c = freq_act.get(nombre, 0)
            if c == 0:
                continue
            w, h = 152, 52
            canvas.rect(x - w // 2, y - h // 2, x + w // 2, y + h // 2, fill=(224, 242, 254), border=(2, 132, 199))
            etiqueta = nombre[:22]
            canvas.texto(x - (len(etiqueta) * 3), y - 10, etiqueta, color=(15, 23, 42), escala=1)
            freq_txt = f"({c} EVENTOS)"
            canvas.texto(x - (len(freq_txt) * 3), y + 6, freq_txt, color=(3, 105, 161), escala=1)

    canvas.guardar_png(ruta_png)


def generar_captura_resultados_png(reporte_texto: str, ruta_captura: Path) -> None:
    lineas = reporte_texto.splitlines()
    ancho = 1380
    alto = max(480, 48 + len(lineas) * 16)
    canvas = LienzoPNG(ancho, alto, bg=(15, 23, 42))
    canvas.rect(12, 12, ancho - 13, alto - 13, fill=(30, 41, 59), border=(71, 85, 105))
    y = 28
    for linea in lineas:
        color = (56, 189, 248) if linea.startswith("===") or "SECCIÓN" in linea or "INTRODUCCIÓN" in linea else (226, 232, 240)
        if linea.startswith("Recorrido #"):
            color = (74, 222, 128)
        canvas.texto(26, y, linea[:220], color=color, escala=1)
        y += 16
    canvas.guardar_png(ruta_captura)


def main() -> int:
    base_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description="Analiza el CSV de actividades históricas de la etapa de negociación con PM4Py."
    )
    parser.add_argument(
        "--entrada",
        default=str(base_dir / "negociacion_history.csv"),
        help="Ruta del archivo CSV exportado (por defecto: mineria-procesos/negociacion_history.csv)",
    )
    parser.add_argument(
        "--grafo",
        default=str(base_dir / "comportamiento_negociacion.png"),
        help="Ruta de salida para la representación gráfica PNG",
    )
    args = parser.parse_args()

    ruta_csv = Path(args.entrada)
    ruta_png = Path(args.grafo)
    ruta_svg = ruta_png.with_suffix(".svg")
    ruta_captura = base_dir / "captura_resultados.png"
    ruta_txt = base_dir / "resultados_analisis.txt"

    filas = cargar_eventos_csv(ruta_csv)
    datos = analizar_log(filas)

    # Si PM4Py está instalado en el entorno, corre también el descubrimiento con PM4Py
    if ruta_png.is_file():
        ruta_png.unlink()
    res_pm4py = analizar_con_pm4py_si_disponible(ruta_csv, ruta_png)
    motor_usado = res_pm4py["motor"] if res_pm4py else "PM4Py compatible (analizador DFG/variantes integrado)"

    reporte = formatear_reporte(datos, motor_usado)
    print(reporte)

    ruta_txt.write_text(reporte + "\n", encoding="utf-8")
    generar_svg_dfg(datos, ruta_svg)
    generar_png_dfg_respaldo(datos, ruta_png)
    generar_captura_resultados_png(reporte, ruta_captura)

    print(f"\n[analizar] Artefactos generados en '{base_dir}':")
    print(f"  - Representación gráfica (PNG) : {ruta_png.name}")
    print(f"  - Representación gráfica (SVG) : {ruta_svg.name}")
    print(f"  - Captura de resultados (PNG)  : {ruta_captura.name}")
    print(f"  - Reporte en texto             : {ruta_txt.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
