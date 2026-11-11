# SEMA NEXUS — funções comuns dos scripts demo-up / demo-down / demo-reset (PowerShell).
# AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Não execute diretamente: é carregado com ". ".
#
# Variáveis opcionais (ambiente ou .env):
#   DEMO_UP_TIMEOUT    segundos de espera pelos healthchecks (padrão 600)
#   DEMO_POLL_SECONDS  intervalo entre verificações (padrão 5)

$script:Servicos = @('oracle', 'api', 'web')

function Stop-Demo {
    param([string]$Mensagem)
    [Console]::Error.WriteLine("ERRO: $Mensagem")
    exit 1
}

# Valor da variável no ambiente do processo; senão no .env; senão o padrão.
function Get-EnvValor {
    param([string]$Chave, [string]$Padrao = '')
    $valor = [Environment]::GetEnvironmentVariable($Chave)
    if ([string]::IsNullOrEmpty($valor)) {
        $arquivo = Join-Path $script:RootDir '.env'
        if (Test-Path -LiteralPath $arquivo) {
            $linha = Get-Content -LiteralPath $arquivo |
                Where-Object { $_ -match "^\s*$([regex]::Escape($Chave))=" } |
                Select-Object -Last 1
            if ($linha) {
                $valor = ($linha -replace '^[^=]*=', '') -replace '\s+#.*$', ''
                $valor = $valor.Trim()
                if ($valor -match '^"(.*)"$' -or $valor -match "^'(.*)'$") { $valor = $Matches[1] }
            }
        }
    }
    if ([string]::IsNullOrEmpty($valor)) { return $Padrao }
    return $valor
}

# Executa um comando nativo sem transformar stderr em erro do PowerShell; devolve o exit code.
function Invoke-Nativo {
    param([string]$Exe, [string[]]$Argumentos, [switch]$Silencioso)
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        # Out-Host impede que a saída do comando contamine o valor devolvido (apenas o exit code).
        if ($Silencioso) { & $Exe @Argumentos *> $null } else { & $Exe @Argumentos | Out-Host }
        return $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $anterior }
}

function Test-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Stop-Demo 'Docker não encontrado no PATH. Instale o Docker (ver docs/DOCKER.md).'
    }
    if ((Invoke-Nativo 'docker' @('info') -Silencioso) -ne 0) {
        Stop-Demo 'O Docker não está ativo. Inicie o Docker Desktop/Engine e tente novamente.'
    }
    if ((Invoke-Nativo 'docker' @('compose', 'version') -Silencioso) -ne 0) {
        Stop-Demo "Docker Compose v2 não encontrado ('docker compose')."
    }
}

# Cria o .env a partir do .env.example somente se ele não existir.
function Initialize-Env {
    $destino = Join-Path $script:RootDir '.env'
    if (Test-Path -LiteralPath $destino) { return }
    $exemplo = Join-Path $script:RootDir '.env.example'
    if (-not (Test-Path -LiteralPath $exemplo)) { Stop-Demo ".env.example não encontrado em $($script:RootDir)." }
    Copy-Item -LiteralPath $exemplo -Destination $destino
    Write-Host '.env criado a partir de .env.example (valores de exemplo, só para a demonstração).'
    [Console]::Error.WriteLine('AVISO: troque as senhas e o JWT_SECRET no .env antes de usar fora de uma máquina de demonstração.')
}

# Estado de saúde de um serviço: healthy | unhealthy | starting | <status> | none
function Get-StatusServico {
    param([string]$Servico)
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $cid = (& docker compose ps -q $Servico 2>$null | Select-Object -First 1)
        if (-not $cid) { return 'none' }
        $st = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $cid 2>$null | Select-Object -First 1)
        if (-not $st) { return 'none' }
        return "$st".Trim()
    }
    finally { $ErrorActionPreference = $anterior }
}

# Aguarda todos os serviços ficarem healthy (falha em unhealthy ou timeout).
function Wait-Healthy {
    $timeout = [int](Get-EnvValor 'DEMO_UP_TIMEOUT' '600')
    $poll = [int](Get-EnvValor 'DEMO_POLL_SECONDS' '5')
    $inicio = Get-Date
    Write-Host "Aguardando os serviços ficarem healthy (timeout ${timeout}s)..."
    while ($true) {
        $todos = $true
        $resumo = @()
        foreach ($svc in $script:Servicos) {
            $st = Get-StatusServico $svc
            $resumo += "$svc=$st"
            if ($st -eq 'unhealthy') {
                Write-Host ('Estado: ' + ($resumo -join ' '))
                Stop-Demo "o serviço '$svc' ficou unhealthy. Veja: docker compose logs $svc"
            }
            if ($st -ne 'healthy') { $todos = $false }
        }
        $linha = $resumo -join ' '
        if ($todos) { Write-Host "Estado: $linha"; return }
        if (((Get-Date) - $inicio).TotalSeconds -ge $timeout) {
            Write-Host "Estado: $linha"
            Stop-Demo "timeout de ${timeout}s aguardando os serviços. Veja: docker compose ps; docker compose logs"
        }
        Write-Host "  ... $linha"
        Start-Sleep -Seconds $poll
    }
}

function Write-Urls {
    $web = Get-EnvValor 'WEB_PORT' '3000'
    $api = Get-EnvValor 'API_PORT' '8080'
    Write-Host ''
    Write-Host 'AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS'
    Write-Host "  Aplicação : http://localhost:$web"
    Write-Host "  API       : http://localhost:$api"
    Write-Host "  Swagger   : http://localhost:$api/swagger-ui.html"
    Write-Host '  Login de teste (somente demonstração): admin@admin.com — senha em docs/DEMO.md'
}

function Start-Demo {
    Test-Docker
    Initialize-Env
    Push-Location $script:RootDir
    try {
        if ((Invoke-Nativo 'docker' @('compose', 'up', '-d', '--build')) -ne 0) {
            Stop-Demo "falha em 'docker compose up -d --build'."
        }
    }
    finally { Pop-Location }
    Wait-Healthy
    Write-Urls
}
