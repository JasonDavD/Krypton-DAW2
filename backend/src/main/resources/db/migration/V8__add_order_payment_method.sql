-- Método de pago (simulado) elegido al pagar el pedido.
-- NULLABLE: un pedido PENDIENTE todavía no se pagó, y los pedidos preexistentes
-- no tienen método registrado (antes el método se recibía y se descartaba).
-- Se persiste como STRING (coincide con @Enumerated(EnumType.STRING)): CREDIT_CARD, DEBIT_CARD, YAPE.
ALTER TABLE orders
    ADD COLUMN payment_method VARCHAR(20) NULL AFTER total;
