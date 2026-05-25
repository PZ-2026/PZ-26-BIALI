-- opcjonalnie czyszczenie
DROP TABLE IF EXISTS client_workouts, session_exercises, exercises, training_plans, progress_logs, reservations, training_sessions,
payments, memberships, membership_types, trainer_clients, trainer_rentals, users CASCADE;

-----------------------------------------------------------
-- 1. USERS
-----------------------------------------------------------
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ADMIN', 'TRAINER', 'CLIENT')),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    account_balance DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-----------------------------------------------------------
-- 2. TRAINER - CLIENT RELATION
-----------------------------------------------------------
CREATE TABLE trainer_clients (
    id SERIAL PRIMARY KEY,
    trainer_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (trainer_id, client_id)
);

-----------------------------------------------------------
-- 2.1 TRAINER RENTALS
-----------------------------------------------------------
CREATE TABLE trainer_rentals (
    id SERIAL PRIMARY KEY,
    trainer_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- only one active trainer rental per client
CREATE UNIQUE INDEX unique_active_trainer_rental_per_client
ON trainer_rentals(client_id)
WHERE status = 'ACTIVE';

-----------------------------------------------------------
-- 3. MEMBERSHIP TYPES (NOWA TABELA)
-----------------------------------------------------------
CREATE TABLE membership_types (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    duration_days INT NOT NULL,
    description TEXT
);

-----------------------------------------------------------
-- 4. MEMBERSHIPS
-----------------------------------------------------------
CREATE TABLE memberships (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    membership_type_id INT NOT NULL REFERENCES membership_types(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 🔥 tylko 1 aktywny karnet na usera
CREATE UNIQUE INDEX unique_active_membership_per_user
ON memberships(user_id)
WHERE status = 'ACTIVE';

-----------------------------------------------------------
-- 5. PAYMENTS
-----------------------------------------------------------
CREATE TABLE payments (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    membership_id INT REFERENCES memberships(id) ON DELETE SET NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED', 'PENDING'))
);

-----------------------------------------------------------
-- 6. TRAINING SESSIONS
-----------------------------------------------------------
CREATE TABLE training_sessions (
    id SERIAL PRIMARY KEY,
    trainer_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    max_participants INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-----------------------------------------------------------
-- 7. RESERVATIONS
-----------------------------------------------------------
CREATE TABLE reservations (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id INT NOT NULL REFERENCES training_sessions(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, session_id)
);

-----------------------------------------------------------
-- 8. PROGRESS LOGS
-----------------------------------------------------------
CREATE TABLE progress_logs (
    id SERIAL PRIMARY KEY,
    client_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    weight DECIMAL(5,2),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-----------------------------------------------------------
-- 9. TRAINING PLANS
-----------------------------------------------------------
CREATE TABLE training_plans (
    id SERIAL PRIMARY KEY,
    trainer_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-----------------------------------------------------------
-- 10. EXERCISES (DICTIONARY)
-----------------------------------------------------------
CREATE TABLE exercises (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    body_part VARCHAR(50)
);

CREATE TABLE session_exercises (
    id SERIAL PRIMARY KEY,
    session_id INT NOT NULL REFERENCES training_sessions(id) ON DELETE CASCADE,
    exercise_id INT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    sets INT NOT NULL,
    reps INT NOT NULL,
    weight DECIMAL(5,2)
);

CREATE TABLE client_workouts (
    id SERIAL PRIMARY KEY,
    client_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id INT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    session_id INT REFERENCES training_sessions(id) ON DELETE CASCADE,
    weight DECIMAL(5,2) NOT NULL,
    sets INT NOT NULL DEFAULT 1,
    reps INT NOT NULL,
    workout_date DATE DEFAULT CURRENT_DATE
);