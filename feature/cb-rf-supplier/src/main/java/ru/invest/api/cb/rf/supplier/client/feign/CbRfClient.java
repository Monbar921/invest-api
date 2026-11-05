package ru.invest.api.cb.rf.supplier.client.feign;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;

public interface CbRfClient {
    //    date_req format is dd/MM/yyyy
    @GetMapping(value = "/scripts/XML_daily.asp", consumes = "application/xml")
    CurrencyDto getCurrencyRates(@RequestParam("date_req") String date);

    /**
     * RSS пресс-релизов. Байты, а не строка: кодировку и BOM в начале файла разбирает XML-парсер.
     */
    @GetMapping("/rss/RssPress")
    byte[] getPressReleasesRss();

    /**
     * RSS «События и комментарии».
     */
    @GetMapping("/rss/eventrss")
    byte[] getEventsRss();

    /**
     * SOAP-сервис DailyInfo: в теле - конверт с вызываемым методом, в ответе - XML с данными.
     */
    @PostMapping(value = "/DailyInfoWebServ/DailyInfo.asmx", consumes = "text/xml; charset=utf-8")
    byte[] callDailyInfo(@RequestHeader("SOAPAction") String soapAction, @RequestBody String envelope);
}
