package br.investimentos.repository;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InvestimentoRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public Investimento salvar(Investimento inv) {
        if (inv.getId() == 0) {
            String sql = "INSERT INTO investimentos (nome,tipo,subtipo,indexador,taxa_anual,data_vencimento,moeda,ativo,notas) VALUES (?,?,?,?,?,?,?,?,?)";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, inv);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) inv.setId(rs.getInt(1));
            } catch (SQLException e) { throw new RuntimeException(e); }
        } else {
            String sql = "UPDATE investimentos SET nome=?,tipo=?,subtipo=?,indexador=?,taxa_anual=?,data_vencimento=?,moeda=?,ativo=?,notas=?,atualizado_em=datetime('now','localtime') WHERE id=?";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, inv);
                ps.setInt(10, inv.getId());
                ps.executeUpdate();
            } catch (SQLException e) { throw new RuntimeException(e); }
        }
        return inv;
    }

    private void bind(PreparedStatement ps, Investimento inv) throws SQLException {
        ps.setString(1, inv.getNome());
        ps.setString(2, inv.getTipo().name());
        ps.setString(3, inv.getSubtipo());
        ps.setString(4, inv.getIndexador());
        if (inv.getTaxaAnual() != null) ps.setDouble(5, inv.getTaxaAnual()); else ps.setNull(5, Types.REAL);
        ps.setString(6, inv.getDataVencimento());
        ps.setString(7, inv.getMoeda());
        ps.setInt(8, inv.isAtivo() ? 1 : 0);
        ps.setString(9, inv.getNotas());
    }

    public Optional<Investimento> findById(int id) {
        String sql = "SELECT * FROM investimentos WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    public List<Investimento> findByTipo(TipoInvestimento tipo) {
        String sql = "SELECT * FROM investimentos WHERE tipo=? AND ativo=1 ORDER BY nome";
        List<Investimento> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public List<Investimento> findAll() {
        String sql = "SELECT * FROM investimentos WHERE ativo=1 ORDER BY tipo, nome";
        List<Investimento> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public void arquivar(int id) {
        String sql = "UPDATE investimentos SET ativo=0, atualizado_em=datetime('now','localtime') WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private Investimento map(ResultSet rs) throws SQLException {
        Investimento inv = new Investimento();
        inv.setId(rs.getInt("id"));
        inv.setNome(rs.getString("nome"));
        inv.setTipo(TipoInvestimento.valueOf(rs.getString("tipo")));
        inv.setSubtipo(rs.getString("subtipo"));
        inv.setIndexador(rs.getString("indexador"));
        double taxa = rs.getDouble("taxa_anual");
        inv.setTaxaAnual(rs.wasNull() ? null : taxa);
        inv.setDataVencimento(rs.getString("data_vencimento"));
        inv.setMoeda(rs.getString("moeda"));
        inv.setAtivo(rs.getInt("ativo") == 1);
        inv.setNotas(rs.getString("notas"));
        inv.setCriadoEm(rs.getString("criado_em"));
        inv.setAtualizadoEm(rs.getString("atualizado_em"));
        return inv;
    }
}
