package ru.invest.api.ui.service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.bond.supplier.collection.BondCollection;
import ru.invest.api.bond.supplier.usecase.BondCollectionUseCase;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.dto.bond.sitemap.SitemapDto;
import ru.invest.api.news.supplier.usecase.IssuerUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Что перечислить в sitemap.xml. Адреса страниц и сам XML строит gateway: он знает публичный адрес сайта.
 */
@RestController
@RequestMapping("/internal/rest/bonds/sitemap")
@RequiredArgsConstructor
public class SitemapController {
    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheRepositoryUseCase;
    private final IssuerUseCase issuerUseCase;
    private final BondCollectionUseCase bondCollectionUseCase;

    /**
     * Тикеры всех облигаций, эмитенты с выпусками в системе и подборки.
     */
    @GetMapping
    public SitemapDto getSitemap() {
        final Map<String, BondModel> bonds = tinkoffBondCacheRepositoryUseCase.getBonds();
        final List<String> tickers = bonds == null ? List.of() : bonds.values().stream()
                .filter(Objects::nonNull)
                .map(BondModel::getTicker)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return new SitemapDto()
                .setTickers(tickers)
                .setIssuerIds(issuerUseCase.getIssuerIds())
                .setCollections(bondCollectionUseCase.getCollections().stream().map(BondCollection::getSlug).toList());
    }
}
