package br.investimentos.model;

import br.investimentos.model.enums.TipoInvestimento;

public class Investimento {
    private int id;
    private String nome;
    private TipoInvestimento tipo;
    private String subtipo;
    private String indexador;
    private Double taxaAnual;
    private String dataVencimento;
    private String moeda;
    private boolean ativo;
    private String notas;
    private String criadoEm;
    private String atualizadoEm;

    public Investimento() { this.moeda = "BRL"; this.ativo = true; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoInvestimento getTipo() { return tipo; }
    public void setTipo(TipoInvestimento tipo) { this.tipo = tipo; }
    public String getSubtipo() { return subtipo; }
    public void setSubtipo(String subtipo) { this.subtipo = subtipo; }
    public String getIndexador() { return indexador; }
    public void setIndexador(String indexador) { this.indexador = indexador; }
    public Double getTaxaAnual() { return taxaAnual; }
    public void setTaxaAnual(Double taxaAnual) { this.taxaAnual = taxaAnual; }
    public String getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(String dataVencimento) { this.dataVencimento = dataVencimento; }
    public String getMoeda() { return moeda; }
    public void setMoeda(String moeda) { this.moeda = moeda; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }
    public String getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(String atualizadoEm) { this.atualizadoEm = atualizadoEm; }

    @Override public String toString() { return nome; }
}
