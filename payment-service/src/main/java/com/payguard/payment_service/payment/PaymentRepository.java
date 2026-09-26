package com.payguard.payment_service.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository
        extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(
            String idempotencyKey
    );

    List<Payment> findByStatus(
            PaymentStatus status
    );

    List<Payment> findByStatusAndNextReconciliationAtLessThanEqual(
            PaymentStatus status,
            OffsetDateTime now
    );
}