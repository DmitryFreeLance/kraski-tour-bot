package ru.kraskitour.bot.model;

import java.util.HashMap;
import java.util.Map;

public class UserSession {
    public long userId;
    public long chatId;
    public UserState state = UserState.NONE;
    public Map<String, Object> data = new HashMap<>();

    public UserSession() {}

    public UserSession(long userId, long chatId) {
        this.userId = userId;
        this.chatId = chatId;
    }
}