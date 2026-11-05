package ru.invest.api.dto.bond.news;

/**
 * Источник новости: CBR_PRESS - пресс-релизы Банка России, CBR_EVENT - его события и комментарии,
 * MOEX - новости Московской биржи.
 */
public enum NewsSourceDto {
    CBR_PRESS,
    CBR_EVENT,
    MOEX
}
