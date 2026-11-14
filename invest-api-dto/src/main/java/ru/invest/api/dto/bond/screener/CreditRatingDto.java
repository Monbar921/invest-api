package ru.invest.api.dto.bond.screener;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * Кредитный рейтинг агентства по национальной шкале.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CreditRatingDto {
    private RatingAgencyDto agency;
    /** ISSUE - рейтинг выпуска, ISSUER - рейтинг эмитента (у выпуска своего нет) */
    private RatingScopeDto scope;
    /** Как пишет агентство: «AA-(RU)», «AA-.ru» */
    private String rating;
    /** Ступень общей шкалы: «AA-» */
    private String grade;
    /** Балл общей шкалы: D - 0 ... AAA - 20 */
    private int score;
    private RatingOutlookDto outlook;
    /** Дата последнего рейтингового действия */
    private LocalDate actionDate;
    /** Релиз агентства */
    private String url;
}
