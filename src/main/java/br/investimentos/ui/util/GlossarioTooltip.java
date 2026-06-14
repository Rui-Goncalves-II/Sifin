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
        // ── KPI cards do Dashboard ───────────────────────────────────────
        SIGLAS.put("Patrimônio Total",  "Patrimônio Total (PTA)\n= VTARF + VTARV + Dólar\nSoma de todos os ativos a valor atual de mercado.");
        SIGLAS.put("Total Investido",   "Total Investido (VTIA)\n= VIARF + VIARV\nCapital líquido efetivamente aplicado no período.");
        SIGLAS.put("Ganho / Perda",     "Ganho / Perda (VTRA)\n= RARF + DTA\nRendimentos de Renda Fixa + Dividendos de Renda Variável.");
        SIGLAS.put("Aportado",          "Aportado no período\n= Σ Depósitos RF + Dólar + Σ Compras RV − Σ Saques RF + Dólar − Σ Vendas RV\nCapital líquido aportado no período.");
        // ── Mini cards do Dashboard ───────────────────────────────────────
        SIGLAS.put("Renda Fixa",        "Renda Fixa\nValor principal: VIARF — capital líquido investido (depósitos − saques).\nSubtexto: RARF — rendimentos acumulados no período.");
        SIGLAS.put("Renda Variável",    "Renda Variável\nValor principal: PARV — patrimônio atual (VAC × QC).\nSubtexto: dividendos recebidos no período.");
        SIGLAS.put("Dólar",             "Dólar\nValor total da posição em USD convertido para BRL pela cotação de compra atual.");
        // ── Gastos KPI cards ──────────────────────────────────────────────
        SIGLAS.put("Gastos Totais",     "Gastos Totais (GT)\n= GAT + GDT + GMT\nSoma de todos os gastos do período.");
        SIGLAS.put("Alimentar",         "Gastos Alimentares (GAT)\n= Σ gastos da categoria Alimentar no período.");
        SIGLAS.put("Diversos",          "Gastos Diversos (GDT)\n= Σ gastos da categoria Diversos no período.");
        SIGLAS.put("Mensalidades",      "Mensalidades (GMT)\n= Σ (meses ativos × valor) de cada mensalidade no período.");
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
        // ── Dólar ────────────────────────────────────────────────────────
        SIGLAS.put("CMC",           "CMC — Cotação Média de Compra\n= Σ (USD × cotação) / Σ USD\nMédia ponderada dos depósitos com cotação registada.");
        SIGLAS.put("Cotação Atual", "Cotação Atual USD/BRL (compra)\nTaxa a que o mercado vende USD.\nFonte: AwesomeAPI, cache de 15 min.");
        SIGLAS.put("Valor de Compra", "Valor de Compra\nCapital líquido investido em reais.\n= Σ (depósito × cotação histórica) − Σ (saque × cotação histórica)\nMov. sem cotação usam a cotação actual como estimativa.");
        SIGLAS.put("Valor de Venda",  "Valor de Venda\nValor actual da posição USD em reais.\n= Saldo USD × cotação de venda actual");
        SIGLAS.put("Rendimento",      "Rendimento\nLucro ou prejuízo cambial não realizado.\n= Valor de Venda − Valor de Compra");
        SIGLAS.put("Rentabilidade %", "Rentabilidade %\nRetorno percentual sobre o capital investido.\n= (Rendimento / |Valor de Compra|) × 100");
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
