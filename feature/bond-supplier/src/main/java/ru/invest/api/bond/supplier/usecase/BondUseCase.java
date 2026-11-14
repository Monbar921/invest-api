package ru.invest.api.bond.supplier.usecase;

import ru.invest.api.bond.supplier.model.BondScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.calculation.ProfitCalculationModel;
import ru.invest.api.common.model.candle.CandleInterval;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.common.model.event.BondEventModel;
import ru.invest.api.common.model.calculation.ProfitCalculationRequestModel;
import ru.invest.api.common.model.parameters.BondParametersModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
     * Облигация с ценой и купоном вместе с графиком выплат и данными скринера (рейтинги всех агентств).
     */
    BondModel getByTicker(String ticker);

    /**
     * Вся выборка с фильтрами и сортировкой, без страниц - для выгрузки в файл.
     */
    List<BondModel> getAllFiltered(BondScope scope, BondParametersModel bondParametersModel);

    /**
     * Секторы облигаций в системе (в нижнем регистре, по алфавиту) - для фильтра.
     */
    List<String> getSectors();

    /**
     * Облигации с текущей ценой по тикерам, ключ - тикер; облигаций, которых нет, в ответе нет.
     */
    Map<String, BondModel> getByTickers(Collection<String> tickers);

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

    /**
     * Доходность к погашению, как если бы облигацию купили сегодня по цене pricePercent (% от номинала) плюс
     * сегодняшний НКД - тот же расчёт, что у yieldToMaturity облигации, только с другой ценой.
     *
     * @param bond облигация с графиком купонов и амортизации ({@link #getWithSchedule})
     * @return % годовых; null - посчитать нельзя (нет графика, НКД, цена в другой валюте, чем номинал)
     */
    BigDecimal getYieldToMaturityAtPrice(BondModel bond, BigDecimal pricePercent);
}
