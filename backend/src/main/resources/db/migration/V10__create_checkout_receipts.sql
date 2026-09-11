-- Only successful checkouts are recorded, in the same transaction as all purchase writes.
CREATE TABLE checkout_receipts (
 customer_id UUID NOT NULL REFERENCES users(id),
 idempotency_key VARCHAR(128) NOT NULL CHECK (idempotency_key ~ '^[A-Za-z0-9_-]{16,128}$'),
 cart_id UUID NOT NULL UNIQUE REFERENCES carts(id),
 cart_version BIGINT NOT NULL CHECK (cart_version>=0),
 saved_address_id UUID NOT NULL,
 payment_method VARCHAR(16) NOT NULL CHECK(payment_method='CASH'),
 order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
 payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),
 created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(customer_id,idempotency_key)
);
CREATE TRIGGER checkout_receipts_immutable BEFORE UPDATE OR DELETE ON checkout_receipts
 FOR EACH ROW EXECUTE FUNCTION protect_order_record();

CREATE FUNCTION validate_checkout_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (
 SELECT 1 FROM carts c JOIN orders o ON o.id=NEW.order_id
 JOIN payments p ON p.id=NEW.payment_id AND p.order_id=o.id
 WHERE c.id=NEW.cart_id AND c.customer_id=NEW.customer_id
 AND o.customer_id=c.customer_id AND o.restaurant_id=c.restaurant_id AND o.branch_id=c.branch_id
 AND c.status='CHECKED_OUT' AND c.version=NEW.cart_version+1
 AND o.status='PLACED' AND p.method='CASH' AND p.status='PENDING') THEN
 RAISE EXCEPTION 'Invalid checkout receipt context' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER checkout_receipt_context BEFORE INSERT ON checkout_receipts
 FOR EACH ROW EXECUTE FUNCTION validate_checkout_receipt();

CREATE FUNCTION protect_consumed_cart() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='carts' THEN
   IF OLD.status='CHECKED_OUT' THEN
     RAISE EXCEPTION 'Checked out cart is immutable' USING ERRCODE='23514';
   END IF;
 ELSE
   IF EXISTS(SELECT 1 FROM carts WHERE id=COALESCE(NEW.cart_id,OLD.cart_id) AND status='CHECKED_OUT') THEN
     RAISE EXCEPTION 'Checked out cart items are immutable' USING ERRCODE='23514';
   END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER consumed_cart_guard BEFORE UPDATE OR DELETE ON carts
 FOR EACH ROW EXECUTE FUNCTION protect_consumed_cart();
CREATE TRIGGER consumed_cart_items_guard BEFORE INSERT OR UPDATE OR DELETE ON cart_items
 FOR EACH ROW EXECUTE FUNCTION protect_consumed_cart();
