package ru.kraskitour.bot.model;

public enum UserState {
    NONE,

    // Подобрать тур
    TOUR_Q1_COUNTRIES_FROM,
    TOUR_Q2_COMPOSITION,
    TOUR_Q3_DATES_NIGHTS,
    TOUR_Q4_BUDGET_HOTEL,
    TOUR_PHONE,

    // Отель без тура -> Сравнить цену
    HOTEL_COMPARE_WAIT_PHOTO,
    HOTEL_COMPARE_WAIT_PHONE,

    // Отель без тура -> Подобрать отель
    HOTEL_PICK_Q1_COUNTRY_CITY,
    HOTEL_PICK_Q2_DATES_PEOPLE,
    HOTEL_PICK_WAIT_PHONE,

    // Админ панель
    ADMIN_ADD_WAIT_ID,
    ADMIN_REMOVE_WAIT_ID
}