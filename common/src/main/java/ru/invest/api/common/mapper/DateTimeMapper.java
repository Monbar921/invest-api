package ru.invest.api.common.mapper;

import com.google.protobuf.Timestamp;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Mapper
public abstract class DateTimeMapper {
    public LocalDateTime toLocalDateTime(final Timestamp timestamp) {
        return Optional.ofNullable(timestamp)
                .map(time -> LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()),
                        ZoneId.of("UTC")
                ))
                .orElse(null);

    }
}
