<#
.SYNOPSIS
  Restaura a demonstração: APAGA os volumes (banco Oracle e uploads), sobe de novo e aguarda o seed.
.DESCRIPTION
  DESTRUTIVO. Recusa profile diferente de demo/dev e exige confirmação explícita:
  DEMO_RESET_CONFIRM=yes (ambiente ou .env) ou digitar RESETAR quando solicitado.
  AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Ver docs/DOCKER.md.
#>
$ErrorActionPreference = 'Stop'
$script:RootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'lib\demo-common.ps1')

# 1. Profile: somente demo/dev (padrão do compose: demo).
$perfil = Get-EnvValor 'SPRING_PROFILES_ACTIVE' 'demo'
foreach ($p in ($perfil -split ',')) {
    if ($p.Trim() -notin @('demo', 'dev')) {
        Stop-Demo "demo-reset recusado: SPRING_PROFILES_ACTIVE='$perfil' não é demo/dev. Nada foi apagado."
    }
}

# 2. Confirmação explícita.
if ((Get-EnvValor 'DEMO_RESET_CONFIRM' '') -eq 'yes') {
    Write-Host 'Confirmação recebida via DEMO_RESET_CONFIRM=yes.'
}
elseif (-not [Console]::IsInputRedirected) {
    Write-Host 'ATENÇÃO: isto apaga os volumes do ambiente demonstrativo (banco Oracle e uploads).'
    $resposta = Read-Host 'Digite RESETAR para continuar'
    if ($resposta -cne 'RESETAR') {
        Write-Host 'Reset cancelado. Nada foi apagado.'
        exit 1
    }
}
else {
    Stop-Demo 'demo-reset recusado: sem confirmação (defina DEMO_RESET_CONFIRM=yes ou execute em terminal interativo). Nada foi apagado.'
}

# 3. Reset.
Test-Docker
Push-Location $script:RootDir
try {
    # Só o banco e os uploads: nunca 'down -v', que apagaria também o volume do modelo de IA (ollama) — Issue #122.
    Write-Host 'Removendo containers e os volumes do banco e dos uploads (o modelo de IA é preservado)...'
    if ((Invoke-Nativo 'docker' @('compose', 'down')) -ne 0) { Stop-Demo "falha em 'docker compose down'." }
    if ((Invoke-Nativo 'docker' @('volume', 'rm', '-f', 'sema-nexus_oradata', 'sema-nexus_uploads')) -ne 0) {
        Stop-Demo 'falha ao remover os volumes do banco e dos uploads.'
    }
}
finally { Pop-Location }
Write-Host 'Subindo novamente (o seed demonstrativo é recarregado pela API)...'
Start-Demo
