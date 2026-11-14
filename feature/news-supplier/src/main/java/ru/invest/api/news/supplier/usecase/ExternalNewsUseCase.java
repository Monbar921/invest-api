package ru.invest.api.news.supplier.usecase;

import ru.invest.api.common.model.news.NewsModel;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Новости СМИ (RSS из настроек) и рейтинговых агентств (АКРА, НКР). Источник, который не ответил, пропускается.
 */
public interface ExternalNewsUseCase {
    List<NewsModel> getMediaNews();

    List<NewsModel> getRatingNews();

    /**
     * Релизы агентств не старше даты: АКРА - по страницам списка (не больше maxPages, с паузой), НКР - весь список
     * одной страницей. Для разбора рейтингов, не для ленты.
     */
    List<NewsModel> getRatingHistory(LocalDate since, int maxPages, Duration pagePause);
}
