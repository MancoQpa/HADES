$JC = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\javac.exe"
$BASE = "C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\HarmonicMonitor"
$OUTDIR = "$BASE\classes"
$LIBDIR = "$BASE\lib"
$CP = (Get-Item "$LIBDIR\*.jar" | ForEach-Object { $_.FullName }) -join ";"

# Also add classes to CP so previously compiled classes are found
$CP = $OUTDIR + ";" + $CP

$src = "$BASE\src\main\java\com\harmonicmonitor\gui\ComtradePanel.java"
Write-Host "Compiling: $src"
$result = & $JC -encoding UTF-8 -d $OUTDIR -cp $CP $src 2>&1
$result | ForEach-Object { Write-Host $_ }
Write-Host "Exit: $LASTEXITCODE"
