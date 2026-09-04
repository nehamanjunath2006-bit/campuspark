-- ============================================
-- CAMPUSPARK DATABASE
-- PostgreSQL
-- ============================================

-- Create the database separately if it does not exist:
-- CREATE DATABASE campuspark;

-- Connect to campuspark before running this file.

-- Preserve incompatible tables from older CampusPark versions.
DO $$
BEGIN
    IF to_regclass('public.users') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = 'public' AND table_name = 'users'
                       AND column_name = 'full_name') THEN
        ALTER TABLE users RENAME TO users_legacy;
    END IF;
    IF to_regclass('public.parking_slots') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = 'public' AND table_name = 'parking_slots'
                       AND column_name = 'allowed_type') THEN
        ALTER TABLE parking_slots RENAME TO parking_slots_legacy;
    END IF;
    IF to_regclass('public.parking_history') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = 'public' AND table_name = 'parking_history'
                       AND column_name = 'status') THEN
        ALTER TABLE parking_history RENAME TO parking_history_legacy;
    END IF;
END $$;

-- ============================================
-- USERS
-- ============================================

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    student_id VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT'
        CHECK (role IN ('STUDENT', 'ADMIN')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- VEHICLES
-- ============================================

CREATE TABLE IF NOT EXISTS vehicles (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL
        REFERENCES users(id)
        ON DELETE CASCADE,

    vehicle_number VARCHAR(30) NOT NULL UNIQUE,

    vehicle_type VARCHAR(20) NOT NULL
        CHECK (vehicle_type IN ('CAR', 'BIKE', 'SCOOTER', 'OTHER')),

    vehicle_model VARCHAR(100),
    vehicle_color VARCHAR(50),
    fuel_type VARCHAR(30),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- PARKING SLOTS
-- ============================================

CREATE TABLE IF NOT EXISTS parking_slots (
    id SERIAL PRIMARY KEY,

    slot_number VARCHAR(20) NOT NULL UNIQUE,

    allowed_type VARCHAR(20) NOT NULL DEFAULT 'ALL'
        CHECK (
            allowed_type IN
            ('ALL', 'CAR', 'BIKE', 'SCOOTER', 'OTHER')
        ),

    available BOOLEAN NOT NULL DEFAULT TRUE
);

-- ============================================
-- PARKING HISTORY
-- ============================================

CREATE TABLE IF NOT EXISTS parking_history (
    id SERIAL PRIMARY KEY,

    user_id INTEGER NOT NULL
        REFERENCES users(id)
        ON DELETE CASCADE,

    vehicle_id INTEGER NOT NULL
        REFERENCES vehicles(id)
        ON DELETE CASCADE,

    slot_id INTEGER NOT NULL
        REFERENCES parking_slots(id)
        ON DELETE RESTRICT,

    parked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    removed_at TIMESTAMP NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'COMPLETED'))
);

-- Only one active parking record can exist for a vehicle.
CREATE UNIQUE INDEX IF NOT EXISTS one_active_vehicle
ON parking_history(vehicle_id)
WHERE status = 'ACTIVE';

-- Only one active record can use a slot.
CREATE UNIQUE INDEX IF NOT EXISTS one_active_slot
ON parking_history(slot_id)
WHERE status = 'ACTIVE';

-- ============================================
-- SAMPLE PARKING SLOTS
-- ============================================

INSERT INTO parking_slots (slot_number, allowed_type) VALUES
('A1', 'CAR'),
('A2', 'CAR'),
('A3', 'CAR'),
('A4', 'CAR'),
('A5', 'CAR'),
('B1', 'BIKE'),
('B2', 'BIKE'),
('B3', 'BIKE'),
('B4', 'BIKE'),
('B5', 'BIKE'),
('C1', 'SCOOTER'),
('C2', 'SCOOTER'),
('C3', 'SCOOTER'),
('C4', 'SCOOTER'),
('D1', 'ALL'),
('D2', 'ALL'),
('D3', 'ALL'),
('D4', 'ALL')
ON CONFLICT (slot_number) DO NOTHING;

-- ============================================
-- ADMIN ACCOUNT
-- ============================================
-- Admin password is: admin123
-- SHA-256(admin123)
-- ============================================

INSERT INTO users
(full_name, student_id, email, phone, password_hash, role)
VALUES
(
    'CampusPark Admin',
    'ADMIN001',
    'admin@campuspark.com',
    '9999999999',
    '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
    'ADMIN'
)
ON CONFLICT (student_id) DO NOTHING;
