<#
.SYNOPSIS
  Sobe o ambiente demonstrativo (Oracle + API + Web) e aguarda os healthchecks.
  AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Ver docs/DOCKER.md.
#>
$ErrorActionPreference = 'Stop'
$script:RootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'lib\demo-common.ps1')

Start-Demo
