package ru.kraskitour.bot.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class Keyboards {

    public static InlineKeyboardMarkup startMenu(String managerUrl) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(cb("🏝️ ПОДОБРАТЬ ТУР", Callback.MENU_TOUR), cb("🛂 ШЕНГЕНСКАЯ ВИЗА", Callback.MENU_SCHENGEN)));
        rows.add(row(cb("🏨 ОТЕЛЬ БЕЗ ТУРА", Callback.MENU_HOTEL), cb("📲 НАШИ СОЦСЕТИ", Callback.MENU_SOCIALS)));
        rows.add(row(cb("📍 НАШ ОФИС", Callback.MENU_OFFICE), url("💬 НАПИСАТЬ МЕНЕДЖЕРУ", managerUrl)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup schengenMenu(String managerUrl) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(cb("💳 Цены на услуги", Callback.SCHENGEN_PRICES)));
        rows.add(row(url("💬 Написать менеджеру", managerUrl)));
        rows.add(row(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup schengenPricesMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(cb("⬅️ Вернуться назад", Callback.SCHENGEN_BACK), cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup hotelMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(cb("📸 Сравнить цену", Callback.HOTEL_COMPARE), cb("🛎️ Подобрать отель", Callback.HOTEL_PICK)));
        rows.add(row(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup cancelToMenuOnly() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup socialsMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(cb("📣 Telegram канал", Callback.SOC_TG), cb("📸 Instagram", Callback.SOC_IG)));
        rows.add(row(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup socialsSubMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(cb("⬅️ Вернуться назад", Callback.SOC_BACK), cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup officeMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    public static InlineKeyboardMarkup adminMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row(cb("👥 Список админов", Callback.ADMIN_LIST)));
        rows.add(row(cb("➕ Добавить админа", Callback.ADMIN_ADD), cb("➖ Удалить админа", Callback.ADMIN_REMOVE)));
        rows.add(row(cb("🗂 Последние заявки", Callback.ADMIN_REQUESTS)));
        rows.add(row(cb("🏠 В меню", Callback.BACK_TO_MENU)));
        return markup(rows);
    }

    private static InlineKeyboardMarkup markup(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    private static List<InlineKeyboardButton> row(InlineKeyboardButton... btns) {
        List<InlineKeyboardButton> r = new ArrayList<>();
        for (InlineKeyboardButton b : btns) r.add(b);
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