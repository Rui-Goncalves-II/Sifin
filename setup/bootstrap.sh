#!/usr/bin/env bash
# bootstrap.sh — instala o Sifin do zero (clona + setup).
#
# Uso:
#   bash <(curl -fsSL https://raw.githubusercontent.com/Rui-Goncalves-II/Sifin/main/setup/bootstrap.sh)
#
# Ou, após fazer download manual:
#   bash setup/bootstrap.sh
set -euo pipefail

REPO_URL="https://github.com/Rui-Goncalves-II/Sifin.git"
INSTALL_DIR="$HOME/Sifin"

GREEN='\033[0;32m'; CYAN='\033[0;36m'; RED='\033[0;31m'; NC='\033[0m'
ok()  { echo -e "${GREEN}✔${NC} $*"; }
info(){ echo -e "${CYAN}→${NC} $*"; }
die() { echo -e "${RED}✘ ERRO:${NC} $*" >&2; exit 1; }

echo ""
echo "╔══════════════════════════════════════╗"
echo "║     Sifin — Instalação Completa      ║"
echo "╚══════════════════════════════════════╝"
echo ""

command -v git &>/dev/null || die "git não encontrado. Instale o git e tente novamente."

if [[ -d "$INSTALL_DIR" ]]; then
    die "A pasta $INSTALL_DIR já existe. Para reinstalar, remova-a primeiro:  rm -rf $INSTALL_DIR"
fi

info "Clonando repositório em $INSTALL_DIR ..."
git clone "$REPO_URL" "$INSTALL_DIR"
ok "Repositório clonado"

info "Iniciando setup..."
bash "$INSTALL_DIR/setup/linux/setup.sh"
