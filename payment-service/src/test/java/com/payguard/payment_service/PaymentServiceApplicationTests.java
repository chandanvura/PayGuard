package com.payguard.payment_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
		properties = "payguard.reconciliation.enabled=false"
)
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
