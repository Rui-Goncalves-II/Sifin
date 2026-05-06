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
            "ALTER TABLE gastos ADD COLUMN grupo_parcela TEXT"
        };
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {
            for (String ddl : cols) {
                try { st.execute(ddl); } catch (SQLException ignored) {}
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to run migrations", e);
        }
    }

    private void initSchema() {
        try (InputStream is = getClass().getResourceAsStream("/db/schema.sql")) {
            if (is == null) throw new RuntimeException("schema.sql not found");
            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            try (Connection conn = DriverManager.getConnection(url);
                 Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA journal_mode = WAL");
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
