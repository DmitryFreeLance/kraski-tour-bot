package ru.kraskitour.bot;

import ru.kraskitour.bot.config.BotConfig;
import ru.kraskitour.bot.db.ActiveUserRepository;
import ru.kraskitour.bot.db.AdminRepository;
import ru.kraskitour.bot.db.Db;
import ru.kraskitour.bot.db.RequestRepository;
import ru.kraskitour.bot.db.SessionRepository;
import ru.kraskitour.bot.max.MaxApiClient;
import ru.kraskitour.bot.max.MaxLongPoller;

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
        ActiveUserRepository activeUserRepo = new ActiveUserRepository(db);

        MaxApiClient api = new MaxApiClient(cfg.token, cfg.apiBaseUrl);
        KraskiTourBot bot = new KraskiTourBot(cfg, api, sessionRepo, adminRepo, requestRepo, activeUserRepo);

        System.out.println("Started MAX bot @" + cfg.username);

        MaxLongPoller poller = new MaxLongPoller(api, bot, cfg.pollingTimeoutSec, cfg.pollingLimit, cfg.pollingErrorBackoffMs);
        poller.runForever();
    }

    private static void ensureDbDir(String dbPath) {
        File f = new File(dbPath);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
    }
}
