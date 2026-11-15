package ru.invest.api.feature.alert;

import org.junit.jupiter.api.Test;
import ru.invest.api.alert.service.model.RiskKind;
import ru.invest.api.alert.service.usecase.impl.RiskEventClassifier;
import ru.invest.api.common.model.news.NewsSource;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Какой риск в заголовке новости - без Spring и БД. Заголовки - в форме, в какой их пишут биржа, агентства и СМИ.
 */
class RiskEventClassifierTest {

    @Test
    void ratingDowngradeOnlyFromAgencies() {
        assertThat(kind(NewsSource.ACRA, "АКРА понизило кредитный рейтинг АО «Авто Финанс Банк» до BBB+(RU)"),
                equalTo(Optional.of(RiskKind.RATING_DOWNGRADE)));
        assertThat(kind(NewsSource.NKR, "НКР понизило кредитный рейтинг ПАО «Авто Финанс Банк» с A+.ru до A.ru, "
                + "прогноз — негативный"), equalTo(Optional.of(RiskKind.RATING_DOWNGRADE)));
        // СМИ пересказывают релиз - событие придёт от агентства
        assertThat(kind(NewsSource.INTERFAX, "АКРА понизило рейтинг «Авто Финанс Банка»"), equalTo(Optional.empty()));
        // подтверждение и повышение - не риск
        assertThat(kind(NewsSource.ACRA, "АКРА подтвердило кредитный рейтинг АО «Авто Финанс Банк» на уровне A(RU)"),
                equalTo(Optional.empty()));
        assertThat(kind(NewsSource.NKR, "НКР повысило кредитный рейтинг ООО «Х» до A.ru"), equalTo(Optional.empty()));
    }

    @Test
    void downgradeToDefaultLevelIsDefault() {
        assertThat(kind(NewsSource.ACRA, "АКРА понизило кредитный рейтинг ООО «Х» до RD(RU)"),
                equalTo(Optional.of(RiskKind.DEFAULT)));
        assertThat(kind(NewsSource.NKR, "НКР понизило кредитный рейтинг ООО «Х» с B-.ru до SD.ru"),
                equalTo(Optional.of(RiskKind.DEFAULT)));
        assertThat(kind(NewsSource.NKR, "НКР понизило кредитный рейтинг ООО «Х» до D.ru"),
                equalTo(Optional.of(RiskKind.DEFAULT)));
        // «до BBB-» начинается не с D
        assertThat(kind(NewsSource.NKR, "НКР понизило кредитный рейтинг ООО «Х» до BBB-.ru"),
                equalTo(Optional.of(RiskKind.RATING_DOWNGRADE)));
    }

    @Test
    void defaultsFromAnySource() {
        assertThat(kind(NewsSource.INTERFAX, "«Х» допустило технический дефолт по купону облигаций"),
                equalTo(Optional.of(RiskKind.TECHNICAL_DEFAULT)));
        assertThat(kind(NewsSource.MOEX, "О техническом дефолте эмитента ООО «Х»"),
                equalTo(Optional.of(RiskKind.TECHNICAL_DEFAULT)));
        assertThat(kind(NewsSource.RBC, "Компания «Х» объявила дефолт по облигациям"),
                equalTo(Optional.of(RiskKind.DEFAULT)));
        assertThat(kind(NewsSource.SMART_LAB, "ДЕФОЛТ: «Х» не выплатила номинал"),
                equalTo(Optional.of(RiskKind.DEFAULT)));
    }

    @Test
    void tradingSuspensionOnlyFromExchange() {
        assertThat(kind(NewsSource.MOEX, "О приостановке торгов ценными бумагами"),
                equalTo(Optional.of(RiskKind.TRADING_SUSPENSION)));
        assertThat(kind(NewsSource.MOEX, "О приостановлении торгов облигациями ООО «Х»"),
                equalTo(Optional.of(RiskKind.TRADING_SUSPENSION)));
        assertThat(kind(NewsSource.MOEX, "О возобновлении торгов ценными бумагами"), equalTo(Optional.empty()));
        assertThat(kind(NewsSource.KOMMERSANT, "Биржа приостановила торги облигациями «Х»"), equalTo(Optional.empty()));
    }

    @Test
    void otherNewsAreNotRisks() {
        assertThat(kind(NewsSource.MOEX, "Изменение параметров выпуска RU000A10EQ34"), equalTo(Optional.empty()));
        assertThat(kind(NewsSource.CBR_PRESS, "Банк России снизил ключевую ставку"), equalTo(Optional.empty()));
        assertThat(kind(NewsSource.MOEX, ""), equalTo(Optional.empty()));
        assertThat(kind(NewsSource.MOEX, null), equalTo(Optional.empty()));
        assertThat(kind(null, "дефолт"), equalTo(Optional.empty()));
    }

    private static Optional<RiskKind> kind(final NewsSource source, final String title) {
        return RiskEventClassifier.classify(source, title);
    }
}
