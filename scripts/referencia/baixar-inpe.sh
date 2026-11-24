#!/usr/bin/env bash
# Atualiza o snapshot de dados PÚBLICOS do INPE (focos de calor e alertas DETER) de Mato Grosso.
# Requer internet, curl, awk, gzip e date (GNU). Uso: scripts/referencia/baixar-inpe.sh [dias=60] [saida]
# Período explícito (opcional): DE=AAAA-MM-DD ATE=AAAA-MM-DD scripts/referencia/baixar-inpe.sh
# Gera focos_calor_mt.csv.gz, deter_mt.geojson.gz e os .properties em src/main/resources/referencia/.
set -euo pipefail
DIAS="${1:-60}"
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
SAIDA="${2:-$RAIZ/src/main/resources/referencia}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$SAIDA"
ATE="${ATE:-$(date -u +%Y-%m-%d)}"
DE="${DE:-$(date -u -d "$ATE - $DIAS days" +%Y-%m-%d)}"
BASE_FOCOS="https://dataserver-coids.inpe.br/queimadas/queimadas/focos/csv/mensal/Brasil"
WFS="https://terrabrasilis.dpi.inpe.br/geoserver/ows"

# Focos: arquivos MENSAIS do BDQueimadas (os diários só ficam ~30 dias no servidor), filtrados por MT e período.
# Mantém todos os atributos do foco que a fonte fornece (o INPE entrega o foco como PONTO, sem área queimada).
echo "Focos de calor: $DE a $ATE"
echo "id,lat,lon,data_hora_gmt,satelite,municipio_id,bioma,frp,numero_dias_sem_chuva,precipitacao,risco_fogo" > "$TMP/focos.csv"
mes="$(date -u -d "${DE:0:7}-01" +%Y-%m-01)"
while [[ "$mes" < "$ATE" || "$mes" == "$ATE" ]]; do
  m="$(date -u -d "$mes" +%Y%m)"
  if curl -fsS --connect-timeout 20 --retry 2 -m 900 "$BASE_FOCOS/focos_mensal_br_$m.csv" -o "$TMP/mes.csv" 2>/dev/null; then
    # colunas da fonte: 1=id 2=lat 3=lon 4=data_hora_gmt 5=satelite 6=municipio 7=estado 8=pais 9=municipio_id
    # 10=estado_id 11=pais_id 12=numero_dias_sem_chuva 13=precipitacao 14=risco_fogo 15=bioma 16=frp
    tr -d '\r' < "$TMP/mes.csv" | awk -F, -v de="$DE" -v ate="$ATE" 'NR>1 && $7=="MATO GROSSO" {
      d=substr($4,1,10); if (d<de || d>ate) next
      gsub(/ /,"",$2); gsub(/ /,"",$3)
      print $1","$2","$3","$4","$5","$9","$15","$16","$12","$13","$14 }' >> "$TMP/focos.csv"
  else
    echo "  aviso: arquivo mensal $m indisponível (ignorado)"
  fi
  mes="$(date -u -d "$mes + 1 month" +%Y-%m-01)"
done

# DETER: polígonos (GeoJSON) com todos os atributos da fonte (areamunkm, areatotalkm, areauckm em km²).
deter() { # $1 camada
  curl -fsS -m 600 -G "$WFS" --data-urlencode "service=WFS" --data-urlencode "version=1.0.0" \
    --data-urlencode "request=GetFeature" --data-urlencode "typeName=$1" \
    --data-urlencode "outputFormat=application/json" \
    --data-urlencode "CQL_FILTER=uf='MT' AND view_date>='$DE' AND view_date<='$ATE'"
}
echo "DETER Amazônia e Cerrado: $DE a $ATE"
{
  printf '{"camadas":[{"bioma":"AMAZONIA","colecao":'
  deter "deter-amz:deter_amz"
  printf '},{"bioma":"CERRADO","colecao":'
  deter "deter-cerrado-nb:deter_cerrado"
  printf '}]}'
} > "$TMP/deter.json"

gzip -9nc "$TMP/focos.csv" > "$SAIDA/focos_calor_mt.csv.gz"
gzip -9nc "$TMP/deter.json" > "$SAIDA/deter_mt.geojson.gz"
rm -f "$SAIDA/deter_mt.csv.gz"
HOJE="$(date -u +%Y-%m-%d)"
cat > "$SAIDA/focos_calor_mt.properties" <<P
arquivo=focos_calor_mt.csv.gz
tipo=FOCO_CALOR
fonte=INPE - Programa Queimadas (BDQueimadas), focos de calor de Mato Grosso
periodo_de=$DE
periodo_ate=$ATE
baixado_em=$HOJE
url_origem=$BASE_FOCOS/focos_mensal_br_AAAAMM.csv
licenca=Dados abertos do INPE (Creative Commons Atribuicao-CompartilhaIgual 4.0). Fonte: INPE - Programa Queimadas.
P
cat > "$SAIDA/deter_mt.properties" <<P
arquivo=deter_mt.geojson.gz
tipo=DESMATAMENTO
fonte=INPE - TerraBrasilis, alertas DETER (Amazonia e Cerrado) de Mato Grosso
periodo_de=$DE
periodo_ate=$ATE
baixado_em=$HOJE
url_origem=$WFS (camadas deter-amz:deter_amz e deter-cerrado-nb:deter_cerrado)
licenca=Dados abertos do INPE/TerraBrasilis (Creative Commons Atribuicao-CompartilhaIgual 4.0). Fonte: INPE - TerraBrasilis.
P
echo "Concluído: $(($(wc -l < "$TMP/focos.csv")-1)) focos e os polígonos DETER em deter_mt.geojson.gz."
