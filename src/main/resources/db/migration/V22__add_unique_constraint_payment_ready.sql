CREATE UNIQUE INDEX uk_payment_ready_unique
    ON payment (user_id, plan_id, provider)
    WHERE status = 'READY';