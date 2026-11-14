package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.PortfolioImportItem;

import java.util.List;

public interface PortfolioImportItemRepository extends JpaRepository<PortfolioImportItem, Long> {
    List<PortfolioImportItem> findByImportIdOrderByLineNoAscIdAsc(Long importId);
}
