package br.investimentos.ui.util;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aplica tooltips explicativos para siglas financeiras nos componentes da UI.
 * Chamada centralizada via {@link #aplicar(Node, String)}.
 */
public final class GlossarioTooltip {

    // LinkedHashMap mantém ordem de inserção — importante para evitar matches curtos
    // sobreporem matches longos (ex: "R" antes de "RARF").
    private static final Map<String, String> SIGLAS = new LinkedHashMap<>();

    static {
        // ── Dashboard ────────────────────────────────────────────────────
        SIGLAS.put("PTA",   "PTA — Patrimônio Total Atual\n= VTARF + VTARV");
        SIGLAS.put("VTIA",  "VTIA — Valor Total Investido Acumulado\n= VIARF + VIARV");
        SIGLAS.put("VTRA",  "VTRA — Valor Total de Rendimentos Acumulados\n= RARF + DTA");
        SIGLAS.put("VIARF", "VIARF — Valor Investido Acumulado em Renda Fixa\n= Σ (Depósitos − Saques) do período");
        SIGLAS.put("RARF",  "RARF — Rendimento Acumulado de Renda Fixa\n= Σ R dos ativos RF no período");
        SIGLAS.put("VTARF", "VTARF — Valor Total Acumulado em Renda Fixa\n= VIARF + RARF");
        SIGLAS.put("VIARV", "VIARV — Valor Investido Acumulado em Renda Variável\n= Σ (qtd × preço) das compras");
        SIGLAS.put("PARV",  "PARV — Patrimônio Atual em Renda Variável\n= Σ (VAC × QC) — sempre valor atual de mercado");
        SIGLAS.put("DTA",   "DTA — Dividendos Totais Acumulados\n= Σ dividendos RV no período");
        SIGLAS.put("VTARV", "VTARV — Valor Total Acumulado em Renda Variável\n= PARV + DTA");
        SIGLAS.put("Crescimento Patrimonial",    "PCPA — Percentual de Crescimento Patrimonial\n= (VTRA / PTA) × 100");
        SIGLAS.put("Rendimento sobre Investido", "PRAT — Percentual de Rendimento sobre Investido\n= (VTRA / VTIA) × 100");
        // ── Renda Fixa ───────────────────────────────────────────────────
        SIGLAS.put("VTA",   "VTA — Valor Total Atual\nValor real do investimento informado no mês");
        SIGLAS.put("VAI",   "VAI — Valor Atual Inicial\nSaldo de 31/12 do ano anterior (base do cálculo anual)");
        SIGLAS.put("SAM",   "SAM — Saldo Acumulado do Mês\n= Σ (Depósitos − Saques) do início do ano até o mês");
        SIGLAS.put("VI",    "VI — Valor Inicial\n= VAI + SAM\nCapital efetivamente aplicado no período");
        SIGLAS.put("R",     "R — Rendimento\n= VTA − VI\nPode ser negativo");
        // ── Renda Variável ───────────────────────────────────────────────
        SIGLAS.put("VTP",   "VTP — Valor Total Pago\n= Σ (qtd × preço) de todas as compras");
        SIGLAS.put("QC",    "QC — Quantidade de Cotas\n= Cotas compradas − cotas vendidas");
        SIGLAS.put("VMA",   "VMA — Valor Médio de Aquisição\n= VTP / QC");
        SIGLAS.put("VAC",   "VAC — Valor Atual por Cota\nCotação atual do ativo (API ou informada manualmente)");
        SIGLAS.put("PTG",   "PTG — Patrimônio Total com Ganhos\n= (VAC × QC) + Dividendos");
        SIGLAS.put("LEB",   "LEB — Lucro/Prejuízo Estimado Bruto\n= (VAC × QC) − VTP");
        SIGLAS.put("LEB %", "LEB % — Rentabilidade Bruta\n= (LEB / VTP) × 100");
        SIGLAS.put("LEA",   "LEA — Lucro/Prejuízo Estimado Acumulado\n= LEB + Dividendos");
        SIGLAS.put("LEA %", "LEA % — Rentabilidade Acumulada com Dividendos\n= (LEA / VTP) × 100");
        SIGLAS.put("D",     "D — Dividendos\n= Σ valores de todos os dividendos recebidos");
        // ── Gastos ───────────────────────────────────────────────────────
        SIGLAS.put("GAT",   "GAT — Gastos Alimentares\n= Σ gastos da categoria Alimentar no período");
        SIGLAS.put("GDT",   "GDT — Gastos Diversos\n= Σ gastos da categoria Diversos no período");
        SIGLAS.put("GMT",   "GMT — Gastos em Mensalidades\n= Σ gastos da categoria Mensalidades no período");
        SIGLAS.put("GT",    "GT — Gastos Totais\n= GAT + GDT + GMT");
    }

    private GlossarioTooltip() {}

    /**
     * Instala tooltip no {@code node} se {@code labelText} corresponde a uma sigla conhecida.
     * <p>Regras de match (em ordem de prioridade):
     * <ul>
     *   <li>Exato: {@code "VTA"}</li>
     *   <li>Prefixo seguido de espaço: {@code "VAI 2026"}, {@code "SAM 05/2026"}</li>
     *   <li>Entre parênteses: {@code "Rendimento (R)"}, {@code "Dividendos (D)"}</li>
     *   <li>Label completo (para siglas de duas palavras): {@code "Crescimento Patrimonial"}</li>
     * </ul>
     */
    public static void aplicar(Node node, String labelText) {
        if (labelText == null || labelText.isBlank()) return;
        for (Map.Entry<String, String> entry : SIGLAS.entrySet()) {
            String s = entry.getKey();
            if (labelText.equals(s)
                    || labelText.startsWith(s + " ")
                    || labelText.contains("(" + s + ")")) {
                Tooltip tip = new Tooltip(entry.getValue());
                tip.setShowDelay(Duration.millis(350));
                tip.setHideDelay(Duration.ZERO);
                tip.setWrapText(true);
                tip.setMaxWidth(300);
                tip.setStyle(
                        "-fx-background-color: #1c2230;" +
                        "-fx-border-color: #2a3441;" +
                        "-fx-border-width: 1px;" +
                        "-fx-text-fill: #e6edf3;" +
                        "-fx-font-size: 16px;" +
                        "-fx-padding: 8 12;"
                );
                Tooltip.install(node, tip);
                node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> tip.hide());
                node.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused) tip.hide();
                });
                return;
            }
        }
    }
}
