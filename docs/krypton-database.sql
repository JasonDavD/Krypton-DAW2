-- =====================================================================
--  Krypton E-commerce — Script de base de datos (MySQL 8)
-- =====================================================================
--  Esquema completo + datos semilla, consolidado a partir de las
--  migraciones Flyway V1..V8 (la fuente de verdad del backend).
--
--  Uso (MySQL Workbench, consola, o `mysql < krypton-database.sql`):
--    Crea la base `krypton`, las 9 tablas, y siembra el admin + catálogo demo.
--    El script es RE-EJECUTABLE: dropea las tablas si existen y las recrea.
--
--  Nota: en el proyecto la base la crea Flyway automáticamente al arrancar
--  el backend. Este archivo es el equivalente "todo en uno" para entregar
--  o para levantar la BD a mano sin correr la app.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS krypton
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE krypton;

-- --- Limpieza (orden inverso por las FK) ------------------------------
DROP TABLE IF EXISTS product_image;
DROP TABLE IF EXISTS stock_movement;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS cart_item;
DROP TABLE IF EXISTS cart;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS users;

-- =====================================================================
--  TABLAS
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

-- Carrito: uno por usuario (user_id UNIQUE), persistido en BD.
CREATE TABLE cart (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Líneas del carrito. NO guarda precio (muestra el precio VIVO del producto).
-- UNIQUE (cart_id, product_id): un producto no se repite como línea; se suma cantidad.
CREATE TABLE cart_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    CONSTRAINT fk_cart_item_cart    FOREIGN KEY (cart_id)    REFERENCES cart (id),
    CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_cart_item_cart_product UNIQUE (cart_id, product_id)
);
CREATE INDEX idx_cart_item_cart ON cart_item (cart_id);

-- Pedidos. Incluye datos de comprobante (boleta/factura) y desglose de montos.
-- El precio del catálogo YA incluye IGV (B2C Perú); el IGV se desglosa hacia adentro.
-- total = subtotal + shipping_cost.  payment_method es NULL hasta que se paga.
CREATE TABLE orders (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    order_date     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status         VARCHAR(20)   NOT NULL,                          -- PENDIENTE|CONFIRMADA|ENVIADO|ENTREGADO|CANCELADA
    document_type  VARCHAR(10)   NOT NULL DEFAULT 'BOLETA',         -- BOLETA | FACTURA
    customer_name  VARCHAR(150)  NOT NULL DEFAULT '',
    customer_doc   VARCHAR(11)   NOT NULL DEFAULT '',               -- DNI (8) boleta / RUC (11) factura
    subtotal       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    shipping_cost  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    igv            DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total          DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(20)   NULL,                              -- CREDIT_CARD|DEBIT_CARD|YAPE
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Líneas del pedido. unit_price = SNAPSHOT: el precio se CONGELA al comprar.
CREATE TABLE order_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INT           NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX idx_order_items_order ON order_items (order_id);

-- Kardex: historial de movimientos de stock (entradas/salidas).
CREATE TABLE stock_movement (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT      NOT NULL,
    type       VARCHAR(20) NOT NULL,                               -- ENTRADA | SALIDA
    quantity   INT         NOT NULL,
    reason     VARCHAR(120),                                       -- compra, venta, ajuste, devolución
    reference  VARCHAR(120),                                       -- ej: id del pedido
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT,                                             -- usuario que lo registró
    CONSTRAINT fk_stock_movement_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movement_user    FOREIGN KEY (created_by) REFERENCES users (id)
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

-- Administrador inicial. Password de desarrollo: "Admin123!" (hash BCrypt).
-- CAMBIAR en producción.
INSERT INTO users (name, email, password, role, active, created_at) VALUES
  ('Admin Krypton', 'admin@krypton.pe',
   '$2a$10$N0.6BPMeDJxcK3BQW/cDnOXSjq6hj9rHwkZd7rEliqr0g.dTnPdBy',
   'ADMIN', 1, CURRENT_TIMESTAMP(6));

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

-- =====================================================================
--  Fin. Login admin: admin@krypton.pe / Admin123!
-- =====================================================================
