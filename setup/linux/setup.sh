#!/usr/bin/env bash
# setup/linux/setup.sh — Sifin: instala dependências, compila e cria atalho na área de trabalho.
set -euo pipefail

JAVA_MIN=17
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# ── cores ──────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✔${NC} $*"; }
info() { echo -e "${CYAN}→${NC} $*"; }
warn() { echo -e "${YELLOW}⚠${NC} $*"; }
die()  { echo -e "${RED}✘ ERRO:${NC} $*" >&2; exit 1; }

echo ""
echo "╔══════════════════════════════════════╗"
echo "║     Sifin — Setup de Ambiente        ║"
echo "╚══════════════════════════════════════╝"
echo ""

# ── 1. Sistema operacional ─────────────────────────────────────────────────────
OS="$(uname -s)"
ARCH="$(uname -m)"
info "Sistema: $OS / $ARCH"

if [[ "$OS" != "Linux" && "$OS" != "Darwin" ]]; then
    die "Este script suporta Linux e macOS. No Windows use setup/windows/setup.ps1."
fi

# Retorna o codinome correto para o repositório Adoptium.
# Mint e Pop!OS retornam seu próprio codinome via lsb_release, mas o Adoptium
# só conhece os codinomes Ubuntu/Debian. UBUNTU_CODENAME (em /etc/os-release)
# sempre contém o codinome da base Ubuntu nessas distros.
get_apt_codename() {
    local cn
    # 1ª opção: UBUNTU_CODENAME (Ubuntu, Mint, Pop!OS, etc.)
    cn=$(grep "^UBUNTU_CODENAME=" /etc/os-release 2>/dev/null | cut -d= -f2)
    [[ -n "$cn" ]] && echo "$cn" && return
    # 2ª opção: VERSION_CODENAME genérico (Debian, etc.)
    cn=$(grep "^VERSION_CODENAME=" /etc/os-release 2>/dev/null | cut -d= -f2)
    [[ -n "$cn" ]] && echo "$cn" && return
    # Fallback seguro: noble (Ubuntu 24.04 LTS)
    echo "noble"
}

# ── 2. Java ───────────────────────────────────────────────────────────────────
check_java() {
    if command -v java &>/dev/null; then
        local ver
        ver=$(java -version 2>&1 | awk -F '"' '/version/{print $2}' | cut -d. -f1)
        [[ "$ver" -ge "$JAVA_MIN" ]] && return 0
    fi
    return 1
}

if check_java; then
    ok "Java $(java -version 2>&1 | awk -F '"' '/version/{print $2}') encontrado"
else
    warn "Java $JAVA_MIN+ não encontrado. Instalando..."
    if [[ "$OS" == "Linux" ]]; then
        if command -v apt-get &>/dev/null; then
            info "Adicionando repositório Adoptium (Eclipse Temurin)..."
            # Remove entrada antiga (pode ter codinome errado de tentativa anterior)
            sudo rm -f /etc/apt/sources.list.d/adoptium.list
            sudo apt-get update -q
            sudo apt-get install -y wget apt-transport-https gnupg
            wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
                | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
            ADOPTIUM_CODENAME="$(get_apt_codename)"
            info "Usando codinome para repositório Adoptium: $ADOPTIUM_CODENAME"
            echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] \
https://packages.adoptium.net/artifactory/deb ${ADOPTIUM_CODENAME} main" \
                | sudo tee /etc/apt/sources.list.d/adoptium.list
            sudo apt-get update -q
            sudo apt-get install -y temurin-17-jdk
        elif command -v dnf &>/dev/null; then
            sudo dnf install -y java-17-openjdk-devel
        elif command -v pacman &>/dev/null; then
            sudo pacman -Sy --noconfirm jdk17-openjdk
        else
            die "Gerenciador de pacotes não reconhecido. Instale o JDK 17 manualmente: https://adoptium.net"
        fi
    elif [[ "$OS" == "Darwin" ]]; then
        if command -v brew &>/dev/null; then
            brew install --cask temurin@17
        else
            die "Homebrew não encontrado. Instale em https://brew.sh ou baixe o Java em https://adoptium.net"
        fi
    fi
    check_java || die "Falha ao instalar Java. Instale o JDK 17+ manualmente e rode o script novamente."
    ok "Java instalado com sucesso"
fi

# ── 3. Maven ──────────────────────────────────────────────────────────────────
if command -v mvn &>/dev/null; then
    ok "Maven $(mvn --version | head -1 | awk '{print $3}') encontrado"
else
    warn "Maven não encontrado. Instalando..."
    if [[ "$OS" == "Linux" ]]; then
        if command -v apt-get &>/dev/null; then
            sudo apt-get install -y maven
        elif command -v dnf &>/dev/null; then
            sudo dnf install -y maven
        elif command -v pacman &>/dev/null; then
            sudo pacman -Sy --noconfirm maven
        else
            MVN_VERSION="3.9.9"
            MVN_URL="https://archive.apache.org/dist/maven/maven-3/${MVN_VERSION}/binaries/apache-maven-${MVN_VERSION}-bin.tar.gz"
            info "Baixando Maven $MVN_VERSION..."
            wget -q "$MVN_URL" -O /tmp/maven.tar.gz
            sudo tar -xzf /tmp/maven.tar.gz -C /opt
            sudo ln -sf "/opt/apache-maven-${MVN_VERSION}/bin/mvn" /usr/local/bin/mvn
            rm /tmp/maven.tar.gz
        fi
    elif [[ "$OS" == "Darwin" ]]; then
        brew install maven
    fi
    command -v mvn &>/dev/null || die "Falha ao instalar Maven."
    ok "Maven instalado com sucesso"
fi

# ── 4. Compilar ───────────────────────────────────────────────────────────────
echo ""
info "Compilando o projeto (pode demorar na primeira vez para baixar dependências)..."
cd "$PROJECT_DIR"
mvn package -DskipTests -q \
    || die "Falha na compilação. Verifique a saída acima."
ok "Build concluído → target/sifin-1.0.0.jar"

# ── 5. Criar data/ ────────────────────────────────────────────────────────────
mkdir -p "$PROJECT_DIR/data"

# ── 6. Tornar run.sh executável ──────────────────────────────────────────────
RUN_SCRIPT="$PROJECT_DIR/run.sh"
chmod +x "$RUN_SCRIPT"
ok "run.sh pronto"

# ── 7. Atalho na área de trabalho ─────────────────────────────────────────────
if [[ "$OS" == "Linux" ]]; then
    ICON_SRC="$PROJECT_DIR/src/main/resources/icons/logo-s-256.png"
    ICON_DEST="$HOME/.local/share/icons/sifin.png"
    mkdir -p "$HOME/.local/share/icons"
    cp "$ICON_SRC" "$ICON_DEST"

    DESKTOP_CONTENT="[Desktop Entry]
Name=Sifin
Comment=Painel de Investimentos
Exec=bash -c 'cd \"$PROJECT_DIR\" && \"$RUN_SCRIPT\"'
Icon=$ICON_DEST
Terminal=false
Type=Application
Categories=Finance;Office;
StartupWMClass=Sifin"

    APPS_DIR="$HOME/.local/share/applications"
    mkdir -p "$APPS_DIR"
    echo "$DESKTOP_CONTENT" > "$APPS_DIR/sifin.desktop"
    chmod +x "$APPS_DIR/sifin.desktop"

    DESKTOP_DIR="$HOME/Desktop"
    [[ ! -d "$DESKTOP_DIR" ]] && DESKTOP_DIR="$HOME/Área de Trabalho"
    if [[ -d "$DESKTOP_DIR" ]]; then
        echo "$DESKTOP_CONTENT" > "$DESKTOP_DIR/sifin.desktop"
        chmod +x "$DESKTOP_DIR/sifin.desktop"
        if command -v gio &>/dev/null; then
            gio set "$DESKTOP_DIR/sifin.desktop" metadata::trusted true 2>/dev/null || true
        fi
        ok "Atalho criado na área de trabalho"
    else
        ok "Atalho criado no menu de aplicativos (pasta Desktop não encontrada)"
    fi

    if command -v update-desktop-database &>/dev/null; then
        update-desktop-database "$APPS_DIR" 2>/dev/null || true
    fi
elif [[ "$OS" == "Darwin" ]]; then
    warn "macOS: atalho na área de trabalho não implementado neste script."
fi

echo ""
echo "╔══════════════════════════════════════╗"
echo "║         Setup concluído! ✔           ║"
echo "╚══════════════════════════════════════╝"
echo ""
echo "  Para iniciar o Sifin:"
echo -e "    ${CYAN}./run.sh${NC}  (ou pelo atalho na área de trabalho)"
echo ""
echo "  Para recompilar após alterações:"
echo -e "    ${CYAN}mvn package -DskipTests${NC}"
echo ""
