package ru.kraskitour.bot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ActiveUserRepository {
    public static class ActiveUserRow {
        public long userId;
        public String firstName;
        public String lastName;
        public String username;
        public long lastSeen;
    }

    private final Db db;

    public ActiveUserRepository(Db db) {
        this.db = db;
    }

    public synchronized void upsert(long userId, String firstName, String lastName, String username, long lastSeen) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO active_users(user_id, first_name, last_name, username, last_seen)
                 VALUES(?,?,?,?,?)
                 ON CONFLICT(user_id) DO UPDATE SET
                   first_name=excluded.first_name,
                   last_name=excluded.last_name,
                   username=excluded.username,
                   last_seen=excluded.last_seen
             """)) {
            ps.setLong(1, userId);
            ps.setString(2, firstName);
            ps.setString(3, lastName);
            ps.setString(4, username);
            ps.setLong(5, lastSeen);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public synchronized List<ActiveUserRow> listActive(long sinceMs, int limit) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT user_id, first_name, last_name, username, last_seen
                 FROM active_users
                 WHERE last_seen >= ?
                 ORDER BY last_seen DESC
                 LIMIT ?
             """)) {
            ps.setLong(1, sinceMs);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<ActiveUserRow> out = new ArrayList<>();
                while (rs.next()) {
                    ActiveUserRow r = new ActiveUserRow();
                    r.userId = rs.getLong("user_id");
                    r.firstName = rs.getString("first_name");
                    r.lastName = rs.getString("last_name");
                    r.username = rs.getString("username");
                    r.lastSeen = rs.getLong("last_seen");
                    out.add(r);
                }
                return out;
            }
        } catch (Exception e) {
            return List.of();
        }
    }
}
