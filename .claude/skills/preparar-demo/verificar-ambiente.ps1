<#
.SYNOPSIS
  SEMA NEXUS — verificação SOMENTE LEITURA do ambiente demonstrativo (não sobe, não para, não apaga nada).
.DESCRIPTION
  Confere Docker, containers do projeto, .env (sem exibir segredos), aplicação web, API, modo demo
  e a visão de satélite Esri. Saída 0 = pronto; 1 = há falhas. AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File .claude\skills\preparar-demo\verificar-ambiente.ps1
#>
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$falhas = 0
function Ok($m) { Write-Host "[OK]    $m" }
function Aviso($m) { Write-Host "[AVISO] $m" }
function Falha($m) { Write-Host "[FALHA] $m"; $script:falhas++ }

# .env: em worktree, usa o do checkout principal.
$envArq = Join-Path $raiz '.env'
if (-not (Test-Path -LiteralPath $envArq)) {
    $comum = (& git -C $raiz rev-parse --git-common-dir 2>$null)
    if ($comum) {
        if (-not [System.IO.Path]::IsPathRooted($comum)) { $comum = Join-Path $raiz $comum }
        $envArq = Join-Path (Split-Path $comum -Parent) '.env'
    }
}
$cfg = @{}
if (Test-Path -LiteralPath $envArq) {
    foreach ($l in Get-Content -LiteralPath $envArq) {
        if ($l -match '^\s*([A-Z0-9_]+)\s*=\s*(.*)$') { $cfg[$Matches[1]] = ($Matches[2] -replace '\s+#.*$', '').Trim().Trim('"').Trim("'") }
    }
    Ok ".env encontrado"
} else { Falha ".env não encontrado (copie de .env.example)" }
function Valor($k, $p) { if ($cfg.ContainsKey($k) -and $cfg[$k]) { $cfg[$k] } else { $p } }
$webPort = Valor 'WEB_PORT' '3000'
$apiPort = Valor 'API_PORT' '8080'
$perfil = Valor 'SPRING_PROFILES_ACTIVE' 'demo'
if ($perfil -match '^(demo|dev)$') { Ok "SPRING_PROFILES_ACTIVE=$perfil" } else { Falha "SPRING_PROFILES_ACTIVE=$perfil (a demonstração usa demo)" }

# Chave Esri: só presença e formato. Nunca exibir o valor.
$esri = Valor 'ESRI_API_KEY' ''
if (-not $esri) { Aviso "ESRI_API_KEY vazia: botão 'Visão de satélite' ficará desabilitado (opcional)" }
elseif ($esri -match '^[A-Za-z0-9._~-]{20,}$') { Ok "ESRI_API_KEY configurada (formato válido)" }
else { Falha "ESRI_API_KEY com formato inválido (o Nginx vai recusá-la)" }

# Docker e containers do projeto.
& docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) { Falha "Docker Desktop não está rodando"; Write-Host "Resultado: $falhas falha(s)."; exit 1 }
Ok "Docker ativo"
# Só os containers do projeto compose "sema-nexus" (nunca toca nos de outros projetos).
$ps = & docker ps -a --filter 'label=com.docker.compose.project=sema-nexus' --format '{{.Names}}|{{.State}}|{{.Status}}'
foreach ($s in 'oracle', 'api', 'web') {
    $linha = $ps | Where-Object { $_ -like "sema-nexus-$s-*|*" } | Select-Object -First 1
    if (-not $linha) { Falha "serviço '$s' não existe (rode .\scripts\demo-up.ps1)"; continue }
    $p = $linha -split '\|'
    if ($p[1] -eq 'running' -and $p[2] -match '\(healthy\)') { Ok "serviço '$s' running (healthy)" }
    else { Falha "serviço '$s': $($p[1]) — $($p[2])" }
}

# HTTP sempre por 127.0.0.1 (localhost cai em IPv6, outro serviço).
function Http($url, $metodo = 'GET') {
    try { $r = Invoke-WebRequest -Uri $url -Method $metodo -UseBasicParsing -TimeoutSec 10; return @{ Status = [int]$r.StatusCode; Corpo = $r.Content } }
    catch { if ($_.Exception.Response) { return @{ Status = [int]$_.Exception.Response.StatusCode; Corpo = '' } } return @{ Status = 0; Corpo = '' } }
}
$web = "http://127.0.0.1:$webPort"
$r = Http "$web/healthz"
if ($r.Status -eq 200) { Ok "web responde em $web" } else { Falha "web não responde em $web/healthz (status $($r.Status))" }
$r = Http "$web/api/auth/config"
if ($r.Status -eq 200 -and $r.Corpo -match '"demo"\s*:\s*true') { Ok "API via proxy em modo demo (/api/auth/config)" }
elseif ($r.Status -eq 200) { Falha "API respondeu, mas não está em modo demo" }
else { Falha "API não responde via $web/api (status $($r.Status))" }
$r = Http "http://127.0.0.1:$apiPort/swagger-ui.html"
if ($r.Status -eq 200) { Ok "Swagger em http://127.0.0.1:$apiPort/swagger-ui.html" } else { Aviso "Swagger não respondeu em 127.0.0.1:$apiPort (status $($r.Status))" }
$r = Http "$web/tiles/esri/status" 'HEAD'
if ($r.Status -eq 204) {
    $t = Http "$web/tiles/esri/5/16/11"
    if ($t.Status -eq 200) { Ok "visão de satélite Esri disponível (tile de teste baixado)" } else { Aviso "chave aceita pelo Nginx, mas tile de teste falhou (status $($t.Status)) — internet ou chave" }
} elseif ($esri) { Falha "Nginx não reconhece a ESRI_API_KEY (recrie o container web: .\scripts\demo-up.ps1)" }

Write-Host ''
Write-Host "Abrir: $web (nunca localhost) · zoom 100% · Ctrl+F5 · usuário admin@admin.com"
if ($falhas) { Write-Host "Resultado: $falhas falha(s)."; exit 1 }
Write-Host 'Resultado: ambiente pronto.'
exit 0
