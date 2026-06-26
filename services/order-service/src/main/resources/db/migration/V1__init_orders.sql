-- =====================================================================
--  order-service — schema krypton_orders (carrito + pedidos)
-- =====================================================================
--  ADAPTACION microservicios: user_id y product_id son BIGINT SUELTOS
--  (FK LOGICAS), SIN constraint a users/products: esas tablas viven en
--  otros servicios (krypton_users / krypton_catalog). La integridad
--  referencial entre servicios se cuida en codigo, no con FK de la DB.
-- =====================================================================

-- Carrito: uno por usuario (user_email UNIQUE). Identidad = email del JWT (sub).
CREATE TABLE cart (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_email VARCHAR(160) NOT NULL UNIQUE,                    -- identidad logica -> users-service (sub del JWT)
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

-- Lineas del carrito. NO guarda precio (se muestra el precio VIVO del producto, via Feign).
-- UNIQUE (cart_id, product_id): un producto no se repite como linea; se suma cantidad.
CREATE TABLE cart_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,                                 -- FK logica -> catalog-service
    quantity   INT    NOT NULL,
    CONSTRAINT fk_cart_item_cart        FOREIGN KEY (cart_id) REFERENCES cart (id),
    CONSTRAINT uq_cart_item_cart_product UNIQUE (cart_id, product_id)
);
CREATE INDEX idx_cart_item_cart ON cart_item (cart_id);

-- Pedidos. total = subtotal + shipping_cost. payment_method NULL hasta que se paga.
-- El precio del catalogo YA incluye IGV (B2C Peru); el IGV se desglosa hacia adentro.
CREATE TABLE orders (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_email     VARCHAR(160)  NOT NULL,                      -- identidad logica -> users-service (sub del JWT)
    order_date     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status         VARCHAR(20)   NOT NULL,                      -- PENDIENTE|CONFIRMADA|ENVIADO|ENTREGADO|CANCELADA
    document_type  VARCHAR(10)   NOT NULL DEFAULT 'BOLETA',     -- BOLETA | FACTURA
    customer_name  VARCHAR(150)  NOT NULL DEFAULT '',
    customer_doc   VARCHAR(11)   NOT NULL DEFAULT '',           -- DNI (8) boleta / RUC (11) factura
    subtotal       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    shipping_cost  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    igv            DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total          DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(20)   NULL                           -- CREDIT_CARD|DEBIT_CARD|YAPE
);

-- Lineas del pedido. unit_price y product_name = SNAPSHOT: se CONGELAN al comprar
-- (asi ver una orden NO requiere llamar a catalog; es historica).
CREATE TABLE order_items (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,                        -- FK logica -> catalog-service
    product_name VARCHAR(150)  NOT NULL,                        -- snapshot del nombre al comprar
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(12,2) NOT NULL,                        -- snapshot del precio al comprar
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
CREATE INDEX idx_order_items_order ON order_items (order_id);
