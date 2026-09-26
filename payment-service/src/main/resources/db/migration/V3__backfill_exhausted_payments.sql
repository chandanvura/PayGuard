UPDATE payments
SET status = 'REQUIRES_REVIEW',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'UNKNOWN'
  AND reconciliation_attempts >= 5
  AND next_reconciliation_at IS NULL;