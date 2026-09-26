package com.payguard.payment_service.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final Counter paymentsCreated;
    private final Counter paymentsSuccessful;
    private final Counter paymentsFailed;
    private final Counter paymentsUnknown;

    private final Counter reconciliationAttempts;
    private final Counter reconciliationSuccessful;
    private final Counter reconciliationFailed;
    private final Counter reconciliationPending;
    private final Counter reconciliationProviderErrors;
    private final Counter reconciliationExhausted;

    public PaymentMetrics(MeterRegistry meterRegistry) {

        this.paymentsCreated = Counter.builder(
                        "payguard.payment.created"
                )
                .description("Total number of payments created")
                .register(meterRegistry);

        this.paymentsSuccessful = Counter.builder(
                        "payguard.payment.success"
                )
                .description("Total number of payments that reached SUCCESS")
                .register(meterRegistry);

        this.paymentsFailed = Counter.builder(
                        "payguard.payment.failed"
                )
                .description("Total number of payments that reached FAILED")
                .register(meterRegistry);

        this.paymentsUnknown = Counter.builder(
                        "payguard.payment.unknown"
                )
                .description(
                        "Total number of payments that entered UNKNOWN"
                )
                .register(meterRegistry);

        this.reconciliationAttempts = Counter.builder(
                        "payguard.reconciliation.attempt"
                )
                .description(
                        "Total number of reconciliation attempts"
                )
                .register(meterRegistry);

        this.reconciliationSuccessful = Counter.builder(
                        "payguard.reconciliation.success"
                )
                .description(
                        "Total number of UNKNOWN payments resolved to SUCCESS"
                )
                .register(meterRegistry);

        this.reconciliationFailed = Counter.builder(
                        "payguard.reconciliation.failed"
                )
                .description(
                        "Total number of UNKNOWN payments resolved to FAILED"
                )
                .register(meterRegistry);

        this.reconciliationPending = Counter.builder(
                        "payguard.reconciliation.pending"
                )
                .description(
                        "Total number of reconciliation attempts that remained unresolved"
                )
                .register(meterRegistry);

        this.reconciliationProviderErrors = Counter.builder(
                        "payguard.reconciliation.provider.error"
                )
                .description(
                        "Total number of provider errors during reconciliation"
                )
                .register(meterRegistry);

        this.reconciliationExhausted = Counter.builder(
                        "payguard.reconciliation.exhausted"
                )
                .description(
                        "Total number of payments that exhausted reconciliation attempts"
                )
                .register(meterRegistry);
    }

    public void paymentCreated() {
        paymentsCreated.increment();
    }

    public void paymentSuccessful() {
        paymentsSuccessful.increment();
    }

    public void paymentFailed() {
        paymentsFailed.increment();
    }

    public void paymentUnknown() {
        paymentsUnknown.increment();
    }

    public void reconciliationAttempt() {
        reconciliationAttempts.increment();
    }

    public void reconciliationSuccessful() {
        reconciliationSuccessful.increment();
    }

    public void reconciliationFailed() {
        reconciliationFailed.increment();
    }

    public void reconciliationPending() {
        reconciliationPending.increment();
    }

    public void reconciliationProviderError() {
        reconciliationProviderErrors.increment();
    }

    public void reconciliationExhausted() {
        reconciliationExhausted.increment();
    }
}