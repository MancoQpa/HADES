# build_paquetes.ps1 — Compila y empaqueta HADES v1.0 + Simulador para distribución Windows
#
# Genera:
#   installer_output/HADES_v1.0_Windows.zip        (app principal, ~100 MB, JRE embebido)
#   installer_output/HADES_Simulador_v1.0_Windows.zip  (simulador standalone, ~2 MB, requiere Java 17+)
#
# Requisitos:
#   - JDK 25 en C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot
#   - JavaFX SDK 17 en C:\Users\admin\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18
#   - jpackage output previo en installer_output\HarmonicMonitor\ (exe + runtime JRE 17)

$ErrorActionPreference = "Continue"

$ROOT    = $PSScriptRoot
$JAVAHOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
$JAVAC   = "$JAVAHOME\bin\javac.exe"
$JAR_EXE = "$JAVAHOME\bin\jar.exe"
$SRCDIR  = "$ROOT\src\main\java"
$RESDIR  = "$ROOT\src\main\resources"
$OUTDIR  = "$ROOT\classes"
$LIBDIR  = "$ROOT\lib"
$FXLIB   = "C:\Users\admin\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18\lib"
$INST_SRC = "$ROOT\installer_output\HarmonicMonitor"   # salida de jpackage
$DIST    = "$ROOT\dist_package\HADES_v1.0"
$OUT_DIR = "$ROOT\installer_output"

# ── 1. Compilar con --release 17 ──────────────────────────────────────────────
Write-Host ""
Write-Host "[1/6] Compilando fuentes con --release 17..."

if (Test-Path "$OUTDIR\com") { Remove-Item -Recurse -Force "$OUTDIR\com" }

$jars    = (Get-ChildItem "$LIBDIR\*.jar" | ForEach-Object { $_.FullName }) -join ';'
$sources = Get-ChildItem -Recurse -Path $SRCDIR -Filter '*.java' | ForEach-Object { $_.FullName }
$allArgs = @(
    '--release', '17',
    '--module-path', $FXLIB,
    '--add-modules', 'javafx.controls,javafx.fxml,javafx.web,javafx.swing,javafx.graphics',
    '-encoding', 'UTF-8',
    '-d', $OUTDIR,
    '-cp', $jars
) + $sources

$output = & $JAVAC @allArgs 2>&1
$code   = $LASTEXITCODE
$output | Where-Object { $_ -notmatch "^Note:" } | ForEach-Object { Write-Host $_ }

if ($code -ne 0) { Write-Host "[ERROR] Compilación falló (código $code)"; exit 1 }
Write-Host "  OK — clases en $OUTDIR"

# ── 2. Preparar staging del paquete principal ──────────────────────────────────
Write-Host ""
Write-Host "[2/6] Preparando staging HADES_v1.0..."

if (Test-Path $DIST) { Remove-Item -Recurse -Force $DIST }
foreach ($d in @("app")) { New-Item -ItemType Directory -Force "$DIST\$d" | Out-Null }

Copy-Item "$INST_SRC\HarmonicMonitor.exe"     "$DIST\HarmonicMonitor.exe"
Copy-Item "$INST_SRC\app\HarmonicMonitor.jar" "$DIST\app\HarmonicMonitor.jar"
Copy-Item "$INST_SRC\app\HarmonicMonitor.cfg" "$DIST\app\HarmonicMonitor.cfg"

foreach ($sub in @("bin","conf","lib","legal","release")) {
    $src = "$INST_SRC\runtime\$sub"
    if (Test-Path $src) { Copy-Item -Recurse $src "$DIST\runtime\$sub" }
}

# ── 3. Inyectar clases + recursos en el JAR ────────────────────────────────────
Write-Host ""
Write-Host "[3/6] Inyectando clases y recursos en HarmonicMonitor.jar..."

$distJar = "$DIST\app\HarmonicMonitor.jar"

# Clases compiladas
Push-Location $OUTDIR
$classes = (Get-ChildItem -Recurse -Filter "*.class" | ForEach-Object {
    $_.FullName.Substring((Resolve-Path $OUTDIR).Path.Length + 1) -replace '\\','/'
})
& $JAR_EXE uf $distJar @classes
Pop-Location

# Recursos (help/*.txt, *.html, *.css)
$tmpRes = "$env:TEMP\hades_res_tmp"
if (Test-Path $tmpRes) { Remove-Item -Recurse -Force $tmpRes }
Copy-Item -Recurse "$RESDIR\com" "$tmpRes\com"

Push-Location $tmpRes
$resFiles = (Get-ChildItem -Recurse -File | ForEach-Object {
    $_.FullName.Substring((Resolve-Path $tmpRes).Path.Length + 1) -replace '\\','/'
})
& $JAR_EXE uf $distJar @resFiles
Pop-Location
Remove-Item -Recurse -Force $tmpRes

Write-Host "  OK"

# ── 4. Escribir LEAME ─────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[4/6] Escribiendo LEAME.txt..."

@"
╔══════════════════════════════════════════════════════════════╗
║         HADES / HarmonicMonitor v1.0 — Windows              ║
║     Monitor de Armónicos y Detección de Cargas Electrónicas  ║
╚══════════════════════════════════════════════════════════════╝

REQUISITOS
  - Windows 10/11 (64-bit)
  - No requiere Java instalado (JRE incluido)
  - Acceso de red al medidor IEC 61850 (puerto 102 TCP)

INICIAR LA APLICACIÓN
  Doble clic en:  HarmonicMonitor.exe

SIMULACIÓN INTERNA
  La aplicación incluye un modo de simulación integrado.
  Al iniciar, seleccionar "Modo Demo" en el diálogo de bienvenida
  para operar sin hardware conectado.

CONEXIÓN A ION 7400 REAL
  Host: 169.254.0.10   Puerto: 102   IED: cbo2   Prefijo MMXU: M03_   LD: LD0

SOPORTE
  Autor: Emilio Medina — Proyecto ANDE-SIGFE
"@ | Out-File -Encoding UTF8 "$DIST\LEAME.txt"

# ── 5. Crear ZIP de HADES ─────────────────────────────────────────────────────
Write-Host ""
Write-Host "[5/6] Empaquetando HADES_v1.0_Windows.zip..."

$zipHades = "$OUT_DIR\HADES_v1.0_Windows.zip"
if (Test-Path $zipHades) { Remove-Item $zipHades }
Push-Location "$ROOT\dist_package"
Compress-Archive -Path "HADES_v1.0" -DestinationPath $zipHades -CompressionLevel Optimal
Pop-Location
$sizeMB = [math]::Round((Get-Item $zipHades).Length / 1MB, 1)
Write-Host "  OK — $zipHades ($sizeMB MB)"

# ── 6. Crear ZIP del Simulador ────────────────────────────────────────────────
Write-Host ""
Write-Host "[6/6] Empaquetando HADES_Simulador_v1.0_Windows.zip..."

$simStage = "$OUT_DIR\HADES_Simulador_v1.0"
if (Test-Path $simStage) { Remove-Item -Recurse -Force $simStage }
New-Item -ItemType Directory -Force "$simStage\simulator\templates" | Out-Null
New-Item -ItemType Directory -Force "$simStage\classes\com\harmonicmonitor\simulator" | Out-Null
New-Item -ItemType Directory -Force "$simStage\lib" | Out-Null

# Fat JAR del simulador
$tmpSim = "$env:TEMP\sim_jar_build"
if (Test-Path $tmpSim) { Remove-Item -Recurse -Force $tmpSim }
New-Item -ItemType Directory -Force $tmpSim | Out-Null

foreach ($j in @("iec61850bean-1.9.0.jar","jasn1-1.11.3.jar","asn1bean-1.13.0.jar",
                  "slf4j-api-2.0.9.jar","slf4j-simple-2.0.9.jar")) {
    Push-Location $tmpSim
    & $JAR_EXE xf "$LIBDIR\$j"
    Pop-Location
}
Copy-Item -Recurse "$OUTDIR\com" "$tmpSim\com"
Remove-Item -Force "$tmpSim\META-INF\*.SF"  -ErrorAction SilentlyContinue
Remove-Item -Force "$tmpSim\META-INF\*.DSA" -ErrorAction SilentlyContinue
Remove-Item -Force "$tmpSim\META-INF\*.RSA" -ErrorAction SilentlyContinue

@"
Manifest-Version: 1.0
Main-Class: com.harmonicmonitor.simulator.SimulatorMain

"@ | Out-File -Encoding ASCII "$tmpSim\META-INF\MANIFEST.MF"

$simJar = "$simStage\HADES-Simulador.jar"
Push-Location $tmpSim
& $JAR_EXE cfm $simJar META-INF/MANIFEST.MF .
Pop-Location
Remove-Item -Recurse -Force $tmpSim

# Clases sueltas + libs (para SimulatorLauncher/dashboard)
Copy-Item "$OUTDIR\com\harmonicmonitor\simulator\*.class" "$simStage\classes\com\harmonicmonitor\simulator\"
foreach ($j in @("iec61850bean-1.9.0.jar","jasn1-1.11.3.jar","asn1bean-1.13.0.jar",
                  "slf4j-api-2.0.9.jar","slf4j-simple-2.0.9.jar")) {
    Copy-Item "$LIBDIR\$j" "$simStage\lib\"
}

# Archivos del simulador
Copy-Item "$ROOT\simulator\generic_meter_sim.cid"  "$simStage\simulator\"
Copy-Item "$ROOT\simulator\generic_meter_sim2.cid" "$simStage\simulator\"
Copy-Item "$ROOT\simulator\dashboard.html"         "$simStage\simulator\"
Copy-Item "$ROOT\simulator\templates\*.json"       "$simStage\simulator\templates\"

# Scripts de lanzamiento
@'
@echo off
cd /d "%~dp0"
set JAVA_CMD=java
if defined JAVA_HOME set JAVA_CMD="%JAVA_HOME%\bin\java"
%JAVA_CMD% -version >nul 2>&1
if %errorlevel% neq 0 ( echo [ERROR] Java 17+ requerido. & pause & exit /b 1 )
echo Iniciando simulador con args: %*
%JAVA_CMD% -jar "%~dp0HADES-Simulador.jar" %*
'@ | Out-File -Encoding ASCII "$simStage\run_sim.bat"

@'
@echo off
cd /d "%~dp0"
set JAVA_CMD=java
if defined JAVA_HOME set JAVA_CMD="%JAVA_HOME%\bin\java"
%JAVA_CMD% -version >nul 2>&1
if %errorlevel% neq 0 ( echo [ERROR] Java 17+ requerido. & pause & exit /b 1 )
netstat -ano | findstr ":8765 " >nul 2>&1
if %errorlevel% equ 0 ( echo [AVISO] Puerto 8765 en uso. & start "" "http://localhost:8765" & pause & exit /b 0 )
echo.
echo  HADES - Simulador ION 7400  ^|  Dashboard: http://localhost:8765
echo  Cierre esta ventana para detener todos los simuladores.
echo.
%JAVA_CMD% -cp "classes;lib/*" com.harmonicmonitor.simulator.SimulatorLauncher
pause
'@ | Out-File -Encoding ASCII "$simStage\launch_dashboard.bat"

@"
╔══════════════════════════════════════════════════════════════╗
║         HADES — Simulador ION 7400  v1.0                    ║
║     Simulacion IEC 61850 MMS de medidor de armonicos         ║
╚══════════════════════════════════════════════════════════════╝

REQUISITOS
  - Windows 10/11 (64-bit)
  - Java 17 o superior instalado  (verificar: java -version)
    Descargar: https://adoptium.net

INICIAR

  Opcion A — Dashboard web (recomendado):
    Doble clic en launch_dashboard.bat  →  abre http://localhost:8765

  Opcion B — Linea de comandos:
    run_sim.bat [--ied SIM1] [--port 10102] [--profile crypto_mining]

PERFILES: crypto_mining, linear_load, data_center, electronic_light,
          industrial, lighting, mixed_electronic, normal_load

CONEXIÓN DESDE HADES:
  Host: 127.0.0.1   Puerto: 10102   IED: SIM1   Prefijo: M03_   LD: LD0

SOPORTE
  Autor: Emilio Medina — Proyecto ANDE-SIGFE
"@ | Out-File -Encoding UTF8 "$simStage\LEAME.txt"

$zipSim = "$OUT_DIR\HADES_Simulador_v1.0_Windows.zip"
if (Test-Path $zipSim) { Remove-Item $zipSim }
Push-Location $OUT_DIR
Compress-Archive -Path "HADES_Simulador_v1.0" -DestinationPath $zipSim -CompressionLevel Optimal
Pop-Location
$sizeMB2 = [math]::Round((Get-Item $zipSim).Length / 1MB, 1)
Write-Host "  OK — $zipSim ($sizeMB2 MB)"

# ── Resumen ───────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "══════════════════════════════════════════════════════"
Write-Host " Paquetes generados:"
Write-Host "   $zipHades"
Write-Host "   $zipSim"
Write-Host "══════════════════════════════════════════════════════"
