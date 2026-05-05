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
    private String criadoEm;

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
    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }
}
