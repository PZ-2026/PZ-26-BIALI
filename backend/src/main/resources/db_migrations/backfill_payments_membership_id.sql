-- Backfill membership_id in payments where it's NULL
BEGIN;

-- 1) Try to match payments to memberships that cover the payment_date
UPDATE payments p
SET membership_id = m.id
FROM memberships m
WHERE p.membership_id IS NULL
  AND p.user_id = m.user_id
  AND (p.payment_date::date BETWEEN m.start_date AND m.end_date);

-- 2) For remaining NULLs, assign the most recent membership for that user (if any)
WITH latest_membership AS (
  SELECT m2.user_id, m2.id,
         ROW_NUMBER() OVER (PARTITION BY m2.user_id ORDER BY m2.end_date DESC) rn
  FROM memberships m2
)
UPDATE payments p
SET membership_id = lm.id
FROM latest_membership lm
WHERE p.membership_id IS NULL
  AND p.user_id = lm.user_id
  AND lm.rn = 1;

COMMIT;

-- Verify: SELECT id, user_id, membership_id, amount, payment_date FROM payments ORDER BY id;
