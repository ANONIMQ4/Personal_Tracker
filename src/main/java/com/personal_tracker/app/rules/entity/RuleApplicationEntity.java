package com.personal_tracker.app.rules.entity;

import com.personal_tracker.app.entity.FinanceOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "rule_applications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"rule_id", "operation_id"})
)
@Getter
@Setter
public class RuleApplicationEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private RuleEntity rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    private FinanceOperation operation;

    @Column(name = "before_category")
    private String beforeCategory;

    @Column(name = "before_exclude_from_analytics")
    private boolean beforeExcludeFromAnalytics;

    @Column(name = "before_counterparty")
    private String beforeCounterparty;

    @Column(name = "before_description", columnDefinition = "TEXT")
    private String beforeDescription;

    @Column(name = "after_category")
    private String afterCategory;

    @Column(name = "after_exclude_from_analytics")
    private boolean afterExcludeFromAnalytics;

    @Column(name = "after_counterparty")
    private String afterCounterparty;

    @Column(name = "after_description", columnDefinition = "TEXT")
    private String afterDescription;

    @Column(name = "applied_at")
    @Setter(AccessLevel.NONE)
    private LocalDateTime appliedAt;

    @PrePersist
    void onCreate() {
        appliedAt = LocalDateTime.now();
    }
}
