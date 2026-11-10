<#
.SYNOPSIS
  Para o ambiente demonstrativo preservando os dados (volumes). Nunca usa -v.
  AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Ver docs/DOCKER.md.
#>
$ErrorActionPreference = 'Stop'
$script:RootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'lib\demo-common.ps1')

Test-Docker
Push-Location $script:RootDir
try {
    if ((Invoke-Nativo 'docker' @('compose', 'down')) -ne 0) { Stop-Demo "falha em 'docker compose down'." }
}
finally { Pop-Location }
Write-Host 'Ambiente parado. Os dados (volumes oradata e uploads) foram preservados.'
