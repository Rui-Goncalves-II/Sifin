package br.investimentos.service;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.Investimento;
import br.investimentos.model.VaiAnual;
import br.investimentos.model.VtaMensal;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.repository.VaiAnualRepository;
import br.investimentos.repository.VtaMensalRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class VaiService {

    private final InvestimentoRepository invRepo;
    private final VaiAnualRepository vaiRepo;
    private final VtaMensalRepository vtaRepo;
    private final DatabaseManager db;

    public VaiService(InvestimentoRepository invRepo, VaiAnualRepository vaiRepo,
                      VtaMensalRepository vtaRepo, DatabaseManager db) {
        this.invRepo = invRepo;
        this.vaiRepo = vaiRepo;
        this.vtaRepo = vtaRepo;
        this.db = db;
    }

    /**
     * Chamado na 1ª abertura após 1º/jan.
     * Para cada ativo RF: cria VAI do novo ano = último VTA do ano anterior.
     */
    public void virarAno() {
        LocalDate hoje = LocalDate.now();
        int anoAtual = hoje.getYear();

        String chave = "ultimo_virada_ano";
        int ultimaVirada = getConfig(chave);

        if (ultimaVirada >= anoAtual) return;

        int anoAnterior = anoAtual - 1;
        List<Investimento> rfAtivos = invRepo.findByTipo(TipoInvestimento.RENDA_FIXA);

        for (Investimento inv : rfAtivos) {
            if (vaiRepo.find(inv.getId(), anoAtual).isPresent()) continue;

            Optional<VtaMensal> ultimoVtaOpt = vtaRepo.findUltimoDoAno(inv.getId(), anoAnterior);
            double valorVai = ultimoVtaOpt.map(VtaMensal::getVta).orElse(0.0);

            VaiAnual novoVai = new VaiAnual();
            novoVai.setInvestimentoId(inv.getId());
            novoVai.setAno(anoAtual);
            novoVai.setVai(valorVai);
            vaiRepo.salvar(novoVai);
        }

        setConfig(chave, String.valueOf(anoAtual));
    }

    private int getConfig(String chave) {
        String sql = "SELECT valor FROM configuracoes WHERE chave=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chave);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Integer.parseInt(rs.getString(1));
        } catch (Exception e) { /* ignora */ }
        return 0;
    }

    /**
     * Chamado após salvar um VTA de um ano anterior ao ano atual.
     * Recalcula o VAI do ano seguinte ao VTA salvo = último VTA disponível do ano do VTA.
     */
    public void recalcularVaiDoAnoSeguinte(int investimentoId, int anoVta) {
        int anoSeguinte = anoVta + 1;
        Optional<VtaMensal> ultimoVtaOpt = vtaRepo.findUltimoDoAno(investimentoId, anoVta);
        if (ultimoVtaOpt.isEmpty()) return;

        double novoVai = ultimoVtaOpt.get().getVta();
        VaiAnual vai = new VaiAnual();
        vai.setInvestimentoId(investimentoId);
        vai.setAno(anoSeguinte);
        vai.setVai(novoVai);
        vaiRepo.salvar(vai);
    }

    private void setConfig(String chave, String valor) {
        String sql = "INSERT INTO configuracoes (chave,valor) VALUES (?,?) ON CONFLICT(chave) DO UPDATE SET valor=excluded.valor";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chave);
            ps.setString(2, valor);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
}
