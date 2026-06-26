-- =====================================================================
--  payment-service — schema krypton_payments (cobros)
-- =====================================================================
--  ADAPTACION microservicios: order_id es un BIGINT SUELTO (FK LOGICA),
--  SIN constraint a orders: esa tabla vive en otro servicio
--  (krypton_orders). La integridad referencial entre servicios se cuida
--  en codigo, no con FK de la DB.
-- =====================================================================

-- Pagos. Un registro por intento de cobro del checkout. status = APPROVED|DECLINED.
CREATE TABLE payments (
    id         BIGINT        AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT        NOT NULL,                        -- FK logica -> order-service
    amount     DECIMAL(12,2) NOT NULL,
    method     VARCHAR(20)   NOT NULL,                        -- CREDIT_CARD|DEBIT_CARD|YAPE...
    status     VARCHAR(20)   NOT NULL,                        -- APPROVED | DECLINED
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
