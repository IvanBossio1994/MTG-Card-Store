# TCG Store Helper: instala una actualizacion ya descargada.
# Lo lanza la app justo antes de cerrarse. Espera a que la app termine, copia la version nueva encima
# de la carpeta del programa y la vuelve a abrir. Los datos del usuario estan en otra carpeta y no se tocan.
param(
    [int]$ParentPid,
    [string]$Source,
    [string]$Target,
    [string]$Exe,
    [string]$Pending,
    [string]$Log,
    [switch]$NoBrowser
)

function Write-Log([string]$text) {
    try { Add-Content -LiteralPath $Log -Value ("{0:yyyy-MM-dd HH:mm:ss} {1}" -f (Get-Date), $text) } catch { }
}

Write-Log "Actualizando desde '$Source' hacia '$Target'"

if ($ParentPid -gt 0) {
    try { Wait-Process -Id $ParentPid -Timeout 90 -ErrorAction SilentlyContinue } catch { }
}
Start-Sleep -Seconds 1

$ok = $false
for ($i = 1; $i -le 10 -and -not $ok; $i++) {
    & robocopy.exe $Source $Target /E /R:3 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    $code = $LASTEXITCODE
    if ($code -lt 8) {
        $ok = $true
    } else {
        Write-Log "Intento $i fallo (robocopy $code), reintentando"
        Start-Sleep -Seconds 2
    }
}

if ($ok) {
    Write-Log "Actualizacion copiada"
    try { Remove-Item -LiteralPath $Pending -Force -ErrorAction SilentlyContinue } catch { }
} else {
    Write-Log "No se pudo copiar la actualizacion; se abre la version anterior"
}

$arguments = @()
if ($NoBrowser) { $arguments += "--app.updates.relaunched=true" }
if ($arguments.Count -gt 0) {
    Start-Process -FilePath $Exe -ArgumentList $arguments -WorkingDirectory $Target
} else {
    Start-Process -FilePath $Exe -WorkingDirectory $Target
}
