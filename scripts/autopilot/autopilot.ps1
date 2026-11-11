<#
.SYNOPSIS
  SEMA NEXUS — Autopilot. Executa o Claude Code sem supervisão, uma Issue por iteração (/next-issue),
  em um dos repositórios (API ou WEB). Use duas janelas para rodar as duas sessões em paralelo.

.DESCRIPTION
  Cada iteração chama:
    claude -p "/next-issue" --model sonnet --effort medium --permission-mode auto --permission-prompts none --output-format json
  O comando /next-issue escolhe a próxima Issue P0 desbloqueada, implementa, testa, abre o PR, espera o CI e
  faz o merge somente se as condições do CLAUDE.md (seção 8.1) forem atendidas.

  Modelos (definido pelo proprietário em 10/11/2026): Sonnet 5.5 com esforço médio por padrão (11/11/2026); agentes simples em Haiku 5.5. Se uma iteração
  terminar com "AUTOPILOT: FALHOU #n", o script faz UMA retentativa da mesma Issue com Opus 5.5 e esforço médio
  ("/next-issue n") antes de contar a falha. Dentro da execução, o /next-issue também pode consultar um
  subagente Opus 5.5 (médio) esporadicamente para diagnóstico.

  Limite de uso: CLAUDE_CODE_RETRY_WATCHDOG=1 faz o Claude Code aguardar a renovação do limite (429).
  Se mesmo assim o processo terminar com erro, este script espera -EsperaErroMin minutos e tenta de novo;
  o /next-issue retoma o branch em andamento.

  Parar com segurança: crie o arquivo C:\SEMANEXUS\autopilot\STOP (ou STOP-API / STOP-WEB).
  A iteração atual termina e o script sai.

.EXAMPLE
  .\autopilot.ps1 -Repo API
  .\autopilot.ps1 -Repo WEB -MaxIteracoes 5
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)][ValidateSet('API', 'WEB')][string]$Repo,
  [int]$MaxIteracoes = 80,
  [int]$EsperaErroMin = 20,
  [int]$EsperaSemTrabalhoMin = 15,
  [int]$MaxSemTrabalho = 96,   # verificações sem trabalho não gastam tokens (proxima.mjs)
  [int]$MaxFalhasSeguidas = 3,
  [int]$MaxTurnos = 400,
  [string]$Modelo = 'sonnet',          # Sonnet 5.5
  [string]$Esforco = 'medium',
  [string]$ModeloReforco = 'opus',     # Opus 5.5 — só na retentativa de uma Issue que falhou
  [string]$EsforcoReforco = 'medium',
  [string]$Prompt = '/next-issue',     # só para teste da mecânica; no uso normal, não altere
  [string]$Raiz = 'C:\SEMANEXUS'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8     # saída do claude em UTF-8 (acentos e JSON íntegros)
$OutputEncoding = [Text.Encoding]::UTF8
$repoDir = Join-Path $Raiz $(if ($Repo -eq 'API') { 'Sema_Nexus_API' } else { 'Sema_Nexus_Web' })
$ghRepo  = if ($Repo -eq 'API') { 'Nexus-Builder-Lab/Sema_Nexus_API' } else { 'Nexus-Builder-Lab/Sema_Nexus_Web' }
$apDir   = Join-Path $Raiz 'autopilot'
$logDir  = Join-Path $apDir "logs-$Repo"
$log     = Join-Path $apDir "autopilot-$Repo.log"
$lock    = Join-Path $apDir "sessao-$Repo.lock"
New-Item -ItemType Directory -Force $apDir, $logDir | Out-Null

function Log([string]$msg) {
  $linha = '{0:yyyy-MM-dd HH:mm:ss} [{1}] {2}' -f (Get-Date), $Repo, $msg
  Add-Content -Path $log -Value $linha -Encoding UTF8
  Write-Host $linha
}
function DeveParar { (Test-Path (Join-Path $apDir 'STOP')) -or (Test-Path (Join-Path $apDir "STOP-$Repo")) }

# Executa uma chamada do Claude Code e devolve código de saída, marcador, duração e caminho da saída.
function Invoke-Iteracao([int]$n, [string]$p, [string]$modelo, [string]$esforco, [string]$rotulo) {
  $saida = Join-Path $logDir ('iteracao-{0:000}{1}-{2:yyyyMMdd-HHmmss}.json' -f $n, $rotulo, (Get-Date))
  $saidaErr = [IO.Path]::ChangeExtension($saida, '.stderr.txt')
  Log "Iteração $n$rotulo — $p (modelo $modelo, esforço $esforco)"
  Push-Location $repoDir
  $eapAnterior = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'   # stderr de executável nativo não pode virar exceção no PS 5.1
  try {
    $inicio = Get-Date
    & claude -p $p --model $modelo --effort $esforco --permission-mode auto --permission-prompts none --output-format json --max-turns $MaxTurnos > $saida 2> $saidaErr
    $codigo = $LASTEXITCODE
  } finally { $ErrorActionPreference = $eapAnterior; Pop-Location }
  $dur = [int]((Get-Date) - $inicio).TotalMinutes

  # O redirecionamento ">" do PS 5.1 grava UTF-16 com BOM; Get-Content detecta pelo BOM.
  $texto = Get-Content $saida -Raw -ErrorAction SilentlyContinue
  if (-not $texto) { $texto = Get-Content $saidaErr -Raw -ErrorAction SilentlyContinue }
  $resultado = $null
  try { $resultado = ($texto | ConvertFrom-Json).result } catch { }
  if (-not $resultado) { $resultado = $texto }
  $marcador = ([regex]::Matches([string]$resultado, 'AUTOPILOT: (MERGED|AGUARDANDO_DEPENDENCIA|FALHOU_AMBIENTE|FALHOU|SEM_TRABALHO)[^\r\n]*') | Select-Object -Last 1).Value
  [pscustomobject]@{ Codigo = $codigo; Marcador = $marcador; Dur = $dur; Saida = $saida; Texto = [string]$texto }
}

# ---- Uma instância por repositório
if (Test-Path $lock) {
  $outroPid = Get-Content $lock -ErrorAction SilentlyContinue
  if ($outroPid -and (Get-Process -Id $outroPid -ErrorAction SilentlyContinue)) {
    throw "Já existe um autopilot $Repo rodando (pid $outroPid). Para encerrá-lo, crie $apDir\STOP-$Repo."
  }
}
Set-Content -Path $lock -Value $PID

# ---- Ambiente desta máquina
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'          # JAVA_HOME do sistema é JDK 8
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:CLAUDE_CODE_RETRY_WATCHDOG = '1'                     # aguarda a renovação do limite de uso (429)
$env:CLAUDE_CODE_RETRY_WATCHDOG_USAGE_LIMIT_RECHECK_MS = '600000'   # verifica a cada 10 min
$env:CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS = '1800000'     # até 30 min aguardando tarefas em segundo plano
Remove-Item Env:CLAUDE_CODE_EFFORT_LEVEL -ErrorAction SilentlyContinue   # senão sobrepõe o --effort

foreach ($cmd in 'claude', 'gh', 'git', 'node') {
  if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) { throw "Comando '$cmd' não encontrado no PATH." }
}
if ($Repo -eq 'API' -and -not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw "Maven (mvn) não encontrado." }
if ($Repo -eq 'WEB' -and -not (Get-Command npm -ErrorAction SilentlyContinue)) { throw "npm não encontrado." }

Log "Início. Repositório: $repoDir. Modelo padrão: $Modelo/$Esforco; reforço: $ModeloReforco/$EsforcoReforco. Máx. iterações: $MaxIteracoes."
$semTrabalho = 0
$falhasSeguidas = 0
$reforcadas = @{}
$seletor = Join-Path $PSScriptRoot 'proxima.mjs'
if (-not (Test-Path $seletor)) { $seletor = Join-Path $Raiz 'Sema_Nexus_API\scripts\autopilot\proxima.mjs' }   # Issues que já tiveram a retentativa com o modelo de reforço

try {
  for ($i = 1; $i -le $MaxIteracoes; $i++) {
    if (DeveParar) { Log 'Arquivo STOP encontrado. Encerrando.'; break }

    # ---- Seleção SEM tokens (proxima.mjs): só chama o Claude quando há trabalho
    $p = $Prompt
    if ($Prompt -eq '/next-issue') {
      $sel = $null
      $eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
      try { $sel = (& node $seletor --repo $Repo --raiz $Raiz 2>$null | Select-Object -Last 1) | ConvertFrom-Json } catch { }
      $ErrorActionPreference = $eap
      if (-not $sel -or $sel.acao -eq 'erro') {
        Log "Seleção falhou ($(if ($sel) { $sel.motivo } else { 'sem resposta' })). Aguardando $EsperaErroMin min."
        Start-Sleep -Seconds ($EsperaErroMin * 60); continue
      }
      if ($sel.acao -eq 'nada') {
        $semTrabalho++
        if ($semTrabalho -ge $MaxSemTrabalho) { Log "Sem trabalho por $semTrabalho verificações. Encerrando."; break }
        Log "Sem trabalho agora (0 tokens): $($sel.motivo). Nova verificação em $EsperaSemTrabalhoMin min."
        Start-Sleep -Seconds ($EsperaSemTrabalhoMin * 60); $i--; continue
      }
      $p = if ($sel.acao -eq 'merge-pendente') { "/next-issue pr $($sel.pr)" } else { "/next-issue $($sel.issue)" }
      Log "Selecionado: $($sel.acao) $(if ($sel.pr) { 'PR #' + $sel.pr } else { 'Issue #' + $sel.issue })"
    }

    $r = Invoke-Iteracao $i $p $Modelo $Esforco ''

    if ($r.Codigo -ne 0 -or -not $r.Marcador) {
      $trecho = $r.Texto
      if ($trecho.Length -gt 400) { $trecho = $trecho.Substring($trecho.Length - 400) }
      Log "Execução terminou sem marcador (código $($r.Codigo), $($r.Dur) min). Provável limite de uso ou erro. Trecho: $($trecho -replace '\s+', ' ')"
      Log "Aguardando $EsperaErroMin min antes de tentar de novo (o branch em andamento é retomado)."
      Start-Sleep -Seconds ($EsperaErroMin * 60)
      continue
    }
    Log "$($r.Marcador) ($($r.Dur) min). Saída: $($r.Saida)"

    # ---- Falha de CÓDIGO em Sonnet: uma retentativa com Opus. Falha de AMBIENTE não tem retentativa.
    $m = [regex]::Match($r.Marcador, 'FALHOU #(\d+)')
    if ($m.Success -and -not $reforcadas.ContainsKey($m.Groups[1].Value) -and $ModeloReforco -ne $Modelo) {
      $issue = $m.Groups[1].Value
      $reforcadas[$issue] = $true
      if (DeveParar) { Log 'Arquivo STOP encontrado. Retentativa com reforço não executada.'; break }
      Log "Issue #$issue falhou com $Modelo. Retentativa única com $ModeloReforco/$EsforcoReforco."
      $eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
      & gh issue edit $issue -R $ghRepo --remove-label autopilot-falhou 2>$null | Out-Null
      $ErrorActionPreference = $eap
      $r2 = Invoke-Iteracao $i "/next-issue $issue reforco" $ModeloReforco $EsforcoReforco '-reforco'
      if ($r2.Marcador) {
        Log "Reforço: $($r2.Marcador) ($($r2.Dur) min). Saída: $($r2.Saida)"
        $r = $r2
      } else {
        Log "Reforço terminou sem marcador (código $($r2.Codigo)). Mantida a falha original."
        $eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        & gh issue edit $issue -R $ghRepo --add-label autopilot-falhou 2>$null | Out-Null
        $ErrorActionPreference = $eap
      }
    }

    switch -Regex ($r.Marcador) {
      'MERGED|AGUARDANDO' { $semTrabalho = 0; $falhasSeguidas = 0 }
      'FALHOU' {
        $falhasSeguidas++
        if ($falhasSeguidas -ge $MaxFalhasSeguidas) {
          Log "$falhasSeguidas falhas seguidas. Parando para revisão humana. Veja $apDir\RELATORIO_ERROS.md."
        }
      }
      'SEM_TRABALHO' { $semTrabalho++ }
    }
    if ($falhasSeguidas -ge $MaxFalhasSeguidas -or $semTrabalho -ge $MaxSemTrabalho) { break }
  }
} finally {
  Remove-Item $lock -ErrorAction SilentlyContinue
  Log 'Fim do autopilot.'
}
