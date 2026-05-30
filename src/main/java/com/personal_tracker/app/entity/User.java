package com.personal_tracker.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String email;

    @JsonIgnore
    @Setter
    private String password;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "account_balance")
    @Getter(AccessLevel.NONE)
    private BigDecimal accountBalance = BigDecimal.ZERO;

    public User() {
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.createdAt = LocalDateTime.now();
    }

    public BigDecimal getAccountBalance() {
        return accountBalance == null ? BigDecimal.ZERO : accountBalance;
    }

    public void setAccountBalance(BigDecimal accountBalance) {
        this.accountBalance = accountBalance == null ? BigDecimal.ZERO : accountBalance;
    }
}
