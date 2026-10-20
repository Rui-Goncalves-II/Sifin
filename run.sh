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
JAR=$(find "$DIR/target" -maxdepth 1 -name 'sifin-*.jar' ! -name '*original*' | sort -V | tail -n1)

if [[ -z "$JAR" ]]; then
    echo -e "${YELLOW}⚠${NC}  Nenhum jar encontrado em target/. Rode 'mvn package' primeiro."
    exit 1
fi

# ── escala da interface ─────────────────────────────────────────────────────────
# No Hyprland (ex.: Omarchy) o GDK_SCALE costuma ser 2 com xwayland force_zero_scaling, e o
# JavaFX renderiza em 2x mesmo num monitor com escala 1.5 — o layout fica espremido. Usa a
# escala real do monitor focado. Pode ser sobrescrita com SIFIN_SCALE=1.25 ./run.sh
SCALE="${SIFIN_SCALE:-}"
if [[ -z "$SCALE" ]] && command -v hyprctl &>/dev/null; then
    if command -v jq &>/dev/null; then
        SCALE=$(hyprctl monitors -j 2>/dev/null | jq -r '(map(select(.focused)) + .)[0].scale // empty')
    else
        SCALE=$(hyprctl monitors 2>/dev/null | awk '/scale:/ {print $2; exit}')
    fi
fi
JAVA_OPTS=()
[[ "$SCALE" =~ ^[0-9]+([.][0-9]+)?$ ]] && JAVA_OPTS+=("-Dglass.gtk.uiScale=$SCALE")

java -Dapp.home="$DIR" \
     -Dfile.encoding=UTF-8 \
     "${JAVA_OPTS[@]}" \
     -jar "$JAR"
