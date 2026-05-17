package ru.invest.api.cb.rf.currency.client.feign;

import org.springframework.web.bind.annotation.GetMapping;

public interface CbRfClient {
    @GetMapping(value = "/XML_daily.asp", consumes = "application/xml")
    ValCurs getCurrencyRates();
}
