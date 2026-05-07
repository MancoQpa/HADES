$JC = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\javac.exe"
$BASE = "C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\HarmonicMonitor"
$OUTDIR = "$BASE\classes"
$LIBDIR = "$BASE\lib"

$CP = (Get-Item "$LIBDIR\*.jar" | ForEach-Object { $_.FullName }) -join ";"
$srcs = Get-ChildItem -Recurse -Filter "*.java" "$BASE\src\main\java" | Select-Object -ExpandProperty FullName

Write-Host "Found $($srcs.Count) Java files"
# Write without BOM, paths quoted for spaces
$quoted = $srcs | ForEach-Object { "`"$_`"" }
[System.IO.File]::WriteAllLines("$env:TEMP\srcs.txt", $quoted)

if (-not (Test-Path $OUTDIR)) { New-Item -ItemType Directory -Path $OUTDIR | Out-Null }

& $JC -encoding UTF-8 -d $OUTDIR -cp $CP "@$env:TEMP\srcs.txt" 2>&1
Write-Host "Exit code: $LASTEXITCODE"
