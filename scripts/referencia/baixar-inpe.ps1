<#
.SYNOPSIS
  Atualiza o snapshot de dados PUBLICOS do INPE (focos de calor e alertas DETER) de Mato Grosso.
.DESCRIPTION
  Requer internet. Baixa os focos MENSAIS do Programa Queimadas (dataserver-coids.inpe.br; os diarios so ficam
  ~30 dias no servidor) e os poligonos DETER Amazonia/Cerrado em GeoJSON (WFS do TerraBrasilis), filtra MT e o
  periodo e gera focos_calor_mt.csv.gz, deter_mt.geojson.gz e os .properties em src/main/resources/referencia/
  (mesmo formato do baixar-inpe.sh). O INPE entrega o foco de calor como PONTO (sem area queimada); o DETER traz o
  poligono e as areas em km2 (areamunkm, areatotalkm, areauckm).
.EXAMPLE
  .\scripts\referencia\baixar-inpe.ps1 -Dias 60
  .\scripts\referencia\baixar-inpe.ps1 -De 2026-10-12 -Ate 2026-11-11
#>
param(
    [int]$Dias = 60,
    [string]$Saida = (Join-Path $PSScriptRoot '..\..\src\main\resources\referencia'),
    [string]$De = '',
    [string]$Ate = ''
)
$ErrorActionPreference = 'Stop'
$inv = [System.Globalization.CultureInfo]::InvariantCulture
if ($Ate) { $ateData = [datetime]::ParseExact($Ate, 'yyyy-MM-dd', $inv) } else { $ateData = (Get-Date).ToUniversalTime().Date }
if ($De) { $deData = [datetime]::ParseExact($De, 'yyyy-MM-dd', $inv) } else { $deData = $ateData.AddDays(-$Dias) }
$deTxt = $deData.ToString('yyyy-MM-dd'); $ateTxt = $ateData.ToString('yyyy-MM-dd')
$baseFocos = 'https://dataserver-coids.inpe.br/queimadas/queimadas/focos/csv/mensal/Brasil'
$wfs = 'https://terrabrasilis.dpi.inpe.br/geoserver/ows'
New-Item -ItemType Directory -Force $Saida | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("inpe-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $tmp | Out-Null

function Salvar-Gz([string]$texto, [string]$arquivo) {
    $fs = [System.IO.File]::Create($arquivo)
    $gz = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionLevel]::Optimal)
    $bytes = $utf8.GetBytes($texto); $gz.Write($bytes, 0, $bytes.Length); $gz.Dispose(); $fs.Dispose()
}

try {
    Write-Host "Focos de calor: $deTxt a $ateTxt"
    $focos = New-Object System.Text.StringBuilder
    [void]$focos.Append("id,lat,lon,data_hora_gmt,satelite,municipio_id,bioma,frp,numero_dias_sem_chuva,precipitacao,risco_fogo`n")
    $nFocos = 0
    $mes = New-Object DateTime($deData.Year, $deData.Month, 1)
    while ($mes -le $ateData) {
        $m = $mes.ToString('yyyyMM')
        $arq = Join-Path $tmp "mes_$m.csv"
        try {
            Invoke-WebRequest -UseBasicParsing -TimeoutSec 900 "$baseFocos/focos_mensal_br_$m.csv" -OutFile $arq
        } catch { Write-Host "  aviso: arquivo mensal $m indisponivel (ignorado)"; $mes = $mes.AddMonths(1); continue }
        # colunas da fonte: 0=id 1=lat 2=lon 3=data_hora_gmt 4=satelite 5=municipio 6=estado 7=pais 8=municipio_id
        # 9=estado_id 10=pais_id 11=numero_dias_sem_chuva 12=precipitacao 13=risco_fogo 14=bioma 15=frp
        $primeira = $true
        foreach ($linha in [System.IO.File]::ReadLines($arq, $utf8)) {
            if ($primeira) { $primeira = $false; continue }
            $c = $linha.TrimEnd("`r").Split(',')
            if ($c.Length -lt 16 -or $c[6] -ne 'MATO GROSSO') { continue }
            $d = $c[3].Substring(0, 10)
            if ($d -lt $deTxt -or $d -gt $ateTxt) { continue }
            [void]$focos.Append("$($c[0]),$($c[1].Trim()),$($c[2].Trim()),$($c[3]),$($c[4]),$($c[8]),$($c[14]),$($c[15]),$($c[11]),$($c[12]),$($c[13])`n")
            $nFocos++
        }
        $mes = $mes.AddMonths(1)
    }

    # DETER: poligonos (GeoJSON) com todos os atributos da fonte.
    function Baixar-Deter([string]$camada) {
        $url = "${wfs}?service=WFS&version=1.0.0&request=GetFeature&typeName=$camada" +
            "&outputFormat=$([uri]::EscapeDataString('application/json'))" +
            "&CQL_FILTER=$([uri]::EscapeDataString("uf='MT' AND view_date>='$deTxt' AND view_date<='$ateTxt'"))"
        $arq = Join-Path $tmp 'deter.json'
        Invoke-WebRequest -UseBasicParsing -TimeoutSec 600 $url -OutFile $arq
        return [System.IO.File]::ReadAllText($arq, $utf8)
    }
    Write-Host "DETER Amazonia e Cerrado: $deTxt a $ateTxt"
    $deter = '{"camadas":[{"bioma":"AMAZONIA","colecao":' + (Baixar-Deter 'deter-amz:deter_amz') +
        '},{"bioma":"CERRADO","colecao":' + (Baixar-Deter 'deter-cerrado-nb:deter_cerrado') + '}]}'

    Salvar-Gz $focos.ToString() (Join-Path $Saida 'focos_calor_mt.csv.gz')
    Salvar-Gz $deter (Join-Path $Saida 'deter_mt.geojson.gz')
    $antigo = Join-Path $Saida 'deter_mt.csv.gz'
    if (Test-Path $antigo) { Remove-Item $antigo }
    $hoje = (Get-Date).ToUniversalTime().ToString('yyyy-MM-dd')
    $pf = @"
arquivo=focos_calor_mt.csv.gz
tipo=FOCO_CALOR
fonte=INPE - Programa Queimadas (BDQueimadas), focos de calor de Mato Grosso
periodo_de=$deTxt
periodo_ate=$ateTxt
baixado_em=$hoje
url_origem=$baseFocos/focos_mensal_br_AAAAMM.csv
licenca=Dados abertos do INPE (Creative Commons Atribuicao-CompartilhaIgual 4.0). Fonte: INPE - Programa Queimadas.
"@
    $pd = @"
arquivo=deter_mt.geojson.gz
tipo=DESMATAMENTO
fonte=INPE - TerraBrasilis, alertas DETER (Amazonia e Cerrado) de Mato Grosso
periodo_de=$deTxt
periodo_ate=$ateTxt
baixado_em=$hoje
url_origem=$wfs (camadas deter-amz:deter_amz e deter-cerrado-nb:deter_cerrado)
licenca=Dados abertos do INPE/TerraBrasilis (Creative Commons Atribuicao-CompartilhaIgual 4.0). Fonte: INPE - TerraBrasilis.
"@
    [System.IO.File]::WriteAllText((Join-Path $Saida 'focos_calor_mt.properties'), ($pf -replace "`r`n", "`n") + "`n", $utf8)
    [System.IO.File]::WriteAllText((Join-Path $Saida 'deter_mt.properties'), ($pd -replace "`r`n", "`n") + "`n", $utf8)
    Write-Host "Concluido: $nFocos focos e os poligonos DETER em deter_mt.geojson.gz."
} finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}
