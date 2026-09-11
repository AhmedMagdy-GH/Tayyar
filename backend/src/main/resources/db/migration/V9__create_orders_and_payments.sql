CREATE TABLE orders (
 id UUID PRIMARY KEY,
 customer_id UUID NOT NULL REFERENCES users(id),
 restaurant_id UUID NOT NULL REFERENCES restaurants(id),
 branch_id UUID NOT NULL,
 status VARCHAR(24) NOT NULL CHECK (status IN (
   'PENDING_PAYMENT','PLACED','ACCEPTED','PREPARING','READY_FOR_PICKUP',
   'OUT_FOR_DELIVERY','DELIVERED','PAYMENT_FAILED','REJECTED','CANCELLED')),
 currency VARCHAR(3) NOT NULL CHECK (currency='EGP'),
 merchandise_subtotal NUMERIC(12,2) NOT NULL CHECK (merchandise_subtotal>=0 AND merchandise_subtotal<10000000000),
 delivery_fee NUMERIC(12,2) NOT NULL CHECK (delivery_fee>=0 AND delivery_fee<10000000000),
 discount_total NUMERIC(12,2) NOT NULL CHECK (discount_total>=0 AND discount_total<10000000000),
 final_total NUMERIC(12,2) NOT NULL CHECK (final_total>=0 AND final_total<10000000000),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(branch_id,restaurant_id) REFERENCES branches(id,restaurant_id),
 CHECK (discount_total<=merchandise_subtotal+delivery_fee),
 CHECK (final_total=merchandise_subtotal+delivery_fee-discount_total),
 UNIQUE(id,restaurant_id),
 UNIQUE(id,currency,final_total)
);
CREATE INDEX orders_customer_history_idx ON orders(customer_id,created_at DESC,id DESC);
CREATE INDEX orders_branch_queue_idx ON orders(branch_id,status,created_at,id);
CREATE INDEX orders_restaurant_queue_idx ON orders(restaurant_id,status,created_at,id);

CREATE TABLE order_items (
 id UUID PRIMARY KEY,
 order_id UUID NOT NULL,
 restaurant_id UUID NOT NULL,
 menu_item_id UUID NOT NULL,
 purchased_name VARCHAR(120) NOT NULL CHECK(length(btrim(purchased_name))>0),
 unit_price NUMERIC(12,2) NOT NULL CHECK(unit_price>=0 AND unit_price<10000000000),
 quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 99),
 line_subtotal NUMERIC(12,2) NOT NULL CHECK(line_subtotal>=0 AND line_subtotal<10000000000),
 created_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(order_id,restaurant_id) REFERENCES orders(id,restaurant_id),
 FOREIGN KEY(menu_item_id,restaurant_id) REFERENCES menu_items(id,restaurant_id),
 CHECK(line_subtotal=unit_price*quantity)
);
CREATE INDEX order_items_order_idx ON order_items(order_id,created_at,id);

CREATE TABLE order_address_snapshots (
 order_id UUID PRIMARY KEY REFERENCES orders(id),
 label VARCHAR(80),
 street VARCHAR(200) NOT NULL CHECK(length(btrim(street))>0),
 building VARCHAR(80) NOT NULL CHECK(length(btrim(building))>0),
 floor VARCHAR(40),
 apartment VARCHAR(40),
 landmark VARCHAR(200),
 instructions VARCHAR(1000),
 city VARCHAR(100) NOT NULL CHECK(length(btrim(city))>0),
 region VARCHAR(100),
 postal_code VARCHAR(20),
 country_code VARCHAR(2) NOT NULL CHECK(country_code ~ '^[A-Z]{2}$'),
 latitude NUMERIC(9,6) CHECK(latitude BETWEEN -90 AND 90),
 longitude NUMERIC(9,6) CHECK(longitude BETWEEN -180 AND 180),
 delivery_zone_id UUID,
 delivery_zone_name VARCHAR(100),
 managed_city_name VARCHAR(100),
 created_at TIMESTAMPTZ NOT NULL,
 CHECK((latitude IS NULL)=(longitude IS NULL))
);

CREATE TABLE order_status_history (
 id UUID PRIMARY KEY,
 order_id UUID NOT NULL REFERENCES orders(id),
 previous_status VARCHAR(24),
 new_status VARCHAR(24) NOT NULL CHECK (new_status IN (
   'PENDING_PAYMENT','PLACED','ACCEPTED','PREPARING','READY_FOR_PICKUP',
   'OUT_FOR_DELIVERY','DELIVERED','PAYMENT_FAILED','REJECTED','CANCELLED')),
 actor_kind VARCHAR(16) NOT NULL CHECK(actor_kind IN ('USER','SYSTEM','PROVIDER')),
 actor_id UUID REFERENCES users(id),
 reason VARCHAR(1000),
 occurred_at TIMESTAMPTZ NOT NULL,
 CHECK((actor_kind='USER')=(actor_id IS NOT NULL)),
 CHECK(previous_status IS NULL OR previous_status<>new_status)
);
CREATE INDEX order_status_history_order_idx ON order_status_history(order_id,occurred_at,id);

CREATE TABLE payments (
 id UUID PRIMARY KEY,
 order_id UUID NOT NULL,
 method VARCHAR(16) NOT NULL CHECK(method IN ('CASH','CARD')),
 status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','AUTHORIZED','PAID','FAILED','REFUNDED')),
 amount NUMERIC(12,2) NOT NULL CHECK(amount>=0 AND amount<10000000000),
 currency VARCHAR(3) NOT NULL CHECK(currency='EGP'),
 provider VARCHAR(100),
 provider_payment_reference VARCHAR(200),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(order_id,currency,amount) REFERENCES orders(id,currency,final_total),
 CHECK(method<>'CASH' OR (provider IS NULL AND provider_payment_reference IS NULL))
);
CREATE INDEX payments_order_idx ON payments(order_id,created_at DESC,id DESC);
CREATE UNIQUE INDEX payments_provider_reference_uq
 ON payments(provider,provider_payment_reference)
 WHERE provider IS NOT NULL AND provider_payment_reference IS NOT NULL;

CREATE TABLE payment_status_history (
 id UUID PRIMARY KEY,
 payment_id UUID NOT NULL REFERENCES payments(id),
 previous_status VARCHAR(16),
 new_status VARCHAR(16) NOT NULL CHECK(new_status IN ('PENDING','AUTHORIZED','PAID','FAILED','REFUNDED')),
 actor_kind VARCHAR(16) NOT NULL CHECK(actor_kind IN ('USER','SYSTEM','PROVIDER')),
 actor_id UUID REFERENCES users(id),
 reason VARCHAR(1000),
 occurred_at TIMESTAMPTZ NOT NULL,
 CHECK((actor_kind='USER')=(actor_id IS NOT NULL)),
 CHECK(previous_status IS NULL OR previous_status<>new_status)
);
CREATE INDEX payment_status_history_payment_idx ON payment_status_history(payment_id,occurred_at,id);

CREATE FUNCTION validate_order_foundation_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.customer_id,NEW.restaurant_id,NEW.branch_id,NEW.currency,
     NEW.merchandise_subtotal,NEW.delivery_fee,NEW.discount_total,NEW.final_total,NEW.created_at)
    IS DISTINCT FROM
    (OLD.customer_id,OLD.restaurant_id,OLD.branch_id,OLD.currency,
     OLD.merchandise_subtotal,OLD.delivery_fee,OLD.discount_total,OLD.final_total,OLD.created_at) THEN
   RAISE EXCEPTION 'Order context and purchase totals are immutable' USING ERRCODE='23514';
 END IF;
 IF NEW.status<>OLD.status AND NOT (
   (OLD.status='PENDING_PAYMENT' AND NEW.status IN ('PLACED','PAYMENT_FAILED','CANCELLED')) OR
   (OLD.status='PLACED' AND NEW.status IN ('ACCEPTED','REJECTED','CANCELLED')) OR
   (OLD.status='ACCEPTED' AND NEW.status IN ('PREPARING','REJECTED')) OR
   (OLD.status='PREPARING' AND NEW.status='READY_FOR_PICKUP') OR
   (OLD.status='READY_FOR_PICKUP' AND NEW.status='OUT_FOR_DELIVERY') OR
   (OLD.status='OUT_FOR_DELIVERY' AND NEW.status='DELIVERED')) THEN
   RAISE EXCEPTION 'Invalid order status transition' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER order_foundation_update_guard BEFORE UPDATE ON orders
 FOR EACH ROW EXECUTE FUNCTION validate_order_foundation_update();

CREATE FUNCTION validate_payment_foundation_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.order_id,NEW.method,NEW.amount,NEW.currency,NEW.created_at) IS DISTINCT FROM
    (OLD.order_id,OLD.method,OLD.amount,OLD.currency,OLD.created_at) THEN
   RAISE EXCEPTION 'Payment order, method and amount are immutable' USING ERRCODE='23514';
 END IF;
 IF NEW.status<>OLD.status AND NOT (
   (OLD.method='CASH' AND OLD.status='PENDING' AND NEW.status='PAID') OR
   (OLD.method='CARD' AND OLD.status='PENDING' AND NEW.status IN ('AUTHORIZED','FAILED')) OR
   (OLD.method='CARD' AND OLD.status='AUTHORIZED' AND NEW.status IN ('PAID','FAILED')) OR
   (OLD.status='PAID' AND NEW.status='REFUNDED')) THEN
   RAISE EXCEPTION 'Invalid payment status transition' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payment_foundation_update_guard BEFORE UPDATE ON payments
 FOR EACH ROW EXECUTE FUNCTION validate_payment_foundation_update();

CREATE FUNCTION protect_order_record() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Historical order records are immutable' USING ERRCODE='23514';
END $$;
CREATE TRIGGER orders_no_delete BEFORE DELETE ON orders FOR EACH ROW EXECUTE FUNCTION protect_order_record();
CREATE TRIGGER order_items_immutable BEFORE UPDATE OR DELETE ON order_items FOR EACH ROW EXECUTE FUNCTION protect_order_record();
CREATE TRIGGER order_addresses_immutable BEFORE UPDATE OR DELETE ON order_address_snapshots FOR EACH ROW EXECUTE FUNCTION protect_order_record();
CREATE TRIGGER order_history_immutable BEFORE UPDATE OR DELETE ON order_status_history FOR EACH ROW EXECUTE FUNCTION protect_order_record();
CREATE TRIGGER payments_no_delete BEFORE DELETE ON payments FOR EACH ROW EXECUTE FUNCTION protect_order_record();
CREATE TRIGGER payment_history_immutable BEFORE UPDATE OR DELETE ON payment_status_history FOR EACH ROW EXECUTE FUNCTION protect_order_record();
