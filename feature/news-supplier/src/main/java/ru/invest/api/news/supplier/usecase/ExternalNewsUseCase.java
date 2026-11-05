package ru.invest.api.news.supplier.usecase;

import ru.invest.api.common.model.news.NewsModel;

import java.util.List;

/**
 * Новости СМИ (RSS из настроек) и рейтинговых агентств (АКРА, НКР). Источник, который не ответил, пропускается.
 */
public interface ExternalNewsUseCase {
    List<NewsModel> getMediaNews();

    List<NewsModel> getRatingNews();
}
