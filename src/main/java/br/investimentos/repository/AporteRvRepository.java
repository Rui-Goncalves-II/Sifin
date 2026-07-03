package br.investimentos.repository;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.AporteRv;
import br.investimentos.model.enums.TipoOperacaoRv;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AporteRvRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public AporteRv salvar(AporteRv a) {
        if (a.getId() == 0) {
            String sql = "INSERT INTO aportes_rv (investimento_id,tipo_op,periodo_dia,periodo_mes,periodo_ano,quantidade,preco_por_cota,valor,notas) VALUES (?,?,?,?,?,?,?,?,?)";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, a);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) a.setId(rs.getInt(1));
            } catch (SQLException e) { throw new RuntimeException(e); }
        } else {
            String sql = "UPDATE aportes_rv SET investimento_id=?,tipo_op=?,periodo_dia=?,periodo_mes=?,periodo_ano=?,quantidade=?,preco_por_cota=?,valor=?,notas=? WHERE id=?";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, a);
                ps.setInt(10, a.getId());
                ps.executeUpdate();
            } catch (SQLException e) { throw new RuntimeException(e); }
        }
        return a;
    }

    private void bind(PreparedStatement ps, AporteRv a) throws SQLException {
        ps.setInt(1, a.getInvestimentoId());
        ps.setString(2, a.getTipoOp().name());
        ps.setInt(3, a.getPeriodoDia());
        ps.setInt(4, a.getPeriodoMes());
        ps.setInt(5, a.getPeriodoAno());
        if (a.getQuantidade() != null) ps.setDouble(6, a.getQuantidade()); else ps.setNull(6, Types.REAL);
        if (a.getPrecoPorCota() != null) ps.setDouble(7, a.getPrecoPorCota()); else ps.setNull(7, Types.REAL);
        ps.setDouble(8, a.getValor());
        ps.setString(9, a.getNotas());
    }

    public void deletar(int id) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM aportes_rv WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<AporteRv> findByInvestimento(int investimentoId) {
        String sql = "SELECT * FROM aportes_rv WHERE investimento_id=? ORDER BY periodo_ano, periodo_mes, id";
        List<AporteRv> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public List<AporteRv> findByInvestimentoAnoMes(int investimentoId, int ano, int mes) {
        String sql = "SELECT * FROM aportes_rv WHERE investimento_id=? AND periodo_ano=? AND periodo_mes=? ORDER BY id";
        List<AporteRv> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, ano);
            ps.setInt(3, mes);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public List<AporteRv> findByInvestimentoEAno(int investimentoId, int ano) {
        String sql = "SELECT * FROM aportes_rv WHERE investimento_id=? AND periodo_ano=? ORDER BY periodo_mes";
        List<AporteRv> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, ano);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    private AporteRv map(ResultSet rs) throws SQLException {
        AporteRv a = new AporteRv();
        a.setId(rs.getInt("id"));
        a.setInvestimentoId(rs.getInt("investimento_id"));
        a.setTipoOp(TipoOperacaoRv.valueOf(rs.getString("tipo_op")));
        a.setPeriodoDia(rs.getInt("periodo_dia"));
        a.setPeriodoMes(rs.getInt("periodo_mes"));
        a.setPeriodoAno(rs.getInt("periodo_ano"));
        double qtd = rs.getDouble("quantidade");
        a.setQuantidade(rs.wasNull() ? null : qtd);
        double preco = rs.getDouble("preco_por_cota");
        a.setPrecoPorCota(rs.wasNull() ? null : preco);
        a.setValor(rs.getDouble("valor"));
        a.setNotas(rs.getString("notas"));
        a.setCriadoEm(rs.getString("criado_em"));
        return a;
    }
}
