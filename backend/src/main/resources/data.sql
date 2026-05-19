-----------------------------------------------------------
-- 1. MEMBERSHIP TYPES
-----------------------------------------------------------
INSERT INTO membership_types (code, name, price, duration_days, description) VALUES
('REGULAR_MONTHLY', 'Karnet miesięczny', 149.99, 30, 'Dostęp do siłowni przez 30 dni'),
('STUDENT_MONTHLY', 'Karnet studencki', 99.99, 30, 'Zniżka dla studentów'),
('REGULAR_YEARLY', 'Karnet roczny', 1490.99, 365, 'Najlepsza opcja na cały rok'),
('STUDENT_YEARLY', 'Karnet studencki roczny', 999.99, 365, 'Zniżka dla studentów na cały rok');

-----------------------------------------------------------
-- 2. USERS
-----------------------------------------------------------
INSERT INTO users (email, password_hash, role, first_name, last_name, phone_number, account_balance) VALUES
('admin@fitmanager.pl', '$2b$10$ekqZnOc3O2F672gf64L7LeXFZcvkdUw0JPSNPacegGAjzamREVIru', 'ADMIN', 'Olaf', 'Słowik', '123456789', 0.00),
('trener@fitmanager.pl', '$2b$10$5YKBYUW/24lWJU5lLeUjTuCE0mFNxHmArE9i6h4LgS028.BJ8doi2', 'TRAINER', 'Jan', 'Kowalski', '987654321', 0.00),
('trener2@fitmanager.pl', '$2b$10$5YKBYUW/24lWJU5lLeUjTuCE0mFNxHmArE9i6h4LgS028.BJ8doi2', 'TRAINER', 'Marek', 'Zieliński', '600111222', 0.00),
('trener3@fitmanager.pl', '$2b$10$5YKBYUW/24lWJU5lLeUjTuCE0mFNxHmArE9i6h4LgS028.BJ8doi2', 'TRAINER', 'Agnieszka', 'Lewandowska', '600333444', 0.00),
('trener4@fitmanager.pl', '$2b$10$5YKBYUW/24lWJU5lLeUjTuCE0mFNxHmArE9i6h4LgS028.BJ8doi2', 'TRAINER', 'Piotr', 'Nowicki', '600555666', 0.00),
('klient@fitmanager.pl', '$2b$10$2Vu16Vf7sDXwbKkcXf57T.7jpcVZpTXf8gS.9OC3EONcQx.IEGoKu', 'CLIENT', 'Anna', 'Nowak', '555444333', 200.00);

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
-- Zmieniono na INTERVAL '1 hour' oraz '2 hours'. 
-- Jeśli trening ma być za kilka dni, zmień na '1 day' / '2 days'.
INSERT INTO training_sessions (trainer_id, title, start_time, end_time, max_participants)
VALUES (
    2,
    'Trening Siłowy - Nogi',
    CURRENT_TIMESTAMP + INTERVAL '1 hour',
    CURRENT_TIMESTAMP + INTERVAL '2 hours',
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