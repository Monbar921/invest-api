package ru.invest.api.common.predicates;

import ru.invest.api.common.model.BondModel;

import java.util.Optional;
import java.util.function.Predicate;

public interface BondModelPredicates {
    Predicate<BondModel> OFZ_PREDICATE = bond -> Optional.ofNullable(bond)
            .filter(b -> bond.getSector().equalsIgnoreCase("GOVERNMENT"))
            .filter(b -> bond.getName().toUpperCase().startsWith("ОФЗ")
                    || bond.getName().toUpperCase().startsWith("RUSSIA")
                    || bond.getName().toUpperCase().startsWith("ОВОЗ")
            )
            .isPresent();
}
