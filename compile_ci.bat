@echo off
setlocal enabledelayedexpansion
set "JC=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\javac.exe"
set "SRCDIR=%~dp0src\main\java"
set "OUTDIR=%~dp0classes"
set "LIBDIR=%~dp0lib"
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
set "FX_LIB=C:\javafx-sdk-17\lib"
if not exist "%FX_LIB%\javafx.controls.jar" set "FX_LIB=C:\Users\admin\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18\lib"
set "CP="
for %%j in ("%LIBDIR%\*.jar") do set "CP=!CP!;%%j"
set "CP=%CP:~1%"
set "CP=%CP%;%FX_LIB%\javafx.controls.jar;%FX_LIB%\javafx.fxml.jar;%FX_LIB%\javafx.graphics.jar;%FX_LIB%\javafx.base.jar;%FX_LIB%\javafx.swing.jar"
dir /s /b "%SRCDIR%\*.java" > "%TEMP%\hm_srcs.txt"
"%JC%" -encoding UTF-8 -d "%OUTDIR%" -cp "%CP%" @"%TEMP%\hm_srcs.txt"
exit /b %errorlevel%
