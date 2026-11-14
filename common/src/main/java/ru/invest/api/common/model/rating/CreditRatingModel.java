package ru.invest.api.common.model.rating;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * Действующий кредитный рейтинг агентства по национальной шкале.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CreditRatingModel {
    private RatingAgency agency;
    private RatingScope scope;
    /** Рейтинг, как его пишет агентство: «AA-(RU)», «AA-.ru» */
    private String rating;
    /** Ступень общей шкалы без суффикса агентства: «AA-» */
    private String grade;
    /** Балл общей шкалы: D - 0, AAA - 20 */
    private int score;
    private RatingOutlook outlook;
    /** Дата последнего рейтингового действия (присвоение, подтверждение, изменение) */
    private LocalDate actionDate;
    /** Релиз агентства с этим действием */
    private String url;
}
