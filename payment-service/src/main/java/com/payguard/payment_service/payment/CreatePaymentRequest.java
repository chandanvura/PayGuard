package com.payguard.payment_service.payment;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(

        @NotBlank
        String customerId,

        @NotNull
        @Positive
        Long amount,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency
) {
}
