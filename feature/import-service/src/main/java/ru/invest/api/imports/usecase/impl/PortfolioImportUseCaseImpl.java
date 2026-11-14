package ru.invest.api.imports.usecase.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.common.entity.PortfolioImport;
import ru.invest.api.common.entity.PortfolioImportItem;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralPaymentRequiredException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.repository.PortfolioImportItemRepository;
import ru.invest.api.common.repository.PortfolioImportRepository;
import ru.invest.api.common.repository.PortfolioPositionRepository;
import ru.invest.api.common.repository.PortfolioSaleRepository;
import ru.invest.api.common.usecase.EntitlementUseCase;
import ru.invest.api.imports.config.ImportProperties;
import ru.invest.api.imports.model.ImportItemModel;
import ru.invest.api.imports.model.ImportItemStatus;
import ru.invest.api.imports.model.ImportKind;
import ru.invest.api.imports.model.ImportModel;
import ru.invest.api.imports.model.ImportSource;
import ru.invest.api.imports.model.ImportStatus;
import ru.invest.api.imports.model.Money;
import ru.invest.api.imports.model.TradeCandidate;
import ru.invest.api.imports.model.UnknownBondModel;
import ru.invest.api.imports.parser.ReportParseException;
import ru.invest.api.imports.parser.ReportParser;
import ru.invest.api.imports.parser.ReportValues;
import ru.invest.api.imports.tinvest.TinvestAccount;
import ru.invest.api.imports.tinvest.TinvestClient;
import ru.invest.api.imports.tinvest.TinvestOperation;
import ru.invest.api.imports.tinvest.TinvestPosition;
import ru.invest.api.imports.usecase.PortfolioImportUseCase;
import ru.invest.api.portfolio.service.model.PositionRequestModel;
import ru.invest.api.portfolio.service.model.SaleRequestModel;
import ru.invest.api.portfolio.service.usecase.PortfolioTradeUseCase;
import ru.invest.api.portfolio.service.usecase.PortfolioUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PortfolioImportUseCaseImpl implements PortfolioImportUseCase {
    private static final String NOT_FOUND = "Portfolio import %d not found";
    private static final int HISTORY = 20;
    private static final int KEY_HASH_LENGTH = 40;
    private static final int MAX_MESSAGE = 500;
    private static final int MAX_FILE_NAME = 255;
    private static final int MEGABYTE = 1024 * 1024;
    private static final int PRICE_SCALE = 10;

    private final PortfolioImportRepository importRepository;
    private final PortfolioImportItemRepository itemRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioSaleRepository saleRepository;
    private final ImportItemResolver resolver;
    private final BrokerConnectionUseCaseImpl connectionUseCase;
    private final TinvestClient tinvestClient;
    private final PortfolioUseCase portfolioUseCase;
    private final PortfolioTradeUseCase portfolioTradeUseCase;
    private final EntitlementUseCase entitlementUseCase;
    private final ImportProperties properties;
    private final DateTimeMapper dateTimeMapper;
    private final Map<ImportSource, ReportParser> parsers;
    private final TransactionTemplate transaction;
    private final TransactionTemplate itemTransaction;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public PortfolioImportUseCaseImpl(final PortfolioImportRepository importRepository,
                                      final PortfolioImportItemRepository itemRepository,
                                      final PortfolioPositionRepository positionRepository,
                                      final PortfolioSaleRepository saleRepository,
                                      final ImportItemResolver resolver,
                                      final BrokerConnectionUseCaseImpl connectionUseCase,
                                      final TinvestClient tinvestClient,
                                      final PortfolioUseCase portfolioUseCase,
                                      final PortfolioTradeUseCase portfolioTradeUseCase,
                                      final EntitlementUseCase entitlementUseCase,
                                      final ImportProperties properties,
                                      final DateTimeMapper dateTimeMapper,
                                      final List<ReportParser> parsers,
                                      final PlatformTransactionManager transactionManager) {
        this.importRepository = importRepository;
        this.itemRepository = itemRepository;
        this.positionRepository = positionRepository;
        this.saleRepository = saleRepository;
        this.resolver = resolver;
        this.connectionUseCase = connectionUseCase;
        this.tinvestClient = tinvestClient;
        this.portfolioUseCase = portfolioUseCase;
        this.portfolioTradeUseCase = portfolioTradeUseCase;
        this.entitlementUseCase = entitlementUseCase;
        this.properties = properties;
        this.dateTimeMapper = dateTimeMapper;
        this.parsers = parsers.stream().collect(Collectors.toMap(ReportParser::format, Function.identity()));
        this.transaction = new TransactionTemplate(transactionManager);
        this.itemTransaction = new TransactionTemplate(transactionManager);
        this.itemTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public ImportModel previewTinvest(final String userId, final List<String> accountIds, final LocalDate from) {
        entitlementUseCase.requireFeature(userId, PlanFeature.BROKER_IMPORT);
        if (from != null && from.isAfter(today())) {
            throw unprocessable("Дата начала не может быть в будущем");
        }
        final String token = connectionUseCase.token(userId);
        final List<TinvestAccount> accounts = selectAccounts(BrokerConnectionUseCaseImpl.readOnlyAccounts(
                () -> tinvestClient.getAccounts(token)), accountIds);
        final Instant since = from == null ? null : from.atStartOfDay(properties.getZone()).toInstant();
        final int max = properties.getTinvest().getMaxOperations();
        final Map<TinvestAccount, List<TinvestOperation>> operations = new LinkedHashMap<>();
        final Map<TinvestAccount, List<TinvestPosition>> positions = new HashMap<>();
        for (final TinvestAccount account : accounts) {
            operations.put(account, tinvestClient.getTrades(token, account.id(), since, max));
            if (from == null) {
                positions.put(account, tinvestClient.getBondPositions(token, account.id()));
            }
        }
        final PortfolioImport created = transaction.execute(status -> {
            final ImportItemResolver.BondIndex index = resolver.bondIndex();
            final List<String> warnings = new ArrayList<>();
            final List<TradeCandidate> candidates = new ArrayList<>();
            int skipped = 0;
            for (final Map.Entry<TinvestAccount, List<TinvestOperation>> entry : operations.entrySet()) {
                skipped += tinvestCandidates(entry.getKey(), entry.getValue(), positions.get(entry.getKey()), index,
                        candidates, warnings);
                if (entry.getValue().size() >= max) {
                    warnings.add("Со счёта «%s» загружено %d сделок - более ранние не загружены, задайте дату начала"
                            .formatted(entry.getKey().name(), max));
                }
            }
            final PortfolioImport result = save(userId, ImportSource.TINVEST_API, null, candidates, skipped,
                    warnings, index);
            connectionUseCase.markImported(userId);
            return result;
        });
        return get(userId, Objects.requireNonNull(created).getId());
    }

    @Override
    public ImportModel previewFile(final String userId, final ImportSource format, final String fileName,
                                   final byte[] content) {
        entitlementUseCase.requireFeature(userId, PlanFeature.BROKER_IMPORT);
        final ReportParser parser = format == null ? null : parsers.get(format);
        if (parser == null) {
            throw unprocessable("Выберите формат отчёта: " + fileFormats().stream().map(Enum::name)
                    .collect(Collectors.joining(", ")));
        }
        if (content == null || content.length == 0) {
            throw unprocessable("Файл пустой");
        }
        if (content.length > properties.getMaxFileSize()) {
            throw unprocessable("Файл больше %d МБ".formatted(properties.getMaxFileSize() / MEGABYTE));
        }
        final ReportParser.ParsedReport report;
        try {
            report = parser.parse(content);
        } catch (final ReportParseException e) {
            throw unprocessable(e.getMessage());
        }
        if (report.trades().isEmpty()) {
            throw unprocessable("В файле не найдено ни одной покупки или продажи");
        }
        final PortfolioImport created = transaction.execute(status -> save(userId, format,
                truncate(fileName, MAX_FILE_NAME), report.trades(), report.skipped(), List.of(),
                resolver.bondIndex()));
        return get(userId, Objects.requireNonNull(created).getId());
    }

    @Override
    public ImportModel apply(final String userId, final Long importId, final Set<Long> itemIds) {
        entitlementUseCase.requireFeature(userId, PlanFeature.BROKER_IMPORT);
        final Integer changed = transaction.execute(status -> importRepository.changeStatus(importId, userId,
                ImportStatus.PREVIEW.name(), ImportStatus.APPLYING.name()));
        if (changed == null || changed == 0) {
            find(userId, importId);
            throw unprocessable("Импорт уже подтверждён - загрузите сделки заново, уже импортированные пропустятся");
        }
        final Set<Long> selected = itemIds == null ? Set.of() : itemIds;
        final List<PortfolioImportItem> items = itemRepository.findByImportIdOrderByLineNoAscIdAsc(importId);
        final Set<String> buyKeys = new HashSet<>(positionRepository.findImportKeys(userId));
        final Set<String> saleKeys = new HashSet<>(saleRepository.findImportKeys(userId));
        final List<PortfolioImportItem> toCreate = new ArrayList<>();
        for (final PortfolioImportItem item : items) {
            final ImportItemStatus status = ImportItemStatus.valueOf(item.getStatus());
            if (status.isSelectable() && selected.contains(item.getId())) {
                toCreate.add(item);
            } else if (status.isSelectable()) {
                item.setStatus(ImportItemStatus.SKIPPED.name()).setMessage("Не выбрана");
            }
        }
        // покупки раньше продаж: продаже FIFO нужны купленные к её дате облигации
        toCreate.sort(Comparator.comparing(PortfolioImportItem::getTradeDate)
                .thenComparing(item -> ImportKind.valueOf(item.getKind()))
                .thenComparing(PortfolioImportItem::getLineNo));
        for (final PortfolioImportItem item : toCreate) {
            final boolean buy = ImportKind.valueOf(item.getKind()) == ImportKind.BUY;
            final Set<String> keys = buy ? buyKeys : saleKeys;
            if (keys.contains(item.getImportKey())) {
                item.setStatus(ImportItemStatus.DUPLICATE.name()).setMessage("Уже импортирована");
                continue;
            }
            create(userId, item, buy);
            if (ImportItemStatus.CREATED.name().equals(item.getStatus())) {
                keys.add(item.getImportKey());
            }
        }
        transaction.executeWithoutResult(status -> {
            itemRepository.saveAll(items);
            importRepository.findById(importId).ifPresent(entity -> entity.setStatus(ImportStatus.APPLIED.name())
                    .setAppliedAt(LocalDateTime.now(ZoneOffset.UTC)));
        });
        log.info("Portfolio import applied: userId={}, importId={}, created={}", userId, importId,
                items.stream().filter(item -> ImportItemStatus.CREATED.name().equals(item.getStatus())).count());
        return get(userId, importId);
    }

    @Override
    public ImportModel get(final String userId, final Long importId) {
        return transaction.execute(status -> {
            final PortfolioImport entity = find(userId, importId);
            return toModel(entity, itemRepository.findByImportIdOrderByLineNoAscIdAsc(entity.getId()), true);
        });
    }

    @Override
    public List<ImportModel> list(final String userId) {
        return transaction.execute(status -> importRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, HISTORY)).stream()
                .map(entity -> toModel(entity, itemRepository.findByImportIdOrderByLineNoAscIdAsc(entity.getId()),
                        false))
                .toList());
    }

    @Override
    public List<ImportSource> fileFormats() {
        return Arrays.stream(ImportSource.values()).filter(parsers::containsKey).toList();
    }

    /** Одна сделка - своя транзакция: не прошедшая проверки портфеля не откатывает остальные */
    private void create(final String userId, final PortfolioImportItem item, final boolean buy) {
        try {
            final Long id = itemTransaction.execute(status -> buy
                    ? portfolioUseCase.create(userId, new PositionRequestModel(item.getTicker(), item.getQuantity(),
                            item.getPrice(), item.getTradeDate(), item.getBroker(), item.getAmountRub(), item.getAci(),
                            item.getCommission()), item.getImportKey()).id()
                    : portfolioTradeUseCase.createSale(userId, new SaleRequestModel(item.getTicker(), item.getQuantity(),
                            item.getPrice(), item.getTradeDate(), item.getAci(), item.getCommission(),
                            item.getBroker()), item.getImportKey()).id());
            item.setStatus(ImportItemStatus.CREATED.name()).setTradeId(id);
        } catch (final GeneralUnprocessableEntityException | GeneralPaymentRequiredException
                       | GeneralNotFoundEntityException e) {
            item.setStatus(ImportItemStatus.FAILED.name()).setMessage(truncate(e.getMessage(), MAX_MESSAGE));
        } catch (final DataIntegrityViolationException e) {
            // ту же сделку только что создал параллельный импорт - уникальный индекс по ключу
            item.setStatus(ImportItemStatus.DUPLICATE.name()).setMessage("Уже импортирована");
        }
    }

    /**
     * Сделки одного счёта T-Invest и остатки позиций без истории (на проверку).
     *
     * @return сколько операций пропущено - не облигации
     */
    private int tinvestCandidates(final TinvestAccount account, final List<TinvestOperation> operations,
                                  final List<TinvestPosition> positions, final ImportItemResolver.BondIndex index,
                                  final List<TradeCandidate> candidates, final List<String> warnings) {
        final String broker = properties.getTinvest().getBrokerName();
        final Map<String, Long> net = new HashMap<>();
        final Map<String, String> names = new HashMap<>();
        int skipped = 0;
        for (final TinvestOperation operation : operations) {
            if (!operation.bond()) {
                skipped++;
                continue;
            }
            final boolean buy = operation.side() == TinvestOperation.Side.BUY;
            net.merge(operation.instrumentUid(), buy ? operation.quantity() : -operation.quantity(), Long::sum);
            names.putIfAbsent(operation.instrumentUid(), operation.name());
            final String basis = operation.tradeNums().isEmpty()
                    ? "op:" + operation.id()
                    : "trades:" + String.join(",", operation.tradeNums());
            final boolean fits = operation.quantity() > 0 && operation.quantity() <= ImportItemResolver.MAX_QUANTITY;
            candidates.add(new TradeCandidate(candidates.size() + 1, buy ? ImportKind.BUY : ImportKind.SELL,
                    key("tinvest", account.id(), basis), LocalDate.ofInstant(operation.date(), properties.getZone()),
                    operation.instrumentUid(), null, null, operation.figi(), operation.name(),
                    fits ? (int) operation.quantity() : null, null, price(operation), operation.aci(),
                    operation.commission(), broker, null, null));
        }
        if (positions == null) {
            return skipped;
        }
        final Map<String, TinvestPosition> held = positions.stream()
                .collect(Collectors.toMap(TinvestPosition::instrumentUid, Function.identity(), (a, b) -> a));
        for (final TinvestPosition position : positions) {
            final long quantity = position.quantity() == null ? 0 : position.quantity().longValue();
            final long rest = quantity - net.getOrDefault(position.instrumentUid(), 0L);
            if (rest > 0 && rest <= ImportItemResolver.MAX_QUANTITY) {
                final LocalDate date = Optional.ofNullable(account.openedDate()).orElseGet(this::today);
                candidates.add(new TradeCandidate(candidates.size() + 1, ImportKind.BUY,
                        key("tinvest-rest", account.id(), position.instrumentUid() + ":" + rest), date,
                        position.instrumentUid(), null, position.ticker(), position.figi(), position.ticker(),
                        (int) rest, null, position.averagePrice(), null, null, broker, null,
                        ("У брокера %d шт. без покупки в истории счёта: дата - открытие счёта, цена - средняя цена "
                                + "позиции у брокера. Проверьте перед импортом").formatted(rest)));
            }
        }
        final LocalDate today = today();
        net.forEach((uid, quantity) -> {
            final long brokerQuantity = Optional.ofNullable(held.get(uid)).map(TinvestPosition::quantity)
                    .map(BigDecimal::longValue).orElse(0L);
            final BondModel bond = index.find(uid, null, null);
            final boolean matured = bond != null && bond.getMaturityDate() != null
                    && !bond.getMaturityDate().toLocalDate().isAfter(today);
            if (quantity > brokerQuantity && !matured) {
                warnings.add(("«%s»: по сделкам %d шт., у брокера %d шт. - часть, вероятно, выкуплена по оферте или "
                        + "погашена досрочно; добавьте продажу вручную").formatted(
                        bond == null ? names.get(uid) : bond.getName(), quantity, brokerQuantity));
            }
        });
        return skipped;
    }

    /** Цена одной облигации; нет - сумма операции на количество */
    private static Money price(final TinvestOperation operation) {
        if (operation.price() != null && !operation.price().isZero()) {
            return operation.price().abs();
        }
        if (operation.payment() != null && !operation.payment().isZero() && operation.quantity() > 0) {
            return new Money(operation.payment().amount().abs()
                    .divide(BigDecimal.valueOf(operation.quantity()), PRICE_SCALE, RoundingMode.HALF_UP),
                    operation.payment().currency());
        }
        return null;
    }

    private static List<TinvestAccount> selectAccounts(final List<TinvestAccount> accounts,
                                                       final List<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return accounts;
        }
        final Map<String, TinvestAccount> byId = accounts.stream()
                .collect(Collectors.toMap(TinvestAccount::id, Function.identity(), (a, b) -> a));
        return accountIds.stream().distinct().map(id -> Optional.ofNullable(byId.get(id))
                        .orElseThrow(() -> unprocessable("Счёт %s токену не доступен".formatted(id))))
                .toList();
    }

    private PortfolioImport save(final String userId, final ImportSource source, final String fileName,
                                 final List<TradeCandidate> candidates, final int skipped, final List<String> warnings,
                                 final ImportItemResolver.BondIndex index) {
        if (candidates.size() > properties.getMaxItems()) {
            throw unprocessable("Сделок в импорте больше %d - загрузите отчёт за период короче"
                    .formatted(properties.getMaxItems()));
        }
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        importRepository.deleteAll(importRepository.findByUserIdAndStatusAndCreatedAtBefore(userId,
                ImportStatus.PREVIEW.name(), now.minus(properties.getPreviewTtl())));
        final PortfolioImport entity = importRepository.save(new PortfolioImport()
                .setUserId(userId)
                .setSource(source.name())
                .setFileName(fileName)
                .setStatus(ImportStatus.PREVIEW.name())
                .setSkippedOperations(skipped)
                .setWarnings(warnings.isEmpty() ? null : String.join("\n", warnings))
                .setCreatedAt(now));
        final List<PortfolioImportItem> items = resolver.resolve(userId, candidates, index);
        items.forEach(item -> item.setImportId(entity.getId()));
        itemRepository.saveAll(items);
        return entity;
    }

    private ImportModel toModel(final PortfolioImport entity, final List<PortfolioImportItem> items,
                                final boolean withItems) {
        final Map<ImportItemStatus, Integer> counts = new EnumMap<>(ImportItemStatus.class);
        items.forEach(item -> counts.merge(ImportItemStatus.valueOf(item.getStatus()), 1, Integer::sum));
        final Map<String, UnknownBondModel> unknown = new LinkedHashMap<>();
        items.stream()
                .filter(item -> ImportItemStatus.UNKNOWN_BOND.name().equals(item.getStatus()))
                .forEach(item -> unknown.merge(Objects.toString(item.getIsin(), "")
                                + "|" + Objects.toString(item.getFigi(), "") + "|" + Objects.toString(item.getTicker(), ""),
                        new UnknownBondModel(item.getIsin(), item.getFigi(), item.getTicker(), item.getName(), 1),
                        (a, b) -> new UnknownBondModel(a.isin(), a.figi(), a.ticker(), a.name(), a.trades() + 1)));
        return new ImportModel(entity.getId(), ImportSource.valueOf(entity.getSource()), entity.getFileName(),
                ImportStatus.valueOf(entity.getStatus()), entity.getCreatedAt(), entity.getAppliedAt(),
                entity.getSkippedOperations(),
                entity.getWarnings() == null ? List.of() : List.of(entity.getWarnings().split("\n")),
                counts,
                withItems ? items.stream().map(PortfolioImportUseCaseImpl::toModel).toList() : null,
                List.copyOf(unknown.values()));
    }

    private static ImportItemModel toModel(final PortfolioImportItem item) {
        return new ImportItemModel(item.getId(), item.getLineNo(),
                item.getKind() == null ? null : ImportKind.valueOf(item.getKind()), item.getTradeDate(),
                item.getTicker(), item.getIsin(), item.getFigi(), item.getName(), item.getQuantity(), item.getPrice(),
                item.getAci(), item.getCommission(), item.getAmountRub(), item.getBroker(),
                ImportItemStatus.valueOf(item.getStatus()), item.getMessage(), item.getTradeId());
    }

    private PortfolioImport find(final String userId, final Long importId) {
        return importRepository.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND,
                        NOT_FOUND.formatted(importId)));
    }

    private LocalDate today() {
        return dateTimeMapper.getNow().toLocalDate();
    }

    /** Ключ сделки T-Invest: счёт и номера биржевых сделок (id операции брокер может менять) */
    private static String key(final String prefix, final String accountId, final String basis) {
        return prefix + ":" + ReportValues.sha256(accountId + "|" + basis).substring(0, KEY_HASH_LENGTH);
    }

    private static String truncate(final String value, final int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static GeneralUnprocessableEntityException unprocessable(final String message) {
        return BrokerConnectionUseCaseImpl.unprocessable(message);
    }
}
