# setup/windows/setup.ps1 — Sifin: instala dependencias, compila e cria atalho na area de trabalho.
# Execute com: powershell -ExecutionPolicy Bypass -File setup\windows\setup.ps1

$ErrorActionPreference = "Stop"
$JAVA_MIN = 17

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectDir = (Resolve-Path "$ScriptDir\..\..")

function Write-Ok   { param($msg) Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Info { param($msg) Write-Host "  --> $msg" -ForegroundColor Cyan }
function Write-Warn { param($msg) Write-Host "  [!] $msg"  -ForegroundColor Yellow }
function Write-Fail { param($msg) Write-Host "  [ERRO] $msg" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "╔══════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     Sifin — Setup de Ambiente        ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ── 1. Java ───────────────────────────────────────────────────────────────────
function Get-JavaVersion {
    try {
        $v = & java -version 2>&1 | Select-String 'version "(\d+)' |
             ForEach-Object { $_.Matches[0].Groups[1].Value }
        return [int]$v
    } catch { return 0 }
}

$javaVer = Get-JavaVersion
if ($javaVer -ge $JAVA_MIN) {
    Write-Ok "Java $javaVer encontrado"
} else {
    Write-Warn "Java $JAVA_MIN+ nao encontrado. Instalando via winget..."
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Fail "winget nao encontrado. Instale o Java 17 manualmente em https://adoptium.net e rode o script novamente."
    }
    winget install --id EclipseAdoptium.Temurin.17.JDK --silent --accept-package-agreements --accept-source-agreements
    # recarrega PATH da sessao
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("Path","User")
    $javaVer = Get-JavaVersion
    if ($javaVer -lt $JAVA_MIN) {
        Write-Fail "Falha ao instalar Java. Instale o JDK 17 manualmente em https://adoptium.net"
    }
    Write-Ok "Java instalado com sucesso"
}

# ── 2. Maven ──────────────────────────────────────────────────────────────────
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvnVer = (& mvn --version 2>&1 | Select-String 'Apache Maven (\S+)').Matches[0].Groups[1].Value
    Write-Ok "Maven $mvnVer encontrado"
} else {
    Write-Warn "Maven nao encontrado. Instalando via winget..."
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Fail "winget nao encontrado. Instale o Maven manualmente em https://maven.apache.org"
    }
    winget install --id Apache.Maven --silent --accept-package-agreements --accept-source-agreements
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("Path","User")
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        Write-Fail "Falha ao instalar Maven. Instale manualmente em https://maven.apache.org"
    }
    Write-Ok "Maven instalado com sucesso"
}

# ── 3. Compilar ───────────────────────────────────────────────────────────────
Write-Host ""
Write-Info "Compilando o projeto (pode demorar na primeira vez para baixar dependencias)..."
Set-Location $ProjectDir
& mvn package -DskipTests -q
if ($LASTEXITCODE -ne 0) { Write-Fail "Falha na compilacao. Verifique a saida acima." }
Write-Ok "Build concluido -> target\sifin-1.0.0.jar"

# ── 4. Criar data\ ────────────────────────────────────────────────────────────
New-Item -ItemType Directory -Force -Path "$ProjectDir\data" | Out-Null

# ── 5. run.bat ja existe no repositorio ──────────────────────────────────────
$RunBat = "$ProjectDir\run.bat"
Write-Ok "run.bat pronto"

# ── 6. Atalho na area de trabalho ─────────────────────────────────────────────
$DesktopPath = [Environment]::GetFolderPath("Desktop")
$ShortcutPath = "$DesktopPath\Sifin.lnk"

$WScript = New-Object -ComObject WScript.Shell
$Shortcut = $WScript.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath    = $RunBat
$Shortcut.WorkingDirectory = $ProjectDir
$Shortcut.Description   = "Sifin — Painel de Investimentos"
# Windows nao suporta .png como icone; usa o icone do javaw.exe como fallback
$JavaExe = (Get-Command javaw -ErrorAction SilentlyContinue)?.Source
if ($JavaExe) { $Shortcut.IconLocation = "$JavaExe,0" }
$Shortcut.Save()

Write-Ok "Atalho criado na area de trabalho -> $ShortcutPath"

# ── 7. Resumo ─────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "╔══════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║         Setup concluido!             ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "  Para iniciar o Sifin:"
Write-Host "    run.bat  (ou pelo atalho na area de trabalho)" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Para recompilar apos alteracoes:"
Write-Host "    mvn package -DskipTests" -ForegroundColor Cyan
Write-Host ""
