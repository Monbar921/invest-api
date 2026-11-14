package ru.invest.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Облигация в выборке сохранённого фильтра на прошлой проверке - о ней уже уведомили (или она была в выборке, когда
 * уведомления включили). Облигация - по uid, без внешнего ключа.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "saved_filter_match")
public class SavedFilterMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "filter_id")
    private Long filterId;

    @Column(name = "uid")
    private String uid;

    @Column(name = "ticker")
    private String ticker;

    /** Когда облигация вошла в выборку, UTC */
    @Column(name = "matched_at")
    private LocalDateTime matchedAt;
}
