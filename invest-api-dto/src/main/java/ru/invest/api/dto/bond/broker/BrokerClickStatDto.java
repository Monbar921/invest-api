package ru.invest.api.dto.bond.broker;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * Переходы к брокеру-партнёру по облигации за день (по Москве).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BrokerClickStatDto {
    private LocalDate date;
    private String partnerId;
    private String ticker;
    private long clicks;
}
