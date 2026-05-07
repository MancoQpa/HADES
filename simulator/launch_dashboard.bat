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
    echo.
    echo [ERROR] Clases no encontradas. Ejecute primero compile_ci.bat
    echo.
    popd
    pause
    exit /b 1
)

:: Verificar si el puerto 8765 ya esta en uso
netstat -ano | findstr ":8765 " >nul 2>&1
if %errorlevel% equ 0 (
    echo.
    echo [AVISO] El puerto 8765 ya esta en uso. Abriendo dashboard existente...
    start "" "http://localhost:8765"
    popd
    pause
    exit /b 0
)

echo.
echo  HADES - Simulador ION 7400
echo  Iniciando Dashboard Launcher en http://localhost:8765 ...
echo  Cierre esta ventana para detener todos los simuladores.
echo.

%JAVA_CMD% -cp "classes;lib/*" com.harmonicmonitor.simulator.SimulatorLauncher
set EXIT_CODE=%errorlevel%

echo.
if %EXIT_CODE% neq 0 (
    echo [ERROR] El servidor termino con codigo %EXIT_CODE%
) else (
    echo [INFO] Servidor detenido correctamente.
)

popd
endlocal
pause
