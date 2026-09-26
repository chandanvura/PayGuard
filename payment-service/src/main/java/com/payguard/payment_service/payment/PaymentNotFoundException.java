package com.payguard.payment_service.payment;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found: " + paymentId);
    }

    public PaymentNotFoundException(String idempotencyKey) {
        super("Payment not found for idempotency key: " + idempotencyKey);
    }
}