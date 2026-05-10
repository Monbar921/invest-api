package ru.invest.api.common.model.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RiskLevel {
    RISK_LEVEL_UNSPECIFIED(0),
    RISK_LEVEL_LOW(1),
    RISK_LEVEL_MODERATE(2),
    RISK_LEVEL_HIGH(3),
    UNRECOGNIZED(-1);

    private final int value;
}
