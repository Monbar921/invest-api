package ru.invest.api.common.model.rating;

/**
 * Прогноз по рейтингу из релиза.
 */
public enum RatingOutlook {
    STABLE,
    POSITIVE,
    NEGATIVE,
    DEVELOPING,
    /** «под наблюдением», «на пересмотре» - рейтинг может скоро измениться */
    UNDER_REVIEW
}
