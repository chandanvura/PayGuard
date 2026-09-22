package com.payguard.payment_service.payment;


import com.payguard.payment_service.provider.PaymentProvider;
import com.payguard.payment_service.provider.ProviderPaymentResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentProvider paymentProvider
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
    }

    @Transactional(readOnly = true)
    public Payment getByIdempotencyKey(String idempotencyKey) {
        return paymentRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found for idempotency key: "
                                        + idempotencyKey
                        )
                );
    }

    @Transactional
    public Payment createPayment(
            String idempotencyKey,
            String customerId,
            Long amount,
            String currency
    ) {

        var existingPayment =
                paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existingPayment.isPresent()) {
            return existingPayment.get();
        }

        Payment payment = new Payment(
                idempotencyKey,
                customerId,
                amount,
                currency
        );

        payment.markProcessing();

        paymentRepository.save(payment);

        ProviderPaymentResult result =
                paymentProvider.charge(
                        customerId,
                        amount,
                        currency
                );

        if (result.successful()) {

            payment.markSuccessful(
                    result.providerReference()
            );

        } else {

            payment.markFailed(
                    result.failureReason()
            );
        }

        return paymentRepository.save(payment);
    }
}