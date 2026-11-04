package ru.invest.api.feature.ui.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.tinkoff.supplier.wrapper.MarketDataGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetCandlesResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Sql(scripts = "/sql/bonds-for-controller.sql")
public class MaintenanceControllerTest extends AbstractInvestApplicationTest {
    private static final String CANDLE_SYNC_ALL = "http://localhost:8099/internal/rest/maintenance/bond/candle/sync-all";
    private static final int ACCEPTED = 202;
    private static final int CONFLICT = 409;

    private final HttpClient client = HttpClient.newHttpClient();

    @Autowired
    private MarketDataGrpcRateLimitedWrapper marketDataGrpcWrapper;

    @Test
    public void syncAllRunsInBackgroundTest() throws Exception {
        // запрос к API «висит», пока тест его не отпустит - синхронизация идёт, а HTTP-ответ уже пришёл
        final CountDownLatch release = new CountDownLatch(1);
        when(marketDataGrpcWrapper.getCandles(any())).thenAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS);
            return GetCandlesResponse.getDefaultInstance();
        });

        final HttpResponse<String> first = syncAll();
        assertThat(first.statusCode(), equalTo(ACCEPTED));
        verify(marketDataGrpcWrapper, timeout(5000)).getCandles(any());

        // та же задача ещё идёт - второй запуск отклоняется, а не идёт параллельно
        final HttpResponse<String> second = syncAll();
        assertThat(second.statusCode(), equalTo(CONFLICT));
        assertThat(second.body(), containsString("candle sync"));

        release.countDown();
        // после завершения задачу снова можно запустить
        awaitAccepted();
    }

    private void awaitAccepted() throws Exception {
        final long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        int status;
        do {
            status = syncAll().statusCode();
        } while (status == CONFLICT && System.nanoTime() < deadline);
        assertThat(status, equalTo(ACCEPTED));
    }

    private HttpResponse<String> syncAll() throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(CANDLE_SYNC_ALL))
                        .header("From", "test")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
