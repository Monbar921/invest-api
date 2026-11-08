package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Брокер пользователя - метка покупки в портфеле.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BrokerDto {
    private Long id;
    private String name;
}
