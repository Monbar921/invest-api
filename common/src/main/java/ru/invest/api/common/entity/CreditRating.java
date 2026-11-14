package ru.invest.api.common.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.rating.RatingAgency;
import ru.invest.api.common.model.rating.RatingOutlook;
import ru.invest.api.common.model.rating.RatingScope;

import java.time.LocalDate;

/**
 * Текущий кредитный рейтинг агентства: эмитента (emitter_id Мосбиржи) или выпуска (ISIN) - последнее рейтинговое
 * действие по нему. Строка на агентство и объект рейтинга; новое действие заменяет её, отзыв ставит withdrawn.
 * От новостей не зависит: новости живут 180 дней, рейтинг - пока его не изменят или не отзовут.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "credit_rating")
public class CreditRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "agency")
    private RatingAgency agency;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope")
    private RatingScope scope;

    /** эмитент - для рейтинга эмитента */
    @Column(name = "emitter_id")
    private Long emitterId;

    /** выпуск - для рейтинга выпуска */
    @Column(name = "isin")
    private String isin;

    /** как в релизе: «AA-(RU)»; у отозванного - null */
    @Column(name = "rating")
    private String rating;

    /** ступень общей шкалы: «AA-» */
    @Column(name = "grade")
    private String grade;

    @Column(name = "score")
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "outlook")
    private RatingOutlook outlook;

    @Column(name = "withdrawn")
    private boolean withdrawn;

    @Column(name = "action_date")
    private LocalDate actionDate;

    @Column(name = "release_id")
    private String releaseId;

    @Column(name = "title")
    private String title;

    @Column(name = "url")
    private String url;

    @Embedded
    @AttributeOverride(name = "committedBy", column = @Column(name = "updated_by"))
    @AttributeOverride(name = "committedAt", column = @Column(name = "updated_at"))
    private Audit updated;
}
