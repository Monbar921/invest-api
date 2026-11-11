package ru.invest.api.feature.news;

import org.junit.jupiter.api.Test;
import ru.invest.api.news.supplier.usecase.impl.IssuerTitles;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Короткое название эмитента для ленты, карточки и страницы эмитента. Без Spring и БД.
 */
class IssuerTitlesTest {

    @Test
    void legalFormsAreShortened() {
        assertThat(IssuerTitles.shortTitle("Публичное акционерное общество \"Селигдар\""), equalTo("ПАО \"Селигдар\""));
        assertThat(IssuerTitles.shortTitle("Непубличное акционерное общество \"Х\""), equalTo("АО \"Х\""));
        assertThat(IssuerTitles.shortTitle("АКЦИОНЕРНОЕ ОБЩЕСТВО \"Х\""), equalTo("АО \"Х\""));
        assertThat(IssuerTitles.shortTitle("Общество с ограниченной ответственностью \"Х\""), equalTo("ООО \"Х\""));
    }

    @Test
    void anyWhitespaceBetweenWords() {
        // названия приходят из выгрузки Мосбиржи: бывают двойные пробелы, табуляция и перенос строки
        assertThat(IssuerTitles.shortTitle("Публичное  акционерное общество \"Х\""), equalTo("ПАО \"Х\""));
        assertThat(IssuerTitles.shortTitle("Публичное\tакционерное\nобщество \"Х\""), equalTo("ПАО \"Х\""));
    }

    @Test
    void titleWithoutLegalFormIsTrimmed() {
        assertThat(IssuerTitles.shortTitle("  Минфин России "), equalTo("Минфин России"));
        assertThat(IssuerTitles.shortTitle(null), nullValue());
    }
}
