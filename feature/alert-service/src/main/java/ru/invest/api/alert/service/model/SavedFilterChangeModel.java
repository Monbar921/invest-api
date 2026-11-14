package ru.invest.api.alert.service.model;

import ru.invest.api.bond.supplier.model.BondScope;
import ru.invest.api.common.model.parameters.BondParametersRequestModel;

/**
 * Новый фильтр или изменение; при изменении null - не менять.
 *
 * @param parameters при изменении - заменяют прежние целиком
 * @param notifications включить или выключить уведомления
 */
public record SavedFilterChangeModel(String name,
                                     BondScope scope,
                                     BondParametersRequestModel parameters,
                                     Boolean notifications) {
}
