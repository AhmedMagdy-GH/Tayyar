CREATE TABLE promotions (
 id UUID PRIMARY KEY,
 restaurant_id UUID NOT NULL REFERENCES restaurants(id),
 code VARCHAR(32) NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{2,31}$'),
 name VARCHAR(120) NOT NULL CHECK (length(btrim(name))>0),
 description VARCHAR(1000),
 discount_type VARCHAR(24) NOT NULL CHECK (discount_type IN ('PERCENTAGE','FIXED_AMOUNT')),
 percentage_value NUMERIC(5,2),
 fixed_amount NUMERIC(12,2),
 minimum_merchandise_subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
 maximum_discount NUMERIC(12,2),
 starts_at TIMESTAMPTZ,
 ends_at TIMESTAMPTZ,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 total_usage_limit BIGINT,
 per_customer_usage_limit BIGINT,
 version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE (restaurant_id,code),
 UNIQUE (id,restaurant_id),
 CHECK ((discount_type='PERCENTAGE' AND percentage_value>0 AND percentage_value<=100
         AND fixed_amount IS NULL)
     OR (discount_type='FIXED_AMOUNT' AND fixed_amount>0 AND percentage_value IS NULL
         AND maximum_discount IS NULL)),
 CHECK (minimum_merchandise_subtotal>=0 AND minimum_merchandise_subtotal<10000000000),
 CHECK (maximum_discount IS NULL OR (maximum_discount>0 AND maximum_discount<10000000000)),
 CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at<ends_at),
 CHECK (total_usage_limit IS NULL OR total_usage_limit>0),
 CHECK (per_customer_usage_limit IS NULL OR per_customer_usage_limit>0)
);
CREATE INDEX promotions_restaurant_active_idx
 ON promotions(restaurant_id,active,updated_at DESC,id);

CREATE TABLE order_promotion_snapshots (
 order_id UUID PRIMARY KEY REFERENCES orders(id),
 promotion_id UUID NOT NULL,
 restaurant_id UUID NOT NULL,
 code VARCHAR(32) NOT NULL,
 name VARCHAR(120) NOT NULL,
 discount_type VARCHAR(24) NOT NULL CHECK (discount_type IN ('PERCENTAGE','FIXED_AMOUNT')),
 discount_total NUMERIC(12,2) NOT NULL CHECK (discount_total>=0 AND discount_total<10000000000),
 created_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(promotion_id,restaurant_id) REFERENCES promotions(id,restaurant_id),
 FOREIGN KEY(order_id,restaurant_id) REFERENCES orders(id,restaurant_id)
);

CREATE TABLE promotion_redemptions (
 id UUID PRIMARY KEY,
 promotion_id UUID NOT NULL REFERENCES promotions(id),
 customer_id UUID NOT NULL REFERENCES users(id),
 order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
 redeemed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX promotion_redemptions_promotion_idx ON promotion_redemptions(promotion_id,redeemed_at,id);
CREATE INDEX promotion_redemptions_customer_idx
 ON promotion_redemptions(promotion_id,customer_id,redeemed_at,id);

ALTER TABLE checkout_receipts ADD COLUMN promotion_code VARCHAR(32);
ALTER TABLE orders ADD CONSTRAINT orders_discount_merchandise_only
 CHECK (discount_total<=merchandise_subtotal);

CREATE FUNCTION protect_promotion_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='promotions' THEN
   IF EXISTS(SELECT 1 FROM promotion_redemptions WHERE promotion_id=OLD.id) THEN
     RAISE EXCEPTION 'A redeemed promotion cannot be deleted' USING ERRCODE='23514';
   END IF;
   RETURN OLD;
 END IF;
 RAISE EXCEPTION 'Promotion financial history is immutable' USING ERRCODE='23514';
END $$;
CREATE TRIGGER redeemed_promotions_no_delete BEFORE DELETE ON promotions
 FOR EACH ROW EXECUTE FUNCTION protect_promotion_history();
CREATE TRIGGER order_promotion_snapshots_immutable BEFORE UPDATE OR DELETE ON order_promotion_snapshots
 FOR EACH ROW EXECUTE FUNCTION protect_promotion_history();
CREATE TRIGGER promotion_redemptions_immutable BEFORE UPDATE OR DELETE ON promotion_redemptions
 FOR EACH ROW EXECUTE FUNCTION protect_promotion_history();

CREATE FUNCTION validate_promotion_redemption() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (
   SELECT 1 FROM order_promotion_snapshots s JOIN orders o ON o.id=s.order_id
   WHERE s.order_id=NEW.order_id AND s.promotion_id=NEW.promotion_id
     AND o.customer_id=NEW.customer_id AND o.restaurant_id=s.restaurant_id
     AND o.discount_total=s.discount_total AND o.discount_total>0) THEN
   RAISE EXCEPTION 'Invalid promotion redemption context' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER promotion_redemption_context
 AFTER INSERT ON promotion_redemptions DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW EXECUTE FUNCTION validate_promotion_redemption();

CREATE OR REPLACE FUNCTION validate_checkout_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (
 SELECT 1 FROM carts c JOIN orders o ON o.id=NEW.order_id
 JOIN payments p ON p.id=NEW.payment_id AND p.order_id=o.id
 WHERE c.id=NEW.cart_id AND c.customer_id=NEW.customer_id
 AND o.customer_id=c.customer_id AND o.restaurant_id=c.restaurant_id AND o.branch_id=c.branch_id
 AND c.status='CHECKED_OUT' AND c.version=NEW.cart_version+1
 AND o.status='PLACED' AND p.method='CASH' AND p.status='PENDING'
 AND ((NEW.promotion_code IS NULL AND o.discount_total=0
       AND NOT EXISTS(SELECT 1 FROM order_promotion_snapshots s WHERE s.order_id=o.id))
   OR (NEW.promotion_code IS NOT NULL AND EXISTS(
       SELECT 1 FROM order_promotion_snapshots s WHERE s.order_id=o.id
       AND s.code=NEW.promotion_code AND s.discount_total=o.discount_total)))
 ) THEN
 RAISE EXCEPTION 'Invalid checkout receipt context' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
