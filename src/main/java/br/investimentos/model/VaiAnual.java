package br.investimentos.model;

public class VaiAnual {
    private int id;
    private int investimentoId;
    private int ano;
    private double vai;
    private String criadoEm;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getInvestimentoId() { return investimentoId; }
    public void setInvestimentoId(int investimentoId) { this.investimentoId = investimentoId; }
    public int getAno() { return ano; }
    public void setAno(int ano) { this.ano = ano; }
    public double getVai() { return vai; }
    public void setVai(double vai) { this.vai = vai; }
    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }
}
