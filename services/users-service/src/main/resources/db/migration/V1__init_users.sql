-- =====================================================================
--  users-service · Schema inicial (krypton_users)
--  Dueño de la tabla `users`. Esquema FINAL alineado con docs/krypton-database.sql.
-- =====================================================================

-- Usuarios (clientes y administradores). El password va HASHEADO (BCrypt).
CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(160) NOT NULL UNIQUE,
    password   VARCHAR(120) NOT NULL,
    role       VARCHAR(20)  NOT NULL,                              -- CLIENTE | ADMIN
    active     BOOLEAN      NOT NULL DEFAULT TRUE,                 -- baja lógica
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

-- =====================================================================
--  DATOS SEMILLA
-- =====================================================================

-- Administrador inicial. Password de desarrollo: "Admin123!" (hash BCrypt).
-- CAMBIAR en producción.
INSERT INTO users (name, email, password, role, active, created_at) VALUES
  ('Admin Krypton', 'admin@krypton.pe',
   '$2a$10$N0.6BPMeDJxcK3BQW/cDnOXSjq6hj9rHwkZd7rEliqr0g.dTnPdBy',
   'ADMIN', 1, CURRENT_TIMESTAMP(6));
