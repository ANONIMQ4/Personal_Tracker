package com.personal_tracker.app.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "finance_operations")
public class FinanceOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "operation_date")
    private LocalDateTime operationDate;

    @JsonIgnore
    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @JsonIgnore
    @Column(name = "card_number")
    private String cardNumber;

    @JsonIgnore
    private String status;

    @Column(name = "operation_amount")
    private BigDecimal operationAmount;

    @Column(name = "operation_currency")
    private String operationCurrency;

    @JsonIgnore
    @Column(name = "payment_amount")
    private BigDecimal paymentAmount;

    @JsonIgnore
    @Column(name = "payment_currency")
    private String paymentCurrency;

    @JsonIgnore
    private BigDecimal cashback;

    private String category;

    @JsonIgnore
    private Integer mcc;

    private String description;

    @JsonIgnore
    private BigDecimal bonuses;

    @JsonIgnore
    @Column(name = "investment_rounding")
    private BigDecimal investmentRounding;

    @JsonIgnore
    @Column(name = "rounded_operation_amount")
    private BigDecimal roundedOperationAmount;

    @JsonIgnore
    private String source;

    @JsonIgnore
    @Column(name = "operation_key")
    private String operationKey;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getOperationDate() {
        return operationDate;
    }

    public void setOperationDate(LocalDateTime operationDate) {
        this.operationDate = operationDate;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getOperationAmount() {
        return operationAmount;
    }

    public void setOperationAmount(BigDecimal operationAmount) {
        this.operationAmount = operationAmount;
    }

    public String getOperationCurrency() {
        return operationCurrency;
    }

    public void setOperationCurrency(String operationCurrency) {
        this.operationCurrency = operationCurrency;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public String getPaymentCurrency() {
        return paymentCurrency;
    }

    public void setPaymentCurrency(String paymentCurrency) {
        this.paymentCurrency = paymentCurrency;
    }

    public BigDecimal getCashback() {
        return cashback;
    }

    public void setCashback(BigDecimal cashback) {
        this.cashback = cashback;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getMcc() {
        return mcc;
    }

    public void setMcc(Integer mcc) {
        this.mcc = mcc;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBonuses() {
        return bonuses;
    }

    public void setBonuses(BigDecimal bonuses) {
        this.bonuses = bonuses;
    }

    public BigDecimal getInvestmentRounding() {
        return investmentRounding;
    }

    public void setInvestmentRounding(BigDecimal investmentRounding) {
        this.investmentRounding = investmentRounding;
    }

    public BigDecimal getRoundedOperationAmount() {
        return roundedOperationAmount;
    }

    public void setRoundedOperationAmount(BigDecimal roundedOperationAmount) {
        this.roundedOperationAmount = roundedOperationAmount;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public void setOperationKey(String operationKey) {
        this.operationKey = operationKey;
    }
}
