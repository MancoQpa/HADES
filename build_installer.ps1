# build_installer.ps1 - HarmonicMonitor v1.0
# Creates a self-contained Windows installer (.exe) with bundled JRE + JavaFX.
# No Java or JavaFX needed on the target PC.
#
# Requirements on BUILD machine:
#   - JDK 14+ with jpackage (you have JDK 25)
#   - Compiled classes/ (run compile_gui_only.bat first)
#   - Internet connection (first run: downloads Liberica Full JRE ~85MB)
#   - WiX Toolset 3.x for .exe (optional; falls back to portable ZIP)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $MyInvocation.MyCommand.Path)
$BASE = (Get-Location).Path

$DIST        = "$BASE\dist_installer"
$APP_IN      = "$DIST\app_input"
$RUNTIME_DIR = "$BASE\tools\liberica-jre17-full"
$OUT         = "$BASE\installer_output"
$APP_VER     = "1.0.0"
$APP_NAME    = "HarmonicMonitor"

Write-Host "============================================================"
Write-Host " HarmonicMonitor - Build Installer v$APP_VER"
Write-Host "============================================================"
Write-Host ""

# -----------------------------------------------------------------
# Step 1: Find JDK with jpackage
# -----------------------------------------------------------------
Write-Host "[1/6] Looking for JDK with jpackage..."
$JPACKAGE = $null
$JARTOOL  = $null
$jdkRoots = @(
    "C:\Program Files\Eclipse Adoptium\jdk-*",
    "C:\Program Files\Java\jdk-*",
    "C:\Program Files\Microsoft\jdk-*",
    "C:\Program Files\BellSoft\LibericaJDK-*"
)
foreach ($glob in $jdkRoots) {
    foreach ($d in (Resolve-Path $glob -ErrorAction SilentlyContinue)) {
        if (Test-Path "$d\bin\jpackage.exe") {
            $JPACKAGE = "$d\bin\jpackage.exe"
            $JARTOOL  = "$d\bin\jar.exe"
            Write-Host "  JDK found: $d"
            break
        }
    }
    if ($JPACKAGE) { break }
}
if (!$JPACKAGE) {
    throw "ERROR: jpackage not found. Requires JDK 14+."
}

# -----------------------------------------------------------------
# Step 2: Verify compiled classes
# -----------------------------------------------------------------
Write-Host "[2/6] Checking compiled classes..."
$mainClass = "$BASE\classes\com\harmonicmonitor\HarmonicMonitorApp.class"
if (!(Test-Path $mainClass)) {
    throw "ERROR: Classes not compiled. Run compile_gui_only.bat first."
}
Write-Host "  OK: classes/ found"

# -----------------------------------------------------------------
# Step 3: Get sqlite-jdbc
# -----------------------------------------------------------------
Write-Host "[3/6] Checking dependencies..."
$SQLITE_JAR = "$BASE\lib\sqlite-jdbc-3.45.3.0.jar"
if (!(Test-Path $SQLITE_JAR)) {
    $found = $null
    $gradleCache = "$env:USERPROFILE\.gradle"
    $mavenCache  = "$env:USERPROFILE\.m2"
    if (Test-Path $gradleCache) {
        $found = Get-ChildItem $gradleCache -Recurse -Filter "sqlite-jdbc*.jar" -ErrorAction SilentlyContinue |
                 Sort-Object Name -Descending | Select-Object -First 1
    }
    if ((!$found) -and (Test-Path $mavenCache)) {
        $found = Get-ChildItem $mavenCache -Recurse -Filter "sqlite-jdbc*.jar" -ErrorAction SilentlyContinue |
                 Sort-Object Name -Descending | Select-Object -First 1
    }
    if ($found) {
        Copy-Item $found.FullName $SQLITE_JAR
        Write-Host "  sqlite-jdbc: copied from local cache ($($found.Name))"
    } else {
        Write-Host "  Downloading sqlite-jdbc 3.45.3.0 from Maven Central..."
        $url = "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.3.0/sqlite-jdbc-3.45.3.0.jar"
        Invoke-WebRequest -Uri $url -OutFile $SQLITE_JAR -UseBasicParsing
        Write-Host "  sqlite-jdbc: downloaded"
    }
} else {
    Write-Host "  sqlite-jdbc: already in lib/"
}

$DEPS = Get-ChildItem "$BASE\lib\*.jar" | Select-Object -ExpandProperty FullName
Write-Host "  Dependencies: $($DEPS.Count) JARs"

# -----------------------------------------------------------------
# Step 4: Get Liberica Full JRE 17 (includes JavaFX natively)
# -----------------------------------------------------------------
Write-Host "[4/6] Checking Liberica Full JRE 17 (includes JavaFX)..."
if (!(Test-Path "$RUNTIME_DIR\bin\java.exe")) {
    New-Item -ItemType Directory -Path "$BASE\tools" -Force | Out-Null
    $LIBERICA_ZIP = "$BASE\tools\liberica-jre17-full.zip"
    if (!(Test-Path $LIBERICA_ZIP)) {
        Write-Host "  Downloading BellSoft Liberica Full JRE 17 (~85MB)..."
        Write-Host "  This is a one-time download saved to tools/"
        $libUrl = "https://download.bell-sw.com/java/17.0.14+10/bellsoft-jre17.0.14+10-windows-amd64-full.zip"
        Invoke-WebRequest -Uri $libUrl -OutFile $LIBERICA_ZIP -UseBasicParsing
        Write-Host "  Download complete."
    }
    Write-Host "  Extracting Liberica JRE..."
    Expand-Archive $LIBERICA_ZIP "$BASE\tools\liberica_tmp" -Force
    $jreSubdir = Get-ChildItem "$BASE\tools\liberica_tmp" |
                 Where-Object { $_.PSIsContainer } | Select-Object -First 1
    Move-Item $jreSubdir.FullName $RUNTIME_DIR
    Remove-Item "$BASE\tools\liberica_tmp" -Recurse -Force
    Write-Host "  Liberica JRE ready at: tools/liberica-jre17-full"
} else {
    Write-Host "  Liberica JRE already available"
}

# -----------------------------------------------------------------
# Step 5: Create fat JAR (all deps + app classes in one JAR)
# -----------------------------------------------------------------
Write-Host "[5/6] Creating fat JAR..."
if (Test-Path $DIST) { Remove-Item $DIST -Recurse -Force }
New-Item -ItemType Directory -Path $APP_IN | Out-Null
$TEMP_EX = "$DIST\extract"
New-Item -ItemType Directory -Path $TEMP_EX | Out-Null

Push-Location $TEMP_EX

foreach ($jar in $DEPS) {
    $leaf = Split-Path $jar -Leaf
    Write-Host "  Extracting $leaf..."
    & $JARTOOL xf $jar 2>$null
}
Write-Host "  Copying app classes..."
Copy-Item "$BASE\classes\*" . -Recurse -Force

# Remove JAR digital signatures (cause SecurityException in fat JARs)
if (Test-Path "META-INF") {
    Get-ChildItem "META-INF" -Filter "*.SF"  | Remove-Item -ErrorAction SilentlyContinue
    Get-ChildItem "META-INF" -Filter "*.DSA" | Remove-Item -ErrorAction SilentlyContinue
    Get-ChildItem "META-INF" -Filter "*.RSA" | Remove-Item -ErrorAction SilentlyContinue
    Get-ChildItem "META-INF" -Filter "*.EC"  | Remove-Item -ErrorAction SilentlyContinue
}
Get-ChildItem "." -Recurse -Filter "module-info.class" | Remove-Item -ErrorAction SilentlyContinue

$manifestFile = "$DIST\MANIFEST.MF"
"Manifest-Version: 1.0`r`nMain-Class: com.harmonicmonitor.HarmonicMonitorApp`r`n" |
    Set-Content $manifestFile -Encoding ascii

$fatJarPath = "$APP_IN\$APP_NAME.jar"
& $JARTOOL cfm $fatJarPath $manifestFile .
Pop-Location

$fatSizeMB = [math]::Round((Get-Item $fatJarPath).Length / 1MB, 1)
Write-Host "  Fat JAR: $APP_NAME.jar ($fatSizeMB MB)"

# -----------------------------------------------------------------
# Step 6: Run jpackage to create installer
# -----------------------------------------------------------------
Write-Host "[6/6] Running jpackage..."
# Clean previous output to avoid "directory already exists" error
if (Test-Path "$OUT\$APP_NAME") { Remove-Item "$OUT\$APP_NAME" -Recurse -Force }
if (Test-Path "$OUT\$APP_NAME-$APP_VER.exe")           { Remove-Item "$OUT\$APP_NAME-$APP_VER.exe" -Force }
if (Test-Path "$OUT\$APP_NAME-$APP_VER-portable.zip")  { Remove-Item "$OUT\$APP_NAME-$APP_VER-portable.zip" -Force }
New-Item -ItemType Directory -Path $OUT -Force | Out-Null

$FX_MODS = "javafx.controls,javafx.fxml,javafx.graphics,javafx.base,javafx.swing,javafx.web,javafx.media"
$jvmOpts = "--add-modules $FX_MODS --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8"

$jpBase = @(
    "--name",          $APP_NAME,
    "--app-version",   $APP_VER,
    "--vendor",        "Emilio Medina",
    "--description",   "Monitor de Armonicos IEC 61850 - Alimentadores MT 23kV",
    "--copyright",     "2025 Emilio Medina",
    "--input",         $APP_IN,
    "--main-jar",      "$APP_NAME.jar",
    "--main-class",    "com.harmonicmonitor.HarmonicMonitorApp",
    "--runtime-image", $RUNTIME_DIR,
    "--java-options",  $jvmOpts,
    "--dest",          $OUT
)
# NOTE: --add-modules is NOT used with --runtime-image (mutually exclusive).
# Liberica Full JRE already has all JavaFX modules bundled.

$iconIco = "$BASE\src\main\resources\com\harmonicmonitor\icon.ico"
$iconPng = "$BASE\src\main\resources\com\harmonicmonitor\icon.png"
if     (Test-Path $iconIco) { $jpBase += @("--icon", $iconIco) }
elseif (Test-Path $iconPng) { $jpBase += @("--icon", $iconPng) }

# --- Try to install WiX if missing ---
$wixFound = Get-Command "candle.exe" -ErrorAction SilentlyContinue
if (!$wixFound) {
    # Check common WiX install paths
    $wixPaths = @(
        "C:\Program Files (x86)\WiX Toolset v3.14\bin",
        "C:\Program Files (x86)\WiX Toolset v3.11\bin",
        "C:\Program Files\WiX Toolset v3.14\bin"
    )
    foreach ($wp in $wixPaths) {
        if (Test-Path "$wp\candle.exe") {
            $env:PATH = "$($env:PATH);$wp"
            $wixFound = $true
            Write-Host "  WiX found at: $wp"
            break
        }
    }
}
if (!$wixFound) {
    Write-Host "  WiX Toolset not found. Attempting to install via winget..."
    try {
        winget install WiX.WiX --silent --accept-package-agreements --accept-source-agreements 2>&1 | Out-Null
        # Re-add to PATH
        $wixPaths2 = @(
            "C:\Program Files (x86)\WiX Toolset v3.14\bin",
            "C:\Program Files (x86)\WiX Toolset v3.11\bin"
        )
        foreach ($wp in $wixPaths2) {
            if (Test-Path "$wp\candle.exe") {
                $env:PATH = "$($env:PATH);$wp"
                Write-Host "  WiX installed and ready"
                $wixFound = $true
                break
            }
        }
    } catch {
        Write-Host "  winget install failed; will create portable ZIP instead"
    }
}

# --- Try .exe installer (needs WiX Toolset 3.x) ---
Write-Host "  Attempting .exe installer (WiX Toolset required)..."
$jpExe = $jpBase + @(
    "--type",            "exe",
    "--win-dir-chooser",
    "--win-shortcut",
    "--win-menu",
    "--win-menu-group",  $APP_NAME
)

$built = $false
$exitCode = 0
try {
    & $JPACKAGE @jpExe
    $exitCode = $LASTEXITCODE
} catch {
    Write-Warning "jpackage exception: $_"
    $exitCode = -1
}

if ($exitCode -eq 0) {
    $built = $true
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " INSTALLER .EXE CREATED"
    Write-Host " Path: $OUT\$APP_NAME-$APP_VER.exe"
    Write-Host "============================================================"
}

# --- Fallback: portable app-image + ZIP ---
if (!$built) {
    Write-Warning "EXE failed (exit $exitCode). WiX Toolset may not be installed."
    Write-Host "  Generating portable app-image instead..."
    Write-Host "  To create .exe: install WiX Toolset 3.x from wixtoolset.org"
    Write-Host ""

    $jpImg = $jpBase + @("--type", "app-image")
    & $JPACKAGE @jpImg
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage app-image failed with exit code $LASTEXITCODE"
    }

    $imgDir  = "$OUT\$APP_NAME"
    $zipPath = "$OUT\$APP_NAME-$APP_VER-portable.zip"
    if (Test-Path $imgDir) {
        Write-Host "  Compressing app-image to ZIP..."
        Compress-Archive -Path $imgDir -DestinationPath $zipPath -Force
        Write-Host ""
        Write-Host "============================================================"
        Write-Host " PORTABLE ZIP CREATED"
        Write-Host " Path: $zipPath"
        Write-Host " Usage: Extract ZIP and run $APP_NAME\$APP_NAME.exe"
        Write-Host " NOTE: No Java or JavaFX needed on target PC"
        Write-Host "============================================================"
    }
}

# Cleanup temp directory
Remove-Item $DIST -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "Done. Output in: $OUT"
