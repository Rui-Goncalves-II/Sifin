package br.investimentos.repository;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.Gasto;
import br.investimentos.model.enums.TipoGasto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GastoRepository {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public Gasto salvar(Gasto g) {
        if (g.getId() == 0) {
            String sql = "INSERT INTO gastos (tipo,descricao,periodo_mes,periodo_ano,valor,notas,parcelas_total,parcela_numero,grupo_parcela) VALUES (?,?,?,?,?,?,?,?,?)";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, g);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) g.setId(rs.getInt(1));
            } catch (SQLException e) { throw new RuntimeException(e); }
        } else {
            String sql = "UPDATE gastos SET tipo=?,descricao=?,periodo_mes=?,periodo_ano=?,valor=?,notas=?,parcelas_total=?,parcela_numero=?,grupo_parcela=? WHERE id=?";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, g);
                ps.setInt(10, g.getId());
                ps.executeUpdate();
            } catch (SQLException e) { throw new RuntimeException(e); }
        }
        return g;
    }

    private void bind(PreparedStatement ps, Gasto g) throws SQLException {
        ps.setString(1, g.getTipo().name());
        ps.setString(2, g.getDescricao());
        ps.setInt(3, g.getPeriodoMes());
        ps.setInt(4, g.getPeriodoAno());
        ps.setDouble(5, g.getValor());
        ps.setString(6, g.getNotas());
        ps.setInt(7, g.getParcelasTotal());
        ps.setInt(8, g.getParcelaNumero());
        ps.setString(9, g.getGrupoParcela());
    }

    public void deletar(int id) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM gastos WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void deletarGrupo(String grupoParcela) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM gastos WHERE grupo_parcela=?")) {
            ps.setString(1, grupoParcela);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Gasto> findAll() {
        return query("SELECT * FROM gastos ORDER BY periodo_ano DESC, periodo_mes DESC");
    }

    public List<Gasto> findByAno(int ano) {
        String sql = "SELECT * FROM gastos WHERE periodo_ano=? ORDER BY periodo_mes, id";
        List<Gasto> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ano);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public List<Gasto> findByTipo(TipoGasto tipo) {
        String sql = "SELECT * FROM gastos WHERE tipo=? ORDER BY periodo_ano DESC, periodo_mes DESC, id DESC";
        List<Gasto> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public List<Gasto> findByTipoEAno(TipoGasto tipo, int ano) {
        String sql = "SELECT * FROM gastos WHERE tipo=? AND periodo_ano=? ORDER BY periodo_mes, id";
        List<Gasto> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo.name());
            ps.setInt(2, ano);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public List<Integer> anosComDados() {
        List<Integer> anos = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT DISTINCT periodo_ano FROM gastos ORDER BY periodo_ano DESC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) anos.add(rs.getInt(1));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return anos;
    }

    private List<Gasto> query(String sql) {
        List<Gasto> list = new ArrayList<>();
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    private Gasto map(ResultSet rs) throws SQLException {
        Gasto g = new Gasto();
        g.setId(rs.getInt("id"));
        g.setTipo(TipoGasto.valueOf(rs.getString("tipo")));
        g.setDescricao(rs.getString("descricao"));
        g.setPeriodoMes(rs.getInt("periodo_mes"));
        g.setPeriodoAno(rs.getInt("periodo_ano"));
        g.setValor(rs.getDouble("valor"));
        g.setNotas(rs.getString("notas"));
        g.setParcelasTotal(rs.getInt("parcelas_total"));
        g.setParcelaNumero(rs.getInt("parcela_numero"));
        g.setGrupoParcela(rs.getString("grupo_parcela"));
        g.setCriadoEm(rs.getString("criado_em"));
        return g;
    }
}
