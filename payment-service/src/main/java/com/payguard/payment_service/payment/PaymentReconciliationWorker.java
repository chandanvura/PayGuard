package com.payguard.payment_service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "payguard.reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentReconciliationWorker {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PaymentReconciliationWorker.class
            );

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public PaymentReconciliationWorker(
            PaymentRepository paymentRepository,
            PaymentService paymentService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @Scheduled(
            fixedDelayString =
                    "${payguard.reconciliation.delay-ms:10000}"
    )
    public void reconcileUnknownPayments() {

        List<Payment> paymentsToReconcile =
                paymentRepository
                        .findByStatusAndNextReconciliationAtLessThanEqual(
                                PaymentStatus.UNKNOWN,
                                OffsetDateTime.now()
                        );

        if (paymentsToReconcile.isEmpty()) {
            return;
        }

        log.info(
                "reconciliation_batch_started eligiblePayments={}",
                paymentsToReconcile.size()
        );

        for (Payment payment : paymentsToReconcile) {

            try {

                Payment reconciled =
                        paymentService.reconcilePayment(
                                payment.getId()
                        );

                log.info(
                        "payment_reconciliation_completed paymentId={} status={} attempts={} nextReconciliationAt={}",
                        payment.getId(),
                        reconciled.getStatus(),
                        reconciled.getReconciliationAttempts(),
                        reconciled.getNextReconciliationAt()
                );

            } catch (Exception exception) {

                log.error(
                        "payment_reconciliation_failed paymentId={} reason={}",
                        payment.getId(),
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}