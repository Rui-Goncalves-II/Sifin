package br.investimentos.model;

public class VacMensal {
    private int id;
    private int investimentoId;
    private int periodoMes;
    private int periodoAno;
    private double vac;
    private String fonte;
    private String criadoEm;
    private String atualizadoEm;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getInvestimentoId() { return investimentoId; }
    public void setInvestimentoId(int investimentoId) { this.investimentoId = investimentoId; }
    public int getPeriodoMes() { return periodoMes; }
    public void setPeriodoMes(int periodoMes) { this.periodoMes = periodoMes; }
    public int getPeriodoAno() { return periodoAno; }
    public void setPeriodoAno(int periodoAno) { this.periodoAno = periodoAno; }
    public double getVac() { return vac; }
    public void setVac(double vac) { this.vac = vac; }
    public String getFonte() { return fonte; }
    public void setFonte(String fonte) { this.fonte = fonte; }
    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }
    public String getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(String atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
