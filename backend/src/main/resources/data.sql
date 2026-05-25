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
('admin@fitmanager.pl', 'hashed_pwd_admin', 'ADMIN', 'Olaf', 'Słowik', '123456789', 0.00),
('trener@fitmanager.pl', 'hashed_pwd_trainer', 'TRAINER', 'Jan', 'Kowalski', '987654321', 0.00),
('klient@fitmanager.pl', 'hashed_pwd_client', 'CLIENT', 'Anna', 'Nowak', '555444333', 200.00),
('trener2@fitmanager.pl', 'hashed_pwd_trainer2', 'TRAINER', 'Marek', 'Zieliński', '600111222', 0.00),
('trener3@fitmanager.pl', 'hashed_pwd_trainer3', 'TRAINER', 'Agnieszka', 'Lewandowska', '600333444', 0.00),
('trener4@fitmanager.pl', 'hashed_pwd_trainer4', 'TRAINER', 'Piotr', 'Nowicki', '600555666', 0.00);

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
INSERT INTO progress_logs (client_id, log_date, weight, notes)
VALUES (3, CURRENT_DATE, 62.5, 'Poprawa mobilności w stawach skokowych.');

-----------------------------------------------------------
-- 9. TRAINING PLAN
-----------------------------------------------------------
INSERT INTO training_plans (trainer_id, client_id, title, description)
VALUES (2, 3, 'Plan FBW', '1. Przysiady 3x10, 2. Martwy ciąg 3x8');

-----------------------------------------------------------
-- 10. EXERCISES (SŁOWNIK ĆWICZEŃ)
-----------------------------------------------------------
INSERT INTO exercises (name, body_part) VALUES
('Przysiad ze sztangą', 'Nogi'),
('Wykroki z hantlami', 'Nogi'),
('Wyciskanie nogami na suwnicy', 'Nogi'),
('Martwy ciąg', 'Plecy'),
('Wiosłowanie sztangą w opadzie', 'Plecy'),
('Podciąganie na drążku', 'Plecy'),
('Wyciskanie sztangi na ławce płaskiej', 'Klatka piersiowa'),
('Wyciskanie hantli na ławce skośnej', 'Klatka piersiowa'),
('Rozpiętki z hantlami', 'Klatka piersiowa'),
('Wyciskanie żołnierskie', 'Barki'),
('Wznosy ramion bokiem z hantlami', 'Barki'),
('Uginanie ramion ze sztangą', 'Biceps'),
('Uginanie ramion z hantlami', 'Biceps'),
('Wyciskanie francuskie sztangi', 'Triceps'),
('Prostowanie ramion na wyciągu', 'Triceps'),
('Deska (Plank)', 'Brzuch'),
('Spięcia brzucha (Crunches)', 'Brzuch');

-----------------------------------------------------------
-- 11. SESSION EXERCISES (PRZYKŁADOWE ĆWICZENIA W SESJI)
-----------------------------------------------------------
INSERT INTO session_exercises (session_id, exercise_id, sets, reps, weight) VALUES
(1, 1, 4, 10, 80.00),  -- Przysiad ze sztangą
(1, 2, 3, 12, 15.00),  -- Wykroki z hantlami
(1, 16, 3, 60, 0.00);  -- Deska (Plank)