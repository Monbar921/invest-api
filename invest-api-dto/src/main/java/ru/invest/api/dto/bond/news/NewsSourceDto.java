package ru.invest.api.dto.bond.news;

/**
 * Источник новости: CBR_PRESS, CBR_EVENT - Банк России; MOEX - Московская биржа; ACRA, NKR - рейтинговые агентства
 * (у их релизов только дата, время в publishedAt - начало дня по Москве); остальные - СМИ.
 */
public enum NewsSourceDto {
    CBR_PRESS,
    CBR_EVENT,
    MOEX,
    ACRA,
    NKR,
    INTERFAX,
    RBC,
    KOMMERSANT,
    VEDOMOSTI,
    FINAM,
    FRANK_MEDIA,
    SMART_LAB
}
