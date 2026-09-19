ALTER TABLE order_items
    ALTER COLUMN product_price_id DROP NOT NULL,
    ALTER COLUMN unit_price DROP NOT NULL,
    ALTER COLUMN total_price DROP NOT NULL;

ALTER TABLE orders
    ALTER COLUMN discount_amount DROP NOT NULL,
    ALTER COLUMN total_amount DROP NOT NULL;
