-- 기존 플랜이 비활성 상태로 존재하면 활성화
UPDATE public.subscription_plan
SET
    is_active = true,
    plan_name = 'Monthly Plan',
    price = 9900,
    description = '월간 구독 플랜',
    updated_at = CURRENT_TIMESTAMP
WHERE billing_cycle = 'MONTHLY'
  AND is_active = false;

INSERT INTO public.subscription_plan (
    plan_name,
    price,
    billing_cycle,
    description,
    is_active
)
SELECT
    'Monthly Plan',
    9900,
    'MONTHLY',
    '월간 구독 플랜',
    true
    WHERE NOT EXISTS (
    SELECT 1
    FROM public.subscription_plan
    WHERE billing_cycle = 'MONTHLY'
);

UPDATE public.subscription_plan
SET
    is_active = true,
    plan_name = 'Yearly Plan',
    price = 99000,
    description = '연간 구독 플랜 (약 17% 할인)',
    updated_at = CURRENT_TIMESTAMP
WHERE billing_cycle = 'YEARLY'
  AND is_active = false;

INSERT INTO public.subscription_plan (
    plan_name,
    price,
    billing_cycle,
    description,
    is_active
)
SELECT
    'Yearly Plan',
    99000,
    'YEARLY',
    '연간 구독 플랜 (약 17% 할인)',
    true
    WHERE NOT EXISTS (
    SELECT 1
    FROM public.subscription_plan
    WHERE billing_cycle = 'YEARLY'
);