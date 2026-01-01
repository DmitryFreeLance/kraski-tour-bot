package ru.kraskitour.bot.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.kraskitour.bot.model.UserSession;
import ru.kraskitour.bot.model.UserState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class SessionRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Db db;

    public SessionRepository(Db db) {
        this.db = db;
    }

    public synchronized UserSession getOrCreate(long userId, long chatId) {
        UserSession s = load(userId);
        if (s == null) {
            s = new UserSession(userId, chatId);
            s.state = UserState.NONE;
            s.data = new HashMap<>();
            upsert(s);
            return s;
        }
        // обновим chatId (если вдруг)
        if (s.chatId != chatId) {
            s.chatId = chatId;
            upsert(s);
        }
        return s;
    }

    public synchronized void setState(long userId, long chatId, UserState state, Map<String, Object> data) {
        UserSession s = new UserSession(userId, chatId);
        s.state = state;
        s.data = (data == null) ? new HashMap<>() : data;
        upsert(s);
    }

    public synchronized void clear(long userId, long chatId) {
        setState(userId, chatId, UserState.NONE, new HashMap<>());
    }

    private UserSession load(long userId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id, chat_id, state, data_json FROM user_sessions WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                UserSession s = new UserSession();
                s.userId = rs.getLong("user_id");
                s.chatId = rs.getLong("chat_id");
                s.state = UserState.valueOf(rs.getString("state"));
                String json = rs.getString("data_json");
                if (json == null || json.isBlank()) s.data = new HashMap<>();
                else s.data = MAPPER.readValue(json, MAP_TYPE);
                return s;
            }
        } catch (Exception e) {
            // если чтение сломалось — сбросим
            UserSession s = new UserSession(userId, 0);
            s.state = UserState.NONE;
            s.data = new HashMap<>();
            return s;
        }
    }

    private void upsert(UserSession s) {
        long now = Instant.now().toEpochMilli();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO user_sessions(user_id, chat_id, state, data_json, updated_at)
                 VALUES(?,?,?,?,?)
                 ON CONFLICT(user_id) DO UPDATE SET
                   chat_id=excluded.chat_id,
                   state=excluded.state,
                   data_json=excluded.data_json,
                   updated_at=excluded.updated_at
             """)) {
            ps.setLong(1, s.userId);
            ps.setLong(2, s.chatId);
            ps.setString(3, s.state.name());
            ps.setString(4, MAPPER.writeValueAsString(s.data));
            ps.setLong(5, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert user_session", e);
        }
    }
}