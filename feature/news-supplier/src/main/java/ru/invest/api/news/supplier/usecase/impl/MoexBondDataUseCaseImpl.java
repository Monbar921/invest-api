package ru.invest.api.news.supplier.usecase.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.invest.api.news.supplier.client.feign.MoexIssClient;
import ru.invest.api.news.supplier.model.IssTableDto;
import ru.invest.api.news.supplier.model.OfzYieldCurve;
import ru.invest.api.news.supplier.usecase.MoexBondDataUseCase;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ru.invest.api.common.constants.CacheConstants.OFZ_CURVE_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.OFZ_CURVE_CACHE_NAME;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoexBondDataUseCaseImpl implements MoexBondDataUseCase {
    private static final int G_PARAMETERS = 9;

    private final MoexIssClient moexIssClient;

    @Override
    @Cacheable(value = OFZ_CURVE_CACHE_NAME, cacheManager = OFZ_CURVE_CACHE_MANAGER, key = "'curve'", unless = "#result == null")
    public Optional<OfzYieldCurve> getOfzCurve() {
        try {
            final List<Map<String, Object>> params = block(moexIssClient.getZeroCouponCurve(Map.of("iss.meta", "off",
                    "iss.only", "params")), "params");
            // строки по времени расчёта; последняя - самая свежая
            return params.isEmpty() ? Optional.empty() : toCurve(params.getLast());
        } catch (final FeignException e) {
            log.warn("MOEX zero-coupon curve is not loaded: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Map<String, String> getCouponDetails() {
        try {
            final Map<String, String> details = new HashMap<>();
            block(moexIssClient.getBondMarketSecurities(Map.of("iss.meta", "off", "iss.only", "securities",
                    "securities.columns", "ISIN,COUPON_DETAILS")), "securities")
                    .forEach(row -> {
                        final String isin = (String) row.get("ISIN");
                        final String couponDetails = (String) row.get("COUPON_DETAILS");
                        if (StringUtils.isNoneBlank(isin, couponDetails)) {
                            // бумага торгуется в нескольких режимах - строк с одним ISIN бывает несколько
                            details.putIfAbsent(isin, couponDetails.trim());
                        }
                    });
            return details;
        } catch (final FeignException e) {
            log.warn("MOEX bond coupon details are not loaded: {}", e.getMessage());
            return Map.of();
        }
    }

    private Optional<OfzYieldCurve> toCurve(final Map<String, Object> row) {
        final double[] g = new double[G_PARAMETERS];
        for (int i = 0; i < G_PARAMETERS; i++) {
            final Double value = number(row, "G" + (i + 1));
            if (value == null) {
                return Optional.empty();
            }
            g[i] = value;
        }
        final Double b1 = number(row, "B1");
        final Double b2 = number(row, "B2");
        final Double b3 = number(row, "B3");
        final Double t1 = number(row, "T1");
        if (b1 == null || b2 == null || b3 == null || t1 == null || t1 <= 0) {
            return Optional.empty();
        }
        return Optional.of(new OfzYieldCurve(date(row.get("tradedate")), b1, b2, b3, t1, g));
    }

    private Double number(final Map<String, Object> row, final String column) {
        return row.get(column) instanceof Number number ? number.doubleValue() : null;
    }

    private LocalDate date(final Object value) {
        try {
            return value == null ? null : LocalDate.parse(value.toString());
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    private List<Map<String, Object>> block(final Map<String, IssTableDto> response, final String name) {
        return Optional.ofNullable(response)
                .map(blocks -> blocks.get(name))
                .map(IssTableDto::rows)
                .orElse(List.of());
    }
}
