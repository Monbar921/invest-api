package ru.invest.api.telegram.bot.channel;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.news.NewsSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Тексты постов канала. Только факты и их источник: без оценок («выгодно», «стоит купить», «надёжный»), подборка -
 * с пометкой, что это не рекомендация. Простой текст без разметки: экранировать нечего.
 */
final class ChannelTexts {
    static final String NOT_A_RECOMMENDATION = "Не является индивидуальной инвестиционной рекомендацией.";
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private ChannelTexts() {
    }

    /**
     * Рейтинговое действие: вид, заголовок релиза, эмитент, агентство с датой, источник и страница продукта.
     *
     * @param issuers названия эмитентов новости; пусто - эмитент не определён (рейтинг выпуска по ISIN)
     * @param bonds   выпуски из системы, упомянутые в релизе: «название (тикер)»
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    static String ratingAction(final RatingActionClassifier.Change change, final String title, final List<String> issuers,
                               final List<String> bonds, final NewsSource source, final LocalDate date,
                               final String url, final String page) {
        final StringBuilder text = new StringBuilder("Рейтинговое действие: ").append(change.getTitle())
                .append('\n').append(title);
        if (!issuers.isEmpty()) {
            text.append("\nЭмитент: ").append(String.join(", ", issuers));
        }
        if (!bonds.isEmpty()) {
            text.append("\nВыпуски: ").append(String.join(", ", bonds));
        }
        text.append("\n\n").append(source == NewsSource.ACRA ? "АКРА" : "НКР");
        appendSource(text, date, url);
        text.append("\nОблигации эмитента: ").append(page);
        return text.toString();
    }

    /**
     * Решение по ключевой ставке: заголовок пресс-релиза ЦБ, дата, источник и подборка флоатеров.
     */
    static String keyRate(final String title, final LocalDate date, final String url, final String page) {
        final StringBuilder text = new StringBuilder("Ключевая ставка\n").append(title).append("\n\nБанк России");
        appendSource(text, date, url);
        text.append("\nОблигации с переменным купоном: ").append(page);
        return text.toString();
    }

    /**
     * Еженедельная подборка: по группе рейтинга - облигации с наибольшей доходностью к погашению; пустые группы не
     * показываются.
     *
     * @param groups   группа → облигации по убыванию доходности (порядок групп сохраняется)
     * @param pageOf   адрес страницы облигации по тикеру
     * @param allBonds адрес списка облигаций
     */
    static String weeklyTop(final LocalDate date, final Map<String, List<BondModel>> groups,
                            final ChannelProperties.WeeklyTop settings,
                            final Function<String, String> pageOf, final String allBonds) {
        final StringBuilder text = new StringBuilder("Доходность облигаций по группам рейтинга на ")
                .append(date.format(DATE))
                .append("\nНаибольшая доходность к погашению по последней цене: рублёвые выпуски с постоянным купоном, ")
                .append("до погашения от ").append(number(settings.getMinYearsToMaturity())).append(" г.");
        if (settings.getMinTurnoverRub() != null) {
            text.append(", средний оборот за 30 дней от ").append(rub(settings.getMinTurnoverRub())).append(" в день.");
        }
        text.append(" Рейтинг - самый низкий из рейтингов АКРА и НКР.");
        groups.forEach((group, bonds) -> {
            text.append("\n\nРейтинг ").append(group);
            for (int i = 0; i < bonds.size(); i++) {
                final BondModel bond = bonds.get(i);
                text.append('\n').append(i + 1).append(". ").append(bond.getName()).append(" (").append(bond.getTicker())
                        .append("): ").append(percent(bond.getCoupon().getYieldToMaturity()));
                if (bond.getMaturityDate() != null) {
                    text.append(", погашение ").append(bond.getMaturityDate().toLocalDate().format(DATE));
                }
                if (bond.getCreditRating() != null) {
                    text.append(", ").append(bond.getCreditRating().getGrade());
                }
                text.append('\n').append(pageOf.apply(bond.getTicker()));
            }
        });
        text.append("\n\nВсе облигации и фильтры: ").append(allBonds)
                .append('\n').append(NOT_A_RECOMMENDATION);
        return text.toString();
    }

    private static void appendSource(final StringBuilder text, final LocalDate date, final String url) {
        if (date != null) {
            text.append(", ").append(date.format(DATE));
        }
        if (url != null) {
            text.append("\nИсточник: ").append(url);
        }
    }

    /** «18,40 %» */
    static String percent(final BigDecimal value) {
        return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(RU))
                .format(value.setScale(2, RoundingMode.HALF_UP)) + " %";
    }

    /** «0,5», «1» */
    private static String number(final BigDecimal value) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(RU)).format(value);
    }

    /** «1 000 000 ₽» */
    private static String rub(final BigDecimal value) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(RU))
                .format(value.setScale(0, RoundingMode.HALF_UP)) + " ₽";
    }
}
