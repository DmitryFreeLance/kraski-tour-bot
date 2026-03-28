package ru.kraskitour.bot.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Keyboards {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode startMenu(String managerUrl) {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("🏝️ ЗАЯВКА НА ПОДБОР ТУРА", Callback.MENU_TOUR)));
        rows.add(one(link("\uD83C\uDF10 НАШ САЙТ•ПОИСК ТУРА", "http://краскитур.рф")));
        rows.add(one(cb("🛂 ШЕНГЕНСКАЯ ВИЗА", Callback.MENU_SCHENGEN)));
        rows.add(one(cb("🏨 ОТЕЛЬ БЕЗ ТУРА", Callback.MENU_HOTEL)));
        rows.add(one(cb("📲 НАШИ СОЦСЕТИ", Callback.MENU_SOCIALS)));
        rows.add(one(cb("📍 НАШ ОФИС", Callback.MENU_OFFICE)));
        rows.add(one(link("💬 НАПИСАТЬ НАМ", managerUrl)));
        return inlineKeyboard(rows);
    }

    public static ObjectNode schengenMenu(String managerUrl) {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("💳 Цены на услуги", Callback.SCHENGEN_PRICES)));
        rows.add(one(link("💬 Написать менеджеру", managerUrl)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return inlineKeyboard(rows);
    }

    public static ObjectNode schengenPricesMenu() {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("⬅️ Вернуться назад", Callback.SCHENGEN_BACK)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return inlineKeyboard(rows);
    }

    public static ObjectNode hotelMenu() {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("📸 Сравнить цену", Callback.HOTEL_COMPARE)));
        rows.add(one(cb("🛎️ Подобрать отель", Callback.HOTEL_PICK)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return inlineKeyboard(rows);
    }

    public static ObjectNode cancelToMenuOnly() {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return inlineKeyboard(rows);
    }

    // ✅ Новая клавиатура: только кнопка "вернуться в меню" (как просили)
    public static ObjectNode backToMenuOnlyLowercase() {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("вернуться в меню", Callback.BACK_TO_MENU)));
        return inlineKeyboard(rows);
    }

    public static ObjectNode socialsMenu() {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("📣 Telegram канал", Callback.SOC_TG)));
        rows.add(one(cb("📸 Instagram", Callback.SOC_IG)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return inlineKeyboard(rows);
    }

    public static ObjectNode socialsSubMenu() {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("⬅️ Вернуться назад", Callback.SOC_BACK)));
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return inlineKeyboard(rows);
    }

    public static ObjectNode officeMenu() {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("🏠 Вернуться в меню", Callback.BACK_TO_MENU)));
        return inlineKeyboard(rows);
    }

    public static ObjectNode adminMenu() {
        ArrayNode rows = MAPPER.createArrayNode();
        rows.add(one(cb("👥 Список админов", Callback.ADMIN_LIST)));
        rows.add(one(cb("➕ Добавить админа", Callback.ADMIN_ADD)));
        rows.add(one(cb("➖ Удалить админа", Callback.ADMIN_REMOVE)));
        rows.add(one(cb("🗂 Последние заявки", Callback.ADMIN_REQUESTS)));
        rows.add(one(cb("🏠 В меню", Callback.BACK_TO_MENU)));
        return inlineKeyboard(rows);
    }

    private static ObjectNode inlineKeyboard(ArrayNode rows) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.set("buttons", rows);

        ObjectNode att = MAPPER.createObjectNode();
        att.put("type", "inline_keyboard");
        att.set("payload", payload);
        return att;
    }

    /** одна строка = одна кнопка (один столбец) */
    private static ArrayNode one(ObjectNode btn) {
        ArrayNode row = MAPPER.createArrayNode();
        row.add(btn);
        return row;
    }

    private static ObjectNode cb(String text, String payload) {
        ObjectNode b = MAPPER.createObjectNode();
        b.put("type", "callback");
        b.put("text", text);
        b.put("payload", payload);
        return b;
    }

    private static ObjectNode link(String text, String url) {
        ObjectNode b = MAPPER.createObjectNode();
        b.put("type", "link");
        b.put("text", text);
        b.put("url", url);
        return b;
    }
}
