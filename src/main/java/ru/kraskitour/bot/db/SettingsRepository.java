package ru.kraskitour.bot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SettingsRepository {
    private final Db db;

    public SettingsRepository(Db db) {
        this.db = db;
    }

    public synchronized String get(String key) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT value FROM bot_settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString("value");
            }
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void set(String key, String value) {
        if (key == null || key.isBlank() || value == null) return;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO bot_settings(key, value)
                 VALUES(?,?)
                 ON CONFLICT(key) DO UPDATE SET value=excluded.value
             """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }
}
