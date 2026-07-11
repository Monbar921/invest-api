package ru.invest.api.common.mapper;

import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.BondParametersRequestModel;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.usecase.BondComparatorUseCase;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Mapper(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public abstract class BondParametersMapper {
    private static final int DEFAULT_BATCH_LIMIT = 50;

    @Setter(onMethod_ = @Autowired)
    private BondComparatorUseCase bondComparatorUseCase;

    @Mapping(target = "comparator", source = "bondSorts", qualifiedByName = "toComparator")
    @Mapping(target = "batchLimit", source = "batchLimit", qualifiedByName = "toBatchLimit")
    public abstract BondParametersModel toModel(Integer batchLimit, List<BondSortModel> bondSorts);

    @Mapping(target = "comparator", source = "bondSorts", qualifiedByName = "toComparator")
    @Mapping(target = "batchLimit", source = "batchLimit", qualifiedByName = "toBatchLimit")
    public abstract BondParametersModel toModel(BondParametersRequestModel bondParametersRequestModel);

    public BondParametersModel toModel(final BondParametersModel bondParametersModel, final List<BondSortModel> sorts) {
        return toModel(getter(bondParametersModel, BondParametersModel::getBatchLimit), sorts);
    }

    public BondParametersModel toModel(final BondParametersModel bondParametersModel) {
        if (bondParametersModel == null || CollectionUtils.isEmpty(bondParametersModel.getBondSorts())) {
            return toModel(getter(bondParametersModel, BondParametersModel::getBatchLimit),
                    getter(bondParametersModel, BondParametersModel::getBondSorts));
        }

        return bondParametersModel;
    }

    @Named("toComparator")
    protected Comparator<BondModel> toComparator(final List<BondSortModel> bondSorts) {
        return bondComparatorUseCase.createComparator(bondSorts);
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
