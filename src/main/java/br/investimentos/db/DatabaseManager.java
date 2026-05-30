package br.investimentos.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class DatabaseManager {

    private static DatabaseManager instance;
    private final String url;

    private DatabaseManager() {
        String appHome = System.getProperty("app.home", System.getProperty("user.dir"));
        File dataDir = new File(appHome, "data");
        dataDir.mkdirs();
        File dbFile = new File(dataDir, "investimentos.db");
        url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        initSchema();
        runMigrations();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
        }
        return conn;
    }

    private void runMigrations() {
        String[] cols = {
            "ALTER TABLE gastos ADD COLUMN parcelas_total INTEGER DEFAULT 1",
            "ALTER TABLE gastos ADD COLUMN parcela_numero INTEGER DEFAULT 1",
            "ALTER TABLE gastos ADD COLUMN grupo_parcela TEXT",
            "ALTER TABLE gastos ADD COLUMN fim_mes INTEGER",
            "ALTER TABLE gastos ADD COLUMN fim_ano INTEGER"
        };
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {
            for (String ddl : cols) {
                try { st.execute(ddl); } catch (SQLException ignored) {}
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to run migrations", e);
        }
        migrateGastosPeriodoNullable();
    }

    private void migrateGastosPeriodoNullable() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {
            boolean needsMigration = false;
            try (java.sql.ResultSet rs = st.executeQuery("PRAGMA table_info(gastos)")) {
                while (rs.next()) {
                    if ("periodo_mes".equals(rs.getString("name")) && rs.getInt("notnull") == 1) {
                        needsMigration = true;
                        break;
                    }
                }
            }
            if (!needsMigration) return;

            conn.setAutoCommit(false);
            try {
                st.execute("ALTER TABLE gastos RENAME TO gastos_old");
                st.execute(
                    "CREATE TABLE gastos (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "tipo TEXT NOT NULL, " +
                    "descricao TEXT NOT NULL, " +
                    "periodo_mes INTEGER, " +
                    "periodo_ano INTEGER, " +
                    "fim_mes INTEGER, " +
                    "fim_ano INTEGER, " +
                    "valor REAL NOT NULL, " +
                    "notas TEXT, " +
                    "parcelas_total INTEGER DEFAULT 1, " +
                    "parcela_numero INTEGER DEFAULT 1, " +
                    "grupo_parcela TEXT, " +
                    "criado_em TEXT DEFAULT (datetime('now','localtime'))" +
                    ")"
                );
                st.execute(
                    "INSERT INTO gastos (id,tipo,descricao,periodo_mes,periodo_ano,valor,notas," +
                    "parcelas_total,parcela_numero,grupo_parcela,criado_em) " +
                    "SELECT id,tipo,descricao,periodo_mes,periodo_ano,valor,notas," +
                    "parcelas_total,parcela_numero,grupo_parcela,criado_em FROM gastos_old"
                );
                st.execute("DROP TABLE gastos_old");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Migration failed: gastos periodo nullable", e);
        }
    }

    private void initSchema() {
        try (InputStream is = getClass().getResourceAsStream("/db/schema.sql")) {
            if (is == null) throw new RuntimeException("schema.sql not found");
            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            try (Connection conn = DriverManager.getConnection(url);
                 Statement st = conn.createStatement()) {
                for (String stmt : sql.split(";")) {
                    String trimmed = stmt.strip();
                    if (!trimmed.isEmpty()) st.execute(trimmed);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to init schema", e);
        }
    }
}
