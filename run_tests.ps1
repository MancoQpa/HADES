# run_tests.ps1 - compila y ejecuta los tests JUnit 5 del proyecto
# Requiere: haber compilado antes las fuentes principales (.\compile_ps2.ps1)
# JUnit: tools\junit\junit-platform-console-standalone-1.10.2.jar (incluye Jupiter)

$JDK  = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin"
$JC   = "$JDK\javac.exe"
$JAVA = "$JDK\java.exe"
$BASE = "C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\HarmonicMonitor"

$MAIN_CLASSES = "$BASE\classes"
$TEST_CLASSES = "$BASE\classes_test"
$JUNIT = "$BASE\tools\junit\junit-platform-console-standalone-1.10.2.jar"

if (!(Test-Path "$MAIN_CLASSES\com\harmonicmonitor\analysis\ElectronicLoadDetector.class")) {
    Write-Host "ERROR: no hay clases compiladas. Ejecuta primero .\compile_ps2.ps1" -ForegroundColor Red
    exit 1
}
if (!(Test-Path $JUNIT)) {
    # tools/ esta en .gitignore: descargar el jar la primera vez en cada clon
    Write-Host "Descargando JUnit console standalone (Maven Central)..."
    $junitDir = Split-Path $JUNIT
    if (!(Test-Path $junitDir)) { New-Item -ItemType Directory -Path $junitDir | Out-Null }
    $url = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar"
    try {
        Invoke-WebRequest -Uri $url -OutFile $JUNIT -ErrorAction Stop
    } catch {
        Write-Host "ERROR: no se pudo descargar JUnit. Descargalo manualmente de:" -ForegroundColor Red
        Write-Host "  $url"
        Write-Host "y guardalo como: $JUNIT"
        exit 1
    }
}

# --- Compilar fuentes de test ---
$srcs = Get-ChildItem -Recurse -Filter "*.java" "$BASE\src\test\java" | Select-Object -ExpandProperty FullName
if (-not $srcs) { Write-Host "No hay fuentes de test."; exit 0 }

$quoted = $srcs | ForEach-Object { $p = $_ -replace '\\', '/'; "`"$p`"" }
[System.IO.File]::WriteAllLines("$env:TEMP\test_srcs.txt", $quoted)

if (!(Test-Path $TEST_CLASSES)) { New-Item -ItemType Directory -Path $TEST_CLASSES | Out-Null }

Write-Host "Compilando $($srcs.Count) archivo(s) de test..."
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $JC
$psi.Arguments = "--release 17 -encoding UTF-8 -d `"$TEST_CLASSES`" -cp `"$MAIN_CLASSES;$JUNIT`" @$env:TEMP\test_srcs.txt"
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$p = [System.Diagnostics.Process]::Start($psi)
$stdout = $p.StandardOutput.ReadToEnd()
$stderr = $p.StandardError.ReadToEnd()
$p.WaitForExit()
if ($stdout) { Write-Host $stdout }
if ($stderr) { Write-Host $stderr }
if ($p.ExitCode -ne 0) { Write-Host "Compilacion de tests FALLO (exit $($p.ExitCode))" -ForegroundColor Red; exit $p.ExitCode }

# --- Ejecutar con JUnit Platform Console Launcher ---
Write-Host "Ejecutando tests..."
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $JAVA
$psi.Arguments = "-jar `"$JUNIT`" execute --class-path `"$MAIN_CLASSES;$TEST_CLASSES`" --scan-class-path `"$TEST_CLASSES`" --fail-if-no-tests --disable-ansi-colors"
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
exit $p.ExitCode
