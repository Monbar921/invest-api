package ru.invest.api.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.invest.api.common.entity.Bond;

import java.util.Optional;

public interface BondRepository extends JpaRepository<Bond, Long> {
    Optional<Bond> findByUid(String uid);
}
