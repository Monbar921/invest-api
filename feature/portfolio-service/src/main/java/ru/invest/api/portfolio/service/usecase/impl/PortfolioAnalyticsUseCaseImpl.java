package ru.invest.api.portfolio.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.BondIssuer;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.rating.CreditRatingModel;
import ru.invest.api.common.repository.BondIssuerRepository;
import ru.invest.api.news.supplier.usecase.impl.IssuerTitles;
import ru.invest.api.portfolio.service.model.AnalyticsPositionModel;
import ru.invest.api.portfolio.service.model.PortfolioAnalyticsModel;
import ru.invest.api.portfolio.service.model.PortfolioGroupModel;
import ru.invest.api.portfolio.service.model.UnvaluedReason;
import ru.invest.api.portfolio.service.usecase.PortfolioAnalyticsUseCase;
import ru.invest.api.portfolio.service.usecase.PortfolioUseCase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Позиции и их стоимость в рублях - те же, что в портфеле (открытые строки, цена без НКД, курс ЦБ на сегодня);
 * данные облигаций - как в карточке: рейтинг, дюрация, доходность к погашению по текущей цене.
 */
@Service
@RequiredArgsConstructor
public class PortfolioAnalyticsUseCaseImpl implements PortfolioAnalyticsUseCase {
    private final PortfolioUseCase portfolioUseCase;
    private final BondUseCase bondUseCase;
    private final BondIssuerRepository bondIssuerRepository;

    @Override
    @Transactional(readOnly = true)
    public PortfolioAnalyticsModel getAnalytics(final String userId) {
        final List<PortfolioGroupModel> groups = portfolioUseCase.getPortfolio(userId, null).groups();
        final Set<String> tickers = groups.stream().map(PortfolioGroupModel::ticker).collect(Collectors.toSet());
        final Set<String> known = tickers.isEmpty() ? Set.of() : bondUseCase.getByTickers(tickers).keySet();
        // карточка - с данными скринера (рейтинг) и дюрацией; облигации, которой нет, - не грузим
        final Map<String, BondModel> bonds = known.stream()
                .collect(Collectors.toMap(Function.identity(), bondUseCase::getByTicker));
        final Set<String> isins = bonds.values().stream().map(BondModel::getIsin).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final Map<String, BondIssuer> issuers = isins.isEmpty()
                ? Map.of()
                : bondIssuerRepository.findByIsinIn(isins).stream()
                        .filter(issuer -> issuer.getEmitterId() != null)
                        .collect(Collectors.toMap(BondIssuer::getIsin, Function.identity(), (first, second) -> first));
        return PortfolioAnalyticsCalculator.calculate(groups.stream()
                .map(group -> toPosition(group, bonds.get(group.ticker()), issuers))
                .toList());
    }

    private static AnalyticsPositionModel toPosition(final PortfolioGroupModel group, final BondModel bond,
                                                     final Map<String, BondIssuer> issuers) {
        final BondIssuer issuer = bond == null || bond.getIsin() == null ? null : issuers.get(bond.getIsin());
        final CreditRatingModel rating = bond == null ? null : bond.getCreditRating();
        final CouponModel coupon = bond == null ? null : bond.getCoupon();
        return new AnalyticsPositionModel(group.ticker(), group.bondName(), group.currentAmountInRub(),
                unvaluedReason(group, bond),
                issuer == null ? null : issuer.getEmitterId(),
                issuer == null ? null : Optional.ofNullable(IssuerTitles.shortTitle(issuer.getTitle()))
                        .filter(title -> !title.isBlank()).orElse(null),
                bond == null ? null : bond.getSector(),
                rating == null ? null : rating.getGrade(),
                rating == null ? null : rating.getScore(),
                group.currency(),
                coupon == null ? null : coupon.getIsFixed(),
                bond == null ? null : Optional.ofNullable(bond.getMaturityDate()).map(LocalDateTime::toLocalDate)
                        .orElse(null),
                coupon == null ? null : coupon.getYieldToMaturity(),
                coupon == null ? null : coupon.getDurationMacaulay(),
                coupon == null ? null : coupon.getDurationModified());
    }

    private static UnvaluedReason unvaluedReason(final PortfolioGroupModel group, final BondModel bond) {
        if (group.currentAmountInRub() != null) {
            return null;
        }
        if (bond == null) {
            return UnvaluedReason.UNKNOWN_BOND;
        }
        return group.currentAmount() == null ? UnvaluedReason.NO_PRICE : UnvaluedReason.NO_RATE;
    }
}
