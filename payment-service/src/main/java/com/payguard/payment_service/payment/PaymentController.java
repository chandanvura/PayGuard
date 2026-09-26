package com.payguard.payment_service.payment;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {

        Payment payment = paymentService.createPayment(
                idempotencyKey,
                request.customerId(),
                request.amount(),
                request.currency().toUpperCase()
        );

        return ResponseEntity.ok(
                PaymentResponse.from(payment)
        );
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID paymentId
    ) {

        Payment payment =
                paymentService.getPayment(paymentId);

        return ResponseEntity.ok(
                PaymentResponse.from(payment)
        );
    }

    @PostMapping("/{paymentId}/reconcile")
    public ResponseEntity<PaymentResponse> reconcilePayment(
            @PathVariable UUID paymentId
    ) {

        Payment payment =
                paymentService.reconcilePayment(paymentId);

        return ResponseEntity.ok(
                PaymentResponse.from(payment)
        );
    }
}