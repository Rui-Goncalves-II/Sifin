package br.investimentos.service;

import br.investimentos.model.Gasto;
import br.investimentos.model.enums.TipoGasto;
import br.investimentos.repository.GastoRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class GastosService {

    public static final int ANO_TODOS = -1;

    public record ResumoMensal(int mes, int ano, double alimentar, double diverso, double mensalidade) {
        public double total() { return alimentar + diverso + mensalidade; }
    }

    public record ResumoSegmento(double total, int count, double maiorValor, String maiorDesc, double media) {}


    private final GastoRepository repo;

    public GastosService(GastoRepository repo) {
        this.repo = repo;
    }

    public double calcularGAT(int ano) {
        return somarVariavel(TipoGasto.ALIMENTAR, ano);
    }

    public double calcularGDT(int ano) {
        return somarVariavel(TipoGasto.DIVERSO, ano);
    }

    public double calcularGMT(int ano) {
        LocalDate hoje = LocalDate.now();
        int anoAtual = hoje.getYear();
        int mesAtual = hoje.getMonthValue();

        double total = 0;
        for (Gasto m : repo.findMensalidades()) {
            if (m.getPeriodoAno() == null) continue;

            if (m.getPeriodoAno() > ano) continue;
            int inicioMes = m.getPeriodoAno().equals(ano) ? m.getPeriodoMes() : 1;

            if (m.getFimAno() != null && m.getFimAno() < ano) continue;
            int fimMes = (m.getFimAno() != null && m.getFimAno().equals(ano)) ? m.getFimMes() : 12;

            if (ano == anoAtual) fimMes = Math.min(fimMes, mesAtual);

            int meses = Math.max(0, fimMes - inicioMes + 1);
            total += meses * m.getValor();
        }
        return total;
    }

    public double calcularGMP() {
        int anoAtual = LocalDate.now().getYear();
        double total = 0;
        for (Gasto m : repo.findMensalidades()) {
            if (m.getPeriodoAno() == null || m.getPeriodoAno() > anoAtual) continue;
            if (m.getFimAno() != null && m.getFimAno() < anoAtual) continue;

            int inicioMes = m.getPeriodoAno().equals(anoAtual) ? m.getPeriodoMes() : 1;
            int fimMes = (m.getFimAno() != null && m.getFimAno().equals(anoAtual)) ? m.getFimMes() : 12;

            int meses = Math.max(0, fimMes - inicioMes + 1);
            total += meses * m.getValor();
        }
        return total;
    }

    public double calcularGT(int ano) {
        return calcularGAT(ano) + calcularGDT(ano) + calcularGMT(ano);
    }

    private double somarVariavel(TipoGasto tipo, int ano) {
        List<Gasto> lista = (ano == ANO_TODOS)
                ? repo.findByTipo(tipo)
                : repo.findByTipoEAno(tipo, ano);
        return lista.stream().mapToDouble(Gasto::getValor).sum();
    }

    public List<ResumoMensal> calcularPorMes(int ano) {
        LocalDate hoje = LocalDate.now();
        int anoAtual = hoje.getYear();
        int mesAtual = hoje.getMonthValue();

        List<Gasto> mensalidades = repo.findMensalidades();

        List<Gasto> alimentar = (ano == ANO_TODOS)
                ? repo.findByTipo(TipoGasto.ALIMENTAR)
                : repo.findByTipoEAno(TipoGasto.ALIMENTAR, ano);
        List<Gasto> diverso = (ano == ANO_TODOS)
                ? repo.findByTipo(TipoGasto.DIVERSO)
                : repo.findByTipoEAno(TipoGasto.DIVERSO, ano);

        Map<String, double[]> mapa = new TreeMap<>();

        if (ano != ANO_TODOS) {
            int limMes = (ano == anoAtual) ? mesAtual : 12;
            for (int m = 1; m <= limMes; m++) {
                double mensal = mensalidadeParaMes(mensalidades, m, ano);
                mapa.put(String.format("%04d-%02d", ano, m), new double[]{m, ano, 0, 0, mensal});
            }
        }

        for (Gasto g : alimentar) {
            String chave = String.format("%04d-%02d", g.getPeriodoAno(), g.getPeriodoMes());
            double mensal = (ano == ANO_TODOS)
                    ? mensalidadeParaMes(mensalidades, g.getPeriodoMes(), g.getPeriodoAno()) : 0;
            double[] vals = mapa.computeIfAbsent(chave,
                    k -> new double[]{g.getPeriodoMes(), g.getPeriodoAno(), 0, 0, mensal});
            vals[2] += g.getValor();
        }
        for (Gasto g : diverso) {
            String chave = String.format("%04d-%02d", g.getPeriodoAno(), g.getPeriodoMes());
            double mensal = (ano == ANO_TODOS)
                    ? mensalidadeParaMes(mensalidades, g.getPeriodoMes(), g.getPeriodoAno()) : 0;
            double[] vals = mapa.computeIfAbsent(chave,
                    k -> new double[]{g.getPeriodoMes(), g.getPeriodoAno(), 0, 0, mensal});
            vals[3] += g.getValor();
        }

        List<ResumoMensal> result = new ArrayList<>();
        for (double[] v : mapa.values()) {
            result.add(new ResumoMensal((int) v[0], (int) v[1], v[2], v[3], v[4]));
        }
        return result;
    }

    private double mensalidadeParaMes(List<Gasto> mensalidades, int mes, int ano) {
        return mensalidades.stream()
                .filter(m -> m.isAtivaEm(mes, ano))
                .mapToDouble(Gasto::getValor)
                .sum();
    }

    public List<Integer> anosComDados() {
        return repo.anosComDados();
    }

    public List<Gasto> listarGastos(TipoGasto tipo, int mes, int ano) {
        if (mes == ANO_TODOS) return repo.findByTipoEAno(tipo, ano);
        return repo.findByTipoMesAno(tipo, mes, ano);
    }

    public ResumoSegmento calcularResumoSegmento(TipoGasto tipo, int mes, int ano) {
        List<Gasto> lista = listarGastos(tipo, mes, ano);
        if (lista.isEmpty()) return new ResumoSegmento(0, 0, 0, "—", 0);
        double total = lista.stream().mapToDouble(Gasto::getValor).sum();
        int count = lista.size();
        Gasto maior = lista.stream().max(java.util.Comparator.comparingDouble(Gasto::getValor)).orElseThrow();
        double media = total / count;
        return new ResumoSegmento(total, count, maior.getValor(), maior.getDescricao(), media);
    }
}
