package ru.invest.api.portfolio.service.usecase.impl;

import ru.invest.api.common.entity.PortfolioPosition;
import ru.invest.api.common.entity.PortfolioSale;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Какие покупки закрывает каждая продажа одной облигации - по FIFO: продажа забирает облигации из самых ранних
 * покупок, сделанных не позже её даты. Так же списывает бумаги брокер и считается НДФЛ.
 *
 * @param portions  проданные части покупок по id покупки
 * @param remaining сколько облигаций покупки ещё не продано, по id покупки
 * @param unsold    первая продажа, которой не хватило облигаций в покупках на её дату; null - все продажи покрыты
 */
record LotAllocation(Map<Long, List<Portion>> portions, Map<Long, Integer> remaining, PortfolioSale unsold) {

    /**
     * @param lots  покупки одной облигации
     * @param sales продажи той же облигации
     */
    static LotAllocation of(final List<PortfolioPosition> lots, final List<PortfolioSale> sales) {
        final List<PortfolioPosition> ordered = lots.stream()
                .sorted(Comparator.comparing(PortfolioPosition::getBuyDate)
                        .thenComparing(PortfolioPosition::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        final Map<Long, Integer> remaining = new HashMap<>();
        ordered.forEach(lot -> remaining.put(lot.getId(), lot.getQuantity()));
        final Map<Long, List<Portion>> portions = new HashMap<>();
        PortfolioSale unsold = null;
        for (final PortfolioSale sale : sales.stream()
                .sorted(Comparator.comparing(PortfolioSale::getSellDate)
                        .thenComparing(PortfolioSale::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            int need = sale.getQuantity();
            for (final PortfolioPosition lot : ordered) {
                if (need == 0) {
                    break;
                }
                final int left = remaining.get(lot.getId());
                if (left == 0 || lot.getBuyDate().isAfter(sale.getSellDate())) {
                    continue;
                }
                final int taken = Math.min(left, need);
                remaining.put(lot.getId(), left - taken);
                portions.computeIfAbsent(lot.getId(), id -> new ArrayList<>())
                        .add(new Portion(sale.getId(), sale.getSellDate(), taken));
                need -= taken;
            }
            if (need > 0 && unsold == null) {
                unsold = sale;
            }
        }
        return new LotAllocation(portions, remaining, unsold);
    }

    /**
     * Сколько облигаций покупки получает выплату в день date: куплены раньше этого дня и не проданы раньше него.
     * Выплата в день покупки достаётся продавцу, в день продажи - нам (как в калькуляторе дохода).
     */
    int heldOn(final PortfolioPosition lot, final LocalDate date) {
        if (!lot.getBuyDate().isBefore(date)) {
            return 0;
        }
        final int sold = Optional.ofNullable(portions.get(lot.getId())).orElse(List.of()).stream()
                .filter(portion -> portion.date().isBefore(date))
                .mapToInt(Portion::quantity)
                .sum();
        return lot.getQuantity() - sold;
    }

    /** Сколько облигаций покупки не продано */
    int remaining(final PortfolioPosition lot) {
        return remaining.getOrDefault(lot.getId(), lot.getQuantity());
    }

    /** Часть покупки, проданная продажей saleId в дату date */
    record Portion(Long saleId, LocalDate date, int quantity) {
    }
}
