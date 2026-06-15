package ru.invest.api.ui.service.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.request.bond.BondSortRequest;
import ru.invest.api.ui.service.mapper.BondDtoMapper;
import ru.invest.api.ui.service.mapper.BondParametersRequestMapper;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/rest/bonds/foreign")
@RequiredArgsConstructor
public class ForeignBondController {
    private final BondUseCase bondUseCase;
    private final BondParametersRequestMapper bondParametersRequestMapper;
    private final BondDtoMapper bondDtoMapper;

    /**
     * Возвращает список иностранных облигаций.
     *
     * @param batchLimit      максимальное количество облигаций в ответе (>0)
     * @param bondSortRequest поля и направления сортивровки
     */
    @PostMapping("/all")
    public List<BondDto> getAll(
            @RequestParam(required = false) @Positive(message = "batchLimit must be a positive number") final Integer batchLimit,
            @RequestBody(required = false) final List<@Valid BondSortRequest> bondSortRequest) {
        return bondDtoMapper.toDto(
                bondUseCase.getForeignCurrencyBonds(
                        bondParametersRequestMapper.toModel(batchLimit, bondSortRequest)));
    }
}
