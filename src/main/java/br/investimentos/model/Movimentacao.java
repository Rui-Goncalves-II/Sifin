package br.investimentos.model;

import br.investimentos.model.enums.TipoMovimentacao;

public class Movimentacao {
    private int id;
    private int investimentoId;
    private int periodoDia;
    private int periodoMes;
    private int periodoAno;
    private TipoMovimentacao tipoMov;
    private double valor;
    private Double cotacaoDolar;
    private String notas;
    private String criadoEm;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getInvestimentoId() { return investimentoId; }
    public void setInvestimentoId(int investimentoId) { this.investimentoId = investimentoId; }
    public int getPeriodoDia() { return periodoDia; }
    public void setPeriodoDia(int periodoDia) { this.periodoDia = periodoDia; }
    public int getPeriodoMes() { return periodoMes; }
    public void setPeriodoMes(int periodoMes) { this.periodoMes = periodoMes; }
    public int getPeriodoAno() { return periodoAno; }
    public void setPeriodoAno(int periodoAno) { this.periodoAno = periodoAno; }
    public TipoMovimentacao getTipoMov() { return tipoMov; }
    public void setTipoMov(TipoMovimentacao tipoMov) { this.tipoMov = tipoMov; }
    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }
    public Double getCotacaoDolar() { return cotacaoDolar; }
    public void setCotacaoDolar(Double cotacaoDolar) { this.cotacaoDolar = cotacaoDolar; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }
}
