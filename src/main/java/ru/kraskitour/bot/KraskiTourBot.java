package ru.kraskitour.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.kraskitour.bot.config.BotConfig;
import ru.kraskitour.bot.db.ActiveUserRepository;
import ru.kraskitour.bot.db.AdminRepository;
import ru.kraskitour.bot.db.RequestRepository;
import ru.kraskitour.bot.db.SessionRepository;
import ru.kraskitour.bot.db.SettingsRepository;
import ru.kraskitour.bot.max.MaxApiClient;
import ru.kraskitour.bot.model.UserSession;
import ru.kraskitour.bot.model.UserState;
import ru.kraskitour.bot.util.Callback;
import ru.kraskitour.bot.util.Keyboards;
import ru.kraskitour.bot.util.PhoneUtil;
import ru.kraskitour.bot.util.Texts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KraskiTourBot {
    private static final Logger log = LoggerFactory.getLogger(KraskiTourBot.class);

    private record Ctx(long userId, long chatId, Long sendChatId) {}

    // keys in session.data
    private static final String K_TOUR_Q1 = "tour_q1";
    private static final String K_TOUR_Q2 = "tour_q2";
    private static final String K_TOUR_Q3 = "tour_q3";
    private static final String K_TOUR_Q4 = "tour_q4";
    private static final String K_PHONE = "phone";

    private static final String K_HOTEL_PHOTO_ATTACHMENT_JSON = "hotel_photo_attachment_json";
    private static final String K_HOTEL_PICK_Q1 = "hotel_pick_q1";
    private static final String K_HOTEL_PICK_Q2 = "hotel_pick_q2";

    private final BotConfig cfg;
    private final MaxApiClient api;
    private final SessionRepository sessions;
    private final AdminRepository admins;
    private final RequestRepository requests;
    private final ActiveUserRepository activeUsers;
    private final SettingsRepository settings;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String managerUrl;

    /**
     * Кэш payload для картинок из ресурсов (images/1.jpg ...).
     * После первой загрузки в MAX можно переиспользовать payload.
     */
    private final Map<String, ObjectNode> resourcePhotoCache = new ConcurrentHashMap<>();

    public KraskiTourBot(BotConfig cfg, MaxApiClient api, SessionRepository sessions, AdminRepository admins, RequestRepository requests,
                         ActiveUserRepository activeUsers, SettingsRepository settings, String managerUrl) {
        this.cfg = cfg;
        this.api = api;
        this.sessions = sessions;
        this.admins = admins;
        this.requests = requests;
        this.activeUsers = activeUsers;
        this.settings = settings;
        this.managerUrl = managerUrl;
    }

    public void handleUpdate(JsonNode update) {
        if (update == null || update.isNull()) return;
        String type = update.path("update_type").asText("");
        try {
            switch (type) {
                case "bot_started" -> handleBotStarted(update);
                case "message_created" -> handleMessage(update.path("message"));
                case "message_callback" -> handleCallback(update);
                default -> {
                    // ignore other updates
                }
            }
        } catch (Exception e) {
            log.error("Update handling error", e);
        }
    }

    private void handleBotStarted(JsonNode update) {
        long userId = pickLong(
                update.path("user").path("user_id"),
                update.path("message").path("sender").path("user_id")
        );
        if (userId <= 0) return;

        updateActiveUser(update.path("user"));

        Long sendChatId = pickLongNullable(
                update.path("chat_id"),
                update.path("message").path("recipient").path("chat_id")
        );
        long chatId = (sendChatId != null) ? sendChatId : userId;

        Ctx ctx = new Ctx(userId, chatId, sendChatId);
        sessions.clear(ctx.userId, ctx.chatId);
        sendStart(ctx);
    }

    private void handleCallback(JsonNode update) {
        JsonNode cb = update.path("callback");
        String payload = cb.path("payload").asText("");
        String callbackId = cb.path("callback_id").asText(null);

        long userId = pickLong(
                cb.path("user").path("user_id"),
                cb.path("message").path("sender").path("user_id"),
                update.path("message").path("sender").path("user_id"),
                update.path("user").path("user_id")
        );
        if (userId <= 0) return;

        updateActiveUser(cb.path("user"));

        Long sendChatId = pickLongNullable(
                cb.path("message").path("recipient").path("chat_id"),
                update.path("message").path("recipient").path("chat_id")
        );
        long chatId = (sendChatId != null) ? sendChatId : userId;
        Ctx ctx = new Ctx(userId, chatId, sendChatId);

        if (callbackId != null && !callbackId.isBlank()) {
            api.answerCallback(callbackId, mapper.createObjectNode());
        }

        // Любой callback может отменить текущий сценарий
        if (Callback.BACK_TO_MENU.equals(payload)) {
            sessions.clear(ctx.userId, ctx.chatId);
            sendStart(ctx);
            return;
        }

        switch (payload) {
            // главное меню
            case Callback.MENU_TOUR -> startTour(ctx);
            case Callback.MENU_SCHENGEN -> showSchengen(ctx);
            case Callback.MENU_HOTEL -> showHotelMenu(ctx);
            case Callback.MENU_SOCIALS -> showSocials(ctx);
            case Callback.MENU_OFFICE -> showOffice(ctx);

            // шенген
            case Callback.SCHENGEN_PRICES -> showSchengenPrices(ctx);
            case Callback.SCHENGEN_BACK -> showSchengen(ctx);

            // отели
            case Callback.HOTEL_COMPARE -> startHotelCompare(ctx);
            case Callback.HOTEL_PICK -> startHotelPick(ctx);

            // соцсети
            case Callback.SOC_TG -> showTgChannel(ctx);
            case Callback.SOC_IG -> showInstagram(ctx);
            case Callback.SOC_BACK -> showSocials(ctx);

            // админка
            case Callback.ADMIN_MENU -> openAdmin(ctx);
            case Callback.ADMIN_LIST -> adminList(ctx);
            case Callback.ADMIN_ADD -> adminAddFlow(ctx);
            case Callback.ADMIN_REMOVE -> adminRemoveFlow(ctx);
            case Callback.ADMIN_REQUESTS -> adminLastRequests(ctx);

            default -> {
                // ignore unknown callbacks
            }
        }
    }

    private void handleMessage(JsonNode msg) {
        if (msg == null || msg.isNull()) return;
        if (msg.path("sender").path("is_bot").asBoolean(false)) return;

        long userId = pickLong(msg.path("sender").path("user_id"));
        if (userId <= 0) return;

        updateActiveUser(msg.path("sender"));

        Long sendChatId = pickLongNullable(msg.path("recipient").path("chat_id"));
        long chatId = (sendChatId != null) ? sendChatId : userId;
        Ctx ctx = new Ctx(userId, chatId, sendChatId);

        UserSession session = sessions.getOrCreate(ctx.userId, ctx.chatId);
        JsonNode body = msg.path("body");

        // если ждём фото, можно обработать сразу
        if (session.state == UserState.HOTEL_COMPARE_WAIT_PHOTO) {
            ObjectNode imageAtt = extractFirstAttachment(body, "image");
            if (imageAtt != null) {
                session.data.put(K_HOTEL_PHOTO_ATTACHMENT_JSON, imageAtt.toString());
                sessions.setState(ctx.userId, ctx.chatId, UserState.HOTEL_COMPARE_WAIT_PHONE, session.data);
                sendHtml(ctx,
                        "Спасибо , Ваша заявка уже в работе.\nНапишите свой номер телефона , мы скоро свяжемся с Вами 📞",
                        Keyboards.cancelToMenuOnly());
                return;
            }
        }

        String text = body.path("text").isTextual() ? body.path("text").asText().trim() : null;
        if (text != null && !text.isBlank()) {
            if ("/start".equalsIgnoreCase(text)) {
                sessions.clear(ctx.userId, ctx.chatId);
                sendStart(ctx);
                return;
            }

            if ("/admin".equalsIgnoreCase(text)) {
                openAdmin(ctx);
                return;
            }

            if (text.startsWith("/add")) {
                handleAddCommand(ctx, text);
                return;
            }

            if (text.startsWith("/link")) {
                handleLinkCommand(ctx, text);
                return;
            }

            // если ждём ввод ID в админке
            if (session.state == UserState.ADMIN_ADD_WAIT_ID) {
                handleAdminAddId(ctx, text);
                return;
            }
            if (session.state == UserState.ADMIN_REMOVE_WAIT_ID) {
                handleAdminRemoveId(ctx, text);
                return;
            }

            // нормальные пользовательские сценарии
            switch (session.state) {
                case TOUR_Q1_COUNTRIES_FROM -> {
                    session.data.put(K_TOUR_Q1, text);
                    sessions.setState(ctx.userId, ctx.chatId, UserState.TOUR_Q2_COMPOSITION, session.data);
                    sendPhotoFromResources(ctx, "images/3.jpg", Texts.TOUR_Q2, null);
                }
                case TOUR_Q2_COMPOSITION -> {
                    session.data.put(K_TOUR_Q2, text);
                    sessions.setState(ctx.userId, ctx.chatId, UserState.TOUR_Q3_DATES_NIGHTS, session.data);
                    sendPhotoFromResources(ctx, "images/4.jpg", Texts.TOUR_Q3, null);
                }
                case TOUR_Q3_DATES_NIGHTS -> {
                    session.data.put(K_TOUR_Q3, text);
                    sessions.setState(ctx.userId, ctx.chatId, UserState.TOUR_Q4_BUDGET_HOTEL, session.data);
                    sendPhotoFromResources(ctx, "images/5.jpg", Texts.TOUR_Q4, null);
                }
                case TOUR_Q4_BUDGET_HOTEL -> {
                    session.data.put(K_TOUR_Q4, text);
                    sessions.setState(ctx.userId, ctx.chatId, UserState.TOUR_PHONE, session.data);
                    sendHtml(ctx, Texts.ASK_PHONE, null);
                }
                case TOUR_PHONE -> handlePhoneInput(ctx, msg.path("sender"), session, text, "Подобрать тур");

                case HOTEL_PICK_Q1_COUNTRY_CITY -> {
                    session.data.put(K_HOTEL_PICK_Q1, text);
                    sessions.setState(ctx.userId, ctx.chatId, UserState.HOTEL_PICK_Q2_DATES_PEOPLE, session.data);
                    sendHtml(ctx, Texts.HOTEL_PICK_Q2, null);
                }
                case HOTEL_PICK_Q2_DATES_PEOPLE -> {
                    session.data.put(K_HOTEL_PICK_Q2, text);
                    sessions.setState(ctx.userId, ctx.chatId, UserState.HOTEL_PICK_WAIT_PHONE, session.data);
                    sendHtml(ctx, Texts.ASK_PHONE, null);
                }
                case HOTEL_PICK_WAIT_PHONE -> handlePhoneInput(ctx, msg.path("sender"), session, text, "Подобрать отель");

                case HOTEL_COMPARE_WAIT_PHONE -> handlePhoneInput(ctx, msg.path("sender"), session, text, "Сравнить цену");

                default -> sendStart(ctx);
            }
            return;
        }

        // Попытка достать телефон из вложения контакта (если вдруг пришло)
        if (session.state == UserState.TOUR_PHONE
                || session.state == UserState.HOTEL_PICK_WAIT_PHONE
                || session.state == UserState.HOTEL_COMPARE_WAIT_PHONE) {
            String phone = extractContactPhone(body);
            if (phone != null) {
                handlePhoneInput(ctx, msg.path("sender"), session, phone, "Контакт");
                return;
            }
        }

        // если пользователь пишет что-то вне сценария — вернем в меню
        sendStart(ctx);
    }

    // ====== Меню / Старт ======

    private void sendStart(Ctx ctx) {
        sendPhotoFromResources(ctx, "images/1.jpg", Texts.START_CAPTION, Keyboards.startMenu(managerUrl));
    }

    private void startTour(Ctx ctx) {
        Map<String, Object> data = new HashMap<>();
        sessions.setState(ctx.userId, ctx.chatId, UserState.TOUR_Q1_COUNTRIES_FROM, data);
        sendPhotoFromResources(ctx, "images/2.jpg", Texts.TOUR_Q1, null);
    }

    private void showSchengen(Ctx ctx) {
        sessions.clear(ctx.userId, ctx.chatId);
        sendHtml(ctx, Texts.SCHENGEN_MAIN, Keyboards.schengenMenu(managerUrl));
    }

    private void showSchengenPrices(Ctx ctx) {
        sessions.clear(ctx.userId, ctx.chatId);
        sendHtml(ctx, Texts.SCHENGEN_PRICES, Keyboards.schengenPricesMenu());
    }

    private void showHotelMenu(Ctx ctx) {
        sessions.clear(ctx.userId, ctx.chatId);
        sendHtml(ctx, Texts.HOTEL_MAIN, Keyboards.hotelMenu());
    }

    private void startHotelCompare(Ctx ctx) {
        Map<String, Object> data = new HashMap<>();
        sessions.setState(ctx.userId, ctx.chatId, UserState.HOTEL_COMPARE_WAIT_PHOTO, data);
        sendHtml(ctx, Texts.HOTEL_COMPARE_ASK_PHOTO, Keyboards.cancelToMenuOnly());
    }

    private void startHotelPick(Ctx ctx) {
        Map<String, Object> data = new HashMap<>();
        sessions.setState(ctx.userId, ctx.chatId, UserState.HOTEL_PICK_Q1_COUNTRY_CITY, data);
        sendHtml(ctx, Texts.HOTEL_PICK_Q1, null);
    }

    private void showSocials(Ctx ctx) {
        sessions.clear(ctx.userId, ctx.chatId);
        sendHtml(ctx, Texts.SOCIALS_MAIN, Keyboards.socialsMenu());
    }

    private void showTgChannel(Ctx ctx) {
        sessions.clear(ctx.userId, ctx.chatId);
        sendHtml(ctx, Texts.TG_CHANNEL, Keyboards.socialsSubMenu());
    }

    private void showInstagram(Ctx ctx) {
        sessions.clear(ctx.userId, ctx.chatId);
        sendHtml(ctx, Texts.INSTAGRAM, Keyboards.socialsSubMenu());
    }

    private void showOffice(Ctx ctx) {
        sessions.clear(ctx.userId, ctx.chatId);
        sendHtml(ctx, Texts.OFFICE, Keyboards.officeMenu());
    }

    // ====== Телефон / финализация заявок ======

    private void handlePhoneInput(Ctx ctx, JsonNode from, UserSession session, String rawPhone, String source) {
        String phone = PhoneUtil.normalize(rawPhone);
        if (phone == null) {
            sendHtml(ctx, "Пожалуйста, укажите корректный номер телефона 📞\nНапример: +79991234567", null);
            return;
        }

        session.data.put(K_PHONE, phone);

        // Определяем какой сценарий завершаем по session.state (или по данным)
        if (session.state == UserState.TOUR_PHONE) {
            finalizeTour(from, ctx.userId, session);
        } else if (session.state == UserState.HOTEL_COMPARE_WAIT_PHONE) {
            finalizeHotelCompare(from, ctx.userId, session);
        } else if (session.state == UserState.HOTEL_PICK_WAIT_PHONE) {
            finalizeHotelPick(from, ctx.userId, session);
        } else {
            // если вдруг пришли сюда не по сценарию
            finalizeGeneric(from, ctx.userId, session, source);
        }

        // ✅ ВАЖНО: больше не перекидываем сразу на /start.
        // Сбросим сценарий и покажем подтверждение + кнопку "вернуться в меню"
        sessions.clear(ctx.userId, ctx.chatId);
        sendHtml(ctx, Texts.PHONE_SAVED, Keyboards.backToMenuOnlyLowercase());
    }

    private void finalizeTour(JsonNode from, long userId, UserSession session) {
        String name = userFullName(from);
        String tag = userTag(from);
        String phone = String.valueOf(session.data.getOrDefault(K_PHONE, ""));

        String q1 = String.valueOf(session.data.getOrDefault(K_TOUR_Q1, ""));
        String q2 = String.valueOf(session.data.getOrDefault(K_TOUR_Q2, ""));
        String q3 = String.valueOf(session.data.getOrDefault(K_TOUR_Q3, ""));
        String q4 = String.valueOf(session.data.getOrDefault(K_TOUR_Q4, ""));

        String adminText =
                "🆕 Заявка: ПОДОБРАТЬ ТУР\n" +
                        "👤 Имя: " + name + "\n" +
                        "🔗 Тег: " + tag + "\n" +
                        "📞 Телефон: " + phone + "\n\n" +
                        "🌍 Страны/города вылета: " + q1 + "\n" +
                        "👨‍👩‍👧‍👦 Состав: " + q2 + "\n" +
                        "📅 Даты/ночи: " + q3 + "\n" +
                        "💰 Пожелания: " + q4;

        notifyAdminsText(adminText);
        requests.add("TOUR", userId, adminText);
    }

    private void finalizeHotelCompare(JsonNode from, long userId, UserSession session) {
        String name = userFullName(from);
        String tag = userTag(from);
        String phone = String.valueOf(session.data.getOrDefault(K_PHONE, ""));
        String attJson = String.valueOf(session.data.getOrDefault(K_HOTEL_PHOTO_ATTACHMENT_JSON, ""));

        String caption =
                "🆕 Заявка: СРАВНИТЬ ЦЕНУ (отель)\n" +
                        "👤 Имя: " + name + "\n" +
                        "🔗 Тег: " + tag + "\n" +
                        "📞 Телефон: " + phone;

        ObjectNode attachment = null;
        if (attJson != null && !attJson.isBlank()) {
            try {
                JsonNode node = mapper.readTree(attJson);
                if (node.isObject()) attachment = (ObjectNode) node;
            } catch (Exception ignored) {
            }
        }

        for (Long adminId : admins.listAdmins()) {
            try {
                sendHtmlToUser(adminId, caption, attachment);
            } catch (Exception e) {
                log.warn("Failed to send compare photo to admin {}", adminId, e);
            }
        }

        requests.add("HOTEL_COMPARE", userId, caption + "\n[attachment=" + attJson + "]");
    }

    private void finalizeHotelPick(JsonNode from, long userId, UserSession session) {
        String name = userFullName(from);
        String tag = userTag(from);
        String phone = String.valueOf(session.data.getOrDefault(K_PHONE, ""));

        String q1 = String.valueOf(session.data.getOrDefault(K_HOTEL_PICK_Q1, ""));
        String q2 = String.valueOf(session.data.getOrDefault(K_HOTEL_PICK_Q2, ""));

        String adminText =
                "🆕 Заявка: ПОДОБРАТЬ ОТЕЛЬ (без тура)\n" +
                        "👤 Имя: " + name + "\n" +
                        "🔗 Тег: " + tag + "\n" +
                        "📞 Телефон: " + phone + "\n\n" +
                        "🌍 Страна/город: " + q1 + "\n" +
                        "📅 Даты/люди: " + q2;

        notifyAdminsText(adminText);
        requests.add("HOTEL_PICK", userId, adminText);
    }

    private void finalizeGeneric(JsonNode from, long userId, UserSession session, String source) {
        String name = userFullName(from);
        String tag = userTag(from);
        String phone = String.valueOf(session.data.getOrDefault(K_PHONE, ""));

        String adminText =
                "🆕 Заявка: " + source + "\n" +
                        "👤 Имя: " + name + "\n" +
                        "🔗 Тег: " + tag + "\n" +
                        "📞 Телефон: " + phone + "\n\n" +
                        "Данные: " + session.data;

        notifyAdminsText(adminText);
        requests.add("GENERIC", userId, adminText);
    }

    private void notifyAdminsText(String text) {
        for (Long adminId : admins.listAdmins()) {
            try {
                sendHtmlToUser(adminId, text, null);
            } catch (Exception e) {
                log.warn("Failed to notify admin {}", adminId, e);
            }
        }
    }

    // ====== Админ панель ======

    private void openAdmin(Ctx ctx) {
        if (!admins.isAdmin(ctx.userId)) {
            sendHtml(ctx, "⛔ Нет доступа.", null);
            return;
        }
        sendHtml(ctx, "🔐 <b>Админ панель</b>", Keyboards.adminMenu());
    }

    private void adminList(Ctx ctx) {
        if (!admins.isAdmin(ctx.userId)) {
            sendHtml(ctx, "⛔ Нет доступа.", null);
            return;
        }
        var list = admins.listAdmins();
        StringBuilder sb = new StringBuilder("👥 <b>Админы:</b>\n");
        if (list.isEmpty()) sb.append("— пусто");
        else {
            for (Long id : list) sb.append("• ").append(id).append("\n");
        }
        sendHtml(ctx, sb.toString(), Keyboards.adminMenu());
    }

    private void adminAddFlow(Ctx ctx) {
        if (!admins.isAdmin(ctx.userId)) {
            sendHtml(ctx, "⛔ Нет доступа.", null);
            return;
        }
        UserSession s = sessions.getOrCreate(ctx.userId, ctx.chatId);
        s.data = new HashMap<>();
        sessions.setState(ctx.userId, ctx.chatId, UserState.ADMIN_ADD_WAIT_ID, s.data);
        sendHtml(ctx, "➕ Отправьте <b>user_id</b> нового админа (число).", null);
    }

    private void adminRemoveFlow(Ctx ctx) {
        if (!admins.isAdmin(ctx.userId)) {
            sendHtml(ctx, "⛔ Нет доступа.", null);
            return;
        }
        UserSession s = sessions.getOrCreate(ctx.userId, ctx.chatId);
        s.data = new HashMap<>();
        sessions.setState(ctx.userId, ctx.chatId, UserState.ADMIN_REMOVE_WAIT_ID, s.data);
        sendHtml(ctx, "➖ Отправьте <b>user_id</b> админа, которого нужно удалить (число).", null);
    }

    private void handleAdminAddId(Ctx ctx, String text) {
        if (!admins.isAdmin(ctx.userId)) {
            sessions.clear(ctx.userId, ctx.chatId);
            sendHtml(ctx, "⛔ Нет доступа.", null);
            return;
        }
        try {
            long id = Long.parseLong(text.trim());
            admins.addAdmin(id);
            sessions.clear(ctx.userId, ctx.chatId);
            sendHtml(ctx, "✅ Админ добавлен: <b>" + id + "</b>", Keyboards.adminMenu());
        } catch (NumberFormatException e) {
            sendHtml(ctx, "Введите число (user_id).", null);
        }
    }

    private void handleAdminRemoveId(Ctx ctx, String text) {
        if (!admins.isAdmin(ctx.userId)) {
            sessions.clear(ctx.userId, ctx.chatId);
            sendHtml(ctx, "⛔ Нет доступа.", null);
            return;
        }
        try {
            long id = Long.parseLong(text.trim());
            admins.removeAdmin(id);
            sessions.clear(ctx.userId, ctx.chatId);
            sendHtml(ctx, "✅ Админ удалён: <b>" + id + "</b>", Keyboards.adminMenu());
        } catch (NumberFormatException e) {
            sendHtml(ctx, "Введите число (user_id).", null);
        }
    }

    private void adminLastRequests(Ctx ctx) {
        if (!admins.isAdmin(ctx.userId)) {
            sendHtml(ctx, "⛔ Нет доступа.", null);
            return;
        }

        var last = requests.last(10);
        if (last.isEmpty()) {
            sendHtml(ctx, "🗂 Заявок пока нет.", Keyboards.adminMenu());
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

        StringBuilder sb = new StringBuilder("🗂 <b>Последние заявки (UTC):</b>\n\n");
        for (var r : last) {
            sb.append("#").append(r.id)
                    .append(" • ").append(r.type)
                    .append(" • user_id=").append(r.userId)
                    .append(" • ").append(fmt.format(Instant.ofEpochMilli(r.createdAt)))
                    .append("\n");
            // payload может быть длинный — чуть обрежем
            String p = r.payload == null ? "" : r.payload;
            if (p.length() > 500) p = p.substring(0, 500) + "...";
            sb.append(p).append("\n\n");
        }

        sendHtml(ctx, sb.toString(), Keyboards.adminMenu());
    }

    // ====== /add helpers ======

    private void handleAddCommand(Ctx ctx, String text) {
        if (!admins.isAdmin(ctx.userId)) {
            sendHtml(ctx, "⛔ Нет доступа.", null);
            return;
        }

        String[] parts = text.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                long id = Long.parseLong(parts[1]);
                admins.addAdmin(id);
                sendHtml(ctx, "✅ Админ добавлен: <b>" + id + "</b>", Keyboards.adminMenu());
            } catch (NumberFormatException e) {
                sendHtml(ctx, "Введите корректный <b>user_id</b>. Пример: <code>/add 123456</code>", null);
            }
            return;
        }

        // выводим список активных пользователей
        long now = System.currentTimeMillis();
        long sinceMs = now - 30L * 24 * 60 * 60 * 1000;
        var list = activeUsers.listActive(sinceMs, 200);
        if (list.isEmpty()) {
            sendHtml(ctx, "Пока нет активных пользователей за последние 30 дней.", null);
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
        StringBuilder sb = new StringBuilder("👥 <b>Активные пользователи (UTC, 30 дней):</b>\n");
        for (var u : list) {
            String name = buildName(u.firstName, u.lastName);
            String uname = (u.username == null || u.username.isBlank()) ? "" : " @" + u.username.trim();
            sb.append("• ").append(name).append(uname)
                    .append(" — <code>").append(u.userId).append("</code>")
                    .append(" (").append(fmt.format(Instant.ofEpochMilli(u.lastSeen))).append(")\n");
        }
        sb.append("\nЧтобы назначить админа: <code>/add USER_ID</code>");
        sendHtml(ctx, sb.toString(), null);
    }

    private void handleLinkCommand(Ctx ctx, String text) {
        if (!admins.isAdmin(ctx.userId)) {
            sendHtml(ctx, "⛔ Нет доступа.", null);
            return;
        }

        String[] parts = text.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                long id = Long.parseLong(parts[1]);
                String url = "max://user/" + id;
                managerUrl = url;
                settings.set("manager_url", url);
                sendHtml(ctx, "✅ Ссылка обновлена: <code>" + url + "</code>", Keyboards.adminMenu());
            } catch (NumberFormatException e) {
                sendHtml(ctx, "Введите корректный <b>user_id</b>. Пример: <code>/link 123456</code>", null);
            }
            return;
        }

        String url = (managerUrl == null || managerUrl.isBlank()) ? "(не задана)" : managerUrl;
        sendHtml(ctx, "Текущая ссылка менеджера: <code>" + url + "</code>\n" +
                "Чтобы обновить: <code>/link USER_ID</code>", null);
    }

    // ====== Helpers ======

    private void sendHtml(Ctx ctx, String text, ObjectNode keyboard) {
        ArrayNode attachments = null;
        if (keyboard != null) {
            attachments = mapper.createArrayNode();
            attachments.add(keyboard);
        }
        sendHtmlWithAttachments(ctx, text, attachments);
    }

    private void sendHtmlWithAttachments(Ctx ctx, String text, ArrayNode attachments) {
        ObjectNode body = buildMessageBody(text, attachments);
        api.sendMessage(ctx.sendChatId, ctx.userId, body);
    }

    private void sendHtmlToUser(long userId, String text, ObjectNode attachment) {
        ArrayNode attachments = null;
        if (attachment != null) {
            attachments = mapper.createArrayNode();
            attachments.add(attachment);
        }
        ObjectNode body = buildMessageBody(text, attachments);
        api.sendMessage(null, userId, body);
    }

    private ObjectNode buildMessageBody(String text, ArrayNode attachments) {
        ObjectNode body = mapper.createObjectNode();
        if (text != null) body.put("text", text);
        body.put("format", "html");
        if (attachments != null && attachments.size() > 0) {
            body.set("attachments", attachments);
        }
        return body;
    }

    private void sendPhotoFromResources(Ctx ctx, String resourcePath, String caption, ObjectNode keyboard) {
        try {
            ObjectNode imageAttachment = getImageAttachment(resourcePath);
            if (imageAttachment == null) {
                sendHtml(ctx, caption + "\n\n(⚠️ Не найден ресурс: " + resourcePath + ")", keyboard);
                return;
            }

            ArrayNode attachments = mapper.createArrayNode();
            attachments.add(imageAttachment);
            if (keyboard != null) attachments.add(keyboard);

            sendHtmlWithAttachments(ctx, caption, attachments);
        } catch (Exception e) {
            log.warn("Failed to send image {}", resourcePath, e);
            sendHtml(ctx, caption, keyboard);
        }
    }

    private ObjectNode getImageAttachment(String resourcePath) throws IOException, InterruptedException {
        ObjectNode cached = resourcePhotoCache.get(resourcePath);
        if (cached != null) return cached;

        byte[] bytes = readResourceBytes(resourcePath);
        if (bytes == null) return null;

        String fileName = resourcePath.contains("/")
                ? resourcePath.substring(resourcePath.lastIndexOf('/') + 1)
                : "image.jpg";
        String contentType = guessContentType(fileName);

        ObjectNode payload = api.uploadImage(bytes, fileName, contentType);
        ObjectNode attachment = mapper.createObjectNode();
        attachment.put("type", "image");
        attachment.set("payload", payload);

        resourcePhotoCache.put(resourcePath, attachment);
        return attachment;
    }

    private static byte[] readResourceBytes(String resourcePath) throws IOException {
        try (InputStream is = KraskiTourBot.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            is.transferTo(out);
            return out.toByteArray();
        }
    }

    private static String guessContentType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private static ObjectNode extractFirstAttachment(JsonNode body, String type) {
        if (body == null || body.isNull()) return null;
        JsonNode atts = body.path("attachments");
        if (!atts.isArray()) return null;
        for (JsonNode att : atts) {
            if (type.equals(att.path("type").asText("")) && att.isObject()) {
                return (ObjectNode) att;
            }
        }
        return null;
    }

    private static String extractContactPhone(JsonNode body) {
        if (body == null || body.isNull()) return null;
        JsonNode atts = body.path("attachments");
        if (!atts.isArray()) return null;
        for (JsonNode att : atts) {
            String type = att.path("type").asText("");
            if (!"contact".equals(type) && !"request_contact".equals(type)) continue;
            JsonNode payload = att.path("payload");
            String phone = firstText(payload.path("phone"), payload.path("phone_number"), payload.path("number"));
            if (phone != null) return phone;
        }
        return null;
    }

    private static String userFullName(JsonNode u) {
        if (u == null || u.isNull()) return "(unknown)";
        String fn = firstText(u.path("first_name"), u.path("name"));
        String ln = firstText(u.path("last_name"));
        String full = (nullToEmpty(fn) + " " + nullToEmpty(ln)).trim();
        return full.isBlank() ? "(no name)" : full;
    }

    private static String userTag(JsonNode u) {
        if (u == null || u.isNull()) return "(нет)";
        String un = firstText(u.path("username"));
        if (un == null || un.isBlank()) return "(нет)";
        return "@" + un.trim();
    }

    private static long pickLong(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && n.isNumber()) return n.asLong();
            if (n != null && n.isTextual()) {
                try {
                    return Long.parseLong(n.asText());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    private static Long pickLongNullable(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && n.isNumber()) return n.asLong();
            if (n != null && n.isTextual()) {
                try {
                    return Long.parseLong(n.asText());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private static String firstText(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && n.isTextual()) {
                String t = n.asText();
                if (!t.isBlank()) return t;
            }
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void updateActiveUser(JsonNode user) {
        if (user == null || user.isNull()) return;
        long userId = pickLong(user.path("user_id"));
        if (userId <= 0) return;
        String firstName = firstText(user.path("first_name"), user.path("name"));
        String lastName = firstText(user.path("last_name"));
        String username = firstText(user.path("username"));
        activeUsers.upsert(userId, firstName, lastName, username, System.currentTimeMillis());
    }

    private static String buildName(String first, String last) {
        String full = (nullToEmpty(first) + " " + nullToEmpty(last)).trim();
        return full.isBlank() ? "(no name)" : full;
    }
}
