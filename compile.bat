@echo off
setlocal enabledelayedexpansion
title HADES - Compilar

:: Detectar Java
if exist "%~dp0jre\bin\javac.exe" ( set "JC=%~dp0jre\bin\javac.exe" & goto :found_java )
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" ( set "JC=%JAVA_HOME%\bin\javac.exe" & goto :found_java )
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do if exist "%%d\bin\javac.exe" ( set "JC=%%d\bin\javac.exe" & goto :found_java )
for /d %%d in ("C:\Program Files\Java\jdk-*")             do if exist "%%d\bin\javac.exe" ( set "JC=%%d\bin\javac.exe" & goto :found_java )
for /d %%d in ("C:\Program Files\Microsoft\jdk-*")        do if exist "%%d\bin\javac.exe" ( set "JC=%%d\bin\javac.exe" & goto :found_java )
where javac >nul 2>&1 && ( set "JC=javac" & goto :found_java )
echo ERROR: javac no encontrado. Instale JDK 11+.
pause & exit /b 1

:found_java
echo Usando: %JC%

:: Directorios
set "SRCDIR=%~dp0src\main\java"
set "OUTDIR=%~dp0classes"
set "RESDIR=%~dp0src\main\resources"
set "LIBDIR=%~dp0lib"

:: Crear directorio de salida
if not exist "%OUTDIR%" mkdir "%OUTDIR%"

:: Localizar JavaFX SDK
set "FX_LIB=C:\javafx-sdk-17\lib"
if not exist "%FX_LIB%\javafx.controls.jar" set "FX_LIB=C:\Users\admin\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18\lib"
if not exist "%FX_LIB%\javafx.controls.jar" (
    echo ERROR: JavaFX SDK no encontrado.
    echo Busque en %FX_LIB%
    pause & exit /b 1
)

:: Construir classpath con las librerías + JavaFX
set "CP="
for %%j in ("%LIBDIR%\*.jar") do set "CP=!CP!;%%j"
set "CP=%CP:~1%"
set "CP=%CP%;%FX_LIB%\javafx.controls.jar;%FX_LIB%\javafx.fxml.jar;%FX_LIB%\javafx.graphics.jar;%FX_LIB%\javafx.base.jar;%FX_LIB%\javafx.swing.jar"

:: Obtener lista de fuentes
dir /s /b "%SRCDIR%\*.java" > "%TEMP%\harmonic_sources.txt"

echo Compilando...
"%JC%" -encoding UTF-8 -d "%OUTDIR%" -cp "%CP%" @"%TEMP%\harmonic_sources.txt"

if %errorlevel% neq 0 (
    echo.
    echo ERROR: La compilacion fallo.
    pause
    exit /b 1
)

:: Copiar recursos
xcopy /E /Y /Q "%RESDIR%\*" "%OUTDIR%\" 2>nul

echo.
echo Compilacion exitosa. Clases en: %OUTDIR%
echo Para ejecutar: run.bat
pause
