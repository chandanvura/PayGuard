package com.payguard.payment_service.provider;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FakePaymentProvider implements PaymentProvider {

    private final AtomicInteger chargeCount = new AtomicInteger(0);

    @Override
    public ProviderPaymentResult charge(
            String customerId,
            Long amount,
            String currency
    ) {

        int attempt = chargeCount.incrementAndGet();

        System.out.println(
                ">>> FAKE PROVIDER CHARGE ATTEMPT #" + attempt +
                        " customer=" + customerId +
                        " amount=" + amount +
                        " thread=" + Thread.currentThread().getName()
        );

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return new ProviderPaymentResult(
                    false,
                    null,
                    "Provider call interrupted"
            );
        }

        return new ProviderPaymentResult(
                true,
                "FAKE-" + UUID.randomUUID(),
                null
        );
    }
}