package br.investimentos.model;

import br.investimentos.model.enums.TipoGasto;

public class Gasto {
    private int id;
    private TipoGasto tipo;
    private String descricao;
    private Integer periodoMes;
    private Integer periodoAno;
    private Integer fimMes;
    private Integer fimAno;
    private double valor;
    private String notas;
    private int parcelasTotal = 1;
    private int parcelaNumero = 1;
    private String grupoParcela;
    private String criadoEm;

    public boolean isParcelado() { return parcelasTotal > 1; }

    public boolean isAtiva() { return fimMes == null && fimAno == null; }

    public boolean isAtivaEm(int mes, int ano) {
        if (periodoAno == null || periodoMes == null) return false;
        if (ano < periodoAno || (ano == periodoAno && mes < periodoMes)) return false;
        if (fimAno != null) {
            if (ano > fimAno || (ano == fimAno && mes > fimMes)) return false;
        }
        return true;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public TipoGasto getTipo() { return tipo; }
    public void setTipo(TipoGasto tipo) { this.tipo = tipo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Integer getPeriodoMes() { return periodoMes; }
    public void setPeriodoMes(Integer periodoMes) { this.periodoMes = periodoMes; }
    public Integer getPeriodoAno() { return periodoAno; }
    public void setPeriodoAno(Integer periodoAno) { this.periodoAno = periodoAno; }
    public Integer getFimMes() { return fimMes; }
    public void setFimMes(Integer fimMes) { this.fimMes = fimMes; }
    public Integer getFimAno() { return fimAno; }
    public void setFimAno(Integer fimAno) { this.fimAno = fimAno; }
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
