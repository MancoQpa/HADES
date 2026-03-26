@echo off
:: ============================================================
::  ION 7400 Desktop Simulator — Launcher
::  Ejecutar desde el directorio HarmonicMonitor\
::
::  Uso:
::    simulator\run_sim.bat [--ied SIM1] [--port 10102] [--profile crypto_mining] ...
::
::  Para dos simuladores simultáneos abrir dos consolas y ejecutar:
::    simulator\run_sim.bat --ied SIM1 --port 10102 --profile crypto_mining
::    simulator\run_sim.bat --ied SIM2 --port 10103 --profile linear_load
:: ============================================================

setlocal

:: Detectar JAVA
set JAVA_CMD=java
if defined JAVA_HOME set JAVA_CMD="%JAVA_HOME%\bin\java"

:: Classpath: clases compiladas + todas las libs
set CP=classes;lib\*

:: Verificar que las clases estén compiladas
if not exist classes\com\harmonicmonitor\simulator\SimulatorMain.class (
    echo [ERROR] Clases no encontradas. Ejecute primero compile_ps2.ps1
    pause
    exit /b 1
)

:: Cambiar al directorio raíz del proyecto HarmonicMonitor si estamos en simulator\
pushd %~dp0\..

echo Iniciando simulador con args: %*
%JAVA_CMD% -cp "%CP%" com.harmonicmonitor.simulator.SimulatorMain %*

popd
endlocal
