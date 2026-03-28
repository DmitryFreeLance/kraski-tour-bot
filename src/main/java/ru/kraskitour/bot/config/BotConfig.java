package ru.kraskitour.bot.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class BotConfig {
    public final String token;
    public final String username;
    public final String dbPath;
    public final String managerUrl;
    public final Set<Long> initialAdminIds;
    public final String apiBaseUrl;
    public final int pollingTimeoutSec;
    public final int pollingLimit;
    public final long pollingErrorBackoffMs;

    public BotConfig(String token, String username, String dbPath, String managerUrl, Set<Long> initialAdminIds,
                     String apiBaseUrl, int pollingTimeoutSec, int pollingLimit, long pollingErrorBackoffMs) {
        this.token = token;
        this.username = username;
        this.dbPath = dbPath;
        this.managerUrl = managerUrl;
        this.initialAdminIds = initialAdminIds == null ? Collections.emptySet() : initialAdminIds;
        this.apiBaseUrl = apiBaseUrl;
        this.pollingTimeoutSec = pollingTimeoutSec;
        this.pollingLimit = pollingLimit;
        this.pollingErrorBackoffMs = pollingErrorBackoffMs;
    }

    public static BotConfig fromEnv() {
        String token = requireEnv("BOT_TOKEN");
        String username = env("BOT_USERNAME", "KraskiTourBot");
        String dbPath = env("DB_PATH", "/data/bot.db");
        String managerUrl = env("MANAGER_URL", "https://t.me/alena_kraskitour");
        Set<Long> admins = parseIds(env("ADMIN_IDS", ""));
        String apiBaseUrl = env("MAX_API_BASE", "https://platform-api.max.ru");
        int pollingTimeoutSec = envInt("POLL_TIMEOUT_SEC", 30);
        int pollingLimit = envInt("POLL_LIMIT", 100);
        long pollingErrorBackoffMs = envLong("POLL_ERROR_BACKOFF_MS", 1500);
        return new BotConfig(token, username, dbPath, managerUrl, admins, apiBaseUrl, pollingTimeoutSec, pollingLimit, pollingErrorBackoffMs);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private static String requireEnv(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Environment variable " + key + " is required");
        }
        return v.trim();
    }

    private static int envInt(String key, int def) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long envLong(String key, long def) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Set<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        Set<Long> out = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            try {
                out.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // пропускаем мусор
            }
        }
        return out;
    }
}
