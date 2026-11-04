package ru.invest.api.common.usecase.impl;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.usecase.BondSearchUseCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class BondSearchUseCaseImpl implements BondSearchUseCase {
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int NOT_FOUND = -1;
    private static final int EXACT = 0;
    private static final int CODE_PREFIX = 1;
    private static final int NAME_PREFIX = 2;
    private static final int WORD_PREFIX = 3;
    private static final int CONTAINS = 4;

    @Override
    public List<BondModel> search(final Collection<BondModel> bonds, final String query, final int limit) {
        final String normalizedQuery = normalize(query);
        if (CollectionUtils.isEmpty(bonds) || normalizedQuery.length() < MIN_QUERY_LENGTH || limit <= 0) {
            return List.of();
        }
        final List<String> words = Arrays.asList(normalizedQuery.split(" "));

        return bonds.stream()
                .filter(Objects::nonNull)
                .map(bond -> new Match(bond, rank(bond, normalizedQuery, words)))
                .filter(match -> match.rank() != NOT_FOUND)
                .sorted(Comparator.comparingInt(Match::rank)
                        .thenComparing(match -> normalize(match.bond().getName()))
                        .thenComparing(match -> normalize(match.bond().getTicker())))
                .limit(limit)
                .map(Match::bond)
                .toList();
    }

    private int rank(final BondModel bond, final String query, final List<String> words) {
        final String ticker = normalize(bond.getTicker());
        final String isin = normalize(bond.getIsin());
        final String name = normalize(bond.getName());

        if (query.equals(ticker) || query.equals(isin)) {
            return EXACT;
        }
        if (ticker.startsWith(query) || isin.startsWith(query)) {
            return CODE_PREFIX;
        }
        if (name.startsWith(query)) {
            return NAME_PREFIX;
        }
        if (name.contains(" " + query)) {
            return WORD_PREFIX;
        }

        // все слова запроса - в любом порядке и в любом из полей: «ОФЗ 26238», «газпром капитал»
        final String all = String.join(" ", ticker, isin, name);
        return words.stream().allMatch(all::contains) ? CONTAINS : NOT_FOUND;
    }

    private static String normalize(final String value) {
        return StringUtils.normalizeSpace(StringUtils.defaultString(value))
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е');
    }

    private record Match(BondModel bond, int rank) {
    }
}
