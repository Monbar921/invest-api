package ru.invest.api.common.mapper;

import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper
public abstract class BigDecimalMapper {
    private static final int SCALE = 9;
    private static final BigDecimal NANO_DIVISOR = BigDecimal.valueOf(1_000_000_000);

    public BigDecimal fromBaseAndNanoFloatParts(final long basePart, final int nanoFloatPart) {
        final BigDecimal base = BigDecimal.valueOf(basePart);
        final BigDecimal nano = BigDecimal.valueOf(nanoFloatPart);

        return base.add(nano.divide(NANO_DIVISOR, SCALE, RoundingMode.FLOOR));
    }
}
