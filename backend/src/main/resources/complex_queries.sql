-- 1. SZCZEGÓŁY TRENERA RAZEM Z INFORMACJAMI O KLIENTACH
SELECT 
    t.id AS trainer_id,
    t.first_name AS trainer_first_name,
    t.last_name AS trainer_last_name,
    t.email AS trainer_email,
    COUNT(DISTINCT tc.client_id) AS total_clients,
    COUNT(DISTINCT tr.id) AS active_rentals,
    COUNT(DISTINCT ts.id) AS sessions_count
FROM users t
LEFT JOIN trainer_clients tc ON t.id = tc.trainer_id
LEFT JOIN trainer_rentals tr ON t.id = tr.trainer_id AND tr.status = 'ACTIVE'
LEFT JOIN training_sessions ts ON t.id = ts.trainer_id
WHERE t.role = 'TRAINER'
GROUP BY t.id, t.first_name, t.last_name, t.email
ORDER BY total_clients DESC;

-- 2. KLIENCI Z AKTYWNYM KARNETU I HISTORIĄ PŁATNOŚCI
SELECT 
    u.id AS client_id,
    u.first_name,
    u.last_name,
    u.email,
    mt.name AS membership_type,
    m.start_date AS membership_start,
    m.end_date AS membership_end,
    COUNT(p.id) AS payment_count,
    SUM(p.amount) AS total_spent,
    AVG(p.amount) AS avg_payment
FROM users u
INNER JOIN memberships m ON u.id = m.user_id AND m.status = 'ACTIVE'
INNER JOIN membership_types mt ON m.membership_type_id = mt.id
LEFT JOIN payments p ON u.id = p.user_id
WHERE u.role = 'CLIENT'
GROUP BY u.id, u.first_name, u.last_name, u.email, mt.name, m.start_date, m.end_date
ORDER BY total_spent DESC;

-- 3. SESJE TRENINGOWE Z LICZBĄ REZERWACJI I INFORMACJAMI TRENERA
SELECT 
    ts.id AS session_id,
    ts.title,
    ts.start_time,
    ts.end_time,
    CONCAT(u.first_name, ' ', u.last_name) AS trainer_name,
    u.email AS trainer_email,
    ts.max_participants,
    COUNT(r.id) AS reserved_spots,
    (ts.max_participants - COUNT(r.id)) AS available_spots,
    ROUND(COUNT(r.id)::numeric / ts.max_participants * 100, 2) AS occupancy_percent
FROM training_sessions ts
INNER JOIN users u ON ts.trainer_id = u.id
LEFT JOIN reservations r ON ts.id = r.session_id AND r.status = 'CONFIRMED'
GROUP BY ts.id, ts.title, ts.start_time, ts.end_time, u.first_name, u.last_name, u.email, ts.max_participants
ORDER BY ts.start_time DESC;

-- 4. POSTĘP KLIENTÓW - RAPORT Z OSTATNIM POMIAREM I TRENERA
SELECT 
    c.id AS client_id,
    CONCAT(c.first_name, ' ', c.last_name) AS client_name,
    c.email AS client_email,
    CONCAT(t.first_name, ' ', t.last_name) AS trainer_name,
    pl.log_date,
    pl.weight,
    pl.notes,
    ROW_NUMBER() OVER (PARTITION BY pl.client_id ORDER BY pl.log_date DESC) AS weight_measurement_rank
FROM progress_logs pl
INNER JOIN users c ON pl.client_id = c.id
INNER JOIN users t ON pl.trainer_id = t.id
WHERE pl.log_date >= CURRENT_DATE - INTERVAL '90 days'
ORDER BY c.id, pl.log_date DESC;

-- 5. RENTALE TRENERÓW - PRZYCHODY I OKRESY UMÓW
SELECT 
    CONCAT(trainer.first_name, ' ', trainer.last_name) AS trainer_name,
    trainer.email AS trainer_email,
    CONCAT(client.first_name, ' ', client.last_name) AS client_name,
    tr.start_date,
    tr.end_date,
    (tr.end_date - tr.start_date) AS duration_days,
    tr.status,
    COUNT(p.id) AS payments_count,
    COALESCE(SUM(p.amount), 0) AS rental_revenue
FROM trainer_rentals tr
INNER JOIN users trainer ON tr.trainer_id = trainer.id
INNER JOIN users client ON tr.client_id = client.id
LEFT JOIN payments p ON client.id = p.user_id 
    AND p.payment_date >= tr.start_date 
    AND p.payment_date <= tr.end_date
    AND p.status = 'SUCCESS'
GROUP BY trainer.id, trainer.first_name, trainer.last_name, trainer.email,
         client.id, client.first_name, client.last_name,
         tr.id, tr.start_date, tr.end_date, tr.status
ORDER BY trainer.last_name, tr.start_date DESC;

-- 6. PLANY TRENINGOWE Z DANYMI KLIENTA I TRENERA
SELECT 
    tp.id AS plan_id,
    tp.title AS plan_title,
    CONCAT(trainer.first_name, ' ', trainer.last_name) AS trainer_name,
    CONCAT(client.first_name, ' ', client.last_name) AS client_name,
    client.email AS client_email,
    tp.created_at,
    COUNT(DISTINCT ts.id) AS assigned_sessions,
    COUNT(DISTINCT r.id) AS total_reservations
FROM training_plans tp
INNER JOIN users trainer ON tp.trainer_id = trainer.id
INNER JOIN users client ON tp.client_id = client.id
LEFT JOIN training_sessions ts ON trainer.id = ts.trainer_id
LEFT JOIN reservations r ON ts.id = r.session_id AND r.user_id = client.id
GROUP BY tp.id, tp.title, tp.description, trainer.id, trainer.first_name, trainer.last_name,
         client.id, client.first_name, client.last_name, client.email, tp.created_at
ORDER BY tp.created_at DESC;

-- 7. STATYSTYKA FINANSOWA - PRZYCHODY MIESIĘCZNE
SELECT 
    DATE_TRUNC('month', p.payment_date)::DATE AS month,
    COUNT(DISTINCT p.id) AS payment_count,
    SUM(CASE WHEN p.status = 'SUCCESS' THEN p.amount ELSE 0 END) AS successful_revenue,
    SUM(CASE WHEN p.status = 'FAILED' THEN p.amount ELSE 0 END) AS failed_attempts,
    SUM(CASE WHEN p.status = 'PENDING' THEN p.amount ELSE 0 END) AS pending_revenue,
    COUNT(DISTINCT p.user_id) AS unique_payers,
    ROUND(AVG(CASE WHEN p.status = 'SUCCESS' THEN p.amount END)::numeric, 2) AS avg_successful_payment
FROM payments p
WHERE p.payment_date >= CURRENT_DATE - INTERVAL '1 year'
GROUP BY DATE_TRUNC('month', p.payment_date)
ORDER BY month DESC;

-- 8. KLIENCI BEZ AKTYWNEGO KARNETU - ANALIZA REZYGNACJI
SELECT 
    u.id,
    CONCAT(u.first_name, ' ', u.last_name) AS client_name,
    u.email,
    u.created_at,
    COUNT(DISTINCT m.id) AS total_memberships_had,
    MAX(m.end_date) AS last_membership_end,
    CURRENT_DATE - MAX(m.end_date) AS days_since_expiry,
    COUNT(DISTINCT r.id) AS total_reservations_made,
    u.account_balance
FROM users u
LEFT JOIN memberships m ON u.id = m.user_id
LEFT JOIN reservations r ON u.id = r.user_id
WHERE u.role = 'CLIENT'
    AND NOT EXISTS (
        SELECT 1 FROM memberships m2 
        WHERE m2.user_id = u.id AND m2.status = 'ACTIVE'
    )
GROUP BY u.id, u.first_name, u.last_name, u.email, u.created_at, u.account_balance
ORDER BY days_since_expiry DESC NULLS LAST;

-- 9. RAPORT SESJI TRENINGOWYCH - WSZYSTKIE DETALE
SELECT 
    ts.id,
    ts.title,
    ts.start_time,
    ts.end_time,
    CONCAT(trainer.first_name, ' ', trainer.last_name) AS trainer_name,
    trainer.email,
    ts.max_participants,
    COUNT(DISTINCT r.id) AS confirmed_participants,
    STRING_AGG(DISTINCT CONCAT(client.first_name, ' ', client.last_name), ', ') AS participants_names,
    CASE 
        WHEN COUNT(r.id) >= ts.max_participants THEN 'FULL'
        WHEN COUNT(r.id) = 0 THEN 'EMPTY'
        ELSE 'PARTIAL'
    END AS session_status
FROM training_sessions ts
INNER JOIN users trainer ON ts.trainer_id = trainer.id
LEFT JOIN reservations r ON ts.id = r.session_id AND r.status = 'CONFIRMED'
LEFT JOIN users client ON r.user_id = client.id
WHERE ts.start_time >= CURRENT_DATE
GROUP BY ts.id, ts.title, ts.start_time, ts.end_time, 
         trainer.id, trainer.first_name, trainer.last_name, trainer.email, ts.max_participants
ORDER BY ts.start_time;
