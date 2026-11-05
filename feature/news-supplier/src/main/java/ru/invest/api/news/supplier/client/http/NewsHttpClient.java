package ru.invest.api.news.supplier.client.http;

/**
 * GET на страницы и RSS-ленты с разных сайтов (СМИ, рейтинговые агентства) - у каждого свой адрес, поэтому не Feign.
 */
public interface NewsHttpClient {
    /**
     * Тело ответа байтами: кодировку XML разбирает парсер, HTML - jsoup по заголовку и meta.
     */
    byte[] get(String url);
}
