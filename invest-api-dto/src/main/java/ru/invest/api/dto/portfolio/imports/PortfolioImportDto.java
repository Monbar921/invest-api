package ru.invest.api.dto.portfolio.imports;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/** Импорт: предпросмотр или отчёт после подтверждения */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioImportDto {
    private Long id;
    private ImportSourceDto source;
    /** Имя файла отчёта; у импорта по токену - null */
    private String fileName;
    private ImportStatusDto status;
    private ZonedDateTime createdAt;
    private ZonedDateTime appliedAt;
    /** Операции источника, которые не сделки с облигациями */
    private int skippedOperations;
    /** Предупреждения по импорту целиком */
    private List<String> warnings;
    /** Сколько сделок в каждом статусе */
    private Map<ImportItemStatusDto, Integer> counts;
    /** Сделки; в списке импортов - null */
    private List<PortfolioImportItemDto> items;
    /** Бумаги, которых нет среди облигаций системы: их сделки не импортируются */
    private List<ImportUnknownBondDto> unknownBonds;
}
