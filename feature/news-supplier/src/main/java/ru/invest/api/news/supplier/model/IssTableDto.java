package ru.invest.api.news.supplier.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Блок ответа ISS: имена колонок и строки значений в том же порядке.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class IssTableDto {
    private List<String> columns = List.of();
    private List<List<Object>> data = List.of();

    /**
     * Строки как «колонка - значение»; null-значения сохраняются.
     */
    public List<Map<String, Object>> rows() {
        if (columns == null || data == null) {
            return List.of();
        }

        return data.stream()
                .map(row -> {
                    final Map<String, Object> values = new HashMap<>();
                    IntStream.range(0, Math.min(columns.size(), row.size()))
                            .forEach(index -> values.put(columns.get(index), row.get(index)));
                    return values;
                })
                .toList();
    }
}
