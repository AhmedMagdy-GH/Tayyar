ALTER TABLE orders ADD CONSTRAINT orders_review_context_uq
 UNIQUE (id, customer_id, restaurant_id, branch_id);

CREATE TABLE reviews (
 id UUID PRIMARY KEY,
 order_id UUID NOT NULL UNIQUE,
 customer_id UUID NOT NULL,
 restaurant_id UUID NOT NULL,
 branch_id UUID NOT NULL,
 rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
 comment VARCHAR(2000),
 moderation_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE'
   CHECK (moderation_status IN ('VISIBLE','HIDDEN')),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY (order_id, customer_id, restaurant_id, branch_id)
   REFERENCES orders(id, customer_id, restaurant_id, branch_id),
 CHECK (comment IS NULL OR length(comment) <= 2000)
);
CREATE INDEX reviews_restaurant_public_idx
 ON reviews(restaurant_id, moderation_status, created_at DESC, id DESC);
CREATE INDEX reviews_customer_idx ON reviews(customer_id, created_at DESC, id DESC);

CREATE FUNCTION protect_review_context() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.order_id, NEW.customer_id, NEW.restaurant_id, NEW.branch_id, NEW.created_at)
    IS DISTINCT FROM
    (OLD.order_id, OLD.customer_id, OLD.restaurant_id, OLD.branch_id, OLD.created_at) THEN
   RAISE EXCEPTION 'Review purchase context is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER review_context_guard BEFORE UPDATE ON reviews
 FOR EACH ROW EXECUTE FUNCTION protect_review_context();

CREATE FUNCTION protect_review_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Reviews are retained for moderation and audit' USING ERRCODE='23514';
END $$;
CREATE TRIGGER reviews_no_delete BEFORE DELETE ON reviews
 FOR EACH ROW EXECUTE FUNCTION protect_review_delete();

CREATE TABLE favorites (
 customer_id UUID NOT NULL REFERENCES users(id),
 restaurant_id UUID NOT NULL REFERENCES restaurants(id),
 created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (customer_id, restaurant_id)
);
CREATE INDEX favorites_customer_created_idx
 ON favorites(customer_id, created_at DESC, restaurant_id DESC);
