package ru.invest.api.imports.usecase;

import ru.invest.api.imports.model.ImportModel;
import ru.invest.api.imports.model.ImportSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Импорт сделок от брокера в два шага: предпросмотр (сделки источника со статусами, ничего не меняет в портфеле) и
 * подтверждение выбранных сделок (создаёт покупки и продажи портфеля, повтор той же сделки пропускается). Предпросмотр
 * и подтверждение - только в тарифе с {@code BROKER_IMPORT} (402); чужой или неизвестный импорт - 404.
 */
public interface PortfolioImportUseCase {
    /**
     * Сделки со счетов T-Invest по подключённому токену; без {@code from} - ещё и остатки позиций, которых нет в
     * истории сделок (на проверку).
     *
     * @param accountIds счета; пусто - все счета токена
     * @param from       с какой даты; null - вся история
     * @param broker     брокер у созданных сделок; пусто - «Т-Инвестиции» из настроек
     */
    ImportModel previewTinvest(String userId, List<String> accountIds, LocalDate from, String broker);

    /**
     * Сделки из файла отчёта брокера.
     *
     * @param broker брокер у созданных сделок; пусто - из отчёта (у CSV - столбец «Брокер», у отчётов брокеров - имя
     *               брокера)
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException файл не этого формата, пустой,
     *                                                                            слишком большой или сделок больше
     *                                                                            предела
     */
    ImportModel previewFile(String userId, ImportSource format, String fileName, byte[] content, String broker);

    /**
     * Создаёт выбранные сделки предпросмотра: покупки раньше продаж той же даты, каждая - в своей транзакции (не
     * прошедшая проверки портфеля получает FAILED с причиной, остальные создаются).
     *
     * @param itemIds выбранные строки; строки, которые выбрать нельзя, не создаются
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException импорт уже подтверждён
     */
    ImportModel apply(String userId, Long importId, Set<Long> itemIds);

    ImportModel get(String userId, Long importId);

    /** Последние импорты пользователя, без строк */
    List<ImportModel> list(String userId);

    /** Форматы файлов отчётов, для которых есть разбор */
    List<ImportSource> fileFormats();
}
