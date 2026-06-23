ALTER TABLE cart_item
    ADD CONSTRAINT uq_cart_item_cart_product UNIQUE (cart_id, product_id);
