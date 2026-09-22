package com.payguard.payment_service.provider;


public interface PaymentProvider {

    ProviderPaymentResult charge(
            String customerId,
            Long amount,
            String currency
    );
}
