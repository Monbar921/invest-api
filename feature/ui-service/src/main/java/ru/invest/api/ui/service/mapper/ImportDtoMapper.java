package ru.invest.api.ui.service.mapper;

import org.springframework.stereotype.Component;
import ru.invest.api.dto.portfolio.imports.BrokerAccountDto;
import ru.invest.api.dto.portfolio.imports.BrokerConnectionDto;
import ru.invest.api.dto.portfolio.imports.ImportItemStatusDto;
import ru.invest.api.dto.portfolio.imports.ImportKindDto;
import ru.invest.api.dto.portfolio.imports.ImportSourceDto;
import ru.invest.api.dto.portfolio.imports.ImportStatusDto;
import ru.invest.api.dto.portfolio.imports.ImportUnknownBondDto;
import ru.invest.api.dto.portfolio.imports.PortfolioImportDto;
import ru.invest.api.dto.portfolio.imports.PortfolioImportItemDto;
import ru.invest.api.imports.model.BrokerConnectionModel;
import ru.invest.api.imports.model.ImportItemModel;
import ru.invest.api.imports.model.ImportModel;
import ru.invest.api.imports.model.UnknownBondModel;
import ru.invest.api.imports.tinvest.TinvestAccount;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Импорт от брокера → DTO. Вручную, а не MapStruct: счётчики - словарь с ключами-перечислениями.
 */
@Component
public class ImportDtoMapper {
    public PortfolioImportDto toDto(final ImportModel model) {
        final Map<ImportItemStatusDto, Integer> counts = new EnumMap<>(ImportItemStatusDto.class);
        model.counts().forEach((status, count) -> counts.put(ImportItemStatusDto.valueOf(status.name()), count));
        return new PortfolioImportDto()
                .setId(model.id())
                .setSource(ImportSourceDto.valueOf(model.source().name()))
                .setFileName(model.fileName())
                .setStatus(ImportStatusDto.valueOf(model.status().name()))
                .setCreatedAt(utc(model.createdAt()))
                .setAppliedAt(utc(model.appliedAt()))
                .setSkippedOperations(model.skippedOperations())
                .setWarnings(model.warnings())
                .setCounts(counts)
                .setItems(model.items() == null ? null : model.items().stream().map(ImportDtoMapper::toItemDto).toList())
                .setUnknownBonds(model.unknownBonds() == null ? null
                        : model.unknownBonds().stream().map(ImportDtoMapper::toUnknownDto).toList());
    }

    public List<PortfolioImportDto> toDto(final List<ImportModel> models) {
        return models.stream().map(this::toDto).toList();
    }

    public BrokerConnectionDto toDto(final BrokerConnectionModel model) {
        return new BrokerConnectionDto()
                .setEnabled(model.enabled())
                .setConnected(model.connected())
                .setTokenHint(model.tokenHint())
                .setConnectedAt(utc(model.connectedAt()))
                .setLastImportAt(utc(model.lastImportAt()))
                .setAccounts(model.accounts() == null ? null : toAccountDto(model.accounts()));
    }

    public List<BrokerAccountDto> toAccountDto(final List<TinvestAccount> accounts) {
        return accounts.stream()
                .map(account -> new BrokerAccountDto()
                        .setId(account.id())
                        .setName(account.name())
                        .setType(account.type())
                        .setOpenedDate(account.openedDate())
                        .setClosed(account.closed()))
                .toList();
    }

    private static PortfolioImportItemDto toItemDto(final ImportItemModel item) {
        return new PortfolioImportItemDto()
                .setId(item.id())
                .setLineNo(item.lineNo())
                .setKind(item.kind() == null ? null : ImportKindDto.valueOf(item.kind().name()))
                .setDate(item.date())
                .setTicker(item.ticker())
                .setIsin(item.isin())
                .setFigi(item.figi())
                .setName(item.name())
                .setQuantity(item.quantity())
                .setPrice(item.price())
                .setAci(item.aci())
                .setCommission(item.commission())
                .setCurrency(item.currency())
                .setAmountRub(item.amountRub())
                .setBroker(item.broker())
                .setStatus(ImportItemStatusDto.valueOf(item.status().name()))
                .setMessage(item.message())
                .setSelectable(item.status().isSelectable())
                .setTradeId(item.tradeId());
    }

    private static ImportUnknownBondDto toUnknownDto(final UnknownBondModel bond) {
        return new ImportUnknownBondDto()
                .setIsin(bond.isin())
                .setFigi(bond.figi())
                .setTicker(bond.ticker())
                .setName(bond.name())
                .setTrades(bond.trades());
    }

    private static ZonedDateTime utc(final LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneOffset.UTC);
    }
}
