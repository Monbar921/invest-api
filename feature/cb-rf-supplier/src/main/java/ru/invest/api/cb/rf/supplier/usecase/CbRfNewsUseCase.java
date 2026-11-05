package ru.invest.api.cb.rf.supplier.usecase;

import ru.invest.api.common.model.news.KeyRateModel;
import ru.invest.api.common.model.news.NewsModel;

import java.time.LocalDate;
import java.util.List;

/**
 * Новости и ключевая ставка Банка России.
 */
public interface CbRfNewsUseCase {
    /**
     * Пресс-релизы и «События и комментарии» из RSS ЦБ. Лента, которая не загрузилась, пропускается.
     */
    List<NewsModel> getNews();

    /**
     * Ключевая ставка по дням за период, по возрастанию даты. ЦБ отдаёт только рабочие дни.
     */
    List<KeyRateModel> getKeyRates(LocalDate from, LocalDate to);
}
