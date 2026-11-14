package ru.invest.api.imports.tinvest;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.imports.config.ImportProperties;
import ru.invest.api.imports.model.Money;
import ru.tinkoff.piapi.contract.v1.AccessLevel;
import ru.tinkoff.piapi.contract.v1.Account;
import ru.tinkoff.piapi.contract.v1.AccountStatus;
import ru.tinkoff.piapi.contract.v1.GetAccountsRequest;
import ru.tinkoff.piapi.contract.v1.GetOperationsByCursorRequest;
import ru.tinkoff.piapi.contract.v1.GetOperationsByCursorResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentType;
import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.contract.v1.OperationItem;
import ru.tinkoff.piapi.contract.v1.OperationItemTrade;
import ru.tinkoff.piapi.contract.v1.OperationState;
import ru.tinkoff.piapi.contract.v1.OperationType;
import ru.tinkoff.piapi.contract.v1.OperationsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.PortfolioRequest;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.contract.v1.UsersServiceGrpc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * T-Invest API по gRPC с токеном пользователя: на каждый вызов - отдельный канал с заголовком Authorization, после
 * вызова закрывается. Общий канал приложения (с токеном приложения) здесь не используется.
 */
@Component
@RequiredArgsConstructor
class GrpcTinvestClient implements TinvestClient {
    private static final int PAGE_SIZE = 1000;
    private static final BigDecimal NANO = BigDecimal.valueOf(1_000_000_000L);
    private static final Set<OperationType> BUY = Set.of(OperationType.OPERATION_TYPE_BUY,
            OperationType.OPERATION_TYPE_BUY_CARD, OperationType.OPERATION_TYPE_BUY_MARGIN);
    private static final Set<OperationType> SELL = Set.of(OperationType.OPERATION_TYPE_SELL,
            OperationType.OPERATION_TYPE_SELL_CARD, OperationType.OPERATION_TYPE_SELL_MARGIN);
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> APP_NAME = Metadata.Key.of("x-app-name", Metadata.ASCII_STRING_MARSHALLER);

    private final ImportProperties properties;

    @Override
    public List<TinvestAccount> getAccounts(final String token) {
        return call(token, channel -> UsersServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .getAccounts(GetAccountsRequest.newBuilder().setStatus(AccountStatus.ACCOUNT_STATUS_ALL).build())
                .getAccountsList().stream()
                .map(GrpcTinvestClient::toAccount)
                .toList());
    }

    @Override
    public List<TinvestOperation> getTrades(final String token, final String accountId, final Instant from,
                                            final int maxOperations) {
        return call(token, channel -> {
            final List<OperationItem> trades = new ArrayList<>();
            final Map<String, List<OperationItem>> fees = new HashMap<>();
            String cursor = null;
            int read = 0;
            do {
                final GetOperationsByCursorRequest.Builder request = GetOperationsByCursorRequest.newBuilder()
                        .setAccountId(accountId)
                        .setLimit(PAGE_SIZE)
                        .setState(OperationState.OPERATION_STATE_EXECUTED)
                        .addAllOperationTypes(BUY)
                        .addAllOperationTypes(SELL)
                        .addOperationTypes(OperationType.OPERATION_TYPE_BROKER_FEE)
                        .setWithoutCommissions(false)
                        .setWithoutTrades(false)
                        .setWithoutOvernights(true);
                if (from != null) {
                    request.setFrom(timestamp(from));
                }
                if (cursor != null) {
                    request.setCursor(cursor);
                }
                final GetOperationsByCursorResponse response = OperationsServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                        .getOperationsByCursor(request.build());
                for (final OperationItem item : response.getItemsList()) {
                    if (item.getType() == OperationType.OPERATION_TYPE_BROKER_FEE) {
                        fees.computeIfAbsent(item.getParentOperationId(), key -> new ArrayList<>()).add(item);
                    } else {
                        trades.add(item);
                    }
                }
                read += response.getItemsCount();
                cursor = response.getHasNext() && !response.getNextCursor().isEmpty() ? response.getNextCursor() : null;
            } while (cursor != null && read < maxOperations);
            return trades.stream()
                    .map(item -> toOperation(item, fees.getOrDefault(item.getId(), List.of())))
                    .sorted(Comparator.comparing(TinvestOperation::date))
                    .toList();
        });
    }

    @Override
    public List<TinvestPosition> getBondPositions(final String token, final String accountId) {
        return call(token, channel -> OperationsServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(timeoutMs(), TimeUnit.MILLISECONDS)
                .getPortfolio(PortfolioRequest.newBuilder().setAccountId(accountId).build())
                .getPositionsList().stream()
                .filter(position -> "bond".equalsIgnoreCase(position.getInstrumentType()))
                .map(position -> new TinvestPosition(position.getInstrumentUid(), position.getFigi(),
                        position.getTicker(), decimal(position.getQuantity()),
                        money(position.getAveragePositionPrice())))
                .toList());
    }

    private <T> T call(final String token, final Function<ManagedChannel, T> request) {
        final Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, "Bearer " + token);
        headers.put(APP_NAME, properties.getTinvest().getAppName());
        final ManagedChannel channel = NettyChannelBuilder.forTarget(properties.getTinvest().getTarget())
                .intercept(MetadataUtils.newAttachHeadersInterceptor(headers))
                .useTransportSecurity()
                .build();
        try {
            return request.apply(channel);
        } catch (final StatusRuntimeException e) {
            throw toException(e.getStatus());
        } finally {
            channel.shutdownNow();
        }
    }

    /** Код и описание статуса - без заголовков запроса, токена в сообщении нет */
    private static TinvestClientException toException(final Status status) {
        final String message = "T-Invest API: " + status.getCode()
                + (status.getDescription() == null ? "" : " " + status.getDescription());
        return new TinvestClientException(switch (status.getCode()) {
            case UNAUTHENTICATED -> TinvestClientException.Kind.UNAUTHENTICATED;
            case PERMISSION_DENIED -> TinvestClientException.Kind.PERMISSION_DENIED;
            default -> TinvestClientException.Kind.UNAVAILABLE;
        }, message);
    }

    private long timeoutMs() {
        return properties.getTinvest().getTimeout().toMillis();
    }

    private static TinvestAccount toAccount(final Account account) {
        return new TinvestAccount(account.getId(), account.getName(), account.getType().name(),
                account.hasOpenedDate() && account.getOpenedDate().getSeconds() > 0
                        ? LocalDate.ofInstant(instant(account.getOpenedDate()), ZoneOffset.UTC)
                        : null,
                toAccess(account.getAccessLevel()),
                account.getStatus() == AccountStatus.ACCOUNT_STATUS_CLOSED);
    }

    private static TinvestAccount.Access toAccess(final AccessLevel level) {
        return switch (level) {
            case ACCOUNT_ACCESS_LEVEL_READ_ONLY -> TinvestAccount.Access.READ_ONLY;
            case ACCOUNT_ACCESS_LEVEL_FULL_ACCESS -> TinvestAccount.Access.FULL_ACCESS;
            case ACCOUNT_ACCESS_LEVEL_NO_ACCESS -> TinvestAccount.Access.NO_ACCESS;
            default -> TinvestAccount.Access.UNKNOWN;
        };
    }

    private static TinvestOperation toOperation(final OperationItem item, final List<OperationItem> fees) {
        final boolean buy = BUY.contains(item.getType());
        Money commission = item.hasCommission() ? money(item.getCommission()).abs() : null;
        // комиссия, списанная отдельной операцией с этой сделкой как родительской
        if ((commission == null || commission.isZero()) && !fees.isEmpty()) {
            commission = new Money(fees.stream().map(fee -> money(fee.getPayment()).amount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add), currency(fees.getFirst().getPayment()));
        }
        final long quantity = item.getQuantityDone() > 0 ? item.getQuantityDone() : item.getQuantity();
        return new TinvestOperation(item.getId(), item.getBrokerAccountId(),
                buy ? TinvestOperation.Side.BUY : TinvestOperation.Side.SELL,
                item.getInstrumentKind() == InstrumentType.INSTRUMENT_TYPE_BOND
                        || "bond".equalsIgnoreCase(item.getInstrumentType()),
                item.getInstrumentUid(), item.getFigi(), item.getName(), instant(item.getDate()), quantity,
                item.hasPrice() ? money(item.getPrice()) : null,
                item.hasPayment() ? money(item.getPayment()).abs() : null,
                item.hasAccruedInt() ? money(item.getAccruedInt()).abs() : null,
                commission,
                item.hasTradesInfo()
                        ? item.getTradesInfo().getTradesList().stream().map(OperationItemTrade::getNum)
                        .filter(num -> !num.isBlank()).sorted().toList()
                        : List.of());
    }

    private static Money money(final MoneyValue value) {
        return new Money(BigDecimal.valueOf(value.getUnits()).add(BigDecimal.valueOf(value.getNano()).divide(NANO)),
                currency(value));
    }

    private static String currency(final MoneyValue value) {
        return value.getCurrency().isBlank() ? null : value.getCurrency().toLowerCase();
    }

    private static BigDecimal decimal(final Quotation value) {
        return BigDecimal.valueOf(value.getUnits()).add(BigDecimal.valueOf(value.getNano()).divide(NANO));
    }

    private static Instant instant(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Timestamp timestamp(final Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
