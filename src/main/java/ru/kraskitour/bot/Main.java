package ru.kraskitour.bot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.kraskitour.bot.config.BotConfig;
import ru.kraskitour.bot.db.*;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        BotConfig cfg = BotConfig.fromEnv();

        ensureDbDir(cfg.dbPath);

        Db db = new Db(cfg.dbPath);
        db.init();

        AdminRepository adminRepo = new AdminRepository(db);
        adminRepo.ensureAdmins(cfg.initialAdminIds);

        SessionRepository sessionRepo = new SessionRepository(db);
        RequestRepository requestRepo = new RequestRepository(db);

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(new KraskiTourBot(cfg, sessionRepo, adminRepo, requestRepo));

        System.out.println("Started @" + cfg.username);
    }

    private static void ensureDbDir(String dbPath) {
        File f = new File(dbPath);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
    }
}