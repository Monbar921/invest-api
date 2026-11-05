package ru.invest.api.common.usecase.impl;

import org.springframework.stereotype.Service;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.event.BondEventModel;
import ru.invest.api.common.model.event.BondEventType;
import ru.invest.api.common.usecase.BondEventUseCase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BondEventUseCaseImpl implements BondEventUseCase {

    @Override
    public List<BondEventModel> getEvents(final BondModel bond, final LocalDate from) {
        final List<BondEventModel> events = new ArrayList<>(coupons(bond, from));

        final List<AmortizationModel> amortizations = Optional.ofNullable(bond.getAmortizations()).orElse(List.of());
        amortizations.stream()
                .filter(amortization -> amortization.getPaymentDate() != null)
                .filter(amortization -> !amortization.getPaymentDate().toLocalDate().isBefore(from))
                .map(amortization -> new BondEventModel()
                        .setType(BondEventType.AMORTIZATION)
                        .setDate(amortization.getPaymentDate().toLocalDate())
                        .setAmount(known(amortization.getPrice())))
                .forEach(events::add);

        date(bond.getOfferDate())
                .filter(offer -> !offer.isBefore(from))
                .ifPresent(offer -> events.add(new BondEventModel().setType(BondEventType.OFFER).setDate(offer)));

        date(bond.getMaturityDate())
                .filter(maturity -> !maturity.isBefore(from))
                .ifPresent(maturity -> events.add(new BondEventModel()
                        .setType(BondEventType.MATURITY)
                        .setDate(maturity)
                        .setAmount(maturityAmount(bond, maturity, amortizations))));

        // в один день купон идёт перед погашением: так их и выплачивают
        return events.stream()
                .sorted(Comparator.comparing(BondEventModel::getDate).thenComparing(BondEventModel::getType))
                .toList();
    }

    /**
     * Купоны с даты from. Размер сравнивается с прошлой известной выплатой, в том числе раньше from: так видно,
     * что у плавающего купона пересмотрели ставку. Нулевой размер - ещё не объявлен.
     */
    private List<BondEventModel> coupons(final BondModel bond, final LocalDate from) {
        final List<CouponDataModel> schedule = Optional.ofNullable(bond.getCoupon())
                .map(CouponModel::getCouponData)
                .orElse(List.of())
                .stream()
                .filter(coupon -> coupon.getPaymentDate() != null)
                .sorted(Comparator.comparing(CouponDataModel::getPaymentDate))
                .toList();

        final List<BondEventModel> events = new ArrayList<>();
        MoneyModel previous = null;
        for (final CouponDataModel coupon : schedule) {
            final MoneyModel amount = known(coupon.getPrice());
            final LocalDate date = coupon.getPaymentDate().toLocalDate();
            if (!date.isBefore(from)) {
                events.add(new BondEventModel()
                        .setType(BondEventType.COUPON)
                        .setDate(date)
                        .setAmount(amount)
                        .setPreviousAmount(changed(previous, amount) ? previous : null));
            }
            if (amount != null) {
                previous = amount;
            }
        }
        return events;
    }

    private boolean changed(final MoneyModel previous, final MoneyModel current) {
        return previous != null && current != null
                && Objects.equals(previous.getCurrency(), current.getCurrency())
                && previous.getQuantity().compareTo(current.getQuantity()) != 0;
    }

    /**
     * Погашение: что осталось от номинала. Если в день погашения есть запись амортизации, сумма уже в ней.
     */
    private MoneyModel maturityAmount(final BondModel bond, final LocalDate maturity,
                                      final List<AmortizationModel> amortizations) {
        final Set<LocalDate> amortizationDates = amortizations.stream()
                .map(AmortizationModel::getPaymentDate)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .collect(Collectors.toSet());
        if (amortizationDates.contains(maturity)) {
            return null;
        }
        return Optional.ofNullable(bond.getPrice()).map(PriceModel::getNominal).map(this::known).orElse(null);
    }

    private MoneyModel known(final MoneyModel money) {
        return money == null || money.getQuantity() == null || money.getQuantity().signum() <= 0 ? null : money;
    }

    private Optional<LocalDate> date(final LocalDateTime dateTime) {
        return Optional.ofNullable(dateTime).map(LocalDateTime::toLocalDate);
    }
}
