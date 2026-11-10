package ru.invest.api.ui.service.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.bond.supplier.collection.BondCollection;
import ru.invest.api.bond.supplier.usecase.BondCollectionUseCase;
import ru.invest.api.dto.bond.collection.BondCollectionDto;
import ru.invest.api.dto.bond.collection.BondCollectionPageDto;
import ru.invest.api.ui.service.mapper.BondDtoMapper;
import ru.invest.api.ui.service.mapper.IssuerDtoMapper;

import java.util.List;

/**
 * Подборки облигаций на готовых фильтрах.
 */
@Validated
@RestController
@RequestMapping("/internal/rest/bonds/collections")
@RequiredArgsConstructor
public class BondCollectionController {
    private static final String DEFAULT_PAGE_SIZE = "50";
    private static final long MAX_PAGE_SIZE = 500;

    private final BondCollectionUseCase bondCollectionUseCase;
    private final IssuerDtoMapper issuerDtoMapper;
    private final BondDtoMapper bondDtoMapper;

    @GetMapping
    public List<BondCollectionDto> getCollections() {
        return issuerDtoMapper.toCollectionDto(bondCollectionUseCase.getCollections());
    }

    /**
     * Подборка со страницей облигаций; неизвестная подборка - 404.
     */
    @GetMapping("/{slug}")
    public BondCollectionPageDto getCollection(
            @PathVariable final String slug,
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must not be negative") final int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Positive(message = "size must be a positive number")
            @Max(value = MAX_PAGE_SIZE, message = "size must not exceed 500") final int size) {
        final BondCollection collection = bondCollectionUseCase.getCollection(slug);
        return new BondCollectionPageDto()
                .setSlug(collection.getSlug())
                .setTitle(collection.getTitle())
                .setDescription(collection.getDescription())
                .setBonds(bondDtoMapper.toDto(bondCollectionUseCase.getPage(collection, PageRequest.of(page, size))));
    }
}
