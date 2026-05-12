package com.personal_tracker.app.repository;

import com.personal_tracker.app.model.BrokerHolding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrokerHoldingRepository extends JpaRepository<BrokerHolding, Long> {
    List<BrokerHolding> findByUserIdOrderByHoldingTypeAscNameAsc(Long userId);

    Optional<BrokerHolding> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
