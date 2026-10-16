# Barra de estado de Claude Code: muestra el uso del plan y lo guarda en uso-plan.json
# para que el hook avisar-uso.ps1 se lo pase a Claude en cada mensaje.
$ErrorActionPreference = 'SilentlyContinue'
$datos = [Console]::In.ReadToEnd() | ConvertFrom-Json

function Hora($epoch) {
    if ($null -eq $epoch) { return '' }
    return [DateTimeOffset]::FromUnixTimeSeconds([long]$epoch).ToLocalTime().ToString('dd/MM HH:mm')
}

$partes = @()
$sesion = $datos.rate_limits.five_hour
$semana = $datos.rate_limits.seven_day
if ($null -ne $sesion) {
    $partes += "Sesion $([math]::Round($sesion.used_percentage))% (reinicia $(Hora $sesion.resets_at))"
}
if ($null -ne $semana) {
    $partes += "Semana $([math]::Round($semana.used_percentage))%"
}
$contexto = $datos.context_window.used_percentage
if ($null -ne $contexto) {
    $partes += "Contexto $([math]::Round($contexto))%"
}

if ($null -ne $sesion -or $null -ne $semana) {
    $uso = [ordered]@{
        sesion_pct      = $sesion.used_percentage
        sesion_reinicia = Hora $sesion.resets_at
        semana_pct      = $semana.used_percentage
        semana_reinicia = Hora $semana.resets_at
        actualizado     = (Get-Date).ToString('o')
    }
    $uso | ConvertTo-Json | Set-Content -Path "$env:USERPROFILE\.claude\uso-plan.json" -Encoding UTF8
}

if ($partes.Count -eq 0) { 'Uso: sin dato todavia' } else { $partes -join ' | ' }
