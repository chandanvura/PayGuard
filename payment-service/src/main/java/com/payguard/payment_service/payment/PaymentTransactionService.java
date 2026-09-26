package com.payguard.payment_service.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;

    public PaymentTransactionService(
            PaymentRepository paymentRepository
    ) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment createProcessingPayment(
            String idempotencyKey,
            String customerId,
            Long amount,
            String currency
    ) {

        Payment payment = new Payment(
                idempotencyKey,
                customerId,
                amount,
                currency
        );

        payment.markProcessing();

        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public Payment markSuccessful(
            UUID paymentId,
            String providerReference
    ) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(paymentId)
                );

        payment.markSuccessful(providerReference);

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markFailed(
            UUID paymentId,
            String failureReason
    ) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(paymentId)
                );

        payment.markFailed(failureReason);

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markUnknown(
            UUID paymentId,
            String reason
    ) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(paymentId)
                );

        payment.markUnknown(reason);

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment scheduleNextReconciliation(
            UUID paymentId
    ) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(paymentId)
                );

        payment.scheduleNextReconciliation();

        return paymentRepository.save(payment);
    }
}