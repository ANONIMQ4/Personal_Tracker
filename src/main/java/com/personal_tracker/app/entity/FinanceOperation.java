package com.personal_tracker.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "finance_operations")
@Getter
@Setter
@JsonIgnoreProperties({
        "user",
        "paymentDate",
        "cardNumber",
        "status",
        "paymentAmount",
        "paymentCurrency",
        "cashback",
        "mcc",
        "bonuses",
        "investmentRounding",
        "roundedOperationAmount",
        "source",
        "operationKey"
})
public class FinanceOperation {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "operation_date")
    private LocalDateTime operationDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "card_number")
    private String cardNumber;

    private String status;

    @Column(name = "operation_amount")
    private BigDecimal operationAmount;

    @Column(name = "operation_currency")
    private String operationCurrency;

    @Column(name = "payment_amount")
    private BigDecimal paymentAmount;

    @Column(name = "payment_currency")
    private String paymentCurrency;

    private BigDecimal cashback;

    private String category;

    private Integer mcc;

    private String description;

    @Column(name = "exclude_from_analytics")
    private boolean excludeFromAnalytics;

    private String counterparty;

    private BigDecimal bonuses;

    @Column(name = "investment_rounding")
    private BigDecimal investmentRounding;

    @Column(name = "rounded_operation_amount")
    private BigDecimal roundedOperationAmount;

    private String source;

    @Column(name = "operation_key")
    private String operationKey;
}
