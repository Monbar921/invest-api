package ru.invest.api.dto.portfolio.imports;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Set;

/** Подтверждение импорта: какие строки предпросмотра создать */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ImportApplyRequest {
    @NotNull(message = "itemIds is required")
    private Set<Long> itemIds;
}
