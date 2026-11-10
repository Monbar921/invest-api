package ru.invest.api.bond.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.invest.api.bond.supplier.collection.BondCollection;
import ru.invest.api.bond.supplier.usecase.BondCollectionUseCase;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.BondParametersMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BondCollectionUseCaseImpl implements BondCollectionUseCase {
    private static final String COLLECTION_NOT_FOUND_MESSAGE = "Collection not found: %s";

    private final BondUseCase bondUseCase;
    private final BondParametersMapper bondParametersMapper;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public List<BondCollection> getCollections() {
        return List.of(BondCollection.values());
    }

    @Override
    public BondCollection getCollection(final String slug) {
        return BondCollection.bySlug(slug)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.COLLECTION_NOT_FOUND,
                        COLLECTION_NOT_FOUND_MESSAGE.formatted(slug)));
    }

    @Override
    public Page<BondModel> getPage(final BondCollection collection, final Pageable pageable) {
        return bondUseCase.getAllPage(
                bondParametersMapper.toModel(collection.parameters(dateTimeMapper.getNow().toLocalDate())), pageable);
    }
}
