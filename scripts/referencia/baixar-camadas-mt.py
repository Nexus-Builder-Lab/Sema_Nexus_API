#!/usr/bin/env python3
"""
Atualiza os snapshots offline das camadas públicas abertas de Mato Grosso em src/main/resources/referencia/camadas/:
  1. Unidades de Conservação (MMA / CNUC — Portal de Dados Abertos)
  2. Terras Indígenas (FUNAI / CGGEO — GeoServer WFS)
  3. Hidrografia principal níveis 1, 2 e 3 (ANA / SNIRH — ArcGIS REST)

Requer internet apenas ao executar este script manualmente; a API em execução é 100% offline.
Usa somente a biblioteca padrão do Python 3.
"""
import argparse
import datetime
import gzip
import io
import json
import os
import re
import struct
import sys
import urllib.parse
import urllib.request
import zipfile

# Retângulo envolvente de Mato Grosso (mesmo dos CHECKs das tabelas)
MT_LON_MIN, MT_LAT_MIN, MT_LON_MAX, MT_LAT_MAX = -61.7, -18.1, -50.1, -7.3
BBOX_MT = f"{MT_LON_MIN},{MT_LAT_MIN},{MT_LON_MAX},{MT_LAT_MAX}"

CNUC_PACKAGE_API = "https://dados.mma.gov.br/api/3/action/package_show?id=44b6dc8a-dc82-4a84-8d95-1b0da7c85dac"
CNUC_FALLBACK_ZIP = (
    "https://dados.mma.gov.br/dataset/44b6dc8a-dc82-4a84-8d95-1b0da7c85dac/resource/"
    "6ba9a557-87e8-4882-acb7-b3e0f0ea192d/download/shp_cnuc_2025_08.zip"
)
FUNAI_WFS = "https://geoserver.funai.gov.br/geoserver/ows"
ANA_BASE = "https://www.snirh.gov.br/arcgis/rest/services/DADOSABERTOS"
IBGE_MT_URL = "https://servicodados.ibge.gov.br/api/v3/malhas/estados/51?formato=application/vnd.geo+json"

HEADERS = {"User-Agent": "SEMA-Alerta-Referencia/1.0"}


def http_get(url: str, timeout: int = 90) -> bytes:
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def http_json(url: str, timeout: int = 60) -> dict:
    return json.loads(http_get(url, timeout=timeout).decode("utf-8"))


# --------------------------------------------------------------------------- geometria & simplificação


def douglas_peucker(pts: list, tol: float) -> list:
    if len(pts) <= 2:
        return pts
    ax, ay = pts[0][0], pts[0][1]
    bx, by = pts[-1][0], pts[-1][1]
    dx, dy = bx - ax, by - ay
    d2 = dx * dx + dy * dy
    max_d2 = -1.0
    idx = 0
    for i in range(1, len(pts) - 1):
        px, py = pts[i][0], pts[i][1]
        if d2 == 0.0:
            dist2 = (px - ax) ** 2 + (py - ay) ** 2
        else:
            t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / d2))
            dist2 = (px - (ax + t * dx)) ** 2 + (py - (ay + t * dy)) ** 2
        if dist2 > max_d2:
            max_d2 = dist2
            idx = i
    if max_d2 > tol * tol:
        return douglas_peucker(pts[: idx + 1], tol)[:-1] + douglas_peucker(pts[idx:], tol)
    return [pts[0], pts[-1]]


def simplificar_anel(anel: list, tol: float, casas: int) -> list:
    s = douglas_peucker(anel, tol)
    if len(s) < 4:
        s = anel[:4] if len(anel) >= 4 else anel
    out = [[round(p[0], casas), round(p[1], casas)] for p in s]
    if out and out[0] != out[-1]:
        out.append(list(out[0]))
    return out


def simplificar_geometria(geom: dict, tol: float, casas: int = 5) -> dict | None:
    if not geom or "type" not in geom or "coordinates" not in geom:
        return None
    tipo = geom["type"]
    coords = geom["coordinates"]
    if tipo == "Polygon":
        aneis = [r for r in (simplificar_anel(a, tol, casas) for a in coords) if len(r) >= 4]
        return {"type": "Polygon", "coordinates": aneis} if aneis else None
    if tipo == "MultiPolygon":
        polis = []
        for poli in coords:
            aneis = [r for r in (simplificar_anel(a, tol, casas) for a in poli) if len(r) >= 4]
            if aneis:
                polis.append(aneis)
        return {"type": "MultiPolygon", "coordinates": polis} if polis else None
    if tipo == "LineString":
        pts = [[round(p[0], casas), round(p[1], casas)] for p in douglas_peucker(coords, tol)]
        return {"type": "LineString", "coordinates": pts} if len(pts) >= 2 else None
    if tipo == "MultiLineString":
        linhas = [
            [[round(p[0], casas), round(p[1], casas)] for p in douglas_peucker(l, tol)]
            for l in coords
        ]
        linhas = [l for l in linhas if len(l) >= 2]
        return {"type": "MultiLineString", "coordinates": linhas} if linhas else None
    return None


def aneis_poligono(geom: dict) -> list:
    tipo = geom.get("type")
    coords = geom.get("coordinates", [])
    if tipo == "Polygon":
        return coords
    if tipo == "MultiPolygon":
        return [anel for poli in coords for anel in poli]
    return []


def ponto_em_aneis(lon: float, lat: float, aneis: list) -> bool:
    dentro = False
    for anel in aneis:
        n = len(anel)
        j = n - 1
        for i in range(n):
            xi, yi = anel[i][0], anel[i][1]
            xj, yj = anel[j][0], anel[j][1]
            if ((yi > lat) != (yj > lat)) and (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi):
                dentro = not dentro
            j = i
    return dentro


def ponto_perto_mt(lon: float, lat: float, mt_aneis: list, margem: float = 0.015) -> bool:
    if ponto_em_aneis(lon, lat, mt_aneis):
        return True
    for dl, dp in ((margem, 0.0), (-margem, 0.0), (0.0, margem), (0.0, -margem)):
        if ponto_em_aneis(lon + dl, lat + dp, mt_aneis):
            return True
    return False


# --------------------------------------------------------------------------- 1. MMA / CNUC (UCs)


def descobrir_zip_cnuc() -> tuple[str, str]:
    try:
        pkg = http_json(CNUC_PACKAGE_API)["result"]
        candidatos = []
        for res in pkg.get("resources", []):
            url = (res.get("url") or "").strip()
            fmt = (res.get("format") or "").lower()
            nome = (res.get("name") or "").lower()
            if url.startswith("https://dados.mma.gov.br/") and url.lower().endswith(".zip") and ("shp" in fmt or "cnuc" in nome):
                candidatos.append((res.get("last_modified") or "", url, res.get("name") or ""))
        if candidatos:
            candidatos.sort(reverse=True)
            mod, url, _ = candidatos[0]
            m = re.search(r"(\d{4})_(\d{2})", url)
            data_ref = f"{m.group(1)}-{m.group(2)}-01" if m else (mod[:10] or "2025-08-01")
            return url, data_ref
    except Exception as e:
        print(f"Aviso CKAN MMA ({e}); usando URL direta de referência.", file=sys.stderr)
    return CNUC_FALLBACK_ZIP, "2025-08-01"


def area_assinada(anel: list) -> float:
    a = 0.0
    for i in range(len(anel) - 1):
        a += anel[i][0] * anel[i + 1][1] - anel[i + 1][0] * anel[i][1]
    return a * 0.5


def baixar_ucs(tol: float) -> tuple[list, dict]:
    url_zip, data_ref = descobrir_zip_cnuc()
    print(f"Baixando UCs MMA/CNUC de {url_zip} ...")
    bruto = http_get(url_zip, timeout=180)
    with zipfile.ZipFile(io.BytesIO(bruto)) as z:
        nomes = z.namelist()
        shp_nome = next(n for n in nomes if n.lower().endswith(".shp") and "ponto" not in n.lower())
        dbf_nome = shp_nome[:-4] + ".dbf"
        cpg_nome = next((n for n in nomes if n.lower() == (shp_nome[:-4] + ".cpg").lower()), None)
        encoding = z.read(cpg_nome).decode("ascii", "ignore").strip() if cpg_nome else "utf-8"
        if not encoding:
            encoding = "utf-8"
        dbf = z.read(dbf_nome)
        shp = z.read(shp_nome)

    num_rec, hdr_len, rec_len = struct.unpack("<IHH", dbf[4:12])
    campos = []
    pos = 32
    while dbf[pos] != 0x0D:
        nome = dbf[pos : pos + 11].split(b"\x00")[0].decode("latin1")
        flen = dbf[pos + 16]
        campos.append((nome, flen))
        pos += 32

    linhas = []
    off = hdr_len
    for _ in range(num_rec):
        rec = dbf[off : off + rec_len]
        off += rec_len
        p = 1
        row = {}
        for nome, flen in campos:
            row[nome] = rec[p : p + flen].decode(encoding, "replace").strip()
            p += flen
        linhas.append(row)

    feicoes = []
    pos = 100
    idx = 0
    while pos < len(shp):
        _, content_words = struct.unpack(">II", shp[pos : pos + 8])
        pos += 8
        content = shp[pos : pos + content_words * 2]
        pos += content_words * 2
        row = linhas[idx]
        idx += 1

        ufs = [u.strip().upper() for u in (row.get("uf") or "").replace(";", ",").split(",")]
        if "MATO GROSSO" not in ufs:
            continue
        shape_type = struct.unpack("<I", content[0:4])[0]
        if shape_type not in (5, 15, 25):
            continue
        num_parts, num_points = struct.unpack("<II", content[36:44])
        parts = list(struct.unpack("<" + "I" * num_parts, content[44 : 44 + 4 * num_parts]))
        pts_start = 44 + 4 * num_parts
        pts = [struct.unpack("<dd", content[pts_start + k * 16 : pts_start + (k + 1) * 16]) for k in range(num_points)]
        aneis = []
        for k, start in enumerate(parts):
            end = parts[k + 1] if k + 1 < num_parts else num_points
            aneis.append([list(pt) for pt in pts[start:end]])
        polis = []
        for r in aneis:
            if area_assinada(r) <= 0:
                polis.append([r])
            elif polis:
                polis[-1].append(r)
            else:
                polis.append([r])
        geom = {"type": "Polygon", "coordinates": polis[0]} if len(polis) == 1 else {"type": "MultiPolygon", "coordinates": polis}
        simp = simplificar_geometria(geom, tol)
        if not simp:
            continue
        props = {
            "cd_cnuc": row.get("cd_cnuc") or row.get("uc_id"),
            "nome_uc": row.get("nome_uc"),
            "esfera": row.get("esfera"),
            "grupo": row.get("grupo"),
            "categoria": row.get("categoria"),
            "cat_iucn": row.get("cat_iucn"),
            "ha_total": row.get("ha_total"),
            "ha_ato": row.get("ha_ato"),
            "cria_ano": row.get("cria_ano"),
            "cria_ato": row.get("cria_ato"),
            "org_gestor": row.get("org_gestor"),
            "uf": row.get("uf"),
            "municipio": row.get("municipio"),
            "situacao": row.get("situacao"),
        }
        feicoes.append({"type": "Feature", "properties": props, "geometry": simp})

    meta = {
        "camada": "UNIDADE_CONSERVACAO",
        "fonte": "MMA - Cadastro Nacional de Unidades de Conservação (CNUC)",
        "data_referencia": data_ref,
        "url_origem": url_zip,
        "licenca": "Creative Commons Atribuição (CC BY) — Portal de Dados Abertos do MMA (dados.mma.gov.br). Fonte: MMA/CNUC.",
    }
    return feicoes, meta


# --------------------------------------------------------------------------- 2. FUNAI (Terras Indígenas)


def baixar_terras_indigenas(tol: float) -> tuple[list, dict]:
    url = (
        f"{FUNAI_WFS}?service=WFS&version=1.0.0&request=GetFeature&typeName=Funai:tis_poligonais"
        f"&outputFormat=application/json&BBOX={BBOX_MT}"
    )
    print("Baixando Terras Indígenas FUNAI WFS...")
    colecao = http_json(url, timeout=120)
    feicoes = []
    datas = []
    for ft in colecao.get("features", []):
        props_in = ft.get("properties") or {}
        ufs = [u.strip().upper() for u in (props_in.get("uf_sigla") or "").replace(",", ";").replace("/", ";").split(";")]
        if "MT" not in ufs:
            continue
        simp = simplificar_geometria(ft.get("geometry"), tol)
        if not simp:
            continue
        dt = (props_in.get("data_atualizacao") or "").strip()
        if re.match(r"^\d{2}/\d{2}/\d{4}$", dt):
            d, m, y = dt.split("/")
            datas.append(f"{y}-{m}-{d}")
        props = {
            "terrai_codigo": props_in.get("terrai_codigo"),
            "terrai_nome": props_in.get("terrai_nome"),
            "etnia_nome": props_in.get("etnia_nome"),
            "municipio_nome": props_in.get("municipio_nome"),
            "uf_sigla": props_in.get("uf_sigla"),
            "superficie_perimetro_ha": props_in.get("superficie_perimetro_ha"),
            "fase_ti": props_in.get("fase_ti"),
            "modalidade_ti": props_in.get("modalidade_ti"),
            "cr": props_in.get("cr"),
            "undadm_sigla": props_in.get("undadm_sigla"),
            "data_atualizacao": dt or None,
        }
        feicoes.append({"type": "Feature", "properties": props, "geometry": simp})

    hoje = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    meta = {
        "camada": "TERRA_INDIGENA",
        "fonte": "FUNAI - Coordenação-Geral de Geoprocessamento (CGGEO), Terras Indígenas",
        "data_referencia": max(datas) if datas else hoje,
        "url_origem": f"{FUNAI_WFS} (camada Funai:tis_poligonais)",
        "licenca": (
            "Reprodução livre desde que citada a fonte (FUNAI/CGGEO), excetuando casos especificados em contrário "
            "e conteúdos replicados de outras fontes — conforme licença declarada no WFS oficial da FUNAI."
        ),
    }
    return feicoes, meta


# --------------------------------------------------------------------------- 3. ANA (Hidrografia principal)


def baixar_hidrografia(tol: float) -> tuple[list, dict]:
    print("Baixando contorno oficial do IBGE para recorte de Mato Grosso...")
    mt_fc = http_json(IBGE_MT_URL, timeout=60)
    mt_aneis = aneis_poligono(mt_fc["features"][0]["geometry"])

    feicoes = []
    for nivel in (1, 2, 3):
        camada = f"Hidrografia_n%C3%ADvel_{nivel}"
        base = f"{ANA_BASE}/{camada}/MapServer/0/query"
        offset = 0
        total_nivel = 0
        while True:
            qs = urllib.parse.urlencode(
                {
                    "where": "1=1",
                    "geometry": BBOX_MT,
                    "geometryType": "esriGeometryEnvelope",
                    "inSR": "4674",
                    "spatialRel": "esriSpatialRelIntersects",
                    "outFields": "COCURSODAG,COBACIA,NORIOCOMP",
                    "outSR": "4326",
                    "resultOffset": offset,
                    "resultRecordCount": 1000,
                    "f": "geojson",
                }
            )
            lote = http_json(f"{base}?{qs}", timeout=60).get("features", [])
            if not lote:
                break
            for ft in lote:
                geom = ft.get("geometry")
                if not geom:
                    continue
                linhas = [geom["coordinates"]] if geom["type"] == "LineString" else geom.get("coordinates", [])
                no_mt = False
                for linha in linhas:
                    passo = max(1, len(linha) // 8)
                    amostra = linha[::passo] + [linha[-1]]
                    if any(ponto_perto_mt(p[0], p[1], mt_aneis) for p in amostra):
                        no_mt = True
                        break
                if not no_mt:
                    continue
                simp = simplificar_geometria(geom, tol)
                if not simp:
                    continue
                p_in = ft.get("properties") or {}
                nome = (p_in.get("NORIOCOMP") or "").strip()
                cod = (p_in.get("COCURSODAG") or "").strip()
                props = {
                    "nivel": nivel,
                    "cocursodag": cod or None,
                    "cobacia": (p_in.get("COBACIA") or "").strip() or None,
                    "noriocomp": nome or (f"Curso d'água ({cod})" if cod else "Curso d'água sem nome"),
                }
                feicoes.append({"type": "Feature", "properties": props, "geometry": simp})
                total_nivel += 1
            if len(lote) < 1000:
                break
            offset += len(lote)
        print(f"  ANA nível {nivel}: {total_nivel} trecho(s) em MT.")

    hoje = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    meta = {
        "camada": "HIDROGRAFIA",
        "fonte": "ANA - Agência Nacional de Águas e Saneamento Básico (SNIRH, Hidrografia níveis 1, 2 e 3)",
        "data_referencia": hoje,
        "url_origem": f"{ANA_BASE}/Hidrografia_nivel_{{1,2,3}}/MapServer/0",
        "licenca": (
            "Dados abertos ANA — disponibilizados livremente para utilização de toda a sociedade, sem restrição "
            "de licenças, patentes ou mecanismos de controle (Portal de Dados Abertos da ANA / Catálogo SNIRH). Fonte: ANA/SNIRH."
        ),
    }
    return feicoes, meta


def salvar(saida: str, prefixo: str, feicoes: list, meta: dict) -> None:
    if not feicoes:
        raise RuntimeError(f"{prefixo}: nenhum registro obtido; arquivo anterior preservado.")
    os.makedirs(saida, exist_ok=True)
    arq_gz = f"{prefixo}.geojson.gz"
    caminho_gz = os.path.join(saida, arq_gz)
    caminho_prop = os.path.join(saida, f"{prefixo}.properties")
    fc = {"type": "FeatureCollection", "features": feicoes}
    dados = json.dumps(fc, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with gzip.open(caminho_gz, "wb", compresslevel=9) as f:
        f.write(dados)

    hoje = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    prop_texto = (
        f"arquivo={arq_gz}\n"
        f"camada={meta['camada']}\n"
        f"fonte={meta['fonte']}\n"
        f"data_referencia={meta['data_referencia']}\n"
        f"baixado_em={hoje}\n"
        f"url_origem={meta['url_origem']}\n"
        f"licenca={meta['licenca']}\n"
    )
    with open(caminho_prop, "w", encoding="utf-8", newline="\n") as f:
        f.write(prop_texto)
    print(
        f"Salvo {arq_gz}: {len(feicoes)} feições, "
        f"{len(dados)/1024:.1f} KB JSON -> {os.path.getsize(caminho_gz)/1024:.1f} KB .gz"
    )


def main() -> int:
    padrao = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "referencia", "camadas")
    )
    parser = argparse.ArgumentParser(description="Baixa e simplifica camadas públicas de MT (CNUC, FUNAI, ANA).")
    parser.add_argument("--saida", default=padrao, help="Diretório de destino (.geojson.gz e .properties)")
    parser.add_argument("--tolerancia", type=float, default=0.00015, help="Tolerância Douglas-Peucker em graus (~0.00015 ≈ 16 m)")
    args = parser.parse_args()

    ucs, meta_ucs = baixar_ucs(args.tolerancia)
    tis, meta_tis = baixar_terras_indigenas(args.tolerancia)
    hidro, meta_hidro = baixar_hidrografia(args.tolerancia)

    salvar(args.saida, "ucs_mt", ucs, meta_ucs)
    salvar(args.saida, "terras_indigenas_mt", tis, meta_tis)
    salvar(args.saida, "hidrografia_mt", hidro, meta_hidro)
    return 0


if __name__ == "__main__":
    sys.exit(main())
