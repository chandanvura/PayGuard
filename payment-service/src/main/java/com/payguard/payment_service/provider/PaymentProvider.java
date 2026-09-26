package com.payguard.payment_service.provider;

public interface PaymentProvider {

    ProviderPaymentResult charge(
            String idempotencyKey,
            String customerId,
            Long amount,
            String currency
    );

    ProviderStatusResult getStatus(
            String idempotencyKey
    );
}