-- Datos de facturación del pedido: comprobante (boleta/factura) + desglose de montos.
-- El precio del catálogo YA incluye IGV (estándar B2C Perú), por eso el IGV se
-- desglosa HACIA ADENTRO del total. total = subtotal_productos + envío.
-- Boleta y factura pagan lo mismo; sólo cambian los datos del receptor y el desglose.
-- customer_doc: DNI (8 díg) para boleta, RUC (11 díg) para factura.
-- DEFAULT en columnas NOT NULL para no romper pedidos preexistentes ni la validación de Hibernate.
ALTER TABLE orders
    ADD COLUMN document_type VARCHAR(10)   NOT NULL DEFAULT 'BOLETA' AFTER status,
    ADD COLUMN customer_name VARCHAR(150)  NOT NULL DEFAULT ''       AFTER document_type,
    ADD COLUMN customer_doc  VARCHAR(11)   NOT NULL DEFAULT ''       AFTER customer_name,
    ADD COLUMN subtotal      DECIMAL(12,2) NOT NULL DEFAULT 0.00     AFTER customer_doc,
    ADD COLUMN shipping_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00     AFTER subtotal,
    ADD COLUMN igv           DECIMAL(12,2) NOT NULL DEFAULT 0.00     AFTER shipping_cost;
