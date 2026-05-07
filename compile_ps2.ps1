$JC = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\javac.exe"
$BASE = "C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\HarmonicMonitor"
$OUTDIR = "$BASE\classes"
$LIBDIR = "$BASE\lib"
$FXLIB  = "C:\Users\admin\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18\lib"

$libJars = (Get-Item "$LIBDIR\*.jar" | ForEach-Object { $_.FullName }) -join ";"
$fxJars  = @("javafx.controls.jar","javafx.fxml.jar","javafx.graphics.jar","javafx.base.jar","javafx.swing.jar","javafx.web.jar","javafx.media.jar") | ForEach-Object { "$FXLIB\$_" }
$fxCP    = $fxJars -join ";"
$CP      = "$libJars;$fxCP"

$srcs = Get-ChildItem -Recurse -Filter "*.java" "$BASE\src\main\java" | Select-Object -ExpandProperty FullName
# javac @argfile: backslash inside quotes is escape char → use forward slashes
$quoted = $srcs | ForEach-Object { $p = $_ -replace '\\', '/'; "`"$p`"" }
[System.IO.File]::WriteAllLines("$env:TEMP\srcs2.txt", $quoted)

Write-Host "Found $($srcs.Count) files. Compiling..."

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $JC
$argsFile = "$env:TEMP\srcs2.txt"
$psi.Arguments = "--release 17 -encoding UTF-8 -d `"$OUTDIR`" -cp `"$CP`" @$argsFile"
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$p = [System.Diagnostics.Process]::Start($psi)
$stdout = $p.StandardOutput.ReadToEnd()
$stderr = $p.StandardError.ReadToEnd()
$p.WaitForExit()
if ($stdout) { Write-Host $stdout }
if ($stderr) { Write-Host $stderr }
Write-Host "Exit: $($p.ExitCode)"

# Copy resources so CSS and other assets are always in sync with compiled classes
if ($p.ExitCode -eq 0) {
    $resDir = "$BASE\src\main\resources"
    $dstDir = "$BASE\classes"
    Get-ChildItem -Recurse -File $resDir | ForEach-Object {
        $rel = $_.FullName.Substring($resDir.Length + 1)
        $dst = Join-Path $dstDir $rel
        $dstParent = Split-Path $dst
        if (!(Test-Path $dstParent)) { New-Item -ItemType Directory -Path $dstParent | Out-Null }
        Copy-Item $_.FullName $dst -Force
    }
    Write-Host "Resources copied."
}
