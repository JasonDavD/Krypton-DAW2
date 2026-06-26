-- =====================================================================
--  promo-service — schema krypton_promos (codigos de descuento / cupones)
-- =====================================================================
--  Servicio autocontenido: no referencia tablas de otros servicios.
--  type = PORCENTAJE | MONTO. value se interpreta segun type:
--    PORCENTAJE -> % a descontar; MONTO -> importe fijo a descontar.
-- =====================================================================

CREATE TABLE promos (
    id         BIGINT        AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(40)   NOT NULL UNIQUE,                 -- clave natural del cupon
    type       VARCHAR(20)   NOT NULL,                        -- PORCENTAJE | MONTO
    value      DECIMAL(12,2) NOT NULL,                        -- % o importe segun type
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
