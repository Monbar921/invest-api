package ru.invest.api.dto.filter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.dto.request.bond.BondParametersRequest;

/**
 * Изменение сохранённого фильтра; незаданные поля не меняются.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SavedFilterUpdateRequest {
    @Pattern(regexp = "(?s).*\\S.*", message = "name must not be blank")
    @Size(max = SavedFilterCreateRequest.MAX_NAME, message = "name must not exceed 100 characters")
    private String name;
    private BondScopeDto scope;
    /** Новые фильтры и сортировка целиком (не слияние с прежними) */
    @Valid
    private BondParametersRequest parameters;
    /** Включить или выключить уведомления о новых облигациях в выборке */
    private Boolean notify;
}
