<#
.SYNOPSIS
  Benchmark da busca do DocIA (Issue #141): textual (LEXICAL) x híbrida (AUTO) com o modelo de embeddings real.
.DESCRIPTION
  Usa a API já no ar (porta API_PORT do .env, sempre em 127.0.0.1) e o conjunto fixo de perguntas
  src/test/resources/benchmark/perguntas-busca.json (o mesmo do teste offline BenchmarkBuscaTest). Para cada pergunta
  chama GET /api/documentos/busca com modo=LEXICAL e modo=AUTO e calcula precisão@k, acerto@1, recall@k e abstenção.
  Somente leitura: não grava nada. Login: SEMA_EMAIL/SEMA_SENHA (ambiente) ou, sem eles, o login demonstrativo do
  perfil CONSULTA (profile demo). Pré-requisitos: scripts/ia-preparar.ps1 (baixa o nomic-embed-text) e a API indexada
  (recuperacao=HIBRIDA). AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
.PARAMETER Saida
  Arquivo Markdown opcional para gravar a tabela e o detalhe por pergunta.
#>
param([string]$Saida = '')
$ErrorActionPreference = 'Stop'
$script:RootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'lib\demo-common.ps1')

$porta = Get-EnvValor 'API_PORT' '18081'
$base = "http://127.0.0.1:$porta"
$conjunto = Get-Content -Raw -Encoding UTF8 (Join-Path $script:RootDir 'src\test\resources\benchmark\perguntas-busca.json') |
    ConvertFrom-Json
$k = [int]$conjunto.k

$email = [Environment]::GetEnvironmentVariable('SEMA_EMAIL')
$senha = [Environment]::GetEnvironmentVariable('SEMA_SENHA')
try {
    if ($email -and $senha) {
        $corpo = @{ email = $email; senha = $senha } | ConvertTo-Json
        $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $corpo
    } else {
        $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/demo/CONSULTA"
    }
} catch {
    Stop-Demo "não foi possível entrar na API em $base (está no ar? profile demo ou SEMA_EMAIL/SEMA_SENHA)."
}
$cabecalho = @{ Authorization = "Bearer $($login.token)" }

# A API responde JSON em UTF-8 sem charset no Content-Type; o PowerShell 5.1 decodificaria como ISO-8859-1 e os
# títulos com acento não bateriam com o conjunto de perguntas.
function Get-Json([string]$Uri, [hashtable]$Cabecalho) {
    $resposta = Invoke-WebRequest -UseBasicParsing -Headers $Cabecalho -Uri $Uri
    $bytes = $resposta.RawContentStream.ToArray()
    return [Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json
}

function Medir([string]$Modo) {
    $somaP = 0.0; $somaR = 0.0; $acertos = 0; $resp = 0; $fora = 0; $absOk = 0; $absErro = 0
    $modos = @{}; $linhas = @()
    foreach ($p in $conjunto.perguntas) {
        $q = [uri]::EscapeDataString($p.pergunta)
        $r = Get-Json "$base/api/documentos/busca?q=$q&limite=$k&modo=$Modo" $cabecalho
        $modos[$r.recuperacao] = 1 + [int]$modos[$r.recuperacao]
        $top = @($r.resultados | ForEach-Object { $_.documento.titulo })
        $relevantes = @($p.relevantes)
        $linhas += "| $Modo ($($r.recuperacao)) | $($p.pergunta) | $(if ($top.Count) { $top -join '; ' } else { '(abstenção)' }) |"
        if ($relevantes.Count -eq 0) { $fora++; if ($top.Count -eq 0) { $absOk++ }; continue }
        $resp++
        if ($top.Count -eq 0) { $absErro++; continue }
        $certos = @($top | Where-Object { $relevantes -contains $_ }).Count
        $somaP += $certos / $top.Count
        $somaR += $certos / [Math]::Min($k, $relevantes.Count)
        if ($relevantes -contains $top[0]) { $acertos++ }
    }
    [pscustomobject]@{
        Modo = $Modo; Recuperacao = ($modos.Keys -join '/'); Precisao = $somaP / $resp; Acerto1 = $acertos / $resp
        Recall = $somaR / $resp; AbstencaoCorreta = $(if ($fora) { $absOk / $fora } else { 1 })
        AbstencaoIndevida = $absErro / $resp; Linhas = $linhas
    }
}

$lexical = Medir 'LEXICAL'
$auto = Medir 'AUTO'
$inv = [Globalization.CultureInfo]::InvariantCulture
$tabela = @("| Modo pedido | Recuperação | Precisão@$k | Acerto@1 | Recall@$k | Abstenção correta | Abstenção indevida |",
    '|---|---|---|---|---|---|---|')
foreach ($m in @($lexical, $auto)) {
    $tabela += [string]::Format($inv, '| {0} | {1} | {2:0.00} | {3:0.00} | {4:0.00} | {5:0.00} | {6:0.00} |', $m.Modo,
        $m.Recuperacao, $m.Precisao, $m.Acerto1, $m.Recall, $m.AbstencaoCorreta, $m.AbstencaoIndevida)
}
$tabela | ForEach-Object { Write-Host $_ }
if ($auto.Recuperacao -ne 'HIBRIDA') {
    Write-Warning 'O modo AUTO não foi híbrido em todas as perguntas: o modelo de embeddings não está disponível ou a indexação não terminou (rode scripts/ia-preparar.ps1 e aguarde ~2 min).'
}
if ($Saida) {
    $md = @("# Benchmark da busca do DocIA (Issue #141) — $(Get-Date -Format 'yyyy-MM-dd HH:mm')", '') + $tabela +
        @('', '| Modo | Pergunta | Top-' + $k + ' |', '|---|---|---|') + $lexical.Linhas + $auto.Linhas
    Set-Content -Path $Saida -Value $md -Encoding utf8
    Write-Host "Detalhe gravado em $Saida"
}
