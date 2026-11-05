package ru.invest.api.news.supplier.usecase;

import ru.invest.api.common.model.news.IssuerModel;
import ru.invest.api.common.model.news.NewsModel;

import java.util.List;

/**
 * Данные Московской биржи: новости и эмитенты выпусков.
 */
public interface MoexNewsUseCase {
    /**
     * Новости новее lastKnownId (null - первая загрузка, берутся последние несколько страниц), без технических
     * сообщений о ценовых коридорах, аукционах и т.п. У каждой - ISIN выпусков из заголовка и текста.
     */
    List<NewsModel> getNewsAfter(Long lastKnownId);

    /**
     * Эмитенты всех торгуемых на бирже облигаций.
     */
    List<IssuerModel> getBondIssuers();
}
