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
 * Сохранённый фильтр списка облигаций пользователя. Уведомления по нему - правило bond_alert типа SAVED_FILTER.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "saved_filter")
public class SavedFilter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    /** Уникально у пользователя без учёта регистра */
    @Column(name = "name")
    private String name;

    /** Какие облигации: ALL, LOCAL, FOREIGN - как BondScope */
    @Column(name = "scope")
    private String scope;

    /** Фильтры и сортировка (BondParametersRequestModel) в JSON */
    @Column(name = "parameters")
    private String parameters;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** UTC */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
