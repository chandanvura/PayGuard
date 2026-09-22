package com.payguard.payment_service.provider;


public record ProviderPaymentResult(
        boolean successful,
        String providerReference,
        String failureReason
) {
}
