@echo off
:: ============================================================
::  HADES — Simulador ION 7400 / Dashboard Launcher
::  Inicia el servidor HTTP en localhost:8765 y abre el
::  dashboard en el navegador automáticamente.
::
::  Los botones "Lanzar" del dashboard arrancan los simuladores
::  como subprocesos de esta consola.
::
::  Ejecutar desde el directorio HarmonicMonitor\
:: ============================================================
setlocal

set JAVA_CMD=java
if defined JAVA_HOME set JAVA_CMD="%JAVA_HOME%\bin\java"

pushd %~dp0\..

if not exist classes\com\harmonicmonitor\simulator\SimulatorLauncher.class (
    echo [ERROR] Clases no encontradas. Ejecute primero:
    echo         powershell -ExecutionPolicy Bypass -File compile_ps2.ps1
    popd
    pause
    exit /b 1
)

echo HADES - Simulador ION 7400
echo Iniciando Dashboard Launcher en http://localhost:8765 ...
%JAVA_CMD% -cp "classes;lib/*" com.harmonicmonitor.simulator.SimulatorLauncher

popd
endlocal
