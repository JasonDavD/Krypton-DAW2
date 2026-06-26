-- =====================================================================
--  catalog-service · Schema inicial (krypton_catalog)
--  Dueño de las tablas categories, products, product_image, stock_movement.
--  Esquema FINAL alineado con docs/krypton-database.sql.
--
--  ADAPTACIÓN microservicios: stock_movement.created_by es BIGINT SIN
--  foreign key a users — la tabla users vive en users-service (krypton_users),
--  no se hace FK cruzada entre servicios.
-- =====================================================================

-- Categorías del catálogo.
CREATE TABLE categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Productos. `sku` es la clave de negocio; `stock` es el valor cacheado
-- (el detalle de movimientos vive en stock_movement).
CREATE TABLE products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku         VARCHAR(60)   NOT NULL UNIQUE,
    name        VARCHAR(150)  NOT NULL,
    description VARCHAR(2000),
    price       DECIMAL(12,2) NOT NULL,
    stock       INT           NOT NULL DEFAULT 0,
    image_url   VARCHAR(500),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    category_id BIGINT        NOT NULL,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
CREATE INDEX idx_products_category ON products (category_id);

-- Kardex: historial de movimientos de stock (entradas/salidas).
-- created_by: id del usuario (admin) que registró el movimiento. Es una FK LÓGICA
-- al servicio de usuarios — SIN constraint física (otro servicio, otra BD).
CREATE TABLE stock_movement (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT      NOT NULL,
    type       VARCHAR(20) NOT NULL,                               -- ENTRADA | SALIDA
    quantity   INT         NOT NULL,
    reason     VARCHAR(120),                                       -- compra, venta, ajuste, devolución
    reference  VARCHAR(120),                                       -- ej: id del pedido
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT,                                             -- usuario (users-service) que lo registró
    CONSTRAINT fk_stock_movement_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX idx_stock_movement_product ON stock_movement (product_id);

-- Imágenes de producto (carrusel). `cover_key` es una columna generada que vale
-- product_id sólo cuando is_cover=1 (NULL si no): con UNIQUE garantiza máximo
-- UNA portada por producto (MySQL permite múltiples NULL en un índice UNIQUE).
CREATE TABLE product_image (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT       NOT NULL,
    path          VARCHAR(500) NOT NULL,
    display_order SMALLINT     NOT NULL DEFAULT 0,
    is_cover      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    cover_key     BIGINT AS (IF(is_cover = 1, product_id, NULL)) STORED,
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_product_image_cover   UNIQUE (cover_key)
);
CREATE INDEX idx_product_image_product ON product_image (product_id);

-- =====================================================================
--  DATOS SEMILLA
-- =====================================================================

-- Categorías (ids explícitos para referenciarlas en los productos).
INSERT INTO categories (id, name, description) VALUES
  (1, 'Laptops',     'Notebooks y ultrabooks'),
  (2, 'Audio',       'Audífonos, parlantes y micrófonos'),
  (3, 'Componentes', 'GPU, CPU, RAM y almacenamiento'),
  (4, 'Periféricos', 'Teclados, mouse y accesorios'),
  (5, 'Monitores',   'Monitores y pantallas');

-- Catálogo demo.
INSERT INTO products (sku, name, description, price, stock, image_url, active, category_id) VALUES
  ('KR-LAP-001', 'Laptop Krypton Vortex 15',         'Intel Core i7, 16GB RAM, RTX 4060, 15.6" 144Hz', 4299.00, 12, NULL, TRUE, 1),
  ('KR-LAP-002', 'Laptop Krypton Air 14',            'Intel Core i5, 8GB RAM, SSD 512GB, 14" liviana',  2799.00, 20, NULL, TRUE, 1),
  ('KR-AUD-001', 'Audífonos Krypton Pulse X',        'Inalámbricos, cancelación de ruido, 30h batería',  349.90, 35, NULL, TRUE, 2),
  ('KR-AUD-002', 'Parlante Krypton Boom',            'Bluetooth, resistente al agua IPX7, 20W',          199.90, 28, NULL, TRUE, 2),
  ('KR-CMP-001', 'Tarjeta de video Krypton RTX 4070','GDDR6X 12GB, ray tracing, DLSS 3',                2599.00,  8, NULL, TRUE, 3),
  ('KR-CMP-002', 'Memoria RAM Krypton Fury 32GB',    'DDR5 6000MHz, kit 2x16GB, RGB',                     559.00, 40, NULL, TRUE, 3),
  ('KR-CMP-003', 'SSD Krypton NVMe 1TB',             'Gen4, 7000MB/s de lectura',                        389.00, 50, NULL, TRUE, 3),
  ('KR-PER-001', 'Teclado Mecánico Krypton TKL',     'Switches rojos, hot-swap, retroiluminado',         259.00, 30, NULL, TRUE, 4),
  ('KR-PER-002', 'Mouse Krypton Glide Pro',          'Inalámbrico, 26000 DPI, 60g',                      179.00, 45, NULL, TRUE, 4),
  ('KR-MON-001', 'Monitor Krypton View 27',          'QHD 165Hz, IPS, 1ms',                             1099.00, 15, NULL, TRUE, 5);
