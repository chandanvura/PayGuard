CREATE TABLE payments (
                          id UUID PRIMARY KEY,

                          idempotency_key VARCHAR(100) NOT NULL UNIQUE,

                          customer_id VARCHAR(100) NOT NULL,

                          amount BIGINT NOT NULL,

                          currency VARCHAR(3) NOT NULL,

                          status VARCHAR(30) NOT NULL,

                          provider_reference VARCHAR(100),

                          failure_reason VARCHAR(500),

                          created_at TIMESTAMP WITH TIME ZONE NOT NULL,

                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);