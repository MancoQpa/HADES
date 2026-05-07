# build_installer_linux.ps1 - HarmonicMonitor v1.0
# Creates a self-contained Linux portable bundle (.tar.gz) with bundled JRE + JavaFX.
# Run this script on WINDOWS. The output .tar.gz can be copied to any Linux Mint (amd64) PC.
# No Java or JavaFX needed on the target Linux PC.
#
# Requirements on BUILD machine (Windows):
#   - Compiled classes/ (run compile_ps2.ps1 first)
#   - JDK 14+ with jar.exe (for fat JAR creation)
#   - Internet connection (first run: downloads Liberica Full JRE 17 Linux ~90MB)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $MyInvocation.MyCommand.Path)
$BASE = (Get-Location).Path

$DIST        = "$BASE\dist_linux"
$APP_IN      = "$DIST\app_input"
$RUNTIME_DIR = "$BASE\tools\liberica-jre17-full-linux"
$OUT         = "$BASE\installer_output"
$APP_VER     = "1.0.0"
$APP_NAME    = "HarmonicMonitor"

Write-Host "============================================================"
Write-Host " HarmonicMonitor - Build Linux Installer v$APP_VER"
Write-Host "============================================================"
Write-Host ""

# -----------------------------------------------------------------
# Step 1: Find JDK with jar.exe
# -----------------------------------------------------------------
Write-Host "[1/5] Looking for JDK with jar.exe..."
$JARTOOL = $null
$jdkRoots = @(
    "C:\Program Files\Eclipse Adoptium\jdk-*",
    "C:\Program Files\Java\jdk-*",
    "C:\Program Files\Microsoft\jdk-*",
    "C:\Program Files\BellSoft\LibericaJDK-*"
)
foreach ($glob in $jdkRoots) {
    foreach ($d in (Resolve-Path $glob -ErrorAction SilentlyContinue)) {
        if (Test-Path "$d\bin\jar.exe") {
            $JARTOOL = "$d\bin\jar.exe"
            Write-Host "  JDK found: $d"
            break
        }
    }
    if ($JARTOOL) { break }
}
if (!$JARTOOL) { throw "ERROR: jar.exe not found. Requires JDK 14+." }

# -----------------------------------------------------------------
# Step 2: Verify compiled classes
# -----------------------------------------------------------------
Write-Host "[2/5] Checking compiled classes..."
$mainClass = "$BASE\classes\com\harmonicmonitor\HarmonicMonitorApp.class"
if (!(Test-Path $mainClass)) { throw "ERROR: Classes not compiled. Run compile_ps2.ps1 first." }
Write-Host "  OK: classes/ found"

# -----------------------------------------------------------------
# Step 3: Create fat JAR
# -----------------------------------------------------------------
Write-Host "[3/5] Creating fat JAR..."
if (Test-Path $DIST) { Remove-Item $DIST -Recurse -Force }
New-Item -ItemType Directory -Path $APP_IN | Out-Null
$TEMP_EX = "$DIST\extract"
New-Item -ItemType Directory -Path $TEMP_EX | Out-Null

Push-Location $TEMP_EX
$DEPS = Get-ChildItem "$BASE\lib\*.jar" | Select-Object -ExpandProperty FullName
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
# Step 4: Download Liberica Full JRE 17 for Linux amd64
# -----------------------------------------------------------------
Write-Host "[4/5] Checking Liberica Full JRE 17 for Linux amd64..."
if (!(Test-Path "$RUNTIME_DIR\bin\java")) {
    New-Item -ItemType Directory -Path "$BASE\tools" -Force | Out-Null
    $LIBERICA_TAR = "$BASE\tools\liberica-jre17-full-linux.tar.gz"
    if (!(Test-Path $LIBERICA_TAR)) {
        Write-Host "  Downloading BellSoft Liberica Full JRE 17 for Linux amd64 (~90MB)..."
        Write-Host "  This is a one-time download saved to tools/"
        $libUrl = "https://download.bell-sw.com/java/17.0.14+10/bellsoft-jre17.0.14+10-linux-amd64-full.tar.gz"
        Invoke-WebRequest -Uri $libUrl -OutFile $LIBERICA_TAR -UseBasicParsing
        Write-Host "  Download complete."
    }
    Write-Host "  Extracting Liberica JRE for Linux..."
    New-Item -ItemType Directory -Path "$BASE\tools\liberica_linux_tmp" -Force | Out-Null
    # Use Windows built-in tar.exe (NOT Git/MSYS tar which misinterprets Windows paths)
    $TAR = "C:\Windows\System32\tar.exe"
    & $TAR -xzf $LIBERICA_TAR -C "$BASE\tools\liberica_linux_tmp"
    $jreSubdir = Get-ChildItem "$BASE\tools\liberica_linux_tmp" |
                 Where-Object { $_.PSIsContainer } | Select-Object -First 1
    Move-Item $jreSubdir.FullName $RUNTIME_DIR
    Remove-Item "$BASE\tools\liberica_linux_tmp" -Recurse -Force
    Write-Host "  Liberica JRE for Linux ready at: tools/liberica-jre17-full-linux"
} else {
    Write-Host "  Liberica JRE for Linux already available"
}

# -----------------------------------------------------------------
# Step 5: Assemble portable bundle and create .tar.gz
# -----------------------------------------------------------------
Write-Host "[5/5] Assembling Linux portable bundle..."

$BUNDLE_DIR  = "$DIST\bundle\$APP_NAME"
New-Item -ItemType Directory -Path $BUNDLE_DIR | Out-Null

# Copy JRE
Write-Host "  Copying JRE (~90MB)..."
Copy-Item "$RUNTIME_DIR" "$BUNDLE_DIR\jre" -Recurse -Force

# Copy fat JAR
Copy-Item $fatJarPath "$BUNDLE_DIR\$APP_NAME.jar" -Force

# Create run.sh
$runSh = @'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
"$DIR/jre/bin/java" \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base,javafx.swing,javafx.web,javafx.media \
  --enable-native-access=ALL-UNNAMED \
  -Dfile.encoding=UTF-8 \
  -jar "$DIR/HarmonicMonitor.jar"
'@
$runSh | Set-Content "$BUNDLE_DIR\run.sh" -Encoding utf8 -NoNewline

# Create install.sh (creates desktop shortcut + /usr/local/bin entry)
$installSh = @'
#!/bin/bash
set -e
INSTALL_DIR="$HOME/.local/opt/HarmonicMonitor"
BIN_LINK="$HOME/.local/bin/harmonicmonitor"
DESKTOP="$HOME/.local/share/applications/HarmonicMonitor.desktop"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Installing HarmonicMonitor to $INSTALL_DIR ..."
mkdir -p "$INSTALL_DIR"
cp -r "$SCRIPT_DIR/." "$INSTALL_DIR/"
chmod +x "$INSTALL_DIR/run.sh"
chmod +x "$INSTALL_DIR/jre/bin/java"

# Create launcher symlink
mkdir -p "$HOME/.local/bin"
ln -sf "$INSTALL_DIR/run.sh" "$BIN_LINK"
chmod +x "$BIN_LINK"

# Create .desktop entry for application menu
mkdir -p "$(dirname "$DESKTOP")"
cat > "$DESKTOP" <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=HarmonicMonitor
Comment=Monitor de Armonicos IEC 61850 - Alimentadores MT 23kV
Exec=$INSTALL_DIR/run.sh
Icon=$INSTALL_DIR/jre/lib/security/cacerts
Terminal=false
Categories=Utility;Science;
EOF

echo ""
echo "Done! You can now launch HarmonicMonitor from:"
echo "  - Application menu (search HarmonicMonitor)"
echo "  - Terminal: harmonicmonitor"
echo "  - Direct: $INSTALL_DIR/run.sh"
'@
$installSh | Set-Content "$BUNDLE_DIR\install.sh" -Encoding utf8 -NoNewline

# Create README.txt
$readme = @"
HarmonicMonitor v$APP_VER - Linux Portable Bundle
================================================
No Java or JavaFX installation required.

QUICK START
-----------
1. Extract this archive:
     tar -xzf HarmonicMonitor-$APP_VER-linux.tar.gz

2. Run directly:
     cd HarmonicMonitor
     chmod +x run.sh
     ./run.sh

INSTALL TO SYSTEM (optional)
-----------------------------
     chmod +x install.sh
     ./install.sh
   This installs to ~/.local/opt/HarmonicMonitor and adds a desktop launcher.

REQUIREMENTS
------------
- Linux Mint 20+ / Ubuntu 20.04+ (amd64)
- No Java needed (bundled Liberica Full JRE 17 with JavaFX)

NOTES
-----
- If you see "permission denied": chmod +x run.sh jre/bin/java
- SQLite database and CSV exports are saved in ~/HarmonicMonitor/data/
"@
$readme | Set-Content "$BUNDLE_DIR\README.txt" -Encoding utf8

# Create .tar.gz using tar
New-Item -ItemType Directory -Path $OUT -Force | Out-Null
$tarPath = "$OUT\$APP_NAME-$APP_VER-linux.tar.gz"
if (Test-Path $tarPath) { Remove-Item $tarPath -Force }

Write-Host "  Creating .tar.gz (this may take a moment)..."
Push-Location "$DIST\bundle"
$TAR = "C:\Windows\System32\tar.exe"
& $TAR -czf $tarPath $APP_NAME
Pop-Location

$tarSizeMB = [math]::Round((Get-Item $tarPath).Length / 1MB, 1)

# Cleanup
Remove-Item $DIST -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "============================================================"
Write-Host " LINUX PORTABLE BUNDLE CREATED"
Write-Host " Path: $tarPath"
Write-Host " Size: $tarSizeMB MB"
Write-Host ""
Write-Host " On Linux Mint:"
Write-Host "   tar -xzf $APP_NAME-$APP_VER-linux.tar.gz"
Write-Host "   cd $APP_NAME"
Write-Host "   chmod +x run.sh"
Write-Host "   ./run.sh"
Write-Host "============================================================"
Write-Host ""
Write-Host "Done. Output in: $OUT"
