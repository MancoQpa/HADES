@echo off
setlocal

set JC="C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\javac.exe"
set BASE=C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\HarmonicMonitor
set OUT=%BASE%\classes
set LIB=%BASE%\lib
set FX=C:\Users\admin\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18\lib

set CP=%LIB%\*
set CP=%CP%;%FX%\javafx.controls.jar;%FX%\javafx.fxml.jar;%FX%\javafx.graphics.jar
set CP=%CP%;%FX%\javafx.base.jar;%FX%\javafx.swing.jar;%FX%\javafx.web.jar;%FX%\javafx.media.jar

echo Buscando fuentes...
dir /s /b "%BASE%\src\main\java\*.java" > "%TEMP%\srcs.txt"
echo Compilando... (puede tardar 2-4 minutos)

%JC% -encoding UTF-8 -d "%OUT%" -cp "%CP%" @"%TEMP%\srcs.txt" 2>&1

if %ERRORLEVEL% == 0 (
    echo.
    echo OK - Copiando recursos...
    xcopy /s /y "%BASE%\src\main\resources\*" "%BASE%\classes\" > nul
    echo Listo. Puedes correr la app.
) else (
    echo.
    echo ERROR de compilacion - ver mensajes arriba
)

pause
