-- V1: esquema inicial de Krypton E-commerce (8 tablas) — MySQL 8 / InnoDB

CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(160) NOT NULL UNIQUE,
    password   VARCHAR(120) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255)
);

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

CREATE TABLE cart (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE cart_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id    BIGINT  NOT NULL,
    product_id BIGINT  NOT NULL,
    quantity   INT     NOT NULL,
    CONSTRAINT fk_cart_item_cart    FOREIGN KEY (cart_id)    REFERENCES cart (id),
    CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE orders (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    order_date DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status     VARCHAR(20)   NOT NULL,
    total      DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE order_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INT           NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,          -- snapshot del precio al comprar
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE stock_movement (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT      NOT NULL,
    type       VARCHAR(20) NOT NULL,           -- ENTRADA | SALIDA
    quantity   INT         NOT NULL,
    reason     VARCHAR(120),
    reference  VARCHAR(120),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT,
    CONSTRAINT fk_stock_movement_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movement_user    FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX idx_products_category      ON products (category_id);
CREATE INDEX idx_cart_item_cart         ON cart_item (cart_id);
CREATE INDEX idx_order_items_order      ON order_items (order_id);
CREATE INDEX idx_stock_movement_product ON stock_movement (product_id);
