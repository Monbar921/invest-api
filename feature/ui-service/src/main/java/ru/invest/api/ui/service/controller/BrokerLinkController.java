package ru.invest.api.ui.service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.broker.service.usecase.BrokerLinkUseCase;
import ru.invest.api.dto.bond.broker.BrokerLinkDto;
import ru.invest.api.ui.service.mapper.BrokerLinkDtoMapper;

import java.util.List;

/**
 * Блок «Купить у брокера». Пути под /bonds: gateway пропускает их без входа, клик гостя - тоже (POST с CSRF).
 */
@RestController
@RequestMapping("/internal/rest/bonds/{ticker}/broker-links")
@RequiredArgsConstructor
public class BrokerLinkController {
    private final BrokerLinkUseCase brokerLinkUseCase;
    private final BrokerLinkDtoMapper brokerLinkDtoMapper;

    /**
     * Реферальные ссылки включённых партнёров; партнёров нет - пустой список, неизвестная облигация - 404.
     */
    @GetMapping
    public List<BrokerLinkDto> getLinks(@PathVariable final String ticker) {
        return brokerLinkDtoMapper.toLinkDto(brokerLinkUseCase.getLinks(ticker));
    }

    /**
     * Учёт перехода к партнёру: 204; партнёра нет или он выключен, облигации нет - 404.
     */
    @PostMapping("/{partnerId}/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerClick(@PathVariable final String ticker, @PathVariable final String partnerId) {
        brokerLinkUseCase.registerClick(ticker, partnerId);
    }
}
