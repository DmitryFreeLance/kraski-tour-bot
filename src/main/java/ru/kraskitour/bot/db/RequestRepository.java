package ru.kraskitour.bot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RequestRepository {
    public static class RequestRow {
        public long id;
        public long createdAt;
        public String type;
        public long userId;
        public String payload;
    }

    private final Db db;

    public RequestRepository(Db db) {
        this.db = db;
    }

    public synchronized void add(String type, long userId, String payload) {
        long now = Instant.now().toEpochMilli();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO requests(created_at, type, user_id, payload) VALUES(?,?,?,?)")) {
            ps.setLong(1, now);
            ps.setString(2, type);
            ps.setLong(3, userId);
            ps.setString(4, payload);
            ps.executeUpdate();
        } catch (Exception e) {
            // логировать можно, но не валим бота
        }
    }

    public synchronized List<RequestRow> last(int limit) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, created_at, type, user_id, payload FROM requests ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<RequestRow> out = new ArrayList<>();
                while (rs.next()) {
                    RequestRow r = new RequestRow();
                    r.id = rs.getLong("id");
                    r.createdAt = rs.getLong("created_at");
                    r.type = rs.getString("type");
                    r.userId = rs.getLong("user_id");
                    r.payload = rs.getString("payload");
                    out.add(r);
                }
                return out;
            }
        } catch (Exception e) {
            return List.of();
        }
    }
}