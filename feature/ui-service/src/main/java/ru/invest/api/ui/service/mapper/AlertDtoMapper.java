package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import ru.invest.api.alert.service.model.AlertModel;
import ru.invest.api.alert.service.model.NotificationModel;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.alert.AlertDirection;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.dto.alert.AlertDirectionDto;
import ru.invest.api.dto.alert.AlertDto;
import ru.invest.api.dto.alert.AlertTypeDto;
import ru.invest.api.dto.alert.NotificationDto;
import ru.invest.api.dto.page.PageDto;

import java.util.List;

@Mapper(uses = DateTimeMapper.class)
public interface AlertDtoMapper {
    AlertDto toDto(AlertModel alert);

    List<AlertDto> toDto(List<AlertModel> alerts);

    NotificationDto toDto(NotificationModel notification);

    default PageDto<NotificationDto> toDto(final Page<NotificationModel> page) {
        return new PageDto<NotificationDto>()
                .setContent(toNotificationDto(page.getContent()))
                .setPage(page.getNumber())
                .setSize(page.getSize())
                .setTotalElements(page.getTotalElements())
                .setTotalPages(page.getTotalPages());
    }

    List<NotificationDto> toNotificationDto(List<NotificationModel> notifications);

    AlertType toType(AlertTypeDto type);

    AlertDirection toDirection(AlertDirectionDto direction);
}
