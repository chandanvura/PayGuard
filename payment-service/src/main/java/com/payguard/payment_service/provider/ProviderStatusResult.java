package com.payguard.payment_service.provider;

public record ProviderStatusResult(
        ProviderPaymentStatus status,
        String providerReference,
        String failureReason
) {
}