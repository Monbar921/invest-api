package ru.invest.api.stock.supplier.wrapper;

import ru.tinkoff.piapi.contract.v1.BondsResponse;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;

public interface InstrumentsGrpcRateLimitedWrapper {
    BondsResponse bonds(InstrumentsRequest request);

    GetBondCouponsResponse getBondCoupons(GetBondCouponsRequest request);
}
