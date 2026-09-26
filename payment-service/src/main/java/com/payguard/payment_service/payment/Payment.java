package com.payguard.payment_service.payment;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 5;

    @Id
    private UUID id;

    @Column(
            name = "idempotency_key",
            nullable = false,
            unique = true,
            length = 100
    )
    private String idempotencyKey;

    @Column(
            name = "customer_id",
            nullable = false,
            length = 100
    )
    private String customerId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(
            name = "reconciliation_attempts",
            nullable = false
    )
    private Integer reconciliationAttempts = 0;

    @Column(name = "next_reconciliation_at")
    private OffsetDateTime nextReconciliationAt;


    /*
     * Required by JPA/Hibernate.
     *
     * Hibernate needs a no-argument constructor
     * when it recreates Payment objects from the database.
     */
    protected Payment() {
    }


    public Payment(
            String idempotencyKey,
            String customerId,
            Long amount,
            String currency
    ) {

        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;

        this.status = PaymentStatus.PENDING;

        OffsetDateTime now = OffsetDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;
    }


    /*
     * PENDING -> PROCESSING
     *
     * PayGuard has accepted the payment
     * and is about to contact the provider.
     */
    public void markProcessing() {

        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = OffsetDateTime.now();
    }


    /*
     * Any uncertain/in-progress state -> SUCCESS
     *
     * SUCCESS is terminal.
     */
    public void markSuccessful(
            String providerReference
    ) {

        this.status = PaymentStatus.SUCCESS;

        this.providerReference = providerReference;

        this.failureReason = null;

        // No reconciliation is needed anymore.
        this.nextReconciliationAt = null;

        this.updatedAt = OffsetDateTime.now();
    }


    /*
     * Any uncertain/in-progress state -> FAILED
     *
     * FAILED is terminal.
     */
    public void markFailed(
            String failureReason
    ) {

        this.status = PaymentStatus.FAILED;

        this.failureReason = failureReason;

        // No reconciliation is needed anymore.
        this.nextReconciliationAt = null;

        this.updatedAt = OffsetDateTime.now();
    }


    /*
     * We don't know whether the provider
     * completed the payment.
     *
     * Example:
     *
     * PayGuard ---- charge ----> Provider
     * PayGuard <---- timeout ---- Provider
     *
     * We MUST NOT call this FAILED because
     * the provider may actually have charged the customer.
     */
    public void markUnknown(
            String reason
    ) {

        this.status = PaymentStatus.UNKNOWN;
        this.failureReason = reason;

        /*
         * Becoming UNKNOWN immediately schedules
         * the first reconciliation attempt.
         */
        scheduleNextReconciliation();
    }


    /*
     * Schedule the next provider-status check.
     *
     * Backoff:
     *
     * attempt 1 -> 30 seconds
     * attempt 2 -> 60 seconds
     * attempt 3 -> 120 seconds
     * attempt 4 -> 240 seconds
     * attempt 5 -> 480 seconds
     *
     * Maximum delay is capped at 3600 seconds.
     */
    public void scheduleNextReconciliation() {

        /*
         * We already reached the maximum.
         *
         * Keep status UNKNOWN because we still
         * don't know the real provider outcome.
         *
         * nextReconciliationAt = null means the
         * background worker will no longer select it.
         */
        if (this.reconciliationAttempts
                >= MAX_RECONCILIATION_ATTEMPTS) {

            /*
             * We tried the provider the maximum number
             * of times and still could not determine
             * the final payment outcome.
             *
             * Do NOT mark FAILED because the provider
             * may actually have processed the payment.
             *
             * Human/manual investigation is now required.
             */
            this.status = PaymentStatus.REQUIRES_REVIEW;

            this.nextReconciliationAt = null;

            this.updatedAt = OffsetDateTime.now();

            return;
        }


        /*
         * Increment before calculating the delay.
         *
         * First schedule:
         * 0 -> 1
         */
        this.reconciliationAttempts++;


        /*
         * Exponential backoff:
         *
         * 30 * 2^(attempt - 1)
         *
         * attempt 1:
         * 30 * 2^0 = 30
         *
         * attempt 2:
         * 30 * 2^1 = 60
         *
         * attempt 3:
         * 30 * 2^2 = 120
         */
        long delaySeconds =
                Math.min(
                        30L * (
                                1L << Math.min(
                                        reconciliationAttempts - 1,
                                        10
                                )
                        ),
                        3600L
                );


        this.nextReconciliationAt =
                OffsetDateTime.now()
                        .plusSeconds(delaySeconds);


        this.updatedAt =
                OffsetDateTime.now();
    }


    /*
     * Exhausted means:
     *
     * 1. We are still UNKNOWN
     * 2. We reached the retry limit
     * 3. There is no future automatic retry
     */
    public boolean isReconciliationExhausted() {
        return this.status == PaymentStatus.REQUIRES_REVIEW;
    }


    // --------------------------------------------------
    // Getters
    // --------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Integer getReconciliationAttempts() {
        return reconciliationAttempts;
    }

    public OffsetDateTime getNextReconciliationAt() {
        return nextReconciliationAt;
    }
}