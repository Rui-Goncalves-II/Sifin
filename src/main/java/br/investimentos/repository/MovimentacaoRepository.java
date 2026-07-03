package br.investimentos.repository;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.enums.TipoMovimentacao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MovimentacaoRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public Movimentacao salvar(Movimentacao mov) {
        if (mov.getId() == 0) {
            String sql = "INSERT INTO movimentacoes (investimento_id,periodo_dia,periodo_mes,periodo_ano,tipo_mov,valor,cotacao_dolar,notas) VALUES (?,?,?,?,?,?,?,?)";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, mov);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) mov.setId(rs.getInt(1));
            } catch (SQLException e) { throw new RuntimeException(e); }
        } else {
            String sql = "UPDATE movimentacoes SET investimento_id=?,periodo_dia=?,periodo_mes=?,periodo_ano=?,tipo_mov=?,valor=?,cotacao_dolar=?,notas=? WHERE id=?";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, mov);
                ps.setInt(9, mov.getId());
                ps.executeUpdate();
            } catch (SQLException e) { throw new RuntimeException(e); }
        }
        return mov;
    }

    private void bind(PreparedStatement ps, Movimentacao mov) throws SQLException {
        ps.setInt(1, mov.getInvestimentoId());
        ps.setInt(2, mov.getPeriodoDia());
        ps.setInt(3, mov.getPeriodoMes());
        ps.setInt(4, mov.getPeriodoAno());
        ps.setString(5, mov.getTipoMov().name());
        ps.setDouble(6, mov.getValor());
        if (mov.getCotacaoDolar() != null) ps.setDouble(7, mov.getCotacaoDolar()); else ps.setNull(7, Types.REAL);
        ps.setString(8, mov.getNotas());
    }

    public void deletar(int id) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM movimentacoes WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Movimentacao> findByInvestimento(int investimentoId) {
        String sql = "SELECT * FROM movimentacoes WHERE investimento_id=? ORDER BY periodo_ano, periodo_mes";
        return query(sql, investimentoId);
    }

    public List<Movimentacao> findByInvestimentoEAno(int investimentoId, int ano) {
        String sql = "SELECT * FROM movimentacoes WHERE investimento_id=? AND periodo_ano=? ORDER BY periodo_mes";
        List<Movimentacao> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, ano);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public List<Movimentacao> findByInvestimentoAnoMes(int investimentoId, int ano, int mes) {
        String sql = "SELECT * FROM movimentacoes WHERE investimento_id=? AND periodo_ano=? AND periodo_mes=? ORDER BY id";
        List<Movimentacao> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, ano);
            ps.setInt(3, mes);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public List<Movimentacao> findByInvestimentoAnoMesAte(int investimentoId, int ano, int mes) {
        String sql = "SELECT * FROM movimentacoes WHERE investimento_id=? AND periodo_ano=? AND periodo_mes<=? ORDER BY periodo_mes";
        List<Movimentacao> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ps.setInt(2, ano);
            ps.setInt(3, mes);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    private List<Movimentacao> query(String sql, int investimentoId) {
        List<Movimentacao> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, investimentoId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    private Movimentacao map(ResultSet rs) throws SQLException {
        Movimentacao m = new Movimentacao();
        m.setId(rs.getInt("id"));
        m.setInvestimentoId(rs.getInt("investimento_id"));
        m.setPeriodoDia(rs.getInt("periodo_dia"));
        m.setPeriodoMes(rs.getInt("periodo_mes"));
        m.setPeriodoAno(rs.getInt("periodo_ano"));
        m.setTipoMov(TipoMovimentacao.valueOf(rs.getString("tipo_mov")));
        m.setValor(rs.getDouble("valor"));
        double cot = rs.getDouble("cotacao_dolar");
        m.setCotacaoDolar(rs.wasNull() ? null : cot);
        m.setNotas(rs.getString("notas"));
        m.setCriadoEm(rs.getString("criado_em"));
        return m;
    }
}
