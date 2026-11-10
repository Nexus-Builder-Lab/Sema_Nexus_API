<#
.SYNOPSIS
  Abre duas janelas do PowerShell, uma com o autopilot da API e outra com o do Web.
  Para parar as duas com segurança: New-Item C:\SEMANEXUS\autopilot\STOP
#>
$script = Join-Path $PSScriptRoot 'autopilot.ps1'
Remove-Item 'C:\SEMANEXUS\autopilot\STOP', 'C:\SEMANEXUS\autopilot\STOP-API', 'C:\SEMANEXUS\autopilot\STOP-WEB' -ErrorAction SilentlyContinue
Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', "`"$script`"", '-Repo', 'API'
Start-Sleep -Seconds 20   # a API começa um pouco antes: o Web depende do contrato dela
Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', "`"$script`"", '-Repo', 'WEB'
Write-Host 'Autopilot iniciado em duas janelas. Logs em C:\SEMANEXUS\autopilot\.'
