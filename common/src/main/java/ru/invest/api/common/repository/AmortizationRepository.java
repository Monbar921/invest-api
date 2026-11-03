package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.Amortization;

import java.util.List;
import java.util.Set;

public interface AmortizationRepository extends JpaRepository<Amortization, Long> {
    List<Amortization> findByUidIn(Set<String> uids);
}
