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
set CP=%CP%;%OUT%

set GUI=%BASE%\src\main\java\com\harmonicmonitor

echo Compilando GUI...

%JC% -encoding UTF-8 -d "%OUT%" -cp "%CP%" ^
  "%GUI%\gui\Theme.java" ^
  "%GUI%\HarmonicMonitorApp.java" ^
  "%GUI%\gui\DashboardPanel.java" ^
  "%GUI%\gui\HarmonicsPanel.java" ^
  "%GUI%\gui\HelpPanel.java" ^
  "%GUI%\gui\AlarmsPanel.java" ^
  "%GUI%\gui\TrendChartsPanel.java" ^
  "%GUI%\gui\MultiFeederMonitorPanel.java" ^
  "%GUI%\gui\FeederMgmtPanel.java" ^
  "%GUI%\gui\RecordsPanel.java" ^
  "%GUI%\gui\ComtradePanel.java" ^
  "%GUI%\gui\AboutPanel.java" ^
  "%GUI%\gui\CompliancePanel.java" ^
  "%GUI%\gui\ComparativaPanel.java" ^
  "%GUI%\gui\DiscoveryPanel.java" ^
  2>&1

if %ERRORLEVEL% == 0 (
    echo.
    echo OK - Copiando CSS...
    xcopy /y "%BASE%\src\main\resources\com\harmonicmonitor\*.css" "%BASE%\classes\com\harmonicmonitor\" > nul
    echo.
    echo === LISTO - Corre run.bat ===
) else (
    echo.
    echo === ERROR - ver mensajes arriba ===
)

pause
