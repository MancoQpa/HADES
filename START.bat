@echo off
setlocal enabledelayedexpansion
title HADES v1.0 — Inicio

cd /d "%~dp0"

echo.
echo  =====================================================
echo   HADES v1.0
echo   Harmonic Analysis for Detection of Electronic Signatures (IEC 61850)
echo   E. Medina, D. Rojas, E. Paiva, S. Dominguez - Paraguay
echo  =====================================================
echo.

:: Verificar si existe el JAR compilado de Maven
if exist "target\harmonic-monitor-1.0.0-jar-with-dependencies.jar" goto :run_jar

:: Verificar si existen las clases compiladas
if exist "classes\com\harmonicmonitor\HarmonicMonitorApp.class" goto :run_classes

:: Nada compilado → intentar compilar con Maven
echo No se encontraron archivos compilados.
echo Intentando compilar con Maven...
where mvn >nul 2>&1
if %errorlevel% equ 0 (
    mvn clean package -DskipTests -q
    if exist "target\harmonic-monitor-1.0.0-jar-with-dependencies.jar" goto :run_jar
)

:: Intentar compilar con bat
call compile.bat
if exist "classes\com\harmonicmonitor\HarmonicMonitorApp.class" goto :run_classes

echo.
echo ERROR: No se pudo compilar la aplicacion.
echo Asegurese de tener JDK 11+ instalado.
pause
exit /b 1

:run_jar
echo Ejecutando desde JAR...
:: Detectar Java
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" ( set "JE=%JAVA_HOME%\bin\java.exe" & goto :launch_jar )
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do if exist "%%d\bin\java.exe" ( set "JE=%%d\bin\java.exe" & goto :launch_jar )
set "JE=java"
:launch_jar
"%JE%" --enable-native-access=ALL-UNNAMED -jar "target\harmonic-monitor-1.0.0-jar-with-dependencies.jar"
goto :end

:run_classes
echo Ejecutando desde clases compiladas...
call run.bat
goto :end

:end
if %errorlevel% neq 0 ( echo. & echo Error al ejecutar. & pause )
