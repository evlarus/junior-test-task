package com.evlarus.spendinglimit.transaction.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.limit.infrastructure.persistence.SpendingLimitEntity;
import com.evlarus.spendinglimit.transaction.domain.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persistence model of {@code bank_transaction}. Business rules live in the domain {@code Transaction}. */
@Entity
@Table(name = "bank_transaction")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_from", nullable = false, length = 10)
    private String accountFrom;

    @Column(name = "account_to", nullable = false, length = 10)
    private String accountTo;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category", nullable = false, length = 16)
    private ExpenseCategory expenseCategory;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "occurred_offset_seconds", nullable = false)
    private int occurredOffsetSeconds;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransactionStatus status;

    @Column(name = "amount_usd", precision = 19, scale = 2)
    private BigDecimal amountUsd;

    /** Lazy and never navigated when building domain objects; used to write the FK and in JPQL joins. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "limit_id")
    private SpendingLimitEntity limit;

    /** The same FK as a plain value: reading it never initializes the {@link #limit} proxy (no extra query). */
    @Column(name = "limit_id", insertable = false, updatable = false)
    private Long limitId;

    @Column(name = "limit_exceeded")
    private Boolean limitExceeded;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
