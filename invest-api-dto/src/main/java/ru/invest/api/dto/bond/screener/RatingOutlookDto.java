package ru.invest.api.dto.bond.screener;

/**
 * Прогноз по рейтингу; UNDER_REVIEW - «под наблюдением» / «на пересмотре».
 */
public enum RatingOutlookDto {
    STABLE,
    POSITIVE,
    NEGATIVE,
    DEVELOPING,
    UNDER_REVIEW
}
