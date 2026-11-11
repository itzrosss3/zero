# Configura (o quita) el arranque automático de Zero con permisos de administrador.
# Lo usan CONFIGURAR_ARRANQUE.bat y QUITAR_ARRANQUE.bat; no hace falta ejecutarlo a mano.
param([switch]$Quitar)

$ErrorActionPreference = 'Stop'
$proyecto = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $proyecto 'target\zero.jar'
$escritorio = [Environment]::GetFolderPath('Desktop')
$acceso = Join-Path $escritorio 'Zero.lnk'

if ($Quitar) {
    Unregister-ScheduledTask -TaskName 'Zero' -Confirm:$false -ErrorAction SilentlyContinue
    if (Test-Path $acceso) { Remove-Item $acceso }
    Write-Host 'Zero ya no arrancara solo y se ha quitado el acceso directo.'
    exit 0
}

if (-not (Test-Path $jar)) {
    Write-Host "No existe $jar"
    Write-Host 'Primero genera el .jar en Eclipse: clic derecho en el proyecto > Run As > Maven build... > Goals: package'
    exit 1
}

$javaw = (Get-Command javaw -ErrorAction SilentlyContinue).Source
if (-not $javaw -and $env:JAVA_HOME) { $javaw = Join-Path $env:JAVA_HOME 'bin\javaw.exe' }
if (-not $javaw -or -not (Test-Path $javaw)) {
    Write-Host 'No encuentro javaw.exe. Comprueba que Java esta instalado y en el PATH (o JAVA_HOME).'
    exit 1
}

$usuario = "$env:USERDOMAIN\$env:USERNAME"
$accion = New-ScheduledTaskAction -Execute $javaw -Argument "-jar `"$jar`" --inicio --log" -WorkingDirectory $proyecto
$disparador = New-ScheduledTaskTrigger -AtLogOn -User $usuario
$principal = New-ScheduledTaskPrincipal -UserId $usuario -LogonType Interactive -RunLevel Highest
$ajustes = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -Priority 4
Register-ScheduledTask -TaskName 'Zero' -Action $accion -Trigger $disparador -Principal $principal `
    -Settings $ajustes -Force | Out-Null

# Acceso directo: lanza la tarea (con permisos de administrador y sin aviso de Windows)
$atajo = (New-Object -ComObject WScript.Shell).CreateShortcut($acceso)
$atajo.TargetPath = "$env:SystemRoot\System32\schtasks.exe"
$atajo.Arguments = '/Run /TN "Zero"'
$atajo.WindowStyle = 7
$icono = Join-Path $proyecto 'recursos\zero.ico'   # lo crea Zero a partir de recursos\logo.png
if (Test-Path $icono) { $atajo.IconLocation = "$icono,0" } else { $atajo.IconLocation = "$javaw,0" }
$atajo.Description = 'Asistente de voz Zero'
$atajo.Save()

Write-Host ''
Write-Host 'Listo:'
Write-Host ' - Zero arrancara solo al encender el PC, con permisos de administrador.'
Write-Host ' - Tienes un acceso directo "Zero" en el escritorio.'
