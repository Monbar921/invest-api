package ru.invest.api.common.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.invest.api.common.entity.PortfolioImport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PortfolioImportRepository extends JpaRepository<PortfolioImport, Long> {
    Optional<PortfolioImport> findByIdAndUserId(Long id, String userId);

    List<PortfolioImport> findByUserIdOrderByCreatedAtDescIdDesc(String userId, Pageable pageable);

    /** Неподтверждённые предпросмотры пользователя старше срока - удаляются вместе со строками */
    List<PortfolioImport> findByUserIdAndStatusAndCreatedAtBefore(String userId, String status, LocalDateTime before);

    /**
     * Переводит импорт из статуса from в to, только если он сейчас from: два одновременных подтверждения не создадут
     * сделки дважды.
     *
     * @return 1 - перевёл, 0 - статус уже другой или импорта нет
     */
    @Modifying
    @Query("update PortfolioImport i set i.status = :to where i.id = :id and i.userId = :userId and i.status = :from")
    int changeStatus(@Param("id") Long id, @Param("userId") String userId, @Param("from") String from,
                     @Param("to") String to);
}
