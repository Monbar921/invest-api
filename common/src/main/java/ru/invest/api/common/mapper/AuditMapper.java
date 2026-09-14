package ru.invest.api.common.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.model.AuditModel;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper
public interface AuditMapper {
    Audit toEntity(AuditModel auditModel);

    AuditModel toModel(Audit audit);

    @Mapping(target = "committedAt", ignore = true)
    @Mapping(target = "committedBy", source = "committedBy")
    AuditModel toCurrentAuditModel(String committedBy);

    @AfterMapping
    default void afterMapping(@MappingTarget final AuditModel auditModel, final String committedBy) {
        auditModel.setCommittedAt(
                LocalDateTime.now(ZoneOffset.UTC)
        );
    }
}
