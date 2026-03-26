@echo off
setlocal enabledelayedexpansion
title HarmonicMonitor v1.0

cd /d "%~dp0"

:: Detectar Java
if exist "%~dp0jre\bin\java.exe" ( set "JE=%~dp0jre\bin\java.exe" & goto :found_java )
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" ( set "JE=%JAVA_HOME%\bin\java.exe" & goto :found_java )
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do if exist "%%d\bin\java.exe" ( set "JE=%%d\bin\java.exe" & goto :found_java )
for /d %%d in ("C:\Program Files\Java\jdk-*")             do if exist "%%d\bin\java.exe" ( set "JE=%%d\bin\java.exe" & goto :found_java )
for /d %%d in ("C:\Program Files\Microsoft\jdk-*")        do if exist "%%d\bin\java.exe" ( set "JE=%%d\bin\java.exe" & goto :found_java )
where java >nul 2>&1 && ( set "JE=java" & goto :found_java )
echo ERROR: Java no encontrado.
pause & exit /b 1

:found_java

:: Ruta de JavaFX SDK (buscar en varias ubicaciones)
set "FX_LIB=C:\javafx-sdk-17\lib"
if not exist "%FX_LIB%\javafx.controls.jar" set "FX_LIB=C:\Users\admin\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18\lib"
if not exist "%FX_LIB%\javafx.controls.jar" (
    echo ERROR: JavaFX no encontrado. Rutas buscadas:
    echo   C:\javafx-sdk-17\lib
    echo   C:\Users\admin\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18\lib
    pause & exit /b 1
)

:: Construir classpath (clases compiladas + IEC61850 libs + JavaFX)
set "CP=classes"
for %%j in (lib\*.jar) do set "CP=!CP!;%%j"
set "CP=!CP!;%FX_LIB%\javafx.controls.jar;%FX_LIB%\javafx.fxml.jar;%FX_LIB%\javafx.graphics.jar;%FX_LIB%\javafx.base.jar;%FX_LIB%\javafx.swing.jar;%FX_LIB%\javafx.web.jar;%FX_LIB%\javafx.media.jar"

echo Iniciando HarmonicMonitor v1.0...
echo JVM: %JE%
echo JavaFX: %FX_LIB%
echo.

"%JE%" ^
    --module-path "%FX_LIB%" ^
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.swing,javafx.web,javafx.media ^
    --enable-native-access=ALL-UNNAMED ^
    -XX:TieredStopAtLevel=1 ^
    -Dfile.encoding=UTF-8 ^
    -cp "%CP%" ^
    com.harmonicmonitor.HarmonicMonitorApp %*

if %errorlevel% neq 0 (
    echo.
    echo La aplicacion termino con errores (codigo %errorlevel%).
    pause
)
