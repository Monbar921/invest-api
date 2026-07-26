package ru.invest.api.tinkoff.supplier.usecase;

import java.util.List;

public interface CacheKeyGenerator  {
    String createKey(List<String> values);
}
