# Hook UserPromptSubmit: le pasa a Claude el uso del plan que guardo barra-estado.ps1.
$ErrorActionPreference = 'SilentlyContinue'
$archivo = "$env:USERPROFILE\.claude\uso-plan.json"
if (-not (Test-Path $archivo)) {
    'Uso del plan: sin dato todavia (pedir /usage si hace falta).'
    exit 0
}
$uso = Get-Content $archivo -Raw | ConvertFrom-Json
$minutos = [math]::Round(((Get-Date) - [datetime]$uso.actualizado).TotalMinutes)
"Uso del plan (barra de estado, hace $minutos min): ventana de 5 h $([math]::Round($uso.sesion_pct))% (reinicia $($uso.sesion_reinicia)); semana $([math]::Round($uso.semana_pct))% (reinicia $($uso.semana_reinicia)). Umbrales: 90% preguntar si seguir; 95% frenar y dejar traspaso."
