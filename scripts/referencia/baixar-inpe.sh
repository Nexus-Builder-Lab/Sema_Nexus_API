#!/usr/bin/env bash
# Atualiza o snapshot de dados PÚBLICOS do INPE (focos de calor e alertas DETER) de Mato Grosso.
# Requer internet, curl, awk, gzip e date (GNU). Uso: scripts/referencia/baixar-inpe.sh [dias=60] [saida]
# Gera focos_calor_mt.csv.gz, deter_mt.csv.gz e os .properties em src/main/resources/referencia/.
set -euo pipefail
DIAS="${1:-60}"
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
SAIDA="${2:-$RAIZ/src/main/resources/referencia}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$SAIDA"
ATE="$(date -u +%Y-%m-%d)"
DE="$(date -u -d "$ATE - $DIAS days" +%Y-%m-%d)"
BASE_FOCOS="https://dataserver-coids.inpe.br/queimadas/queimadas/focos/csv/diario/Brasil"
WFS="https://terrabrasilis.dpi.inpe.br/geoserver/ows"

echo "Focos de calor: $DE a $ATE"
echo "lat,lon,data_hora_gmt,satelite,municipio_id,bioma,frp" > "$TMP/focos.csv"
for i in $(seq "$DIAS" -1 0); do
  d="$(date -u -d "$ATE - $i days" +%Y%m%d)"
  if curl -fsS --connect-timeout 20 --retry 2 -m 120 "$BASE_FOCOS/focos_diario_br_$d.csv" -o "$TMP/dia.csv" 2>/dev/null; then
    tr -d '\r' < "$TMP/dia.csv" | awk -F, 'NR>1 && $7=="MATO GROSSO" { gsub(/ /,"",$2); gsub(/ /,"",$3);
      print $2","$3","$4","$5","$9","$15","$16 }' >> "$TMP/focos.csv"
  else
    echo "  aviso: arquivo de $d indisponível (ignorado)"
  fi
done

# DETER: centroide aproximado (média dos vértices do 1º anel) do polígono do alerta.
deter() { # $1 camada  $2 propertyName  $3 bioma  $4 coluna do código IBGE (0 = sem)
  curl -fsS -m 300 -G "$WFS" --data-urlencode "service=WFS" --data-urlencode "version=1.0.0" \
    --data-urlencode "request=GetFeature" --data-urlencode "typeName=$1" --data-urlencode "outputFormat=csv" \
    --data-urlencode "propertyName=$2" --data-urlencode "CQL_FILTER=uf='MT' AND view_date>='$DE'" -o "$TMP/deter.csv"
  tr -d '\r' < "$TMP/deter.csv" | awk -v bioma="$3" -v colmun="$4" '
    NR>1 { p=match($0,/"(MULTI)?POLYGON/); if(!p) next
      split(substr($0,1,p-2),a,","); g=substr($0,p); sub(/^[^(]*\(+/,"",g); sub(/\).*/,"",g)
      n=split(g,v,","); sx=0; sy=0; k=0
      for(j=1;j<=n;j++){ split(v[j],xy," "); if(xy[1]!=""){sx+=xy[1]; sy+=xy[2]; k++} }
      if(k==0) next
      mun=(colmun>0)?a[colmun]:""
      # a: 1=FID 2=classname 3=view_date 4=satellite 5=areamunkm 6=municipality [7=mun_geocod]
      printf "%.6f,%.6f,%s,%s,%s,%s,%s,%s\n", sy/k, sx/k, a[3], a[4], a[2], a[5], mun, bioma }'
}
echo "DETER Amazônia e Cerrado: $DE a $ATE"
echo "lat,lon,data,satelite,classe,area_km2,municipio_id,bioma" > "$TMP/deter.csv.out"
deter "deter-amz:deter_amz" "classname,view_date,satellite,areamunkm,municipality,mun_geocod,geom" AMAZONIA 7 >> "$TMP/deter.csv.out"
deter "deter-cerrado-nb:deter_cerrado" "classname,view_date,satellite,areamunkm,municipality,st_multi" CERRADO 0 >> "$TMP/deter.csv.out"

gzip -9nc "$TMP/focos.csv" > "$SAIDA/focos_calor_mt.csv.gz"
gzip -9nc "$TMP/deter.csv.out" > "$SAIDA/deter_mt.csv.gz"
HOJE="$(date -u +%Y-%m-%d)"
cat > "$SAIDA/focos_calor_mt.properties" <<P
arquivo=focos_calor_mt.csv.gz
tipo=FOCO_CALOR
fonte=INPE - Programa Queimadas (BDQueimadas), focos de calor de Mato Grosso
periodo_de=$DE
periodo_ate=$ATE
baixado_em=$HOJE
url_origem=$BASE_FOCOS/focos_diario_br_AAAAMMDD.csv
licenca=Dados abertos do INPE (Creative Commons Atribuicao-CompartilhaIgual 4.0). Fonte: INPE - Programa Queimadas.
P
cat > "$SAIDA/deter_mt.properties" <<P
arquivo=deter_mt.csv.gz
tipo=DESMATAMENTO
fonte=INPE - TerraBrasilis, alertas DETER (Amazonia e Cerrado) de Mato Grosso
periodo_de=$DE
periodo_ate=$ATE
baixado_em=$HOJE
url_origem=$WFS (camadas deter-amz:deter_amz e deter-cerrado-nb:deter_cerrado)
licenca=Dados abertos do INPE/TerraBrasilis (Creative Commons Atribuicao-CompartilhaIgual 4.0). Fonte: INPE - TerraBrasilis.
P
echo "Concluído: $(($(wc -l < "$TMP/focos.csv")-1)) focos, $(($(wc -l < "$TMP/deter.csv.out")-1)) alertas DETER."
