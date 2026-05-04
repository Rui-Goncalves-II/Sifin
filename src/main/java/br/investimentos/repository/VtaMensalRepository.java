package br.investimentos.repository;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.VtaMensal;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VtaMensalRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public VtaMensal salvar(VtaMensal v) {
        String sql = "INSERT INTO vta_mensal (investimento_id,periodo_mes,periodo_ano,vta) VALUES (?,?,?,?) " +
                "ON CONFLICT(investimento_id,periodo_mes,periodo_ano) DO UPDATE SET vta=excluded.vta, atualizado_em=datetime('now','localtime')";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, v.getInvestimentoId());
            ps.setInt(2, v.getPeriodoMes());
            ps.setInt(3, v.getPeriodoAno());
            ps.setDouble(4, v.getVta());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next() && rs.getInt(1) > 0) v.setId(rs.getInt(1));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return v;
    }

    public void deletar(int id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM vta_mensal WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<VtaMensal> find(int investimentoId, int mes, int ano) {
        String sql = "SELECT * FROM vta_mensal WHERE investimento_id=? AND periodo_mes=? AND periodo_ano=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, mes);
            ps.setInt(3, ano);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    public Optional<VtaMensal> findUltimoDoAno(int investimentoId, int ano) {
        String sql = "SELECT * FROM vta_mensal WHERE investimento_id=? AND periodo_ano=? ORDER BY periodo_mes DESC LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, ano);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    public Optional<VtaMensal> findUltimo(int investimentoId) {
        String sql = "SELECT * FROM vta_mensal WHERE investimento_id=? ORDER BY periodo_ano DESC, periodo_mes DESC LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    public List<VtaMensal> findByInvestimento(int investimentoId) {
        String sql = "SELECT * FROM vta_mensal WHERE investimento_id=? ORDER BY periodo_ano, periodo_mes";
        List<VtaMensal> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public List<VtaMensal> findByInvestimentoEAno(int investimentoId, int ano) {
        String sql = "SELECT * FROM vta_mensal WHERE investimento_id=? AND periodo_ano=? ORDER BY periodo_mes";
        List<VtaMensal> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, ano);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    private VtaMensal map(ResultSet rs) throws SQLException {
        VtaMensal v = new VtaMensal();
        v.setId(rs.getInt("id"));
        v.setInvestimentoId(rs.getInt("investimento_id"));
        v.setPeriodoMes(rs.getInt("periodo_mes"));
        v.setPeriodoAno(rs.getInt("periodo_ano"));
        v.setVta(rs.getDouble("vta"));
        v.setCriadoEm(rs.getString("criado_em"));
        v.setAtualizadoEm(rs.getString("atualizado_em"));
        return v;
    }
}
