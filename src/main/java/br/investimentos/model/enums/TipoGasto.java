package br.investimentos.model.enums;

public enum TipoGasto {
    ALIMENTAR("Alimentar"),
    DIVERSO("Diversos"),
    MENSALIDADE("Mensalidades");

    private final String label;

    TipoGasto(String label) { this.label = label; }

    public String getLabel() { return label; }
}
