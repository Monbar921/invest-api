package ru.invest.api.common.constants;

public interface SchedulerConstants {
    String SCHEDULER_PROCESS = "SYSTEM";

    /**
     * Общая блокировка ночной цепочки синхронизации и частой синхронизации цен:
     * пока идёт цепочка (она сама обновляет цены), частый прогон цен пропускается.
     */
    String BOND_DATA_SYNC_LOCK = "BondDataSync";

    /**
     * Загрузка новостей: своя блокировка - с синхронизацией облигаций она не пересекается по данным.
     */
    String NEWS_SYNC_LOCK = "NewsSync";
}
