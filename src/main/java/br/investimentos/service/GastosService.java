package br.investimentos.service;

import br.investimentos.model.Gasto;
import br.investimentos.model.enums.TipoGasto;
import br.investimentos.repository.GastoRepository;

import java.util.*;

public class GastosService {

    public static final int ANO_TODOS = -1;

    public record ResumoMensal(int mes, int ano, double alimentar, double diverso, double mensalidade) {
        public double total() { return alimentar + diverso + mensalidade; }
    }

    private final GastoRepository repo;

    public GastosService(GastoRepository repo) {
        this.repo = repo;
    }

    public double calcularGAT(int ano) {
        return somarTipo(TipoGasto.ALIMENTAR, ano);
    }

    public double calcularGDT(int ano) {
        return somarTipo(TipoGasto.DIVERSO, ano);
    }

    public double calcularGMT(int ano) {
        return somarTipo(TipoGasto.MENSALIDADE, ano);
    }

    public double calcularGT(int ano) {
        return calcularGAT(ano) + calcularGDT(ano) + calcularGMT(ano);
    }

    private double somarTipo(TipoGasto tipo, int ano) {
        List<Gasto> lista = (ano == ANO_TODOS)
                ? repo.findByTipo(tipo)
                : repo.findByTipoEAno(tipo, ano);
        return lista.stream().mapToDouble(Gasto::getValor).sum();
    }

    public List<ResumoMensal> calcularPorMes(int ano) {
        List<Gasto> todos = (ano == ANO_TODOS) ? repo.findAll() : repo.findByAno(ano);

        Map<String, double[]> mapa = new TreeMap<>();
        for (Gasto g : todos) {
            String chave = String.format("%04d-%02d", g.getPeriodoAno(), g.getPeriodoMes());
            double[] vals = mapa.computeIfAbsent(chave, k -> new double[]{g.getPeriodoMes(), g.getPeriodoAno(), 0, 0, 0});
            switch (g.getTipo()) {
                case ALIMENTAR    -> vals[2] += g.getValor();
                case DIVERSO      -> vals[3] += g.getValor();
                case MENSALIDADE  -> vals[4] += g.getValor();
            }
        }

        List<ResumoMensal> result = new ArrayList<>();
        for (double[] v : mapa.values()) {
            result.add(new ResumoMensal((int) v[0], (int) v[1], v[2], v[3], v[4]));
        }
        return result;
    }

    public List<Integer> anosComDados() {
        return repo.anosComDados();
    }
}
