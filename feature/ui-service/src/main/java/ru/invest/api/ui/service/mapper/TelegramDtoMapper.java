package ru.invest.api.ui.service.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.dto.telegram.TelegramLinkDto;
import ru.invest.api.dto.telegram.TelegramStatusDto;
import ru.invest.api.telegram.bot.model.TelegramLinkModel;
import ru.invest.api.telegram.bot.model.TelegramStatusModel;

@Mapper(uses = DateTimeMapper.class)
public interface TelegramDtoMapper {
    TelegramStatusDto toDto(TelegramStatusModel status);

    TelegramLinkDto toDto(TelegramLinkModel link);
}
