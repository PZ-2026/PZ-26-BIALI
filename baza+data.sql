odp-- 1. Tabela Użytkowników (wspólna dla ról: ADMIN, TRAINER, CLIENT)
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ADMIN', 'TRAINER', 'CLIENT')),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Tabela relacji: Trener - Podopieczny (Klient)
CREATE TABLE trainer_clients (
    id SERIAL PRIMARY KEY,
    trainer_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (trainer_id, client_id)
);

-- 3. Tabela Karnetów
CREATE TABLE memberships (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    membership_type VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Tabela Płatności
CREATE TABLE payments (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    membership_id INT REFERENCES memberships(id) ON DELETE SET NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED', 'PENDING'))
);

-- 5. Tabela Harmonogramu / Zajęć Treningowych
CREATE TABLE training_sessions (
    id SERIAL PRIMARY KEY,
    trainer_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    max_participants INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Tabela Rezerwacji Klientów na zajęcia
CREATE TABLE reservations (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id INT NOT NULL REFERENCES training_sessions(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, session_id)
);

-- 7. Tabela Postępów Klienta 
CREATE TABLE progress_logs (
    id SERIAL PRIMARY KEY,
    client_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trainer_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    weight DECIMAL(5, 2),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. Tabela Planów Treningowych 
CREATE TABLE training_plans (
    id SERIAL PRIMARY KEY,
    trainer_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);



-- Dodawanie przykładowych użytkowników 
INSERT INTO users (email, password_hash, role, first_name, last_name, phone_number) VALUES
('admin@fitmanager.pl', 'hashed_pwd_admin', 'ADMIN', 'Olaf', 'Slowik', '123456789'),
('trener@fitmanager.pl', 'hashed_pwd_trainer', 'TRAINER', 'Jan', 'Kowalski', '987654321'),
('klient@fitmanager.pl', 'hashed_pwd_client', 'CLIENT', 'Anna', 'Nowak', '555444333');

-- Przypisanie klienta (ID 3) do trenera (ID 2)
INSERT INTO trainer_clients (trainer_id, client_id) VALUES
(2, 3);

-- Dodanie aktywnego karnetu dla klienta
INSERT INTO memberships (user_id, membership_type, start_date, end_date, status) VALUES
(3, 'Karnet OPEN 30 Dni', CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days', 'ACTIVE');

-- Dodanie historii płatności za karnet
INSERT INTO payments (user_id, membership_id, amount, status) VALUES
(3, 1, 149.99, 'SUCCESS');

-- Stworzenie przykładowych zajęć w harmonogramie trenera
INSERT INTO training_sessions (trainer_id, title, start_time, end_time, max_participants) VALUES
(2, 'Trening Siłowy - Nogi', CURRENT_TIMESTAMP + INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 day 1 hour', 1);

-- Rezerwacja klienta na powyższe zajęcia
INSERT INTO reservations (user_id, session_id, status) VALUES
(3, 1, 'CONFIRMED');

-- Wpis postępów treningowych przez trenera 
INSERT INTO progress_logs (client_id, trainer_id, log_date, weight, notes) VALUES
(3, 2, CURRENT_DATE, 62.5, 'Poprawa mobilności w stawach skokowych, technika przysiadu znacznie lepsza.');

-- Stworzenie planu treningowego dla klienta
INSERT INTO training_plans (trainer_id, client_id, title, description) VALUES
(2, 3, 'Plan FBW dla początkujących', '1. Przysiady 3x10\n2. Martwy ciąg 3x8\n3. Wyciskanie na ławce płaskiej 3x10');