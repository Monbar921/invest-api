package ru.invest.api.common.mapper;

import com.google.protobuf.Timestamp;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

@Mapper
public interface DateTimeMapper {
    default LocalDateTime toLocalDateTime(final Timestamp timestamp) {
        return Optional.ofNullable(timestamp)
                .map(time -> LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()),
                        ZoneId.of("UTC")
                ))
                .orElse(null);

    }

    default ZonedDateTime toZonedDateTime(final LocalDateTime localDateTime) {
        return Optional.ofNullable(localDateTime)
                .map(dateTime -> dateTime.atZone(ZoneOffset.UTC))
                .orElse(null);
    }

    default LocalDateTime getNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
