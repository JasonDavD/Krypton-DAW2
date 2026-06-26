-- =====================================================================
--  review-service — schema krypton_reviews (resenas de productos)
-- =====================================================================
--  ADAPTACION microservicios: product_id (BIGINT) y user_email (VARCHAR)
--  son FK LOGICAS, SIN constraint a catalog/users: esas tablas viven en
--  otros servicios (krypton_catalog / krypton_users). La integridad
--  referencial entre servicios se cuida en codigo, no con FK de la DB.
-- =====================================================================

-- Una resena por (usuario, producto): UNIQUE evita reseniar dos veces el mismo producto.
CREATE TABLE reviews (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,                              -- FK logica -> catalog-service
    user_email VARCHAR(160) NOT NULL,                              -- identidad logica -> users-service (sub del JWT)
    rating     INT          NOT NULL,                              -- estrellas 1..5
    comment    VARCHAR(1000),
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_review_user_product UNIQUE (user_email, product_id)
);

CREATE INDEX idx_reviews_product ON reviews (product_id);
