package ru.invest.api.dto.page;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Страница выдачи.
 *
 * @param <T> тип элементов страницы
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PageDto<T> {
    private List<T> content;
    /** Номер страницы, начиная с 0 */
    private int page;
    /** Запрошенный размер страницы */
    private int size;
    /** Количество элементов во всей выдаче с учётом фильтров */
    private long totalElements;
    private int totalPages;
}
