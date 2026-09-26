package com.payguard.payment_service.payment;

import com.payguard.payment_service.provider.PaymentProvider;
import com.payguard.payment_service.provider.PaymentProviderException;
import com.payguard.payment_service.provider.ProviderPaymentStatus;
import com.payguard.payment_service.provider.ProviderPaymentResult;
import com.payguard.payment_service.provider.ProviderStatusResult;

import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@SpringBootTest(
        properties = "payguard.reconciliation.enabled=false"
)
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentProvider paymentProvider;

    @Test
    void duplicateSequentialRequestsShouldChargeProviderOnlyOnce() {

        String idempotencyKey =
                "TEST-" + UUID.randomUUID();

        when(paymentProvider.charge(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                new ProviderPaymentResult(
                        true,
                        "PROVIDER-TEST-123",
                        null
                )
        );

        Payment first = paymentService.createPayment(
                idempotencyKey,
                "CUST-TEST",
                50000L,
                "INR"
        );

        Payment second = paymentService.createPayment(
                idempotencyKey,
                "CUST-TEST",
                50000L,
                "INR"
        );

        assertThat(first.getId())
                .isEqualTo(second.getId());

        assertThat(first.getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);

        assertThat(second.getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);

        assertThat(
                paymentRepository.findByIdempotencyKey(idempotencyKey)
        ).isPresent();

        verify(
                paymentProvider,
                times(1)
        ).charge(
                idempotencyKey,
                "CUST-TEST",
                50000L,
                "INR"
        );
    }

    @Test
    void concurrentRequestsShouldCreateOnePaymentAndChargeProviderOnce()
            throws Exception {

        String idempotencyKey =
                "CONCURRENT-TEST-" + UUID.randomUUID();

        when(paymentProvider.charge(
                any(),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {

            // Widen the concurrency window.
            Thread.sleep(500);

            return new ProviderPaymentResult(
                    true,
                    "PROVIDER-CONCURRENT-123",
                    null
            );
        });

        int numberOfRequests = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfRequests);

        CountDownLatch ready =
                new CountDownLatch(numberOfRequests);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<Payment>> futures =
                new ArrayList<>();

        try {

            for (int i = 0; i < numberOfRequests; i++) {

                futures.add(
                        executor.submit(() -> {

                            ready.countDown();

                            start.await();

                            return paymentService.createPayment(
                                    idempotencyKey,
                                    "CUST-CONCURRENT-TEST",
                                    50000L,
                                    "INR"
                            );
                        })
                );
            }

            assertThat(
                    ready.await(5, TimeUnit.SECONDS)
            ).isTrue();

            start.countDown();

            List<Payment> results =
                    new ArrayList<>();

            for (Future<Payment> future : futures) {

                Payment payment =
                        assertDoesNotThrow(
                                () -> future.get(
                                        10,
                                        TimeUnit.SECONDS
                                )
                        );

                results.add(payment);
            }

            assertThat(results)
                    .hasSize(numberOfRequests);

            UUID paymentId =
                    results.getFirst().getId();

            assertThat(results)
                    .allMatch(payment ->
                            payment.getId().equals(paymentId)
                    );

            long databaseCount =
                    paymentRepository
                            .findAll()
                            .stream()
                            .filter(payment ->
                                    payment
                                            .getIdempotencyKey()
                                            .equals(idempotencyKey)
                            )
                            .count();

            assertThat(databaseCount)
                    .isEqualTo(1);

            verify(
                    paymentProvider,
                    times(1)
            ).charge(
                    idempotencyKey,
                    "CUST-CONCURRENT-TEST",
                    50000L,
                    "INR"
            );

        } finally {

            executor.shutdownNow();
        }
    }

    @Test
    void providerRejectionShouldMarkPaymentAsFailed() {

        String idempotencyKey =
                "FAILED-TEST-" + UUID.randomUUID();

        when(paymentProvider.charge(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                new ProviderPaymentResult(
                        false,
                        null,
                        "CARD_DECLINED"
                )
        );

        Payment payment =
                paymentService.createPayment(
                        idempotencyKey,
                        "CUST-FAILED",
                        25000L,
                        "INR"
                );

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        assertThat(payment.getProviderReference())
                .isNull();

        assertThat(payment.getFailureReason())
                .isEqualTo("CARD_DECLINED");

        verify(
                paymentProvider,
                times(1)
        ).charge(
                idempotencyKey,
                "CUST-FAILED",
                25000L,
                "INR"
        );
    }

    @Test
    void providerExceptionShouldMarkPaymentAsUnknown() {

        String idempotencyKey =
                "UNKNOWN-TEST-" + UUID.randomUUID();

        when(paymentProvider.charge(
                any(),
                any(),
                any(),
                any()
        )).thenThrow(
                new PaymentProviderException(
                        "Provider connection timed out"
                )
        );

        Payment payment =
                paymentService.createPayment(
                        idempotencyKey,
                        "CUST-UNKNOWN",
                        75000L,
                        "INR"
                );

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.UNKNOWN);

        assertThat(payment.getProviderReference())
                .isNull();

        assertThat(payment.getFailureReason())
                .isEqualTo(
                        "Provider connection timed out"
                );

        verify(
                paymentProvider,
                times(1)
        ).charge(
                idempotencyKey,
                "CUST-UNKNOWN",
                75000L,
                "INR"
        );
    }

    @Test
    void reconciliationShouldConvertUnknownPaymentToSuccess() {

        String idempotencyKey =
                "RECONCILE-SUCCESS-" + UUID.randomUUID();

        when(paymentProvider.charge(
                any(),
                any(),
                any(),
                any()
        )).thenThrow(
                new PaymentProviderException(
                        "Provider response timed out"
                )
        );

        Payment unknownPayment =
                paymentService.createPayment(
                        idempotencyKey,
                        "CUST-RECONCILE",
                        100000L,
                        "INR"
                );

        assertThat(unknownPayment.getStatus())
                .isEqualTo(PaymentStatus.UNKNOWN);

        when(paymentProvider.getStatus(idempotencyKey))
                .thenReturn(
                        new ProviderStatusResult(
                                ProviderPaymentStatus.SUCCESS,
                                "PROVIDER-RECONCILED-123",
                                null
                        )
                );

        Payment reconciled =
                paymentService.reconcilePayment(
                        unknownPayment.getId()
                );

        assertThat(reconciled.getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);

        assertThat(reconciled.getProviderReference())
                .isEqualTo(
                        "PROVIDER-RECONCILED-123"
                );

        assertThat(reconciled.getFailureReason())
                .isNull();

        verify(
                paymentProvider,
                times(1)
        ).getStatus(idempotencyKey);
    }

    @Test
    void reconciliationShouldConvertUnknownPaymentToFailed() {

        String idempotencyKey =
                "RECONCILE-FAILED-" + UUID.randomUUID();

        when(paymentProvider.charge(
                any(),
                any(),
                any(),
                any()
        )).thenThrow(
                new PaymentProviderException(
                        "Provider response timed out"
                )
        );

        Payment unknownPayment =
                paymentService.createPayment(
                        idempotencyKey,
                        "CUST-RECONCILE-FAILED",
                        200000L,
                        "INR"
                );

        assertThat(unknownPayment.getStatus())
                .isEqualTo(PaymentStatus.UNKNOWN);

        when(paymentProvider.getStatus(idempotencyKey))
                .thenReturn(
                        new ProviderStatusResult(
                                ProviderPaymentStatus.FAILED,
                                null,
                                "INSUFFICIENT_FUNDS"
                        )
                );

        Payment reconciled =
                paymentService.reconcilePayment(
                        unknownPayment.getId()
                );

        assertThat(reconciled.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        assertThat(reconciled.getProviderReference())
                .isNull();

        assertThat(reconciled.getFailureReason())
                .isEqualTo(
                        "INSUFFICIENT_FUNDS"
                );
    }

    @Test
    void reconciliationShouldKeepPaymentUnknownWhenProviderIsPending() {

        String idempotencyKey =
                "RECONCILE-PENDING-" + UUID.randomUUID();

        when(paymentProvider.charge(
                any(),
                any(),
                any(),
                any()
        )).thenThrow(
                new PaymentProviderException(
                        "Provider response timed out"
                )
        );

        Payment unknownPayment =
                paymentService.createPayment(
                        idempotencyKey,
                        "CUST-PENDING",
                        300000L,
                        "INR"
                );

        assertThat(unknownPayment.getStatus())
                .isEqualTo(PaymentStatus.UNKNOWN);

        when(paymentProvider.getStatus(idempotencyKey))
                .thenReturn(
                        new ProviderStatusResult(
                                ProviderPaymentStatus.PENDING,
                                null,
                                null
                        )
                );

        Payment reconciled =
                paymentService.reconcilePayment(
                        unknownPayment.getId()
                );

        assertThat(reconciled.getStatus())
                .isEqualTo(PaymentStatus.UNKNOWN);

        verify(
                paymentProvider,
                times(1)
        ).getStatus(idempotencyKey);
    }

    @Test
    void reconciliationShouldStopAfterMaximumAttempts() {

        String idempotencyKey =
                "RECONCILIATION-EXHAUSTION-" + UUID.randomUUID();

        // Initial provider charge has an uncertain outcome.
        when(paymentProvider.charge(
                eq(idempotencyKey),
                anyString(),
                anyLong(),
                anyString()
        )).thenThrow(
                new PaymentProviderException(
                        "Provider timed out"
                )
        );

        Payment payment =
                paymentService.createPayment(
                        idempotencyKey,
                        "CUSTOMER-001",
                        149900L,
                        "INR"
                );

        // The timeout caused the first reconciliation attempt
        // to be scheduled.
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.UNKNOWN);

        assertThat(payment.getReconciliationAttempts())
                .isEqualTo(1);

        assertThat(payment.getNextReconciliationAt())
                .isNotNull();

        // Every provider status check remains unresolved.
        when(paymentProvider.getStatus(idempotencyKey))
                .thenReturn(
                        new ProviderStatusResult(
                                ProviderPaymentStatus.PENDING,
                                null,
                                null
                        )
                );

        // Attempt 1 already exists.
        // Four more reconciliations take us to attempt 5.
        for (int i = 0; i < 4; i++) {
            payment =
                    paymentService.reconcilePayment(
                            payment.getId()
                    );
        }

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.UNKNOWN);

        assertThat(payment.getReconciliationAttempts())
                .isEqualTo(5);

        assertThat(payment.getNextReconciliationAt())
                .isNotNull();

        // Provider is STILL pending.
        //
        // This call tries to schedule another reconciliation,
        // but the maximum has already been reached.
        // Provider is STILL pending.
//
// This call tries to schedule another reconciliation,
// but the maximum has already been reached.
        payment =
                paymentService.reconcilePayment(
                        payment.getId()
                );

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.REQUIRES_REVIEW);

        assertThat(payment.getReconciliationAttempts())
                .isEqualTo(5);

        assertThat(payment.getNextReconciliationAt())
                .isNull();

        assertThat(payment.isReconciliationExhausted())
                .isTrue();
    }
}
