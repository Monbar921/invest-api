package ru.invest.api.cb.rf.supplier.client.feign;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;

public interface CbRfClient {
    //    date_req format is dd/MM/yyyy
    @GetMapping(value = "/scripts/XML_daily.asp", consumes = "application/xml")
    CurrencyDto getCurrencyRates(@RequestParam("date_req") String date);
}
