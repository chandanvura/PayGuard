package com.payguard.payment_service.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@ConditionalOnProperty(
        name = "payguard.failure-simulation.enabled",
        havingValue = "true"
)
public class FailureSimulationController {

    @GetMapping("/simulate-failure")
    public ResponseEntity<String> simulateFailure() {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Simulated PayGuard failure");
    }
}