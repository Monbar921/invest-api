package ru.invest.api.tinkoff.supplier.usecase.impl;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.tinkoff.supplier.usecase.CacheKeyGenerator;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CacheKeyGeneratorImpl implements CacheKeyGenerator {
    @Override
    public String createKey(final List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }

        return values
                .stream()
                .filter(StringUtils::isNotBlank)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
