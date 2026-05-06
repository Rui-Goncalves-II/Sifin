package br.investimentos.model;

import br.investimentos.model.enums.TipoGasto;

public class Gasto {
    private int id;
    private TipoGasto tipo;
    private String descricao;
    private int periodoMes;
    private int periodoAno;
    private double valor;
    private String notas;
    private int parcelasTotal = 1;
    private int parcelaNumero = 1;
    private String grupoParcela;
    private String criadoEm;

    public boolean isParcelado() { return parcelasTotal > 1; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public TipoGasto getTipo() { return tipo; }
    public void setTipo(TipoGasto tipo) { this.tipo = tipo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public int getPeriodoMes() { return periodoMes; }
    public void setPeriodoMes(int periodoMes) { this.periodoMes = periodoMes; }
    public int getPeriodoAno() { return periodoAno; }
    public void setPeriodoAno(int periodoAno) { this.periodoAno = periodoAno; }
    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public int getParcelasTotal() { return parcelasTotal; }
    public void setParcelasTotal(int parcelasTotal) { this.parcelasTotal = parcelasTotal; }
    public int getParcelaNumero() { return parcelaNumero; }
    public void setParcelaNumero(int parcelaNumero) { this.parcelaNumero = parcelaNumero; }
    public String getGrupoParcela() { return grupoParcela; }
    public void setGrupoParcela(String grupoParcela) { this.grupoParcela = grupoParcela; }
    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }
}
