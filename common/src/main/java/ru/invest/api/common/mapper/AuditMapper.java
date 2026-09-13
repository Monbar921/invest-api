package ru.invest.api.common.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.model.AuditModel;

@Mapper
public interface AuditMapper {
    Audit toEntity(AuditModel auditModel);

    AuditModel toModel(Audit audit);
}
