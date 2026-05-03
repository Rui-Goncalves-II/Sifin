package br.investimentos.model.enums;

public enum TipoInvestimento {
    RENDA_FIXA, RENDA_VARIAVEL, DOLAR;

    public String label() {
        return switch (this) {
            case RENDA_FIXA -> "Renda Fixa";
            case RENDA_VARIAVEL -> "Renda Variável";
            case DOLAR -> "Dólar";
        };
    }
}
