package ru.invest.api.ui.service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.alert.service.model.SavedFilterChangeModel;
import ru.invest.api.alert.service.usecase.SavedFilterUseCase;
import ru.invest.api.dto.filter.SavedFilterCreateRequest;
import ru.invest.api.dto.filter.SavedFilterDto;
import ru.invest.api.dto.filter.SavedFilterUpdateRequest;
import ru.invest.api.ui.service.mapper.SavedFilterDtoMapper;

import java.util.List;

/**
 * Сохранённые фильтры списка облигаций и уведомления о новых облигациях в их выборке. Пользователь - X-User-Id от
 * gateway, без него 401; чужой фильтр - 404. Наружу через gateway: /api/alerts/filters/** (маршрут /api/alerts/**).
 */
@Validated
@RestController
@RequestMapping("/internal/rest/alerts/filters")
@RequiredArgsConstructor
public class SavedFilterController {
    private final SavedFilterUseCase savedFilterUseCase;
    private final SavedFilterDtoMapper mapper;

    /** Фильтры пользователя по названию, с числом облигаций в выборке сейчас */
    @GetMapping
    public List<SavedFilterDto> getFilters(
            @RequestHeader(value = UserIdHeader.NAME, required = false) final String userId) {
        return mapper.toDto(savedFilterUseCase.getFilters(UserIdHeader.require(userId)));
    }

    @GetMapping("/{id}")
    public SavedFilterDto getFilter(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                                    @PathVariable final Long id) {
        return mapper.toDto(savedFilterUseCase.getFilter(UserIdHeader.require(userId), id));
    }

    /**
     * Новый фильтр: 201. Такое название уже есть - 422; фильтров (а с notify - и правил уведомлений) столько, сколько
     * разрешает тариф, - 402; пустое или длинное название, неизвестная ступень рейтинга - 400.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedFilterDto create(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                                 @RequestBody @Valid final SavedFilterCreateRequest request) {
        return mapper.toDto(savedFilterUseCase.create(UserIdHeader.require(userId), new SavedFilterChangeModel(
                request.getName(), mapper.toScope(request.getScope()), mapper.toModel(request.getParameters()),
                request.getNotify())));
    }

    /**
     * Переименовать, заменить параметры, включить или выключить уведомления. Новые параметры и включение - выборка
     * сейчас становится отправной точкой; выключение удаляет правило уведомлений.
     */
    @PutMapping("/{id}")
    public SavedFilterDto update(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                                 @PathVariable final Long id,
                                 @RequestBody @Valid final SavedFilterUpdateRequest request) {
        return mapper.toDto(savedFilterUseCase.update(UserIdHeader.require(userId), id, new SavedFilterChangeModel(
                request.getName(), mapper.toScope(request.getScope()), mapper.toModel(request.getParameters()),
                request.getNotify())));
    }

    /** Удалить фильтр и уведомления по нему (204); история уведомлений остаётся */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = UserIdHeader.NAME, required = false) final String userId,
                       @PathVariable final Long id) {
        savedFilterUseCase.delete(UserIdHeader.require(userId), id);
    }
}
