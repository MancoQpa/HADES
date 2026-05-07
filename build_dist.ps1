# build_dist.ps1 — Empaqueta HADES v1.0 para distribución Windows
$ErrorActionPreference = "Stop"

$ROOT   = "C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\HarmonicMonitor"
$STAGE  = "$ROOT\dist_package\HADES_v1.0"
$SRCAPP = "$ROOT\installer_output\HarmonicMonitor"
$OUT    = "$ROOT\installer_output\HADES_v1.0_Windows.rar"
$RAR    = "C:\Program Files\WinRAR\Rar.exe"

# ── 1. Limpiar y crear estructura ───────────────────────────────────────────
Write-Host "[1/4] Creando estructura de staging..."
if (Test-Path $STAGE) { Remove-Item -Recurse -Force $STAGE }
foreach ($d in @("app")) {
    New-Item -ItemType Directory -Force "$STAGE\$d" | Out-Null
}

# ── 2. Copiar app principal ─────────────────────────────────────────────────
Write-Host "[2/4] Copiando app principal..."
Copy-Item "$SRCAPP\HarmonicMonitor.exe"        "$STAGE\HarmonicMonitor.exe"
Copy-Item "$SRCAPP\app\HarmonicMonitor.jar"    "$STAGE\app\HarmonicMonitor.jar"
Copy-Item "$SRCAPP\app\HarmonicMonitor.cfg"    "$STAGE\app\HarmonicMonitor.cfg"

# ── 3. Copiar runtime (incluyendo legal/ por cumplimiento GPL v2+CPE) ───────
Write-Host "[3/4] Copiando JRE embebido (con legal/)..."
foreach ($sub in @("bin","conf","lib","legal","release")) {
    $src = "$SRCAPP\runtime\$sub"
    if (Test-Path $src) {
        Copy-Item -Recurse $src "$STAGE\runtime\$sub"
    }
}

# ── 4. Escribir LEAME ───────────────────────────────────────────────────────
Write-Host "[4/4] Escribiendo LEAME y creando RAR..."

$leame = @"
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
"@
$leame | Out-File -Encoding UTF8 "$STAGE\LEAME.txt"

# ── 5. Crear RAR ────────────────────────────────────────────────────────────
if (Test-Path $OUT) { Remove-Item $OUT }
$parent = Split-Path $STAGE -Parent
$folder = Split-Path $STAGE -Leaf
& $RAR a -r -ep1 -m3 $OUT "$STAGE\*"
if ($LASTEXITCODE -ne 0) { throw "WinRAR fallo con codigo $LASTEXITCODE" }

Write-Host ""
Write-Host "══════════════════════════════════════════════"
Write-Host " RAR creado exitosamente:"
Write-Host " $OUT"
$size = [math]::Round((Get-Item $OUT).Length / 1MB, 1)
Write-Host " Tamaño: $size MB"
Write-Host "══════════════════════════════════════════════"
