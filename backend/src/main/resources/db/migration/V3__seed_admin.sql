-- V3: usuario administrador inicial (seed).
-- Password de desarrollo: "Admin123!" (hash BCrypt strength 10).
-- CAMBIAR en producción (crear un admin nuevo y dar de baja a este).
INSERT INTO users (name, email, password, role, active, created_at)
VALUES ('Admin Krypton',
        'admin@krypton.pe',
        '$2a$10$N0.6BPMeDJxcK3BQW/cDnOXSjq6hj9rHwkZd7rEliqr0g.dTnPdBy',
        'ADMIN',
        1,
        CURRENT_TIMESTAMP(6));
