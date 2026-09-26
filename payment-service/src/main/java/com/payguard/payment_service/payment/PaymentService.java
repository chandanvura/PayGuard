package com.payguard.payment_service.payment;

import com.payguard.payment_service.provider.PaymentProvider;
import com.payguard.payment_service.provider.PaymentProviderException;
import com.payguard.payment_service.provider.ProviderPaymentResult;
import com.payguard.payment_service.provider.ProviderStatusResult;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentMetrics paymentMetrics;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentProvider paymentProvider,
            PaymentTransactionService paymentTransactionService,
            PaymentMetrics paymentMetrics
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.paymentTransactionService = paymentTransactionService;
        this.paymentMetrics = paymentMetrics;
    }


    /*
     * -------------------------------------------------------
     * CREATE PAYMENT
     * -------------------------------------------------------
     *
     * Responsibilities:
     *
     * 1. Protect against duplicate idempotency keys.
     * 2. Create the payment in PROCESSING state.
     * 3. Call the external payment provider.
     * 4. Convert the provider result into:
     *
     *      SUCCESS
     *      FAILED
     *      UNKNOWN
     *
     * 5. Record business metrics.
     */
    public Payment createPayment(
            String idempotencyKey,
            String customerId,
            Long amount,
            String currency
    ) {

        /*
         * First idempotency check.
         *
         * Example:
         *
         * Customer clicks Pay again with the same
         * Idempotency-Key.
         *
         * We return the existing logical payment.
         */
        var existingPayment =
                paymentRepository.findByIdempotencyKey(
                        idempotencyKey
                );

        if (existingPayment.isPresent()) {
            return existingPayment.get();
        }


        Payment payment;

        try {

            /*
             * Create the payment using a short
             * database transaction.
             *
             * createProcessingPayment() also performs
             * saveAndFlush(), so PostgreSQL evaluates
             * the UNIQUE idempotency constraint now.
             */
            payment =
                    paymentTransactionService
                            .createProcessingPayment(
                                    idempotencyKey,
                                    customerId,
                                    amount,
                                    currency
                            );


            /*
             * Only increment this AFTER PostgreSQL
             * successfully creates a genuinely new
             * logical payment.
             */
            paymentMetrics.paymentCreated();

        } catch (DataIntegrityViolationException exception) {

            /*
             * CONCURRENT IDEMPOTENCY RACE
             *
             * Example:
             *
             * Request A ─┐
             *            ├── same key
             * Request B ─┘
             *
             * Both may pass the first SELECT.
             *
             * PostgreSQL's UNIQUE constraint decides
             * which one actually creates the row.
             *
             * The loser comes here and returns the
             * payment created by the winner.
             *
             * IMPORTANT:
             * No business metric is incremented here.
             */
            return paymentRepository
                    .findByIdempotencyKey(
                            idempotencyKey
                    )
                    .orElseThrow(() -> exception);
        }


        ProviderPaymentResult result;

        try {

            /*
             * External provider call.
             *
             * The provider receives the same
             * idempotency key.
             */
            result =
                    paymentProvider.charge(
                            idempotencyKey,
                            customerId,
                            amount,
                            currency
                    );

        } catch (PaymentProviderException exception) {

            /*
             * Provider communication failed.
             *
             * We cannot safely say FAILED because
             * the provider may actually have charged
             * the customer before the timeout occurred.
             *
             * Therefore:
             *
             * PROCESSING -> UNKNOWN
             */
            Payment unknownPayment =
                    paymentTransactionService.markUnknown(
                            payment.getId(),
                            exception.getMessage()
                    );

            paymentMetrics.paymentUnknown();

            return unknownPayment;
        }


        /*
         * Provider gave us a definite result.
         */
        if (result.successful()) {

            Payment successfulPayment =
                    paymentTransactionService.markSuccessful(
                            payment.getId(),
                            result.providerReference()
                    );

            paymentMetrics.paymentSuccessful();

            return successfulPayment;
        }


        /*
         * Provider explicitly rejected the payment.
         */
        Payment failedPayment =
                paymentTransactionService.markFailed(
                        payment.getId(),
                        result.failureReason()
                );

        paymentMetrics.paymentFailed();

        return failedPayment;
    }


    /*
     * -------------------------------------------------------
     * GET PAYMENT
     * -------------------------------------------------------
     *
     * Used by:
     *
     * GET /api/v1/payments/{paymentId}
     */
    public Payment getPayment(UUID paymentId) {

        return paymentRepository
                .findById(paymentId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(
                                paymentId
                        )
                );
    }


    /*
     * -------------------------------------------------------
     * RECONCILE PAYMENT
     * -------------------------------------------------------
     *
     * Used by:
     *
     * 1. Manual reconciliation endpoint
     * 2. Background reconciliation worker
     *
     * Only UNKNOWN payments need reconciliation.
     */
    public Payment reconcilePayment(UUID paymentId) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(paymentId)
                );

        /*
         * Reconciliation only applies to UNKNOWN payments.
         */
        if (payment.getStatus() != PaymentStatus.UNKNOWN) {
            return payment;
        }

        /*
         * We are now making a real reconciliation attempt.
         */
        paymentMetrics.reconciliationAttempt();

        ProviderStatusResult providerStatus;

        try {

            providerStatus = paymentProvider.getStatus(
                    payment.getIdempotencyKey()
            );

        } catch (PaymentProviderException exception) {

            /*
             * Provider is still unreachable.
             *
             * Payment remains UNKNOWN and we schedule another
             * reconciliation attempt using exponential backoff.
             */
            paymentMetrics.reconciliationProviderError();

            Payment rescheduled =
                    paymentTransactionService
                            .scheduleNextReconciliation(
                                    payment.getId()
                            );

            if (rescheduled.isReconciliationExhausted()) {
                paymentMetrics.reconciliationExhausted();
            }

            return rescheduled;
        }

        return switch (providerStatus.status()) {

            case SUCCESS -> {

                Payment successful =
                        paymentTransactionService
                                .markSuccessful(
                                        payment.getId(),
                                        providerStatus.providerReference()
                                );

                /*
                 * Payment reached SUCCESS through reconciliation.
                 */
                paymentMetrics.reconciliationSuccessful();
                paymentMetrics.paymentSuccessful();

                yield successful;
            }

            case FAILED -> {

                Payment failed =
                        paymentTransactionService
                                .markFailed(
                                        payment.getId(),
                                        providerStatus.failureReason()
                                );

                /*
                 * Payment reached FAILED through reconciliation.
                 */
                paymentMetrics.reconciliationFailed();
                paymentMetrics.paymentFailed();

                yield failed;
            }

            case PENDING -> {

                /*
                 * Provider still doesn't have a final answer.
                 */
                paymentMetrics.reconciliationPending();

                Payment rescheduled =
                        paymentTransactionService
                                .scheduleNextReconciliation(
                                        payment.getId()
                                );

                if (rescheduled.isReconciliationExhausted()) {
                    paymentMetrics.reconciliationExhausted();
                }

                yield rescheduled;
            }
        };
    }
}