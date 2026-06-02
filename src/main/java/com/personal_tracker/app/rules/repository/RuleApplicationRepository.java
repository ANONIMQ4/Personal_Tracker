package com.personal_tracker.app.rules.repository;

import com.personal_tracker.app.rules.entity.RuleApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleApplicationRepository extends JpaRepository<RuleApplicationEntity, Long> {
    List<RuleApplicationEntity> findByRuleId(Long ruleId);

    void deleteByRuleId(Long ruleId);
}
