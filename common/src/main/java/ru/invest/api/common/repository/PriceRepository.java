package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.Price;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PriceRepository extends JpaRepository<Price, Long> {
    Optional<Price> findByUid(String uid);

    List<Price> findByUidIn(Set<String> uids);
}
