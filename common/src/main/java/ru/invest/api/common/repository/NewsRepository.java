package ru.invest.api.common.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.invest.api.common.entity.News;
import ru.invest.api.common.model.news.NewsSource;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NewsRepository extends JpaRepository<News, Long> {
    Page<News> findBySourceIn(Collection<NewsSource> sources, Pageable pageable);

    /**
     * Новости выпуска: упомянут сам выпуск или новость относится к его эмитенту.
     */
    @Query("""
            select n from News n
            where exists (select 1 from NewsLink l
                          where l.news = n
                            and (l.isin = :isin
                                 or l.emitterId in (select i.emitterId from BondIssuer i where i.isin = :isin)))
            """)
    Page<News> findByIsin(String isin, Pageable pageable);

    List<News> findBySourceAndExternalIdIn(NewsSource source, Collection<String> externalIds);

    @Query("select n from News n left join fetch n.links where n.id in :ids")
    List<News> findWithLinksByIdIn(Collection<Long> ids);

    /**
     * Самый новый id Мосбиржи: новости идут по возрастанию id, загрузка останавливается на уже известных.
     */
    /** Последняя загруженная новость: с неё начинают новые правила по новостям */
    @Query("select max(n.id) from News n")
    Optional<Long> findMaxId();

    @Query(value = "select max(cast(external_id as bigint)) from news where source = 'MOEX'", nativeQuery = true)
    Optional<Long> findLastMoexId();

    @Modifying
    @Query("delete from News n where n.publishedAt < :before")
    int deletePublishedBefore(LocalDateTime before);
}
