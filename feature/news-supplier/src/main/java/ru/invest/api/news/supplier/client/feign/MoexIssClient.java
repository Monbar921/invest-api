package ru.invest.api.news.supplier.client.feign;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.invest.api.news.supplier.model.IssTableDto;

import java.util.Map;

/**
 * ISS API Московской биржи (iss.moex.com): бесплатный, без ключа. Ответ - таблицы «колонки + строки» по имени блока.
 * Параметры передаются картой: iss.meta=off убирает описание колонок из ответа.
 */
public interface MoexIssClient {
    /**
     * Новости биржи, новые первыми, по 50 на страницу: блок sitenews (id, tag, title, published_at, modified_at).
     */
    @GetMapping("/iss/sitenews.json")
    Map<String, IssTableDto> getSiteNews(@RequestParam Map<String, String> parameters);

    /**
     * Текст новости: блок content (id, title, published_at, body), body - HTML.
     */
    @GetMapping("/iss/sitenews/{id}.json")
    Map<String, IssTableDto> getSiteNewsContent(@PathVariable("id") long id, @RequestParam Map<String, String> parameters);

    /**
     * Бумаги с эмитентами: блок securities, по 100 на страницу.
     */
    @GetMapping("/iss/securities.json")
    Map<String, IssTableDto> getSecurities(@RequestParam Map<String, String> parameters);
}
