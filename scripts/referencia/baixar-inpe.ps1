<#
.SYNOPSIS
  Atualiza o snapshot de dados PUBLICOS do INPE (focos de calor e alertas DETER) de Mato Grosso.
.DESCRIPTION
  Requer internet. Baixa os focos diarios do Programa Queimadas (dataserver-coids.inpe.br) e os alertas DETER
  Amazonia/Cerrado (WFS do TerraBrasilis), filtra MT e gera focos_calor_mt.csv.gz, deter_mt.csv.gz e os .properties
  em src/main/resources/referencia/ (mesmo formato do baixar-inpe.sh).
.EXAMPLE
  .\scripts\referencia\baixar-inpe.ps1 -Dias 60
#>
param(
    [int]$Dias = 60,
    [string]$Saida = (Join-Path $PSScriptRoot '..\..\src\main\resources\referencia')
)
$ErrorActionPreference = 'Stop'
$inv = [System.Globalization.CultureInfo]::InvariantCulture
$ate = (Get-Date).ToUniversalTime().Date
$de = $ate.AddDays(-$Dias)
$deTxt = $de.ToString('yyyy-MM-dd'); $ateTxt = $ate.ToString('yyyy-MM-dd')
$baseFocos = 'https://dataserver-coids.inpe.br/queimadas/queimadas/focos/csv/diario/Brasil'
$wfs = 'https://terrabrasilis.dpi.inpe.br/geoserver/ows'
New-Item -ItemType Directory -Force $Saida | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Salvar-Gz([string]$texto, [string]$arquivo) {
    $fs = [System.IO.File]::Create($arquivo)
    $gz = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionLevel]::Optimal)
    $bytes = $utf8.GetBytes($texto); $gz.Write($bytes, 0, $bytes.Length); $gz.Dispose(); $fs.Dispose()
}

Write-Host "Focos de calor: $deTxt a $ateTxt"
$focos = New-Object System.Text.StringBuilder
[void]$focos.Append("lat,lon,data_hora_gmt,satelite,municipio_id,bioma,frp`n")
$nFocos = 0
for ($i = $Dias; $i -ge 0; $i--) {
    $d = $ate.AddDays(-$i).ToString('yyyyMMdd')
    try {
        $csv = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 120 "$baseFocos/focos_diario_br_$d.csv").Content
    } catch { Write-Host "  aviso: arquivo de $d indisponivel (ignorado)"; continue }
    foreach ($linha in ($csv -split "`r?`n" | Select-Object -Skip 1)) {
        $c = $linha.Split(',')
        if ($c.Length -ge 16 -and $c[6] -eq 'MATO GROSSO') {
            [void]$focos.Append("$($c[1].Trim()),$($c[2].Trim()),$($c[3]),$($c[4]),$($c[8]),$($c[14]),$($c[15])`n"); $nFocos++
        }
    }
}

# DETER: centroide aproximado (media dos vertices do 1o anel) do poligono do alerta.
function Baixar-Deter([string]$camada, [string]$props, [string]$bioma, [bool]$temCodigo, [System.Text.StringBuilder]$sb) {
    $url = "${wfs}?service=WFS&version=1.0.0&request=GetFeature&typeName=$camada&outputFormat=csv" +
        "&propertyName=$([uri]::EscapeDataString($props))" +
        "&CQL_FILTER=$([uri]::EscapeDataString("uf='MT' AND view_date>='$deTxt'"))"
    $txt = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 300 $url).Content
    $n = 0
    foreach ($linha in ($txt -split "`r?`n" | Select-Object -Skip 1)) {
        $p = $linha.IndexOf('"MULTIPOLYGON'); if ($p -lt 0) { $p = $linha.IndexOf('"POLYGON') }
        if ($p -lt 1) { continue }
        $a = $linha.Substring(0, $p - 1).Split(',')
        $g = $linha.Substring($p) -replace '^[^(]*\(+', '' -replace '\).*$', ''
        $sx = 0.0; $sy = 0.0; $k = 0
        foreach ($v in $g.Split(',')) {
            $xy = $v.Trim().Split(' ')
            if ($xy.Length -ge 2) { $sx += [double]::Parse($xy[0], $inv); $sy += [double]::Parse($xy[1], $inv); $k++ }
        }
        if ($k -eq 0) { continue }
        $mun = ''; if ($temCodigo) { $mun = $a[6] }
        [void]$sb.Append(("{0},{1},{2},{3},{4},{5},{6},{7}`n" -f ($sy / $k).ToString('F6', $inv), ($sx / $k).ToString('F6', $inv), $a[2], $a[3], $a[1], $a[4], $mun, $bioma)); $n++
    }
    return $n
}
Write-Host "DETER Amazonia e Cerrado: $deTxt a $ateTxt"
$deter = New-Object System.Text.StringBuilder
[void]$deter.Append("lat,lon,data,satelite,classe,area_km2,municipio_id,bioma`n")
$nDeter = Baixar-Deter 'deter-amz:deter_amz' 'classname,view_date,satellite,areamunkm,municipality,mun_geocod,geom' 'AMAZONIA' $true $deter
$nDeter += Baixar-Deter 'deter-cerrado-nb:deter_cerrado' 'classname,view_date,satellite,areamunkm,municipality,st_multi' 'CERRADO' $false $deter

Salvar-Gz $focos.ToString() (Join-Path $Saida 'focos_calor_mt.csv.gz')
Salvar-Gz $deter.ToString() (Join-Path $Saida 'deter_mt.csv.gz')
$hoje = (Get-Date).ToUniversalTime().ToString('yyyy-MM-dd')
$pf = @"
arquivo=focos_calor_mt.csv.gz
tipo=FOCO_CALOR
fonte=INPE - Programa Queimadas (BDQueimadas), focos de calor de Mato Grosso
periodo_de=$deTxt
periodo_ate=$ateTxt
baixado_em=$hoje
url_origem=$baseFocos/focos_diario_br_AAAAMMDD.csv
licenca=Dados abertos do INPE (Creative Commons Atribuicao-CompartilhaIgual 4.0). Fonte: INPE - Programa Queimadas.
"@
$pd = @"
arquivo=deter_mt.csv.gz
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
Write-Host "Concluido: $nFocos focos, $nDeter alertas DETER."
