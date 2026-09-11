CREATE TABLE carts (
 id UUID PRIMARY KEY,
 customer_id UUID NOT NULL REFERENCES users(id),
 branch_id UUID NOT NULL,
 restaurant_id UUID NOT NULL,
 status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','ABANDONED','REPLACED','CHECKED_OUT')),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY (branch_id,restaurant_id) REFERENCES branches(id,restaurant_id),
 UNIQUE (id,restaurant_id)
);
CREATE UNIQUE INDEX carts_one_active_customer_idx ON carts(customer_id) WHERE status='ACTIVE';
CREATE INDEX carts_customer_history_idx ON carts(customer_id,created_at DESC,id DESC);

CREATE TABLE cart_items (
 id UUID PRIMARY KEY,
 cart_id UUID NOT NULL,
 menu_item_id UUID NOT NULL,
 restaurant_id UUID NOT NULL,
 quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
 acknowledged_unit_price NUMERIC(12,2) NOT NULL
   CHECK (acknowledged_unit_price >= 0 AND acknowledged_unit_price <> 'NaN'::numeric),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY (cart_id,restaurant_id) REFERENCES carts(id,restaurant_id),
 FOREIGN KEY (menu_item_id,restaurant_id) REFERENCES menu_items(id,restaurant_id),
 UNIQUE (cart_id,menu_item_id)
);

-- Context changes retire/create aggregates; they never retarget existing records.
CREATE FUNCTION protect_cart_context() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='carts' THEN
   IF (NEW.customer_id,NEW.branch_id,NEW.restaurant_id) IS DISTINCT FROM
      (OLD.customer_id,OLD.branch_id,OLD.restaurant_id) THEN
     RAISE EXCEPTION 'Cart context cannot change' USING ERRCODE='23514';
   END IF;
 ELSE
   IF (NEW.cart_id,NEW.menu_item_id,NEW.restaurant_id) IS DISTINCT FROM
      (OLD.cart_id,OLD.menu_item_id,OLD.restaurant_id) THEN
     RAISE EXCEPTION 'Cart item context cannot change' USING ERRCODE='23514';
   END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER cart_context_guard BEFORE UPDATE ON carts
 FOR EACH ROW EXECUTE FUNCTION protect_cart_context();
CREATE TRIGGER cart_item_context_guard BEFORE UPDATE ON cart_items
 FOR EACH ROW EXECUTE FUNCTION protect_cart_context();
