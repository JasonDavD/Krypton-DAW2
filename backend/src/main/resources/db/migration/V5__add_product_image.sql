-- V5: tabla product_image — soporte multi-imagen con carrusel por producto (MySQL 8)

CREATE TABLE product_image (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT       NOT NULL,
    path          VARCHAR(500) NOT NULL,
    display_order SMALLINT     NOT NULL DEFAULT 0,
    is_cover      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- Columna generada: = product_id solo cuando is_cover; NULL en caso contrario.
    -- MySQL permite múltiples NULL en un índice UNIQUE → garantiza máximo UNA
    -- portada por producto. Reemplaza el índice parcial de PostgreSQL
    -- (CREATE UNIQUE INDEX ... WHERE is_cover = TRUE), no soportado por MySQL.
    cover_key     BIGINT AS (IF(is_cover = 1, product_id, NULL)) STORED,
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_product_image_cover   UNIQUE (cover_key)
);

-- Índice general para listados por producto
CREATE INDEX idx_product_image_product ON product_image (product_id);
