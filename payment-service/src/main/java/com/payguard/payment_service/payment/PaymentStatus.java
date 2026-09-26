package com.payguard.payment_service.payment;

public enum PaymentStatus {

    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    UNKNOWN,
    REQUIRES_REVIEW
}