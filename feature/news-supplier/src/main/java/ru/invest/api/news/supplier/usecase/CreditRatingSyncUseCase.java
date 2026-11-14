package ru.invest.api.news.supplier.usecase;

import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.news.NewsModel;

import java.util.List;

/**
 * Текущие кредитные рейтинги из релизов АКРА и НКР (таблица credit_rating).
 */
public interface CreditRatingSyncUseCase {
    /**
     * Разбирает заголовки релизов и сохраняет рейтинги эмитентов и выпусков; повторный разбор тех же релизов ничего
     * не меняет, более старое действие не заменяет более новое.
     *
     * @return сколько рейтингов сохранено
     */
    int apply(List<NewsModel> releases, AuditModel audit);

    /**
     * История релизов агентств за срок действия рейтинга (настройка ratings.max-age): АКРА - по страницам списка,
     * НКР - весь список. Новости при этом не сохраняются - только рейтинги.
     *
     * @return сколько рейтингов сохранено
     */
    int loadHistory(AuditModel audit);
}
