package br.investimentos.repository;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.VaiAnual;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VaiAnualRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public VaiAnual salvar(VaiAnual v) {
        String sql = "INSERT INTO vai_anual (investimento_id,ano,vai) VALUES (?,?,?) " +
                "ON CONFLICT(investimento_id,ano) DO UPDATE SET vai=excluded.vai";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, v.getInvestimentoId());
            ps.setInt(2, v.getAno());
            ps.setDouble(3, v.getVai());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next() && rs.getInt(1) > 0) v.setId(rs.getInt(1));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return v;
    }

    public double getVai(int investimentoId, int ano) {
        String sql = "SELECT vai FROM vai_anual WHERE investimento_id=? AND ano=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, ano);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
        return 0.0;
    }

    public Optional<VaiAnual> find(int investimentoId, int ano) {
        String sql = "SELECT * FROM vai_anual WHERE investimento_id=? AND ano=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, ano);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    public List<VaiAnual> findByInvestimento(int investimentoId) {
        String sql = "SELECT * FROM vai_anual WHERE investimento_id=? ORDER BY ano";
        List<VaiAnual> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    private VaiAnual map(ResultSet rs) throws SQLException {
        VaiAnual v = new VaiAnual();
        v.setId(rs.getInt("id"));
        v.setInvestimentoId(rs.getInt("investimento_id"));
        v.setAno(rs.getInt("ano"));
        v.setVai(rs.getDouble("vai"));
        v.setCriadoEm(rs.getString("criado_em"));
        return v;
    }
}
