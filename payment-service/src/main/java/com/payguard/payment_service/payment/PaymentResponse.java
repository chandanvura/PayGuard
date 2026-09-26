package com.payguard.payment_service.payment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String idempotencyKey,
        String customerId,
        Long amount,
        String currency,
        PaymentStatus status,
        String providerReference,
        String failureReason,
        Integer reconciliationAttempts,
        OffsetDateTime nextReconciliationAt,
        boolean reconciliationExhausted,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static PaymentResponse from(
            Payment payment
    ) {

        return new PaymentResponse(
                payment.getId(),
                payment.getIdempotencyKey(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProviderReference(),
                payment.getFailureReason(),
                payment.getReconciliationAttempts(),
                payment.getNextReconciliationAt(),
                payment.isReconciliationExhausted(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}