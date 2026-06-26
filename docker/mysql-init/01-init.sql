-- Init de schemas para el stack de microservicios Krypton.
-- El contenedor MySQL ya crea la base `krypton` (via MYSQL_DATABASE). Aca creamos
-- una base por servicio. Las TABLAS y el SEED las crea Flyway de cada servicio al
-- arrancar; este script solo deja las bases vacias + permisos para el user `krypton`.

CREATE DATABASE IF NOT EXISTS krypton_users    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS krypton_catalog  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS krypton_orders   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS krypton_payments CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS krypton_reviews  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS krypton_promos   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON krypton_users.*    TO 'krypton'@'%';
GRANT ALL PRIVILEGES ON krypton_catalog.*  TO 'krypton'@'%';
GRANT ALL PRIVILEGES ON krypton_orders.*   TO 'krypton'@'%';
GRANT ALL PRIVILEGES ON krypton_payments.* TO 'krypton'@'%';
GRANT ALL PRIVILEGES ON krypton_reviews.*  TO 'krypton'@'%';
GRANT ALL PRIVILEGES ON krypton_promos.*   TO 'krypton'@'%';

FLUSH PRIVILEGES;
