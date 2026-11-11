package ru.invest.api.ui.service.controller.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.broker.service.usecase.BrokerLinkUseCase;
import ru.invest.api.dto.bond.broker.BrokerClickStatDto;
import ru.invest.api.ui.service.mapper.BrokerLinkDtoMapper;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/internal/rest/maintenance/broker-clicks")
@RequiredArgsConstructor
public class BrokerClickMaintenanceController {
    private final BrokerLinkUseCase brokerLinkUseCase;
    private final BrokerLinkDtoMapper brokerLinkDtoMapper;

    /**
     * Переходы к брокерам-партнёрам по дням, партнёрам и облигациям - для сверки с отчётом партнёрской программы.
     *
     * @param from первый день (YYYY-MM-DD) включительно
     * @param to   последний день включительно
     */
    @GetMapping
    public List<BrokerClickStatDto> getClicks(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate to) {
        return brokerLinkDtoMapper.toClickDto(brokerLinkUseCase.getClicks(from, to));
    }
}
