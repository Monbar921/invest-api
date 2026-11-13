package ru.invest.api.payment.service.provider.tbank;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.invest.api.payment.service.config.PaymentProperties;
import ru.invest.api.payment.service.provider.PaymentProviderException;

import java.util.Map;

/**
 * HTTP к API v2 Т-Кассы: POST {api-url}/{метод} с JSON. Подпись ставит вызывающий. В тестах замокан.
 */
@Component
public class TbankClient {
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public TbankClient(final PaymentProperties properties) {
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTbank().getConnectTimeout());
        requestFactory.setReadTimeout(properties.getTbank().getReadTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.getTbank().getApiUrl())
                .build();
    }

    /**
     * @param method Init, Charge, GetState
     * @throws PaymentProviderException нет ответа или ответ не JSON
     */
    public Map<String, Object> call(final String method, final Map<String, Object> request) {
        try {
            final Map<String, Object> response = restClient.post()
                    .uri("/{method}", method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RESPONSE);
            if (response == null) {
                throw new PaymentProviderException("T-Bank %s: empty response".formatted(method));
            }
            return response;
        } catch (final RestClientException e) {
            throw new PaymentProviderException("T-Bank %s failed: %s".formatted(method, e.getMessage()), e);
        }
    }
}
