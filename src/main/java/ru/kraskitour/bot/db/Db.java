package ru.kraskitour.bot.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Db {
    private final String jdbcUrl;

    public Db(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public void init() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA foreign_keys=ON;");

            st.execute("""
                CREATE TABLE IF NOT EXISTS user_sessions (
                  user_id    INTEGER PRIMARY KEY,
                  chat_id    INTEGER NOT NULL,
                  state      TEXT    NOT NULL,
                  data_json  TEXT    NOT NULL,
                  updated_at INTEGER NOT NULL
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS admins (
                  user_id INTEGER PRIMARY KEY
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS requests (
                  id         INTEGER PRIMARY KEY AUTOINCREMENT,
                  created_at INTEGER NOT NULL,
                  type       TEXT    NOT NULL,
                  user_id    INTEGER NOT NULL,
                  payload    TEXT    NOT NULL
                );
            """);
        }
    }
}