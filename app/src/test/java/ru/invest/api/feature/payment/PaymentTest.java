package ru.invest.api.feature.payment;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.dto.account.AccountPlanDto;
import ru.invest.api.dto.account.PaymentDto;
import ru.invest.api.dto.account.PlanDto;
import ru.invest.api.payment.service.provider.PaymentProviderException;
import ru.invest.api.payment.service.provider.tbank.TbankClient;
import ru.invest.api.payment.service.provider.tbank.TbankSignature;
import ru.invest.api.payment.service.usecase.RenewalUseCase;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Оплата тарифа через Т-Кассу: покупка, webhook с подписью, идемпотентность, неудачный платёж, опрос статуса,
 * продление срока, автопродление и его отмена, возврат. HTTP к Т-Кассе замокан (TbankClient), webhook приходит в
 * настоящий эндпоинт с подписью, посчитанной тем же алгоритмом. Тарифы и оплата - из
 * app/src/test/resources/application.yml: premium продаётся за 299 ₽ на месяц, basic - платный без цены.
 */
public class PaymentTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ALICE = "payment-alice";
    private static final String BOB = "payment-bob";
    private static final String TERMINAL = "InvestTestDEMO";
    private static final String PASSWORD = "test-password";
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final long PREMIUM_KOPECKS = 29900;
    private static final String PAN = "430000******0777";
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;
    private static final int BAD_GATEWAY = 502;
    private static final TypeReference<List<PaymentDto>> PAYMENTS = new TypeReference<>() {
    };

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicLong providerIds = new AtomicLong(7000);

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TbankClient tbankClient;
    @Autowired
    private RenewalUseCase renewalUseCase;

    @BeforeEach
    void setUp() {
        reset(tbankClient);
        // Init: платёж создан, страница оплаты - по id платежа
        when(tbankClient.call(eq("Init"), anyMap())).thenAnswer(invocation -> {
            final Map<String, Object> request = invocation.getArgument(1);
            final String paymentId = String.valueOf(providerIds.incrementAndGet());
            return response("NEW", paymentId, request.get("OrderId"), "PaymentURL",
                    "https://securepay.test/new/" + paymentId);
        });
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("truncate table account_auto_renewal, payment, account_subscription restart identity");
    }

    @Test
    void requestsWithoutUserAreRejected() {
        assertThat(send("POST", "/account/payments", null, Map.of("plan", "premium")).statusCode(),
                equalTo(UNAUTHORIZED));
        assertThat(send("GET", "/account/payments", null, null).statusCode(), equalTo(UNAUTHORIZED));
        assertThat(send("DELETE", "/account/auto-renewal", null, null).statusCode(), equalTo(UNAUTHORIZED));
    }

    @Test
    void planShowsPricesAndPaymentLinks() {
        final AccountPlanDto plan = plan(ALICE);

        final PlanDto premium = plan.getPlans().get(2);
        assertThat(premium.isPurchasable(), equalTo(true));
        assertThat(premium.getPrice(), comparesEqualTo(new BigDecimal("299")));
        assertThat(premium.getPeriod(), equalTo("P1M"));
        assertThat(premium.getPeriodTitle(), equalTo("1 месяц"));
        // платный без цены и бесплатный не продаются
        assertThat(plan.getPlans().get(1).isPurchasable(), equalTo(false));
        assertThat(plan.getPlans().get(0).isPurchasable(), equalTo(false));
        assertThat(plan.getPayments().isEnabled(), equalTo(true));
        assertThat(plan.getPayments().getOfferUrl(), equalTo("https://invest.test/offer"));
        assertThat(plan.getPayments().getPrivacyUrl(), equalTo("https://invest.test/privacy"));
        assertThat(plan.getPayments().getAutoRenewal(), nullValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void purchaseCreatesPaymentPageAndDoesNotChangePlan() {
        final PaymentDto payment = purchase(ALICE, "premium");

        assertThat(payment.getStatus(), equalTo("PENDING"));
        assertThat(payment.getKind(), equalTo("PURCHASE"));
        assertThat(payment.getAmount(), comparesEqualTo(new BigDecimal("299")));
        assertThat(payment.getPlanTitle(), equalTo("Премиум"));
        assertThat(payment.getConfirmationUrl(), equalTo("https://securepay.test/new/" + providerIds.get()));
        assertThat(plan(ALICE).getPlan().getId(), equalTo("free"));

        final ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(tbankClient).call(eq("Init"), captor.capture());
        final Map<String, Object> init = captor.getValue();
        assertThat(init.get("TerminalKey"), equalTo(TERMINAL));
        assertThat(init.get("Amount"), equalTo(PREMIUM_KOPECKS));
        assertThat(init.get("Description"), equalTo("Тариф «Премиум» на 1 месяц"));
        assertThat(init.get("CustomerKey"), equalTo(ALICE));
        assertThat(init.get("Recurrent"), equalTo("Y"));
        assertThat(init.get("SuccessURL"), equalTo("https://invest.test/plan?payment=" + payment.getId()));
        assertThat(init.get("FailURL"), equalTo("https://invest.test/plan?payment=" + payment.getId() + "&failed=true"));
        assertThat(init.get("NotificationURL"), equalTo("https://invest.test/api/payments/notifications/tbank"));
        assertThat(init.get("Token"), equalTo(TbankSignature.token(init, PASSWORD)));
    }

    @Test
    void confirmedNotificationGrantsPlanOnce() {
        final PaymentDto payment = purchase(ALICE, "premium");
        final LocalDate lastDay = today().plusMonths(1);

        final HttpResponse<String> first = notify(notification(payment, "CONFIRMED", true, "REBILL-1"));
        assertThat(first.statusCode(), equalTo(OK));
        assertThat(first.body(), equalTo("OK"));

        final AccountPlanDto plan = plan(ALICE);
        assertThat(plan.getPlan().getId(), equalTo("premium"));
        assertThat(plan.getSource(), equalTo("PAYMENT"));
        assertThat(plan.getValidUntil(), equalTo(lastDay));
        assertThat(plan.getPayments().getAutoRenewal().getCardPan(), equalTo(PAN));
        // списание - за сутки до конца срока: начало последнего дня по Москве
        assertThat(plan.getPayments().getAutoRenewal().getNextChargeDate(), equalTo(lastDay));
        assertThat(plan.getPayments().getAutoRenewal().getAmount(), comparesEqualTo(new BigDecimal("299")));

        // повтор того же webhook (провайдер не получил ответ) - тариф не продлевается второй раз
        assertThat(notify(notification(payment, "CONFIRMED", true, "REBILL-1")).body(), equalTo("OK"));
        assertThat(plan(ALICE).getValidUntil(), equalTo(lastDay));
        assertThat(count("select count(*) from account_subscription where user_id = ?", ALICE), equalTo(1L));
        assertThat(count("select count(*) from account_auto_renewal where user_id = ?", ALICE), equalTo(1L));

        final PaymentDto paid = payment(ALICE, payment.getId());
        assertThat(paid.getStatus(), equalTo("SUCCEEDED"));
        assertThat(paid.getCardPan(), equalTo(PAN));
        assertThat(paid.getPaidAt(), notNullValue());
        assertThat(paid.getConfirmationUrl(), nullValue());
    }

    @Test
    void notificationWithWrongSignatureIsRejected() {
        final PaymentDto payment = purchase(ALICE, "premium");
        final Map<String, Object> forged = notification(payment, "CONFIRMED", true, "REBILL-1");
        forged.put("Token", TbankSignature.token(forged, "not-the-password"));

        assertThat(notify(forged).statusCode(), equalTo(BAD_REQUEST));
        // подпись верная, но поле изменено после подписи
        final Map<String, Object> tampered = notification(payment, "CONFIRMED", true, "REBILL-1");
        tampered.put("Amount", 100);
        assertThat(notify(tampered).statusCode(), equalTo(BAD_REQUEST));
        assertThat(sendRaw("/payments/notifications/tbank", "not json").statusCode(), equalTo(BAD_REQUEST));

        assertThat(plan(ALICE).getPlan().getId(), equalTo("free"));
        assertThat(payment(ALICE, payment.getId()).getStatus(), equalTo("PENDING"));
    }

    @Test
    void unknownProviderAndOrderAreHandled() {
        assertThat(sendRaw("/payments/notifications/yookassa", "{}").statusCode(), equalTo(NOT_FOUND));

        final Map<String, Object> unknown = new LinkedHashMap<>();
        unknown.put("TerminalKey", TERMINAL);
        unknown.put("OrderId", "not-ours");
        unknown.put("Success", true);
        unknown.put("Status", "CONFIRMED");
        unknown.put("PaymentId", 1);
        unknown.put("Amount", PREMIUM_KOPECKS);
        unknown.put("Token", TbankSignature.token(unknown, PASSWORD));
        // чужой заказ подтверждается, иначе провайдер повторял бы его
        assertThat(notify(unknown).body(), equalTo("OK"));
    }

    @Test
    void failedPaymentDoesNotChangePlan() {
        final PaymentDto payment = purchase(ALICE, "premium");
        final Map<String, Object> rejected = notification(payment, "REJECTED", false, null);
        rejected.put("ErrorCode", "1051");
        rejected.put("Message", "Недостаточно средств на карте");
        rejected.put("Token", TbankSignature.token(rejected, PASSWORD));

        assertThat(notify(rejected).body(), equalTo("OK"));

        final AccountPlanDto plan = plan(ALICE);
        assertThat(plan.getPlan().getId(), equalTo("free"));
        assertThat(plan.getPayments().getAutoRenewal(), nullValue());
        final PaymentDto failed = payment(ALICE, payment.getId());
        assertThat(failed.getStatus(), equalTo("FAILED"));
        assertThat(failed.getErrorMessage(), equalTo("Недостаточно средств на карте"));
        // успех после отказа (запоздавший или поддельный порядок) не выдаёт тариф
        notify(notification(payment, "CONFIRMED", true, "REBILL-1"));
        assertThat(plan(ALICE).getPlan().getId(), equalTo("free"));
    }

    @Test
    void amountMismatchIsNotApplied() {
        final PaymentDto payment = purchase(ALICE, "premium");
        final Map<String, Object> cheaper = notification(payment, "CONFIRMED", true, "REBILL-1");
        cheaper.put("Amount", 100);
        cheaper.put("Token", TbankSignature.token(cheaper, PASSWORD));

        assertThat(notify(cheaper).body(), equalTo("OK"));

        assertThat(plan(ALICE).getPlan().getId(), equalTo("free"));
        assertThat(payment(ALICE, payment.getId()).getStatus(), equalTo("PENDING"));
    }

    @Test
    void pendingPaymentStatusIsRequestedFromProvider() {
        final PaymentDto payment = purchase(ALICE, "premium");
        when(tbankClient.call(eq("GetState"), anyMap())).thenReturn(
                response("CONFIRMED", String.valueOf(providerIds.get()), null, "Amount", PREMIUM_KOPECKS));

        // webhook не дошёл (локальный запуск): UI опрашивает платёж - статус берётся у провайдера
        assertThat(payment(ALICE, payment.getId()).getStatus(), equalTo("SUCCEEDED"));
        assertThat(plan(ALICE).getPlan().getId(), equalTo("premium"));
        assertThat(plan(ALICE).getPayments().getAutoRenewal(), nullValue());

        // webhook пришёл позже - карта сохраняется для автопродления, срок не продлевается второй раз
        notify(notification(payment, "CONFIRMED", true, "REBILL-1"));
        final AccountPlanDto plan = plan(ALICE);
        assertThat(plan.getValidUntil(), equalTo(today().plusMonths(1)));
        assertThat(plan.getPayments().getAutoRenewal().getCardPan(), equalTo(PAN));

        // оплаченный платёж у провайдера больше не запрашивается
        payment(ALICE, payment.getId());
        verify(tbankClient, times(1)).call(eq("GetState"), anyMap());
    }

    @Test
    void secondPurchaseExtendsFromLastDay() {
        pay(ALICE, "REBILL-1");
        pay(ALICE, null);

        final AccountPlanDto plan = plan(ALICE);
        assertThat(plan.getValidUntil(), equalTo(today().plusMonths(2)));
        // покупка без новой карты сдвигает автопродление на новый конец срока
        assertThat(plan.getPayments().getAutoRenewal().getNextChargeDate(), equalTo(today().plusMonths(2)));
        assertThat(history(ALICE).stream().map(PaymentDto::getStatus).toList(), contains("SUCCEEDED", "SUCCEEDED"));
    }

    @Test
    void purchaseValidation() {
        assertThat(send("POST", "/account/payments", ALICE, Map.of("plan", "basic")).statusCode(),
                equalTo(UNPROCESSABLE));
        assertThat(send("POST", "/account/payments", ALICE, Map.of("plan", "free")).statusCode(),
                equalTo(UNPROCESSABLE));
        assertThat(send("POST", "/account/payments", ALICE, Map.of("plan", "gold")).statusCode(),
                equalTo(UNPROCESSABLE));
        assertThat(send("POST", "/account/payments", ALICE, Map.of()).statusCode(), equalTo(BAD_REQUEST));
        verify(tbankClient, never()).call(any(), anyMap());
    }

    @Test
    void providerErrorReturns502AndKeepsFailedPayment() {
        when(tbankClient.call(eq("Init"), anyMap())).thenThrow(new PaymentProviderException("timeout"));

        final HttpResponse<String> response = send("POST", "/account/payments", ALICE, Map.of("plan", "premium"));

        assertThat(response.statusCode(), equalTo(BAD_GATEWAY));
        assertThat(history(ALICE).stream().map(PaymentDto::getStatus).toList(), contains("FAILED"));
        assertThat(plan(ALICE).getPlan().getId(), equalTo("free"));
    }

    @Test
    void foreignPaymentIsNotVisible() {
        final PaymentDto payment = purchase(ALICE, "premium");

        assertThat(send("GET", "/account/payments/" + payment.getId(), BOB, null).statusCode(), equalTo(NOT_FOUND));
        assertThat(history(BOB), equalTo(List.of()));
    }

    @Test
    void renewalChargesSavedCard() {
        pay(ALICE, "REBILL-1");
        dueNow(ALICE);
        when(tbankClient.call(eq("Charge"), anyMap())).thenAnswer(invocation -> {
            final Map<String, Object> request = invocation.getArgument(1);
            return response("CONFIRMED", String.valueOf(request.get("PaymentId")), null, "Amount", PREMIUM_KOPECKS);
        });

        assertThat(renewalUseCase.renewDue(), equalTo(1));

        final AccountPlanDto plan = plan(ALICE);
        assertThat(plan.getValidUntil(), equalTo(today().plusMonths(2)));
        assertThat(plan.getPayments().getAutoRenewal().getNextChargeDate(), equalTo(today().plusMonths(2)));
        final PaymentDto renewal = history(ALICE).get(0);
        assertThat(renewal.getKind(), equalTo("RENEWAL"));
        assertThat(renewal.getStatus(), equalTo("SUCCEEDED"));
        assertThat(renewal.getCardPan(), equalTo(PAN));
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(tbankClient).call(eq("Charge"), captor.capture());
        assertThat(captor.getValue().get("RebillId"), equalTo("REBILL-1"));
        assertThat(captor.getValue().get("Token"), equalTo(TbankSignature.token(captor.getValue(), PASSWORD)));
        // следующий запуск: списывать пока нечего
        assertThat(renewalUseCase.renewDue(), equalTo(0));
    }

    @Test
    void cancelledAutoRenewalDoesNotCharge() {
        pay(ALICE, "REBILL-1");
        final LocalDate lastDay = plan(ALICE).getValidUntil();

        assertThat(send("DELETE", "/account/auto-renewal", ALICE, null).statusCode(), equalTo(NO_CONTENT));
        assertThat(send("DELETE", "/account/auto-renewal", ALICE, null).statusCode(), equalTo(NO_CONTENT));
        jdbcTemplate.update("update account_auto_renewal set next_charge_at = now() at time zone 'utc'"
                + " - interval '1 minute' where user_id = ?", ALICE);

        assertThat(renewalUseCase.renewDue(), equalTo(0));

        verify(tbankClient, never()).call(eq("Charge"), anyMap());
        // оплаченный срок остаётся
        final AccountPlanDto plan = plan(ALICE);
        assertThat(plan.getPlan().getId(), equalTo("premium"));
        assertThat(plan.getValidUntil(), equalTo(lastDay));
        assertThat(plan.getPayments().getAutoRenewal(), nullValue());
    }

    @Test
    void failedRenewalsDisableAutoRenewal() {
        pay(ALICE, "REBILL-1");
        final LocalDate lastDay = plan(ALICE).getValidUntil();
        when(tbankClient.call(eq("Charge"), anyMap())).thenAnswer(invocation -> {
            final Map<String, Object> request = invocation.getArgument(1);
            final Map<String, Object> declined = response("REJECTED", String.valueOf(request.get("PaymentId")),
                    null, "Message", "Недостаточно средств");
            declined.put("Success", false);
            declined.put("ErrorCode", "1051");
            return declined;
        });

        for (int attempt = 1; attempt <= 3; attempt++) {
            dueNow(ALICE);
            assertThat(renewalUseCase.renewDue(), equalTo(1));
            assertThat(count("select failed_attempts from account_auto_renewal where user_id = ?", ALICE),
                    equalTo((long) attempt));
        }

        assertThat(jdbcTemplate.queryForObject("select cancel_reason from account_auto_renewal where user_id = ?",
                String.class, ALICE), equalTo("PAYMENT_FAILED"));
        // неудачные списания тариф не меняют: он действует до конца оплаченного срока
        final AccountPlanDto plan = plan(ALICE);
        assertThat(plan.getPlan().getId(), equalTo("premium"));
        assertThat(plan.getValidUntil(), equalTo(lastDay));
        assertThat(plan.getPayments().getAutoRenewal(), nullValue());
        assertThat(history(ALICE).stream().filter(payment -> "FAILED".equals(payment.getStatus())).count(),
                equalTo(3L));
    }

    @Test
    void renewalWithUnknownResultIsCheckedBeforeNewCharge() {
        pay(ALICE, "REBILL-1");
        dueNow(ALICE);
        // списание ушло, ответа нет
        when(tbankClient.call(eq("Charge"), anyMap())).thenThrow(new PaymentProviderException("read timeout"));
        renewalUseCase.renewDue();
        assertThat(history(ALICE).get(0).getStatus(), equalTo("PENDING"));

        // следующий запуск не списывает ещё раз, а узнаёт итог прошлого списания
        when(tbankClient.call(eq("GetState"), anyMap())).thenAnswer(invocation -> {
            final Map<String, Object> request = invocation.getArgument(1);
            return response("CONFIRMED", String.valueOf(request.get("PaymentId")), null, "Amount", PREMIUM_KOPECKS);
        });
        renewalUseCase.renewDue();

        verify(tbankClient, times(1)).call(eq("Charge"), anyMap());
        assertThat(history(ALICE).get(0).getStatus(), equalTo("SUCCEEDED"));
        assertThat(plan(ALICE).getValidUntil(), equalTo(today().plusMonths(2)));
    }

    @Test
    void manualPlanChangeCancelsAutoRenewal() {
        pay(ALICE, "REBILL-1");

        final HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/maintenance/account/" + ALICE
                        + "/plan"))
                .header("From", "tester")
                .DELETE()
                .build();
        assertThat(sendRequest(request).statusCode(), equalTo(NO_CONTENT));

        assertThat(plan(ALICE).getPlan().getId(), equalTo("free"));
        assertThat(jdbcTemplate.queryForObject("select cancel_reason from account_auto_renewal where user_id = ?",
                String.class, ALICE), equalTo("MANUAL"));
    }

    @Test
    void refundRevokesPaidPlan() {
        final PaymentDto payment = pay(ALICE, "REBILL-1");

        notify(notification(payment, "REFUNDED", true, null));

        final AccountPlanDto plan = plan(ALICE);
        assertThat(plan.getPlan().getId(), equalTo("free"));
        assertThat(plan.getPayments().getAutoRenewal(), nullValue());
        assertThat(payment(ALICE, payment.getId()).getStatus(), equalTo("REFUNDED"));
    }

    @Test
    void maintenanceListsPaymentsAndRunsRenewals() {
        pay(ALICE, null);

        final HttpResponse<String> payments = sendRequest(HttpRequest.newBuilder(
                URI.create(BASE_URL + "/maintenance/payments/users/" + ALICE)).GET().build());
        assertThat(payments.statusCode(), equalTo(OK));
        assertThat(jsonMapper.readValue(payments.body(), PAYMENTS).size(), equalTo(1));
        final HttpResponse<String> renewals = sendRequest(HttpRequest.newBuilder(
                URI.create(BASE_URL + "/maintenance/payments/renewals")).POST(HttpRequest.BodyPublishers.noBody())
                .build());
        assertThat(renewals.body(), equalTo("{\"processed\":0}"));
    }

    /** Покупка и успешный webhook; rebillId null - карта не сохранена */
    private PaymentDto pay(final String userId, final String rebillId) {
        final PaymentDto payment = purchase(userId, "premium");
        assertThat(notify(notification(payment, "CONFIRMED", true, rebillId)).body(), equalTo("OK"));
        return payment;
    }

    private void dueNow(final String userId) {
        jdbcTemplate.update("update account_auto_renewal set next_charge_at = now() at time zone 'utc'"
                + " - interval '1 minute' where user_id = ? and cancelled_at is null", userId);
    }

    private PaymentDto purchase(final String userId, final String plan) {
        final HttpResponse<String> response = send("POST", "/account/payments", userId, Map.of("plan", plan));
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        return jsonMapper.readValue(response.body(), PaymentDto.class);
    }

    private Map<String, Object> notification(final PaymentDto payment, final String status, final boolean success,
                                             final String rebillId) {
        final String orderId = jdbcTemplate.queryForObject("select order_id from payment where id = ?", String.class,
                payment.getId());
        final String providerId = jdbcTemplate.queryForObject("select provider_payment_id from payment where id = ?",
                String.class, payment.getId());
        final Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("TerminalKey", TERMINAL);
        notification.put("OrderId", orderId);
        notification.put("Success", success);
        notification.put("Status", status);
        // в webhook Т-Кассы PaymentId - число
        notification.put("PaymentId", Long.parseLong(providerId));
        notification.put("ErrorCode", "0");
        notification.put("Amount", PREMIUM_KOPECKS);
        if (rebillId != null) {
            notification.put("RebillId", rebillId);
        }
        notification.put("CardId", 1234);
        notification.put("Pan", PAN);
        notification.put("ExpDate", "1130");
        notification.put("Token", TbankSignature.token(notification, PASSWORD));
        return notification;
    }

    private static Map<String, Object> response(final String status, final String paymentId, final Object orderId,
                                                final String extraKey, final Object extraValue) {
        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("Success", true);
        response.put("ErrorCode", "0");
        response.put("TerminalKey", TERMINAL);
        response.put("Status", status);
        response.put("PaymentId", paymentId);
        if (orderId != null) {
            response.put("OrderId", orderId);
        }
        response.put("Amount", PREMIUM_KOPECKS);
        response.put(extraKey, extraValue);
        return response;
    }

    @SneakyThrows
    private HttpResponse<String> notify(final Map<String, Object> notification) {
        return sendRaw("/payments/notifications/tbank", jsonMapper.writeValueAsString(notification));
    }

    private AccountPlanDto plan(final String userId) {
        final HttpResponse<String> response = send("GET", "/account/plan", userId, null);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), AccountPlanDto.class);
    }

    private PaymentDto payment(final String userId, final long id) {
        final HttpResponse<String> response = send("GET", "/account/payments/" + id, userId, null);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), PaymentDto.class);
    }

    private List<PaymentDto> history(final String userId) {
        final HttpResponse<String> response = send("GET", "/account/payments", userId, null);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), PAYMENTS);
    }

    private long count(final String sql, final String userId) {
        return jdbcTemplate.queryForObject(sql, Long.class, userId);
    }

    private static LocalDate today() {
        return LocalDate.now(MOSCOW);
    }

    @SneakyThrows
    private HttpResponse<String> send(final String method, final String path, final String userId,
                                      final Object body) {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));
        if (userId != null) {
            request.header(USER_ID_HEADER, userId);
        }
        return sendRequest(request.build());
    }

    private HttpResponse<String> sendRaw(final String path, final String body) {
        return sendRequest(HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    @SneakyThrows
    private HttpResponse<String> sendRequest(final HttpRequest request) {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
