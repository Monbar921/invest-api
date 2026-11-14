package ru.invest.api.imports.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Импорт: предпросмотр или отчёт после подтверждения.
 *
 * @param skippedOperations операции источника, которые не сделки с облигациями (не импортируются)
 * @param warnings          предупреждения по импорту целиком
 * @param counts            сколько сделок в каждом статусе
 * @param items             сделки; в списке импортов - null
 * @param unknownBonds      бумаги, которых нет среди облигаций системы
 */
public record ImportModel(Long id, ImportSource source, String fileName, ImportStatus status,
                          LocalDateTime createdAt, LocalDateTime appliedAt, int skippedOperations,
                          List<String> warnings, Map<ImportItemStatus, Integer> counts, List<ImportItemModel> items,
                          List<UnknownBondModel> unknownBonds) {
}
