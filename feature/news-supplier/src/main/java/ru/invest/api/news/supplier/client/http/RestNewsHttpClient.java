package ru.invest.api.news.supplier.client.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class RestNewsHttpClient implements NewsHttpClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    // часть сайтов отвечает 403 на запросы без User-Agent
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; invest-api news reader)";

    private final RestClient restClient;

    public RestNewsHttpClient() {
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    @Override
    public byte[] get(final String url) {
        return restClient.get().uri(url).retrieve().body(byte[].class);
    }
}
