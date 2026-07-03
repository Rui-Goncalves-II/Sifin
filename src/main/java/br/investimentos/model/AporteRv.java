package br.investimentos.model;

import br.investimentos.model.enums.TipoOperacaoRv;

public class AporteRv {
    private int id;
    private int investimentoId;
    private TipoOperacaoRv tipoOp;
    private int periodoDia;
    private int periodoMes;
    private int periodoAno;
    private Double quantidade;
    private Double precoPorCota;
    private double valor;
    private String notas;
    private String criadoEm;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getInvestimentoId() { return investimentoId; }
    public void setInvestimentoId(int investimentoId) { this.investimentoId = investimentoId; }
    public TipoOperacaoRv getTipoOp() { return tipoOp; }
    public void setTipoOp(TipoOperacaoRv tipoOp) { this.tipoOp = tipoOp; }
    public int getPeriodoDia() { return periodoDia; }
    public void setPeriodoDia(int periodoDia) { this.periodoDia = periodoDia; }
    public int getPeriodoMes() { return periodoMes; }
    public void setPeriodoMes(int periodoMes) { this.periodoMes = periodoMes; }
    public int getPeriodoAno() { return periodoAno; }
    public void setPeriodoAno(int periodoAno) { this.periodoAno = periodoAno; }
    public Double getQuantidade() { return quantidade; }
    public void setQuantidade(Double quantidade) { this.quantidade = quantidade; }
    public Double getPrecoPorCota() { return precoPorCota; }
    public void setPrecoPorCota(Double precoPorCota) { this.precoPorCota = precoPorCota; }
    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }
}
