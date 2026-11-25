<#
.SYNOPSIS
  Atualiza os snapshots offline de UCs (MMA/CNUC), Terras Indigenas (FUNAI) e hidrografia principal (ANA) de MT.
.DESCRIPTION
  Requer internet e Python 3. Recorta para Mato Grosso e simplifica as geometrias em
  src/main/resources/referencia/camadas/ com arquivos .geojson.gz e .properties (fonte, licenca, data).
.EXAMPLE
  .\scripts\referencia\baixar-camadas-mt.ps1
#>
param(
    [string]$Saida = (Join-Path $PSScriptRoot '..\..\src\main\resources\referencia\camadas'),
    [double]$Tolerancia = 0.00015
)
$ErrorActionPreference = 'Stop'
$py = Join-Path $PSScriptRoot 'baixar-camadas-mt.py'
python $py --saida $Saida --tolerancia $Tolerancia
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
