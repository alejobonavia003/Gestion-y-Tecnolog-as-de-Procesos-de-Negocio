#!/usr/bin/env python3
"""Corrige las asociaciones de compensación que el Flowable Modeler 6.8.0 rompe.

Bug: cuando el modelo tiene un pool, BpmnJsonConverter convierte las asociaciones
(evento de borde de compensación -> tarea compensadora) en sequenceFlow al importar,
exportar y publicar. El motor busca la asociación al terminar la actividad con el borde
y, si no la encuentra, lanza "Compensation activity could not be found".

Este script reemplaza cada sequenceFlow cuyo sourceRef es un boundaryEvent con
compensateEventDefinition por:

    <association id="..." sourceRef="..." targetRef="..." associationDirection="One"></association>

con el mismo id, para que siga coincidiendo con su BPMNEdge. La sustitución se hace
sobre el texto (no se reserializa el XML), así se conservan formato y namespaces.

Uso:
    python3 corregir-asociaciones.py institucion-de-capacitacion.bpmn20.xml
    python3 corregir-asociaciones.py institucion-de-capacitacion.bpmn20.xml --bar
    python3 corregir-asociaciones.py modelo.bpmn -o corregido.bpmn

Con --bar, además arma Organizacion_de_cursos.bar (en la carpeta del script) con:
    organizacionCursos.app      (reutilizado del .bar actual)
    process.bpmn                (el XML corregido)
    dmn-reglaViabilidad.dmn     (reglaViabilidad.dmn)

Sin dependencias externas: solo biblioteca estándar de Python 3.
"""

import argparse
import io
import os
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile
import zlib

CARPETA = os.path.dirname(os.path.abspath(__file__))
BAR_POR_DEFECTO = os.path.join(CARPETA, "Organizacion_de_cursos.bar")
DMN_POR_DEFECTO = os.path.join(CARPETA, "reglaViabilidad.dmn")
APP_EN_BAR = "organizacionCursos.app"
BPMN_EN_BAR = "process.bpmn"
DMN_EN_BAR = "dmn-reglaViabilidad.dmn"

# <sequenceFlow .../>  o  <sequenceFlow ...> ... </sequenceFlow>  (con prefijo opcional)
RE_SEQUENCE_FLOW = re.compile(
    r"<(?P<prefijo>[A-Za-z_][\w.-]*:)?sequenceFlow\b(?P<attrs>[^>]*?)"
    r"(?:/>|>(?P<cuerpo>.*?)</(?P=prefijo)?sequenceFlow\s*>)",
    re.DOTALL,
)
RE_ASSOCIATION = re.compile(r"<(?:[A-Za-z_][\w.-]*:)?association\b")
RE_ATTR = re.compile(r'([\w:.-]+)\s*=\s*(?:"([^"]*)"|\'([^\']*)\')')


def nombre_local(tag):
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


def bordes_de_compensacion(texto):
    """Ids de los boundaryEvent que tienen compensateEventDefinition."""
    raiz = ET.fromstring(texto.encode("utf-8"))
    ids = set()
    for elem in raiz.iter():
        if nombre_local(elem.tag) != "boundaryEvent":
            continue
        if any(nombre_local(hijo.tag) == "compensateEventDefinition" for hijo in elem):
            if elem.get("id"):
                ids.add(elem.get("id"))
    return ids


def atributos(texto_attrs):
    return {m.group(1): (m.group(2) if m.group(2) is not None else m.group(3))
            for m in RE_ATTR.finditer(texto_attrs)}


def corregir(texto):
    """Devuelve (texto_corregido, lista de (id, sourceRef, targetRef) reemplazados)."""
    bordes = bordes_de_compensacion(texto)
    reemplazos = []

    def sustituir(m):
        attrs = atributos(m.group("attrs"))
        origen = attrs.get("sourceRef")
        if origen not in bordes:
            return m.group(0)
        cuerpo = (m.group("cuerpo") or "").strip()
        if "conditionExpression" in cuerpo:
            print(f"  aviso: el flujo {attrs.get('id')} tenía conditionExpression; se descarta",
                  file=sys.stderr)
        prefijo = m.group("prefijo") or ""
        ident, destino = attrs.get("id"), attrs.get("targetRef")
        reemplazos.append((ident, origen, destino))
        return (f'<{prefijo}association id="{ident}" sourceRef="{origen}" '
                f'targetRef="{destino}" associationDirection="One"></{prefijo}association>')

    nuevo = RE_SEQUENCE_FLOW.sub(sustituir, texto)
    return nuevo, reemplazos, bordes


# ---------------------------------------------------------------------------
# Lectura de zips sin directorio central (los .bar del Modeler a veces vienen así)
# ---------------------------------------------------------------------------

def leer_entradas_locales(datos):
    """Lee las entradas de un zip recorriendo los encabezados locales (PK\\x03\\x04)."""
    entradas = {}
    pos = 0
    firma = b"PK\x03\x04"
    while True:
        pos = datos.find(firma, pos)
        if pos < 0:
            break
        (_, _, flags, metodo, _, _, _, tam_comp, _, largo_nombre, largo_extra) = \
            struct.unpack("<IHHHHHIIIHH", datos[pos:pos + 30])
        inicio_nombre = pos + 30
        nombre = datos[inicio_nombre:inicio_nombre + largo_nombre].decode("utf-8", "replace")
        inicio = inicio_nombre + largo_nombre + largo_extra
        if flags & 0x08 and tam_comp == 0:
            # Tamaño en el data descriptor: descomprimir en streaming hasta el final.
            if metodo != 8:
                raise ValueError(f"No se puede leer {nombre}: tamaño desconocido sin deflate")
            d = zlib.decompressobj(-15)
            contenido = d.decompress(datos[inicio:])
            fin = len(datos) - len(d.unused_data)
        else:
            crudo = datos[inicio:inicio + tam_comp]
            if metodo == 0:
                contenido = crudo
            elif metodo == 8:
                contenido = zlib.decompress(crudo, -15)
            else:
                raise ValueError(f"Método de compresión {metodo} no soportado en {nombre}")
            fin = inicio + tam_comp
        entradas[nombre] = contenido
        pos = max(fin, pos + 4)
    return entradas


def leer_zip(ruta):
    with open(ruta, "rb") as f:
        datos = f.read()
    try:
        with zipfile.ZipFile(io.BytesIO(datos)) as z:
            return {i.filename: z.read(i.filename) for i in z.infolist()}
    except zipfile.BadZipFile:
        print(f"  {os.path.basename(ruta)} no tiene directorio central válido; "
              "se leen los encabezados locales", file=sys.stderr)
        return leer_entradas_locales(datos)


def armar_bar(xml_corregido, ruta_bar, ruta_dmn):
    app = None
    if os.path.exists(ruta_bar):
        app = leer_zip(ruta_bar).get(APP_EN_BAR)
    if app is None:
        raise SystemExit(f"ERROR: no se encontró {APP_EN_BAR} en {ruta_bar}")
    with open(ruta_dmn, "rb") as f:
        dmn = f.read()
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(APP_EN_BAR, app)
        z.writestr(BPMN_EN_BAR, xml_corregido.encode("utf-8"))
        z.writestr(DMN_EN_BAR, dmn)
    with open(ruta_bar, "wb") as f:
        f.write(buffer.getvalue())
    # Verificación: el .bar nuevo se abre y tiene las 3 entradas
    with zipfile.ZipFile(ruta_bar) as z:
        nombres = z.namelist()
    print(f"  .bar generado: {ruta_bar}")
    for n in nombres:
        print(f"    - {n}")


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("archivo", help="modelo .bpmn20.xml o .bpmn")
    p.add_argument("-o", "--salida", help="archivo de salida (por defecto, sobrescribe la entrada)")
    p.add_argument("--bar", action="store_true",
                   help="además arma Organizacion_de_cursos.bar")
    p.add_argument("--bar-ruta", default=BAR_POR_DEFECTO, help=argparse.SUPPRESS)
    p.add_argument("--dmn", default=DMN_POR_DEFECTO, help=argparse.SUPPRESS)
    args = p.parse_args()

    with open(args.archivo, "r", encoding="utf-8", newline="") as f:
        texto = f.read()

    nuevo, reemplazos, bordes = corregir(texto)

    # Validación: el XML resultante tiene que parsear
    try:
        ET.fromstring(nuevo.encode("utf-8"))
    except ET.ParseError as e:
        raise SystemExit(f"ERROR: el XML corregido no parsea: {e}")

    print(f"Bordes de compensación encontrados: {len(bordes)}")
    for ident, origen, destino in reemplazos:
        print(f"  sequenceFlow -> association: {ident}  ({origen} -> {destino})")
    asociaciones = len(RE_ASSOCIATION.findall(nuevo))
    print(f"Reemplazadas: {len(reemplazos)}. Asociaciones en el XML: {asociaciones}")

    salida = args.salida or args.archivo
    if nuevo != texto or salida != args.archivo:
        with open(salida, "w", encoding="utf-8", newline="") as f:
            f.write(nuevo)
        print(f"Escrito: {salida}")
    else:
        print("Sin cambios en el XML.")

    if args.bar:
        armar_bar(nuevo, args.bar_ruta, args.dmn)


if __name__ == "__main__":
    main()
