package ru.invest.api.dto.filter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.dto.request.bond.BondParametersRequest;

/**
 * Новый сохранённый фильтр.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SavedFilterCreateRequest {
    public static final int MAX_NAME = 100;

    @NotBlank(message = "name must not be blank")
    @Size(max = MAX_NAME, message = "name must not exceed 100 characters")
    private String name;
    /** null - ALL */
    private BondScopeDto scope;
    /** null - без фильтров */
    @Valid
    private BondParametersRequest parameters;
    /** true - сразу включить уведомления о новых облигациях в выборке */
    private Boolean notify;
}
