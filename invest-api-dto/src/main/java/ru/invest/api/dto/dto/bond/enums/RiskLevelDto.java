package ru.invest.api.dto.dto.bond.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum RiskLevelDto {
    RISK_LEVEL_UNSPECIFIED(0),
    RISK_LEVEL_LOW(1),
    RISK_LEVEL_MODERATE(2),
    RISK_LEVEL_HIGH(3),
    UNRECOGNIZED(-1);

    private final int value;
}
