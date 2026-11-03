package ru.invest.api.tinkoff.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper(uses = {TinkoffPriceApiMapper.class, DateTimeMapper.class})
public interface TinkoffBondApiMapper {

    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "logged", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "ticker", source = "ticker")
    @Mapping(target = "uid", source = "uid")
    @Mapping(target = "isin", source = "isin")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "sector", source = "sector")
    @Mapping(target = "riskLevel", source = "riskLevel")
    @Mapping(target = "coupon", source = "bond", qualifiedByName = "toInitialCoupon")
    @Mapping(target = "maturityDate", source = "maturityDate")
    @Mapping(target = "offerDate", source = "bond", qualifiedByName = "toOfferDate")
    @Mapping(target = "amortizationFlag", source = "amortizationFlag")
    @Mapping(target = "amortizations", ignore = true)
    @Mapping(target = "price", source = "bond")
    BondModel toModel(Bond bond);

    /**
     * Дата оферты; у облигаций без оферты call_date не заполнен.
     */
    @Named("toOfferDate")
    default LocalDateTime toOfferDate(final Bond bond) {
        if (bond == null || !bond.hasCallDate() || bond.getCallDate().getSeconds() <= 0) {
            return null;
        }

        return LocalDateTime.ofEpochSecond(bond.getCallDate().getSeconds(), bond.getCallDate().getNanos(), ZoneOffset.UTC);
    }

    @Named("toInitialCoupon")
    default CouponModel toInitialCoupon(final Bond bond) {
        if (bond == null) {
            return null;
        }

        return new CouponModel()
                .setQuantityPerYear(bond.getCouponQuantityPerYear())
                .setIsFixed(!bond.getFloatingCouponFlag());
    }
}
