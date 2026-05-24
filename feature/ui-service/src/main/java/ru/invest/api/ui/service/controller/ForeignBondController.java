package ru.invest.api.ui.service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.dto.dto.bond.BondDto;
import ru.invest.api.ui.service.mapper.BondDtoMapper;
import ru.invest.api.ui.service.mapper.BondParametersRequestMapper;

import java.util.List;

@RestController
@RequestMapping("/internal/rest/bonds/foreign")
@RequiredArgsConstructor
public class ForeignBondController {
    private final BondUseCase bondUseCase;

    private final BondParametersRequestMapper bondParametersRequestMapper;
    private final BondDtoMapper bondDtoMapper;

    @GetMapping("/all")
    public List<BondDto> getAll(@RequestParam(required = false) Integer batchLimit) {
        return bondDtoMapper.toDto(
                bondUseCase.getForeignCurrencyBonds(bondParametersRequestMapper.toModel(batchLimit))
        );
    }
}
