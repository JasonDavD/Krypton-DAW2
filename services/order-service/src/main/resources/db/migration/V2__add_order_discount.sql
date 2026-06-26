-- Cupones (F11): el descuento aplicado por un cupón en el checkout.
-- total = subtotal - discount + shipping_cost. 0.00 si no se usó cupón.
ALTER TABLE orders ADD COLUMN discount DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER subtotal;
