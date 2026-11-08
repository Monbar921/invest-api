package ru.invest.api.bond.supplier.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.calculation.ProfitCalculationModel;
import ru.invest.api.common.model.candle.CandleInterval;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.common.model.event.BondEventModel;
import ru.invest.api.common.model.calculation.ProfitCalculationRequestModel;
import ru.invest.api.common.model.parameters.BondParametersModel;

import java.time.LocalDate;
import java.util.List;

public interface BondUseCase {
    /**
     * Страница облигаций в иностранной валюте: фильтры и сортировка применяются ко всей выборке, затем берётся страница.
     */
    Page<BondModel> getForeignCurrencyBondsPage(BondParametersModel bondParametersModel, Pageable pageable);

    /**
     * Страница рублёвых облигаций: фильтры и сортировка применяются ко всей выборке, затем берётся страница.
     */
    Page<BondModel> getRubbleCurrencyBondsPage(BondParametersModel bondParametersModel, Pageable pageable);

    /**
     * Страница всех облигаций: фильтры и сортировка применяются ко всей выборке, затем берётся страница.
     */
    Page<BondModel> getAllPage(BondParametersModel bondParametersModel, Pageable pageable);

    /**
     * Облигация с ценой и купоном вместе с графиком выплат.
     */
    BondModel getByTicker(String ticker);

    /**
     * Нестрогий поиск облигаций по тикеру, ISIN и названию - см. {@link ru.invest.api.common.usecase.BondSearchUseCase}.
     */
    List<BondModel> search(String query, int limit);

    /**
     * Доход по облигации за период владения.
     */
    ProfitCalculationModel calculateProfit(String ticker, ProfitCalculationRequestModel request);

    /**
     * Свечи цены облигации в % от номинала за период; неделя и месяц собираются из дневных.
     *
     * @param from первый день, null - вся история
     * @param to   последний день, null - сегодня
     */
    List<CandleModel> getCandles(String ticker, CandleInterval interval, LocalDate from, LocalDate to);

    /**
     * События облигации из её данных - купоны, амортизация, оферта, погашение - за последние 3 месяца и все будущие.
     */
    List<BondEventModel> getEvents(String ticker);

    /**
     * Облигация с графиком купонов и графиком погашений номинала - всё, из чего считаются её события.
     *
     * @throws ru.invest.api.common.exception.GeneralNotFoundEntityException облигации нет
     */
    BondModel getWithSchedule(String ticker);
}
