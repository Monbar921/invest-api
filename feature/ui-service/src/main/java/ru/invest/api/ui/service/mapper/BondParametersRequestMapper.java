package ru.invest.api.ui.service.mapper;

import lombok.Setter;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.mapper.BondParametersMapper;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.BondParametersRequestModel;
import ru.invest.api.common.model.parameters.PriceRequestModel;
import ru.invest.api.dto.request.bond.BondParametersRequest;
import ru.invest.api.dto.request.bond.PriceRequest;

@Mapper
public abstract class BondParametersRequestMapper {
    @Setter(onMethod_ = @Autowired)
    private BondParametersMapper bondParametersMapper;

    public BondParametersModel toModel(final Integer batchLimit, final BondParametersRequest bondParametersRequest) {
        return toBondParametersModel(
                toBondParametersRequestModel(batchLimit, bondParametersRequest)
        );
    }

    protected abstract BondParametersRequestModel toBondParametersRequestModel(Integer batchLimit, BondParametersRequest bondParametersRequest);

    protected BondParametersModel toBondParametersModel(BondParametersRequestModel bondParametersRequestModel) {
        return bondParametersMapper.toModel(bondParametersRequestModel);
    }

    protected abstract PriceRequestModel toPriceRequestModel(PriceRequest priceRequest);
}
