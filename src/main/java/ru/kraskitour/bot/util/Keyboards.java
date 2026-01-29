package ru.kraskitour.bot.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class Keyboards {

    public static InlineKeyboardMarkup startMenu(String managerUrl) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("🏝️ ЗАЯВКА НА ПОДБОР ТУРА", Callback.MENU_TOUR)));
        rows.add(one(url("\uD83C\uDF10 НАШ САЙТ•ПОИСК ТУРА", "http://краскитур.рф")));
        rows.add(one(cb("🛂 ШЕНГЕНСКАЯ ВИЗА", Callback.MENU_SCHENGEN)));
        rows.add(one(cb("🏨 ОТЕЛЬ БЕЗ ТУРА", Callback.MENU_HOTEL)));
        rows.add(one(cb("📲 НАШИ СОЦСЕТИ", Callback.MENU_SOCIALS)));
        rows.add(one(cb("📍 НАШ ОФИС", Callback.MENU_OFFICE)));
        rows.add(one(url("💬 НАПИСАТЬ НАМ", managerUrl)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup schengenMenu(String managerUrl) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("💳 Цены на услуги", Callback.SCHENGEN_PRICES)));
        rows.add(one(url("💬 Написать менеджеру", managerUrl)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup schengenPricesMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("⬅️ Вернуться назад", Callback.SCHENGEN_BACK)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup hotelMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("📸 Сравнить цену", Callback.HOTEL_COMPARE)));
        rows.add(one(cb("🛎️ Подобрать отель", Callback.HOTEL_PICK)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup cancelToMenuOnly() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    // ✅ Новая клавиатура: только кнопка "вернуться в меню" (как просили)
    public static InlineKeyboardMarkup backToMenuOnlyLowercase() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup socialsMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("📣 Telegram канал", Callback.SOC_TG)));
        rows.add(one(cb("📸 Instagram", Callback.SOC_IG)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup socialsSubMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("⬅️ Вернуться назад", Callback.SOC_BACK)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup officeMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup adminMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(one(cb("👥 Список админов", Callback.ADMIN_LIST)));
        rows.add(one(cb("➕ Добавить админа", Callback.ADMIN_ADD)));
        rows.add(one(cb("➖ Удалить админа", Callback.ADMIN_REMOVE)));
        rows.add(one(cb("🗂 Последние заявки", Callback.ADMIN_REQUESTS)));
        rows.add(one(cb("🏠 В меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    private static InlineKeyboardMarkup markup(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    /** одна строка = одна кнопка (один столбец) */
    private static List<InlineKeyboardButton> one(InlineKeyboardButton btn) {
        List<InlineKeyboardButton> r = new ArrayList<>(1);
        r.add(btn);
        return r;
    }

    private static InlineKeyboardButton cb(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(data);
        return b;
    }

    private static InlineKeyboardButton url(String text, String url) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setUrl(url);
        return b;
    }
}