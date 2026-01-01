package ru.kraskitour.bot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AdminRepository {
    private final Db db;

    public AdminRepository(Db db) {
        this.db = db;
    }

    public synchronized void ensureAdmins(Collection<Long> ids) {
        if (ids == null) return;
        for (Long id : ids) {
            if (id == null) continue;
            addAdmin(id);
        }
    }

    public synchronized boolean isAdmin(long userId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM admins WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void addAdmin(long userId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO admins(user_id) VALUES(?)")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to add admin", e);
        }
    }

    public synchronized void removeAdmin(long userId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM admins WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove admin", e);
        }
    }

    public synchronized List<Long> listAdmins() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT user_id FROM admins ORDER BY user_id");
             ResultSet rs = ps.executeQuery()) {
            List<Long> out = new ArrayList<>();
            while (rs.next()) out.add(rs.getLong(1));
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list admins", e);
        }
    }
}