package ru.invest.api.news.supplier.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;

import java.util.Set;

/**
 * Чтение ленты новостей, новые первыми.
 */
public interface NewsUseCase {
    /**
     * Общая лента.
     *
     * @param sources из каких источников; пусто - из всех
     */
    Page<NewsModel> getFeed(Set<NewsSource> sources, Pageable pageable);

    /**
     * Новости облигации: упомянут её ISIN или новость относится к её эмитенту.
     */
    Page<NewsModel> getByTicker(String ticker, Pageable pageable);

    /**
     * Новости эмитента: относятся к нему или упомянут любой его выпуск. Неизвестный эмитент - пустая страница.
     */
    Page<NewsModel> getByIssuer(Long emitterId, Pageable pageable);
}
