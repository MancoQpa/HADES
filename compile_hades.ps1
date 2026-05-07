$JAVAC = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\javac.exe'
$SRCDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\HarmonicMonitor\src\main\java'
$OUTDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\HarmonicMonitor\classes'
$LIBDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\HarmonicMonitor\lib'
$FXLIB  = 'C:\Users\admin\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18\lib'

$jars = (Get-ChildItem "$LIBDIR\*.jar" | ForEach-Object { $_.FullName }) -join ';'
$CP = "$jars;$FXLIB\javafx.controls.jar;$FXLIB\javafx.fxml.jar;$FXLIB\javafx.graphics.jar;$FXLIB\javafx.base.jar;$FXLIB\javafx.web.jar;$FXLIB\javafx.swing.jar"

$sources = Get-ChildItem -Recurse -Path $SRCDIR -Filter '*.java' | ForEach-Object { $_.FullName }

if (-not (Test-Path $OUTDIR)) { New-Item -ItemType Directory -Path $OUTDIR | Out-Null }

$allArgs = @('-encoding', 'UTF-8', '-d', $OUTDIR, '-cp', $CP) + $sources
& $JAVAC @allArgs 2>&1
exit $LASTEXITCODE
