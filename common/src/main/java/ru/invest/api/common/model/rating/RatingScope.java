package ru.invest.api.common.model.rating;

/**
 * Чему присвоен рейтинг.
 */
public enum RatingScope {
    /** эмитенту - действует для всех его выпусков без своего рейтинга */
    ISSUER,
    /** выпуску облигаций (ISIN) - точнее рейтинга эмитента */
    ISSUE
}
