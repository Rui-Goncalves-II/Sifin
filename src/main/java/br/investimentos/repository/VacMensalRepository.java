package br.investimentos.repository;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.VacMensal;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VacMensalRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public VacMensal salvar(VacMensal v) {
        String sql = "INSERT INTO vac_mensal (investimento_id,periodo_mes,periodo_ano,vac,fonte) VALUES (?,?,?,?,?) " +
                "ON CONFLICT(investimento_id,periodo_mes,periodo_ano) DO UPDATE SET vac=excluded.vac, fonte=excluded.fonte, atualizado_em=datetime('now','localtime')";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, v.getInvestimentoId());
            ps.setInt(2, v.getPeriodoMes());
            ps.setInt(3, v.getPeriodoAno());
            ps.setDouble(4, v.getVac());
            ps.setString(5, v.getFonte() != null ? v.getFonte() : "MANUAL");
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next() && rs.getInt(1) > 0) v.setId(rs.getInt(1));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return v;
    }

    public void deletar(int id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM vac_mensal WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<VacMensal> find(int investimentoId, int mes, int ano) {
        String sql = "SELECT * FROM vac_mensal WHERE investimento_id=? AND periodo_mes=? AND periodo_ano=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, mes);
            ps.setInt(3, ano);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    public Optional<VacMensal> findUltimo(int investimentoId) {
        String sql = "SELECT * FROM vac_mensal WHERE investimento_id=? ORDER BY periodo_ano DESC, periodo_mes DESC LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    public List<VacMensal> findByInvestimento(int investimentoId) {
        String sql = "SELECT * FROM vac_mensal WHERE investimento_id=? ORDER BY periodo_ano, periodo_mes";
        List<VacMensal> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    private VacMensal map(ResultSet rs) throws SQLException {
        VacMensal v = new VacMensal();
        v.setId(rs.getInt("id"));
        v.setInvestimentoId(rs.getInt("investimento_id"));
        v.setPeriodoMes(rs.getInt("periodo_mes"));
        v.setPeriodoAno(rs.getInt("periodo_ano"));
        v.setVac(rs.getDouble("vac"));
        v.setFonte(rs.getString("fonte"));
        v.setCriadoEm(rs.getString("criado_em"));
        v.setAtualizadoEm(rs.getString("atualizado_em"));
        return v;
    }
}
