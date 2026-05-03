package br.investimentos.model.enums;

public enum TipoOperacaoRv {
    COMPRA, VENDA, DIVIDENDO;

    public String label() {
        return switch (this) {
            case COMPRA -> "Compra";
            case VENDA -> "Venda";
            case DIVIDENDO -> "Dividendo";
        };
    }
}
