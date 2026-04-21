INSERT INTO public.subscription_plan (
    plan_name,
    price,
    billing_cycle,
    description,
    is_active
)
SELECT 'Monthly Plan', 9900, 'MONTHLY', '월간 구독 플랜', true
    WHERE NOT EXISTS (
    SELECT 1 FROM public.subscription_plan WHERE billing_cycle = 'MONTHLY'
);

INSERT INTO public.subscription_plan (
    plan_name,
    price,
    billing_cycle,
    description,
    is_active
)
SELECT 'Yearly Plan', 99000, 'YEARLY', '연간 구독 플랜 (약 17% 할인)', true
    WHERE NOT EXISTS (
    SELECT 1 FROM public.subscription_plan WHERE billing_cycle = 'YEARLY'
);