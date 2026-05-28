package com.personal_tracker.app.rules.repository;

import com.personal_tracker.app.rules.entity.RuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleRepository extends JpaRepository<RuleEntity, Long> {
    List<RuleEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<RuleEntity> findByIdAndUserId(Long id, Long userId);
}
