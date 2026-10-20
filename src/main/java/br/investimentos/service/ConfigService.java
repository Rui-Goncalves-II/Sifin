package br.investimentos.service;

import br.investimentos.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ConfigService {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public Optional<String> get(String chave) {
        String sql = "SELECT valor FROM configuracoes WHERE chave=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chave);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.ofNullable(rs.getString(1));
        } catch (Exception e) { /* ignora */ }
        return Optional.empty();
    }

    public void set(String chave, String valor) {
        String sql = "INSERT INTO configuracoes (chave,valor) VALUES (?,?) ON CONFLICT(chave) DO UPDATE SET valor=excluded.valor";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chave);
            ps.setString(2, valor);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Map<String, String> findAll() {
        Map<String, String> out = new LinkedHashMap<>();
        String sql = "SELECT chave, valor FROM configuracoes ORDER BY chave";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.put(rs.getString(1), rs.getString(2));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public String getBrapiToken() {
        return get("brapi_token").orElse("");
    }

    public void setBrapiToken(String token) {
        set("brapi_token", token.strip());
    }
}
