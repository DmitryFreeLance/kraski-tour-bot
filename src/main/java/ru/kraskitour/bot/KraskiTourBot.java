package ru.kraskitour.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.kraskitour.bot.config.BotConfig;
import ru.kraskitour.bot.db.AdminRepository;
import ru.kraskitour.bot.db.RequestRepository;
import ru.kraskitour.bot.db.SessionRepository;
import ru.kraskitour.bot.model.UserSession;
import ru.kraskitour.bot.model.UserState;
import ru.kraskitour.bot.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KraskiTourBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(KraskiTourBot.class);

    // keys in session.data
    private static final String K_TOUR_Q1 = "tour_q1";
    private static final String K_TOUR_Q2 = "tour_q2";
    private static final String K_TOUR_Q3 = "tour_q3";
    private static final String K_TOUR_Q4 = "tour_q4";
    private static final String K_PHONE = "phone";

    private static final String K_HOTEL_PHOTO_FILE_ID = "hotel_photo_file_id";
    private static final String K_HOTEL_PICK_Q1 = "hotel_pick_q1";
    private static final String K_HOTEL_PICK_Q2 = "hotel_pick_q2";

    private final BotConfig cfg;
    private final SessionRepository sessions;
    private final AdminRepository admins;
    private final RequestRepository requests;

    /**
     * Кэш file_id для картинок из ресурсов (images/1.jpg ...).
     * После первой отправки Telegram вернет file_id, и дальше фото будет уходить без повторной загрузки.
     */
    private final Map<String, String> resourcePhotoFileIdCache = new ConcurrentHashMap<>();

    public KraskiTourBot(BotConfig cfg, SessionRepository sessions, AdminRepository admins, RequestRepository requests) {
        this.cfg = cfg;
        this.sessions = sessions;
        this.admins = admins;
        this.requests = requests;
    }

    @Override
    public String getBotUsername() {
        return cfg.username;
    }

    @Override
    public String getBotToken() {
        return cfg.token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
                return;
            }
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Update handling error", e);
        }
    }

    private void handleCallback(CallbackQuery q) throws TelegramApiException {
        answerCb(q.getId());

        long chatId = q.getMessage().getChatId();
        long userId = q.getFrom().getId();
        String data = q.getData();

        // Любой callback может отменить текущий сценарий
        if (Callback.BACK_TO_MENU.equals(data)) {
            sessions.clear(userId, chatId);
            sendStart(chatId);
            return;
        }

        switch (data) {
            // главное меню
            case Callback.MENU_TOUR -> startTour(chatId, userId);
            case Callback.MENU_SCHENGEN -> showSchengen(chatId, userId);
            case Callback.MENU_HOTEL -> showHotelMenu(chatId, userId);
            case Callback.MENU_SOCIALS -> showSocials(chatId, userId);
            case Callback.MENU_OFFICE -> showOffice(chatId, userId);

            // шенген
            case Callback.SCHENGEN_PRICES -> showSchengenPrices(chatId, userId);
            case Callback.SCHENGEN_BACK -> showSchengen(chatId, userId);

            // отели
            case Callback.HOTEL_COMPARE -> startHotelCompare(chatId, userId);
            case Callback.HOTEL_PICK -> startHotelPick(chatId, userId);

            // соцсети
            case Callback.SOC_TG -> showTgChannel(chatId, userId);
            case Callback.SOC_IG -> showInstagram(chatId, userId);
            case Callback.SOC_BACK -> showSocials(chatId, userId);

            // админка
            case Callback.ADMIN_MENU -> openAdmin(chatId, userId);
            case Callback.ADMIN_LIST -> adminList(chatId, userId);
            case Callback.ADMIN_ADD -> adminAddFlow(chatId, userId);
            case Callback.ADMIN_REMOVE -> adminRemoveFlow(chatId, userId);
            case Callback.ADMIN_REQUESTS -> adminLastRequests(chatId, userId);

            default -> {
                // ignore unknown callbacks
            }
        }
    }

    private void handleMessage(Message msg) throws TelegramApiException {
        long chatId = msg.getChatId();
        long userId = msg.getFrom().getId();

        UserSession session = sessions.getOrCreate(userId, chatId);

        if (msg.hasText()) {
            String text = msg.getText().trim();

            if ("/start".equalsIgnoreCase(text)) {
                sessions.clear(userId, chatId);
                sendStart(chatId);
                return;
            }

            if ("/admin".equalsIgnoreCase(text)) {
                openAdmin(chatId, userId);
                return;
            }

            // если ждём ввод ID в админке
            if (session.state == UserState.ADMIN_ADD_WAIT_ID) {
                handleAdminAddId(chatId, userId, session, text);
                return;
            }
            if (session.state == UserState.ADMIN_REMOVE_WAIT_ID) {
                handleAdminRemoveId(chatId, userId, session, text);
                return;
            }

            // нормальные пользовательские сценарии
            switch (session.state) {
                case TOUR_Q1_COUNTRIES_FROM -> {
                    session.data.put(K_TOUR_Q1, text);
                    sessions.setState(userId, chatId, UserState.TOUR_Q2_COMPOSITION, session.data);
                    sendPhotoFromResources(chatId, "images/3.jpg", Texts.TOUR_Q2, null);
                }
                case TOUR_Q2_COMPOSITION -> {
                    session.data.put(K_TOUR_Q2, text);
                    sessions.setState(userId, chatId, UserState.TOUR_Q3_DATES_NIGHTS, session.data);
                    sendPhotoFromResources(chatId, "images/4.jpg", Texts.TOUR_Q3, null);
                }
                case TOUR_Q3_DATES_NIGHTS -> {
                    session.data.put(K_TOUR_Q3, text);
                    sessions.setState(userId, chatId, UserState.TOUR_Q4_BUDGET_HOTEL, session.data);
                    sendPhotoFromResources(chatId, "images/5.jpg", Texts.TOUR_Q4, null);
                }
                case TOUR_Q4_BUDGET_HOTEL -> {
                    session.data.put(K_TOUR_Q4, text);
                    sessions.setState(userId, chatId, UserState.TOUR_PHONE, session.data);
                    sendHtml(chatId, Texts.ASK_PHONE, null);
                }
                case TOUR_PHONE -> {
                    handlePhoneInput(msg.getFrom(), chatId, userId, session, text, "Подобрать тур");
                }

                case HOTEL_PICK_Q1_COUNTRY_CITY -> {
                    session.data.put(K_HOTEL_PICK_Q1, text);
                    sessions.setState(userId, chatId, UserState.HOTEL_PICK_Q2_DATES_PEOPLE, session.data);
                    sendHtml(chatId, Texts.HOTEL_PICK_Q2, null);
                }
                case HOTEL_PICK_Q2_DATES_PEOPLE -> {
                    session.data.put(K_HOTEL_PICK_Q2, text);
                    sessions.setState(userId, chatId, UserState.HOTEL_PICK_WAIT_PHONE, session.data);
                    sendHtml(chatId, Texts.ASK_PHONE, null);
                }
                case HOTEL_PICK_WAIT_PHONE -> {
                    handlePhoneInput(msg.getFrom(), chatId, userId, session, text, "Подобрать отель");
                }

                case HOTEL_COMPARE_WAIT_PHONE -> {
                    handlePhoneInput(msg.getFrom(), chatId, userId, session, text, "Сравнить цену");
                }

                default -> {
                    // если пользователь пишет что-то вне сценария — вернем в меню
                    sendStart(chatId);
                }
            }
            return;
        }

        if (msg.hasContact()) {
            Contact c = msg.getContact();
            String phone = c.getPhoneNumber();
            handlePhoneInput(msg.getFrom(), chatId, userId, session, phone, "Контакт");
            return;
        }

        if (msg.hasPhoto()) {
            if (session.state == UserState.HOTEL_COMPARE_WAIT_PHOTO) {
                List<PhotoSize> photos = msg.getPhoto();
                PhotoSize best = photos.get(photos.size() - 1); // обычно последняя — самая большая
                session.data.put(K_HOTEL_PHOTO_FILE_ID, best.getFileId());
                sessions.setState(userId, chatId, UserState.HOTEL_COMPARE_WAIT_PHONE, session.data);

                sendHtml(chatId,
                        "Спасибо , Ваша заявка уже в работе.\nНапишите свой номер телефона , мы скоро свяжемся с Вами 📞",
                        Keyboards.cancelToMenuOnly());
            } else {
                // фото не ждали
                sendStart(chatId);
            }
        }
    }

    // ====== Меню / Старт ======

    private void sendStart(long chatId) throws TelegramApiException {
        sendPhotoFromResources(chatId, "images/1.jpg", Texts.START_CAPTION, Keyboards.startMenu(cfg.managerUrl));
    }

    private void startTour(long chatId, long userId) throws TelegramApiException {
        Map<String, Object> data = new HashMap<>();
        sessions.setState(userId, chatId, UserState.TOUR_Q1_COUNTRIES_FROM, data);
        sendPhotoFromResources(chatId, "images/2.jpg", Texts.TOUR_Q1, null);
    }

    private void showSchengen(long chatId, long userId) throws TelegramApiException {
        sessions.clear(userId, chatId);
        sendHtml(chatId, Texts.SCHENGEN_MAIN, Keyboards.schengenMenu(cfg.managerUrl));
    }

    private void showSchengenPrices(long chatId, long userId) throws TelegramApiException {
        sessions.clear(userId, chatId);
        sendHtml(chatId, Texts.SCHENGEN_PRICES, Keyboards.schengenPricesMenu());
    }

    private void showHotelMenu(long chatId, long userId) throws TelegramApiException {
        sessions.clear(userId, chatId);
        sendHtml(chatId, Texts.HOTEL_MAIN, Keyboards.hotelMenu());
    }

    private void startHotelCompare(long chatId, long userId) throws TelegramApiException {
        Map<String, Object> data = new HashMap<>();
        sessions.setState(userId, chatId, UserState.HOTEL_COMPARE_WAIT_PHOTO, data);
        sendHtml(chatId, Texts.HOTEL_COMPARE_ASK_PHOTO, Keyboards.cancelToMenuOnly());
    }

    private void startHotelPick(long chatId, long userId) throws TelegramApiException {
        Map<String, Object> data = new HashMap<>();
        sessions.setState(userId, chatId, UserState.HOTEL_PICK_Q1_COUNTRY_CITY, data);
        sendHtml(chatId, Texts.HOTEL_PICK_Q1, null);
    }

    private void showSocials(long chatId, long userId) throws TelegramApiException {
        sessions.clear(userId, chatId);
        sendHtml(chatId, Texts.SOCIALS_MAIN, Keyboards.socialsMenu());
    }

    private void showTgChannel(long chatId, long userId) throws TelegramApiException {
        sessions.clear(userId, chatId);
        sendHtml(chatId, Texts.TG_CHANNEL, Keyboards.socialsSubMenu());
    }

    private void showInstagram(long chatId, long userId) throws TelegramApiException {
        sessions.clear(userId, chatId);
        sendHtml(chatId, Texts.INSTAGRAM, Keyboards.socialsSubMenu());
    }

    private void showOffice(long chatId, long userId) throws TelegramApiException {
        sessions.clear(userId, chatId);
        sendHtml(chatId, Texts.OFFICE, Keyboards.officeMenu());
    }

    // ====== Телефон / финализация заявок ======

    private void handlePhoneInput(User from, long chatId, long userId, UserSession session, String rawPhone, String source) throws TelegramApiException {
        String phone = PhoneUtil.normalize(rawPhone);
        if (phone == null) {
            sendHtml(chatId, "Пожалуйста, укажите корректный номер телефона 📞\nНапример: +79991234567", null);
            return;
        }

        session.data.put(K_PHONE, phone);

        // Определяем какой сценарий завершаем по session.state (или по данным)
        if (session.state == UserState.TOUR_PHONE) {
            finalizeTour(from, userId, session);
        } else if (session.state == UserState.HOTEL_COMPARE_WAIT_PHONE) {
            finalizeHotelCompare(from, userId, session);
        } else if (session.state == UserState.HOTEL_PICK_WAIT_PHONE) {
            finalizeHotelPick(from, userId, session);
        } else {
            // если вдруг пришли сюда не по сценарию
            finalizeGeneric(from, userId, session, source);
        }

        // сброс и в меню
        sessions.clear(userId, chatId);
        sendStart(chatId);
    }

    private void finalizeTour(User from, long userId, UserSession session) {
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

    private void finalizeHotelCompare(User from, long userId, UserSession session) {
        String name = userFullName(from);
        String tag = userTag(from);
        String phone = String.valueOf(session.data.getOrDefault(K_PHONE, ""));
        String fileId = String.valueOf(session.data.getOrDefault(K_HOTEL_PHOTO_FILE_ID, ""));

        String caption =
                "🆕 Заявка: СРАВНИТЬ ЦЕНУ (отель)\n" +
                        "👤 Имя: " + name + "\n" +
                        "🔗 Тег: " + tag + "\n" +
                        "📞 Телефон: " + phone;

        // отправляем админам фото + подпись
        for (Long adminId : admins.listAdmins()) {
            try {
                SendPhoto sp = new SendPhoto();
                sp.setChatId(String.valueOf(adminId));
                sp.setPhoto(new InputFile(fileId)); // переиспользуем file_id от пользователя
                sp.setCaption(caption);
                // без ParseMode, чтобы не ломалось от символов
                execute(sp);
            } catch (Exception e) {
                log.warn("Failed to send compare photo to admin {}", adminId, e);
            }
        }

        requests.add("HOTEL_COMPARE", userId, caption + "\n[fileId=" + fileId + "]");
    }

    private void finalizeHotelPick(User from, long userId, UserSession session) {
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

    private void finalizeGeneric(User from, long userId, UserSession session, String source) {
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
                SendMessage sm = new SendMessage();
                sm.setChatId(String.valueOf(adminId));
                sm.setText(text);
                execute(sm);
            } catch (Exception e) {
                log.warn("Failed to notify admin {}", adminId, e);
            }
        }
    }

    // ====== Админ панель ======

    private void openAdmin(long chatId, long userId) throws TelegramApiException {
        if (!admins.isAdmin(userId)) {
            sendHtml(chatId, "⛔ Нет доступа.", null);
            return;
        }
        sendHtml(chatId, "🔐 <b>Админ панель</b>", Keyboards.adminMenu());
    }

    private void adminList(long chatId, long userId) throws TelegramApiException {
        if (!admins.isAdmin(userId)) {
            sendHtml(chatId, "⛔ Нет доступа.", null);
            return;
        }
        List<Long> list = admins.listAdmins();
        StringBuilder sb = new StringBuilder("👥 <b>Админы:</b>\n");
        if (list.isEmpty()) sb.append("— пусто");
        else {
            for (Long id : list) sb.append("• ").append(id).append("\n");
        }
        sendHtml(chatId, sb.toString(), Keyboards.adminMenu());
    }

    private void adminAddFlow(long chatId, long userId) throws TelegramApiException {
        if (!admins.isAdmin(userId)) {
            sendHtml(chatId, "⛔ Нет доступа.", null);
            return;
        }
        UserSession s = sessions.getOrCreate(userId, chatId);
        s.data = new HashMap<>();
        sessions.setState(userId, chatId, UserState.ADMIN_ADD_WAIT_ID, s.data);
        sendHtml(chatId, "➕ Отправьте Telegram <b>user_id</b> нового админа (число).", null);
    }

    private void adminRemoveFlow(long chatId, long userId) throws TelegramApiException {
        if (!admins.isAdmin(userId)) {
            sendHtml(chatId, "⛔ Нет доступа.", null);
            return;
        }
        UserSession s = sessions.getOrCreate(userId, chatId);
        s.data = new HashMap<>();
        sessions.setState(userId, chatId, UserState.ADMIN_REMOVE_WAIT_ID, s.data);
        sendHtml(chatId, "➖ Отправьте Telegram <b>user_id</b> админа, которого нужно удалить (число).", null);
    }

    private void handleAdminAddId(long chatId, long userId, UserSession session, String text) throws TelegramApiException {
        if (!admins.isAdmin(userId)) {
            sessions.clear(userId, chatId);
            sendHtml(chatId, "⛔ Нет доступа.", null);
            return;
        }
        try {
            long id = Long.parseLong(text.trim());
            admins.addAdmin(id);
            sessions.clear(userId, chatId);
            sendHtml(chatId, "✅ Админ добавлен: <b>" + id + "</b>", Keyboards.adminMenu());
        } catch (NumberFormatException e) {
            sendHtml(chatId, "Введите число (user_id).", null);
        }
    }

    private void handleAdminRemoveId(long chatId, long userId, UserSession session, String text) throws TelegramApiException {
        if (!admins.isAdmin(userId)) {
            sessions.clear(userId, chatId);
            sendHtml(chatId, "⛔ Нет доступа.", null);
            return;
        }
        try {
            long id = Long.parseLong(text.trim());
            admins.removeAdmin(id);
            sessions.clear(userId, chatId);
            sendHtml(chatId, "✅ Админ удалён: <b>" + id + "</b>", Keyboards.adminMenu());
        } catch (NumberFormatException e) {
            sendHtml(chatId, "Введите число (user_id).", null);
        }
    }

    private void adminLastRequests(long chatId, long userId) throws TelegramApiException {
        if (!admins.isAdmin(userId)) {
            sendHtml(chatId, "⛔ Нет доступа.", null);
            return;
        }

        var last = requests.last(10);
        if (last.isEmpty()) {
            sendHtml(chatId, "🗂 Заявок пока нет.", Keyboards.adminMenu());
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

        sendHtml(chatId, sb.toString(), Keyboards.adminMenu());
    }

    // ====== Helpers ======

    private void sendHtml(long chatId, String text, InlineKeyboardMarkup kb) throws TelegramApiException {
        SendMessage sm = new SendMessage();
        sm.setChatId(String.valueOf(chatId));
        sm.setText(text);
        sm.setParseMode(ParseMode.HTML);
        if (kb != null) sm.setReplyMarkup(kb);
        execute(sm);
    }

    private void sendPhotoFromResources(long chatId, String resourcePath, String caption, InlineKeyboardMarkup kb) throws TelegramApiException {
        // 1) если уже знаем file_id — шлем без загрузки
        String cachedFileId = resourcePhotoFileIdCache.get(resourcePath);
        if (cachedFileId != null && !cachedFileId.isBlank()) {
            SendPhoto sp = new SendPhoto();
            sp.setChatId(String.valueOf(chatId));
            sp.setPhoto(new InputFile(cachedFileId));
            sp.setCaption(caption);
            sp.setParseMode(ParseMode.HTML);
            if (kb != null) sp.setReplyMarkup(kb);
            execute(sp);
            return;
        }

        // 2) иначе грузим из ресурсов и после отправки сохраняем file_id
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                sendHtml(chatId, caption + "\n\n(⚠️ Не найден ресурс: " + resourcePath + ")", kb);
                return;
            }

            String fileName = resourcePath.contains("/")
                    ? resourcePath.substring(resourcePath.lastIndexOf('/') + 1)
                    : "image.jpg";

            SendPhoto sp = new SendPhoto();
            sp.setChatId(String.valueOf(chatId));
            sp.setPhoto(new InputFile(is, fileName));
            sp.setCaption(caption);
            sp.setParseMode(ParseMode.HTML);
            if (kb != null) sp.setReplyMarkup(kb);

            Message sent = execute(sp);

            // достаем file_id (берем самый большой размер)
            List<PhotoSize> photos = sent.getPhoto();
            if (photos != null && !photos.isEmpty()) {
                PhotoSize best = photos.get(photos.size() - 1);
                if (best.getFileId() != null && !best.getFileId().isBlank()) {
                    resourcePhotoFileIdCache.put(resourcePath, best.getFileId());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void answerCb(String callbackId) throws TelegramApiException {
        AnswerCallbackQuery a = new AnswerCallbackQuery();
        a.setCallbackQueryId(callbackId);
        execute(a);
    }

    private static String userFullName(User u) {
        if (u == null) return "(unknown)";
        String fn = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String ln = u.getLastName() == null ? "" : u.getLastName().trim();
        String full = (fn + " " + ln).trim();
        return full.isBlank() ? "(no name)" : full;
    }

    private static String userTag(User u) {
        if (u == null) return "(нет)";
        String un = u.getUserName();
        if (un == null || un.isBlank()) return "(нет)";
        return "@" + un.trim();
    }
}