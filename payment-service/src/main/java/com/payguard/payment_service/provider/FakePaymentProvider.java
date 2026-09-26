package com.payguard.payment_service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FakePaymentProvider implements PaymentProvider {

    private static final Logger log =
            LoggerFactory.getLogger(FakePaymentProvider.class);

    private final AtomicInteger chargeCount =
            new AtomicInteger(0);

    private final Map<String, ProviderStatusResult> payments =
            new ConcurrentHashMap<>();

    @Override
    public ProviderPaymentResult charge(
            String idempotencyKey,
            String customerId,
            Long amount,
            String currency
    ) {

        int attempt = chargeCount.incrementAndGet();

        log.info(
                "provider_charge_attempt attempt={} idempotencyKey={} customerId={} amount={} currency={} thread={}",
                attempt,
                idempotencyKey,
                customerId,
                amount,
                currency,
                Thread.currentThread().getName()
        );

        /*
         * Idempotency at provider level.
         *
         * If this provider has already processed the same key,
         * return the previously stored result.
         */
        ProviderStatusResult existing =
                payments.get(idempotencyKey);

        if (existing != null) {

            log.info(
                    "provider_existing_payment idempotencyKey={} status={}",
                    idempotencyKey,
                    existing.status()
            );

            return toPaymentResult(existing);
        }

        /*
         * DEMO 1:
         *
         * Simulate a provider timeout AFTER the provider actually
         * processed the payment.
         *
         * PayGuard does not receive the SUCCESS response, so it must
         * initially mark the payment UNKNOWN.
         *
         * During reconciliation, getStatus() will discover SUCCESS.
         */
        if (idempotencyKey.startsWith("DEMO-TIMEOUT-")) {

            String providerReference =
                    "FAKE-" + UUID.randomUUID();

            ProviderStatusResult result =
                    new ProviderStatusResult(
                            ProviderPaymentStatus.SUCCESS,
                            providerReference,
                            null
                    );

            /*
             * Provider processed the payment successfully.
             */
            payments.put(
                    idempotencyKey,
                    result
            );

            log.warn(
                    "provider_timeout_after_success idempotencyKey={} providerReference={}",
                    idempotencyKey,
                    providerReference
            );

            /*
             * But PayGuard never receives that successful response.
             */
            throw new PaymentProviderException(
                    "Simulated provider timeout after processing payment"
            );
        }

        /*
         * DEMO 2:
         *
         * Deterministic provider failure.
         */
        if (idempotencyKey.startsWith("DEMO-FAIL-")) {

            ProviderStatusResult result =
                    new ProviderStatusResult(
                            ProviderPaymentStatus.FAILED,
                            null,
                            "Simulated provider decline"
                    );

            payments.put(
                    idempotencyKey,
                    result
            );

            log.warn(
                    "provider_payment_failed idempotencyKey={} reason={}",
                    idempotencyKey,
                    result.failureReason()
            );

            return toPaymentResult(result);
        }

        /*
         * NORMAL CASE:
         *
         * Everything succeeds.
         */
        String providerReference =
                "FAKE-" + UUID.randomUUID();

        ProviderStatusResult result =
                new ProviderStatusResult(
                        ProviderPaymentStatus.SUCCESS,
                        providerReference,
                        null
                );

        payments.put(
                idempotencyKey,
                result
        );

        log.info(
                "provider_payment_success idempotencyKey={} providerReference={}",
                idempotencyKey,
                providerReference
        );

        return toPaymentResult(result);
    }

    @Override
    public ProviderStatusResult getStatus(
            String idempotencyKey
    ) {

        ProviderStatusResult result =
                payments.getOrDefault(
                        idempotencyKey,
                        new ProviderStatusResult(
                                ProviderPaymentStatus.PENDING,
                                null,
                                null
                        )
                );

        log.info(
                "provider_status_lookup idempotencyKey={} status={}",
                idempotencyKey,
                result.status()
        );

        return result;
    }

    private ProviderPaymentResult toPaymentResult(
            ProviderStatusResult result
    ) {

        return new ProviderPaymentResult(
                result.status()
                        == ProviderPaymentStatus.SUCCESS,
                result.providerReference(),
                result.failureReason()
        );
    }
}