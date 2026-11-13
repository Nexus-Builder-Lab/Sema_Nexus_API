<#
.SYNOPSIS
  Prepara a IA local dos agentes (Issue #106): sobe o serviço Ollama do projeto e baixa o modelo aberto.
.DESCRIPTION
  Usa COMPOSE_PROFILES do .env: "ia" (GPU NVIDIA) ou "ia-cpu" (sem GPU). Baixa OLLAMA_MODELO (padrão qwen3:4b-instruct,
  licença Apache 2.0, ~2,5 GB) para o volume "ollama" do projeto sema-nexus. Não mexe em outros projetos.
  Sem custo: o modelo roda na própria máquina e nenhum dado sai dela. Ver docs/AGENTES_IA.md.
  AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
#>
$ErrorActionPreference = 'Stop'
$script:RootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'lib\demo-common.ps1')

$perfis = (Get-EnvValor 'COMPOSE_PROFILES' '') -split ',' | ForEach-Object { $_.Trim() }
if ($perfis -contains 'ia') { $servico = 'ollama' }
elseif ($perfis -contains 'ia-cpu') { $servico = 'ollama-cpu' }
else {
    Stop-Demo "defina COMPOSE_PROFILES=ia (GPU NVIDIA) ou COMPOSE_PROFILES=ia-cpu (sem GPU) no .env e rode de novo."
}
$modelo = Get-EnvValor 'OLLAMA_MODELO' 'qwen3:4b-instruct'
if ($modelo -notmatch '^[a-z0-9][a-z0-9._-]{1,60}(:[a-z0-9._-]{1,40})?$') { Stop-Demo "OLLAMA_MODELO inválido: '$modelo'." }

Test-Docker
Push-Location $script:RootDir
try {
    Write-Host "Subindo o serviço '$servico'..."
    if ((Invoke-Nativo 'docker' @('compose', 'up', '-d', $servico)) -ne 0) { Stop-Demo "falha ao subir '$servico'." }

    Write-Host 'Aguardando o Ollama responder...'
    $pronto = $false
    for ($i = 0; $i -lt 40 -and -not $pronto; $i++) {
        if ((Invoke-Nativo 'docker' @('compose', 'exec', '-T', $servico, 'ollama', 'list') -Silencioso) -eq 0) { $pronto = $true }
        else { Start-Sleep -Seconds 3 }
    }
    if (-not $pronto) { Stop-Demo "o Ollama não respondeu. Veja: docker compose logs $servico" }

    Write-Host "Baixando o modelo '$modelo' (só na primeira vez; pode levar alguns minutos)..."
    if ((Invoke-Nativo 'docker' @('compose', 'exec', '-T', $servico, 'ollama', 'pull', $modelo)) -ne 0) {
        Stop-Demo "falha ao baixar '$modelo' (verifique a internet)."
    }
    Write-Host 'Aquecendo o modelo (primeira resposta)...'
    Invoke-Nativo 'docker' @('compose', 'exec', '-T', $servico, 'ollama', 'run', $modelo, '/no_think Responda apenas: pronto') | Out-Null
}
finally { Pop-Location }

Write-Host ''
Write-Host "IA local pronta: modelo '$modelo' no serviço '$servico'."
Write-Host 'Os agentes passam a usar a IA local em até 20 segundos (sem reiniciar a API).'
Write-Host 'Conferir: GET /api/agentes/status deve indicar o modo LOCAL.'
