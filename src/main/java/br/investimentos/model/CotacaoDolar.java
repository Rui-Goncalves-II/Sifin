package br.investimentos.model;

public class CotacaoDolar {
    private int id;
    private String data;
    private double valorCompra;
    private double valorVenda;
    private String fonte;
    private String criadoEm;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public double getValorCompra() { return valorCompra; }
    public void setValorCompra(double valorCompra) { this.valorCompra = valorCompra; }
    public double getValorVenda() { return valorVenda; }
    public void setValorVenda(double valorVenda) { this.valorVenda = valorVenda; }
    public String getFonte() { return fonte; }
    public void setFonte(String fonte) { this.fonte = fonte; }
    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String criadoEm) { this.criadoEm = criadoEm; }
}
