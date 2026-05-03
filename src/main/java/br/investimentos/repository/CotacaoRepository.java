package br.investimentos.repository;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.CotacaoDolar;

import java.sql.*;
import java.util.Optional;

public class CotacaoRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public void salvar(CotacaoDolar c) {
        String sql = "INSERT INTO cotacoes_dolar (data,valor_compra,valor_venda,fonte) VALUES (?,?,?,?) " +
                "ON CONFLICT(data) DO UPDATE SET valor_compra=excluded.valor_compra, valor_venda=excluded.valor_venda";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getData());
            ps.setDouble(2, c.getValorCompra());
            ps.setDouble(3, c.getValorVenda());
            ps.setString(4, c.getFonte() != null ? c.getFonte() : "AwesomeAPI");
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Optional<CotacaoDolar> findByData(String data) {
        String sql = "SELECT * FROM cotacoes_dolar WHERE data=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, data);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    public Optional<CotacaoDolar> findUltima() {
        String sql = "SELECT * FROM cotacoes_dolar ORDER BY data DESC LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    private CotacaoDolar map(ResultSet rs) throws SQLException {
        CotacaoDolar c = new CotacaoDolar();
        c.setId(rs.getInt("id"));
        c.setData(rs.getString("data"));
        c.setValorCompra(rs.getDouble("valor_compra"));
        c.setValorVenda(rs.getDouble("valor_venda"));
        c.setFonte(rs.getString("fonte"));
        c.setCriadoEm(rs.getString("criado_em"));
        return c;
    }
}
