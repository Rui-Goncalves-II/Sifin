#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

# ── verificar atualização ──────────────────────────────────────────────────────
if command -v git &>/dev/null && git -C "$DIR" rev-parse --git-dir &>/dev/null 2>&1; then
    LOCAL=$(git -C "$DIR" rev-parse HEAD 2>/dev/null)
    echo -e "${CYAN}→${NC} Verificando atualizações..."

    if git -C "$DIR" fetch origin --quiet 2>/dev/null; then
        # tenta upstream configurado, depois origin/main, depois origin/master
        REMOTE=$(git -C "$DIR" rev-parse "@{upstream}" 2>/dev/null \
              || git -C "$DIR" rev-parse "origin/main"   2>/dev/null \
              || git -C "$DIR" rev-parse "origin/master" 2>/dev/null \
              || echo "")

        if [[ -n "$REMOTE" && "$LOCAL" != "$REMOTE" ]]; then
            echo -e "${YELLOW}⚠${NC}  Nova versão disponível. Atualizando..."
            git -C "$DIR" pull --quiet
            echo -e "${CYAN}→${NC} Recompilando..."
            mvn -f "$DIR/pom.xml" package -DskipTests -q
            echo -e "${GREEN}✔${NC} Atualizado com sucesso!"
        else
            echo -e "${GREEN}✔${NC} Versão já é a mais recente."
        fi
    else
        echo -e "${YELLOW}⚠${NC}  Sem conexão — iniciando sem verificar atualizações."
    fi
fi

# ── iniciar ───────────────────────────────────────────────────────────────────
java -Dapp.home="$DIR" \
     -Dfile.encoding=UTF-8 \
     -jar "$DIR/target/sifin-1.0.0.jar"
