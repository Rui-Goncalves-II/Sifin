package br.investimentos.model.enums;

public enum TipoMovimentacao {
    DEPOSITO, SAQUE;

    public String label() {
        return switch (this) {
            case DEPOSITO -> "Depósito";
            case SAQUE -> "Saque";
        };
    }
}
