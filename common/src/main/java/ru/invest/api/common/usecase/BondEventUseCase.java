package ru.invest.api.common.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.event.BondEventModel;

import java.time.LocalDate;
import java.util.List;

/**
 * События облигации из её собственных данных: купоны (с пересмотром размера), амортизация, оферта, погашение.
 */
public interface BondEventUseCase {
    /**
     * События с даты from по возрастанию даты.
     *
     * @param bond облигация с графиком купонов и амортизации
     */
    List<BondEventModel> getEvents(BondModel bond, LocalDate from);
}
