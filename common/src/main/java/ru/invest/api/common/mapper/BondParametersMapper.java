package ru.invest.api.common.mapper;

import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.enums.RiskLevel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.BondParametersRequestModel;
import ru.invest.api.common.model.parameters.BondSortField;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.model.parameters.BondSortOrder;
import ru.invest.api.common.model.parameters.ValueRangeModel;
import ru.invest.api.common.usecase.BondComparatorUseCase;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Mapper(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public abstract class BondParametersMapper {
    private static final int DEFAULT_BATCH_LIMIT = 100;

    private static final List<BondSortModel> DEFAULT_SORTS = List.of(
            new BondSortModel()
                    .setSortField(BondSortField.PRICE)
                    .setSortOrder(BondSortOrder.ASC)
    );

    @Setter(onMethod_ = @Autowired)
    private BondComparatorUseCase bondComparatorUseCase;

    @Mapping(target = "comparator", source = "bondSorts", qualifiedByName = "toComparator")
    @Mapping(target = "bondSorts", source = "bondSorts", qualifiedByName = "toBondSorts")
    @Mapping(target = "batchLimit", source = "batchLimit", qualifiedByName = "toBatchLimit")
    @Mapping(target = "currentPrice", source = "currentPrice")
    @Mapping(target = "percentagePrice", source = "percentagePrice")
    @Mapping(target = "riskLevels", source = "riskLevels")
    @Mapping(target = "isOfz", source = "isOfz")
    public abstract BondParametersModel toModel(Integer batchLimit, List<BondSortModel> bondSorts
            , ValueRangeModel currentPrice, ValueRangeModel percentagePrice, List<RiskLevel> riskLevels, Boolean isOfz);

    @Mapping(target = "comparator", source = "bondSorts", qualifiedByName = "toComparator")
    @Mapping(target = "bondSorts", source = "bondSorts", qualifiedByName = "toBondSorts")
    @Mapping(target = "batchLimit", source = "batchLimit", qualifiedByName = "toBatchLimit")
    @Mapping(target = "currentPrice", source = "currentPrice")
    @Mapping(target = "percentagePrice", source = "percentagePrice")
    public abstract BondParametersModel toModel(BondParametersRequestModel bondParametersRequestModel);

    public BondParametersModel toModel(final BondParametersModel bondParametersModel, final List<BondSortModel> sorts) {
        return toModel(getter(bondParametersModel, BondParametersModel::getBatchLimit), sorts,
                getter(bondParametersModel, BondParametersModel::getCurrentPrice),
                getter(bondParametersModel, BondParametersModel::getPercentagePrice),
                getter(bondParametersModel, BondParametersModel::getRiskLevels),
                getter(bondParametersModel, BondParametersModel::getIsOfz));
    }

    public BondParametersModel toModel(final BondParametersModel bondParametersModel) {
        if (bondParametersModel == null || bondParametersModel.getComparator() == null
                || CollectionUtils.isEmpty(bondParametersModel.getBondSorts())) {
            return toModel(bondParametersModel, getter(bondParametersModel, BondParametersModel::getBondSorts));
        }

        return bondParametersModel;
    }

    @Named("toBondSorts")
    protected List<BondSortModel> toBondSorts(final List<BondSortModel> bondSorts) {
        return Optional.ofNullable(bondSorts)
                .filter(CollectionUtils::isNotEmpty)
                .orElse(DEFAULT_SORTS);
    }

    @Named("toComparator")
    protected Comparator<BondModel> toComparator(final List<BondSortModel> bondSorts) {
        final List<BondSortModel> resultSorts = toBondSorts(bondSorts);
        return bondComparatorUseCase.createComparator(resultSorts);
    }

    @Named("toBatchLimit")
    protected Integer toBatchLimit(final Integer batchLimit) {
        return Optional.ofNullable(batchLimit)
                .orElse(DEFAULT_BATCH_LIMIT);
    }

    private <O, T> T getter(final O object, final Function<O, T> getterFunction) {
        return Optional.ofNullable(object)
                .map(getterFunction)
                .orElse(null);
    }
}
