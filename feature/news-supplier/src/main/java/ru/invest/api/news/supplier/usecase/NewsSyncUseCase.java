package ru.invest.api.news.supplier.usecase;

import ru.invest.api.common.model.AuditModel;

/**
 * Загрузка новостей: эмитенты выпусков (раз в сутки), RSS Банка России, новости Мосбиржи; удаление старых.
 */
public interface NewsSyncUseCase {
    void syncAll(AuditModel audit);
}
