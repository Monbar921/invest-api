package ru.invest.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Импорт сделок от брокера: предпросмотр (что нашлось в источнике) и, после подтверждения, отчёт - что создано, что
 * пропущено как уже импортированное, что не распознано. Строки - {@link PortfolioImportItem}.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "portfolio_import")
public class PortfolioImport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    /** Источник: TINVEST_API, CSV, SBER_HTML */
    @Column(name = "source")
    private String source;

    /** Имя файла отчёта; у импорта по токену - null */
    @Column(name = "file_name")
    private String fileName;

    /** PREVIEW - ждёт подтверждения, APPLIED - сделки созданы */
    @Column(name = "status")
    private String status;

    /** Операций источника, которые не сделки с облигациями (купоны, налоги, акции...) - не импортируются */
    @Column(name = "skipped_operations")
    private int skippedOperations;

    /** Предупреждения по импорту целиком (например, остаток у брокера не сходится со сделками), через перевод строки */
    @Column(name = "warnings")
    private String warnings;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** UTC; null - не подтверждён */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;
}
