-----------------------------------------------------------
-- 1. MEMBERSHIP TYPES
-----------------------------------------------------------
INSERT INTO membership_types (code, name, price, duration_days, description) VALUES
('REGULAR_MONTHLY', 'Karnet miesieczny', 149.99, 30, 'Dostep do silowni przez 30 dni'),
('STUDENT_MONTHLY', 'Karnet studencki', 99.99, 30, 'Znizka dla studentow'),
('REGULAR_YEARLY', 'Karnet roczny', 1490.99, 365, 'Najlepsza opcja na caly rok'),
('STUDENT_YEARLY', 'Karnet studencki roczny', 999.99, 365, 'Znizka dla studentow na caly rok');

-----------------------------------------------------------
-- 2. USERS
-----------------------------------------------------------
INSERT INTO users (email, password_hash, role, first_name, last_name, phone_number, account_balance) VALUES
('admin@fitmanager.pl', 'hashed_pwd_admin', 'ADMIN', 'Olaf', 'Slowik', '123456789', 0.00),
('trener@fitmanager.pl', 'hashed_pwd_trainer', 'TRAINER', 'Jan', 'Kowalski', '987654321', 0.00),
('klient@fitmanager.pl', 'hashed_pwd_client', 'CLIENT', 'Anna', 'Nowak', '555444333', 200.00);

-----------------------------------------------------------
-- 3. RELATION
-----------------------------------------------------------
INSERT INTO trainer_clients (trainer_id, client_id) VALUES (2, 3);

-----------------------------------------------------------
-- 4. MEMBERSHIP (AKTYWNY KARNET)
-----------------------------------------------------------
INSERT INTO memberships (user_id, membership_type_id, start_date, end_date, status)
VALUES (
    3,
    (SELECT id FROM membership_types WHERE code = 'REGULAR_MONTHLY'),
    CURRENT_DATE,
    CURRENT_DATE + INTERVAL '30 days',
    'ACTIVE'
);

-----------------------------------------------------------
-- 5. PAYMENT
-----------------------------------------------------------
INSERT INTO payments (user_id, membership_id, amount, status)
VALUES (
    3,
    (SELECT id FROM memberships WHERE user_id = 3 AND status = 'ACTIVE'),
    149.99,
    'SUCCESS'
);

-----------------------------------------------------------
-- 6. TRAINING SESSION
-----------------------------------------------------------
INSERT INTO training_sessions (trainer_id, title, start_time, end_time, max_participants)
VALUES (
    2,
    'Trening Siłowy - Nogi',
    CURRENT_TIMESTAMP + INTERVAL '1 day',
    CURRENT_TIMESTAMP + INTERVAL '1 day 1 hour',
    1
);

-----------------------------------------------------------
-- 7. RESERVATION
-----------------------------------------------------------
INSERT INTO reservations (user_id, session_id, status)
VALUES (3, 1, 'CONFIRMED');

-----------------------------------------------------------
-- 8. PROGRESS
-----------------------------------------------------------
INSERT INTO progress_logs (client_id, trainer_id, log_date, weight, notes)
VALUES (3, 2, CURRENT_DATE, 62.5, 'Poprawa mobilności w stawach skokowych.');

-----------------------------------------------------------
-- 9. TRAINING PLAN
-----------------------------------------------------------
INSERT INTO training_plans (trainer_id, client_id, title, description)
VALUES (2, 3, 'Plan FBW', '1. Przysiady 3x10, 2. Martwy ciąg 3x8');